package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TranslateController;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.mirrly.MirrlyConfig;
import org.telegram.messenger.mirrly.MirrlyTranslatorEngine;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.MirrlyUiKit;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Interactive preview and target-language selector bottom sheet triggered by holding
 * the chat send button ("Translate & send").
 */
public class MirrlyTranslateSendSheet extends MirrlyUiKit.FrostedBottomSheet {

    private static final String[] POPULAR_LANG_CODES = new String[]{
            "en", "ru", "fa", "uk", "de", "fr", "es", "it", "pt", "tr",
            "ar", "zh-cn", "zh-tw", "ja", "ko", "hi", "id", "pl", "nl", "cs",
            "ro", "sv", "vi", "th", "he", "ka", "hy", "az", "uz", "kk",
            "be", "bg", "el", "fi", "da", "no", "hu", "sk", "sr", "hr",
            "ms", "bn", "ur"
    };

    public interface Delegate {
        void onSendTranslated(@NonNull String translatedText, boolean notify, int scheduleDate);

        void onApplyTranslatedToInput(@NonNull String translatedText);
    }

    private final long dialogId;
    private final String sourceText;
    private final boolean canSendWithoutSound;
    private final boolean canSchedule;
    @Nullable
    private final Delegate delegate;
    @Nullable
    private final Theme.ResourcesProvider sheetResourcesProvider;

    private String detectedSourceLang = "auto";
    private String targetLang;
    private int selectedProvider;
    @Nullable
    private MirrlyTranslatorEngine.CancelToken activeToken;
    private boolean isTranslating;
    private boolean hasValidTranslation;

    private final TextView providerChipView;
    private final TextView sourceLangChipView;
    private final TextView targetLangChipView;
    private final TextView statusLabelView;
    private final RadialProgressView statusSpinner;
    private final ImageView retryIconView;
    private final ImageView copyIconView;
    private final EditTextBoldCursor previewEditText;
    private final ButtonWithCounterView toInputButton;
    private final ButtonWithCounterView sendButton;
    private final LinearLayout rootLayout;

    public MirrlyTranslateSendSheet(
            @NonNull Context context,
            long dialogId,
            @NonNull CharSequence draftText,
            boolean canSendWithoutSound,
            boolean canSchedule,
            @Nullable Theme.ResourcesProvider resourcesProvider,
            @Nullable Delegate delegate
    ) {
        super(context, true, resourcesProvider);
        this.dialogId = dialogId;
        this.sourceText = draftText.toString();
        this.canSendWithoutSound = canSendWithoutSound;
        this.canSchedule = canSchedule;
        this.delegate = delegate;
        this.sheetResourcesProvider = resourcesProvider;
        this.selectedProvider = MirrlyConfig.getTranslatorProvider();
        this.targetLang = MirrlyTranslatorEngine.resolveTargetLanguageForChat(dialogId, null);

        setApplyTopPadding(false);
        setApplyBottomPadding(false);
        smoothKeyboardAnimationEnabled = true;
        if (getWindow() != null) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        ScrollView scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);

        rootLayout = new LinearLayout(context);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(dp(16), dp(22), dp(16), dp(16));
        scrollView.addView(rootLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        // 1. Header Row (Title + Tappable Provider Badge)
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        rootLayout.addView(headerRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        TextView titleView = new TextView(context);
        titleView.setText(LocaleController.getString(R.string.MirrlyTranslateSendTitle));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        headerRow.addView(titleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

        providerChipView = createPillTextView(context, true);
        providerChipView.setText(MirrlyTranslatorEngine.getProviderShortName(selectedProvider));
        providerChipView.setOnClickListener(v -> openProviderSelector());
        headerRow.addView(providerChipView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 30, Gravity.CENTER_VERTICAL, 8, 0, 0, 0));

        // 2. Language Pair Row
        LinearLayout langBar = new LinearLayout(context);
        langBar.setOrientation(LinearLayout.HORIZONTAL);
        langBar.setGravity(Gravity.CENTER_VERTICAL);
        langBar.setPadding(dp(12), dp(8), dp(12), dp(8));
        langBar.setBackground(Theme.createRoundRectDrawable(dp(12), getSurfaceCardColor()));
        rootLayout.addView(langBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        sourceLangChipView = new TextView(context);
        sourceLangChipView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
        sourceLangChipView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        sourceLangChipView.setSingleLine(true);
        sourceLangChipView.setEllipsize(TextUtils.TruncateAt.END);
        sourceLangChipView.setMaxWidth(dp(145));
        sourceLangChipView.setText(LocaleController.getString(R.string.MirrlyTranslatorDetecting));
        langBar.addView(sourceLangChipView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        TextView arrowView = new TextView(context);
        arrowView.setText(LocaleController.isRTL ? "←" : "→");
        arrowView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        arrowView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        arrowView.setPadding(dp(8), 0, dp(8), 0);
        langBar.addView(arrowView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        targetLangChipView = createPillTextView(context, true);
        updateTargetLangLabel();
        targetLangChipView.setOnClickListener(v -> showLanguagePickerSheet(
                getContext(),
                sheetResourcesProvider,
                targetLang,
                false,
                newCode -> {
                    if (!TextUtils.isEmpty(newCode)) {
                        targetLang = newCode;
                        MirrlyConfig.setChatTranslateToLang(dialogId, newCode);
                        updateTargetLangLabel();
                        runTranslation();
                    }
                }
        ));
        langBar.addView(targetLangChipView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 30, Gravity.CENTER_VERTICAL));

        // 3. Original Source Card (Compact Reference)
        LinearLayout sourceCard = new LinearLayout(context);
        sourceCard.setOrientation(LinearLayout.VERTICAL);
        sourceCard.setPadding(dp(12), dp(9), dp(12), dp(9));
        sourceCard.setBackground(Theme.createRoundRectDrawable(dp(12), getSurfaceCardColor()));
        rootLayout.addView(sourceCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        TextView originalLabel = new TextView(context);
        originalLabel.setText(LocaleController.getString(R.string.MirrlyTranslateSendOriginalLabel));
        originalLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
        originalLabel.setTypeface(AndroidUtilities.bold());
        originalLabel.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        sourceCard.addView(originalLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 3));

        TextView originalPreview = new TextView(context);
        originalPreview.setText(this.sourceText);
        originalPreview.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
        originalPreview.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        originalPreview.setMaxLines(3);
        originalPreview.setEllipsize(TextUtils.TruncateAt.END);
        sourceCard.addView(originalPreview, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // 4. Translated Editable Preview Box
        LinearLayout previewCard = new LinearLayout(context);
        previewCard.setOrientation(LinearLayout.VERTICAL);
        previewCard.setPadding(dp(12), dp(10), dp(12), dp(10));
        previewCard.setBackground(Theme.createRoundRectDrawable(dp(12), getSurfaceCardColor()));
        rootLayout.addView(previewCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        LinearLayout previewTopBar = new LinearLayout(context);
        previewTopBar.setOrientation(LinearLayout.HORIZONTAL);
        previewTopBar.setGravity(Gravity.CENTER_VERTICAL);
        previewCard.addView(previewTopBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        statusSpinner = new RadialProgressView(context, resourcesProvider);
        statusSpinner.setSize(dp(14));
        statusSpinner.setProgressColor(getThemedColor(Theme.key_featuredStickers_addButton));
        statusSpinner.setVisibility(View.GONE);
        previewTopBar.addView(statusSpinner, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));

        statusLabelView = new TextView(context);
        statusLabelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        statusLabelView.setTypeface(AndroidUtilities.bold());
        statusLabelView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText4));
        statusLabelView.setSingleLine(true);
        statusLabelView.setEllipsize(TextUtils.TruncateAt.END);
        previewTopBar.addView(statusLabelView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

        previewEditText = new EditTextBoldCursor(context);

        copyIconView = createActionIcon(context, R.drawable.msg_copy);
        copyIconView.setContentDescription(LocaleController.getString(R.string.Copy));
        copyIconView.setOnClickListener(v -> {
            Editable cur = previewEditText.getText();
            if (!TextUtils.isEmpty(cur)) {
                AndroidUtilities.addToClipboard(cur.toString());
                BulletinFactory.of((FrameLayout) containerView, resourcesProvider)
                        .createCopyBulletin(LocaleController.getString(R.string.TextCopied))
                        .show();
            }
        });
        previewTopBar.addView(copyIconView, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL, 4, 0, 2, 0));

        retryIconView = createActionIcon(context, R.drawable.msg_retry);
        retryIconView.setContentDescription(LocaleController.getString(R.string.Retry));
        retryIconView.setOnClickListener(v -> runTranslation());
        previewTopBar.addView(retryIconView, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL, 2, 0, 0, 0));

        previewEditText.setBackground(null);
        previewEditText.setPadding(0, dp(2), 0, dp(2));
        previewEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15.5f);
        previewEditText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        previewEditText.setHintTextColor(getThemedColor(Theme.key_dialogTextHint));
        previewEditText.setHint(LocaleController.getString(R.string.MirrlyTranslateSendPreviewHint));
        previewEditText.setCursorColor(getThemedColor(Theme.key_chat_TextSelectionCursor));
        previewEditText.setCursorSize(dp(18));
        previewEditText.setCursorWidth(1.5f);
        previewEditText.setGravity(Gravity.TOP | Gravity.START);
        previewEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        previewEditText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        previewEditText.setMinLines(2);
        previewEditText.setMaxLines(6);
        previewEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                hasValidTranslation = !TextUtils.isEmpty(s) && !TextUtils.isEmpty(s.toString().trim());
                updateButtonsState();
            }
        });
        previewCard.addView(previewEditText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // 5. Action Buttons Row
        LinearLayout actionsRow = new LinearLayout(context);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setGravity(Gravity.CENTER_VERTICAL);
        rootLayout.addView(actionsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        toInputButton = new ButtonWithCounterView(context, false, resourcesProvider).setRound().setNeutral();
        toInputButton.setText(LocaleController.getString(R.string.MirrlyTranslateSendToInput), false);
        toInputButton.setOnClickListener(v -> {
            String out = getPreviewText();
            if (TextUtils.isEmpty(out)) {
                return;
            }
            if (delegate != null) {
                delegate.onApplyTranslatedToInput(out);
            }
            dismiss();
        });
        actionsRow.addView(toInputButton, LayoutHelper.createLinear(0, 48, 0.9f, 0, 0, 8, 0));

        sendButton = new ButtonWithCounterView(context, true, resourcesProvider).setRound();
        sendButton.setText(LocaleController.getString(R.string.Send), false);
        sendButton.setOnClickListener(v -> commitSend(true, 0));
        if (canSendWithoutSound || canSchedule) {
            sendButton.setOnLongClickListener(v -> {
                String out = getPreviewText();
                if (TextUtils.isEmpty(out) || isTranslating) {
                    return false;
                }
                try {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                } catch (Exception ignore) {
                }
                ItemOptions opts = ItemOptions.makeOptions((FrameLayout) containerView, sheetResourcesProvider, sendButton);
                if (canSchedule) {
                    opts.add(R.drawable.msg_calendar2, LocaleController.getString(R.string.ScheduleMessage), () -> commitSend(true, -1));
                }
                if (canSendWithoutSound) {
                    opts.add(R.drawable.input_notify_off, LocaleController.getString(R.string.SendWithoutSound), () -> commitSend(false, 0));
                }
                opts.add(R.drawable.msg_send, LocaleController.getString(R.string.Send), () -> commitSend(true, 0));
                opts.show();
                return true;
            });
        }
        actionsRow.addView(sendButton, LayoutHelper.createLinear(0, 48, 1.35f));

        setCustomView(scrollView);
        updateButtonsState();
        startDetectAndTranslate();
    }

    private void commitSend(boolean notify, int scheduleDate) {
        String out = getPreviewText();
        if (TextUtils.isEmpty(out) || isTranslating) {
            return;
        }
        dismiss();
        if (delegate != null) {
            delegate.onSendTranslated(out, notify, scheduleDate);
        }
    }

    @NonNull
    private String getPreviewText() {
        Editable e = previewEditText.getText();
        return e == null ? "" : e.toString().trim();
    }

    private int getSurfaceCardColor() {
        int bg = getThemedColor(Theme.key_dialogBackground);
        int txt = getThemedColor(Theme.key_dialogTextBlack);
        boolean dark = AndroidUtilities.computePerceivedBrightness(bg) < 0.5f;
        return ColorUtils.blendARGB(bg, txt, dark ? 0.08f : 0.05f);
    }

    private TextView createPillTextView(@NonNull Context context, boolean accent) {
        int color = getThemedColor(accent ? Theme.key_featuredStickers_addButton : Theme.key_dialogTextBlack);
        TextView tv = new TextView(context);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(12), 0, dp(12), 0);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                dp(15),
                ColorUtils.setAlphaComponent(color, 26),
                ColorUtils.setAlphaComponent(color, 52)
        ));
        return tv;
    }

    private ImageView createActionIcon(@NonNull Context context, int iconRes) {
        ImageView iv = new ImageView(context);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iv.setPadding(dp(4), dp(4), dp(4), dp(4));
        int tint = getThemedColor(Theme.key_windowBackgroundWhiteGrayText);
        iv.setColorFilter(new PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN));
        iv.setImageResource(iconRes);
        iv.setBackground(Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(tint, 28), 1));
        return iv;
    }

    private void updateTargetLangLabel() {
        String label = formatLanguageDisplay(targetLang, false);
        targetLangChipView.setText(label + " ▾");
    }

    private void openProviderSelector() {
        showProviderSelectorMenu(
                (ViewGroup) containerView,
                providerChipView,
                sheetResourcesProvider,
                selectedProvider,
                providerId -> {
                    selectedProvider = providerId;
                    providerChipView.setText(MirrlyTranslatorEngine.getProviderShortName(selectedProvider));
                    runTranslation();
                }
        );
    }

    private void startDetectAndTranslate() {
        setTranslatingUi(true, LocaleController.getString(R.string.MirrlyTranslatorStatusWorking));
        MirrlyTranslatorEngine.detectLanguageAsync(sourceText, lang -> {
            if (isDismissed()) {
                return;
            }
            detectedSourceLang = TextUtils.isEmpty(lang) ? "auto" : lang;
            sourceLangChipView.setText(formatLanguageDisplay(detectedSourceLang, true));
            String remembered = MirrlyConfig.getChatTranslateToLang(dialogId);
            if (TextUtils.isEmpty(remembered)) {
                targetLang = MirrlyTranslatorEngine.resolveTargetLanguageForChat(dialogId, detectedSourceLang);
                updateTargetLangLabel();
            }
            runTranslation();
        });
    }

    private void runTranslation() {
        if (activeToken != null) {
            activeToken.cancel();
        }
        final MirrlyTranslatorEngine.CancelToken token = new MirrlyTranslatorEngine.CancelToken();
        activeToken = token;
        setTranslatingUi(true, LocaleController.getString(R.string.MirrlyTranslatorStatusWorking));

        MirrlyTranslatorEngine.translateAsync(
                sourceText,
                detectedSourceLang,
                targetLang,
                selectedProvider,
                token,
                result -> {
                    if (token.isCancelled() || isDismissed()) {
                        return;
                    }
                    if (result.success && !TextUtils.isEmpty(result.translatedText)) {
                        if (("auto".equals(detectedSourceLang) || "und".equals(detectedSourceLang))
                                && !TextUtils.isEmpty(result.detectedSourceLanguage)) {
                            detectedSourceLang = result.detectedSourceLanguage;
                            sourceLangChipView.setText(formatLanguageDisplay(detectedSourceLang, true));
                        }
                        previewEditText.setText(result.translatedText);
                        previewEditText.setSelection(previewEditText.length());
                        hasValidTranslation = true;
                        setTranslatingUi(false, result.providerLabel + " • " + result.latencyMs + " ms");
                    } else {
                        String msg = LocaleController.getString(result.rateLimited
                                ? R.string.TranslationFailedAlert1
                                : R.string.TranslationFailedAlert2);
                        setTranslatingUi(false, msg);
                        BulletinFactory.of((FrameLayout) containerView, sheetResourcesProvider)
                                .createErrorBulletin(msg)
                                .show();
                    }
                }
        );
    }

    private void setTranslatingUi(boolean translating, @NonNull String status) {
        isTranslating = translating;
        statusSpinner.setVisibility(translating ? View.VISIBLE : View.GONE);
        statusLabelView.setText(status);
        statusLabelView.setTextColor(getThemedColor(
                translating ? Theme.key_windowBackgroundWhiteBlueText4 : Theme.key_windowBackgroundWhiteGrayText
        ));
        updateButtonsState();
    }

    private void updateButtonsState() {
        boolean ready = !isTranslating && hasValidTranslation;
        sendButton.setLoading(isTranslating);
        sendButton.setEnabled(ready);
        sendButton.setAlpha(ready || isTranslating ? 1f : 0.45f);
        toInputButton.setEnabled(ready);
        toInputButton.setAlpha(ready ? 1f : 0.45f);
        copyIconView.setEnabled(hasValidTranslation);
        copyIconView.setAlpha(hasValidTranslation ? 1f : 0.35f);
    }

    @Override
    public void dismissInternal() {
        if (activeToken != null) {
            activeToken.cancel();
            activeToken = null;
        }
        super.dismissInternal();
    }

    @NonNull
    public static String formatLanguageDisplay(@Nullable String code, boolean allowAutoLabel) {
        if (TextUtils.isEmpty(code) || "auto".equalsIgnoreCase(code) || "und".equalsIgnoreCase(code)) {
            return allowAutoLabel
                    ? LocaleController.getString(R.string.MirrlyTranslatorLangAuto)
                    : LocaleController.getString(R.string.MirrlyTranslatorLangAppDefault);
        }
        String cap = org.telegram.ui.Components.TranslateAlert2.languageNameCapital(code);
        if (!TextUtils.isEmpty(cap)) {
            return cap;
        }
        String sys = org.telegram.ui.Components.TranslateAlert2.systemLanguageName(code, false);
        if (!TextUtils.isEmpty(sys)) {
            return sys.substring(0, 1).toUpperCase(Locale.ROOT) + sys.substring(1);
        }
        return code.toUpperCase(Locale.ROOT);
    }

    public static void showProviderSelectorMenu(
            @NonNull ViewGroup container,
            @NonNull View anchor,
            @Nullable Theme.ResourcesProvider rp,
            int currentProvider,
            @NonNull Utilities.Callback<Integer> onPicked
    ) {
        ItemOptions opts = ItemOptions.makeOptions(container, rp, anchor);
        int[] ids = new int[]{
                MirrlyConfig.TRANSLATOR_PROVIDER_AUTO,
                MirrlyConfig.TRANSLATOR_PROVIDER_GOOGLE,
                MirrlyConfig.TRANSLATOR_PROVIDER_LINGVA,
                MirrlyConfig.TRANSLATOR_PROVIDER_MYMEMORY
        };
        for (int id : ids) {
            opts.addChecked(
                    id == currentProvider,
                    MirrlyTranslatorEngine.getProviderDisplayName(id),
                    () -> onPicked.run(id)
            );
        }
        opts.show();
    }

    /**
     * Reusable searchable native language picker sheet shared by message translation,
     * Translate & Send, and MirrlyTranslatorSettingsActivity.
     */
    public static void showLanguagePickerSheet(
            @NonNull Context context,
            @Nullable Theme.ResourcesProvider rp,
            @Nullable String currentCode,
            boolean includeAutoOption,
            @NonNull Utilities.Callback<String> onSelected
    ) {
        final List<LangEntry> allEntries = buildCuratedLanguages(includeAutoOption);
        final List<LangEntry> filtered = new ArrayList<>(allEntries);
        final String normCurrent = MirrlyTranslatorEngine.normalizeLangCode(currentCode);

        final MirrlyUiKit.FrostedBottomSheet sheet = new MirrlyUiKit.FrostedBottomSheet(context, true, rp);
        sheet.setApplyTopPadding(false);
        sheet.setApplyBottomPadding(false);

        LinearLayout root = new LinearLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int w = MeasureSpec.getSize(widthMeasureSpec);
                if (w > 0) {
                    widthMeasureSpec = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY);
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        root.setOrientation(LinearLayout.VERTICAL);
        root.setMinimumWidth(dp(320));
        root.setPadding(dp(16), dp(22), dp(16), dp(12));

        TextView title = new TextView(context);
        title.setText(LocaleController.getString(R.string.MirrlyTranslatorPickLanguage));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.LEFT);
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        root.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        EditTextBoldCursor searchEdit = new EditTextBoldCursor(context);
        searchEdit.setHint(LocaleController.getString(R.string.Search));
        searchEdit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        searchEdit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        searchEdit.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint, rp));
        searchEdit.setCursorColor(Theme.getColor(Theme.key_chat_TextSelectionCursor, rp));
        searchEdit.setSingleLine(true);
        searchEdit.setPadding(dp(12), dp(10), dp(12), dp(10));
        int bg = Theme.getColor(Theme.key_dialogBackground, rp);
        int fg = Theme.getColor(Theme.key_dialogTextBlack, rp);
        boolean dark = AndroidUtilities.computePerceivedBrightness(bg) < 0.5f;
        searchEdit.setBackground(Theme.createRoundRectDrawable(dp(10), ColorUtils.blendARGB(bg, fg, dark ? 0.09f : 0.06f)));
        root.addView(searchEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 42, 0, 0, 0, 8));

        RecyclerView rv = new RecyclerView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int w = MeasureSpec.getSize(widthMeasureSpec);
                if (w > 0) {
                    widthMeasureSpec = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY);
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        rv.setLayoutManager(new LinearLayoutManager(context));
        rv.setClipToPadding(false);
        RecyclerView.Adapter<LangHolder> adapter = new RecyclerView.Adapter<LangHolder>() {
            @NonNull
            @Override
            public LangHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                FrameLayout row = new FrameLayout(context) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        int w = MeasureSpec.getSize(widthMeasureSpec);
                        int parentW = parent.getMeasuredWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
                        int targetW = parentW > 0 ? parentW : w;
                        if (targetW > 0) {
                            widthMeasureSpec = MeasureSpec.makeMeasureSpec(targetW, MeasureSpec.EXACTLY);
                        }
                        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    }
                };
                row.setPadding(dp(12), dp(10), dp(12), dp(10));
                row.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, rp), 2));
                row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                LinearLayout col = new LinearLayout(context);
                col.setOrientation(LinearLayout.VERTICAL);
                row.addView(col, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 0, 0, 56, 0));

                TextView primary = new TextView(context);
                primary.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                primary.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
                primary.setSingleLine(true);
                primary.setEllipsize(TextUtils.TruncateAt.END);
                primary.setGravity(Gravity.LEFT);
                col.addView(primary, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                TextView secondary = new TextView(context);
                secondary.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
                secondary.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, rp));
                secondary.setSingleLine(true);
                secondary.setEllipsize(TextUtils.TruncateAt.END);
                secondary.setGravity(Gravity.LEFT);
                col.addView(secondary, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

                TextView badge = new TextView(context);
                badge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
                badge.setTypeface(AndroidUtilities.bold());
                badge.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton, rp));
                badge.setSingleLine(true);
                badge.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                row.addView(badge, LayoutHelper.createFrame(52, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL));

                return new LangHolder(row, primary, secondary, badge);
            }

            @Override
            public void onBindViewHolder(@NonNull LangHolder holder, int position) {
                LangEntry entry = filtered.get(position);
                holder.primary.setText(entry.primaryName);
                holder.secondary.setText(entry.secondaryName);
                boolean selected = ("auto".equals(entry.code) && ("auto".equals(normCurrent) || TextUtils.isEmpty(normCurrent)))
                        || entry.code.equals(normCurrent);
                holder.badge.setText(selected ? "✓" : entry.code.toUpperCase(Locale.ROOT));
                holder.badge.setTextColor(Theme.getColor(
                        selected ? Theme.key_featuredStickers_addButton : Theme.key_windowBackgroundWhiteGrayText,
                        rp
                ));
                holder.itemView.setOnClickListener(v -> {
                    AndroidUtilities.hideKeyboard(searchEdit);
                    sheet.dismiss();
                    onSelected.run("auto".equals(entry.code) && includeAutoOption ? "auto" : entry.code);
                });
            }

            @Override
            public int getItemCount() {
                return filtered.size();
            }
        };
        rv.setAdapter(adapter);
        root.addView(rv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 340));

        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String q = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                filtered.clear();
                if (TextUtils.isEmpty(q)) {
                    filtered.addAll(allEntries);
                } else {
                    for (LangEntry e : allEntries) {
                        if (e.query.contains(q)) {
                            filtered.add(e);
                        }
                    }
                }
                adapter.notifyDataSetChanged();
            }
        });

        sheet.setCustomView(root);
        sheet.show();
    }

    private static final class LangEntry {
        final String code;
        final String primaryName;
        final String secondaryName;
        final String query;

        LangEntry(String code, String primaryName, String secondaryName) {
            this.code = code;
            this.primaryName = primaryName;
            this.secondaryName = secondaryName;
            this.query = (code + " " + primaryName + " " + secondaryName).toLowerCase(Locale.ROOT);
        }
    }

    private static final class LangHolder extends RecyclerView.ViewHolder {
        final TextView primary;
        final TextView secondary;
        final TextView badge;

        LangHolder(@NonNull View itemView, TextView primary, TextView secondary, TextView badge) {
            super(itemView);
            this.primary = primary;
            this.secondary = secondary;
            this.badge = badge;
        }
    }

    @NonNull
    private static List<LangEntry> buildCuratedLanguages(boolean includeAutoOption) {
        LinkedHashMap<String, LangEntry> map = new LinkedHashMap<>();
        if (includeAutoOption) {
            map.put("auto", new LangEntry(
                    "auto",
                    LocaleController.getString(R.string.MirrlyTranslatorLangAuto),
                    LocaleController.getString(R.string.MirrlyTranslatorLangAutoSub)
            ));
        }
        ArrayList<TranslateController.Language> suggested = TranslateController.getSuggestedLanguages(null);
        for (int i = 0; i < suggested.size(); i++) {
            TranslateController.Language l = suggested.get(i);
            if (l != null && !TextUtils.isEmpty(l.code)) {
                String norm = MirrlyTranslatorEngine.normalizeLangCode(l.code);
                if (!map.containsKey(norm)) {
                    String primary = !TextUtils.isEmpty(l.displayName) ? l.displayName : formatLanguageDisplay(norm, false);
                    String secondary = !TextUtils.isEmpty(l.ownDisplayName) ? l.ownDisplayName : norm;
                    map.put(norm, new LangEntry(norm, primary, secondary));
                }
            }
        }
        for (String code : POPULAR_LANG_CODES) {
            String norm = MirrlyTranslatorEngine.normalizeLangCode(code);
            if (!map.containsKey(norm)) {
                String primary = formatLanguageDisplay(norm, false);
                String own = TranslateAlert2.systemLanguageName(norm, true);
                String secondary = !TextUtils.isEmpty(own)
                        ? own.substring(0, 1).toUpperCase(Locale.ROOT) + own.substring(1)
                        : norm.toUpperCase(Locale.ROOT);
                map.put(norm, new LangEntry(norm, primary, secondary));
            }
        }
        return new ArrayList<>(map.values());
    }
}
