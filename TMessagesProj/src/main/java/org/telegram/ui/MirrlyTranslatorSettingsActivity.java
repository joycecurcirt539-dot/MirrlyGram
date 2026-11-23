package org.telegram.ui;

import android.os.Build;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.mirrly.MirrlyConfig;
import org.telegram.messenger.mirrly.MirrlySttEngine;
import org.telegram.messenger.mirrly.MirrlyTranslatorEngine;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.MirrlyTranslateSendSheet;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

/**
 * Dedicated MirrlyGram settings hub for keyless free message translation aggregators,
 * quick translate controls, and local/system Speech-to-Text fallback.
 */
public class MirrlyTranslatorSettingsActivity extends MirrlyBaseSettingsFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int ID_HERO = 1;

    private static final int ID_MSG_TRANSLATE_BTN = 10;
    private static final int ID_MSG_TRANSLATE_SCOPE = 11;
    private static final int ID_DEFAULT_TARGET_LANG = 12;
    private static final int ID_SHOW_PROVIDER_TOAST = 13;

    private static final int ID_TRANSLATOR_PROVIDER = 20;
    private static final int ID_TEST_TRANSLATOR = 26;
    private static final int ID_CLEAR_TRANSLATOR_CACHE = 27;

    private static final int ID_FREE_STT_ENABLED = 40;
    private static final int ID_FREE_STT_MODE = 41;
    private static final int ID_FREE_STT_PROVIDER = 42;
    private static final int ID_FREE_STT_LANGUAGE = 43;
    private static final int ID_FREE_STT_OFFLINE = 44;

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.mirrlyTranslatorSettingsChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.mirrlyTranslatorSettingsChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        super.onFragmentDestroy();
    }

    @Override
    protected CharSequence getScreenTitle() {
        return LocaleController.getString(R.string.MirrlyTranslatorSettingsTitle);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        MirrlyConfig.load();

        String trProviderLabel = MirrlyTranslatorEngine.getProviderShortName(MirrlyConfig.getTranslatorProvider());
        String sttStatusPill = MirrlyConfig.isFreeSttEnabled()
                ? LocaleController.formatString(R.string.MirrlySttHeroOn, MirrlySttEngine.getProviderShortName(MirrlyConfig.getFreeSttProvider()))
                : LocaleController.getString(R.string.MirrlySttHeroOff);

        items.add(MirrlyUiKit.HeroCardView.Factory.of(
                ID_HERO,
                new MirrlyUiKit.HeroSpec(
                        MirrlyUiKit.HeroSpec.MODE_LOTTIE,
                        R.raw.msg_translate,
                        false,
                        LocaleController.getString(R.string.MirrlyTranslatorSettingsTitle),
                        new CharSequence[]{trProviderLabel, sttStatusPill},
                        LocaleController.getString(R.string.MirrlyTranslatorHeroPill)
                )
        ));

        // 1. In-Chat & Compose Message Translation
        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyTranslatorChatHeader)));
        boolean msgBtnEnabled = MirrlyConfig.isMsgTranslateButtonEnabled();
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_MSG_TRANSLATE_BTN,
                R.drawable.msg_translate,
                LocaleController.getString(R.string.MirrlyTranslatorMsgButtonToggle),
                msgBtnEnabled
        ));
        if (msgBtnEnabled) {
            items.add(MirrlyUiKit.SegmentedCardCell.Factory.of(
                    ID_MSG_TRANSLATE_SCOPE,
                    new MirrlyUiKit.SegmentedSpec(
                            R.drawable.outline_groups_24,
                            LocaleController.getString(R.string.MirrlyTranslatorScopeAll),
                            R.drawable.filled_profile_message_24,
                            LocaleController.getString(R.string.MirrlyTranslatorScopeIncoming),
                            MirrlyConfig.getMsgTranslateButtonScope() == MirrlyConfig.TRANSLATE_SCOPE_INCOMING ? 1 : 0,
                            idx -> MirrlyConfig.setMsgTranslateButtonScope(
                                    idx == 1 ? MirrlyConfig.TRANSLATE_SCOPE_INCOMING : MirrlyConfig.TRANSLATE_SCOPE_ALL
                            )
                    )
            ));
        }
        items.add(UItem.asButton(
                ID_DEFAULT_TARGET_LANG,
                R.drawable.msg_language,
                LocaleController.getString(R.string.MirrlyTranslatorDefaultTargetLang),
                MirrlyTranslateSendSheet.formatLanguageDisplay(MirrlyConfig.getTranslatorTargetLang(), false)
        ));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_SHOW_PROVIDER_TOAST,
                R.drawable.msg_info,
                LocaleController.getString(R.string.MirrlyTranslatorShowProviderToast),
                MirrlyConfig.isTranslatorShowProviderToast()
        ));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MirrlyTranslatorChatHint)));

        // 2. Aggregator & Translation Providers
        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyTranslatorEngineHeader)));
        items.add(UItem.asButton(
                ID_TRANSLATOR_PROVIDER,
                R.drawable.outline_ai_translate2,
                LocaleController.getString(R.string.MirrlyTranslatorProviderRow),
                MirrlyTranslatorEngine.getProviderShortName(MirrlyConfig.getTranslatorProvider())
        ));
        items.add(UItem.asButton(
                ID_TEST_TRANSLATOR,
                R.drawable.msg_retry,
                LocaleController.getString(R.string.MirrlyTranslatorTestAction),
                MirrlyTranslatorEngine.getProviderShortName(MirrlyConfig.getTranslatorProvider())
        ));
        items.add(UItem.asButton(
                ID_CLEAR_TRANSLATOR_CACHE,
                R.drawable.msg_delete,
                LocaleController.getString(R.string.MirrlyTranslatorClearCache),
                ""
        ));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MirrlyTranslatorEngineHint)));

        // 3. Free Voice & Round Video STT Fallback
        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlySttHeader)));
        boolean sttEnabled = MirrlyConfig.isFreeSttEnabled();
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_FREE_STT_ENABLED,
                R.drawable.msg_voice_unmuted,
                LocaleController.getString(R.string.MirrlySttEnableToggle),
                sttEnabled
        ));
        if (sttEnabled) {
            items.add(MirrlyUiKit.SegmentedCardCell.Factory.of(
                    ID_FREE_STT_MODE,
                    new MirrlyUiKit.SegmentedSpec(
                            R.drawable.outline_shield_check,
                            LocaleController.getString(R.string.MirrlySttModeTrialExhausted),
                            R.drawable.msg_speed,
                            LocaleController.getString(R.string.MirrlySttModeAlways),
                            MirrlyConfig.getFreeSttMode() == MirrlyConfig.STT_MODE_ALWAYS ? 1 : 0,
                            idx -> MirrlyConfig.setFreeSttMode(
                                    idx == 1 ? MirrlyConfig.STT_MODE_ALWAYS : MirrlyConfig.STT_MODE_TRIAL_EXHAUSTED
                            )
                    )
            ));
            items.add(UItem.asButton(
                    ID_FREE_STT_PROVIDER,
                    R.drawable.msg_settings,
                    LocaleController.getString(R.string.MirrlySttProviderRow),
                    MirrlySttEngine.getProviderShortName(MirrlyConfig.getFreeSttProvider())
            ));
            items.add(UItem.asButton(
                    ID_FREE_STT_LANGUAGE,
                    R.drawable.msg_language,
                    LocaleController.getString(R.string.MirrlySttLanguageRow),
                    MirrlyTranslateSendSheet.formatLanguageDisplay(MirrlyConfig.getFreeSttLanguage(), true)
            ));
            items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                    ID_FREE_STT_OFFLINE,
                    R.drawable.msg_download,
                    LocaleController.getString(R.string.MirrlySttPreferOfflineToggle),
                    MirrlyConfig.isFreeSttPreferOffline()
            ));
        }
        items.add(UItem.asShadow(buildSttFooterText()));
    }

    @NonNull
    private String buildSttFooterText() {
        String base = LocaleController.getString(R.string.MirrlySttHint);
        String compat = MirrlySttEngine.isSystemSpeechFileInjectionSupported()
                ? LocaleController.getString(R.string.MirrlySttCompatSupported)
                : (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                        ? LocaleController.getString(R.string.MirrlySttCompatOldAndroid)
                        : LocaleController.getString(R.string.MirrlySttCompatNoService));
        return base + "\n\n" + compat;
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item == null || getParentActivity() == null) {
            return;
        }
        switch (item.id) {
            case ID_MSG_TRANSLATE_BTN: {
                boolean next = !MirrlyConfig.isMsgTranslateButtonEnabled();
                MirrlyConfig.setMsgTranslateButtonEnabled(next);
                if (view instanceof TextCell) {
                    ((TextCell) view).setChecked(next);
                }
                refreshList(true);
                break;
            }
            case ID_DEFAULT_TARGET_LANG: {
                MirrlyTranslateSendSheet.showLanguagePickerSheet(
                        getParentActivity(),
                        getResourceProvider(),
                        MirrlyConfig.getTranslatorTargetLang(),
                        true,
                        code -> {
                            MirrlyConfig.setTranslatorTargetLang("auto".equals(code) ? "" : code);
                            refreshList(true);
                        }
                );
                break;
            }
            case ID_SHOW_PROVIDER_TOAST: {
                boolean next = !MirrlyConfig.isTranslatorShowProviderToast();
                MirrlyConfig.setTranslatorShowProviderToast(next);
                if (view instanceof TextCell) {
                    ((TextCell) view).setChecked(next);
                }
                refreshList(false);
                break;
            }
            case ID_TRANSLATOR_PROVIDER: {
                showTranslatorProviderDialog();
                break;
            }
            case ID_TEST_TRANSLATOR: {
                testActiveTranslator();
                break;
            }
            case ID_CLEAR_TRANSLATOR_CACHE: {
                MirrlyTranslatorEngine.clearCache();
                BulletinFactory.of(this)
                        .createSimpleBulletin(R.raw.ic_delete, LocaleController.getString(R.string.MirrlyTranslatorCacheCleared))
                        .show();
                break;
            }
            case ID_FREE_STT_ENABLED: {
                boolean next = !MirrlyConfig.isFreeSttEnabled();
                MirrlyConfig.setFreeSttEnabled(next);
                if (view instanceof TextCell) {
                    ((TextCell) view).setChecked(next);
                }
                refreshList(true);
                break;
            }
            case ID_FREE_STT_PROVIDER: {
                showSttProviderDialog();
                break;
            }
            case ID_FREE_STT_LANGUAGE: {
                MirrlyTranslateSendSheet.showLanguagePickerSheet(
                        getParentActivity(),
                        getResourceProvider(),
                        MirrlyConfig.getFreeSttLanguage(),
                        true,
                        code -> {
                            MirrlyConfig.setFreeSttLanguage(TextUtils.isEmpty(code) ? "auto" : code);
                            refreshList(true);
                        }
                );
                break;
            }
            case ID_FREE_STT_OFFLINE: {
                boolean next = !MirrlyConfig.isFreeSttPreferOffline();
                MirrlyConfig.setFreeSttPreferOffline(next);
                if (view instanceof TextCell) {
                    ((TextCell) view).setChecked(next);
                }
                refreshList(false);
                break;
            }
        }
    }

    private void testActiveTranslator() {
        final String target = MirrlyTranslatorEngine.getEffectiveDefaultTargetLanguage();
        final String sample = "en".equals(target) ? "Проверка связи переводчика" : "Hello, translation works";
        BulletinFactory.of(this)
                .createSimpleBulletin(R.raw.msg_translate, LocaleController.getString(R.string.MirrlyTranslatorStatusWorking))
                .show();
        MirrlyTranslatorEngine.translateAsync(sample, null, target, MirrlyConfig.getTranslatorProvider(), null, res -> {
            if (getParentActivity() == null) {
                return;
            }
            if (res.success && !TextUtils.isEmpty(res.translatedText)) {
                BulletinFactory.of(this)
                        .createSimpleBulletin(
                                R.raw.msg_translate,
                                res.providerLabel + " (" + res.latencyMs + " ms): " + res.translatedText
                        )
                        .setDuration(Bulletin.DURATION_LONG)
                        .show();
            } else {
                String err = !TextUtils.isEmpty(res.errorMessage)
                        ? res.errorMessage
                        : LocaleController.getString(R.string.TranslationFailedAlert2);
                BulletinFactory.of(this).createErrorBulletin(res.providerLabel + ": " + err).show();
            }
        });
    }

    private void showTranslatorProviderDialog() {
        if (getParentActivity() == null) {
            return;
        }
        final int[] ids = new int[]{
                MirrlyConfig.TRANSLATOR_PROVIDER_AUTO,
                MirrlyConfig.TRANSLATOR_PROVIDER_GOOGLE,
                MirrlyConfig.TRANSLATOR_PROVIDER_LINGVA,
                MirrlyConfig.TRANSLATOR_PROVIDER_MYMEMORY
        };
        int rawCur = MirrlyConfig.getTranslatorProvider();
        int cur = MirrlyConfig.TRANSLATOR_PROVIDER_AUTO;
        for (int id : ids) {
            if (id == rawCur) {
                cur = rawCur;
                break;
            }
        }
        CharSequence[] labels = new CharSequence[ids.length];
        for (int i = 0; i < ids.length; i++) {
            String name = MirrlyTranslatorEngine.getProviderDisplayName(ids[i]);
            labels[i] = ids[i] == cur ? "✓  " + name : name;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle(LocaleController.getString(R.string.MirrlyTranslatorProviderRow));
        builder.setItems(labels, (d, idx) -> {
            MirrlyConfig.setTranslatorProvider(ids[idx]);
            refreshList(true);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showSttProviderDialog() {
        if (getParentActivity() == null) {
            return;
        }
        final int[] ids = new int[]{
                MirrlyConfig.STT_PROVIDER_AUTO,
                MirrlyConfig.STT_PROVIDER_SYSTEM
        };
        int rawCur = MirrlyConfig.getFreeSttProvider();
        int cur = rawCur == MirrlyConfig.STT_PROVIDER_SYSTEM
                ? MirrlyConfig.STT_PROVIDER_SYSTEM
                : MirrlyConfig.STT_PROVIDER_AUTO;
        CharSequence[] labels = new CharSequence[ids.length];
        for (int i = 0; i < ids.length; i++) {
            String name = MirrlySttEngine.getProviderDisplayName(ids[i]);
            labels[i] = ids[i] == cur ? "✓  " + name : name;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle(LocaleController.getString(R.string.MirrlySttProviderRow));
        builder.setItems(labels, (d, idx) -> {
            MirrlyConfig.setFreeSttProvider(ids[idx]);
            refreshList(true);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void refreshList(boolean animated) {
        String trLabel = MirrlyTranslatorEngine.getProviderShortName(MirrlyConfig.getTranslatorProvider());
        String sttPill = MirrlyConfig.isFreeSttEnabled()
                ? LocaleController.formatString(R.string.MirrlySttHeroOn, MirrlySttEngine.getProviderShortName(MirrlyConfig.getFreeSttProvider()))
                : LocaleController.getString(R.string.MirrlySttHeroOff);
        refreshHeroSubtitles(new CharSequence[]{trLabel, sttPill}, animated);
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(animated);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.mirrlyTranslatorSettingsChanged) {
            refreshList(true);
        } else if (id == NotificationCenter.didSetNewTheme && listView != null && listView.adapter != null) {
            listView.adapter.updateWithoutNotify();
            for (int i = 0; i < listView.getChildCount(); i++) {
                View child = listView.getChildAt(i);
                if (child instanceof Theme.Colorable) {
                    ((Theme.Colorable) child).updateColors();
                }
            }
            listView.invalidate();
        }
    }
}
