package org.telegram.messenger.mirrly;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.NotificationCenter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persistent configuration for MirrlyGram home screen, bottom navigation bar,
 * three-dots menu, and chat message hold menu customizations.
 */
public final class MirrlyInterfaceConfig {

    private static final String PREFS_NAME = "mirrly_config";

    private static final String KEY_HIDE_TOP_STORIES = "iface_hide_top_stories";
    private static final String KEY_HIDE_ARCHIVE_CHATS = "iface_hide_archive_chats";
    private static final String KEY_SHOW_TOP_THEME_TOGGLE = "iface_show_top_theme_toggle";
    private static final String KEY_SHOW_TOP_PROXY_BTN = "iface_show_top_proxy_btn";
    private static final String KEY_SHOW_TOP_DONATE_BTN = "iface_show_top_donate_btn";
    private static final String KEY_SHOW_TOP_MORE_BTN = "iface_show_top_more_btn";
    private static final String KEY_TOP_SETTINGS_CIRCLE_MODE = "iface_top_settings_circle_mode";
    private static final String KEY_HIDE_FAB_STORIES = "iface_hide_fab_stories";
    private static final String KEY_HIDE_FAB_NEW_CHAT = "iface_hide_fab_new_chat";

    private static final String KEY_BAR_COLLAPSE_MODE = "iface_bar_collapse_mode";
    private static final String KEY_BAR_ALIGNMENT = "iface_bar_alignment";
    private static final String KEY_BAR_SLOT_MODE_PREFIX = "iface_bar_slot_mode_";
    private static final String KEY_BAR_SLOT_ACTION_PREFIX = "iface_bar_slot_action_";
    private static final String KEY_BAR_SLOT_CHAT_ID_PREFIX = "iface_bar_slot_chat_id_";
    private static final String KEY_BAR_SLOT_CHAT_TITLE_PREFIX = "iface_bar_slot_chat_title_";

    private static final String KEY_DRAWER_ORDER = "iface_drawer_order";
    private static final String KEY_DRAWER_VIS_PREFIX = "iface_drawer_vis_";
    private static final String KEY_DRAWER_ARCHIVE_USER_SET = "drawer_archive_user_set";
    private static final String KEY_DRAWER_PROFILE_USER_SET = "drawer_profile_user_set";

    private static final String KEY_MSG_MENU_ORDER = "iface_msg_menu_order";
    private static final String KEY_MSG_MENU_VIS_PREFIX = "iface_msg_menu_vis_";

    // Top-bar settings circle modes
    public static final int TOP_SETTINGS_AUTO = 0;
    public static final int TOP_SETTINGS_ALWAYS = 1;
    public static final int TOP_SETTINGS_OFF = 2;

    // Bottom bar slots (matches MainTabsActivity logical tabs)
    public static final int SLOT_CHATS = 0;
    public static final int SLOT_CONTACTS = 1;
    public static final int SLOT_SETTINGS_OR_CALLS = 2;
    public static final int SLOT_PROFILE = 3;
    public static final int SLOT_COUNT = 4;

    // Per-slot display modes
    public static final int SLOT_MODE_DEFAULT = 0;
    public static final int SLOT_MODE_HIDDEN = 1;
    public static final int SLOT_MODE_CUSTOM = 2;

    // Bottom bar minimized single-circle collapse modes
    public static final int COLLAPSE_OFF = 0;
    public static final int COLLAPSE_AUTO = 1;
    public static final int COLLAPSE_ALWAYS = 2;

    // Bottom bar horizontal alignment (when shrunk or collapsed into a single circle)
    public static final int ALIGN_LEFT = 0;
    public static final int ALIGN_CENTER = 1;
    public static final int ALIGN_RIGHT = 2;

    // Quick action identifiers (22 actions)
    public static final String QUICK_SAVED = "saved";
    public static final String QUICK_NOTES = "notes";
    public static final String QUICK_DELETED = "deleted";
    public static final String QUICK_MIRRLY_SETTINGS = "mirrly_settings";
    public static final String QUICK_CUSTOMIZATION = "customization";
    public static final String QUICK_NETWORK = "network";
    public static final String QUICK_INTERFACE_SETTINGS = "interface";
    public static final String QUICK_ARCHIVE = "archive";
    public static final String QUICK_CALLS = "calls";
    public static final String QUICK_CONTACTS = "contacts";
    public static final String QUICK_SETTINGS = "settings";
    public static final String QUICK_PROFILE = "profile";
    public static final String QUICK_SEARCH = "search";
    public static final String QUICK_NEW_CHAT = "new_chat";
    public static final String QUICK_NEW_GROUP = "new_group";
    public static final String QUICK_NEW_CHANNEL = "new_channel";
    public static final String QUICK_FOLDERS = "folders";
    public static final String QUICK_PROXY_LIST = "proxy_list";
    public static final String QUICK_THEME_TOGGLE = "theme_toggle";
    public static final String QUICK_DEVICES = "devices";
    public static final String QUICK_APPEARANCE = "appearance";
    public static final String QUICK_WALLET = "wallet";
    public static final String QUICK_CUSTOM_CHAT = "custom_chat";

    public static final List<String> ALL_QUICK_ACTIONS = Collections.unmodifiableList(Arrays.asList(
            QUICK_SAVED,
            QUICK_NOTES,
            QUICK_DELETED,
            QUICK_MIRRLY_SETTINGS,
            QUICK_CUSTOMIZATION,
            QUICK_NETWORK,
            QUICK_INTERFACE_SETTINGS,
            QUICK_ARCHIVE,
            QUICK_CALLS,
            QUICK_CONTACTS,
            QUICK_SETTINGS,
            QUICK_PROFILE,
            QUICK_SEARCH,
            QUICK_NEW_CHAT,
            QUICK_NEW_GROUP,
            QUICK_NEW_CHANNEL,
            QUICK_FOLDERS,
            QUICK_PROXY_LIST,
            QUICK_THEME_TOGGLE,
            QUICK_DEVICES,
            QUICK_APPEARANCE,
            QUICK_WALLET,
            QUICK_CUSTOM_CHAT
    ));

    // Three-dots menu item IDs (14 items)
    public static final String DRAWER_THEME_TOGGLE = "theme_toggle";
    public static final String DRAWER_ARCHIVE = "archive";
    public static final String DRAWER_PROFILE = "profile";
    public static final String DRAWER_NEW_GROUP = "new_group";
    public static final String DRAWER_NEW_CHANNEL = "new_channel";
    public static final String DRAWER_SAVED = "saved";
    public static final String DRAWER_MIRRLY_NOTES = "mirrly_notes";
    public static final String DRAWER_MIRRLY_DELETED = "mirrly_deleted";
    public static final String DRAWER_CONTACTS = "contacts";
    public static final String DRAWER_CALLS = "calls";
    public static final String DRAWER_WALLET_BOTS = "wallet_bots";
    public static final String DRAWER_TG_SETTINGS = "tg_settings";
    public static final String DRAWER_MIRRLY_SETTINGS = "mirrly_settings";
    public static final String DRAWER_SUPPORT = "support";
    public static final String DRAWER_PROXY = "proxy";

    public static final List<String> DEFAULT_DRAWER_ORDER = Collections.unmodifiableList(Arrays.asList(
            DRAWER_THEME_TOGGLE,
            DRAWER_ARCHIVE,
            DRAWER_PROFILE,
            DRAWER_NEW_GROUP,
            DRAWER_NEW_CHANNEL,
            DRAWER_SAVED,
            DRAWER_MIRRLY_NOTES,
            DRAWER_MIRRLY_DELETED,
            DRAWER_CONTACTS,
            DRAWER_CALLS,
            DRAWER_WALLET_BOTS,
            DRAWER_TG_SETTINGS,
            DRAWER_MIRRLY_SETTINGS,
            DRAWER_SUPPORT,
            DRAWER_PROXY
    ));

    // Message hold menu group keys (16 keys)
    public static final String MSG_REPLY = "reply";
    public static final String MSG_COPY = "copy";
    public static final String MSG_FORWARD = "forward";
    public static final String MSG_PIN = "pin";
    public static final String MSG_EDIT = "edit";
    public static final String MSG_TRANSLATE = "translate";
    public static final String MSG_TRANSCRIBE = "transcribe";
    public static final String MSG_SAVE_MEDIA = "save_media";
    public static final String MSG_SHARE = "share";
    public static final String MSG_COPY_LINK = "copy_link";
    public static final String MSG_REPLIES_THREAD = "replies_thread";
    public static final String MSG_SELECT = "select";
    public static final String MSG_STATS = "stats";
    public static final String MSG_SAVE_TO_NOTES = "save_to_notes";
    public static final String MSG_REPORT = "report";
    public static final String MSG_DELETE = "delete";

    public static final List<String> DEFAULT_MSG_MENU_ORDER = Collections.unmodifiableList(Arrays.asList(
            MSG_REPLY,
            MSG_COPY,
            MSG_FORWARD,
            MSG_PIN,
            MSG_EDIT,
            MSG_TRANSLATE,
            MSG_TRANSCRIBE,
            MSG_SAVE_MEDIA,
            MSG_SHARE,
            MSG_COPY_LINK,
            MSG_REPLIES_THREAD,
            MSG_SELECT,
            MSG_STATS,
            MSG_SAVE_TO_NOTES,
            MSG_REPORT,
            MSG_DELETE
    ));

    // Dedicated ChatActivity option IDs for Mirrly custom message menu entries
    public static final int CHAT_OPTION_MIRRLY_SAVE_NOTE_ALT = 999;
    public static final int CHAT_OPTION_MIRRLY_SAVE_NOTE = 9993;
    public static final int CHAT_OPTION_SELECT = 9994;

    // ChatActivity.OPTION_* integer constants (mirrored to avoid circular UI dependency)
    private static final int TG_OPTION_RETRY = 0;
    private static final int TG_OPTION_DELETE = 1;
    private static final int TG_OPTION_FORWARD = 2;
    private static final int TG_OPTION_COPY = 3;
    private static final int TG_OPTION_SAVE_TO_GALLERY = 4;
    private static final int TG_OPTION_APPLY_LOCALIZATION_OR_THEME = 5;
    private static final int TG_OPTION_SHARE = 6;
    private static final int TG_OPTION_SAVE_TO_GALLERY2 = 7;
    private static final int TG_OPTION_REPLY = 8;
    private static final int TG_OPTION_ADD_TO_STICKERS_OR_MASKS = 9;
    private static final int TG_OPTION_SAVE_TO_DOWNLOADS_OR_MUSIC = 10;
    private static final int TG_OPTION_ADD_TO_GIFS = 11;
    private static final int TG_OPTION_EDIT = 12;
    private static final int TG_OPTION_PIN = 13;
    private static final int TG_OPTION_UNPIN = 14;
    private static final int TG_OPTION_COPY_PHONE_NUMBER = 16;
    private static final int TG_OPTION_ADD_STICKER_TO_FAVORITES = 20;
    private static final int TG_OPTION_DELETE_STICKER_FROM_FAVORITES = 21;
    private static final int TG_OPTION_COPY_LINK = 22;
    private static final int TG_OPTION_REPORT_CHAT = 23;
    private static final int TG_OPTION_CANCEL_SENDING = 24;
    private static final int TG_OPTION_VIEW_REPLIES_OR_THREAD = 27;
    private static final int TG_OPTION_STATISTICS = 28;
    private static final int TG_OPTION_TRANSLATE = 29;
    private static final int TG_OPTION_TRANSCRIBE = 30;
    private static final int TG_OPTION_HIDE_SPONSORED_MESSAGE = 31;
    private static final int TG_OPTION_VIEW_IN_TOPIC = 32;
    private static final int TG_OPTION_SEND_NOW = 100;
    private static final int TG_OPTION_VIEW_STATISTICS = 115;

    private static final Set<String> VALID_QUICK_ACTIONS = new HashSet<>(ALL_QUICK_ACTIONS);
    private static final Set<String> VALID_DRAWER_ITEMS = new HashSet<>(DEFAULT_DRAWER_ORDER);
    private static final Set<String> VALID_MSG_MENU_ITEMS = new HashSet<>(DEFAULT_MSG_MENU_ORDER);

    private static volatile boolean loaded = false;

    private static volatile boolean hideTopStories = false;
    private static volatile boolean hideArchiveFromChats = false;
    private static volatile boolean showTopThemeToggle = true;
    private static volatile boolean showTopProxyBtn = true;
    private static volatile boolean showTopDonateBtn = true;
    private static volatile boolean showTopMoreBtn = true;
    private static volatile int topSettingsCircleMode = TOP_SETTINGS_AUTO;
    private static volatile boolean hideFabStories = false;
    private static volatile boolean hideFabNewChat = false;

    private static final java.util.concurrent.atomic.AtomicIntegerArray slotModes = new java.util.concurrent.atomic.AtomicIntegerArray(SLOT_COUNT);
    private static final String[] slotActions = new String[]{
            QUICK_SAVED, QUICK_SAVED, QUICK_SAVED, QUICK_SAVED
    };
    private static final long[] slotCustomChatIds = new long[SLOT_COUNT];
    private static final String[] slotCustomChatTitles = new String[]{"", "", "", ""};
    private static volatile int bottomBarCollapseMode = COLLAPSE_AUTO;
    private static volatile int bottomBarAlignment = ALIGN_CENTER;

    private static final ArrayList<String> drawerMenuOrder = new ArrayList<>(DEFAULT_DRAWER_ORDER);
    private static final Map<String, Boolean> drawerVisibility = new HashMap<>();
    private static volatile boolean drawerArchiveUserSet = false;
    private static volatile boolean drawerProfileUserSet = false;

    private static final ArrayList<String> msgMenuOrder = new ArrayList<>(DEFAULT_MSG_MENU_ORDER);
    private static final Map<String, Boolean> msgMenuVisibility = new HashMap<>();

    static {
        initDefaultMaps();
    }

    private MirrlyInterfaceConfig() {
    }

    private static void initDefaultMaps() {
        drawerVisibility.clear();
        for (String id : DEFAULT_DRAWER_ORDER) {
            drawerVisibility.put(id, getDefaultDrawerVisibility(id));
        }
        msgMenuVisibility.clear();
        for (String key : DEFAULT_MSG_MENU_ORDER) {
            msgMenuVisibility.put(key, true);
        }
    }

    private static boolean getDefaultDrawerVisibility(String id) {
        if (id == null) {
            return false;
        }
        switch (id) {
            case DRAWER_THEME_TOGGLE:
            case DRAWER_NEW_GROUP:
            case DRAWER_SAVED:
            case DRAWER_MIRRLY_NOTES:
            case DRAWER_WALLET_BOTS:
            case DRAWER_TG_SETTINGS:
            case DRAWER_PROXY:
                return true;
            case DRAWER_ARCHIVE:
            case DRAWER_PROFILE:
            case DRAWER_NEW_CHANNEL:
            case DRAWER_CONTACTS:
            case DRAWER_CALLS:
            case DRAWER_MIRRLY_SETTINGS:
            case DRAWER_SUPPORT:
            default:
                return false;
        }
    }

    public static void load() {
        if (loaded) {
            return;
        }
        loadInternal();
    }

    private static synchronized void loadInternal() {
        if (loaded) {
            return;
        }
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);

        hideTopStories = prefs.getBoolean(KEY_HIDE_TOP_STORIES, false);
        hideArchiveFromChats = prefs.getBoolean(KEY_HIDE_ARCHIVE_CHATS, false);
        showTopThemeToggle = prefs.getBoolean(KEY_SHOW_TOP_THEME_TOGGLE, true);
        showTopProxyBtn = prefs.getBoolean(KEY_SHOW_TOP_PROXY_BTN, true);
        showTopDonateBtn = prefs.getBoolean(KEY_SHOW_TOP_DONATE_BTN, true);
        showTopMoreBtn = prefs.getBoolean(KEY_SHOW_TOP_MORE_BTN, true);
        topSettingsCircleMode = normalizeTopSettingsMode(prefs.getInt(KEY_TOP_SETTINGS_CIRCLE_MODE, TOP_SETTINGS_AUTO));
        hideFabStories = prefs.getBoolean(KEY_HIDE_FAB_STORIES, false);
        hideFabNewChat = prefs.getBoolean(KEY_HIDE_FAB_NEW_CHAT, false);

        bottomBarCollapseMode = normalizeCollapseMode(prefs.getInt(KEY_BAR_COLLAPSE_MODE, COLLAPSE_AUTO));
        bottomBarAlignment = normalizeAlignment(prefs.getInt(KEY_BAR_ALIGNMENT, ALIGN_CENTER));
        for (int i = 0; i < SLOT_COUNT; i++) {
            slotModes.set(i, normalizeSlotMode(prefs.getInt(KEY_BAR_SLOT_MODE_PREFIX + i, SLOT_MODE_DEFAULT)));
            slotCustomChatIds[i] = prefs.getLong(KEY_BAR_SLOT_CHAT_ID_PREFIX + i, 0L);
            String title = prefs.getString(KEY_BAR_SLOT_CHAT_TITLE_PREFIX + i, "");
            slotCustomChatTitles[i] = title != null ? title : "";
            String action = prefs.getString(KEY_BAR_SLOT_ACTION_PREFIX + i, QUICK_SAVED);
            slotActions[i] = normalizeQuickActionForSlotLocked(i, action);
        }

        parseOrder(prefs.getString(KEY_DRAWER_ORDER, ""), DEFAULT_DRAWER_ORDER, VALID_DRAWER_ITEMS, drawerMenuOrder);
        drawerArchiveUserSet = prefs.getBoolean(KEY_DRAWER_ARCHIVE_USER_SET, false);
        drawerProfileUserSet = prefs.getBoolean(KEY_DRAWER_PROFILE_USER_SET, false);
        drawerVisibility.clear();
        for (String id : DEFAULT_DRAWER_ORDER) {
            drawerVisibility.put(id, prefs.getBoolean(KEY_DRAWER_VIS_PREFIX + id, getDefaultDrawerVisibility(id)));
        }

        parseOrder(prefs.getString(KEY_MSG_MENU_ORDER, ""), DEFAULT_MSG_MENU_ORDER, VALID_MSG_MENU_ITEMS, msgMenuOrder);
        msgMenuVisibility.clear();
        for (String key : DEFAULT_MSG_MENU_ORDER) {
            msgMenuVisibility.put(key, prefs.getBoolean(KEY_MSG_MENU_VIS_PREFIX + key, true));
        }

        loaded = true;
    }

    @Nullable
    private static SharedPreferences getPrefs() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return null;
        }
        return context.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
    }

    private static void postNotification(int notificationId) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            NotificationCenter.getGlobalInstance().postNotificationName(notificationId);
        } else {
            AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(notificationId));
        }
    }

    private static void postBottomBarAndInterfaceNotifications() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            NotificationCenter center = NotificationCenter.getGlobalInstance();
            center.postNotificationName(NotificationCenter.mirrlyBottomBarConfigChanged);
            center.postNotificationName(NotificationCenter.mirrlyInterfaceSettingsChanged);
        } else {
            AndroidUtilities.runOnUIThread(() -> {
                NotificationCenter center = NotificationCenter.getGlobalInstance();
                center.postNotificationName(NotificationCenter.mirrlyBottomBarConfigChanged);
                center.postNotificationName(NotificationCenter.mirrlyInterfaceSettingsChanged);
            });
        }
    }

    // -------------------------------------------------------------------------
    // Home & Header Settings
    // -------------------------------------------------------------------------

    public static boolean isHideTopStories() {
        load();
        return hideTopStories;
    }

    public static void setHideTopStories(boolean value) {
        boolean changed;
        synchronized (MirrlyInterfaceConfig.class) {
            load();
            changed = hideTopStories != value;
            hideTopStories = value;
            SharedPreferences prefs = getPrefs();
            if (prefs != null) {
                prefs.edit().putBoolean(KEY_HIDE_TOP_STORIES, value).apply();
            }
        }
        if (changed) {
            postNotification(NotificationCenter.mirrlyInterfaceSettingsChanged);
        }
    }

    public static boolean isHideArchiveFromChats() {
        load();
        return hideArchiveFromChats;
    }

    public static void setHideArchiveFromChats(boolean value) {
        boolean changed;
        synchronized (MirrlyInterfaceConfig.class) {
            load();
            changed = hideArchiveFromChats != value;
            hideArchiveFromChats = value;
            SharedPreferences prefs = getPrefs();
            if (prefs != null) {
                prefs.edit().putBoolean(KEY_HIDE_ARCHIVE_CHATS, value).apply();
            }
        }
        if (changed) {
            postNotification(NotificationCenter.mirrlyInterfaceSettingsChanged);
        }
    }

    public static boolean isShowTopThemeToggle() {
        load();
        return showTopThemeToggle;
    }

    public static void setShowTopThemeToggle(boolean value) {
        boolean changed;
        synchronized (MirrlyInterfaceConfig.class) {
            load();
            changed = showTopThemeToggle != value;
            showTopThemeToggle = value;
            SharedPreferences prefs = getPrefs();
            if (prefs != null) {
                prefs.edit().putBoolean(KEY_SHOW_TOP_THEME_TOGGLE, value).apply();
            }
        }
        if (changed) {
            postNotification(NotificationCenter.mirrlyInterfaceSettingsChanged);
        }
    }

    public static boolean isShowTopProxyBtn() {
        load();
        return showTopProxyBtn;
    }

    public static void setShowTopProxyBtn(boolean value) {
        boolean changed;
        synchronized (MirrlyInterfaceConfig.class) {
            load();
            changed = showTopProxyBtn != value;
            showTopProxyBtn = value;
            SharedPreferences prefs = getPrefs();
            if (prefs != null) {
                prefs.edit().putBoolean(KEY_SHOW_TOP_PROXY_BTN, value).apply();
            }
        }
        if (changed) {
            postNotification(NotificationCenter.mirrlyInterfaceSettingsChanged);
        }
    }

    public static boolean isShowTopDonateBtn() {
        load();
        return showTopDonateBtn;
    }

    public static void setShowTopDonateBtn(boolean value) {
        boolean changed;
        synchronized (MirrlyInterfaceConfig.class) {
            load();
            changed = showTopDonateBtn != value;
            showTopDonateBtn = value;
            SharedPreferences prefs = getPrefs();
            if (prefs != null) {
                prefs.edit().putBoolean(KEY_SHOW_TOP_DONATE_BTN, value).apply();
            }
        }
        if (changed) {
            postNotification(NotificationCenter.mirrlyInterfaceSettingsChanged);
        }
    }

    public static boolean isShowTopMoreBtn() {
        load();
        return showTopMoreBtn;
    }

    public static void setShowTopMoreBtn(boolean value) {
        boolean changed;
        synchronized (MirrlyInterfaceConfig.class) {
            load();
            changed = showTopMoreBtn != value;
            showTopMoreBtn = value;
            SharedPreferences prefs = getPrefs();
            if (prefs != null) {
                prefs.edit().putBoolean(KEY_SHOW_TOP_MORE_BTN, value).apply();
            }
        }
        if (changed) {
            postNotification(NotificationCenter.mirrlyInterfaceSettingsChanged);
        }
    }

    public static int getTopSettingsCircleMode() {
        load();
        return topSettingsCircleMode;
    }

    public static void setTopSettingsCircleMode(int mode) {
        int normalized = normalizeTopSettingsMode(mode);
        boolean changed;
        synchronized (MirrlyInterfaceConfig.class) {
            load();
            changed = topSettingsCircleMode != normalized;
            topSettingsCircleMode = normalized;
            SharedPreferences prefs = getPrefs();
            if (prefs != null) {
                prefs.edit().putInt(KEY_TOP_SETTINGS_CIRCLE_MODE, normalized).apply();
            }
        }
        if (changed) {
            postNotification(NotificationCenter.mirrlyInterfaceSettingsChanged);
        }
    }

    public static boolean shouldShowTopSettingsCircle(boolean showCallsTab) {
        load();
        int mode = topSettingsCircleMode;
        if (mode == TOP_SETTINGS_OFF) {
            return false;
        }
        if (mode == TOP_SETTINGS_ALWAYS) {
            return true;
        }
        return isBottomBarCompletelyHidden()
                || showCallsTab
                || getSlotMode(SLOT_SETTINGS_OR_CALLS) != SLOT_MODE_DEFAULT;
    }

    public static boolean isHideFabStories() {
        load();
        return hideFabStories;
    }

    public static void setHideFabStories(boolean value) {
        boolean changed;
        synchronized (MirrlyInterfaceConfig.class) {
            load();
            changed = hideFabStories != value;
            hideFabStories = value;
            SharedPreferences prefs = getPrefs();
            if (prefs != null) {
                prefs.edit().putBoolean(KEY_HIDE_FAB_STORIES, value).apply();
            }
        }
        if (changed) {
            postNotification(NotificationCenter.mirrlyInterfaceSettingsChanged);
        }
    }

    public static boolean isHideFabNewChat() {
        load();
        return hideFabNewChat;
    }

    public static void setHideFabNewChat(boolean value) {
        boolean changed;
        synchronized (MirrlyInterfaceConfig.class) {
            load();
            changed = hideFabNewChat != value;
            hideFabNewChat = value;
            SharedPreferences prefs = getPrefs();
            if (prefs != null) {
                prefs.edit().putBoolean(KEY_HIDE_FAB_NEW_CHAT, value).apply();
            }
        }
        if (changed) {
            postNotification(NotificationCenter.mirrlyInterfaceSettingsChanged);
        }
    }

    public static boolean shouldShowProfileInMoreMenuFallback() {
        load();
        return getSlotMode(SLOT_PROFILE) != SLOT_MODE_DEFAULT;
    }

    public static void resetHomeSettingsDefaults() {
        synchronized (MirrlyInterfaceConfig.class) {
            load();
            hideTopStories = false;
            hideArchiveFromChats = false;
            showTopThemeToggle = true;
            showTopProxyBtn = true;
            showTopDonateBtn = true;
            showTopMoreBtn = true;
            topSettingsCircleMode = TOP_SETTINGS_AUTO;
            hideFabStories = false;
            hideFabNewChat = false;
            SharedPreferences prefs = getPrefs();
            if (prefs != null) {
                prefs.edit()
                        .putBoolean(KEY_HIDE_TOP_STORIES, false)
                        .putBoolean(KEY_HIDE_ARCHIVE_CHATS, false)
                        .putBoolean(KEY_SHOW_TOP_THEME_TOGGLE, true)
                        .putBoolean(KEY_SHOW_TOP_PROXY_BTN, true)
                        .putBoolean(KEY_SHOW_TOP_DONATE_BTN, true)
                        .putBoolean(KEY_SHOW_TOP_MORE_BTN, true)
                        .putInt(KEY_TOP_SETTINGS_CIRCLE_MODE, TOP_SETTINGS_AUTO)
                        .putBoolean(KEY_HIDE_FAB_STORIES, false)
                        .putBoolean(KEY_HIDE_FAB_NEW_CHAT, false)
                        .apply();
            }
        }
        postNotification(NotificationCenter.mirrlyInterfaceSettingsChanged);
    }

    // -------------------------------------------------------------------------
    // Bottom Navigation Bar
    // -------------------------------------------------------------------------

    public static int getSlotMode(int slot) {
        load();
        if (!isValidSlot(slot)) {
            return SLOT_MODE_DEFAULT;
        }
        return slotModes.get(slot);
    }

    public static void setSlotMode(int slot, int mode) {
        if (!isValidSlot(slot)) {
            return;
        }
        int normalizedMode = normalizeSlotMode(mode);
        synchronized (MirrlyInterfaceConfig.class) {
            load();
            slotModes.set(slot, normalizedMode);
            if (normalizedMode == SLOT_MODE_CUSTOM) {
                slotActions[slot] = normalizeQuickActionForSlotLocked(slot, slotActions[slot]);
            }
            SharedPreferences prefs = getPrefs();
            if (prefs != null) {
                prefs.edit()
                        .putInt(KEY_BAR_SLOT_MODE_PREFIX + slot, normalizedMode)
                        .putString(KEY_BAR_SLOT_ACTION_PREFIX + slot, slotActions[slot])
                        .apply();
            }
        }
        postBottomBarAndInterfaceNotifications();
    }

    @NonNull
    public static synchronized String getSlotCustomAction(int slot) {
        load();
        if (!isValidSlot(slot)) {
            return QUICK_SAVED;
        }
        return normalizeQuickActionForSlotLocked(slot, slotActions[slot]);
    }

    public static void setSlotCustomAction(int slot, @Nullable String action) {
        if (!isValidSlot(slot)) {
            return;
        }
        synchronized (MirrlyInterfaceConfig.class) {
            load();
            String normalized = normalizeQuickActionForSlotLocked(slot, action);
            slotActions[slot] = normalized;
            slotModes.set(slot, SLOT_MODE_CUSTOM);
            SharedPreferences prefs = getPrefs();
            if (prefs != null) {
                prefs.edit()
                        .putString(KEY_BAR_SLOT_ACTION_PREFIX + slot, normalized)
                        .putInt(KEY_BAR_SLOT_MODE_PREFIX + slot, SLOT_MODE_CUSTOM)
                        .apply();
            }
        }
        postBottomBarAndInterfaceNotifications();
    }

    public static synchronized long getSlotCustomChatId(int slot) {
        load();
        if (!isValidSlot(slot)) {
            return 0L;
        }
        return slotCustomChatIds[slot];
    }

    @NonNull
    public static synchronized String getSlotCustomChatTitle(int slot) {
        load();
        if (!isValidSlot(slot)) {
            return "";
        }
        return slotCustomChatTitles[slot] != null ? slotCustomChatTitles[slot] : "";
    }

    public static void setSlotCustomChat(int slot, long dialogId, @Nullable String title) {
        if (!isValidSlot(slot)) {
            return;
        }
        String cleanTitle = title != null ? title.trim() : "";
        synchronized (MirrlyInterfaceConfig.class) {
            load();
            if (dialogId == 0L) {
                slotCustomChatIds[slot] = 0L;
                slotCustomChatTitles[slot] = "";
                if (QUICK_CUSTOM_CHAT.equals(slotActions[slot])) {
                    slotActions[slot] = QUICK_SAVED;
                }
            } else {
                slotCustomChatIds[slot] = dialogId;
                slotCustomChatTitles[slot] = cleanTitle;
                slotActions[slot] = QUICK_CUSTOM_CHAT;
                slotModes.set(slot, SLOT_MODE_CUSTOM);
            }
            SharedPreferences prefs = getPrefs();
            if (prefs != null) {
                prefs.edit()
                        .putLong(KEY_BAR_SLOT_CHAT_ID_PREFIX + slot, slotCustomChatIds[slot])
                        .putString(KEY_BAR_SLOT_CHAT_TITLE_PREFIX + slot, slotCustomChatTitles[slot])
                        .putString(KEY_BAR_SLOT_ACTION_PREFIX + slot, slotActions[slot])
                        .putInt(KEY_BAR_SLOT_MODE_PREFIX + slot, slotModes.get(slot))
                        .apply();
            }
        }
        postBottomBarAndInterfaceNotifications();
    }

    public static int getBottomBarCollapseMode() {
        load();
        return bottomBarCollapseMode;
    }

    public static void setBottomBarCollapseMode(int mode) {
        int normalized = normalizeCollapseMode(mode);
        synchronized (MirrlyInterfaceConfig.class) {
            load();
            bottomBarCollapseMode = normalized;
            SharedPreferences prefs = getPrefs();
            if (prefs != null) {
                prefs.edit().putInt(KEY_BAR_COLLAPSE_MODE, normalized).apply();
            }
        }
        postBottomBarAndInterfaceNotifications();
    }

    public static int getBottomBarAlignment() {
        load();
        return bottomBarAlignment;
    }

    public static void setBottomBarAlignment(int alignment) {
        int normalized = normalizeAlignment(alignment);
        boolean changed;
        synchronized (MirrlyInterfaceConfig.class) {
            load();
            changed = bottomBarAlignment != normalized;
            bottomBarAlignment = normalized;
            SharedPreferences prefs = getPrefs();
            if (prefs != null) {
                prefs.edit().putInt(KEY_BAR_ALIGNMENT, normalized).apply();
            }
        }
        if (changed) {
            postNotification(NotificationCenter.mirrlyBottomBarConfigChanged);
        }
    }

    public static int getVisibleBottomTabsCount() {
        load();
        int count = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (slotModes.get(i) != SLOT_MODE_HIDDEN) {
                count++;
            }
        }
        return count;
    }

    public static int getNativeBottomTabsCount() {
        load();
        int count = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (slotModes.get(i) == SLOT_MODE_DEFAULT) {
                count++;
            }
        }
        return count;
    }

    public static boolean isBottomBarCompletelyHidden() {
        return getVisibleBottomTabsCount() == 0;
    }

    public static boolean shouldCollapseToCircle() {
        load();
        int visible = getVisibleBottomTabsCount();
        if (visible <= 0) {
            return false;
        }
        int mode = bottomBarCollapseMode;
        return mode == COLLAPSE_ALWAYS
                || (mode == COLLAPSE_AUTO && visible <= 2);
    }

    public static void resetBottomBarDefaults() {
        synchronized (MirrlyInterfaceConfig.class) {
            load();
            bottomBarCollapseMode = COLLAPSE_AUTO;
            bottomBarAlignment = ALIGN_CENTER;
            SharedPreferences prefs = getPrefs();
            SharedPreferences.Editor editor = prefs != null ? prefs.edit() : null;
            if (editor != null) {
                editor.putInt(KEY_BAR_COLLAPSE_MODE, COLLAPSE_AUTO)
                        .putInt(KEY_BAR_ALIGNMENT, ALIGN_CENTER);
            }
            for (int i = 0; i < SLOT_COUNT; i++) {
                slotModes.set(i, SLOT_MODE_DEFAULT);
                slotActions[i] = QUICK_SAVED;
                slotCustomChatIds[i] = 0L;
                slotCustomChatTitles[i] = "";
                if (editor != null) {
                    editor.putInt(KEY_BAR_SLOT_MODE_PREFIX + i, SLOT_MODE_DEFAULT)
                            .putString(KEY_BAR_SLOT_ACTION_PREFIX + i, QUICK_SAVED)
                            .putLong(KEY_BAR_SLOT_CHAT_ID_PREFIX + i, 0L)
                            .putString(KEY_BAR_SLOT_CHAT_TITLE_PREFIX + i, "");
                }
            }
            if (editor != null) {
                editor.apply();
            }
        }
        postBottomBarAndInterfaceNotifications();
    }

    public static boolean isValidQuickAction(@Nullable String action) {
        return action != null && VALID_QUICK_ACTIONS.contains(action);
    }

    // -------------------------------------------------------------------------
    // Three-Dots Menu ("More Options" / Drawer)
    // -------------------------------------------------------------------------

    @NonNull
    public static synchronized ArrayList<String> getDrawerMenuOrder() {
        load();
        return new ArrayList<>(drawerMenuOrder);
    }

    public static synchronized boolean isDrawerItemVisible(@Nullable String id) {
        load();
        if (id == null || !VALID_DRAWER_ITEMS.contains(id)) {
            return false;
        }
        if (DRAWER_ARCHIVE.equals(id) && !drawerArchiveUserSet) {
            return hideArchiveFromChats;
        }
        if (DRAWER_PROFILE.equals(id) && !drawerProfileUserSet) {
            return shouldShowProfileInMoreMenuFallback();
        }
        Boolean value = drawerVisibility.get(id);
        return value != null ? value : getDefaultDrawerVisibility(id);
    }

    public static synchronized boolean isDrawerItemAutoEnabled(@Nullable String id) {
        load();
        if (DRAWER_ARCHIVE.equals(id)) {
            return !drawerArchiveUserSet && hideArchiveFromChats;
        }
        if (DRAWER_PROFILE.equals(id)) {
            return !drawerProfileUserSet && shouldShowProfileInMoreMenuFallback();
        }
        return false;
    }

    public static synchronized boolean isDrawerArchiveUserSet() {
        load();
        return drawerArchiveUserSet;
    }

    public static synchronized boolean isDrawerProfileUserSet() {
        load();
        return drawerProfileUserSet;
    }

    public static boolean setDrawerItemVisible(@Nullable String id, boolean visible) {
        if (id == null || !VALID_DRAWER_ITEMS.contains(id)) {
            return false;
        }
        synchronized (MirrlyInterfaceConfig.class) {
            load();
            if (!visible && isDrawerItemVisible(id) && getDrawerEnabledCount() <= 1) {
                return false;
            }
            if (DRAWER_ARCHIVE.equals(id)) {
                drawerArchiveUserSet = true;
            } else if (DRAWER_PROFILE.equals(id)) {
                drawerProfileUserSet = true;
            }
            drawerVisibility.put(id, visible);
            SharedPreferences prefs = getPrefs();
            if (prefs != null) {
                SharedPreferences.Editor editor = prefs.edit().putBoolean(KEY_DRAWER_VIS_PREFIX + id, visible);
                if (DRAWER_ARCHIVE.equals(id)) {
                    editor.putBoolean(KEY_DRAWER_ARCHIVE_USER_SET, true);
                } else if (DRAWER_PROFILE.equals(id)) {
                    editor.putBoolean(KEY_DRAWER_PROFILE_USER_SET, true);
                }
                editor.apply();
            }
        }
        postNotification(NotificationCenter.mirrlyInterfaceSettingsChanged);
        return true;
    }

    public static boolean moveDrawerItem(@Nullable String id, int delta) {
        if (id == null || delta == 0 || !VALID_DRAWER_ITEMS.contains(id)) {
            return false;
        }
        synchronized (MirrlyInterfaceConfig.class) {
            load();
            if (!moveItemInList(drawerMenuOrder, id, delta)) {
                return false;
            }
            SharedPreferences prefs = getPrefs();
            if (prefs != null) {
                prefs.edit().putString(KEY_DRAWER_ORDER, serializeOrder(drawerMenuOrder)).apply();
            }
        }
        postNotification(NotificationCenter.mirrlyInterfaceSettingsChanged);
        return true;
    }

    public static synchronized int getDrawerEnabledCount() {
        load();
        int count = 0;
        for (String id : drawerMenuOrder) {
            if (isDrawerItemVisible(id)) {
                count++;
            }
        }
        return count;
    }

    public static void resetDrawerMenuDefaults() {
        synchronized (MirrlyInterfaceConfig.class) {
            load();
            drawerMenuOrder.clear();
            drawerMenuOrder.addAll(DEFAULT_DRAWER_ORDER);
            drawerArchiveUserSet = false;
            drawerProfileUserSet = false;
            SharedPreferences prefs = getPrefs();
            SharedPreferences.Editor editor = prefs != null ? prefs.edit() : null;
            if (editor != null) {
                editor.putString(KEY_DRAWER_ORDER, serializeOrder(drawerMenuOrder))
                        .putBoolean(KEY_DRAWER_ARCHIVE_USER_SET, false)
                        .putBoolean(KEY_DRAWER_PROFILE_USER_SET, false);
            }
            for (String id : DEFAULT_DRAWER_ORDER) {
                boolean def = getDefaultDrawerVisibility(id);
                drawerVisibility.put(id, def);
                if (editor != null) {
                    editor.putBoolean(KEY_DRAWER_VIS_PREFIX + id, def);
                }
            }
            if (editor != null) {
                editor.apply();
            }
        }
        postNotification(NotificationCenter.mirrlyInterfaceSettingsChanged);
    }

    // -------------------------------------------------------------------------
    // Message Hold Menu (ChatActivity)
    // -------------------------------------------------------------------------

    @NonNull
    public static synchronized ArrayList<String> getMessageMenuOrder() {
        load();
        return new ArrayList<>(msgMenuOrder);
    }

    public static synchronized boolean isMessageMenuItemVisible(@Nullable String key) {
        load();
        if (key == null || !VALID_MSG_MENU_ITEMS.contains(key)) {
            return true;
        }
        Boolean value = msgMenuVisibility.get(key);
        return value == null || value;
    }

    public static boolean setMessageMenuItemVisible(@Nullable String key, boolean visible) {
        if (key == null || !VALID_MSG_MENU_ITEMS.contains(key)) {
            return false;
        }
        synchronized (MirrlyInterfaceConfig.class) {
            load();
            if (!visible && isMessageMenuItemVisible(key) && getMessageMenuEnabledCount() <= 1) {
                return false;
            }
            msgMenuVisibility.put(key, visible);
            SharedPreferences prefs = getPrefs();
            if (prefs != null) {
                prefs.edit().putBoolean(KEY_MSG_MENU_VIS_PREFIX + key, visible).apply();
            }
        }
        postNotification(NotificationCenter.mirrlyMessageMenuConfigChanged);
        return true;
    }

    public static boolean moveMessageMenuItem(@Nullable String key, int delta) {
        if (key == null || delta == 0 || !VALID_MSG_MENU_ITEMS.contains(key)) {
            return false;
        }
        synchronized (MirrlyInterfaceConfig.class) {
            load();
            if (!moveItemInList(msgMenuOrder, key, delta)) {
                return false;
            }
            SharedPreferences prefs = getPrefs();
            if (prefs != null) {
                prefs.edit().putString(KEY_MSG_MENU_ORDER, serializeOrder(msgMenuOrder)).apply();
            }
        }
        postNotification(NotificationCenter.mirrlyMessageMenuConfigChanged);
        return true;
    }

    public static synchronized int getMessageMenuEnabledCount() {
        load();
        int count = 0;
        for (String key : msgMenuOrder) {
            if (isMessageMenuItemVisible(key)) {
                count++;
            }
        }
        return count;
    }

    public static void resetMessageMenuDefaults() {
        synchronized (MirrlyInterfaceConfig.class) {
            load();
            msgMenuOrder.clear();
            msgMenuOrder.addAll(DEFAULT_MSG_MENU_ORDER);
            SharedPreferences prefs = getPrefs();
            SharedPreferences.Editor editor = prefs != null ? prefs.edit() : null;
            if (editor != null) {
                editor.putString(KEY_MSG_MENU_ORDER, serializeOrder(msgMenuOrder));
            }
            for (String key : DEFAULT_MSG_MENU_ORDER) {
                msgMenuVisibility.put(key, true);
                if (editor != null) {
                    editor.putBoolean(KEY_MSG_MENU_VIS_PREFIX + key, true);
                }
            }
            if (editor != null) {
                editor.apply();
            }
        }
        postNotification(NotificationCenter.mirrlyMessageMenuConfigChanged);
    }

    /**
     * Maps a ChatActivity.OPTION_* integer ID to its customizable message menu group key,
     * or returns null for transient/required/system options that must stay unhidden.
     */
    @Nullable
    public static String mapChatOptionToKey(int option) {
        switch (option) {
            case TG_OPTION_REPLY:
                return MSG_REPLY;
            case TG_OPTION_COPY:
            case TG_OPTION_COPY_PHONE_NUMBER:
                return MSG_COPY;
            case TG_OPTION_FORWARD:
                return MSG_FORWARD;
            case TG_OPTION_PIN:
            case TG_OPTION_UNPIN:
                return MSG_PIN;
            case TG_OPTION_EDIT:
                return MSG_EDIT;
            case TG_OPTION_TRANSLATE:
                return MSG_TRANSLATE;
            case TG_OPTION_TRANSCRIBE:
                return MSG_TRANSCRIBE;
            case TG_OPTION_SAVE_TO_GALLERY:
            case TG_OPTION_SAVE_TO_GALLERY2:
            case TG_OPTION_ADD_TO_STICKERS_OR_MASKS:
            case TG_OPTION_SAVE_TO_DOWNLOADS_OR_MUSIC:
            case TG_OPTION_ADD_TO_GIFS:
            case TG_OPTION_ADD_STICKER_TO_FAVORITES:
            case TG_OPTION_DELETE_STICKER_FROM_FAVORITES:
                return MSG_SAVE_MEDIA;
            case TG_OPTION_SHARE:
                return MSG_SHARE;
            case TG_OPTION_COPY_LINK:
                return MSG_COPY_LINK;
            case TG_OPTION_VIEW_REPLIES_OR_THREAD:
            case TG_OPTION_VIEW_IN_TOPIC:
                return MSG_REPLIES_THREAD;
            case CHAT_OPTION_SELECT:
                return MSG_SELECT;
            case TG_OPTION_STATISTICS:
            case TG_OPTION_VIEW_STATISTICS:
                return MSG_STATS;
            case CHAT_OPTION_MIRRLY_SAVE_NOTE_ALT:
            case CHAT_OPTION_MIRRLY_SAVE_NOTE:
                return MSG_SAVE_TO_NOTES;
            case TG_OPTION_REPORT_CHAT:
                return MSG_REPORT;
            case TG_OPTION_DELETE:
                return MSG_DELETE;
            case TG_OPTION_RETRY:
            case TG_OPTION_APPLY_LOCALIZATION_OR_THEME:
            case TG_OPTION_CANCEL_SENDING:
            case TG_OPTION_HIDE_SPONSORED_MESSAGE:
            case TG_OPTION_SEND_NOW:
            default:
                return null;
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static boolean isValidSlot(int slot) {
        return slot >= 0 && slot < SLOT_COUNT;
    }

    private static int normalizeTopSettingsMode(int mode) {
        if (mode < TOP_SETTINGS_AUTO || mode > TOP_SETTINGS_OFF) {
            return TOP_SETTINGS_AUTO;
        }
        return mode;
    }

    private static int normalizeSlotMode(int mode) {
        if (mode < SLOT_MODE_DEFAULT || mode > SLOT_MODE_CUSTOM) {
            return SLOT_MODE_DEFAULT;
        }
        return mode;
    }

    private static int normalizeCollapseMode(int mode) {
        if (mode < COLLAPSE_OFF || mode > COLLAPSE_ALWAYS) {
            return COLLAPSE_AUTO;
        }
        return mode;
    }

    private static int normalizeAlignment(int alignment) {
        if (alignment < ALIGN_LEFT || alignment > ALIGN_RIGHT) {
            return ALIGN_CENTER;
        }
        return alignment;
    }

    @NonNull
    private static String normalizeQuickActionForSlotLocked(int slot, @Nullable String action) {
        if (TextUtils.isEmpty(action) || !VALID_QUICK_ACTIONS.contains(action)) {
            return QUICK_SAVED;
        }
        if (QUICK_CUSTOM_CHAT.equals(action) && slotCustomChatIds[slot] == 0L) {
            return QUICK_SAVED;
        }
        return action;
    }

    private static boolean moveItemInList(ArrayList<String> list, String item, int delta) {
        int currentIndex = list.indexOf(item);
        if (currentIndex < 0) {
            return false;
        }
        int targetIndex = Math.max(0, Math.min(list.size() - 1, currentIndex + delta));
        if (targetIndex == currentIndex) {
            return false;
        }
        list.remove(currentIndex);
        list.add(targetIndex, item);
        return true;
    }

    private static void parseOrder(@Nullable String saved, List<String> defaults, Set<String> validSet, ArrayList<String> out) {
        out.clear();
        HashSet<String> seen = new HashSet<>();
        if (!TextUtils.isEmpty(saved)) {
            String[] parts = saved.split(",");
            for (String raw : parts) {
                String token = raw != null ? raw.trim() : "";
                if (!TextUtils.isEmpty(token) && validSet.contains(token) && seen.add(token)) {
                    out.add(token);
                }
            }
        }
        for (String def : defaults) {
            if (seen.add(def)) {
                out.add(def);
            }
        }
    }

    private static String serializeOrder(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(list.get(i));
        }
        return sb.toString();
    }
}
