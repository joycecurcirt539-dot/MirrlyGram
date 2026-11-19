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
    private static final int ID_CAT_CHATS = 12;
    private static final int ID_OPEN_ABOUT = 13;

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
        buildSearchIndex();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.mirrlyNotesDidUpdate);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        pollingStats = false;
        AndroidUtilities.cancelRunOnUIThread(statsUpdateRunnable);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.mirrlyNotesDidUpdate);
        super.onFragmentDestroy();
    }

    private void buildSearchIndex() {
        searchTargets.clear();
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 1, SearchTarget.ACTION_NETWORK, IconBackgroundColors.CYAN.top, IconBackgroundColors.CYAN.bottom, R.drawable.mirrly_settings_engine, R.string.MirrlySettingsNetworkTitle, R.string.MirrlySettingsTitle, R.string.MirrlySettingsSearchKwNetwork, "socks5 mtproto transport proxy"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 2, SearchTarget.ACTION_NETWORK, IconBackgroundColors.CYAN.top, IconBackgroundColors.CYAN.bottom, R.drawable.mirrly_settings_engine, R.string.MirrlyNetworkTlsEnable, R.string.MirrlySettingsNetworkTitle, R.string.MirrlySettingsSearchKwTls, "tls 1.2 1.3 sni"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 3, SearchTarget.ACTION_NETWORK, IconBackgroundColors.CYAN.top, IconBackgroundColors.CYAN.bottom, R.drawable.mirrly_settings_engine, R.string.MirrlyNetworkTlsSplit, R.string.MirrlySettingsNetworkTitle, R.string.MirrlySettingsSearchKwTlsSplit, "fragment split clienthello"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 4, SearchTarget.ACTION_NETWORK, IconBackgroundColors.CYAN.top, IconBackgroundColors.CYAN.bottom, R.drawable.mirrly_settings_engine, R.string.MirrlyNetworkReconnect, R.string.MirrlySettingsNetworkTitle, R.string.MirrlySettingsSearchKwReconnect, "socket reconnect"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 5, SearchTarget.ACTION_PROXY_LIST, IconBackgroundColors.CYAN.top, IconBackgroundColors.CYAN.bottom, R.drawable.mirrly_settings_engine, R.string.MirrlyNetworkProxyList, R.string.MirrlySettingsNetworkTitle, R.string.MirrlySettingsSearchKwProxyList, "proxy list"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 6, SearchTarget.ACTION_DIAGNOSTICS, IconBackgroundColors.GREEN.top, IconBackgroundColors.GREEN.bottom, R.drawable.mirrly_settings_engine, R.string.MirrlyDiagTitle, R.string.MirrlySettingsTitle, R.string.MirrlySettingsSearchKwDiag, "diagnostics uptime ws pool stats"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 7, SearchTarget.ACTION_VAULT, IconBackgroundColors.PURPLE.top, IconBackgroundColors.PURPLE.bottom, R.drawable.mirrly_settings_vault, R.string.MirrlySettingsVaultTitle, R.string.MirrlySettingsTitle, R.string.MirrlySettingsSearchKwVault, "vault notes edit history"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 8, SearchTarget.ACTION_NOTES_CHAT, IconBackgroundColors.PURPLE.top, IconBackgroundColors.PURPLE.bottom, R.drawable.mirrly_settings_vault, R.string.MirrlyVaultOpenNotesChat, R.string.MirrlySettingsVaultTitle, R.string.MirrlySettingsSearchKwNotesChat, "notes chat"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 9, SearchTarget.ACTION_VAULT, IconBackgroundColors.PURPLE.top, IconBackgroundColors.PURPLE.bottom, R.drawable.mirrly_settings_vault, R.string.MirrlyVaultSaveHistory, R.string.MirrlySettingsVaultTitle, R.string.MirrlySettingsSearchKwSaveHistory, "edit history"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 10, SearchTarget.ACTION_CHATS, IconBackgroundColors.ORANGE.top, IconBackgroundColors.ORANGE.bottom, R.drawable.mirrly_settings_chats, R.string.MirrlyChatsConfirmStickers, R.string.MirrlySettingsChatsTitle, R.string.MirrlySettingsSearchKwConfirmStickers, "stickers confirm"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 11, SearchTarget.ACTION_CHATS, IconBackgroundColors.ORANGE.top, IconBackgroundColors.ORANGE.bottom, R.drawable.mirrly_settings_chats, R.string.MirrlyChatsBackCameraRounds, R.string.MirrlySettingsChatsTitle, R.string.MirrlySettingsSearchKwBackCamera, "camera round video"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 12, SearchTarget.ACTION_SUPPORT, IconBackgroundColors.RED.top, IconBackgroundColors.RED.bottom, R.drawable.mirrly_settings_support, R.string.MirrlySettingsSupportTitle, R.string.MirrlySettingsTitle, R.string.MirrlySettingsSearchKwSupport, "donate cloudtips yandex dalink"));
        searchTargets.add(new SearchTarget(ID_SEARCH_BASE + 13, SearchTarget.ACTION_ABOUT, 0xFF8A68F7, 0xFF5B4CE6, R.drawable.settings_mirrly, R.string.MirrlySettingsAboutTitle, R.string.MirrlySettingsTitle, R.string.MirrlySettingsSearchKwAbout, "about github gpl license"));
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
        return notes > 0 ? String.valueOf(notes) : "";
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
                ID_CAT_VAULT,
                IconBackgroundColors.PURPLE.top,
                IconBackgroundColors.PURPLE.bottom,
                R.drawable.mirrly_settings_vault,
                LocaleController.getString(R.string.MirrlySettingsVaultTitle),
                LocaleController.getString(R.string.MirrlySettingsVaultSub),
                formatVaultBadge()
        ));
        items.add(SettingsActivity.SettingCell.Factory.of(
                ID_CAT_CHATS,
                IconBackgroundColors.ORANGE.top,
                IconBackgroundColors.ORANGE.bottom,
                R.drawable.mirrly_settings_chats,
                LocaleController.getString(R.string.MirrlySettingsChatsTitle),
                LocaleController.getString(R.string.MirrlySettingsChatsSub)
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

            case ID_CAT_VAULT:
                presentFragment(new MirrlyVaultSettingsActivity());
                break;

            case ID_CAT_CHATS:
                presentFragment(new MirrlyChatsSettingsActivity());
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
                || id == NotificationCenter.mirrlyNotesDidUpdate) {
            refreshAllState();
        }
    }
}
