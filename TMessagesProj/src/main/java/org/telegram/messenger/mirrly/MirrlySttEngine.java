package org.telegram.messenger.mirrly;

import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.TranscribeButton;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.MirrlyTranslatorSettingsActivity;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Free Speech-to-Text engine powering MirrlyGram's fallback voice and round-video transcription
 * when Telegram trial quota is exhausted or Mirrly STT override is enabled.
 */
public final class MirrlySttEngine {

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    private static final int MAX_INLINE_AUDIO_BYTES = 18 * 1024 * 1024;
    private static final long PARTIAL_UPDATE_THROTTLE_MS = 180L;

    private static final MirrlySttEngine[] INSTANCES = new MirrlySttEngine[org.telegram.messenger.UserConfig.MAX_ACCOUNT_COUNT];
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "MirrlySttWorker");
        t.setDaemon(true);
        return t;
    });
    // Separate feeder pool so blocking PCM pipe writes cannot deadlock active worker jobs
    private static final ExecutorService FEEDER_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "MirrlySttFeeder");
        t.setDaemon(true);
        return t;
    });

    private final int currentAccount;
    private final ConcurrentHashMap<String, SttJob> activeJobs = new ConcurrentHashMap<>();

    private MirrlySttEngine(int currentAccount) {
        this.currentAccount = currentAccount;
    }

    @NonNull
    public static MirrlySttEngine getInstance(int currentAccount) {
        int acc = Math.max(0, Math.min(INSTANCES.length - 1, currentAccount));
        MirrlySttEngine instance = INSTANCES[acc];
        if (instance == null) {
            synchronized (MirrlySttEngine.class) {
                instance = INSTANCES[acc];
                if (instance == null) {
                    instance = new MirrlySttEngine(acc);
                    INSTANCES[acc] = instance;
                }
            }
        }
        return instance;
    }

    public static final class SttJob {
        final String key;
        final MessageObject messageObject;
        final long dialogId;
        final int messageId;
        final long syntheticId;
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        volatile NotificationCenter.NotificationCenterDelegate fileObserver;
        volatile String awaitedFileName;
        volatile SpeechRecognizer activeRecognizer;
        volatile ParcelFileDescriptor activePipeRead;
        volatile ParcelFileDescriptor activePipeWrite;

        SttJob(String key, MessageObject msg) {
            this.key = key;
            this.messageObject = msg;
            this.dialogId = msg.getDialogId();
            this.messageId = msg.getId();
            this.syntheticId = -Math.max(1L, Math.abs((long) this.messageId));
        }
    }

    @NonNull
    private static String jobKey(@NonNull MessageObject msg) {
        return msg.getDialogId() + "_" + msg.getId();
    }

    public boolean isTranscribing(@Nullable MessageObject msg) {
        if (msg == null || msg.messageOwner == null) {
            return false;
        }
        SttJob job = activeJobs.get(jobKey(msg));
        return job != null && !job.cancelled.get();
    }

    public static boolean isSystemSpeechFileInjectionSupported() {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false;
        }
        try {
            return SpeechRecognizer.isRecognitionAvailable(ctx) || SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx);
        } catch (Throwable ignore) {
            return false;
        }
    }

    @NonNull
    public static String getProviderShortName(int provider) {
        switch (provider) {
            case MirrlyConfig.STT_PROVIDER_SYSTEM:
                return LocaleController.getString(R.string.MirrlySttProviderSystemShort);
            case MirrlyConfig.STT_PROVIDER_GROQ:
                return TextUtils.isEmpty(MirrlyConfig.getFreeSttGroqApiKey())
                        ? LocaleController.getString(R.string.MirrlySttProviderAuto)
                        : "Groq";
            case MirrlyConfig.STT_PROVIDER_GEMINI:
                return TextUtils.isEmpty(MirrlyConfig.getGeminiApiKey())
                        ? LocaleController.getString(R.string.MirrlySttProviderAuto)
                        : "Gemini";
            case MirrlyConfig.STT_PROVIDER_CUSTOM:
                return TextUtils.isEmpty(MirrlyConfig.getFreeSttCustomUrl())
                        ? LocaleController.getString(R.string.MirrlySttProviderAuto)
                        : "Whisper";
            case MirrlyConfig.STT_PROVIDER_AUTO:
            default:
                return LocaleController.getString(R.string.MirrlySttProviderAuto);
        }
    }

    @NonNull
    public static String getProviderDisplayName(int provider) {
        switch (provider) {
            case MirrlyConfig.STT_PROVIDER_SYSTEM:
                return LocaleController.getString(R.string.MirrlySttProviderSystem);
            case MirrlyConfig.STT_PROVIDER_GROQ:
                return TextUtils.isEmpty(MirrlyConfig.getFreeSttGroqApiKey())
                        ? LocaleController.getString(R.string.MirrlySttProviderAuto)
                        : LocaleController.getString(R.string.MirrlySttProviderGroq);
            case MirrlyConfig.STT_PROVIDER_GEMINI:
                return TextUtils.isEmpty(MirrlyConfig.getGeminiApiKey())
                        ? LocaleController.getString(R.string.MirrlySttProviderAuto)
                        : LocaleController.getString(R.string.MirrlySttProviderGemini);
            case MirrlyConfig.STT_PROVIDER_CUSTOM:
                return TextUtils.isEmpty(MirrlyConfig.getFreeSttCustomUrl())
                        ? LocaleController.getString(R.string.MirrlySttProviderAuto)
                        : LocaleController.getString(R.string.MirrlySttProviderCustom);
            case MirrlyConfig.STT_PROVIDER_AUTO:
            default:
                return LocaleController.getString(R.string.MirrlySttProviderAuto);
        }
    }

    public void cancel(@Nullable MessageObject msg) {
        if (msg == null) {
            return;
        }
        SttJob job = activeJobs.remove(jobKey(msg));
        if (job != null) {
            job.cancelled.set(true);
            cleanupJobObservers(job);
            closePipeQuietly(job.activePipeWrite);
            job.activePipeWrite = null;
            AndroidUtilities.runOnUIThread(() -> {
                destroyActiveRecognizer(job);
                closePipeQuietly(job.activePipeRead);
                job.activePipeRead = null;
                if (msg.messageOwner != null && !msg.messageOwner.voiceTranscriptionFinal && TextUtils.isEmpty(msg.messageOwner.voiceTranscription)) {
                    msg.messageOwner.voiceTranscription = null;
                    msg.messageOwner.voiceTranscriptionOpen = false;
                    msg.messageOwner.voiceTranscriptionFinal = false;
                }
                NotificationCenter.getInstance(currentAccount).postNotificationName(
                        NotificationCenter.voiceTranscriptionUpdate,
                        msg,
                        job.syntheticId,
                        msg.messageOwner != null ? msg.messageOwner.voiceTranscription : null,
                        msg.messageOwner != null && msg.messageOwner.voiceTranscriptionOpen,
                        true
                );
            });
        }
    }

    public void transcribeMessage(@NonNull MessageObject msg) {
        if (msg.messageOwner == null || (!msg.isVoice() && !msg.isRoundVideo())) {
            return;
        }
        String key = jobKey(msg);
        if (activeJobs.containsKey(key)) {
            cancel(msg);
            return;
        }

        final SttJob job = new SttJob(key, msg);
        activeJobs.put(key, job);

        AndroidUtilities.runOnUIThread(() -> {
            if (job.cancelled.get() || activeJobs.get(key) != job || msg.messageOwner == null) {
                return;
            }
            msg.messageOwner.translatedVoiceTranscription = null;
            msg.translated = false;
            msg.messageOwner.mirrlyTranslatedOpen = false;
            if (msg.isRoundVideo()) {
                TranscribeButton.openVideoTranscription(msg);
            }
            msg.messageOwner.voiceTranscription = "";
            msg.messageOwner.voiceTranscriptionOpen = true;
            msg.messageOwner.voiceTranscriptionFinal = false;
            msg.messageOwner.voiceTranscriptionId = job.syntheticId;

            NotificationCenter.getInstance(currentAccount).postNotificationName(
                    NotificationCenter.voiceTranscriptionUpdate,
                    msg,
                    job.syntheticId,
                    "",
                    true,
                    false
            );
        });

        EXECUTOR.execute(() -> runJob(job));
    }

    private void runJob(@NonNull SttJob job) {
        try {
            File mediaFile = ensureMediaDownloaded(job);
            if (job.cancelled.get()) {
                return;
            }
            if (mediaFile == null || !mediaFile.exists() || mediaFile.length() == 0) {
                finishWithFailure(job, LocaleController.getString(R.string.MirrlySttErrDownloadFailed), false);
                return;
            }

            List<Integer> plan = buildProviderPlan(MirrlyConfig.getFreeSttProvider());
            if (plan.isEmpty()) {
                finishWithFailure(job, LocaleController.getString(R.string.MirrlySttErrSetupNeeded), true);
                return;
            }

            String lastError = null;
            boolean suggestSettings = false;
            for (int provider : plan) {
                if (job.cancelled.get()) {
                    return;
                }
                long sttStartMs = System.currentTimeMillis();
                SttAttemptResult res = executeProvider(job, provider, mediaFile);
                MirrlyPowerTelemetry.recordSttWork(mediaFile.length(), Math.max(0L, System.currentTimeMillis() - sttStartMs));
                if (job.cancelled.get()) {
                    return;
                }
                if (res.success && !TextUtils.isEmpty(res.text)) {
                    finishWithSuccess(job, res.text.trim());
                    return;
                }
                if (res.emptySpeech) {
                    finishWithSuccess(job, "");
                    return;
                }
                suggestSettings |= res.suggestSettings;
                if (!TextUtils.isEmpty(res.error)) {
                    lastError = res.error;
                }
            }

            finishWithFailure(
                    job,
                    lastError != null ? lastError : LocaleController.getString(R.string.MirrlySttErrGeneric),
                    suggestSettings
            );
        } catch (Throwable t) {
            FileLog.e(t, false);
            finishWithFailure(job, LocaleController.getString(R.string.MirrlySttErrGeneric), false);
        }
    }

    @Nullable
    private File ensureMediaDownloaded(@NonNull SttJob job) {
        MessageObject msg = job.messageObject;
        TLRPC.Document doc = msg.getDocument();
        File existing = resolveExistingMediaFile(msg);
        if (existing != null) {
            return existing;
        }
        if (doc == null) {
            return null;
        }
        final String expectedName = FileLoader.getAttachFileName(doc);
        job.awaitedFileName = expectedName;
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean failed = new AtomicBoolean(false);
        final AtomicReference<File> loadedFileRef = new AtomicReference<>();

        final NotificationCenter.NotificationCenterDelegate delegate = (id, account, args) -> {
            if (args == null || args.length == 0) {
                return;
            }
            String location = String.valueOf(args[0]);
            if (TextUtils.equals(location, expectedName)) {
                if (id == NotificationCenter.fileLoadFailed) {
                    failed.set(true);
                } else if (id == NotificationCenter.fileLoaded && args.length > 1 && args[1] instanceof File) {
                    File f = (File) args[1];
                    if (isNonEmptyFile(f)) {
                        loadedFileRef.set(f);
                    }
                }
                latch.countDown();
            }
        };
        job.fileObserver = delegate;
        AndroidUtilities.runOnUIThread(() -> {
            if (job.cancelled.get()) {
                latch.countDown();
                return;
            }
            NotificationCenter nc = NotificationCenter.getInstance(currentAccount);
            nc.addObserver(delegate, NotificationCenter.fileLoaded);
            nc.addObserver(delegate, NotificationCenter.fileLoadFailed);
            File fastReady = resolveExistingMediaFile(msg);
            if (fastReady != null) {
                loadedFileRef.set(fastReady);
                latch.countDown();
                return;
            }
            FileLoader.getInstance(currentAccount).loadFile(doc, msg, FileLoader.PRIORITY_HIGH, 0);
        });

        try {
            latch.await(90, TimeUnit.SECONDS);
        } catch (InterruptedException ignore) {
            Thread.currentThread().interrupt();
        } finally {
            cleanupJobObservers(job);
        }
        if (failed.get() || job.cancelled.get()) {
            return null;
        }
        File eventFile = loadedFileRef.get();
        if (isNonEmptyFile(eventFile)) {
            return eventFile;
        }
        return resolveExistingMediaFile(msg);
    }

    @Nullable
    private File resolveExistingMediaFile(@NonNull MessageObject msg) {
        if (msg.messageOwner == null) {
            return null;
        }
        if (!TextUtils.isEmpty(msg.messageOwner.attachPath)) {
            File attachFile = new File(msg.messageOwner.attachPath);
            if (isNonEmptyFile(attachFile)) {
                return attachFile;
            }
        }
        FileLoader loader = FileLoader.getInstance(currentAccount);
        File mediaFile = loader.getPathToMessage(msg.messageOwner);
        if (isNonEmptyFile(mediaFile)) {
            return mediaFile;
        }
        File cachedFile = loader.getPathToMessage(msg.messageOwner, true, true);
        return isNonEmptyFile(cachedFile) ? cachedFile : null;
    }

    private static boolean isNonEmptyFile(@Nullable File f) {
        return f != null && f.exists() && f.length() > 0;
    }

    private void cleanupJobObservers(@NonNull SttJob job) {
        NotificationCenter.NotificationCenterDelegate delegate = job.fileObserver;
        if (delegate != null) {
            job.fileObserver = null;
            AndroidUtilities.runOnUIThread(() -> {
                NotificationCenter nc = NotificationCenter.getInstance(currentAccount);
                nc.removeObserver(delegate, NotificationCenter.fileLoaded);
                nc.removeObserver(delegate, NotificationCenter.fileLoadFailed);
            });
        }
    }

    @NonNull
    private static List<Integer> buildProviderPlan(int selectedProvider) {
        if ((selectedProvider == MirrlyConfig.STT_PROVIDER_GROQ && TextUtils.isEmpty(MirrlyConfig.getFreeSttGroqApiKey()))
                || (selectedProvider == MirrlyConfig.STT_PROVIDER_GEMINI && TextUtils.isEmpty(MirrlyConfig.getGeminiApiKey()))
                || (selectedProvider == MirrlyConfig.STT_PROVIDER_CUSTOM && TextUtils.isEmpty(MirrlyConfig.getFreeSttCustomUrl()))) {
            selectedProvider = MirrlyConfig.STT_PROVIDER_AUTO;
        }
        List<Integer> plan = new ArrayList<>();
        if (selectedProvider != MirrlyConfig.STT_PROVIDER_AUTO) {
            plan.add(selectedProvider);
            return plan;
        }
        if (!TextUtils.isEmpty(MirrlyConfig.getFreeSttGroqApiKey())) {
            plan.add(MirrlyConfig.STT_PROVIDER_GROQ);
        }
        if (!TextUtils.isEmpty(MirrlyConfig.getGeminiApiKey())) {
            plan.add(MirrlyConfig.STT_PROVIDER_GEMINI);
        }
        if (!TextUtils.isEmpty(MirrlyConfig.getFreeSttCustomUrl())) {
            plan.add(MirrlyConfig.STT_PROVIDER_CUSTOM);
        }
        if (isSystemSpeechFileInjectionSupported()) {
            plan.add(MirrlyConfig.STT_PROVIDER_SYSTEM);
        }
        return plan;
    }

    private static final class SttAttemptResult {
        final boolean success;
        final boolean emptySpeech;
        final boolean suggestSettings;
        final String text;
        final String error;

        SttAttemptResult(boolean success, boolean emptySpeech, boolean suggestSettings, String text, String error) {
            this.success = success;
            this.emptySpeech = emptySpeech;
            this.suggestSettings = suggestSettings;
            this.text = text;
            this.error = error;
        }

        static SttAttemptResult ok(String text) {
            return new SttAttemptResult(true, false, false, text, null);
        }

        static SttAttemptResult noSpeech() {
            return new SttAttemptResult(false, true, false, "", null);
        }

        static SttAttemptResult err(String error, boolean suggestSettings) {
            return new SttAttemptResult(false, false, suggestSettings, null, error);
        }
    }

    @NonNull
    private SttAttemptResult executeProvider(@NonNull SttJob job, int provider, @NonNull File mediaFile) {
        switch (provider) {
            case MirrlyConfig.STT_PROVIDER_SYSTEM:
                return recognizeWithSystemSpeech(job, mediaFile);
            case MirrlyConfig.STT_PROVIDER_GROQ:
                return recognizeWithGroq(job, mediaFile);
            case MirrlyConfig.STT_PROVIDER_GEMINI:
                return recognizeWithGemini(job, mediaFile);
            case MirrlyConfig.STT_PROVIDER_CUSTOM:
                return recognizeWithCustomWhisper(job, mediaFile);
            default:
                return SttAttemptResult.err(LocaleController.getString(R.string.MirrlySttErrSetupNeeded), true);
        }
    }

    @NonNull
    private SttAttemptResult recognizeWithSystemSpeech(@NonNull SttJob job, @NonNull File mediaFile) {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return SttAttemptResult.err(LocaleController.getString(R.string.MirrlySttErrAndroid13Required), true);
        }
        boolean hasOnDevice = isOnDeviceRecognitionAvailableSafe(ctx);
        boolean hasStandard = isStandardRecognitionAvailableSafe(ctx);
        if (!hasOnDevice && !hasStandard) {
            return SttAttemptResult.err(LocaleController.getString(R.string.MirrlySttErrSystemUnavailable), true);
        }

        File cacheDir = ctx.getCacheDir();
        File pcmFile = new File(cacheDir, "mirrly_stt_" + Math.abs(job.messageId) + "_" + System.nanoTime() + ".pcm");
        try {
            if (!MirrlyAudioDecoder.decodeToMonoPcm16(mediaFile, pcmFile, MirrlyAudioDecoder.TARGET_SAMPLE_RATE)) {
                return SttAttemptResult.err(LocaleController.getString(R.string.MirrlySttErrDecodeFailed), false);
            }
            if (job.cancelled.get()) {
                return SttAttemptResult.err("Cancelled", false);
            }

            final String langTag = resolveRecognitionLanguageTag();
            final boolean preferOffline = MirrlyConfig.isFreeSttPreferOffline();
            final boolean primaryOnDevice = preferOffline && hasOnDevice;
            SystemSpeechOutcome outcome = runSystemSpeechAttempt(
                    job,
                    ctx,
                    pcmFile,
                    langTag,
                    preferOffline && primaryOnDevice,
                    primaryOnDevice
            );

            boolean canTryAlternate = primaryOnDevice ? hasStandard : hasOnDevice;
            if (!job.cancelled.get()
                    && TextUtils.isEmpty(outcome.text)
                    && !isNoSpeechError(outcome.errorCode)
                    && canTryAlternate) {
                boolean altOnDevice = !primaryOnDevice;
                outcome = runSystemSpeechAttempt(
                        job,
                        ctx,
                        pcmFile,
                        langTag,
                        preferOffline && altOnDevice,
                        altOnDevice
                );
            }

            if (job.cancelled.get()) {
                return SttAttemptResult.err("Cancelled", false);
            }
            if (!TextUtils.isEmpty(outcome.text)) {
                return SttAttemptResult.ok(outcome.text);
            }
            if (isNoSpeechError(outcome.errorCode)) {
                return SttAttemptResult.noSpeech();
            }
            if (outcome.errorCode == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED
                    || outcome.errorCode == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) {
                return SttAttemptResult.err(LocaleController.getString(R.string.MirrlySttErrLanguagePack), true);
            }
            return SttAttemptResult.err(LocaleController.getString(R.string.MirrlySttErrSystemUnavailable), true);
        } catch (InterruptedException ignore) {
            Thread.currentThread().interrupt();
            return SttAttemptResult.err("Cancelled", false);
        } finally {
            if (pcmFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                pcmFile.delete();
            }
        }
    }

    private static final class SystemSpeechOutcome {
        final String text;
        final int errorCode;

        SystemSpeechOutcome(String text, int errorCode) {
            this.text = text;
            this.errorCode = errorCode;
        }
    }

    @NonNull
    private SystemSpeechOutcome runSystemSpeechAttempt(
            @NonNull SttJob job,
            @NonNull Context ctx,
            @NonNull File pcmFile,
            @NonNull String langTag,
            boolean preferOfflineExtra,
            boolean useOnDevice
    ) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> finalText = new AtomicReference<>("");
        final AtomicReference<String> lastPartial = new AtomicReference<>("");
        final AtomicLong lastPartialPostMs = new AtomicLong(0L);
        final AtomicInteger errCode = new AtomicInteger(0);
        final AtomicBoolean stopFeeder = new AtomicBoolean(false);
        final AtomicReference<ParcelFileDescriptor> readPipeRef = new AtomicReference<>();
        final AtomicReference<ParcelFileDescriptor> writePipeRef = new AtomicReference<>();

        AndroidUtilities.runOnUIThread(() -> {
            if (job.cancelled.get()) {
                latch.countDown();
                return;
            }
            try {
                SpeechRecognizer recognizer = useOnDevice
                        ? SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
                        : SpeechRecognizer.createSpeechRecognizer(ctx);
                job.activeRecognizer = recognizer;

                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                readPipeRef.set(pipe[0]);
                writePipeRef.set(pipe[1]);
                job.activePipeRead = pipe[0];
                job.activePipeWrite = pipe[1];

                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag);
                intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
                if (preferOfflineExtra) {
                    intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
                }
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0]);
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, MirrlyAudioDecoder.TARGET_SAMPLE_RATE);
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1);
                intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L);
                intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L);

                recognizer.setRecognitionListener(new RecognitionListener() {
                    @Override
                    public void onReadyForSpeech(Bundle params) {
                    }

                    @Override
                    public void onBeginningOfSpeech() {
                    }

                    @Override
                    public void onRmsChanged(float rmsdB) {
                    }

                    @Override
                    public void onBufferReceived(byte[] buffer) {
                    }

                    @Override
                    public void onEndOfSpeech() {
                    }

                    @Override
                    public void onError(int error) {
                        errCode.set(error);
                        stopFeeder.set(true);
                        closeAttemptPipes(job, readPipeRef, writePipeRef);
                        latch.countDown();
                    }

                    @Override
                    public void onResults(Bundle results) {
                        ArrayList<String> matches = results != null
                                ? results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
                        if (matches != null && !matches.isEmpty() && !TextUtils.isEmpty(matches.get(0))) {
                            finalText.set(matches.get(0));
                        }
                        stopFeeder.set(true);
                        closeAttemptPipes(job, readPipeRef, writePipeRef);
                        latch.countDown();
                    }

                    @Override
                    public void onPartialResults(Bundle partialResults) {
                        ArrayList<String> partials = partialResults != null
                                ? partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
                        if (partials != null && !partials.isEmpty() && !TextUtils.isEmpty(partials.get(0))) {
                            String latest = partials.get(0);
                            lastPartial.set(latest);
                            long now = System.currentTimeMillis();
                            if (now - lastPartialPostMs.get() >= PARTIAL_UPDATE_THROTTLE_MS) {
                                lastPartialPostMs.set(now);
                                postPartialUpdate(job, latest);
                            }
                        }
                    }

                    @Override
                    public void onEvent(int eventType, Bundle params) {
                    }
                });

                recognizer.startListening(intent);
                // Keep pipe[0] open until RecognitionService finishes binding and parcelling EXTRA_AUDIO_SOURCE
                final ParcelFileDescriptor writeEnd = pipe[1];
                FEEDER_EXECUTOR.execute(() -> streamPcmIntoPipe(pcmFile, writeEnd, stopFeeder));
            } catch (Throwable t) {
                FileLog.e(t, false);
                errCode.set(SpeechRecognizer.ERROR_CLIENT);
                stopFeeder.set(true);
                destroyActiveRecognizer(job);
                closeAttemptPipes(job, readPipeRef, writePipeRef);
                latch.countDown();
            }
        });

        long audioDurationSec = Math.max(5L, pcmFile.length() / (MirrlyAudioDecoder.TARGET_SAMPLE_RATE * 2L));
        boolean completed = latch.await(audioDurationSec + 25L, TimeUnit.SECONDS);
        stopFeeder.set(true);
        if (!completed && errCode.get() == 0) {
            errCode.set(SpeechRecognizer.ERROR_SPEECH_TIMEOUT);
        }

        // Unblock any stalled feeder write immediately before UI-thread recognizer destruction
        ParcelFileDescriptor writePipe = writePipeRef.getAndSet(null);
        if (job.activePipeWrite == writePipe) {
            job.activePipeWrite = null;
        }
        closePipeQuietly(writePipe);

        final CountDownLatch cleanupLatch = new CountDownLatch(1);
        AndroidUtilities.runOnUIThread(() -> {
            try {
                destroyActiveRecognizer(job);
                closeAttemptPipes(job, readPipeRef, writePipeRef);
            } finally {
                cleanupLatch.countDown();
            }
        });
        cleanupLatch.await(2L, TimeUnit.SECONDS);

        String recognized = finalText.get();
        int code = errCode.get();
        if (TextUtils.isEmpty(recognized) && !job.cancelled.get()) {
            String partial = lastPartial.get();
            if (!TextUtils.isEmpty(partial)
                    && (code == 0 || isNoSpeechError(code) || code == SpeechRecognizer.ERROR_SERVER_DISCONNECTED)) {
                recognized = partial;
            }
        }
        return new SystemSpeechOutcome(recognized, code);
    }

    private static boolean isOnDeviceRecognitionAvailableSafe(@NonNull Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false;
        }
        try {
            return SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx);
        } catch (Throwable ignore) {
            return false;
        }
    }

    private static boolean isStandardRecognitionAvailableSafe(@NonNull Context ctx) {
        try {
            return SpeechRecognizer.isRecognitionAvailable(ctx);
        } catch (Throwable ignore) {
            return false;
        }
    }

    private static boolean isNoSpeechError(int code) {
        return code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT;
    }

    private static void destroyActiveRecognizer(@NonNull SttJob job) {
        SpeechRecognizer recognizer = job.activeRecognizer;
        job.activeRecognizer = null;
        if (recognizer != null) {
            try {
                recognizer.cancel();
            } catch (Throwable ignore) {
            }
            try {
                recognizer.destroy();
            } catch (Throwable ignore) {
            }
        }
    }

    private static void closeAttemptPipes(
            @NonNull SttJob job,
            @NonNull AtomicReference<ParcelFileDescriptor> readPipeRef,
            @NonNull AtomicReference<ParcelFileDescriptor> writePipeRef
    ) {
        ParcelFileDescriptor writePipe = writePipeRef.getAndSet(null);
        if (job.activePipeWrite == writePipe) {
            job.activePipeWrite = null;
        }
        closePipeQuietly(writePipe);

        ParcelFileDescriptor readPipe = readPipeRef.getAndSet(null);
        if (job.activePipeRead == readPipe) {
            job.activePipeRead = null;
        }
        closePipeQuietly(readPipe);
    }

    private static void closePipeQuietly(@Nullable ParcelFileDescriptor pfd) {
        if (pfd != null) {
            try {
                pfd.close();
            } catch (Throwable ignore) {
            }
        }
    }

    private static void streamPcmIntoPipe(@NonNull File pcmFile, @NonNull ParcelFileDescriptor writeEnd, @NonNull AtomicBoolean stopFlag) {
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(pcmFile));
             ParcelFileDescriptor.AutoCloseOutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(writeEnd)) {
            byte[] chunk = new byte[3200]; // 100 ms at 16 kHz mono 16-bit
            int n;
            while (!stopFlag.get() && (n = in.read(chunk)) > 0) {
                out.write(chunk, 0, n);
                out.flush();
                Thread.sleep(95L);
            }
            if (!stopFlag.get()) {
                byte[] trailingSilence = new byte[3200];
                for (int i = 0; i < 6 && !stopFlag.get(); i++) {
                    out.write(trailingSilence);
                    out.flush();
                    Thread.sleep(95L);
                }
            }
        } catch (Throwable ignore) {
        }
    }

    @NonNull
    private SttAttemptResult recognizeWithGroq(@NonNull SttJob job, @NonNull File mediaFile) {
        String apiKey = MirrlyConfig.getFreeSttGroqApiKey();
        if (TextUtils.isEmpty(apiKey)) {
            return SttAttemptResult.err(LocaleController.getString(R.string.MirrlySttErrKeyMissing), true);
        }
        File uploadFile = mediaFile;
        File tempWav = null;
        try {
            if (mediaFile.length() > MAX_INLINE_AUDIO_BYTES) {
                tempWav = decodeToTempWav(job, mediaFile);
                if (tempWav == null) {
                    return SttAttemptResult.err(LocaleController.getString(R.string.MirrlySttErrDecodeFailed), false);
                }
                uploadFile = tempWav;
            }
            String fileName = uploadFile.getName();
            if (!fileName.contains(".")) {
                fileName = job.messageObject.isRoundVideo() ? "round.mp4" : "voice.ogg";
            }
            String mime = fileName.endsWith(".wav") ? "audio/wav" : (fileName.endsWith(".mp4") ? "video/mp4" : "audio/ogg");
            String lang = getExplicitIso2SpeechLang();
            return executeWhisperMultipart(
                    "https://api.groq.com/openai/v1/audio/transcriptions",
                    apiKey,
                    "whisper-large-v3-turbo",
                    lang,
                    uploadFile,
                    fileName,
                    mime
            );
        } finally {
            if (tempWav != null && tempWav.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tempWav.delete();
            }
        }
    }

    @NonNull
    private SttAttemptResult recognizeWithCustomWhisper(@NonNull SttJob job, @NonNull File mediaFile) {
        String customUrl = MirrlyTranslatorEngine.normalizeBaseUrl(MirrlyConfig.getFreeSttCustomUrl());
        if (TextUtils.isEmpty(customUrl)) {
            return SttAttemptResult.err(LocaleController.getString(R.string.MirrlySttErrKeyMissing), true);
        }
        if (!MirrlyTranslatorEngine.isSafeEndpointUrl(customUrl)) {
            return SttAttemptResult.err("HTTPS required for remote Whisper endpoints", true);
        }
        String endpoint = customUrl.endsWith("/transcriptions") ? customUrl : customUrl + "/v1/audio/transcriptions";
        File uploadFile = mediaFile;
        File tempWav = decodeToTempWav(job, mediaFile);
        if (tempWav != null && tempWav.exists() && tempWav.length() > 44) {
            uploadFile = tempWav;
        }
        try {
            String fileName = uploadFile.getName().endsWith(".wav") ? "audio.wav"
                    : (job.messageObject.isRoundVideo() ? "round.mp4" : "voice.ogg");
            String mime = fileName.endsWith(".wav") ? "audio/wav" : (fileName.endsWith(".mp4") ? "video/mp4" : "audio/ogg");
            return executeWhisperMultipart(
                    endpoint,
                    MirrlyConfig.getFreeSttCustomApiKey(),
                    "whisper-1",
                    getExplicitIso2SpeechLang(),
                    uploadFile,
                    fileName,
                    mime
            );
        } finally {
            if (tempWav != null && tempWav.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tempWav.delete();
            }
        }
    }

    @NonNull
    private SttAttemptResult executeWhisperMultipart(
            @NonNull String urlString,
            @Nullable String bearerKey,
            @NonNull String model,
            @Nullable String languageIso2,
            @NonNull File audioFile,
            @NonNull String fileName,
            @NonNull String mimeType
    ) {
        String boundary = "MirrlyBoundary" + UUID.randomUUID().toString().replace("-", "");
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlString).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            if (!TextUtils.isEmpty(bearerKey)) {
                conn.setRequestProperty("Authorization", "Bearer " + bearerKey.trim());
            }

            try (OutputStream out = conn.getOutputStream()) {
                writeFormField(out, boundary, "model", model);
                writeFormField(out, boundary, "response_format", "json");
                if (!TextUtils.isEmpty(languageIso2)) {
                    writeFormField(out, boundary, "language", languageIso2);
                }
                writeFilePart(out, boundary, "file", fileName, mimeType, audioFile);
                out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }

            int code = conn.getResponseCode();
            String body = readResponseBody(conn, code);
            if (code == 401 || code == 403) {
                return SttAttemptResult.err(LocaleController.getString(R.string.MirrlySttErrAuth), true);
            }
            if (code == 429) {
                return SttAttemptResult.err(LocaleController.getString(R.string.MirrlySttErrRateLimit), false);
            }
            if (code < 200 || code >= 300 || TextUtils.isEmpty(body)) {
                return SttAttemptResult.err("HTTP " + code, false);
            }
            JSONObject obj = new JSONObject(body);
            String text = obj.optString("text", "").trim();
            if (TextUtils.isEmpty(text)) {
                return SttAttemptResult.noSpeech();
            }
            return SttAttemptResult.ok(text);
        } catch (Exception e) {
            FileLog.e(e, false);
            return SttAttemptResult.err(e.getMessage(), false);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    @NonNull
    private SttAttemptResult recognizeWithGemini(@NonNull SttJob job, @NonNull File mediaFile) {
        String apiKey = MirrlyConfig.getGeminiApiKey();
        if (TextUtils.isEmpty(apiKey)) {
            return SttAttemptResult.err(LocaleController.getString(R.string.MirrlySttErrKeyMissing), true);
        }

        File payloadFile = mediaFile;
        File tempWav = null;
        String mimeType = job.messageObject.isRoundVideo() ? "audio/wav" : "audio/ogg";
        // For round videos, always extract lightweight audio-only WAV rather than uploading video pixels
        if (job.messageObject.isRoundVideo() || mediaFile.length() > MAX_INLINE_AUDIO_BYTES) {
            tempWav = decodeToTempWav(job, mediaFile);
            if (tempWav == null) {
                return SttAttemptResult.err(LocaleController.getString(R.string.MirrlySttErrDecodeFailed), false);
            }
            payloadFile = tempWav;
            mimeType = "audio/wav";
        }
        if (payloadFile.length() > MAX_INLINE_AUDIO_BYTES) {
            if (tempWav != null) {
                //noinspection ResultOfMethodCallIgnored
                tempWav.delete();
            }
            return SttAttemptResult.err(LocaleController.getString(R.string.MirrlySttErrAudioTooLarge), false);
        }

        HttpURLConnection conn = null;
        try {
            byte[] audioBytes = readFileBytes(payloadFile);
            String b64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP);

            String explicitLang = getExplicitIso2SpeechLang();
            String prompt = !TextUtils.isEmpty(explicitLang)
                    ? "Transcribe spoken words verbatim in language '" + explicitLang + "' with punctuation. If silent or unintelligible, return empty string. Output ONLY transcript text."
                    : "Transcribe spoken words verbatim in the speaker's original language with natural punctuation. If silent, output empty string. Output ONLY transcript text.";

            JSONObject inlineData = new JSONObject()
                    .put("mime_type", mimeType)
                    .put("data", b64);
            JSONArray parts = new JSONArray()
                    .put(new JSONObject().put("text", prompt))
                    .put(new JSONObject().put("inline_data", inlineData));
            JSONObject content = new JSONObject().put("role", "user").put("parts", parts);
            JSONObject genCfg = new JSONObject().put("temperature", 0.0);
            JSONObject root = new JSONObject()
                    .put("contents", new JSONArray().put(content))
                    .put("generationConfig", genCfg);

            conn = (HttpURLConnection) new URL(
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
            ).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("x-goog-api-key", apiKey.trim());

            byte[] reqBytes = root.toString().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(reqBytes.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(reqBytes);
            }

            int code = conn.getResponseCode();
            String body = readResponseBody(conn, code);
            if (code == 401 || code == 403) {
                return SttAttemptResult.err(LocaleController.getString(R.string.MirrlySttErrAuth), true);
            }
            if (code == 429) {
                return SttAttemptResult.err(LocaleController.getString(R.string.MirrlySttErrRateLimit), false);
            }
            if (code < 200 || code >= 300 || TextUtils.isEmpty(body)) {
                return SttAttemptResult.err("HTTP " + code, false);
            }
            JSONObject res = new JSONObject(body);
            JSONArray candidates = res.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) {
                return SttAttemptResult.noSpeech();
            }
            JSONObject c0 = candidates.optJSONObject(0);
            JSONObject cContent = c0 != null ? c0.optJSONObject("content") : null;
            JSONArray outParts = cContent != null ? cContent.optJSONArray("parts") : null;
            if (outParts == null || outParts.length() == 0) {
                return SttAttemptResult.noSpeech();
            }
            String text = outParts.optJSONObject(0).optString("text", "").trim();
            if (TextUtils.isEmpty(text)) {
                return SttAttemptResult.noSpeech();
            }
            return SttAttemptResult.ok(text);
        } catch (Exception e) {
            FileLog.e(e, false);
            return SttAttemptResult.err(e.getMessage(), false);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
            if (tempWav != null && tempWav.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tempWav.delete();
            }
        }
    }

    @Nullable
    private File decodeToTempWav(@NonNull SttJob job, @NonNull File srcFile) {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) {
            return null;
        }
        File pcm = new File(ctx.getCacheDir(), "mirrly_tmp_" + Math.abs(job.messageId) + "_" + System.nanoTime() + ".pcm");
        File wav = new File(ctx.getCacheDir(), "mirrly_tmp_" + Math.abs(job.messageId) + "_" + System.nanoTime() + ".wav");
        try {
            if (!MirrlyAudioDecoder.decodeToMonoPcm16(srcFile, pcm, MirrlyAudioDecoder.TARGET_SAMPLE_RATE)) {
                return null;
            }
            if (!MirrlyAudioDecoder.wrapPcmAsWav(pcm, wav, MirrlyAudioDecoder.TARGET_SAMPLE_RATE)) {
                if (wav.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    wav.delete();
                }
                return null;
            }
            return wav;
        } finally {
            if (pcm.exists()) {
                //noinspection ResultOfMethodCallIgnored
                pcm.delete();
            }
        }
    }

    private void postPartialUpdate(@NonNull SttJob job, @NonNull String partialText) {
        AndroidUtilities.runOnUIThread(() -> {
            if (job.cancelled.get() || job.messageObject.messageOwner == null) {
                return;
            }
            if (job.messageObject.isRoundVideo()) {
                TranscribeButton.openVideoTranscription(job.messageObject);
            }
            job.messageObject.messageOwner.voiceTranscription = partialText;
            job.messageObject.messageOwner.voiceTranscriptionOpen = true;
            job.messageObject.messageOwner.voiceTranscriptionFinal = false;
            NotificationCenter.getInstance(currentAccount).postNotificationName(
                    NotificationCenter.voiceTranscriptionUpdate,
                    job.messageObject,
                    job.syntheticId,
                    partialText,
                    true,
                    false
            );
        });
    }

    private void finishWithSuccess(@NonNull SttJob job, @NonNull String text) {
        activeJobs.remove(job.key);
        AndroidUtilities.runOnUIThread(() -> {
            if (job.cancelled.get() || job.messageObject.messageOwner == null) {
                return;
            }
            if (job.messageObject.isRoundVideo()) {
                TranscribeButton.openVideoTranscription(job.messageObject);
            }
            job.messageObject.messageOwner.voiceTranscription = text;
            job.messageObject.messageOwner.voiceTranscriptionOpen = true;
            job.messageObject.messageOwner.voiceTranscriptionFinal = true;
            job.messageObject.messageOwner.voiceTranscriptionId = job.syntheticId;

            MessagesStorage.getInstance(currentAccount).updateMessageVoiceTranscription(
                    job.dialogId,
                    job.messageId,
                    text,
                    job.messageObject.messageOwner
            );
            NotificationCenter.getInstance(currentAccount).postNotificationName(
                    NotificationCenter.voiceTranscriptionUpdate,
                    job.messageObject,
                    job.syntheticId,
                    text,
                    true,
                    true
            );
        });
    }

    private void finishWithFailure(@NonNull SttJob job, @NonNull String errorMessage, boolean suggestSettings) {
        activeJobs.remove(job.key);
        AndroidUtilities.runOnUIThread(() -> {
            if (job.cancelled.get()) {
                return;
            }
            if (job.messageObject.messageOwner != null
                    && !job.messageObject.messageOwner.voiceTranscriptionFinal
                    && TextUtils.isEmpty(job.messageObject.messageOwner.voiceTranscription)) {
                job.messageObject.messageOwner.voiceTranscription = null;
                job.messageObject.messageOwner.voiceTranscriptionOpen = false;
                job.messageObject.messageOwner.voiceTranscriptionFinal = false;
            }
            NotificationCenter.getInstance(currentAccount).postNotificationName(
                    NotificationCenter.voiceTranscriptionUpdate,
                    job.messageObject,
                    job.syntheticId,
                    job.messageObject.messageOwner != null ? job.messageObject.messageOwner.voiceTranscription : null,
                    job.messageObject.messageOwner != null && job.messageObject.messageOwner.voiceTranscriptionOpen,
                    true
            );

            BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
            if (lastFragment != null && lastFragment.getParentActivity() != null) {
                if (suggestSettings) {
                    BulletinFactory.of(lastFragment)
                            .createSimpleBulletin(
                                    R.raw.transcribe,
                                    errorMessage,
                                    LocaleController.getString(R.string.Settings),
                                    () -> lastFragment.presentFragment(new MirrlyTranslatorSettingsActivity())
                            )
                            .setDuration(Bulletin.DURATION_PROLONG)
                            .show();
                } else {
                    BulletinFactory.of(lastFragment).createErrorBulletin(errorMessage).show();
                }
            }
        });
    }

    @NonNull
    private static String resolveRecognitionLanguageTag() {
        String cfg = MirrlyConfig.getFreeSttLanguage();
        if (!TextUtils.isEmpty(cfg) && !"auto".equalsIgnoreCase(cfg)) {
            return toBcp47(cfg);
        }
        Locale locale = LocaleController.getInstance().getCurrentLocale();
        if (locale != null && !TextUtils.isEmpty(locale.getLanguage())) {
            return toBcp47(locale.getLanguage());
        }
        return "ru-RU";
    }

    @Nullable
    private static String getExplicitIso2SpeechLang() {
        String cfg = MirrlyConfig.getFreeSttLanguage();
        if (TextUtils.isEmpty(cfg) || "auto".equalsIgnoreCase(cfg)) {
            return null;
        }
        return MirrlyTranslatorEngine.normalizeLangCode(cfg);
    }

    @NonNull
    private static String toBcp47(@NonNull String code) {
        String norm = MirrlyTranslatorEngine.normalizeLangCode(code);
        switch (norm) {
            case "ru": return "ru-RU";
            case "en": return "en-US";
            case "fa": return "fa-IR";
            case "uk": return "uk-UA";
            case "de": return "de-DE";
            case "fr": return "fr-FR";
            case "es": return "es-ES";
            case "it": return "it-IT";
            case "pt": return "pt-BR";
            case "tr": return "tr-TR";
            case "ar": return "ar-SA";
            case "zh-cn": return "zh-CN";
            case "zh-tw": return "zh-TW";
            case "ja": return "ja-JP";
            case "ko": return "ko-KR";
            default:
                return norm;
        }
    }

    private static void writeFormField(@NonNull OutputStream out, @NonNull String boundary, @NonNull String name, @NonNull String value) throws IOException {
        String part = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n";
        out.write(part.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFilePart(
            @NonNull OutputStream out,
            @NonNull String boundary,
            @NonNull String fieldName,
            @NonNull String fileName,
            @NonNull String mimeType,
            @NonNull File file
    ) throws IOException {
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: " + mimeType + "\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    @NonNull
    private static byte[] readFileBytes(@NonNull File file) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream((int) Math.min(file.length(), MAX_INLINE_AUDIO_BYTES));
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
        }
        return bos.toByteArray();
    }

    @NonNull
    private static String readResponseBody(@NonNull HttpURLConnection conn, int code) throws IOException {
        InputStream stream = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
        if (stream == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buf = new char[2048];
            int n;
            while ((n = reader.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
        }
        return sb.toString();
    }
}
