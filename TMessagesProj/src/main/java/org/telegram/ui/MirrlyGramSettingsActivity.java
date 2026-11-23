package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.mirrly.MirrlyConfig;
import org.telegram.messenger.mirrly.MirrlyInterfaceConfig;
import org.telegram.messenger.mirrly.MirrlyVaultStorage;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.SimpleActionBarMenu;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Main Mirrlygram settings hub styled after Telegram's native SettingsActivity,
 * featuring an interactive hero emblem, centered live telemetry banner,
 * search across sub-screens, and modular category navigation.
 */
public class MirrlyGramSettingsActivity extends MirrlyBaseSettingsFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int ID_HERO = 1;
    private static final int ID_TELEMETRY_BANNER = 2;
    private static final int ID_OPEN_SUPPORT = 3;

    private static final int ID_CAT_NETWORK = 10;
    private static final int ID_CAT_VAULT = 11;
    private static final int ID_CAT_PROFILE = 12;
    private static final int ID_CAT_CHATS = 13;
    private static final int ID_OPEN_ABOUT = 14;
    private static final int ID_CAT_CUSTOMIZATION = 15;
    private static final int ID_CAT_INTERFACE = 16;
    private static final int ID_CAT_TRANSLATOR = 17;
    private static final int ID_CAT_POWER = 18;

    private static final int ID_SEARCH_BASE = 100;

    private ActionBarMenuItem searchItem;
    private boolean searching = false;
    private String searchQuery = "";
    private boolean pollingStats = false;

    private final List<SearchTarget> searchTargets = new ArrayList<>();

    private final Runnable statsUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (!pollingStats) {
                return;
            }
            refreshLiveTelemetry(true);
            AndroidUtilities.runOnUIThread(this, 1000);
        }
    };

    private static final class SearchTarget {
        final int id;
        final int actionType;
        final int iconTop;
        final int iconBottom;
        final int iconRes;
        final int titleRes;
        final int categoryRes;
        final int keywordsRes;
        final String extraKeywords;

        static final int ACTION_NETWORK = 1;
        static final int ACTION_VAULT = 2;
        static final int ACTION_CHATS = 3;
        static final int ACTION_DIAGNOSTICS = 4;
        static final int ACTION_SUPPORT = 5;
        static final int ACTION_ABOUT = 6;
        static final int ACTION_PROXY_LIST = 7;
        static final int ACTION_NOTES_CHAT = 8;
        static final int ACTION_PROFILE = 9;
        static final int ACTION_CUSTOMIZATION = 10;
        static final int ACTION_INTERFACE_HOME = 11;
        static final int ACTION_INTERFACE_BOTTOM_BAR = 12;
        static final int ACTION_INTERFACE_MORE_MENU = 13;
        static final int ACTION_INTERFACE_MSG_MENU = 14;
        static final int ACTION_TRANSLATOR = 15;
        static final int ACTION_DATACENTER_STATUS = 16;
        static final int ACTION_DELETED_CHAT = 17;
        static final int ACTION_DELETED_CHATS_LIST = 18;
        static final int ACTION_POWER = 19;

        SearchTarget(int id, int actionType, int iconTop, int iconBottom, int iconRes, int titleRes, int categoryRes, int keywordsRes, String extraKeywords) {
            this.id = id;
            this.actionType = actionType;
            this.iconTop = iconTop;
            this.iconBottom = iconBottom;
            this.iconRes = iconRes;
            this.titleRes = titleRes;
            this.categoryRes = categoryRes;
            this.keywordsRes = keywordsRes;
            this.extraKeywords = extraKeywords;
        }
    }

    @Override
    public boolean onFragmentCreate() {
        org.telegram.messenger.mirrly.MirrlyEngineController.ensureStarted();
        MirrlyConfig.load();
        MirrlyInterfaceConfig.load();
        buildSearchIndex();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.mirrlyNotesDidUpdate);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.mirrlyDeletedDidUpdate);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.mirrlyTranslatorSettingsChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.mirrlyPowerStatsUpdated);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.mirrlyPowerPolicyChanged);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        pollingStats = false;
        AndroidUtilities.cancelRunOnUIThread(statsUpdateRunnable);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.mirrlyNotesDidUpdate);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.mirrlyDeletedDidUpdate);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.mirrlyTranslatorSettingsChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.mirrlyPowerStatsUpdated);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.mirrlyPowerPolicyChanged);
        super.onFragmentDestroy();
    }

    private void buildSearchIndex() {
        searchTargets.clear();
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 1, SearchTarget.ACTION_NETWORK, IconBackgroundColors.CYAN.top, IconBackgroundColors.CYAN.bottom, R.drawable.mirrly_settings_engine, R.string.MirrlySettingsNetworkTitle, R.string.MirrlySettingsTitle, R.string.MirrlySettingsSearchKwNetwork, "socks5 mtproto transport proxy"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 2, SearchTarget.ACTION_NETWORK, IconBackgroundColors.CYAN.top, IconBackgroundColors.CYAN.bottom, R.drawable.mirrly_settings_engine, R.string.MirrlyNetworkTlsEnable, R.string.MirrlySettingsNetworkTitle, R.string.MirrlySettingsSearchKwTls, "tls 1.2 1.3 sni"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 3, SearchTarget.ACTION_NETWORK, IconBackgroundColors.CYAN.top, IconBackgroundColors.CYAN.bottom, R.drawable.mirrly_settings_engine, R.string.MirrlyNetworkTlsSplit, R.string.MirrlySettingsNetworkTitle, R.string.MirrlySettingsSearchKwTlsSplit, "fragment split clienthello"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 44, SearchTarget.ACTION_NETWORK, IconBackgroundColors.CYAN.top, IconBackgroundColors.CYAN.bottom, R.drawable.mirrly_settings_engine, R.string.MirrlyNetworkFlowsealPerDcEnable, R.string.MirrlySettingsNetworkTitle, R.string.MirrlySettingsSearchKwFlowsealPerDc, "cdn flowseal dc datacenter media isolated"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 4, SearchTarget.ACTION_NETWORK, IconBackgroundColors.CYAN.top, IconBackgroundColors.CYAN.bottom, R.drawable.mirrly_settings_engine, R.string.MirrlyNetworkReconnect, R.string.MirrlySettingsNetworkTitle, R.string.MirrlySettingsSearchKwReconnect, "socket reconnect"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 5, SearchTarget.ACTION_PROXY_LIST, IconBackgroundColors.CYAN.top, IconBackgroundColors.CYAN.bottom, R.drawable.mirrly_settings_engine, R.string.MirrlyNetworkProxyList, R.string.MirrlySettingsNetworkTitle, R.string.MirrlySettingsSearchKwProxyList, "proxy list"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 38, SearchTarget.ACTION_DATACENTER_STATUS, IconBackgroundColors.CYAN.top, IconBackgroundColors.CYAN.bottom, R.drawable.mirrly_settings_engine, R.string.MirrlyDatacenterStatusTitle, R.string.MirrlySettingsNetworkTitle, R.string.MirrlySettingsSearchKwDatacenter, "datacenter dc ping status"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 6, SearchTarget.ACTION_DIAGNOSTICS, IconBackgroundColors.GREEN.top, IconBackgroundColors.GREEN.bottom, R.drawable.mirrly_settings_engine, R.string.MirrlyDiagTitle, R.string.MirrlySettingsTitle, R.string.MirrlySettingsSearchKwDiag, "diagnostics uptime ws pool stats"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 7, SearchTarget.ACTION_VAULT, IconBackgroundColors.PURPLE.top, IconBackgroundColors.PURPLE.bottom, R.drawable.mirrly_settings_vault, R.string.MirrlySettingsVaultTitle, R.string.MirrlySettingsTitle, R.string.MirrlySettingsSearchKwVault, "vault notes edit history"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 8, SearchTarget.ACTION_NOTES_CHAT, IconBackgroundColors.PURPLE.top, IconBackgroundColors.PURPLE.bottom, R.drawable.mirrly_settings_vault, R.string.MirrlyVaultOpenNotesChat, R.string.MirrlySettingsVaultTitle, R.string.MirrlySettingsSearchKwNotesChat, "notes chat"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 40, SearchTarget.ACTION_DELETED_CHAT, IconBackgroundColors.RED.top, IconBackgroundColors.RED.bottom, R.drawable.msg_delete, R.string.MirrlyVaultOpenDeletedChat, R.string.MirrlySettingsVaultTitle, R.string.MirrlySettingsSearchKwDeletedChat, "deleted messages archive ghost"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 42, SearchTarget.ACTION_DELETED_CHATS_LIST, IconBackgroundColors.RED.top, IconBackgroundColors.RED.bottom, R.drawable.msg_list, R.string.MirrlyVaultOpenDeletedChatsList, R.string.MirrlySettingsVaultTitle, R.string.MirrlySettingsSearchKwDeletedChatsList, "deleted chats list dialogs archive"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 43, SearchTarget.ACTION_VAULT, IconBackgroundColors.RED.top, IconBackgroundColors.RED.bottom, R.drawable.msg_message, R.string.MirrlyVaultSaveDeletedChats, R.string.MirrlySettingsVaultTitle, R.string.MirrlySettingsSearchKwDeletedChatsList, "save deleted chats archive"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 41, SearchTarget.ACTION_VAULT, IconBackgroundColors.RED.top, IconBackgroundColors.RED.bottom, R.drawable.msg_delete, R.string.MirrlyVaultSaveDeleted, R.string.MirrlySettingsVaultTitle, R.string.MirrlySettingsSearchKwDeletedChat, "save deleted opacity alpha trash icon color ephemeral"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 9, SearchTarget.ACTION_VAULT, IconBackgroundColors.PURPLE.top, IconBackgroundColors.PURPLE.bottom, R.drawable.mirrly_settings_vault, R.string.MirrlyVaultSaveHistory, R.string.MirrlySettingsVaultTitle, R.string.MirrlySettingsSearchKwSaveHistory, "edit history"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 10, SearchTarget.ACTION_CHATS, IconBackgroundColors.ORANGE.top, IconBackgroundColors.ORANGE.bottom, R.drawable.mirrly_settings_chats, R.string.MirrlyChatsConfirmStickers, R.string.MirrlySettingsChatsTitle, R.string.MirrlySettingsSearchKwConfirmStickers, "stickers confirm"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 11, SearchTarget.ACTION_CHATS, IconBackgroundColors.ORANGE.top, IconBackgroundColors.ORANGE.bottom, R.drawable.mirrly_settings_chats, R.string.MirrlyChatsBackCameraRounds, R.string.MirrlySettingsChatsTitle, R.string.MirrlySettingsSearchKwBackCamera, "camera round video"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 15, SearchTarget.ACTION_CHATS, IconBackgroundColors.ORANGE.top, IconBackgroundColors.ORANGE.bottom, R.drawable.mirrly_settings_chats, R.string.MirrlyChatsSendSound, R.string.MirrlySettingsChatsTitle, R.string.MirrlySettingsSearchKwChatSounds, "sound send message mute"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 16, SearchTarget.ACTION_CHATS, IconBackgroundColors.ORANGE.top, IconBackgroundColors.ORANGE.bottom, R.drawable.mirrly_settings_chats, R.string.MirrlyChatsReceiveSound, R.string.MirrlySettingsChatsTitle, R.string.MirrlySettingsSearchKwChatSounds, "sound receive message mute"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 22, SearchTarget.ACTION_CHATS, IconBackgroundColors.ORANGE.top, IconBackgroundColors.ORANGE.bottom, R.drawable.mirrly_settings_chats, R.string.MirrlyChatsShowOwnTag, R.string.MirrlySettingsChatsTitle, R.string.MirrlySettingsSearchKwChatTags, "tag rank badge admin role own"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 39, SearchTarget.ACTION_CHATS, IconBackgroundColors.ORANGE.top, IconBackgroundColors.ORANGE.bottom, R.drawable.mirrly_settings_chats, R.string.MirrlyVibrationDisableAll, R.string.MirrlySettingsChatsTitle, R.string.MirrlySettingsSearchKwVibration, "vibration haptic tactile disable mute off"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 12, SearchTarget.ACTION_SUPPORT, IconBackgroundColors.RED.top, IconBackgroundColors.RED.bottom, R.drawable.mirrly_settings_support, R.string.MirrlySettingsSupportTitle, R.string.MirrlySettingsTitle, R.string.MirrlySettingsSearchKwSupport, "donate cloudtips yandex dalink"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 13, SearchTarget.ACTION_ABOUT, 0xFF8A68F7, 0xFF5B4CE6, R.drawable.settings_mirrly, R.string.MirrlySettingsAboutTitle, R.string.MirrlySettingsTitle, R.string.MirrlySettingsSearchKwAbout, "about github gpl license"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 14, SearchTarget.ACTION_PROFILE, IconBackgroundColors.GREEN.top, IconBackgroundColors.GREEN.bottom, R.drawable.mirrly_settings_phone, R.string.MirrlySettingsProfileTitle, R.string.MirrlySettingsTitle, R.string.MirrlySettingsSearchKwProfile, "phone hide spoof spoiler number"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 17, SearchTarget.ACTION_CUSTOMIZATION, IconBackgroundColors.BLUE_DEEP.top, IconBackgroundColors.BLUE_DEEP.bottom, R.drawable.mirrly_settings_customization, R.string.MirrlySettingsCustomizationTitle, R.string.MirrlySettingsTitle, R.string.MirrlySettingsSearchKwCustomization, "customization monet material you theme palette"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 18, SearchTarget.ACTION_CUSTOMIZATION, IconBackgroundColors.BLUE_DEEP.top, IconBackgroundColors.BLUE_DEEP.bottom, R.drawable.mirrly_settings_customization, R.string.MirrlyMonetModeHeader, R.string.MirrlySettingsCustomizationTitle, R.string.MirrlySettingsSearchKwCustomization, "monet amoled gradient wallpaper"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 19, SearchTarget.ACTION_CUSTOMIZATION, IconBackgroundColors.BLUE_DEEP.top, IconBackgroundColors.BLUE_DEEP.bottom, R.drawable.mirrly_settings_customization, R.string.MirrlyGlassHeader, R.string.MirrlySettingsCustomizationTitle, R.string.MirrlySettingsSearchKwCustomization, "glass liquid blur opacity refraction rim saturation"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 20, SearchTarget.ACTION_CUSTOMIZATION, IconBackgroundColors.BLUE_DEEP.top, IconBackgroundColors.BLUE_DEEP.bottom, R.drawable.mirrly_settings_customization, R.string.MirrlyGlassDialogsHeader, R.string.MirrlyGlassHeader, R.string.MirrlySettingsSearchKwCustomization, "dialogs header proxy donate floating glass pill circle"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 21, SearchTarget.ACTION_CUSTOMIZATION, IconBackgroundColors.BLUE_DEEP.top, IconBackgroundColors.BLUE_DEEP.bottom, R.drawable.mirrly_settings_customization, R.string.MirrlyGlassEverywhereToggle, R.string.MirrlyGlassHeader, R.string.MirrlySettingsSearchKwCustomization, "liquid glass everywhere adapt blur interface"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 23, SearchTarget.ACTION_INTERFACE_HOME, IconBackgroundColors.BLUE.top, IconBackgroundColors.BLUE.bottom, R.drawable.mirrly_settings_interface, R.string.MirrlySettingsInterfaceTitle, R.string.MirrlySettingsTitle, R.string.MirrlySettingsSearchKwInterface, "interface home bottom bar menu"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 24, SearchTarget.ACTION_INTERFACE_HOME, IconBackgroundColors.BLUE.top, IconBackgroundColors.BLUE.bottom, R.drawable.mirrly_settings_interface, R.string.MirrlyInterfaceHideStories, R.string.MirrlySettingsInterfaceTitle, R.string.MirrlySettingsSearchKwInterfaceHome, "stories hide top feed"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 25, SearchTarget.ACTION_INTERFACE_HOME, IconBackgroundColors.BLUE.top, IconBackgroundColors.BLUE.bottom, R.drawable.mirrly_settings_interface, R.string.MirrlyInterfaceHideArchive, R.string.MirrlySettingsInterfaceTitle, R.string.MirrlySettingsSearchKwInterfaceHome, "archive hide chats list"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 26, SearchTarget.ACTION_INTERFACE_HOME, IconBackgroundColors.BLUE.top, IconBackgroundColors.BLUE.bottom, R.drawable.mirrly_settings_interface, R.string.MirrlyInterfaceTopThemeBtn, R.string.MirrlySettingsInterfaceTitle, R.string.MirrlySettingsSearchKwInterfaceHome, "theme day night sun moon toggle header"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 27, SearchTarget.ACTION_INTERFACE_HOME, IconBackgroundColors.BLUE.top, IconBackgroundColors.BLUE.bottom, R.drawable.mirrly_settings_interface, R.string.MirrlyInterfaceTopSettingsTitle, R.string.MirrlySettingsInterfaceTitle, R.string.MirrlySettingsSearchKwInterfaceHome, "settings circle right header"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 28, SearchTarget.ACTION_INTERFACE_BOTTOM_BAR, IconBackgroundColors.BLUE.top, IconBackgroundColors.BLUE.bottom, R.drawable.mirrly_settings_interface, R.string.MirrlyInterfaceTabBottomBar, R.string.MirrlySettingsInterfaceTitle, R.string.MirrlySettingsSearchKwInterfaceBottomBar, "bottom bar tabs hide replace quick action custom chat"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 29, SearchTarget.ACTION_INTERFACE_BOTTOM_BAR, IconBackgroundColors.BLUE.top, IconBackgroundColors.BLUE.bottom, R.drawable.mirrly_settings_interface, R.string.MirrlyInterfaceBarCollapseHeader, R.string.MirrlyInterfaceTabBottomBar, R.string.MirrlySettingsSearchKwInterfaceBottomBar, "collapse circle single compact mode"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 30, SearchTarget.ACTION_INTERFACE_MORE_MENU, IconBackgroundColors.BLUE.top, IconBackgroundColors.BLUE.bottom, R.drawable.mirrly_settings_interface, R.string.MirrlyInterfaceTabMoreMenu, R.string.MirrlySettingsInterfaceTitle, R.string.MirrlySettingsSearchKwInterfaceMoreMenu, "three dots menu drawer reorder archive profile"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 31, SearchTarget.ACTION_INTERFACE_MSG_MENU, IconBackgroundColors.BLUE.top, IconBackgroundColors.BLUE.bottom, R.drawable.mirrly_settings_interface, R.string.MirrlyInterfaceTabMsgMenu, R.string.MirrlySettingsInterfaceTitle, R.string.MirrlySettingsSearchKwInterfaceMsgMenu, "message hold context menu reorder save notes"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 32, SearchTarget.ACTION_INTERFACE_HOME, IconBackgroundColors.BLUE.top, IconBackgroundColors.BLUE.bottom, R.drawable.mirrly_settings_interface, R.string.MirrlyInterfaceHideFabStories, R.string.MirrlySettingsInterfaceTitle, R.string.MirrlySettingsSearchKwInterfaceHome, "fab floating button story create hide"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 33, SearchTarget.ACTION_INTERFACE_HOME, IconBackgroundColors.BLUE.top, IconBackgroundColors.BLUE.bottom, R.drawable.mirrly_settings_interface, R.string.MirrlyInterfaceHideFabNewChat, R.string.MirrlySettingsInterfaceTitle, R.string.MirrlySettingsSearchKwInterfaceHome, "fab floating button new chat message compose hide"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 34, SearchTarget.ACTION_INTERFACE_BOTTOM_BAR, IconBackgroundColors.BLUE.top, IconBackgroundColors.BLUE.bottom, R.drawable.mirrly_settings_interface, R.string.MirrlyInterfaceBarAlignHeader, R.string.MirrlyInterfaceTabBottomBar, R.string.MirrlySettingsSearchKwInterfaceBottomBar, "alignment horizontal left center right pill circle"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 35, SearchTarget.ACTION_TRANSLATOR, 0xFF20B4A6, 0xFF13899E, R.drawable.msg_translate, R.string.MirrlyTranslatorSettingsTitle, R.string.MirrlySettingsTitle, R.string.MirrlySettingsSearchKwTranslator, "translate translator aggregator google lingva mymemory send"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 36, SearchTarget.ACTION_TRANSLATOR, 0xFF20B4A6, 0xFF13899E, R.drawable.msg_translate, R.string.MirrlySttEnableToggle, R.string.MirrlyTranslatorSettingsTitle, R.string.MirrlySettingsSearchKwTranslator, "stt voice transcription round video speechrecognizer local"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 37, SearchTarget.ACTION_TRANSLATOR, 0xFF20B4A6, 0xFF13899E, R.drawable.msg_translate, R.string.MirrlyTranslatorMsgButtonToggle, R.string.MirrlyTranslatorSettingsTitle, R.string.MirrlySettingsSearchKwTranslator, "translate button message bubble toggle"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 45, SearchTarget.ACTION_POWER, IconBackgroundColors.ORANGE_DEEP.top, IconBackgroundColors.ORANGE_DEEP.bottom, R.drawable.settings_power, R.string.MirrlyPowerTitle, R.string.MirrlySettingsTitle, R.string.MirrlySettingsSearchKwPower, "power battery energy eco mah mw push keepalive wakelock thermal doze"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 46, SearchTarget.ACTION_POWER, IconBackgroundColors.ORANGE_DEEP.top, IconBackgroundColors.ORANGE_DEEP.bottom, R.drawable.settings_power, R.string.MirrlyPowerAutoEcoTitle, R.string.MirrlyPowerTitle, R.string.MirrlySettingsSearchKwPower, "auto eco battery threshold saver"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 47, SearchTarget.ACTION_POWER, IconBackgroundColors.ORANGE_DEEP.top, IconBackgroundColors.ORANGE_DEEP.bottom, R.drawable.msg_notifications, R.string.MirrlyPowerPushSectionHeader, R.string.MirrlyPowerTitle, R.string.MirrlySettingsSearchKwPower, "push service keepalive interval alarm wakelock notifications"));
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        refreshLiveTelemetry(false);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        pollingStats = true;
        AndroidUtilities.cancelRunOnUIThread(statsUpdateRunnable);
        AndroidUtilities.runOnUIThread(statsUpdateRunnable, 150);
    }

    @Override
    public void onPause() {
        super.onPause();
        pollingStats = false;
        AndroidUtilities.cancelRunOnUIThread(statsUpdateRunnable);
    }

    @Override
    protected void setupMenu(SimpleActionBarMenu menu) {
        searchItem = menu.addItem(0, R.drawable.outline_header_search, getResourceProvider())
                .setIsSearchField(true)
                .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                    @Override
                    public void onSearchCollapse() {
                        searching = false;
                        searchQuery = "";
                        if (listView != null && listView.adapter != null) {
                            listView.adapter.update(true);
                            listView.post(() -> updateActionBarVisible(true));
                        } else {
                            updateActionBarVisible(true);
                        }
                    }

                    @Override
                    public void onSearchExpand() {
                        searching = true;
                        searchQuery = "";
                        updateActionBarVisible(true);
                        if (listView != null && listView.adapter != null) {
                            listView.adapter.update(true);
                        }
                    }

                    @Override
                    public void onTextChanged(EditText editText) {
                        searchQuery = editText != null ? editText.getText().toString() : "";
                        if (listView != null && listView.adapter != null) {
                            listView.adapter.update(true);
                        }
                    }
                });
        searchItem.setSearchFieldHint(LocaleController.getString(R.string.MirrlySettingsSearchHint));
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return !searching && super.isSwipeBackEnabled(event);
    }

    @Override
    protected boolean shouldForceActionBarTitle() {
        return searching;
    }

    @Override
    protected CharSequence getScreenTitle() {
        return LocaleController.getString(R.string.MirrlySettingsTitle);
    }

    private CharSequence[] buildHeroSubtitles() {
        return new CharSequence[]{
                MirrlyUiKit.formatClientVersion() + " • " + LocaleController.getString(R.string.MirrlyHeroSubtitleOpenSource),
                "mirrlyengine • GNU GPLv3"
        };
    }

    private String formatVaultBadge() {
        int notes = MirrlyVaultStorage.getNotesCount();
        int deleted = org.telegram.messenger.mirrly.MirrlyDeletedStorage.getInstance(currentAccount).getCount();
        int total = notes + deleted;
        return total > 0 ? String.valueOf(total) : "";
    }

    private void refreshLiveTelemetry(boolean animated) {
        if (listView == null) {
            return;
        }
        MirrlyUiKit.EngineSnapshot snapshot = MirrlyUiKit.EngineSnapshot.capture();
        for (int i = 0, count = listView.getChildCount(); i < count; i++) {
            View child = listView.getChildAt(i);
            if (child instanceof MirrlyUiKit.TelemetryBannerCell) {
                ((MirrlyUiKit.TelemetryBannerCell) child).updateSnapshot(snapshot, animated);
            }
        }
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        MirrlyConfig.load();
        MirrlyUiKit.EngineSnapshot snapshot = MirrlyUiKit.EngineSnapshot.capture();

        if (searching) {
            items.add(UItem.asSpace(dp(4)));
            String normalized = searchQuery != null ? searchQuery.trim().toLowerCase(Locale.ROOT) : "";
            int matched = 0;
            for (SearchTarget target : searchTargets) {
                String title = LocaleController.getString(target.titleRes);
                String cat = LocaleController.getString(target.categoryRes);
                String locKeywords = target.keywordsRes != 0 ? LocaleController.getString(target.keywordsRes) : "";
                boolean hit = TextUtils.isEmpty(normalized)
                        || title.toLowerCase(Locale.ROOT).contains(normalized)
                        || cat.toLowerCase(Locale.ROOT).contains(normalized)
                        || locKeywords.toLowerCase(Locale.ROOT).contains(normalized)
                        || target.extraKeywords.contains(normalized);
                if (hit) {
                    matched++;
                    items.add(SettingsActivity.SettingCell.Factory.of(
                            target.id,
                            target.iconTop,
                            target.iconBottom,
                            target.iconRes,
                            title,
                            cat
                    ));
                }
            }
            if (matched == 0) {
                items.add(UItem.asCenterShadow(LocaleController.getString(R.string.MirrlySettingsSearchEmpty)));
            }
            return;
        }

        items.add(MirrlyUiKit.HeroCardView.Factory.of(
                ID_HERO,
                new MirrlyUiKit.HeroSpec(
                        MirrlyUiKit.HeroSpec.MODE_BRAND,
                        R.drawable.mirrly_settings_emblem,
                        true,
                        LocaleController.getString(R.string.MirrlySettingsTitle),
                        buildHeroSubtitles(),
                        LocaleController.getString(R.string.MirrlySettingsHeroEasterEgg)
                )
        ));

        items.add(MirrlyUiKit.TelemetryBannerCell.Factory.of(ID_TELEMETRY_BANNER, snapshot));
        items.add(UItem.asShadow(null));

        items.add(MirrlyUiKit.SupportBannerCell.Factory.of(
                ID_OPEN_SUPPORT,
                IconBackgroundColors.RED.top,
                IconBackgroundColors.RED.bottom,
                R.drawable.mirrly_settings_support,
                LocaleController.getString(R.string.MirrlySettingsSupportTitle),
                LocaleController.getString(R.string.MirrlySettingsSupportSub)
        ));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlySettingsCategoriesHeader)));
        items.add(SettingsActivity.SettingCell.Factory.of(
                ID_CAT_NETWORK,
                IconBackgroundColors.CYAN.top,
                IconBackgroundColors.CYAN.bottom,
                R.drawable.mirrly_settings_engine,
                LocaleController.getString(R.string.MirrlySettingsNetworkTitle),
                LocaleController.getString(R.string.MirrlySettingsNetworkSub),
                snapshot.getShortBadge()
        ));
        items.add(SettingsActivity.SettingCell.Factory.of(
                ID_CAT_POWER,
                IconBackgroundColors.ORANGE_DEEP.top,
                IconBackgroundColors.ORANGE_DEEP.bottom,
                R.drawable.settings_power,
                LocaleController.getString(R.string.MirrlyPowerTitle),
                LocaleController.getString(R.string.MirrlyPowerHubSub),
                org.telegram.messenger.mirrly.MirrlyPowerTelemetry.getHubShortBadge()
        ));
        items.add(SettingsActivity.SettingCell.Factory.of(
                ID_CAT_VAULT,
                IconBackgroundColors.PURPLE.top,
                IconBackgroundColors.PURPLE.bottom,
                R.drawable.mirrly_settings_vault,
                LocaleController.getString(R.string.MirrlySettingsVaultTitle),
                LocaleController.getString(R.string.MirrlySettingsVaultSub),
                formatVaultBadge()
        ));
        items.add(SettingsActivity.SettingCell.Factory.of(
                ID_CAT_PROFILE,
                IconBackgroundColors.GREEN.top,
                IconBackgroundColors.GREEN.bottom,
                R.drawable.mirrly_settings_phone,
                LocaleController.getString(R.string.MirrlySettingsProfileTitle),
                LocaleController.getString(R.string.MirrlySettingsProfileSub)
        ));
        items.add(SettingsActivity.SettingCell.Factory.of(
                ID_CAT_CHATS,
                IconBackgroundColors.ORANGE.top,
                IconBackgroundColors.ORANGE.bottom,
                R.drawable.mirrly_settings_chats,
                LocaleController.getString(R.string.MirrlySettingsChatsTitle),
                LocaleController.getString(R.string.MirrlySettingsChatsSub)
        ));
        items.add(SettingsActivity.SettingCell.Factory.of(
                ID_CAT_TRANSLATOR,
                0xFF20B4A6,
                0xFF13899E,
                R.drawable.msg_translate,
                LocaleController.getString(R.string.MirrlyTranslatorSettingsTitle),
                LocaleController.getString(R.string.MirrlySettingsTranslatorSub),
                org.telegram.messenger.mirrly.MirrlyTranslatorEngine.getProviderShortName(MirrlyConfig.getTranslatorProvider())
        ));
        items.add(SettingsActivity.SettingCell.Factory.of(
                ID_CAT_CUSTOMIZATION,
                IconBackgroundColors.BLUE_DEEP.top,
                IconBackgroundColors.BLUE_DEEP.bottom,
                R.drawable.mirrly_settings_customization,
                LocaleController.getString(R.string.MirrlySettingsCustomizationTitle),
                LocaleController.getString(R.string.MirrlySettingsCustomizationSub)
        ));
        items.add(SettingsActivity.SettingCell.Factory.of(
                ID_CAT_INTERFACE,
                IconBackgroundColors.BLUE.top,
                IconBackgroundColors.BLUE.bottom,
                R.drawable.mirrly_settings_interface,
                LocaleController.getString(R.string.MirrlySettingsInterfaceTitle),
                LocaleController.getString(R.string.MirrlySettingsInterfaceSub)
        ));
        items.add(UItem.asShadow(null));

        items.add(SettingsActivity.SettingCell.Factory.of(
                ID_OPEN_ABOUT,
                0xFF8A68F7,
                0xFF5B4CE6,
                R.drawable.settings_mirrly,
                LocaleController.getString(R.string.MirrlySettingsAboutTitle),
                LocaleController.getString(R.string.MirrlySettingsAboutSub)
        ));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item == null) {
            return;
        }
        if (item.id >= ID_SEARCH_BASE) {
            for (SearchTarget target : searchTargets) {
                if (target.id == item.id) {
                    executeSearchAction(target.actionType);
                    return;
                }
            }
            return;
        }
        switch (item.id) {
            case ID_TELEMETRY_BANNER:
                MirrlyUiKit.showEngineDiagnosticsSheet(this, getResourceProvider(), this::refreshAllState);
                break;

            case ID_OPEN_SUPPORT:
                presentFragment(new MirrlyAboutActivity(MirrlyAboutActivity.TAB_SUPPORT));
                break;

            case ID_CAT_NETWORK:
                presentFragment(new MirrlyNetworkSettingsActivity());
                break;

            case ID_CAT_POWER:
                presentFragment(new MirrlyPowerActivity());
                break;

            case ID_CAT_VAULT:
                presentFragment(new MirrlyVaultSettingsActivity());
                break;

            case ID_CAT_PROFILE:
                presentFragment(new MirrlyProfileSettingsActivity());
                break;

            case ID_CAT_CHATS:
                presentFragment(new MirrlyChatsSettingsActivity());
                break;

            case ID_CAT_TRANSLATOR:
                presentFragment(new MirrlyTranslatorSettingsActivity());
                break;

            case ID_CAT_CUSTOMIZATION:
                presentFragment(new MirrlyCustomizationActivity());
                break;

            case ID_CAT_INTERFACE:
                presentFragment(new MirrlyInterfaceSettingsActivity());
                break;

            case ID_OPEN_ABOUT:
                presentFragment(new MirrlyAboutActivity(MirrlyAboutActivity.TAB_PROJECT));
                break;
        }
    }

    private void executeSearchAction(int actionType) {
        AndroidUtilities.hideKeyboard(fragmentView);
        switch (actionType) {
            case SearchTarget.ACTION_NETWORK:
                presentFragment(new MirrlyNetworkSettingsActivity());
                break;
            case SearchTarget.ACTION_VAULT:
                presentFragment(new MirrlyVaultSettingsActivity());
                break;
            case SearchTarget.ACTION_CHATS:
                presentFragment(new MirrlyChatsSettingsActivity());
                break;
            case SearchTarget.ACTION_DIAGNOSTICS:
                MirrlyUiKit.showEngineDiagnosticsSheet(this, getResourceProvider(), this::refreshAllState);
                break;
            case SearchTarget.ACTION_SUPPORT:
                presentFragment(new MirrlyAboutActivity(MirrlyAboutActivity.TAB_SUPPORT));
                break;
            case SearchTarget.ACTION_ABOUT:
                presentFragment(new MirrlyAboutActivity(MirrlyAboutActivity.TAB_PROJECT));
                break;
            case SearchTarget.ACTION_PROXY_LIST:
                presentFragment(new ProxyListActivity());
                break;
            case SearchTarget.ACTION_NOTES_CHAT:
                presentFragment(new MirrlyNotesChatActivity());
                break;
            case SearchTarget.ACTION_PROFILE:
                presentFragment(new MirrlyProfileSettingsActivity());
                break;
            case SearchTarget.ACTION_CUSTOMIZATION:
                presentFragment(new MirrlyCustomizationActivity());
                break;
            case SearchTarget.ACTION_INTERFACE_HOME:
                presentFragment(new MirrlyInterfaceSettingsActivity(MirrlyInterfaceSettingsActivity.TAB_HOME));
                break;
            case SearchTarget.ACTION_INTERFACE_BOTTOM_BAR:
                presentFragment(new MirrlyInterfaceSettingsActivity(MirrlyInterfaceSettingsActivity.TAB_BOTTOM_BAR));
                break;
            case SearchTarget.ACTION_INTERFACE_MORE_MENU:
                presentFragment(new MirrlyInterfaceSettingsActivity(MirrlyInterfaceSettingsActivity.TAB_MORE_MENU));
                break;
            case SearchTarget.ACTION_INTERFACE_MSG_MENU:
                presentFragment(new MirrlyInterfaceSettingsActivity(MirrlyInterfaceSettingsActivity.TAB_MESSAGE_MENU));
                break;
            case SearchTarget.ACTION_TRANSLATOR:
                presentFragment(new MirrlyTranslatorSettingsActivity());
                break;
            case SearchTarget.ACTION_DATACENTER_STATUS:
                presentFragment(new MirrlyDatacenterActivity());
                break;
            case SearchTarget.ACTION_DELETED_CHAT:
                presentFragment(new MirrlyDeletedChatActivity());
                break;
            case SearchTarget.ACTION_DELETED_CHATS_LIST:
                presentFragment(new MirrlyDeletedChatsListActivity());
                break;
            case SearchTarget.ACTION_POWER:
                presentFragment(new MirrlyPowerActivity());
                break;
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item == null) {
            return false;
        }
        if (item.id == ID_TELEMETRY_BANNER || item.id == ID_CAT_NETWORK) {
            MirrlyUiKit.showQuickEngineOptions(
                    this,
                    view,
                    listView != null ? listView.getClipBackground(view) : null,
                    getResourceProvider(),
                    this::refreshAllState
            );
            return true;
        }
        return false;
    }

    private void refreshAllState() {
        refreshLiveTelemetry(true);
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.proxySettingsChanged
                || id == NotificationCenter.didSetNewTheme
                || id == NotificationCenter.mirrlyNotesDidUpdate
                || id == NotificationCenter.mirrlyDeletedDidUpdate
                || id == NotificationCenter.mirrlyTranslatorSettingsChanged
                || id == NotificationCenter.mirrlyPowerStatsUpdated
                || id == NotificationCenter.mirrlyPowerPolicyChanged) {
            refreshAllState();
        }
    }
}
