package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.mirrly.MirrlyConfig;
import org.telegram.messenger.mirrly.MirrlyInterfaceConfig;
import org.telegram.messenger.mirrly.MirrlyMonetThemeHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;

/**
 * Interactive miniature live previews and shared icon/title resolvers for
 * {@link MirrlyInterfaceSettingsActivity}.
 */
public final class MirrlyInterfacePreviews {

    private MirrlyInterfacePreviews() {
    }

    // -------------------------------------------------------------------------
    // Shared Action / Menu Icon & Localization Resolvers
    // -------------------------------------------------------------------------

    @DrawableRes
    public static int resolveQuickActionIcon(@Nullable String action) {
        if (action == null) {
            return R.drawable.msg_saved;
        }
        switch (action) {
            case MirrlyInterfaceConfig.QUICK_SAVED:
                return R.drawable.msg_saved;
            case MirrlyInterfaceConfig.QUICK_NOTES:
                return R.drawable.outline_saved_24;
            case MirrlyInterfaceConfig.QUICK_DELETED:
                return R.drawable.msg_delete;
            case MirrlyInterfaceConfig.QUICK_MIRRLY_SETTINGS:
                return R.drawable.settings_mirrly;
            case MirrlyInterfaceConfig.QUICK_CUSTOMIZATION:
                return R.drawable.msg_customize;
            case MirrlyInterfaceConfig.QUICK_NETWORK:
                return R.drawable.mirrly_about_proxy;
            case MirrlyInterfaceConfig.QUICK_INTERFACE_SETTINGS:
                return R.drawable.mirrly_settings_interface;
            case MirrlyInterfaceConfig.QUICK_ARCHIVE:
                return R.drawable.msg_archive;
            case MirrlyInterfaceConfig.QUICK_CALLS:
                return R.drawable.msg_calls;
            case MirrlyInterfaceConfig.QUICK_CONTACTS:
                return R.drawable.msg_contacts;
            case MirrlyInterfaceConfig.QUICK_SETTINGS:
                return R.drawable.msg_settings;
            case MirrlyInterfaceConfig.QUICK_PROFILE:
                return R.drawable.msg_openprofile;
            case MirrlyInterfaceConfig.QUICK_SEARCH:
                return R.drawable.msg_search;
            case MirrlyInterfaceConfig.QUICK_NEW_CHAT:
                return R.drawable.msg_message;
            case MirrlyInterfaceConfig.QUICK_NEW_GROUP:
                return R.drawable.msg_groups;
            case MirrlyInterfaceConfig.QUICK_NEW_CHANNEL:
                return R.drawable.msg_channel;
            case MirrlyInterfaceConfig.QUICK_FOLDERS:
                return R.drawable.msg_folders;
            case MirrlyInterfaceConfig.QUICK_PROXY_LIST:
                return R.drawable.mirrly_about_proxy;
            case MirrlyInterfaceConfig.QUICK_THEME_TOGGLE:
                return R.drawable.msg_theme;
            case MirrlyInterfaceConfig.QUICK_DEVICES:
                return R.drawable.msg2_devices;
            case MirrlyInterfaceConfig.QUICK_APPEARANCE:
                return R.drawable.msg_palette;
            case MirrlyInterfaceConfig.QUICK_WALLET:
                return R.drawable.filled_wallet_move;
            case MirrlyInterfaceConfig.QUICK_CUSTOM_CHAT:
                return R.drawable.msg_discuss;
            default:
                return R.drawable.msg_saved;
        }
    }

    @NonNull
    public static String resolveQuickActionName(@Nullable String action) {
        if (action == null) {
            return LocaleController.getString(R.string.MirrlyQuickNameSaved);
        }
        switch (action) {
            case MirrlyInterfaceConfig.QUICK_SAVED:
                return LocaleController.getString(R.string.MirrlyQuickNameSaved);
            case MirrlyInterfaceConfig.QUICK_NOTES:
                return LocaleController.getString(R.string.MirrlyQuickNameNotes);
            case MirrlyInterfaceConfig.QUICK_DELETED:
                return LocaleController.getString(R.string.MirrlyQuickNameDeleted);
            case MirrlyInterfaceConfig.QUICK_MIRRLY_SETTINGS:
                return LocaleController.getString(R.string.MirrlyQuickNameMirrlySettings);
            case MirrlyInterfaceConfig.QUICK_CUSTOMIZATION:
                return LocaleController.getString(R.string.MirrlyQuickNameCustomization);
            case MirrlyInterfaceConfig.QUICK_NETWORK:
                return LocaleController.getString(R.string.MirrlyQuickNameNetwork);
            case MirrlyInterfaceConfig.QUICK_INTERFACE_SETTINGS:
                return LocaleController.getString(R.string.MirrlyQuickNameInterface);
            case MirrlyInterfaceConfig.QUICK_ARCHIVE:
                return LocaleController.getString(R.string.MirrlyQuickNameArchive);
            case MirrlyInterfaceConfig.QUICK_CALLS:
                return LocaleController.getString(R.string.MirrlyQuickNameCalls);
            case MirrlyInterfaceConfig.QUICK_CONTACTS:
                return LocaleController.getString(R.string.MirrlyQuickNameContacts);
            case MirrlyInterfaceConfig.QUICK_SETTINGS:
                return LocaleController.getString(R.string.MirrlyQuickNameSettings);
            case MirrlyInterfaceConfig.QUICK_PROFILE:
                return LocaleController.getString(R.string.MirrlyQuickNameProfile);
            case MirrlyInterfaceConfig.QUICK_SEARCH:
                return LocaleController.getString(R.string.MirrlyQuickNameSearch);
            case MirrlyInterfaceConfig.QUICK_NEW_CHAT:
                return LocaleController.getString(R.string.MirrlyQuickNameNewChat);
            case MirrlyInterfaceConfig.QUICK_NEW_GROUP:
                return LocaleController.getString(R.string.MirrlyQuickNameNewGroup);
            case MirrlyInterfaceConfig.QUICK_NEW_CHANNEL:
                return LocaleController.getString(R.string.MirrlyQuickNameNewChannel);
            case MirrlyInterfaceConfig.QUICK_FOLDERS:
                return LocaleController.getString(R.string.MirrlyQuickNameFolders);
            case MirrlyInterfaceConfig.QUICK_PROXY_LIST:
                return LocaleController.getString(R.string.MirrlyQuickNameProxyList);
            case MirrlyInterfaceConfig.QUICK_THEME_TOGGLE:
                return LocaleController.getString(R.string.MirrlyQuickNameThemeToggle);
            case MirrlyInterfaceConfig.QUICK_DEVICES:
                return LocaleController.getString(R.string.MirrlyQuickNameDevices);
            case MirrlyInterfaceConfig.QUICK_APPEARANCE:
                return LocaleController.getString(R.string.MirrlyQuickNameAppearance);
            case MirrlyInterfaceConfig.QUICK_WALLET:
                return LocaleController.getString(R.string.MirrlyQuickNameWallet);
            case MirrlyInterfaceConfig.QUICK_CUSTOM_CHAT:
                return LocaleController.getString(R.string.MirrlyQuickNameCustomChat);
            default:
                return LocaleController.getString(R.string.MirrlyQuickNameSaved);
        }
    }

    @NonNull
    public static String resolveQuickActionShort(int currentAccount, @Nullable String action, long customDialogId, @Nullable String customChatTitle) {
        if (action == null) {
            return LocaleController.getString(R.string.MirrlyQuickShortSaved);
        }
        if (MirrlyInterfaceConfig.QUICK_CUSTOM_CHAT.equals(action)) {
            if (customDialogId != 0L) {
                String liveTitle = resolveDialogTitle(currentAccount, customDialogId);
                if (!TextUtils.isEmpty(liveTitle)) {
                    return liveTitle;
                }
            }
            if (!TextUtils.isEmpty(customChatTitle)) {
                return customChatTitle;
            }
            return LocaleController.getString(R.string.MirrlyQuickShortCustomChat);
        }
        switch (action) {
            case MirrlyInterfaceConfig.QUICK_SAVED:
                return LocaleController.getString(R.string.MirrlyQuickShortSaved);
            case MirrlyInterfaceConfig.QUICK_NOTES:
                return LocaleController.getString(R.string.MirrlyQuickShortNotes);
            case MirrlyInterfaceConfig.QUICK_DELETED:
                return LocaleController.getString(R.string.MirrlyQuickShortDeleted);
            case MirrlyInterfaceConfig.QUICK_MIRRLY_SETTINGS:
                return LocaleController.getString(R.string.MirrlyQuickShortMirrlySettings);
            case MirrlyInterfaceConfig.QUICK_CUSTOMIZATION:
                return LocaleController.getString(R.string.MirrlyQuickShortCustomization);
            case MirrlyInterfaceConfig.QUICK_NETWORK:
                return LocaleController.getString(R.string.MirrlyQuickShortNetwork);
            case MirrlyInterfaceConfig.QUICK_INTERFACE_SETTINGS:
                return LocaleController.getString(R.string.MirrlyQuickShortInterface);
            case MirrlyInterfaceConfig.QUICK_ARCHIVE:
                return LocaleController.getString(R.string.MirrlyQuickShortArchive);
            case MirrlyInterfaceConfig.QUICK_CALLS:
                return LocaleController.getString(R.string.MirrlyQuickShortCalls);
            case MirrlyInterfaceConfig.QUICK_CONTACTS:
                return LocaleController.getString(R.string.MirrlyQuickShortContacts);
            case MirrlyInterfaceConfig.QUICK_SETTINGS:
                return LocaleController.getString(R.string.MirrlyQuickShortSettings);
            case MirrlyInterfaceConfig.QUICK_PROFILE:
                return LocaleController.getString(R.string.MirrlyQuickShortProfile);
            case MirrlyInterfaceConfig.QUICK_SEARCH:
                return LocaleController.getString(R.string.MirrlyQuickShortSearch);
            case MirrlyInterfaceConfig.QUICK_NEW_CHAT:
                return LocaleController.getString(R.string.MirrlyQuickShortNewChat);
            case MirrlyInterfaceConfig.QUICK_NEW_GROUP:
                return LocaleController.getString(R.string.MirrlyQuickShortNewGroup);
            case MirrlyInterfaceConfig.QUICK_NEW_CHANNEL:
                return LocaleController.getString(R.string.MirrlyQuickShortNewChannel);
            case MirrlyInterfaceConfig.QUICK_FOLDERS:
                return LocaleController.getString(R.string.MirrlyQuickShortFolders);
            case MirrlyInterfaceConfig.QUICK_PROXY_LIST:
                return LocaleController.getString(R.string.MirrlyQuickShortProxyList);
            case MirrlyInterfaceConfig.QUICK_THEME_TOGGLE:
                return LocaleController.getString(R.string.MirrlyQuickShortThemeToggle);
            case MirrlyInterfaceConfig.QUICK_DEVICES:
                return LocaleController.getString(R.string.MirrlyQuickShortDevices);
            case MirrlyInterfaceConfig.QUICK_APPEARANCE:
                return LocaleController.getString(R.string.MirrlyQuickShortAppearance);
            case MirrlyInterfaceConfig.QUICK_WALLET:
                return LocaleController.getString(R.string.MirrlyQuickShortWallet);
            default:
                return LocaleController.getString(R.string.MirrlyQuickShortSaved);
        }
    }

    @DrawableRes
    public static int resolveDrawerItemIcon(@Nullable String id) {
        if (id == null) {
            return R.drawable.msg_settings;
        }
        switch (id) {
            case MirrlyInterfaceConfig.DRAWER_THEME_TOGGLE:
                return R.drawable.msg_theme;
            case MirrlyInterfaceConfig.DRAWER_ARCHIVE:
                return R.drawable.msg_archive;
            case MirrlyInterfaceConfig.DRAWER_PROFILE:
                return R.drawable.msg_openprofile;
            case MirrlyInterfaceConfig.DRAWER_NEW_GROUP:
                return R.drawable.msg_groups;
            case MirrlyInterfaceConfig.DRAWER_NEW_CHANNEL:
                return R.drawable.msg_channel;
            case MirrlyInterfaceConfig.DRAWER_SAVED:
                return R.drawable.msg_saved;
            case MirrlyInterfaceConfig.DRAWER_MIRRLY_NOTES:
                return R.drawable.outline_saved_24;
            case MirrlyInterfaceConfig.DRAWER_MIRRLY_DELETED:
                return R.drawable.msg_delete;
            case MirrlyInterfaceConfig.DRAWER_CONTACTS:
                return R.drawable.msg_contacts;
            case MirrlyInterfaceConfig.DRAWER_CALLS:
                return R.drawable.msg_calls;
            case MirrlyInterfaceConfig.DRAWER_WALLET_BOTS:
                return R.drawable.filled_wallet_move;
            case MirrlyInterfaceConfig.DRAWER_TG_SETTINGS:
                return R.drawable.msg_settings;
            case MirrlyInterfaceConfig.DRAWER_MIRRLY_SETTINGS:
                return R.drawable.settings_mirrly;
            case MirrlyInterfaceConfig.DRAWER_SUPPORT:
                return R.drawable.mirrly_about_support;
            case MirrlyInterfaceConfig.DRAWER_PROXY:
                return R.drawable.mirrly_about_proxy;
            default:
                return R.drawable.msg_settings;
        }
    }

    @NonNull
    public static String resolveDrawerItemTitle(@Nullable String id) {
        if (id == null) {
            return "";
        }
        switch (id) {
            case MirrlyInterfaceConfig.DRAWER_THEME_TOGGLE:
                return LocaleController.getString(R.string.MirrlyDrawerItemThemeToggle);
            case MirrlyInterfaceConfig.DRAWER_ARCHIVE:
                return LocaleController.getString(R.string.MirrlyDrawerItemArchive);
            case MirrlyInterfaceConfig.DRAWER_PROFILE:
                return LocaleController.getString(R.string.MirrlyDrawerItemProfile);
            case MirrlyInterfaceConfig.DRAWER_NEW_GROUP:
                return LocaleController.getString(R.string.MirrlyDrawerItemNewGroup);
            case MirrlyInterfaceConfig.DRAWER_NEW_CHANNEL:
                return LocaleController.getString(R.string.MirrlyDrawerItemNewChannel);
            case MirrlyInterfaceConfig.DRAWER_SAVED:
                return LocaleController.getString(R.string.MirrlyDrawerItemSaved);
            case MirrlyInterfaceConfig.DRAWER_MIRRLY_NOTES:
                return LocaleController.getString(R.string.MirrlyDrawerItemNotes);
            case MirrlyInterfaceConfig.DRAWER_MIRRLY_DELETED:
                return LocaleController.getString(R.string.MirrlyDrawerItemDeleted);
            case MirrlyInterfaceConfig.DRAWER_CONTACTS:
                return LocaleController.getString(R.string.MirrlyDrawerItemContacts);
            case MirrlyInterfaceConfig.DRAWER_CALLS:
                return LocaleController.getString(R.string.MirrlyDrawerItemCalls);
            case MirrlyInterfaceConfig.DRAWER_WALLET_BOTS:
                return LocaleController.getString(R.string.MirrlyDrawerItemWalletBots);
            case MirrlyInterfaceConfig.DRAWER_TG_SETTINGS:
                return LocaleController.getString(R.string.MirrlyDrawerItemTgSettings);
            case MirrlyInterfaceConfig.DRAWER_MIRRLY_SETTINGS:
                return LocaleController.getString(R.string.MirrlyDrawerItemMirrlySettings);
            case MirrlyInterfaceConfig.DRAWER_SUPPORT:
                return LocaleController.getString(R.string.MirrlyDrawerItemSupport);
            case MirrlyInterfaceConfig.DRAWER_PROXY:
                return LocaleController.getString(R.string.MirrlyDrawerItemProxy);
            default:
                return id;
        }
    }

    @DrawableRes
    public static int resolveMsgMenuItemIcon(@Nullable String key) {
        if (key == null) {
            return R.drawable.msg_actions;
        }
        switch (key) {
            case MirrlyInterfaceConfig.MSG_REPLY:
                return R.drawable.menu_reply;
            case MirrlyInterfaceConfig.MSG_COPY:
                return R.drawable.msg_copy;
            case MirrlyInterfaceConfig.MSG_FORWARD:
                return R.drawable.msg_forward;
            case MirrlyInterfaceConfig.MSG_PIN:
                return R.drawable.msg_pin;
            case MirrlyInterfaceConfig.MSG_EDIT:
                return R.drawable.msg_edit;
            case MirrlyInterfaceConfig.MSG_TRANSLATE:
                return R.drawable.msg_translate;
            case MirrlyInterfaceConfig.MSG_TRANSCRIBE:
                return R.drawable.msg_voice_unmuted;
            case MirrlyInterfaceConfig.MSG_SAVE_MEDIA:
                return R.drawable.msg_gallery;
            case MirrlyInterfaceConfig.MSG_SHARE:
                return R.drawable.msg_shareout;
            case MirrlyInterfaceConfig.MSG_COPY_LINK:
                return R.drawable.msg_link;
            case MirrlyInterfaceConfig.MSG_REPLIES_THREAD:
                return R.drawable.msg_viewreplies;
            case MirrlyInterfaceConfig.MSG_SELECT:
                return R.drawable.msg_select;
            case MirrlyInterfaceConfig.MSG_STATS:
                return R.drawable.msg_stats;
            case MirrlyInterfaceConfig.MSG_SAVE_TO_NOTES:
                return R.drawable.outline_saved_24;
            case MirrlyInterfaceConfig.MSG_REPORT:
                return R.drawable.msg_report;
            case MirrlyInterfaceConfig.MSG_DELETE:
                return R.drawable.msg_delete;
            default:
                return R.drawable.msg_actions;
        }
    }

    @NonNull
    public static String resolveMsgMenuItemTitle(@Nullable String key) {
        if (key == null) {
            return "";
        }
        switch (key) {
            case MirrlyInterfaceConfig.MSG_REPLY:
                return LocaleController.getString(R.string.MirrlyMsgMenuItemReply);
            case MirrlyInterfaceConfig.MSG_COPY:
                return LocaleController.getString(R.string.MirrlyMsgMenuItemCopy);
            case MirrlyInterfaceConfig.MSG_FORWARD:
                return LocaleController.getString(R.string.MirrlyMsgMenuItemForward);
            case MirrlyInterfaceConfig.MSG_PIN:
                return LocaleController.getString(R.string.MirrlyMsgMenuItemPin);
            case MirrlyInterfaceConfig.MSG_EDIT:
                return LocaleController.getString(R.string.MirrlyMsgMenuItemEdit);
            case MirrlyInterfaceConfig.MSG_TRANSLATE:
                return LocaleController.getString(R.string.MirrlyMsgMenuItemTranslate);
            case MirrlyInterfaceConfig.MSG_TRANSCRIBE:
                return LocaleController.getString(R.string.MirrlyMsgMenuItemTranscribe);
            case MirrlyInterfaceConfig.MSG_SAVE_MEDIA:
                return LocaleController.getString(R.string.MirrlyMsgMenuItemSaveMedia);
            case MirrlyInterfaceConfig.MSG_SHARE:
                return LocaleController.getString(R.string.MirrlyMsgMenuItemShare);
            case MirrlyInterfaceConfig.MSG_COPY_LINK:
                return LocaleController.getString(R.string.MirrlyMsgMenuItemCopyLink);
            case MirrlyInterfaceConfig.MSG_REPLIES_THREAD:
                return LocaleController.getString(R.string.MirrlyMsgMenuItemRepliesThread);
            case MirrlyInterfaceConfig.MSG_SELECT:
                return LocaleController.getString(R.string.MirrlyMsgMenuItemSelect);
            case MirrlyInterfaceConfig.MSG_STATS:
                return LocaleController.getString(R.string.MirrlyMsgMenuItemStats);
            case MirrlyInterfaceConfig.MSG_SAVE_TO_NOTES:
                return LocaleController.getString(R.string.MirrlyMsgMenuItemSaveToNotes);
            case MirrlyInterfaceConfig.MSG_REPORT:
                return LocaleController.getString(R.string.MirrlyMsgMenuItemReport);
            case MirrlyInterfaceConfig.MSG_DELETE:
                return LocaleController.getString(R.string.MirrlyMsgMenuItemDelete);
            default:
                return key;
        }
    }

    public static boolean isMsgMenuItemDestructive(@Nullable String key) {
        return MirrlyInterfaceConfig.MSG_DELETE.equals(key) || MirrlyInterfaceConfig.MSG_REPORT.equals(key);
    }

    @NonNull
    public static String resolveDialogTitle(int currentAccount, long dialogId) {
        if (dialogId == 0L) {
            return "";
        }
        UserConfig userConfig = UserConfig.getInstance(currentAccount);
        if (dialogId == userConfig.getClientUserId()) {
            return LocaleController.getString(R.string.SavedMessages);
        }
        MessagesController mc = MessagesController.getInstance(currentAccount);
        if (DialogObject.isUserDialog(dialogId)) {
            TLRPC.User user = mc.getUser(dialogId);
            if (user != null) {
                return UserObject.getUserName(user);
            }
        } else if (DialogObject.isEncryptedDialog(dialogId)) {
            TLRPC.EncryptedChat encryptedChat = mc.getEncryptedChat(DialogObject.getEncryptedChatId(dialogId));
            if (encryptedChat != null) {
                TLRPC.User user = mc.getUser(encryptedChat.user_id);
                if (user != null) {
                    return UserObject.getUserName(user);
                }
            }
        } else if (DialogObject.isChatDialog(dialogId)) {
            TLRPC.Chat chat = mc.getChat(-dialogId);
            if (chat != null && !TextUtils.isEmpty(chat.title)) {
                return chat.title;
            }
        }
        return "";
    }

    // -------------------------------------------------------------------------
    // Shared Canvas Drawing Helpers (Zero-Allocation Icon Cache)
    // -------------------------------------------------------------------------

    private static final class PreviewIconCache {
        private final HashMap<Integer, Drawable> drawables = new HashMap<>();
        private final HashMap<Integer, Integer> lastColors = new HashMap<>();

        @Nullable
        Drawable get(Context context, @DrawableRes int resId, int color) {
            if (resId == 0) {
                return null;
            }
            Drawable cached = drawables.get(resId);
            if (cached == null) {
                Drawable raw = ContextCompat.getDrawable(context, resId);
                if (raw == null) {
                    return null;
                }
                cached = raw.mutate();
                drawables.put(resId, cached);
                lastColors.remove(resId);
            }
            Integer prevColor = lastColors.get(resId);
            if (prevColor == null || prevColor != color) {
                cached.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
                lastColors.put(resId, color);
            }
            return cached;
        }

        void clearColors() {
            lastColors.clear();
        }
    }

    private static Drawable loadTintedDrawable(Context context, @DrawableRes int resId, int color) {
        Drawable raw = ContextCompat.getDrawable(context, resId);
        if (raw == null) {
            return null;
        }
        Drawable mutated = raw.mutate();
        mutated.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        return mutated;
    }

    private static void drawCenteredDrawable(Canvas canvas, @Nullable Drawable d, float cx, float cy, float sizePx, int alpha) {
        if (d == null || alpha <= 2) {
            return;
        }
        int half = Math.round(sizePx * 0.5f);
        d.setBounds(Math.round(cx) - half, Math.round(cy) - half, Math.round(cx) + half, Math.round(cy) + half);
        d.setAlpha(alpha);
        d.draw(canvas);
    }

    private static int resolveCardGlassFill(boolean isDark, int baseSurface) {
        float alpha = Utilities.clamp(MirrlyConfig.getEffectiveGlassAlpha(isDark ? 0.78f : 0.86f), 0.94f, 0.45f);
        return ColorUtils.setAlphaComponent(baseSurface, Math.round(alpha * 255f));
    }

    private static int resolveCardRimColor(boolean isDark) {
        float rimScale = Utilities.clamp(MirrlyConfig.getGlassRimScale(), 1.5f, 0.35f);
        return ColorUtils.setAlphaComponent(isDark ? Color.WHITE : Color.BLACK,
                Math.min(255, Math.round((isDark ? 0x26 : 0x16) * rimScale)));
    }

    // -------------------------------------------------------------------------
    // Preview 1: Home & Header Live Preview (Tab 0)
    // -------------------------------------------------------------------------

    public static final class HomeSpec {
        public final boolean hideStories;
        public final boolean hideArchive;
        public final boolean showThemeBtn;
        public final boolean showProxyBtn;
        public final boolean showDonateBtn;
        public final boolean showMoreBtn;
        public final boolean showSettingsCircle;
        public final boolean hideFabStories;
        public final boolean hideFabNewChat;

        public HomeSpec(
                boolean hideStories,
                boolean hideArchive,
                boolean showThemeBtn,
                boolean showProxyBtn,
                boolean showDonateBtn,
                boolean showMoreBtn,
                boolean showSettingsCircle,
                boolean hideFabStories,
                boolean hideFabNewChat
        ) {
            this.hideStories = hideStories;
            this.hideArchive = hideArchive;
            this.showThemeBtn = showThemeBtn;
            this.showProxyBtn = showProxyBtn;
            this.showDonateBtn = showDonateBtn;
            this.showMoreBtn = showMoreBtn;
            this.showSettingsCircle = showSettingsCircle;
            this.hideFabStories = hideFabStories;
            this.hideFabNewChat = hideFabNewChat;
        }

        public HomeSpec(
                boolean hideStories,
                boolean hideArchive,
                boolean showThemeBtn,
                boolean showSettingsCircle,
                boolean hideFabStories,
                boolean hideFabNewChat
        ) {
            this(hideStories, hideArchive, showThemeBtn, true, true, true, showSettingsCircle, hideFabStories, hideFabNewChat);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof HomeSpec)) return false;
            HomeSpec that = (HomeSpec) o;
            return hideStories == that.hideStories
                    && hideArchive == that.hideArchive
                    && showThemeBtn == that.showThemeBtn
                    && showProxyBtn == that.showProxyBtn
                    && showDonateBtn == that.showDonateBtn
                    && showMoreBtn == that.showMoreBtn
                    && showSettingsCircle == that.showSettingsCircle
                    && hideFabStories == that.hideFabStories
                    && hideFabNewChat == that.hideFabNewChat;
        }

        @Override
        public int hashCode() {
            return Objects.hash(hideStories, hideArchive, showThemeBtn, showProxyBtn, showDonateBtn, showMoreBtn, showSettingsCircle, hideFabStories, hideFabNewChat);
        }
    }

    public static class HomeHeaderPreviewCell extends FrameLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        @Nullable
        private Runnable onThemeCircleTap;

        private final Paint frameBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint frameRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint headerGlassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint headerRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint storyRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint avatarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fabPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint subPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint storyLabelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint badgeTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint avatarLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        private final RectF frameRect = new RectF();
        private final RectF pillRect = new RectF();
        private final RectF tmpRect = new RectF();
        private final RectF themeCircleHitRect = new RectF();
        private final Path clipPath = new Path();

        private final AnimatedFloat storyVisibleAnim = new AnimatedFloat(this, 0, 280, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final AnimatedFloat archiveVisibleAnim = new AnimatedFloat(this, 0, 280, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final AnimatedFloat themeBtnVisibleAnim = new AnimatedFloat(this, 0, 260, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final AnimatedFloat proxyBtnVisibleAnim = new AnimatedFloat(this, 0, 260, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final AnimatedFloat donateBtnVisibleAnim = new AnimatedFloat(this, 0, 260, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final AnimatedFloat moreBtnVisibleAnim = new AnimatedFloat(this, 0, 260, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final AnimatedFloat settingsCircleVisibleAnim = new AnimatedFloat(this, 0, 260, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final AnimatedFloat storyFabVisibleAnim = new AnimatedFloat(this, 0, 260, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final AnimatedFloat newChatFabVisibleAnim = new AnimatedFloat(this, 0, 260, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final AnimatedFloat previewInvertAnim = new AnimatedFloat(this, 0, 340, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final AnimatedFloat themeSpinAnim = new AnimatedFloat(this, 0, 420, CubicBezierInterpolator.EASE_OUT_QUINT);

        private HomeSpec spec = new HomeSpec(false, false, true, true, true, true, false, false, false);
        private boolean previewMoodInverted = false;
        private int spinCount = 0;

        private Drawable sunDrawable;
        private Drawable moonDrawable;
        private Drawable proxyDrawable;
        private Drawable donateDrawable;
        private Drawable moreDrawable;
        private Drawable settingsDrawable;
        private Drawable archiveDrawable;
        private Drawable fabStoryDrawable;
        private Drawable fabComposeDrawable;
        private int lastIconColor = Integer.MIN_VALUE;

        public HomeHeaderPreviewCell(@NonNull Context context, @Nullable Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setWillNotDraw(false);
            setPadding(dp(12), dp(8), dp(12), dp(10));

            frameRimPaint.setStyle(Paint.Style.STROKE);
            frameRimPaint.setStrokeWidth(dpf2(0.75f));
            headerRimPaint.setStyle(Paint.Style.STROKE);
            headerRimPaint.setStrokeWidth(dpf2(0.6f));
            storyRingPaint.setStyle(Paint.Style.STROKE);
            storyRingPaint.setStrokeWidth(dp(1.6f));

            titlePaint.setTypeface(AndroidUtilities.bold());
            storyLabelPaint.setTextSize(dp(9.5f));
            storyLabelPaint.setTextAlign(Paint.Align.CENTER);
            badgeTextPaint.setTextSize(dp(10));
            badgeTextPaint.setTypeface(AndroidUtilities.bold());
            badgeTextPaint.setTextAlign(Paint.Align.CENTER);
            avatarLetterPaint.setTextSize(dp(12.5f));
            avatarLetterPaint.setTypeface(AndroidUtilities.bold());
            avatarLetterPaint.setTextAlign(Paint.Align.CENTER);
            avatarLetterPaint.setColor(Color.WHITE);

            storyFabVisibleAnim.force(1f);
            newChatFabVisibleAnim.force(1f);
            previewInvertAnim.force(0f);
            themeSpinAnim.force(0f);
            updateColors();
        }

        public void setOnThemeCircleTap(@Nullable Runnable onThemeCircleTap) {
            this.onThemeCircleTap = onThemeCircleTap;
        }

        public void bind(HomeSpec nextSpec, boolean animated) {
            if (nextSpec == null) {
                return;
            }
            this.spec = nextSpec;
            boolean anim = animated && isAttachedToWindow() && SharedConfig.animationsEnabled();
            storyVisibleAnim.set(nextSpec.hideStories ? 0f : 1f, !anim);
            archiveVisibleAnim.set(nextSpec.hideArchive ? 0f : 1f, !anim);
            themeBtnVisibleAnim.set(nextSpec.showThemeBtn ? 1f : 0f, !anim);
            settingsCircleVisibleAnim.set(nextSpec.showSettingsCircle ? 1f : 0f, !anim);
            storyFabVisibleAnim.set(nextSpec.hideFabStories ? 0f : 1f, !anim);
            newChatFabVisibleAnim.set(nextSpec.hideFabNewChat ? 0f : 1f, !anim);
            invalidate();
        }

        public boolean isAnimating() {
            return storyVisibleAnim.isInProgress()
                    || archiveVisibleAnim.isInProgress()
                    || themeBtnVisibleAnim.isInProgress()
                    || settingsCircleVisibleAnim.isInProgress()
                    || storyFabVisibleAnim.isInProgress()
                    || newChatFabVisibleAnim.isInProgress()
                    || previewInvertAnim.isInProgress()
                    || themeSpinAnim.isInProgress();
        }

        private void ensureDrawables(int iconColor) {
            if (lastIconColor == iconColor && sunDrawable != null) {
                return;
            }
            lastIconColor = iconColor;
            Context ctx = getContext();
            sunDrawable = loadTintedDrawable(ctx, R.drawable.msg_brightness_high, iconColor);
            moonDrawable = loadTintedDrawable(ctx, R.drawable.msg_brightness_low, iconColor);
            proxyDrawable = loadTintedDrawable(ctx, R.drawable.mirrly_about_proxy, iconColor);
            donateDrawable = loadTintedDrawable(ctx, R.drawable.mirrly_about_support, iconColor);
            moreDrawable = loadTintedDrawable(ctx, R.drawable.ic_ab_other, iconColor);
            settingsDrawable = loadTintedDrawable(ctx, R.drawable.msg_settings, iconColor);
            archiveDrawable = loadTintedDrawable(ctx, R.drawable.chats_archive, Color.WHITE);
            fabStoryDrawable = loadTintedDrawable(ctx, R.drawable.outline_fab_story_24, iconColor);
            fabComposeDrawable = loadTintedDrawable(ctx, R.drawable.filled_fab_compose_32, Color.WHITE);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (!themeCircleHitRect.isEmpty() && themeCircleHitRect.contains(event.getX(), event.getY())) {
                    return true;
                }
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                if (!themeCircleHitRect.isEmpty() && themeCircleHitRect.contains(event.getX(), event.getY())) {
                    AndroidUtilities.vibrateCursor(this);
                    previewMoodInverted = !previewMoodInverted;
                    spinCount++;
                    previewInvertAnim.set(previewMoodInverted ? 1f : 0f, !SharedConfig.animationsEnabled());
                    themeSpinAnim.set(spinCount, !SharedConfig.animationsEnabled());
                    invalidate();
                    if (onThemeCircleTap != null) {
                        onThemeCircleTap.run();
                    }
                    return true;
                }
            }
            return super.onTouchEvent(event);
        }

        @Override
        public void updateColors() {
            lastIconColor = Integer.MIN_VALUE;
            invalidate();
        }

        @Override
        public void addColorKeys(HashSet<Integer> colors) {
            colors.add(Theme.key_windowBackgroundWhite);
            colors.add(Theme.key_windowBackgroundGray);
            colors.add(Theme.key_windowBackgroundWhiteBlackText);
            colors.add(Theme.key_windowBackgroundWhiteGrayText);
            colors.add(Theme.key_windowBackgroundWhiteBlueText4);
            colors.add(Theme.key_divider);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int w = MeasureSpec.getSize(widthMeasureSpec);
            setMeasuredDimension(w, dp(236));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }

            boolean rtl = LocaleController.isRTL;
            float storyF = storyVisibleAnim.set(spec.hideStories ? 0f : 1f);
            float archiveF = archiveVisibleAnim.set(spec.hideArchive ? 0f : 1f);
            float themeF = themeBtnVisibleAnim.set(spec.showThemeBtn ? 1f : 0f);
            float proxyF = proxyBtnVisibleAnim.set(spec.showProxyBtn ? 1f : 0f);
            float donateF = donateBtnVisibleAnim.set(spec.showDonateBtn ? 1f : 0f);
            float moreF = moreBtnVisibleAnim.set(spec.showMoreBtn ? 1f : 0f);
            float settingsF = settingsCircleVisibleAnim.set(spec.showSettingsCircle ? 1f : 0f);
            float invertF = previewInvertAnim.set(previewMoodInverted ? 1f : 0f);
            float spinF = themeSpinAnim.set(spinCount);

            int themeBg = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
            int themeText = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
            int themeSub = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);
            int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider);
            int baseIsDark = AndroidUtilities.computePerceivedBrightness(themeBg) < 0.5f ? 1 : 0;

            int altBg = baseIsDark == 1 ? 0xFFFFFFFF : 0xFF151A22;
            int altText = baseIsDark == 1 ? 0xFF12151C : 0xFFF3F6FB;
            int altSub = baseIsDark == 1 ? 0xFF707579 : 0xFF8B98AA;

            int screenBg = ColorUtils.blendARGB(themeBg, altBg, invertF);
            int fgColor = ColorUtils.blendARGB(themeText, altText, invertF);
            int mutedColor = ColorUtils.blendARGB(themeSub, altSub, invertF);
            boolean effectiveDark = AndroidUtilities.computePerceivedBrightness(screenBg) < 0.5f;

            ensureDrawables(fgColor);

            frameRect.set(getPaddingLeft(), getPaddingTop(), w - getPaddingRight(), h - getPaddingBottom());
            float corner = dp(16);
            clipPath.rewind();
            clipPath.addRoundRect(frameRect, corner, corner, Path.Direction.CW);

            frameBgPaint.setColor(ColorUtils.blendARGB(screenBg, effectiveDark ? Color.BLACK : 0xFFF4F6FA, effectiveDark ? 0.16f : 0.28f));
            canvas.drawRoundRect(frameRect, corner, corner, frameBgPaint);

            canvas.save();
            canvas.clipPath(clipPath);

            // Top miniature bar area
            float barTop = frameRect.top + dp(10);
            float barH = dp(32);
            float circleSize = dp(28);
            float circleGap = dp(5);

            int pillFill = resolveCardGlassFill(effectiveDark, screenBg);
            int pillRim = resolveCardRimColor(effectiveDark);
            headerGlassPaint.setColor(pillFill);
            headerRimPaint.setColor(pillRim);

            // Logo pill
            String appTitle = LocaleController.getString(R.string.AppName);
            titlePaint.setTextSize(dp(13.5f));
            titlePaint.setColor(fgColor);
            float titleW = Math.min(dp(106), titlePaint.measureText(appTitle) + dp(20));
            float logoLeft = rtl ? (frameRect.right - dp(10) - titleW) : (frameRect.left + dp(10));
            pillRect.set(logoLeft, barTop + (barH - circleSize) * 0.5f, logoLeft + titleW, barTop + (barH + circleSize) * 0.5f);
            canvas.drawRoundRect(pillRect, circleSize * 0.5f, circleSize * 0.5f, headerGlassPaint);
            canvas.drawRoundRect(pillRect, circleSize * 0.5f, circleSize * 0.5f, headerRimPaint);
            CharSequence clippedLogo = TextUtils.ellipsize(appTitle, titlePaint, pillRect.width() - dp(12), TextUtils.TruncateAt.END);
            float logoTextW = titlePaint.measureText(clippedLogo, 0, clippedLogo.length());
            canvas.drawText(clippedLogo, 0, clippedLogo.length(), pillRect.centerX() - logoTextW * 0.5f, pillRect.centerY() + dp(4.5f), titlePaint);

            // Action circles in order: [Theme] [Proxy] [Donate] [More ⋮] [Settings]
            float[] circleWeights = new float[]{themeF, proxyF, donateF, moreF, settingsF};
            Drawable[] circleIcons = new Drawable[]{
                    (effectiveDark ^ previewMoodInverted) ? sunDrawable : moonDrawable,
                    proxyDrawable,
                    donateDrawable,
                    moreDrawable,
                    settingsDrawable
            };

            float cy = barTop + barH * 0.5f;
            float cursor = rtl ? (frameRect.left + dp(10)) : (frameRect.right - dp(10));
            themeCircleHitRect.setEmpty();

            if (!rtl) {
                for (int i = circleWeights.length - 1; i >= 0; i--) {
                    float f = circleWeights[i];
                    if (f <= 0.01f) {
                        continue;
                    }
                    float stepW = (circleSize + circleGap) * f;
                    float cx = cursor - circleSize * 0.5f * f;
                    drawMiniHeaderCircle(canvas, cx, cy, circleSize * 0.5f * f, circleIcons[i], f, i == 0 ? spinF * 180f : 0f);
                    if (i == 0 && f > 0.5f) {
                        themeCircleHitRect.set(cx - dp(18), cy - dp(18), cx + dp(18), cy + dp(18));
                    }
                    cursor -= stepW;
                }
            } else {
                for (int i = circleWeights.length - 1; i >= 0; i--) {
                    float f = circleWeights[i];
                    if (f <= 0.01f) {
                        continue;
                    }
                    float stepW = (circleSize + circleGap) * f;
                    float cx = cursor + circleSize * 0.5f * f;
                    drawMiniHeaderCircle(canvas, cx, cy, circleSize * 0.5f * f, circleIcons[i], f, i == 0 ? spinF * 180f : 0f);
                    if (i == 0 && f > 0.5f) {
                        themeCircleHitRect.set(cx - dp(18), cy - dp(18), cx + dp(18), cy + dp(18));
                    }
                    cursor += stepW;
                }
            }

            float contentY = barTop + barH + dp(8);

            // Stories strip
            if (storyF > 0.01f) {
                float storyRowH = dp(52) * storyF;
                canvas.save();
                canvas.clipRect(frameRect.left, contentY, frameRect.right, contentY + storyRowH);
                int storyCount = 4;
                float avR = dp(14.5f) * (0.7f + 0.3f * storyF);
                float slotStep = dp(46);
                String storyName = LocaleController.getString(R.string.MirrlyInterfacePreviewStoryName);
                storyLabelPaint.setColor(ColorUtils.setAlphaComponent(mutedColor, Math.round(255 * storyF)));
                int[] storyColors = new int[]{accent, 0xFFE95C6E, 0xFF34B899, 0xFFF29C38};
                for (int i = 0; i < storyCount; i++) {
                    float sx = rtl
                            ? (frameRect.right - dp(28) - i * slotStep)
                            : (frameRect.left + dp(28) + i * slotStep);
                    float sy = contentY + dp(17);
                    storyRingPaint.setColor(ColorUtils.setAlphaComponent(storyColors[i], Math.round(255 * storyF)));
                    canvas.drawCircle(sx, sy, avR + dp(2.2f), storyRingPaint);
                    avatarPaint.setColor(ColorUtils.setAlphaComponent(storyColors[i], Math.round(0xCC * storyF)));
                    canvas.drawCircle(sx, sy, avR, avatarPaint);
                    canvas.drawText(storyName, sx, sy + avR + dp(13), storyLabelPaint);
                }
                dividerPaint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_divider, resourcesProvider), Math.round(0x88 * storyF)));
                canvas.drawLine(frameRect.left + dp(12), contentY + storyRowH - 1, frameRect.right - dp(12), contentY + storyRowH - 1, dividerPaint);
                canvas.restore();
                contentY += storyRowH;
            }

            // Archive row
            if (archiveF > 0.01f) {
                float archRowH = dp(44) * archiveF;
                canvas.save();
                canvas.clipRect(frameRect.left, contentY, frameRect.right, contentY + archRowH);
                drawMiniDialogRow(
                        canvas,
                        frameRect.left,
                        frameRect.right,
                        contentY,
                        dp(44),
                        rtl,
                        ColorUtils.blendARGB(mutedColor, accent, 0.25f),
                        archiveDrawable,
                        null,
                        LocaleController.getString(R.string.MirrlyInterfacePreviewArchive),
                        LocaleController.getString(R.string.MirrlyInterfacePreviewChat1),
                        "3",
                        accent,
                        fgColor,
                        mutedColor,
                        archiveF
                );
                canvas.restore();
                contentY += archRowH;
            }

            // Sample chat 1
            drawMiniDialogRow(
                    canvas,
                    frameRect.left,
                    frameRect.right,
                    contentY,
                    dp(46),
                    rtl,
                    accent,
                    null,
                    "D",
                    LocaleController.getString(R.string.MirrlyInterfacePreviewChat1),
                    LocaleController.getString(R.string.MirrlyInterfacePreviewChat1Msg),
                    "12:40",
                    0,
                    fgColor,
                    mutedColor,
                    1f
            );
            contentY += dp(46);

            // Sample chat 2
            drawMiniDialogRow(
                    canvas,
                    frameRect.left,
                    frameRect.right,
                    contentY,
                    dp(46),
                    rtl,
                    0xFF39B56A,
                    null,
                    "W",
                    LocaleController.getString(R.string.MirrlyInterfacePreviewChat2),
                    LocaleController.getString(R.string.MirrlyInterfacePreviewChat2Msg),
                    "11:15",
                    0,
                    fgColor,
                    mutedColor,
                    1f
            );

            // Bottom-right floating action buttons (Create Story + New Chat)
            float storyFabF = storyFabVisibleAnim.set(spec.hideFabStories ? 0f : 1f);
            float newChatFabF = newChatFabVisibleAnim.set(spec.hideFabNewChat ? 0f : 1f);
            float fabCx = rtl ? (frameRect.left + dp(24)) : (frameRect.right - dp(24));
            float mainFabCy = frameRect.bottom - dp(23);
            if (storyFabF > 0.01f) {
                float storyFabCy = AndroidUtilities.lerp(mainFabCy, mainFabCy - dp(31), newChatFabF);
                float storyR = dp(10.5f) * (0.55f + 0.45f * storyFabF);
                fabPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, Math.round((effectiveDark ? 0x52 : 0x1A) * storyFabF)));
                canvas.drawCircle(fabCx, storyFabCy + dp(1.5f), storyR + dp(0.6f), fabPaint);
                int storyFill = ColorUtils.blendARGB(screenBg, accent, effectiveDark ? 0.14f : 0.06f);
                fabPaint.setColor(ColorUtils.setAlphaComponent(storyFill, Math.round(240 * storyFabF)));
                canvas.drawCircle(fabCx, storyFabCy, storyR, fabPaint);
                int origRim = headerRimPaint.getColor();
                headerRimPaint.setAlpha(Math.round(Color.alpha(origRim) * storyFabF));
                canvas.drawCircle(fabCx, storyFabCy, storyR, headerRimPaint);
                headerRimPaint.setColor(origRim);
                drawCenteredDrawable(canvas, fabStoryDrawable, fabCx, storyFabCy, dp(12.5f) * (0.65f + 0.35f * storyFabF), Math.round(255 * storyFabF));
            }
            if (newChatFabF > 0.01f) {
                float mainR = dp(13.5f) * (0.55f + 0.45f * newChatFabF);
                fabPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, Math.round((effectiveDark ? 0x5A : 0x24) * newChatFabF)));
                canvas.drawCircle(fabCx, mainFabCy + dp(1.8f), mainR + dp(0.7f), fabPaint);
                fabPaint.setColor(ColorUtils.setAlphaComponent(accent, Math.round(255 * newChatFabF)));
                canvas.drawCircle(fabCx, mainFabCy, mainR, fabPaint);
                int origRim = headerRimPaint.getColor();
                headerRimPaint.setColor(ColorUtils.setAlphaComponent(Color.WHITE, Math.round((effectiveDark ? 0x45 : 0x65) * newChatFabF)));
                canvas.drawCircle(fabCx, mainFabCy, mainR, headerRimPaint);
                headerRimPaint.setColor(origRim);
                drawCenteredDrawable(canvas, fabComposeDrawable, fabCx, mainFabCy, dp(15f) * (0.65f + 0.35f * newChatFabF), Math.round(255 * newChatFabF));
            }

            canvas.restore();

            frameRimPaint.setColor(resolveCardRimColor(effectiveDark));
            canvas.drawRoundRect(frameRect, corner, corner, frameRimPaint);
        }

        private void drawMiniHeaderCircle(Canvas canvas, float cx, float cy, float r, @Nullable Drawable icon, float alphaFactor, float rotationDeg) {
            if (r <= 1f) {
                return;
            }
            int fillAlpha = Math.round(Color.alpha(headerGlassPaint.getColor()) * alphaFactor);
            int origFill = headerGlassPaint.getColor();
            headerGlassPaint.setAlpha(fillAlpha);
            canvas.drawCircle(cx, cy, r, headerGlassPaint);
            headerGlassPaint.setColor(origFill);

            int origRim = headerRimPaint.getColor();
            headerRimPaint.setAlpha(Math.round(Color.alpha(origRim) * alphaFactor));
            canvas.drawCircle(cx, cy, r, headerRimPaint);
            headerRimPaint.setColor(origRim);

            if (icon != null) {
                canvas.save();
                if (Math.abs(rotationDeg) > 0.1f) {
                    canvas.rotate(rotationDeg, cx, cy);
                }
                drawCenteredDrawable(canvas, icon, cx, cy, dp(15) * alphaFactor, Math.round(255 * alphaFactor));
                canvas.restore();
            }
        }

        private void drawMiniDialogRow(
                Canvas canvas,
                float left,
                float right,
                float top,
                float rowH,
                boolean rtl,
                int avatarColor,
                @Nullable Drawable avatarIcon,
                @Nullable String letter,
                String title,
                String sub,
                String trailingTag,
                int badgeBgColor,
                int titleColor,
                int subColor,
                float alphaFactor
        ) {
            int alpha = Math.round(255 * alphaFactor);
            float cy = top + rowH * 0.5f;
            float avR = dp(15);
            float avCx = rtl ? (right - dp(14) - avR) : (left + dp(14) + avR);
            avatarPaint.setColor(ColorUtils.setAlphaComponent(avatarColor, alpha));
            canvas.drawCircle(avCx, cy, avR, avatarPaint);

            if (avatarIcon != null) {
                drawCenteredDrawable(canvas, avatarIcon, avCx, cy, dp(16), alpha);
            } else if (!TextUtils.isEmpty(letter)) {
                avatarLetterPaint.setAlpha(alpha);
                canvas.drawText(letter, avCx, cy + dp(4.5f), avatarLetterPaint);
            }

            float textStart = rtl ? (left + dp(46)) : (avCx + avR + dp(10));
            float textEnd = rtl ? (avCx - avR - dp(10)) : (right - dp(46));
            float maxTextW = Math.max(dp(40), textEnd - textStart);

            titlePaint.setTextSize(dp(12.5f));
            titlePaint.setColor(ColorUtils.setAlphaComponent(titleColor, alpha));
            CharSequence clippedTitle = TextUtils.ellipsize(title, titlePaint, maxTextW, TextUtils.TruncateAt.END);
            float tw = titlePaint.measureText(clippedTitle, 0, clippedTitle.length());
            float titleX = rtl ? (textEnd - tw) : textStart;
            canvas.drawText(clippedTitle, 0, clippedTitle.length(), titleX, cy - dp(2.5f), titlePaint);

            subPaint.setTextSize(dp(11f));
            subPaint.setColor(ColorUtils.setAlphaComponent(subColor, alpha));
            CharSequence clippedSub = TextUtils.ellipsize(sub, subPaint, maxTextW, TextUtils.TruncateAt.END);
            float sw = subPaint.measureText(clippedSub, 0, clippedSub.length());
            float subX = rtl ? (textEnd - sw) : textStart;
            canvas.drawText(clippedSub, 0, clippedSub.length(), subX, cy + dp(12f), subPaint);

            if (!TextUtils.isEmpty(trailingTag)) {
                if (badgeBgColor != 0) {
                    float bw = Math.max(dp(18), badgeTextPaint.measureText(trailingTag) + dp(10));
                    float bh = dp(16);
                    float bx = rtl ? (left + dp(12)) : (right - dp(12) - bw);
                    tmpRect.set(bx, cy - bh * 0.5f, bx + bw, cy + bh * 0.5f);
                    badgePaint.setColor(ColorUtils.setAlphaComponent(badgeBgColor, alpha));
                    canvas.drawRoundRect(tmpRect, bh * 0.5f, bh * 0.5f, badgePaint);
                    badgeTextPaint.setColor(ColorUtils.setAlphaComponent(Color.WHITE, alpha));
                    canvas.drawText(trailingTag, tmpRect.centerX(), tmpRect.centerY() + dp(3.5f), badgeTextPaint);
                } else {
                    subPaint.setTextSize(dp(10f));
                    float timeW = subPaint.measureText(trailingTag);
                    float tx = rtl ? (left + dp(12)) : (right - dp(12) - timeW);
                    canvas.drawText(trailingTag, tx, cy - dp(2.5f), subPaint);
                }
            }
        }

        public static class Factory extends UItem.UItemFactory<HomeHeaderPreviewCell> {
            static {
                setup(new Factory());
            }

            @Override
            public HomeHeaderPreviewCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new HomeHeaderPreviewCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                HomeHeaderPreviewCell cell = (HomeHeaderPreviewCell) view;
                if (item.object instanceof HomeSpec) {
                    cell.bind((HomeSpec) item.object, view.isAttachedToWindow());
                }
                if (item.object2 instanceof Runnable) {
                    cell.setOnThemeCircleTap((Runnable) item.object2);
                } else {
                    cell.setOnThemeCircleTap(null);
                }
            }

            @Override
            public boolean isClickable() {
                return false;
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a != null && b != null && a.id == b.id && a.viewType == b.viewType;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return a != null && b != null
                        && a.id == b.id
                        && a.viewType == b.viewType
                        && Objects.equals(a.object, b.object);
            }

            public static UItem of(int id, HomeSpec spec, @Nullable Runnable onThemeCircleTap) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = spec;
                item.object2 = onThemeCircleTap;
                return item;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Preview 2: Bottom Navigation Bar Interactive Preview (Tab 1)
    // -------------------------------------------------------------------------

    public static final class BottomBarSpec {
        public final int currentAccount;
        public final int collapseMode;
        public final int alignment;
        public final int[] slotModes;
        public final String[] slotActions;
        public final long[] slotChatIds;
        public final String[] slotChatTitles;
        public final boolean showCallsTab;
        public final boolean showTopSettingsFallback;
        public final boolean showMoreProfileFallback;

        public BottomBarSpec(
                int currentAccount,
                int collapseMode,
                int alignment,
                int[] slotModes,
                String[] slotActions,
                long[] slotChatIds,
                String[] slotChatTitles,
                boolean showCallsTab,
                boolean showTopSettingsFallback,
                boolean showMoreProfileFallback
        ) {
            this.currentAccount = currentAccount;
            this.collapseMode = collapseMode;
            this.alignment = alignment;
            this.slotModes = slotModes;
            this.slotActions = slotActions;
            this.slotChatIds = slotChatIds;
            this.slotChatTitles = slotChatTitles;
            this.showCallsTab = showCallsTab;
            this.showTopSettingsFallback = showTopSettingsFallback;
            this.showMoreProfileFallback = showMoreProfileFallback;
        }

        public static BottomBarSpec capture(int currentAccount) {
            int[] modes = new int[MirrlyInterfaceConfig.SLOT_COUNT];
            String[] actions = new String[MirrlyInterfaceConfig.SLOT_COUNT];
            long[] chatIds = new long[MirrlyInterfaceConfig.SLOT_COUNT];
            String[] chatTitles = new String[MirrlyInterfaceConfig.SLOT_COUNT];
            for (int i = 0; i < MirrlyInterfaceConfig.SLOT_COUNT; i++) {
                modes[i] = MirrlyInterfaceConfig.getSlotMode(i);
                actions[i] = MirrlyInterfaceConfig.getSlotCustomAction(i);
                chatIds[i] = MirrlyInterfaceConfig.getSlotCustomChatId(i);
                chatTitles[i] = MirrlyInterfaceConfig.getSlotCustomChatTitle(i);
            }
            boolean showCalls = UserConfig.getInstance(currentAccount).showCallsTab;
            return new BottomBarSpec(
                    currentAccount,
                    MirrlyInterfaceConfig.getBottomBarCollapseMode(),
                    MirrlyInterfaceConfig.getBottomBarAlignment(),
                    modes,
                    actions,
                    chatIds,
                    chatTitles,
                    showCalls,
                    MirrlyInterfaceConfig.shouldShowTopSettingsCircle(showCalls),
                    MirrlyInterfaceConfig.shouldShowProfileInMoreMenuFallback()
            );
        }

        public int getVisibleCount() {
            int count = 0;
            for (int m : slotModes) {
                if (m != MirrlyInterfaceConfig.SLOT_MODE_HIDDEN) {
                    count++;
                }
            }
            return count;
        }

        public boolean isCollapsedDefault() {
            int visible = getVisibleCount();
            if (visible <= 0) {
                return false;
            }
            return collapseMode == MirrlyInterfaceConfig.COLLAPSE_ALWAYS
                    || (collapseMode == MirrlyInterfaceConfig.COLLAPSE_AUTO && visible <= 2);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BottomBarSpec)) return false;
            BottomBarSpec that = (BottomBarSpec) o;
            return currentAccount == that.currentAccount
                    && collapseMode == that.collapseMode
                    && alignment == that.alignment
                    && showCallsTab == that.showCallsTab
                    && showTopSettingsFallback == that.showTopSettingsFallback
                    && showMoreProfileFallback == that.showMoreProfileFallback
                    && Arrays.equals(slotModes, that.slotModes)
                    && Arrays.equals(slotActions, that.slotActions)
                    && Arrays.equals(slotChatIds, that.slotChatIds)
                    && Arrays.equals(slotChatTitles, that.slotChatTitles);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    currentAccount,
                    collapseMode,
                    alignment,
                    Arrays.hashCode(slotModes),
                    Arrays.hashCode(slotActions),
                    Arrays.hashCode(slotChatIds),
                    Arrays.hashCode(slotChatTitles),
                    showCallsTab,
                    showTopSettingsFallback,
                    showMoreProfileFallback
            );
        }
    }

    public static class BottomBarPreviewCell extends FrameLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        private final Paint frameBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint frameRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ghostRowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pillBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pillRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint activePillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint chipBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint chipTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint hintPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint badgeTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        private final RectF frameRect = new RectF();
        private final RectF pillRect = new RectF();
        private final RectF chipRect = new RectF();
        private final RectF activeTabRect = new RectF();
        private final RectF interactivePillHitRect = new RectF();
        private final RectF tmpRect = new RectF();
        private final Path clipPath = new Path();

        private final AnimatedFloat barVisibleAnim = new AnimatedFloat(this, 0, 280, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final AnimatedFloat collapseAnim = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final AnimatedFloat pillWidthAnim = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final AnimatedFloat pillAlignAnim = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final PreviewIconCache iconCache = new PreviewIconCache();

        private BottomBarSpec spec;
        private boolean previewExpanded = false;
        private int lastCollapseMode = -1;
        private int lastVisibleCount = -1;

        public BottomBarPreviewCell(@NonNull Context context, @Nullable Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            this.spec = BottomBarSpec.capture(UserConfig.selectedAccount);
            setWillNotDraw(false);
            setPadding(dp(12), dp(8), dp(12), dp(10));

            frameRimPaint.setStyle(Paint.Style.STROKE);
            frameRimPaint.setStrokeWidth(dpf2(0.75f));
            pillRimPaint.setStyle(Paint.Style.STROKE);
            pillRimPaint.setStrokeWidth(dpf2(0.8f));

            labelPaint.setTextSize(dp(9.5f));
            labelPaint.setTypeface(AndroidUtilities.bold());
            labelPaint.setTextAlign(Paint.Align.CENTER);

            chipTextPaint.setTextSize(dp(10.5f));
            chipTextPaint.setTypeface(AndroidUtilities.bold());

            hintPaint.setTextSize(dp(11f));
            hintPaint.setTextAlign(Paint.Align.CENTER);

            badgeTextPaint.setTextSize(dp(9.5f));
            badgeTextPaint.setTypeface(AndroidUtilities.bold());
            badgeTextPaint.setTextAlign(Paint.Align.CENTER);
            badgeTextPaint.setColor(Color.WHITE);

            pillAlignAnim.force(alignmentToFraction(this.spec.alignment));
        }

        private static float alignmentToFraction(int alignment) {
            if (alignment == MirrlyInterfaceConfig.ALIGN_LEFT) {
                return 0f;
            }
            if (alignment == MirrlyInterfaceConfig.ALIGN_RIGHT) {
                return 1f;
            }
            return 0.5f;
        }

        public void bind(BottomBarSpec nextSpec, boolean animated) {
            if (nextSpec == null) {
                return;
            }
            int nextVis = nextSpec.getVisibleCount();
            if (lastCollapseMode != nextSpec.collapseMode || lastVisibleCount != nextVis || !nextSpec.isCollapsedDefault()) {
                previewExpanded = false;
            }
            lastCollapseMode = nextSpec.collapseMode;
            lastVisibleCount = nextVis;
            this.spec = nextSpec;

            boolean anim = animated && isAttachedToWindow() && SharedConfig.animationsEnabled();
            barVisibleAnim.set(nextVis > 0 ? 1f : 0f, !anim);
            boolean collapsed = nextSpec.isCollapsedDefault() && !previewExpanded;
            collapseAnim.set(collapsed ? 1f : 0f, !anim);
            pillWidthAnim.set(computeTargetPillWidthPx(nextVis, collapsed), !anim);
            pillAlignAnim.set(alignmentToFraction(nextSpec.alignment), !anim);
            invalidate();
        }

        public boolean isAnimating() {
            return barVisibleAnim.isInProgress()
                    || collapseAnim.isInProgress()
                    || pillWidthAnim.isInProgress()
                    || pillAlignAnim.isInProgress();
        }

        private float computeTargetPillWidthPx(int visibleCount, boolean collapsed) {
            if (visibleCount <= 0) {
                return dp(52);
            }
            if (collapsed) {
                return dp(52);
            }
            switch (visibleCount) {
                case 1:
                    return dp(82);
                case 2:
                    return dp(156);
                case 3:
                    return dp(228);
                case 4:
                default:
                    return dp(292);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (spec == null || !spec.isCollapsedDefault() || spec.getVisibleCount() <= 0) {
                return super.onTouchEvent(event);
            }
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                return frameRect.contains(event.getX(), event.getY());
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                if (interactivePillHitRect.contains(event.getX(), event.getY()) || previewExpanded) {
                    AndroidUtilities.vibrateCursor(this);
                    previewExpanded = !previewExpanded;
                    boolean anim = SharedConfig.animationsEnabled();
                    boolean collapsed = spec.isCollapsedDefault() && !previewExpanded;
                    collapseAnim.set(collapsed ? 1f : 0f, !anim);
                    pillWidthAnim.set(computeTargetPillWidthPx(spec.getVisibleCount(), collapsed), !anim);
                    invalidate();
                    return true;
                }
            }
            return super.onTouchEvent(event);
        }

        @Override
        public void updateColors() {
            iconCache.clearColors();
            invalidate();
        }

        @Override
        public void addColorKeys(HashSet<Integer> colors) {
            colors.add(Theme.key_windowBackgroundWhite);
            colors.add(Theme.key_windowBackgroundGray);
            colors.add(Theme.key_windowBackgroundWhiteBlackText);
            colors.add(Theme.key_windowBackgroundWhiteGrayText);
            colors.add(Theme.key_windowBackgroundWhiteBlueText4);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int w = MeasureSpec.getSize(widthMeasureSpec);
            setMeasuredDimension(w, dp(164));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0 || spec == null) {
                return;
            }

            boolean rtl = LocaleController.isRTL;
            int cardBg = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
            int grayBg = Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider);
            int textPrimary = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
            int textMuted = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);
            int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider);
            boolean isDark = AndroidUtilities.computePerceivedBrightness(cardBg) < 0.5f;

            frameRect.set(getPaddingLeft(), getPaddingTop(), w - getPaddingRight(), h - getPaddingBottom());
            float corner = dp(16);
            clipPath.rewind();
            clipPath.addRoundRect(frameRect, corner, corner, Path.Direction.CW);

            frameBgPaint.setColor(ColorUtils.blendARGB(cardBg, grayBg, isDark ? 0.45f : 0.65f));
            canvas.drawRoundRect(frameRect, corner, corner, frameBgPaint);

            canvas.save();
            canvas.clipPath(clipPath);

            // Subtle simulated chat rows extending to bottom so glass translucency & freed space show clearly
            for (int r = 0; r < 3; r++) {
                float rowY = frameRect.top + dp(34 + r * 36);
                ghostRowPaint.setColor(ColorUtils.setAlphaComponent(textMuted, isDark ? 0x20 : 0x18));
                float avCx = rtl ? (frameRect.right - dp(26)) : (frameRect.left + dp(26));
                canvas.drawCircle(avCx, rowY + dp(12), dp(11), ghostRowPaint);
                float lineL = rtl ? (frameRect.left + dp(40)) : (avCx + dp(18));
                float lineR = rtl ? (avCx - dp(18)) : (frameRect.right - dp(38));
                tmpRect.set(lineL, rowY + dp(4), lineR, rowY + dp(11));
                canvas.drawRoundRect(tmpRect, dp(3.5f), dp(3.5f), ghostRowPaint);
                ghostRowPaint.setColor(ColorUtils.setAlphaComponent(textMuted, isDark ? 0x14 : 0x10));
                float subL = rtl ? (lineL + dp(36)) : lineL;
                float subR = rtl ? lineR : (lineR - dp(36));
                tmpRect.set(subL, rowY + dp(16), subR, rowY + dp(21));
                canvas.drawRoundRect(tmpRect, dp(2.5f), dp(2.5f), ghostRowPaint);
            }

            // Top-bar fallback status badges inside miniature frame
            float chipY = frameRect.top + dp(10);
            float chipCursor = rtl ? (frameRect.left + dp(10)) : (frameRect.right - dp(10));
            if (spec.showTopSettingsFallback) {
                String label = "⋮ + " + LocaleController.getString(R.string.MirrlyInterfaceTopSettingsAcc);
                chipCursor = drawMiniStatusChip(canvas, chipCursor, chipY, rtl, label, cardBg, accent, textPrimary, isDark);
            }
            if (spec.showMoreProfileFallback) {
                String label = "⋮ " + LocaleController.getString(R.string.MirrlyInterfaceSlotProfile);
                drawMiniStatusChip(canvas, chipCursor, chipY, rtl, label, cardBg, accent, textPrimary, isDark);
            }

            int visibleCount = spec.getVisibleCount();
            float barVisF = barVisibleAnim.set(visibleCount > 0 ? 1f : 0f);
            float collF = collapseAnim.set((spec.isCollapsedDefault() && !previewExpanded) ? 1f : 0f);
            float targetW = computeTargetPillWidthPx(visibleCount, spec.isCollapsedDefault() && !previewExpanded);
            float maxPillW = Math.max(dp(56), frameRect.width() - dp(24));
            float pillW = Math.min(maxPillW, pillWidthAnim.set(targetW));
            float pillH = dp(52);
            float pillBottom = frameRect.bottom - dp(22);
            float pillTop = pillBottom - pillH;
            float alignF = pillAlignAnim.set(alignmentToFraction(spec.alignment));
            float minPillCx = frameRect.left + dp(12) + pillW * 0.5f;
            float maxPillCx = frameRect.right - dp(12) - pillW * 0.5f;
            float pillCx = maxPillCx <= minPillCx ? frameRect.centerX() : AndroidUtilities.lerp(minPillCx, maxPillCx, alignF);

            // Hidden notice when 0 buttons remain
            if (barVisF < 0.98f) {
                float hiddenAlpha = 1f - barVisF;
                String hiddenMsg = LocaleController.getString(R.string.MirrlyInterfaceBarPreviewHiddenBadge);
                float msgW = hintPaint.measureText(hiddenMsg) + dp(24);
                float msgH = dp(28);
                float noticeCx = frameRect.centerX();
                tmpRect.set(noticeCx - msgW * 0.5f, pillBottom - msgH - dp(8), noticeCx + msgW * 0.5f, pillBottom - dp(8));
                chipBgPaint.setColor(ColorUtils.setAlphaComponent(cardBg, Math.round(0xCC * hiddenAlpha)));
                canvas.drawRoundRect(tmpRect, msgH * 0.5f, msgH * 0.5f, chipBgPaint);
                hintPaint.setColor(ColorUtils.setAlphaComponent(textMuted, Math.round(255 * hiddenAlpha)));
                canvas.drawText(hiddenMsg, tmpRect.centerX(), tmpRect.centerY() + dp(4f), hintPaint);
            }

            // Glass bottom bar pill or collapsed circle
            if (barVisF > 0.01f) {
                pillRect.set(pillCx - pillW * 0.5f, pillTop, pillCx + pillW * 0.5f, pillBottom);
                interactivePillHitRect.set(pillRect.left - dp(12), pillRect.top - dp(8), pillRect.right + dp(12), pillRect.bottom + dp(16));
                float r = pillH * 0.5f;

                canvas.save();
                canvas.scale(0.84f + 0.16f * barVisF, 0.84f + 0.16f * barVisF, pillRect.centerX(), pillRect.centerY());

                pillBgPaint.setColor(ColorUtils.setAlphaComponent(cardBg, Math.round(Color.alpha(resolveCardGlassFill(isDark, cardBg)) * barVisF)));
                pillRimPaint.setColor(ColorUtils.setAlphaComponent(resolveCardRimColor(isDark), Math.round(255 * barVisF)));
                canvas.drawRoundRect(pillRect, r, r, pillBgPaint);
                canvas.drawRoundRect(pillRect, r, r, pillRimPaint);

                // Collect visible slots
                ArrayList<Integer> visibleSlots = new ArrayList<>(4);
                for (int i = 0; i < MirrlyInterfaceConfig.SLOT_COUNT; i++) {
                    if (spec.slotModes[i] != MirrlyInterfaceConfig.SLOT_MODE_HIDDEN) {
                        visibleSlots.add(i);
                    }
                }

                if (collF >= 0.5f && !visibleSlots.isEmpty()) {
                    // Single centered collapsed circle representation
                    float alphaMul = Math.min(1f, (collF - 0.5f) * 2f) * barVisF;
                    int primarySlot = visibleSlots.contains(MirrlyInterfaceConfig.SLOT_CHATS) ? MirrlyInterfaceConfig.SLOT_CHATS : visibleSlots.get(0);
                    Drawable icon = iconCache.get(getContext(), resolveSlotPreviewIcon(primarySlot), accent);
                    drawCenteredDrawable(canvas, icon, pillRect.centerX(), pillRect.centerY(), dp(22), Math.round(255 * alphaMul));

                    // Unread dot badge
                    float bx = pillRect.centerX() + dp(11);
                    float by = pillRect.centerY() - dp(10);
                    badgePaint.setColor(ColorUtils.setAlphaComponent(accent, Math.round(255 * alphaMul)));
                    canvas.drawCircle(bx, by, dp(7.5f), badgePaint);
                    badgeTextPaint.setAlpha(Math.round(255 * alphaMul));
                    canvas.drawText("3", bx, by + dp(3.3f), badgeTextPaint);
                } else if (!visibleSlots.isEmpty()) {
                    float alphaMul = Math.min(1f, (1f - collF) * 2f) * barVisF;
                    int n = visibleSlots.size();
                    float innerPad = dp(4);
                    float slotW = (pillRect.width() - innerPad * 2f) / n;
                    int activeIdx = 0;
                    for (int k = 0; k < n; k++) {
                        if (spec.slotModes[visibleSlots.get(k)] == MirrlyInterfaceConfig.SLOT_MODE_DEFAULT) {
                            activeIdx = k;
                            break;
                        }
                    }

                    for (int k = 0; k < n; k++) {
                        int slot = visibleSlots.get(k);
                        int visualPos = rtl ? (n - 1 - k) : k;
                        float sLeft = pillRect.left + innerPad + visualPos * slotW;
                        float sRight = sLeft + slotW;
                        boolean isSelected = k == activeIdx;
                        if (isSelected) {
                            activeTabRect.set(sLeft + dp(2), pillRect.top + dp(4), sRight - dp(2), pillRect.bottom - dp(4));
                            activePillPaint.setColor(ColorUtils.setAlphaComponent(accent, Math.round((isDark ? 0x33 : 0x22) * alphaMul)));
                            canvas.drawRoundRect(activeTabRect, dp(20), dp(20), activePillPaint);
                        }
                        int fg = isSelected ? accent : textPrimary;
                        Drawable icon = iconCache.get(getContext(), resolveSlotPreviewIcon(slot), fg);
                        drawCenteredDrawable(canvas, icon, (sLeft + sRight) * 0.5f, pillRect.top + dp(18), dp(19), Math.round(255 * alphaMul));

                        String shortLabel = resolveSlotShortLabel(slot);
                        labelPaint.setColor(ColorUtils.setAlphaComponent(fg, Math.round(255 * alphaMul)));
                        CharSequence clipped = TextUtils.ellipsize(shortLabel, labelPaint, Math.max(dp(18), slotW - dp(6)), TextUtils.TruncateAt.END);
                        canvas.drawText(clipped, 0, clipped.length(), (sLeft + sRight) * 0.5f, pillRect.bottom - dp(9), labelPaint);
                    }
                }
                canvas.restore();

                // Interactive tap hint below collapsed circle / expanded interactive mode
                if (spec.isCollapsedDefault()) {
                    String tapHint = LocaleController.getString(R.string.MirrlyInterfaceBarPreviewTapHint);
                    hintPaint.setTextSize(dp(10.5f));
                    hintPaint.setColor(textMuted);
                    CharSequence clippedHint = TextUtils.ellipsize(tapHint, hintPaint, frameRect.width() - dp(24), TextUtils.TruncateAt.END);
                    canvas.drawText(clippedHint, 0, clippedHint.length(), frameRect.centerX(), frameRect.bottom - dp(6), hintPaint);
                }
            } else {
                interactivePillHitRect.setEmpty();
            }

            canvas.restore();

            frameRimPaint.setColor(resolveCardRimColor(isDark));
            canvas.drawRoundRect(frameRect, corner, corner, frameRimPaint);
        }

        private float drawMiniStatusChip(Canvas canvas, float cursor, float top, boolean rtl, String text, int cardBg, int accent, int fgColor, boolean isDark) {
            float tw = chipTextPaint.measureText(text) + dp(14);
            float h = dp(20);
            float left = rtl ? cursor : (cursor - tw);
            float right = rtl ? (cursor + tw) : cursor;
            chipRect.set(left, top, right, top + h);
            chipBgPaint.setColor(ColorUtils.blendARGB(cardBg, accent, isDark ? 0.25f : 0.14f));
            canvas.drawRoundRect(chipRect, h * 0.5f, h * 0.5f, chipBgPaint);
            chipTextPaint.setColor(accent);
            canvas.drawText(text, chipRect.left + dp(7), chipRect.centerY() + dp(3.5f), chipTextPaint);
            return rtl ? (right + dp(6)) : (left - dp(6));
        }

        @DrawableRes
        private int resolveSlotPreviewIcon(int slot) {
            if (spec.slotModes[slot] == MirrlyInterfaceConfig.SLOT_MODE_CUSTOM) {
                return resolveQuickActionIcon(spec.slotActions[slot]);
            }
            switch (slot) {
                case MirrlyInterfaceConfig.SLOT_CHATS:
                    return R.drawable.filled_chatlist2;
                case MirrlyInterfaceConfig.SLOT_CONTACTS:
                    return R.drawable.msg_contacts;
                case MirrlyInterfaceConfig.SLOT_SETTINGS_OR_CALLS:
                    return spec.showCallsTab ? R.drawable.msg_calls : R.drawable.msg_settings;
                case MirrlyInterfaceConfig.SLOT_PROFILE:
                default:
                    return R.drawable.msg_openprofile;
            }
        }

        private String resolveSlotShortLabel(int slot) {
            if (spec.slotModes[slot] == MirrlyInterfaceConfig.SLOT_MODE_CUSTOM) {
                return resolveQuickActionShort(spec.currentAccount, spec.slotActions[slot], spec.slotChatIds[slot], spec.slotChatTitles[slot]);
            }
            switch (slot) {
                case MirrlyInterfaceConfig.SLOT_CHATS:
                    return LocaleController.getString(R.string.MirrlyInterfaceSlotChats);
                case MirrlyInterfaceConfig.SLOT_CONTACTS:
                    return LocaleController.getString(R.string.MirrlyInterfaceSlotContacts);
                case MirrlyInterfaceConfig.SLOT_SETTINGS_OR_CALLS:
                    return LocaleController.getString(spec.showCallsTab ? R.string.MirrlyQuickShortCalls : R.string.MirrlyQuickShortSettings);
                case MirrlyInterfaceConfig.SLOT_PROFILE:
                default:
                    return LocaleController.getString(R.string.MirrlyQuickShortProfile);
            }
        }

        public static class Factory extends UItem.UItemFactory<BottomBarPreviewCell> {
            static {
                setup(new Factory());
            }

            @Override
            public BottomBarPreviewCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new BottomBarPreviewCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                if (item.object instanceof BottomBarSpec) {
                    ((BottomBarPreviewCell) view).bind((BottomBarSpec) item.object, view.isAttachedToWindow());
                }
            }

            @Override
            public boolean isClickable() {
                return false;
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a != null && b != null && a.id == b.id && a.viewType == b.viewType;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return a != null && b != null
                        && a.id == b.id
                        && a.viewType == b.viewType
                        && Objects.equals(a.object, b.object);
            }

            public static UItem of(int id, BottomBarSpec spec) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = spec;
                return item;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Preview 3: Three-Dots Menu Live Glass Popup Preview (Tab 2)
    // -------------------------------------------------------------------------

    public static final class DrawerSpec {
        public final ArrayList<String> visibleItems;
        public final boolean archiveAuto;
        public final boolean profileAuto;

        public DrawerSpec(ArrayList<String> visibleItems, boolean archiveAuto, boolean profileAuto) {
            this.visibleItems = visibleItems;
            this.archiveAuto = archiveAuto;
            this.profileAuto = profileAuto;
        }

        public static DrawerSpec capture() {
            ArrayList<String> order = MirrlyInterfaceConfig.getDrawerMenuOrder();
            ArrayList<String> visible = new ArrayList<>();
            for (String id : order) {
                if (MirrlyInterfaceConfig.isDrawerItemVisible(id)) {
                    visible.add(id);
                }
            }
            return new DrawerSpec(
                    visible,
                    MirrlyInterfaceConfig.isDrawerItemAutoEnabled(MirrlyInterfaceConfig.DRAWER_ARCHIVE),
                    MirrlyInterfaceConfig.isDrawerItemAutoEnabled(MirrlyInterfaceConfig.DRAWER_PROFILE)
            );
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DrawerSpec)) return false;
            DrawerSpec that = (DrawerSpec) o;
            return archiveAuto == that.archiveAuto
                    && profileAuto == that.profileAuto
                    && Objects.equals(visibleItems, that.visibleItems);
        }

        @Override
        public int hashCode() {
            return Objects.hash(visibleItems, archiveAuto, profileAuto);
        }
    }

    public static class DrawerMenuPreviewCell extends FrameLayout implements Theme.Colorable {

        private static final int MAX_VISIBLE_ROWS = 5;

        private final Theme.ResourcesProvider resourcesProvider;
        private final Paint frameBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint frameRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint popupBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint popupShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint popupRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint chipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final TextPaint rowTitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint chipTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint overflowPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        private final RectF frameRect = new RectF();
        private final RectF popupRect = new RectF();
        private final RectF tmpRect = new RectF();
        private final PreviewIconCache iconCache = new PreviewIconCache();

        private DrawerSpec spec = DrawerSpec.capture();

        public DrawerMenuPreviewCell(@NonNull Context context, @Nullable Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setWillNotDraw(false);
            setPadding(dp(12), dp(8), dp(12), dp(10));

            frameRimPaint.setStyle(Paint.Style.STROKE);
            frameRimPaint.setStrokeWidth(dpf2(0.75f));
            popupRimPaint.setStyle(Paint.Style.STROKE);
            popupRimPaint.setStrokeWidth(dpf2(0.8f));

            rowTitlePaint.setTextSize(dp(12.5f));
            chipTextPaint.setTextSize(dp(9.5f));
            chipTextPaint.setTypeface(AndroidUtilities.bold());
            chipTextPaint.setTextAlign(Paint.Align.CENTER);
            overflowPaint.setTextSize(dp(11f));
            overflowPaint.setTypeface(AndroidUtilities.bold());
            overflowPaint.setTextAlign(Paint.Align.CENTER);
        }

        public void bind(DrawerSpec nextSpec) {
            if (nextSpec != null) {
                this.spec = nextSpec;
                invalidate();
            }
        }

        @Override
        public void updateColors() {
            iconCache.clearColors();
            invalidate();
        }

        @Override
        public void addColorKeys(HashSet<Integer> colors) {
            colors.add(Theme.key_windowBackgroundWhite);
            colors.add(Theme.key_windowBackgroundGray);
            colors.add(Theme.key_actionBarDefaultSubmenuBackground);
            colors.add(Theme.key_actionBarDefaultSubmenuItem);
            colors.add(Theme.key_actionBarDefaultSubmenuItemIcon);
            colors.add(Theme.key_windowBackgroundWhiteBlueText4);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int w = MeasureSpec.getSize(widthMeasureSpec);
            setMeasuredDimension(w, dp(224));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0 || spec == null) {
                return;
            }

            boolean rtl = LocaleController.isRTL;
            int cardBg = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
            int grayBg = Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider);
            int menuBg = Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider);
            int menuFg = Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider);
            int menuIcon = Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon, resourcesProvider);
            int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider);
            int muted = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);
            boolean isDark = AndroidUtilities.computePerceivedBrightness(cardBg) < 0.5f;

            frameRect.set(getPaddingLeft(), getPaddingTop(), w - getPaddingRight(), h - getPaddingBottom());
            float corner = dp(16);
            frameBgPaint.setColor(ColorUtils.blendARGB(cardBg, grayBg, isDark ? 0.5f : 0.72f));
            canvas.drawRoundRect(frameRect, corner, corner, frameBgPaint);

            // Top anchor ⋮ circle
            float anchorR = dp(14);
            float anchorCx = rtl ? (frameRect.left + dp(24)) : (frameRect.right - dp(24));
            float anchorCy = frameRect.top + dp(22);
            popupBgPaint.setColor(resolveCardGlassFill(isDark, menuBg));
            popupRimPaint.setColor(resolveCardRimColor(isDark));
            canvas.drawCircle(anchorCx, anchorCy, anchorR, popupBgPaint);
            canvas.drawCircle(anchorCx, anchorCy, anchorR, popupRimPaint);
            Drawable dotsIcon = iconCache.get(getContext(), R.drawable.ic_ab_other, menuFg);
            drawCenteredDrawable(canvas, dotsIcon, anchorCx, anchorCy, dp(16), 255);

            // Glass popup menu card
            int total = spec.visibleItems.size();
            int shown = Math.min(MAX_VISIBLE_ROWS, total);
            boolean hasOverflow = total > MAX_VISIBLE_ROWS;
            float rowH = dp(28);
            float popupH = dp(12) + Math.max(1, shown) * rowH + (hasOverflow ? dp(20) : 0);
            float popupW = Math.min(dp(224), frameRect.width() - dp(36));
            float popupLeft = rtl ? (frameRect.left + dp(14)) : (frameRect.right - dp(14) - popupW);
            float popupTop = anchorCy + anchorR + dp(6);
            popupRect.set(popupLeft, popupTop, popupLeft + popupW, Math.min(frameRect.bottom - dp(10), popupTop + popupH));

            popupShadowPaint.setColor(isDark ? 0x44000000 : 0x1A000000);
            tmpRect.set(popupRect.left - dp(1), popupRect.top + dp(2), popupRect.right + dp(1), popupRect.bottom + dp(4));
            canvas.drawRoundRect(tmpRect, dp(15), dp(15), popupShadowPaint);

            canvas.drawRoundRect(popupRect, dp(14), dp(14), popupBgPaint);
            canvas.drawRoundRect(popupRect, dp(14), dp(14), popupRimPaint);

            float y = popupRect.top + dp(6);
            rowTitlePaint.setColor(menuFg);
            for (int i = 0; i < shown; i++) {
                String id = spec.visibleItems.get(i);
                float cy = y + rowH * 0.5f;
                float iconCx = rtl ? (popupRect.right - dp(20)) : (popupRect.left + dp(20));
                Drawable rowIcon = iconCache.get(getContext(), resolveDrawerItemIcon(id), menuIcon);
                drawCenteredDrawable(canvas, rowIcon, iconCx, cy, dp(16), 255);

                boolean isArchive = MirrlyInterfaceConfig.DRAWER_ARCHIVE.equals(id);
                boolean isProfile = MirrlyInterfaceConfig.DRAWER_PROFILE.equals(id);
                boolean showAutoChip = (isArchive && spec.archiveAuto) || (isProfile && spec.profileAuto);

                float trailingReserve = dp(12);
                if (isArchive) {
                    String countBadge = "3";
                    float bw = dp(18);
                    float bh = dp(15);
                    float bx = rtl ? (popupRect.left + trailingReserve) : (popupRect.right - trailingReserve - bw);
                    tmpRect.set(bx, cy - bh * 0.5f, bx + bw, cy + bh * 0.5f);
                    chipPaint.setColor(accent);
                    canvas.drawRoundRect(tmpRect, bh * 0.5f, bh * 0.5f, chipPaint);
                    chipTextPaint.setColor(Color.WHITE);
                    canvas.drawText(countBadge, tmpRect.centerX(), tmpRect.centerY() + dp(3.3f), chipTextPaint);
                    trailingReserve += bw + dp(5);
                }
                if (showAutoChip) {
                    String autoText = LocaleController.getString(R.string.MirrlyInterfaceDrawerAutoBadge);
                    float bw = chipTextPaint.measureText(autoText) + dp(10);
                    float bh = dp(15);
                    float bx = rtl ? (popupRect.left + trailingReserve) : (popupRect.right - trailingReserve - bw);
                    tmpRect.set(bx, cy - bh * 0.5f, bx + bw, cy + bh * 0.5f);
                    chipPaint.setColor(ColorUtils.setAlphaComponent(accent, isDark ? 0x35 : 0x22));
                    canvas.drawRoundRect(tmpRect, bh * 0.5f, bh * 0.5f, chipPaint);
                    chipTextPaint.setColor(accent);
                    canvas.drawText(autoText, tmpRect.centerX(), tmpRect.centerY() + dp(3.3f), chipTextPaint);
                    trailingReserve += bw + dp(5);
                }

                float textStart = rtl ? (popupRect.left + trailingReserve) : (iconCx + dp(16));
                float textEnd = rtl ? (iconCx - dp(16)) : (popupRect.right - trailingReserve);
                float maxTextW = Math.max(dp(36), textEnd - textStart);
                String label = resolveDrawerItemTitle(id);
                CharSequence clipped = TextUtils.ellipsize(label, rowTitlePaint, maxTextW, TextUtils.TruncateAt.END);
                float tw = rowTitlePaint.measureText(clipped, 0, clipped.length());
                float tx = rtl ? (textEnd - tw) : textStart;
                canvas.drawText(clipped, 0, clipped.length(), tx, cy + dp(4f), rowTitlePaint);

                y += rowH;
            }

            if (hasOverflow) {
                overflowPaint.setColor(muted);
                String moreText = "+" + (total - MAX_VISIBLE_ROWS);
                canvas.drawText(moreText, popupRect.centerX(), y + dp(12), overflowPaint);
            }

            frameRimPaint.setColor(resolveCardRimColor(isDark));
            canvas.drawRoundRect(frameRect, corner, corner, frameRimPaint);
        }

        public static class Factory extends UItem.UItemFactory<DrawerMenuPreviewCell> {
            static {
                setup(new Factory());
            }

            @Override
            public DrawerMenuPreviewCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new DrawerMenuPreviewCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                if (item.object instanceof DrawerSpec) {
                    ((DrawerMenuPreviewCell) view).bind((DrawerSpec) item.object);
                }
            }

            @Override
            public boolean isClickable() {
                return false;
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a != null && b != null && a.id == b.id && a.viewType == b.viewType;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return a != null && b != null
                        && a.id == b.id
                        && a.viewType == b.viewType
                        && Objects.equals(a.object, b.object);
            }

            public static UItem of(int id, DrawerSpec spec) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = spec;
                return item;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Preview 4: Chat Message Hold Menu Preview (Tab 3)
    // -------------------------------------------------------------------------

    public static final class MsgMenuSpec {
        public final ArrayList<String> visibleActions;

        public MsgMenuSpec(ArrayList<String> visibleActions) {
            this.visibleActions = visibleActions;
        }

        public static MsgMenuSpec capture() {
            ArrayList<String> order = MirrlyInterfaceConfig.getMessageMenuOrder();
            ArrayList<String> visible = new ArrayList<>();
            for (String key : order) {
                if (MirrlyInterfaceConfig.isMessageMenuItemVisible(key)) {
                    visible.add(key);
                }
            }
            return new MsgMenuSpec(visible);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MsgMenuSpec)) return false;
            MsgMenuSpec that = (MsgMenuSpec) o;
            return Objects.equals(visibleActions, that.visibleActions);
        }

        @Override
        public int hashCode() {
            return Objects.hash(visibleActions);
        }
    }

    public static class MessageMenuPreviewCell extends FrameLayout implements Theme.Colorable {

        private static final int MAX_VISIBLE_ROWS = 5;

        private final Theme.ResourcesProvider resourcesProvider;
        private final Paint frameRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint menuBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint menuShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint menuRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final TextPaint msgTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint msgTimePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint menuRowPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint overflowPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        private final RectF frameRect = new RectF();
        private final RectF bubbleRect = new RectF();
        private final RectF menuRect = new RectF();
        private final RectF tmpRect = new RectF();
        private final Path clipPath = new Path();

        private MotionBackgroundDrawable wallpaperDrawable;
        private final PreviewIconCache iconCache = new PreviewIconCache();
        private MsgMenuSpec spec = MsgMenuSpec.capture();

        public MessageMenuPreviewCell(@NonNull Context context, @Nullable Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setWillNotDraw(false);
            setPadding(dp(12), dp(8), dp(12), dp(10));

            frameRimPaint.setStyle(Paint.Style.STROKE);
            frameRimPaint.setStrokeWidth(dpf2(0.75f));
            menuRimPaint.setStyle(Paint.Style.STROKE);
            menuRimPaint.setStrokeWidth(dpf2(0.8f));

            msgTextPaint.setTextSize(dp(12.5f));
            msgTimePaint.setTextSize(dp(10f));
            menuRowPaint.setTextSize(dp(12.5f));
            overflowPaint.setTextSize(dp(11f));
            overflowPaint.setTypeface(AndroidUtilities.bold());
            overflowPaint.setTextAlign(Paint.Align.CENTER);

            refreshWallpaperColors();
        }

        public void bind(MsgMenuSpec nextSpec) {
            if (nextSpec != null) {
                this.spec = nextSpec;
                invalidate();
            }
        }

        private void refreshWallpaperColors() {
            boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
            MirrlyMonetThemeHelper.PreviewColors monet = MirrlyMonetThemeHelper.getPreviewColors(
                    getContext(), isDark, MirrlyConfig.isMonetAmoled(), true, true);
            int c1 = Theme.getColor(Theme.key_chat_wallpaper, resourcesProvider);
            int c2 = Theme.getColor(Theme.key_chat_wallpaper_gradient_to1, resourcesProvider);
            int c3 = Theme.getColor(Theme.key_chat_wallpaper_gradient_to2, resourcesProvider);
            int c4 = Theme.getColor(Theme.key_chat_wallpaper_gradient_to3, resourcesProvider);
            if (Color.alpha(c1) == 0) {
                c1 = monet.wallpaperTop;
                c2 = monet.wallpaperBottom;
                c3 = monet.wallpaper3;
                c4 = monet.wallpaper4;
            } else {
                if (Color.alpha(c2) == 0) {
                    c2 = c1;
                }
                if (Color.alpha(c3) == 0) {
                    c3 = c1;
                }
                if (Color.alpha(c4) == 0) {
                    c4 = c2;
                }
            }
            if (wallpaperDrawable == null) {
                wallpaperDrawable = new MotionBackgroundDrawable(c1, c2, c3, c4, true);
                wallpaperDrawable.setRoundRadius(dp(16));
            } else {
                wallpaperDrawable.setColors(c1, c2, c3, c4);
            }
        }

        @Override
        public void updateColors() {
            iconCache.clearColors();
            refreshWallpaperColors();
            invalidate();
        }

        @Override
        public void addColorKeys(HashSet<Integer> colors) {
            colors.add(Theme.key_chat_wallpaper);
            colors.add(Theme.key_chat_outBubble);
            colors.add(Theme.key_chat_messageTextOut);
            colors.add(Theme.key_chat_outTimeText);
            colors.add(Theme.key_actionBarDefaultSubmenuBackground);
            colors.add(Theme.key_actionBarDefaultSubmenuItem);
            colors.add(Theme.key_actionBarDefaultSubmenuItemIcon);
            colors.add(Theme.key_text_RedRegular);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int w = MeasureSpec.getSize(widthMeasureSpec);
            setMeasuredDimension(w, dp(238));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0 || spec == null) {
                return;
            }

            boolean rtl = LocaleController.isRTL;
            int outBubble = Theme.getColor(Theme.key_chat_outBubble, resourcesProvider);
            int outText = Theme.getColor(Theme.key_chat_messageTextOut, resourcesProvider);
            int outTime = Theme.getColor(Theme.key_chat_outTimeText, resourcesProvider);
            int menuBg = Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider);
            int menuFg = Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider);
            int menuIcon = Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon, resourcesProvider);
            int redColor = Theme.getColor(Theme.key_text_RedRegular, resourcesProvider);
            int muted = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);
            boolean isDark = AndroidUtilities.computePerceivedBrightness(menuBg) < 0.5f;

            frameRect.set(getPaddingLeft(), getPaddingTop(), w - getPaddingRight(), h - getPaddingBottom());
            float corner = dp(16);
            clipPath.rewind();
            clipPath.addRoundRect(frameRect, corner, corner, Path.Direction.CW);

            canvas.save();
            canvas.clipPath(clipPath);

            if (wallpaperDrawable != null) {
                wallpaperDrawable.setBounds(
                        Math.round(frameRect.left),
                        Math.round(frameRect.top),
                        Math.round(frameRect.right),
                        Math.round(frameRect.bottom)
                );
                wallpaperDrawable.draw(canvas);
            }

            // Sample outgoing message bubble
            String msgText = LocaleController.getString(R.string.MirrlyInterfaceMsgPreviewText);
            String timeText = LocaleController.getString(R.string.MirrlyInterfaceMsgPreviewTime);
            float timeW = msgTimePaint.measureText(timeText);
            float maxBubbleW = frameRect.width() - dp(32);
            float desiredBubbleW = Math.min(maxBubbleW, msgTextPaint.measureText(msgText) + timeW + dp(34));
            float bubbleH = dp(38);
            float bubbleLeft = rtl ? (frameRect.left + dp(14)) : (frameRect.right - dp(14) - desiredBubbleW);
            float bubbleTop = frameRect.top + dp(14);
            bubbleRect.set(bubbleLeft, bubbleTop, bubbleLeft + desiredBubbleW, bubbleTop + bubbleH);
            float bubbleR = Utilities.clamp(dp(Math.max(4f, SharedConfig.bubbleRadius * 0.85f)), dp(16), dp(4));

            bubblePaint.setColor(outBubble);
            canvas.drawRoundRect(bubbleRect, bubbleR, bubbleR, bubblePaint);

            msgTextPaint.setColor(outText);
            msgTimePaint.setColor(outTime);
            float textPad = dp(12);
            float msgMaxW = Math.max(dp(40), bubbleRect.width() - textPad * 2f - timeW - dp(8));
            CharSequence clippedMsg = TextUtils.ellipsize(msgText, msgTextPaint, msgMaxW, TextUtils.TruncateAt.END);
            float msgX = rtl ? (bubbleRect.right - textPad - msgTextPaint.measureText(clippedMsg, 0, clippedMsg.length())) : (bubbleRect.left + textPad);
            canvas.drawText(clippedMsg, 0, clippedMsg.length(), msgX, bubbleRect.centerY() + dp(3.5f), msgTextPaint);

            float timeX = rtl ? (bubbleRect.left + textPad) : (bubbleRect.right - textPad - timeW);
            canvas.drawText(timeText, timeX, bubbleRect.bottom - dp(9), msgTimePaint);

            // Glass context popup menu below bubble
            int total = spec.visibleActions.size();
            int shown = Math.min(MAX_VISIBLE_ROWS, total);
            boolean hasOverflow = total > MAX_VISIBLE_ROWS;
            float rowH = dp(28);
            float menuH = dp(12) + Math.max(1, shown) * rowH + (hasOverflow ? dp(20) : 0);
            float menuW = Math.min(dp(216), frameRect.width() - dp(36));
            float menuLeft = rtl ? (frameRect.left + dp(14)) : (frameRect.right - dp(14) - menuW);
            float menuTop = bubbleRect.bottom + dp(8);
            menuRect.set(menuLeft, menuTop, menuLeft + menuW, Math.min(frameRect.bottom - dp(10), menuTop + menuH));

            menuShadowPaint.setColor(isDark ? 0x48000000 : 0x1E000000);
            tmpRect.set(menuRect.left - dp(1), menuRect.top + dp(2), menuRect.right + dp(1), menuRect.bottom + dp(4));
            canvas.drawRoundRect(tmpRect, dp(15), dp(15), menuShadowPaint);

            menuBgPaint.setColor(resolveCardGlassFill(isDark, menuBg));
            menuRimPaint.setColor(resolveCardRimColor(isDark));
            canvas.drawRoundRect(menuRect, dp(14), dp(14), menuBgPaint);
            canvas.drawRoundRect(menuRect, dp(14), dp(14), menuRimPaint);

            float y = menuRect.top + dp(6);
            for (int i = 0; i < shown; i++) {
                String key = spec.visibleActions.get(i);
                boolean destructive = isMsgMenuItemDestructive(key);
                int iconColor = destructive ? redColor : menuIcon;
                int labelColor = destructive ? redColor : menuFg;

                float cy = y + rowH * 0.5f;
                float iconCx = rtl ? (menuRect.right - dp(20)) : (menuRect.left + dp(20));
                Drawable rowIcon = iconCache.get(getContext(), resolveMsgMenuItemIcon(key), iconColor);
                drawCenteredDrawable(canvas, rowIcon, iconCx, cy, dp(16), 255);

                float textStart = rtl ? (menuRect.left + dp(14)) : (iconCx + dp(16));
                float textEnd = rtl ? (iconCx - dp(16)) : (menuRect.right - dp(14));
                float maxTextW = Math.max(dp(40), textEnd - textStart);
                String label = resolveMsgMenuItemTitle(key);
                menuRowPaint.setColor(labelColor);
                CharSequence clipped = TextUtils.ellipsize(label, menuRowPaint, maxTextW, TextUtils.TruncateAt.END);
                float tw = menuRowPaint.measureText(clipped, 0, clipped.length());
                float tx = rtl ? (textEnd - tw) : textStart;
                canvas.drawText(clipped, 0, clipped.length(), tx, cy + dp(4f), menuRowPaint);
                y += rowH;
            }

            if (hasOverflow) {
                overflowPaint.setColor(muted);
                canvas.drawText("+" + (total - MAX_VISIBLE_ROWS), menuRect.centerX(), y + dp(12), overflowPaint);
            }

            canvas.restore();

            frameRimPaint.setColor(resolveCardRimColor(isDark));
            canvas.drawRoundRect(frameRect, corner, corner, frameRimPaint);
        }

        public static class Factory extends UItem.UItemFactory<MessageMenuPreviewCell> {
            static {
                setup(new Factory());
            }

            @Override
            public MessageMenuPreviewCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new MessageMenuPreviewCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                if (item.object instanceof MsgMenuSpec) {
                    ((MessageMenuPreviewCell) view).bind((MsgMenuSpec) item.object);
                }
            }

            @Override
            public boolean isClickable() {
                return false;
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a != null && b != null && a.id == b.id && a.viewType == b.viewType;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return a != null && b != null
                        && a.id == b.id
                        && a.viewType == b.viewType
                        && Objects.equals(a.object, b.object);
            }

            public static UItem of(int id, MsgMenuSpec spec) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = spec;
                return item;
            }
        }
    }
}
