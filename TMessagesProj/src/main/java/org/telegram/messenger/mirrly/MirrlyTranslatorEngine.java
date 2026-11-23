package org.telegram.messenger.mirrly;

import android.text.TextUtils;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LanguageDetector;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.TranslateAlert2;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Multi-provider translation aggregator supporting keyless public engines (Google GTX,
 * Google Clients5, Lingva, MyMemory), self-hosted LibreTranslate, and optional BYOK keys
 * (DeepL Free/Pro, Yandex Cloud, Gemini) with automatic rate-limit failover and chunking.
 */
public final class MirrlyTranslatorEngine {

    private static final int CONNECT_TIMEOUT_MS = 12_000;
    private static final int READ_TIMEOUT_MS = 24_000;
    private static final int CACHE_SIZE = 96;

    private static final String[] LINGVA_MIRRORS = new String[]{
            "https://lingva.ml",
            "https://lingva.garudalinux.org",
            "https://lingva.lunar.icu"
    };

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "MirrlyTranslator");
        t.setDaemon(true);
        return t;
    });

    private static final LruCache<String, TranslationResult> MEMORY_CACHE = new LruCache<>(CACHE_SIZE);

    private MirrlyTranslatorEngine() {
    }

    public static final class CancelToken {
        private volatile boolean cancelled;

        public void cancel() {
            cancelled = true;
        }

        public boolean isCancelled() {
            return cancelled;
        }
    }

    public static final class TranslationResult {
        public final boolean success;
        @Nullable
        public final String translatedText;
        @Nullable
        public final String detectedSourceLanguage;
        public final int providerUsed;
        @NonNull
        public final String providerLabel;
        public final long latencyMs;
        public final boolean rateLimited;
        @Nullable
        public final String errorMessage;

        private TranslationResult(
                boolean success,
                @Nullable String translatedText,
                @Nullable String detectedSourceLanguage,
                int providerUsed,
                @NonNull String providerLabel,
                long latencyMs,
                boolean rateLimited,
                @Nullable String errorMessage
        ) {
            this.success = success;
            this.translatedText = translatedText;
            this.detectedSourceLanguage = detectedSourceLanguage;
            this.providerUsed = providerUsed;
            this.providerLabel = providerLabel;
            this.latencyMs = latencyMs;
            this.rateLimited = rateLimited;
            this.errorMessage = errorMessage;
        }

        static TranslationResult ok(String text, @Nullable String detectedSrc, int provider, String label, long latencyMs) {
            return new TranslationResult(true, text, detectedSrc, provider, label, latencyMs, false, null);
        }

        static TranslationResult fail(int provider, String label, long latencyMs, boolean rateLimited, String err) {
            return new TranslationResult(false, null, null, provider, label, latencyMs, rateLimited, err);
        }
    }

    public static void clearCache() {
        synchronized (MEMORY_CACHE) {
            MEMORY_CACHE.evictAll();
        }
    }

    @NonNull
    public static String getProviderShortName(int providerId) {
        switch (providerId) {
            case MirrlyConfig.TRANSLATOR_PROVIDER_GOOGLE:
                return "Google";
            case MirrlyConfig.TRANSLATOR_PROVIDER_LINGVA:
                return "Lingva";
            case MirrlyConfig.TRANSLATOR_PROVIDER_MYMEMORY:
                return "MyMemory";
            case MirrlyConfig.TRANSLATOR_PROVIDER_LIBRE:
                return TextUtils.isEmpty(MirrlyConfig.getTranslatorCustomUrl())
                        ? LocaleController.getString(R.string.MirrlyTranslatorProviderAutoShort)
                        : "LibreTranslate";
            case MirrlyConfig.TRANSLATOR_PROVIDER_DEEPL:
                return TextUtils.isEmpty(MirrlyConfig.getTranslatorDeepLApiKey())
                        ? LocaleController.getString(R.string.MirrlyTranslatorProviderAutoShort)
                        : "DeepL";
            case MirrlyConfig.TRANSLATOR_PROVIDER_YANDEX:
                return TextUtils.isEmpty(MirrlyConfig.getTranslatorYandexApiKey())
                        ? LocaleController.getString(R.string.MirrlyTranslatorProviderAutoShort)
                        : "Yandex Cloud";
            case MirrlyConfig.TRANSLATOR_PROVIDER_GEMINI:
                return TextUtils.isEmpty(MirrlyConfig.getGeminiApiKey())
                        ? LocaleController.getString(R.string.MirrlyTranslatorProviderAutoShort)
                        : "Gemini";
            case MirrlyConfig.TRANSLATOR_PROVIDER_AUTO:
            default:
                return LocaleController.getString(R.string.MirrlyTranslatorProviderAutoShort);
        }
    }

    @NonNull
    public static String getProviderDisplayName(int providerId) {
        switch (providerId) {
            case MirrlyConfig.TRANSLATOR_PROVIDER_GOOGLE:
                return LocaleController.getString(R.string.MirrlyTranslatorProviderGoogle);
            case MirrlyConfig.TRANSLATOR_PROVIDER_LINGVA:
                return LocaleController.getString(R.string.MirrlyTranslatorProviderLingva);
            case MirrlyConfig.TRANSLATOR_PROVIDER_MYMEMORY:
                return LocaleController.getString(R.string.MirrlyTranslatorProviderMyMemory);
            case MirrlyConfig.TRANSLATOR_PROVIDER_LIBRE:
                return TextUtils.isEmpty(MirrlyConfig.getTranslatorCustomUrl())
                        ? LocaleController.getString(R.string.MirrlyTranslatorProviderAuto)
                        : LocaleController.getString(R.string.MirrlyTranslatorProviderLibre);
            case MirrlyConfig.TRANSLATOR_PROVIDER_DEEPL:
                return TextUtils.isEmpty(MirrlyConfig.getTranslatorDeepLApiKey())
                        ? LocaleController.getString(R.string.MirrlyTranslatorProviderAuto)
                        : LocaleController.getString(R.string.MirrlyTranslatorProviderDeepL);
            case MirrlyConfig.TRANSLATOR_PROVIDER_YANDEX:
                return TextUtils.isEmpty(MirrlyConfig.getTranslatorYandexApiKey())
                        ? LocaleController.getString(R.string.MirrlyTranslatorProviderAuto)
                        : LocaleController.getString(R.string.MirrlyTranslatorProviderYandex);
            case MirrlyConfig.TRANSLATOR_PROVIDER_GEMINI:
                return TextUtils.isEmpty(MirrlyConfig.getGeminiApiKey())
                        ? LocaleController.getString(R.string.MirrlyTranslatorProviderAuto)
                        : LocaleController.getString(R.string.MirrlyTranslatorProviderGemini);
            case MirrlyConfig.TRANSLATOR_PROVIDER_AUTO:
            default:
                return LocaleController.getString(R.string.MirrlyTranslatorProviderAuto);
        }
    }

    @NonNull
    public static String getEffectiveDefaultTargetLanguage() {
        String configured = MirrlyConfig.getTranslatorTargetLang();
        if (!TextUtils.isEmpty(configured)) {
            return normalizeLangCode(configured);
        }
        String globalTo = TranslateAlert2.getToLanguage();
        if (!TextUtils.isEmpty(globalTo)) {
            return normalizeLangCode(globalTo);
        }
        String appLang = LocaleController.getInstance().getCurrentLocale().getLanguage();
        return TextUtils.isEmpty(appLang) ? "en" : normalizeLangCode(appLang);
    }

    @NonNull
    public static String resolveTargetLanguageForChat(long dialogId, @Nullable String detectedSourceLang) {
        String remembered = dialogId != 0 ? MirrlyConfig.getChatTranslateToLang(dialogId) : "";
        String baseTarget = !TextUtils.isEmpty(remembered) ? normalizeLangCode(remembered) : getEffectiveDefaultTargetLanguage();
        String normSource = normalizeLangCode(detectedSourceLang);
        if (!TextUtils.isEmpty(normSource) && !"auto".equals(normSource) && !"und".equals(normSource) && normSource.equals(baseTarget)) {
            if (!"en".equals(baseTarget)) {
                return "en";
            }
            String appLang = normalizeLangCode(LocaleController.getInstance().getCurrentLocale().getLanguage());
            if (!TextUtils.isEmpty(appLang) && !"en".equals(appLang) && !"und".equals(appLang)) {
                return appLang;
            }
            return "ru";
        }
        return baseTarget;
    }

    public static void detectLanguageAsync(@Nullable String text, @NonNull Utilities.Callback<String> callback) {
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(text.trim())) {
            AndroidUtilities.runOnUIThread(() -> callback.run("und"));
            return;
        }
        String sample = text.length() > 600 ? text.substring(0, 600) : text;
        if (LanguageDetector.hasSupport()) {
            LanguageDetector.detectLanguage(sample,
                    lang -> {
                        String normalized = normalizeLangCode(lang);
                        if (TextUtils.isEmpty(normalized) || "und".equals(normalized)) {
                            normalized = detectScriptFallback(sample);
                        }
                        String resultLang = normalized;
                        AndroidUtilities.runOnUIThread(() -> callback.run(resultLang));
                    },
                    err -> {
                        String fallback = detectScriptFallback(sample);
                        AndroidUtilities.runOnUIThread(() -> callback.run(fallback));
                    });
        } else {
            String fallback = detectScriptFallback(sample);
            AndroidUtilities.runOnUIThread(() -> callback.run(fallback));
        }
    }

    @NonNull
    public static String detectLanguageSync(@Nullable String text) {
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(text.trim())) {
            return "und";
        }
        String sample = text.length() > 600 ? text.substring(0, 600) : text;
        if (LanguageDetector.hasSupport()) {
            final AtomicReference<String> ref = new AtomicReference<>(null);
            final CountDownLatch latch = new CountDownLatch(1);
            AndroidUtilities.runOnUIThread(() -> LanguageDetector.detectLanguage(
                    sample,
                    lang -> {
                        ref.set(lang);
                        latch.countDown();
                    },
                    err -> latch.countDown()
            ));
            try {
                latch.await(900, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignore) {
                Thread.currentThread().interrupt();
            }
            String detected = normalizeLangCode(ref.get());
            if (!TextUtils.isEmpty(detected) && !"und".equals(detected)) {
                return detected;
            }
        }
        return detectScriptFallback(sample);
    }

    @NonNull
    public static String detectScriptFallback(@Nullable String text) {
        if (TextUtils.isEmpty(text)) {
            return "und";
        }
        int cyrillic = 0;
        int persianSpecific = 0;
        int arabic = 0;
        int cjk = 0;
        int hangul = 0;
        int hiraganaKatakana = 0;
        int latin = 0;
        for (int i = 0, n = Math.min(text.length(), 400); i < n; i++) {
            char c = text.charAt(i);
            if (c >= '\u0400' && c <= '\u04FF') {
                cyrillic++;
            } else if (c == '\u067E' || c == '\u0686' || c == '\u0698' || c == '\u06AF' || c == '\u06A9' || c == '\u06CC') {
                persianSpecific++;
                arabic++;
            } else if (c >= '\u0600' && c <= '\u06FF') {
                arabic++;
            } else if (c >= '\u4E00' && c <= '\u9FFF') {
                cjk++;
            } else if (c >= '\uAC00' && c <= '\uD7AF') {
                hangul++;
            } else if ((c >= '\u3040' && c <= '\u30FF')) {
                hiraganaKatakana++;
            } else if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                latin++;
            }
        }
        if (cyrillic >= Math.max(2, latin)) return "ru";
        if (persianSpecific > 0 || (arabic >= 2 && "fa".equalsIgnoreCase(LocaleController.getInstance().getCurrentLocale().getLanguage()))) return "fa";
        if (arabic >= Math.max(2, latin)) return "ar";
        if (hangul >= 2) return "ko";
        if (hiraganaKatakana >= 2) return "ja";
        if (cjk >= 2) return "zh";
        if (latin >= 2) return "en";
        return "und";
    }

    public static void translateAsync(
            @Nullable String text,
            @Nullable String sourceLang,
            @NonNull String targetLang,
            int providerOverride,
            @Nullable CancelToken cancelToken,
            @NonNull Utilities.Callback<TranslationResult> callback
    ) {
        if (TextUtils.isEmpty(text)) {
            AndroidUtilities.runOnUIThread(() -> callback.run(
                    TranslationResult.fail(providerOverride, getProviderShortName(providerOverride), 0L, false, "Empty text")
            ));
            return;
        }
        EXECUTOR.execute(() -> {
            if (cancelToken != null && cancelToken.isCancelled()) {
                return;
            }
            TranslationResult res = translateSync(text, sourceLang, targetLang, providerOverride, cancelToken);
            if (cancelToken != null && cancelToken.isCancelled()) {
                return;
            }
            AndroidUtilities.runOnUIThread(() -> callback.run(res));
        });
    }

    @NonNull
    public static TranslationResult translateSync(
            @Nullable String rawText,
            @Nullable String sourceLang,
            @NonNull String targetLang,
            int providerOverride,
            @Nullable CancelToken cancelToken
    ) {
        final long startMs = System.currentTimeMillis();
        if (TextUtils.isEmpty(rawText)) {
            return TranslationResult.fail(providerOverride, getProviderShortName(providerOverride), 0L, false, "Empty text");
        }
        final int effectiveProvider = providerOverride < 0 ? MirrlyConfig.getTranslatorProvider() : providerOverride;
        final String normTarget = TextUtils.isEmpty(targetLang) ? getEffectiveDefaultTargetLanguage() : normalizeLangCode(targetLang);
        final String normSource = TextUtils.isEmpty(sourceLang) || "und".equalsIgnoreCase(sourceLang) ? "auto" : normalizeLangCode(sourceLang);

        final String cacheKey = effectiveProvider + "|" + normSource + "|" + normTarget + "|" + rawText.hashCode() + "|" + rawText.length();
        synchronized (MEMORY_CACHE) {
            TranslationResult cached = MEMORY_CACHE.get(cacheKey);
            if (cached != null && cached.success) {
                return cached;
            }
        }

        List<Integer> cascade = buildProviderPlan(effectiveProvider);
        boolean anyRateLimit = false;
        String lastError = null;
        for (int p : cascade) {
            if (cancelToken != null && cancelToken.isCancelled()) {
                return TranslationResult.fail(p, getProviderShortName(p), System.currentTimeMillis() - startMs, false, "Cancelled");
            }
            TranslationResult res = executeProvider(p, rawText, normSource, normTarget, cancelToken);
            long txBytes = rawText.length() * 2L + (res.translatedText != null ? res.translatedText.length() * 2L : 0L);
            MirrlyPowerTelemetry.recordTranslatorWork(txBytes, res.latencyMs);
            if (res.success && !TextUtils.isEmpty(res.translatedText)) {
                synchronized (MEMORY_CACHE) {
                    MEMORY_CACHE.put(cacheKey, res);
                }
                return res;
            }
            anyRateLimit |= res.rateLimited;
            if (!TextUtils.isEmpty(res.errorMessage)) {
                lastError = res.errorMessage;
            }
        }

        return TranslationResult.fail(
                effectiveProvider,
                getProviderShortName(effectiveProvider),
                System.currentTimeMillis() - startMs,
                anyRateLimit,
                lastError != null ? lastError : "Translation failed"
        );
    }

    @NonNull
    private static List<Integer> buildProviderPlan(int requestedProvider) {
        if ((requestedProvider == MirrlyConfig.TRANSLATOR_PROVIDER_LIBRE && TextUtils.isEmpty(MirrlyConfig.getTranslatorCustomUrl()))
                || (requestedProvider == MirrlyConfig.TRANSLATOR_PROVIDER_DEEPL && TextUtils.isEmpty(MirrlyConfig.getTranslatorDeepLApiKey()))
                || (requestedProvider == MirrlyConfig.TRANSLATOR_PROVIDER_YANDEX && TextUtils.isEmpty(MirrlyConfig.getTranslatorYandexApiKey()))
                || (requestedProvider == MirrlyConfig.TRANSLATOR_PROVIDER_GEMINI && TextUtils.isEmpty(MirrlyConfig.getGeminiApiKey()))) {
            requestedProvider = MirrlyConfig.TRANSLATOR_PROVIDER_AUTO;
        }
        List<Integer> plan = new ArrayList<>();
        if (requestedProvider != MirrlyConfig.TRANSLATOR_PROVIDER_AUTO) {
            plan.add(requestedProvider);
            return plan;
        }
        // Keyless free aggregators first to avoid consuming optional personal API quotas unless configured/needed
        plan.add(MirrlyConfig.TRANSLATOR_PROVIDER_GOOGLE);
        plan.add(MirrlyConfig.TRANSLATOR_PROVIDER_LINGVA);
        plan.add(MirrlyConfig.TRANSLATOR_PROVIDER_MYMEMORY);
        String customUrl = MirrlyConfig.getTranslatorCustomUrl();
        if (!TextUtils.isEmpty(customUrl) && customUrl.toLowerCase(Locale.ROOT).contains("translate")) {
            plan.add(MirrlyConfig.TRANSLATOR_PROVIDER_LIBRE);
        }
        if (!TextUtils.isEmpty(MirrlyConfig.getTranslatorDeepLApiKey())) {
            plan.add(MirrlyConfig.TRANSLATOR_PROVIDER_DEEPL);
        }
        if (!TextUtils.isEmpty(MirrlyConfig.getTranslatorYandexApiKey())) {
            plan.add(MirrlyConfig.TRANSLATOR_PROVIDER_YANDEX);
        }
        if (!TextUtils.isEmpty(MirrlyConfig.getGeminiApiKey())) {
            plan.add(MirrlyConfig.TRANSLATOR_PROVIDER_GEMINI);
        }
        return plan;
    }

    @NonNull
    private static TranslationResult executeProvider(
            int providerId,
            @NonNull String text,
            @NonNull String srcLang,
            @NonNull String dstLang,
            @Nullable CancelToken cancelToken
    ) {
        final long start = System.currentTimeMillis();
        switch (providerId) {
            case MirrlyConfig.TRANSLATOR_PROVIDER_GOOGLE: {
                TranslationResult gtx = translateGoogleGtx(text, srcLang, dstLang, start, cancelToken);
                if (gtx.success) {
                    return gtx;
                }
                TranslationResult c5 = translateGoogleClients5(text, srcLang, dstLang, start, cancelToken);
                return c5.success ? c5 : gtx;
            }
            case MirrlyConfig.TRANSLATOR_PROVIDER_LINGVA:
                return translateLingva(text, srcLang, dstLang, start, cancelToken);
            case MirrlyConfig.TRANSLATOR_PROVIDER_MYMEMORY:
                return translateMyMemory(text, srcLang, dstLang, start, cancelToken);
            case MirrlyConfig.TRANSLATOR_PROVIDER_LIBRE:
                return translateLibre(text, srcLang, dstLang, start, cancelToken);
            case MirrlyConfig.TRANSLATOR_PROVIDER_DEEPL:
                return translateDeepL(text, srcLang, dstLang, start, cancelToken);
            case MirrlyConfig.TRANSLATOR_PROVIDER_YANDEX:
                return translateYandexCloud(text, srcLang, dstLang, start, cancelToken);
            case MirrlyConfig.TRANSLATOR_PROVIDER_GEMINI:
                return translateGemini(text, srcLang, dstLang, start, cancelToken);
            default:
                return translateGoogleGtx(text, srcLang, dstLang, start, cancelToken);
        }
    }

    @NonNull
    private static TranslationResult translateGoogleGtx(
            @NonNull String text,
            @NonNull String srcLang,
            @NonNull String dstLang,
            long start,
            @Nullable CancelToken cancelToken
    ) {
        List<String> chunks = splitRawText(text, 2800);
        StringBuilder out = new StringBuilder();
        String detected = null;
        for (int i = 0; i < chunks.size(); i++) {
            if (cancelToken != null && cancelToken.isCancelled()) {
                return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_GOOGLE, "Google", elapsed(start), false, "Cancelled");
            }
            try {
                String q = URLEncoder.encode(chunks.get(i), "UTF-8");
                String sl = "auto".equals(srcLang) ? "auto" : toGoogleLangCode(srcLang);
                String tl = toGoogleLangCode(dstLang);
                String uri = "https://translate.googleapis.com/translate_a/single?client=gtx&sl="
                        + URLEncoder.encode(sl, "UTF-8")
                        + "&tl=" + URLEncoder.encode(tl, "UTF-8")
                        + "&dt=t";
                HttpResult http = executePostForm(uri, "q=" + q, null);
                if (http.code == 429) {
                    return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_GOOGLE, "Google", elapsed(start), true, "HTTP 429");
                }
                if (http.code < 200 || http.code >= 300 || TextUtils.isEmpty(http.body)) {
                    return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_GOOGLE, "Google", elapsed(start), http.code == 403, "HTTP " + http.code);
                }
                JSONArray array = new JSONArray(new JSONTokener(http.body));
                JSONArray lines = array.optJSONArray(0);
                if (lines == null) {
                    return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_GOOGLE, "Google", elapsed(start), false, "Invalid JSON");
                }
                for (int j = 0; j < lines.length(); j++) {
                    JSONArray seg = lines.optJSONArray(j);
                    if (seg != null && !seg.isNull(0)) {
                        out.append(seg.optString(0));
                    }
                }
                if (detected == null && array.length() > 2 && !array.isNull(2)) {
                    detected = normalizeLangCode(array.optString(2));
                }
            } catch (Exception e) {
                FileLog.e(e, false);
                return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_GOOGLE, "Google", elapsed(start), false, e.getMessage());
            }
        }
        return TranslationResult.ok(out.toString(), detected, MirrlyConfig.TRANSLATOR_PROVIDER_GOOGLE, "Google", elapsed(start));
    }

    @NonNull
    private static TranslationResult translateGoogleClients5(
            @NonNull String text,
            @NonNull String srcLang,
            @NonNull String dstLang,
            long start,
            @Nullable CancelToken cancelToken
    ) {
        List<String> chunks = splitRawText(text, 2400);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            if (cancelToken != null && cancelToken.isCancelled()) {
                return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_GOOGLE, "Google Mirror", elapsed(start), false, "Cancelled");
            }
            try {
                String q = URLEncoder.encode(chunks.get(i), "UTF-8");
                String sl = "auto".equals(srcLang) ? "auto" : toGoogleLangCode(srcLang);
                String tl = toGoogleLangCode(dstLang);
                String uri = "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl="
                        + URLEncoder.encode(sl, "UTF-8")
                        + "&tl=" + URLEncoder.encode(tl, "UTF-8");
                HttpResult http = executePostForm(uri, "q=" + q, null);
                if (http.code == 429) {
                    return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_GOOGLE, "Google Mirror", elapsed(start), true, "HTTP 429");
                }
                if (http.code < 200 || http.code >= 300 || TextUtils.isEmpty(http.body)) {
                    return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_GOOGLE, "Google Mirror", elapsed(start), http.code == 403, "HTTP " + http.code);
                }
                Object parsed = new JSONTokener(http.body).nextValue();
                if (parsed instanceof JSONArray) {
                    JSONArray arr = (JSONArray) parsed;
                    for (int j = 0; j < arr.length(); j++) {
                        Object item = arr.opt(j);
                        if (item instanceof String) {
                            out.append((String) item);
                        } else if (item instanceof JSONArray) {
                            JSONArray sub = (JSONArray) item;
                            if (sub.length() > 0 && !sub.isNull(0)) {
                                out.append(sub.optString(0));
                            }
                        }
                    }
                } else if (parsed instanceof JSONObject) {
                    JSONArray sentences = ((JSONObject) parsed).optJSONArray("sentences");
                    if (sentences != null) {
                        for (int j = 0; j < sentences.length(); j++) {
                            JSONObject s = sentences.optJSONObject(j);
                            if (s != null) {
                                out.append(s.optString("trans"));
                            }
                        }
                    }
                } else {
                    return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_GOOGLE, "Google Mirror", elapsed(start), false, "Unsupported format");
                }
            } catch (Exception e) {
                FileLog.e(e, false);
                return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_GOOGLE, "Google Mirror", elapsed(start), false, e.getMessage());
            }
        }
        if (out.length() == 0) {
            return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_GOOGLE, "Google Mirror", elapsed(start), false, "Empty output");
        }
        return TranslationResult.ok(out.toString(), null, MirrlyConfig.TRANSLATOR_PROVIDER_GOOGLE, "Google", elapsed(start));
    }

    @NonNull
    private static TranslationResult translateLingva(
            @NonNull String text,
            @NonNull String srcLang,
            @NonNull String dstLang,
            long start,
            @Nullable CancelToken cancelToken
    ) {
        List<String> hosts = new ArrayList<>();
        String custom = MirrlyConfig.getTranslatorCustomUrl();
        if (!TextUtils.isEmpty(custom) && !custom.toLowerCase(Locale.ROOT).contains("/translate")) {
            String normalized = normalizeBaseUrl(custom);
            if (isSafeEndpointUrl(normalized)) {
                hosts.add(normalized);
            }
        }
        for (String mirror : LINGVA_MIRRORS) {
            if (!hosts.contains(mirror)) {
                hosts.add(mirror);
            }
        }
        String sl = "auto".equals(srcLang) ? "auto" : toGoogleLangCode(srcLang);
        String tl = toGoogleLangCode(dstLang);
        List<String> chunks = splitRawText(text, 1200);
        String lastErr = "Lingva unavailable";
        boolean rateLimited = false;

        for (String host : hosts) {
            StringBuilder combined = new StringBuilder();
            String detected = null;
            boolean hostOk = true;
            for (String chunk : chunks) {
                if (cancelToken != null && cancelToken.isCancelled()) {
                    return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_LINGVA, "Lingva", elapsed(start), false, "Cancelled");
                }
                try {
                    String safeText = chunk.replace("/", " ");
                    String url = host + "/api/v1/" + URLEncoder.encode(sl, "UTF-8") + "/"
                            + URLEncoder.encode(tl, "UTF-8") + "/" + encodePathSegment(safeText);
                    HttpResult http = executeGet(url, null);
                    if (http.code == 429) {
                        rateLimited = true;
                    }
                    if (http.code < 200 || http.code >= 300 || TextUtils.isEmpty(http.body)) {
                        lastErr = "HTTP " + http.code;
                        hostOk = false;
                        break;
                    }
                    JSONObject obj = new JSONObject(http.body);
                    String tr = obj.optString("translation", null);
                    if (TextUtils.isEmpty(tr)) {
                        lastErr = obj.optString("error", "Empty Lingva response");
                        hostOk = false;
                        break;
                    }
                    combined.append(tr);
                    if (detected == null) {
                        JSONObject info = obj.optJSONObject("info");
                        if (info != null) {
                            String det = info.optString("detectedSource", null);
                            if (!TextUtils.isEmpty(det)) {
                                detected = normalizeLangCode(det);
                            }
                        }
                    }
                } catch (Exception e) {
                    lastErr = e.getMessage();
                    hostOk = false;
                    break;
                }
            }
            if (hostOk && combined.length() > 0) {
                return TranslationResult.ok(combined.toString(), detected, MirrlyConfig.TRANSLATOR_PROVIDER_LINGVA, "Lingva", elapsed(start));
            }
        }
        return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_LINGVA, "Lingva", elapsed(start), rateLimited, lastErr);
    }

    @NonNull
    private static TranslationResult translateMyMemory(
            @NonNull String text,
            @NonNull String srcLang,
            @NonNull String dstLang,
            long start,
            @Nullable CancelToken cancelToken
    ) {
        String effectiveSrc = srcLang;
        if ("auto".equals(effectiveSrc) || "und".equals(effectiveSrc)) {
            effectiveSrc = detectLanguageSync(text);
            if ("auto".equals(effectiveSrc) || "und".equals(effectiveSrc)) {
                effectiveSrc = "en";
            }
        }
        String effectiveDst = toMyMemoryCode(dstLang);
        effectiveSrc = toMyMemoryCode(effectiveSrc);
        if (effectiveSrc.equalsIgnoreCase(effectiveDst)) {
            effectiveSrc = "en".equalsIgnoreCase(effectiveDst) ? "ru" : "en";
        }
        List<String> chunks = splitRawText(text, 440);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            if (cancelToken != null && cancelToken.isCancelled()) {
                return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_MYMEMORY, "MyMemory", elapsed(start), false, "Cancelled");
            }
            try {
                String url = "https://api.mymemory.translated.net/get?q=" + URLEncoder.encode(chunks.get(i), "UTF-8")
                        + "&langpair=" + URLEncoder.encode(effectiveSrc + "|" + effectiveDst, "UTF-8");
                HttpResult http = executeGet(url, null);
                if (http.code == 429) {
                    return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_MYMEMORY, "MyMemory", elapsed(start), true, "HTTP 429");
                }
                if (http.code < 200 || http.code >= 300 || TextUtils.isEmpty(http.body)) {
                    return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_MYMEMORY, "MyMemory", elapsed(start), false, "HTTP " + http.code);
                }
                JSONObject root = new JSONObject(http.body);
                int status = root.optInt("responseStatus", 200);
                if (status == 429) {
                    return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_MYMEMORY, "MyMemory", elapsed(start), true, root.optString("responseDetails", "Quota reached"));
                }
                if (status != 200) {
                    return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_MYMEMORY, "MyMemory", elapsed(start), false, root.optString("responseDetails", "Error " + status));
                }
                JSONObject data = root.optJSONObject("responseData");
                if (data == null) {
                    return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_MYMEMORY, "MyMemory", elapsed(start), false, "No responseData");
                }
                String translated = data.optString("translatedText", "");
                if (translated.startsWith("MYMEMORY WARNING:")) {
                    return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_MYMEMORY, "MyMemory", elapsed(start), true, translated);
                }
                out.append(translated);
            } catch (Exception e) {
                FileLog.e(e, false);
                return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_MYMEMORY, "MyMemory", elapsed(start), false, e.getMessage());
            }
        }
        return TranslationResult.ok(out.toString(), normalizeLangCode(effectiveSrc), MirrlyConfig.TRANSLATOR_PROVIDER_MYMEMORY, "MyMemory", elapsed(start));
    }

    @NonNull
    private static TranslationResult translateLibre(
            @NonNull String text,
            @NonNull String srcLang,
            @NonNull String dstLang,
            long start,
            @Nullable CancelToken cancelToken
    ) {
        String custom = normalizeBaseUrl(MirrlyConfig.getTranslatorCustomUrl());
        if (TextUtils.isEmpty(custom)) {
            return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_LIBRE, "LibreTranslate", elapsed(start), false, "Set LibreTranslate URL in settings");
        }
        String endpoint = custom.endsWith("/translate") ? custom : custom + "/translate";
        if (!isSafeEndpointUrl(endpoint)) {
            return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_LIBRE, "LibreTranslate", elapsed(start), false, "HTTPS required for remote hosts");
        }
        try {
            JSONObject payload = new JSONObject();
            payload.put("q", text);
            payload.put("source", "auto".equals(srcLang) ? "auto" : toIso2(srcLang));
            payload.put("target", toIso2(dstLang));
            payload.put("format", "text");
            HttpResult http = executePostJson(endpoint, payload.toString(), null);
            if (http.code == 429) {
                return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_LIBRE, "LibreTranslate", elapsed(start), true, "HTTP 429");
            }
            if (http.code < 200 || http.code >= 300 || TextUtils.isEmpty(http.body)) {
                return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_LIBRE, "LibreTranslate", elapsed(start), false, "HTTP " + http.code);
            }
            JSONObject obj = new JSONObject(http.body);
            String tr = obj.optString("translatedText", null);
            if (TextUtils.isEmpty(tr)) {
                return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_LIBRE, "LibreTranslate", elapsed(start), false, obj.optString("error", "Empty response"));
            }
            String detected = null;
            JSONObject detObj = obj.optJSONObject("detectedLanguage");
            if (detObj != null) {
                detected = normalizeLangCode(detObj.optString("language", null));
            }
            return TranslationResult.ok(tr, detected, MirrlyConfig.TRANSLATOR_PROVIDER_LIBRE, "LibreTranslate", elapsed(start));
        } catch (Exception e) {
            FileLog.e(e, false);
            return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_LIBRE, "LibreTranslate", elapsed(start), false, e.getMessage());
        }
    }

    @NonNull
    private static TranslationResult translateDeepL(
            @NonNull String text,
            @NonNull String srcLang,
            @NonNull String dstLang,
            long start,
            @Nullable CancelToken cancelToken
    ) {
        String apiKey = MirrlyConfig.getTranslatorDeepLApiKey();
        if (TextUtils.isEmpty(apiKey)) {
            return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_DEEPL, "DeepL", elapsed(start), false, "DeepL API key not set");
        }
        String endpoint = apiKey.endsWith(":fx")
                ? "https://api-free.deepl.com/v2/translate"
                : "https://api.deepl.com/v2/translate";
        try {
            JSONObject body = new JSONObject();
            JSONArray texts = new JSONArray();
            texts.put(text);
            body.put("text", texts);
            body.put("target_lang", toDeepLTargetCode(dstLang));
            if (!"auto".equals(srcLang) && !"und".equals(srcLang)) {
                String src = toDeepLSourceCode(srcLang);
                if (!TextUtils.isEmpty(src)) {
                    body.put("source_lang", src);
                }
            }
            HttpResult http = executePostJson(endpoint, body.toString(), new String[][]{
                    {"Authorization", "DeepL-Auth-Key " + apiKey}
            });
            if (http.code == 429 || http.code == 456) {
                return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_DEEPL, "DeepL", elapsed(start), true, "DeepL quota / rate limit (" + http.code + ")");
            }
            if (http.code < 200 || http.code >= 300 || TextUtils.isEmpty(http.body)) {
                return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_DEEPL, "DeepL", elapsed(start), false, "HTTP " + http.code);
            }
            JSONObject root = new JSONObject(http.body);
            JSONArray translations = root.optJSONArray("translations");
            if (translations == null || translations.length() == 0) {
                return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_DEEPL, "DeepL", elapsed(start), false, "Empty translations array");
            }
            JSONObject first = translations.optJSONObject(0);
            String tr = first != null ? first.optString("text", null) : null;
            if (TextUtils.isEmpty(tr)) {
                return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_DEEPL, "DeepL", elapsed(start), false, "Empty DeepL output");
            }
            String detected = normalizeLangCode(first.optString("detected_source_language", null));
            return TranslationResult.ok(tr, detected, MirrlyConfig.TRANSLATOR_PROVIDER_DEEPL, "DeepL", elapsed(start));
        } catch (Exception e) {
            FileLog.e(e, false);
            return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_DEEPL, "DeepL", elapsed(start), false, e.getMessage());
        }
    }

    @NonNull
    private static TranslationResult translateYandexCloud(
            @NonNull String text,
            @NonNull String srcLang,
            @NonNull String dstLang,
            long start,
            @Nullable CancelToken cancelToken
    ) {
        String apiKey = MirrlyConfig.getTranslatorYandexApiKey();
        if (TextUtils.isEmpty(apiKey)) {
            return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_YANDEX, "Yandex Cloud", elapsed(start), false, "Yandex Cloud API key not set");
        }
        try {
            JSONObject body = new JSONObject();
            body.put("targetLanguageCode", toIso2(dstLang));
            if (!"auto".equals(srcLang) && !"und".equals(srcLang)) {
                body.put("sourceLanguageCode", toIso2(srcLang));
            }
            String folderId = MirrlyConfig.getTranslatorYandexFolderId();
            if (!TextUtils.isEmpty(folderId)) {
                body.put("folderId", folderId);
            }
            JSONArray texts = new JSONArray();
            texts.put(text);
            body.put("texts", texts);

            HttpResult http = executePostJson(
                    "https://translate.api.cloud.yandex.net/translate/v2/translate",
                    body.toString(),
                    new String[][]{{"Authorization", "Api-Key " + apiKey}}
            );
            if (http.code == 429) {
                return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_YANDEX, "Yandex Cloud", elapsed(start), true, "HTTP 429");
            }
            if (http.code < 200 || http.code >= 300 || TextUtils.isEmpty(http.body)) {
                return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_YANDEX, "Yandex Cloud", elapsed(start), false, "HTTP " + http.code);
            }
            JSONObject root = new JSONObject(http.body);
            JSONArray translations = root.optJSONArray("translations");
            if (translations == null || translations.length() == 0) {
                return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_YANDEX, "Yandex Cloud", elapsed(start), false, "Empty response");
            }
            JSONObject first = translations.optJSONObject(0);
            String tr = first != null ? first.optString("text", null) : null;
            if (TextUtils.isEmpty(tr)) {
                return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_YANDEX, "Yandex Cloud", elapsed(start), false, "Empty text");
            }
            String detected = normalizeLangCode(first.optString("detectedLanguageCode", null));
            return TranslationResult.ok(tr, detected, MirrlyConfig.TRANSLATOR_PROVIDER_YANDEX, "Yandex Cloud", elapsed(start));
        } catch (Exception e) {
            FileLog.e(e, false);
            return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_YANDEX, "Yandex Cloud", elapsed(start), false, e.getMessage());
        }
    }

    @NonNull
    private static TranslationResult translateGemini(
            @NonNull String text,
            @NonNull String srcLang,
            @NonNull String dstLang,
            long start,
            @Nullable CancelToken cancelToken
    ) {
        String apiKey = MirrlyConfig.getGeminiApiKey();
        if (TextUtils.isEmpty(apiKey)) {
            return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_GEMINI, "Gemini", elapsed(start), false, "Gemini API key not set");
        }
        try {
            String targetDisplay = TranslateAlert2.languageNameCapital(dstLang);
            if (TextUtils.isEmpty(targetDisplay)) {
                targetDisplay = dstLang;
            }
            String srcHint = ("auto".equals(srcLang) || "und".equals(srcLang)) ? "auto-detected source language" : srcLang;
            String instruction = "Translate the user text from " + srcHint + " into " + targetDisplay + " (" + dstLang + "). "
                    + "Preserve line breaks, @mentions, URLs, and tone. Output ONLY the translated text without quotes or explanations.";

            JSONObject sysPart = new JSONObject().put("text", instruction);
            JSONObject sysInstruction = new JSONObject().put("parts", new JSONArray().put(sysPart));
            JSONObject userPart = new JSONObject().put("text", text);
            JSONObject content = new JSONObject().put("role", "user").put("parts", new JSONArray().put(userPart));
            JSONObject genCfg = new JSONObject().put("temperature", 0.1);
            JSONObject payload = new JSONObject()
                    .put("system_instruction", sysInstruction)
                    .put("contents", new JSONArray().put(content))
                    .put("generationConfig", genCfg);

            HttpResult http = executePostJson(
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
                    payload.toString(),
                    new String[][]{{"x-goog-api-key", apiKey}}
            );
            if (http.code == 429) {
                return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_GEMINI, "Gemini", elapsed(start), true, "HTTP 429");
            }
            if (http.code < 200 || http.code >= 300 || TextUtils.isEmpty(http.body)) {
                return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_GEMINI, "Gemini", elapsed(start), false, "HTTP " + http.code);
            }
            JSONObject root = new JSONObject(http.body);
            JSONArray candidates = root.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) {
                return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_GEMINI, "Gemini", elapsed(start), false, "No candidates");
            }
            JSONObject c0 = candidates.optJSONObject(0);
            JSONObject cContent = c0 != null ? c0.optJSONObject("content") : null;
            JSONArray parts = cContent != null ? cContent.optJSONArray("parts") : null;
            if (parts == null || parts.length() == 0) {
                return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_GEMINI, "Gemini", elapsed(start), false, "Empty output");
            }
            String out = parts.optJSONObject(0).optString("text", "").trim();
            if (TextUtils.isEmpty(out)) {
                return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_GEMINI, "Gemini", elapsed(start), false, "Empty output");
            }
            return TranslationResult.ok(out, null, MirrlyConfig.TRANSLATOR_PROVIDER_GEMINI, "Gemini", elapsed(start));
        } catch (Exception e) {
            FileLog.e(e, false);
            return TranslationResult.fail(MirrlyConfig.TRANSLATOR_PROVIDER_GEMINI, "Gemini", elapsed(start), false, e.getMessage());
        }
    }

    private static final class HttpResult {
        final int code;
        final String body;

        HttpResult(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    private static HttpResult executeGet(@NonNull String urlString, @Nullable String[][] headers) throws IOException {
        HttpURLConnection conn = openConnection(urlString, "GET", headers);
        try {
            return readHttpResponse(conn);
        } finally {
            conn.disconnect();
        }
    }

    private static HttpResult executePostForm(@NonNull String urlString, @NonNull String formBody, @Nullable String[][] headers) throws IOException {
        HttpURLConnection conn = openConnection(urlString, "POST", headers);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        conn.setDoOutput(true);
        try {
            byte[] bytes = formBody.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
            }
            return readHttpResponse(conn);
        } finally {
            conn.disconnect();
        }
    }

    private static HttpResult executePostJson(@NonNull String urlString, @NonNull String jsonBody, @Nullable String[][] headers) throws IOException {
        HttpURLConnection conn = openConnection(urlString, "POST", headers);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);
        try {
            byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
            }
            return readHttpResponse(conn);
        } finally {
            conn.disconnect();
        }
    }

    private static HttpURLConnection openConnection(@NonNull String urlString, @NonNull String method, @Nullable String[][] headers) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", TranslateAlert2.userAgents[(int) (Math.abs(System.nanoTime()) % TranslateAlert2.userAgents.length)]);
        conn.setRequestProperty("Accept", "application/json");
        if (headers != null) {
            for (String[] h : headers) {
                if (h != null && h.length == 2) {
                    conn.setRequestProperty(h[0], h[1]);
                }
            }
        }
        return conn;
    }

    private static HttpResult readHttpResponse(@NonNull HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        InputStream stream = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
        if (stream == null) {
            return new HttpResult(code, "");
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buf = new char[2048];
            int n;
            int total = 0;
            while ((n = reader.read(buf)) > 0) {
                sb.append(buf, 0, n);
                total += n;
                if (total > 1_000_000) {
                    break;
                }
            }
        }
        return new HttpResult(code, sb.toString());
    }

    public static boolean isSafeEndpointUrl(@Nullable String urlStr) {
        if (TextUtils.isEmpty(urlStr)) {
            return false;
        }
        try {
            URI uri = URI.create(urlStr.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (TextUtils.isEmpty(scheme) || TextUtils.isEmpty(host)) {
                return false;
            }
            if ("https".equalsIgnoreCase(scheme)) {
                return true;
            }
            if ("http".equalsIgnoreCase(scheme)) {
                if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host)) {
                    return true;
                }
                InetAddress addr = InetAddress.getByName(host);
                return addr.isLoopbackAddress() || addr.isSiteLocalAddress();
            }
        } catch (Exception ignore) {
        }
        return false;
    }

    @NonNull
    public static String normalizeBaseUrl(@Nullable String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    @NonNull
    private static List<String> splitRawText(@NonNull String text, int maxChars) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        int len = text.length();
        while (start < len) {
            int end = Math.min(start + maxChars, len);
            if (end < len) {
                int nl = text.lastIndexOf('\n', end);
                if (nl > start + maxChars / 3) {
                    end = nl + 1;
                } else {
                    int sp = text.lastIndexOf(' ', end);
                    if (sp > start + maxChars / 3) {
                        end = sp + 1;
                    }
                }
            }
            parts.add(text.substring(start, end));
            start = end;
        }
        return parts;
    }

    @NonNull
    private static String encodePathSegment(@NonNull String text) throws IOException {
        return URLEncoder.encode(text, "UTF-8")
                .replace("+", "%20")
                .replace("%7E", "~");
    }

    @NonNull
    public static String normalizeLangCode(@Nullable String code) {
        if (TextUtils.isEmpty(code)) {
            return "";
        }
        String c = code.trim().replace('_', '-').toLowerCase(Locale.ROOT);
        if ("nb".equals(c)) return "no";
        if ("iw".equals(c)) return "he";
        if ("in".equals(c)) return "id";
        if ("ji".equals(c)) return "yi";
        if (c.startsWith("zh-cn") || "zh".equals(c) || "zh-hans".equals(c)) return "zh-cn";
        if (c.startsWith("zh-tw") || "zh-hant".equals(c)) return "zh-tw";
        int dash = c.indexOf('-');
        if (dash > 0 && !"zh-cn".equals(c) && !"zh-tw".equals(c)) {
            return c.substring(0, dash);
        }
        return c;
    }

    @NonNull
    private static String toGoogleLangCode(@NonNull String code) {
        String norm = normalizeLangCode(code);
        if ("zh-cn".equals(norm)) return "zh-CN";
        if ("zh-tw".equals(norm)) return "zh-TW";
        return norm;
    }

    @NonNull
    private static String toMyMemoryCode(@NonNull String code) {
        String norm = normalizeLangCode(code);
        if ("zh-cn".equals(norm)) return "zh-CN";
        if ("zh-tw".equals(norm)) return "zh-TW";
        if ("he".equals(norm)) return "he";
        return norm;
    }

    @NonNull
    private static String toIso2(@NonNull String code) {
        String norm = normalizeLangCode(code);
        if (norm.startsWith("zh")) return "zh";
        return norm;
    }

    @NonNull
    private static String toDeepLTargetCode(@NonNull String code) {
        String norm = normalizeLangCode(code);
        if ("en".equals(norm)) return "EN-US";
        if ("pt".equals(norm)) return "PT-PT";
        if ("zh-cn".equals(norm)) return "ZH-HANS";
        if ("zh-tw".equals(norm)) return "ZH-HANT";
        return toIso2(norm).toUpperCase(Locale.ROOT);
    }

    @NonNull
    private static String toDeepLSourceCode(@NonNull String code) {
        return toIso2(code).toUpperCase(Locale.ROOT);
    }

    private static long elapsed(long start) {
        return Math.max(0L, System.currentTimeMillis() - start);
    }
}
