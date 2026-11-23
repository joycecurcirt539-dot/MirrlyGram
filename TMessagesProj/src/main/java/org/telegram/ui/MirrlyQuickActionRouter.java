package org.telegram.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.mirrly.MirrlyConfig;
import org.telegram.messenger.mirrly.MirrlyInterfaceConfig;
import org.telegram.messenger.mirrly.MirrlyMonetThemeHelper;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.glass.GlassTabView;
import org.telegram.ui.Wallet.WalletActivity;

/**
 * Central dispatcher and visual metadata resolver for MirrlyGram bottom-bar quick actions.
 */
public final class MirrlyQuickActionRouter {

    private MirrlyQuickActionRouter() {
    }

    public static void executeAction(
            @Nullable BaseFragment host,
            @Nullable String actionKey,
            long customDialogId,
            @Nullable Runnable onCustomSearchRequested
    ) {
        executeAction(host, null, actionKey, customDialogId, onCustomSearchRequested);
    }

    public static void executeAction(
            @Nullable BaseFragment host,
            @Nullable View anchorView,
            @Nullable String actionKey,
            long customDialogId,
            @Nullable Runnable onCustomSearchRequested
    ) {
        if (host == null || host.getParentActivity() == null) {
            return;
        }
        final String action = MirrlyInterfaceConfig.isValidQuickAction(actionKey)
                ? actionKey
                : MirrlyInterfaceConfig.QUICK_SAVED;
        final int account = host.getCurrentAccount();

        switch (action) {
            case MirrlyInterfaceConfig.QUICK_SAVED: {
                Bundle args = new Bundle();
                args.putLong("user_id", UserConfig.getInstance(account).getClientUserId());
                host.presentFragment(new ChatActivity(args));
                break;
            }
            case MirrlyInterfaceConfig.QUICK_NOTES: {
                host.presentFragment(new MirrlyNotesChatActivity());
                break;
            }
            case MirrlyInterfaceConfig.QUICK_DELETED: {
                host.presentFragment(new MirrlyDeletedChatActivity());
                break;
            }
            case MirrlyInterfaceConfig.QUICK_MIRRLY_SETTINGS: {
                host.presentFragment(new MirrlyGramSettingsActivity());
                break;
            }
            case MirrlyInterfaceConfig.QUICK_CUSTOMIZATION: {
                host.presentFragment(new MirrlyCustomizationActivity());
                break;
            }
            case MirrlyInterfaceConfig.QUICK_NETWORK: {
                host.presentFragment(new MirrlyNetworkSettingsActivity());
                break;
            }
            case MirrlyInterfaceConfig.QUICK_INTERFACE_SETTINGS: {
                host.presentFragment(new MirrlyInterfaceSettingsActivity());
                break;
            }
            case MirrlyInterfaceConfig.QUICK_ARCHIVE: {
                Bundle args = new Bundle();
                args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_DEFAULT);
                args.putInt("folderId", 1);
                host.presentFragment(new DialogsActivity(args));
                break;
            }
            case MirrlyInterfaceConfig.QUICK_CALLS: {
                Bundle args = new Bundle();
                args.putBoolean("needFinishFragment", true);
                host.presentFragment(new CallLogActivity(args));
                break;
            }
            case MirrlyInterfaceConfig.QUICK_CONTACTS: {
                Bundle args = new Bundle();
                args.putBoolean("needPhonebook", true);
                args.putBoolean("needFinishFragment", true);
                host.presentFragment(new ContactsActivity(args));
                break;
            }
            case MirrlyInterfaceConfig.QUICK_SETTINGS: {
                host.presentFragment(new SettingsActivity());
                break;
            }
            case MirrlyInterfaceConfig.QUICK_PROFILE: {
                Bundle args = new Bundle();
                args.putLong("user_id", UserConfig.getInstance(account).getClientUserId());
                args.putBoolean("my_profile", true);
                host.presentFragment(new ProfileActivity(args));
                break;
            }
            case MirrlyInterfaceConfig.QUICK_SEARCH: {
                if (onCustomSearchRequested != null) {
                    onCustomSearchRequested.run();
                } else if (host instanceof DialogsActivity) {
                    ((DialogsActivity) host).openSearchFromTabs();
                } else if (host instanceof MainTabsActivity) {
                    DialogsActivity dialogs = ((MainTabsActivity) host).getDialogsActivity();
                    if (dialogs != null) {
                        dialogs.openSearchFromTabs();
                    }
                }
                break;
            }
            case MirrlyInterfaceConfig.QUICK_NEW_CHAT: {
                if (MessagesController.getInstance(account).isFrozen()) {
                    AccountFrozenAlert.show(account);
                    return;
                }
                Bundle args = new Bundle();
                args.putBoolean("destroyAfterSelect", true);
                host.presentFragment(new ContactsActivity(args));
                break;
            }
            case MirrlyInterfaceConfig.QUICK_NEW_GROUP: {
                if (MessagesController.getInstance(account).isFrozen()) {
                    AccountFrozenAlert.show(account);
                    return;
                }
                host.presentFragment(new GroupCreateActivity(new Bundle()));
                break;
            }
            case MirrlyInterfaceConfig.QUICK_NEW_CHANNEL: {
                if (MessagesController.getInstance(account).isFrozen()) {
                    AccountFrozenAlert.show(account);
                    return;
                }
                Bundle args = new Bundle();
                args.putInt("step", 0);
                host.presentFragment(new ChannelCreateActivity(args));
                break;
            }
            case MirrlyInterfaceConfig.QUICK_FOLDERS: {
                host.presentFragment(new FiltersSetupActivity());
                break;
            }
            case MirrlyInterfaceConfig.QUICK_PROXY_LIST: {
                host.presentFragment(new ProxyListActivity());
                break;
            }
            case MirrlyInterfaceConfig.QUICK_THEME_TOGGLE: {
                toggleThemeWithReveal(host, anchorView);
                break;
            }
            case MirrlyInterfaceConfig.QUICK_DEVICES: {
                host.presentFragment(new SessionsActivity(0));
                break;
            }
            case MirrlyInterfaceConfig.QUICK_APPEARANCE: {
                host.presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
                break;
            }
            case MirrlyInterfaceConfig.QUICK_WALLET: {
                if (MessagesController.getInstance(account).config.walletAvailable.get()) {
                    host.presentFragment(new WalletActivity());
                } else {
                    BulletinFactory.of(host)
                            .createErrorBulletin(LocaleController.getString(R.string.MirrlyInterfaceWalletUnavailable))
                            .show();
                }
                break;
            }
            case MirrlyInterfaceConfig.QUICK_CUSTOM_CHAT: {
                openCustomChat(host, account, customDialogId);
                break;
            }
            default: {
                Bundle args = new Bundle();
                args.putLong("user_id", UserConfig.getInstance(account).getClientUserId());
                host.presentFragment(new ChatActivity(args));
                break;
            }
        }
    }

    private static void toggleThemeWithReveal(@NonNull BaseFragment host, @Nullable View anchorView) {
        if (DialogsActivity.switchingTheme) {
            return;
        }
        final boolean isCurrentThemeDark = host.getResourceProvider() != null
                ? host.getResourceProvider().isDark()
                : Theme.isCurrentThemeDark();
        final int monetMode = MirrlyConfig.getMonetMode();
        if (monetMode != MirrlyConfig.MONET_MODE_OFF) {
            final int nextMode = isCurrentThemeDark ? MirrlyConfig.MONET_MODE_LIGHT : MirrlyConfig.MONET_MODE_DARK;
            MirrlyMonetThemeHelper.applyMonetMode(host.getContext(), nextMode, true, anchorView);
            return;
        }

        if (ApplicationLoader.applicationContext == null) {
            return;
        }
        DialogsActivity.switchingTheme = true;
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
        String dayThemeName = preferences.getString("lastDayTheme", "Blue");
        if (Theme.getTheme(dayThemeName) == null || Theme.getTheme(dayThemeName).isDark()) {
            dayThemeName = "Blue";
        }
        String nightThemeName = preferences.getString("lastDarkTheme", "Dark Blue");
        if (Theme.getTheme(nightThemeName) == null || !Theme.getTheme(nightThemeName).isDark()) {
            nightThemeName = "Dark Blue";
        }
        Theme.ThemeInfo activeTheme = Theme.getActiveTheme();
        if (dayThemeName.equals(nightThemeName)) {
            if ((activeTheme != null && activeTheme.isDark()) || "Dark Blue".equals(dayThemeName) || "Night".equals(dayThemeName)) {
                dayThemeName = "Blue";
            } else {
                nightThemeName = "Dark Blue";
            }
        }

        final boolean toDark = activeTheme == null || dayThemeName.equals(activeTheme.getKey());
        Theme.ThemeInfo targetInfo = Theme.getTheme(toDark ? nightThemeName : dayThemeName);
        if (targetInfo == null) {
            targetInfo = Theme.getTheme(toDark ? "Dark Blue" : "Blue");
        }
        if (targetInfo == null) {
            DialogsActivity.switchingTheme = false;
            return;
        }

        final int[] pos = new int[2];
        if (anchorView != null && anchorView.isAttachedToWindow()) {
            anchorView.getLocationInWindow(pos);
            pos[0] += anchorView.getMeasuredWidth() / 2;
            pos[1] += anchorView.getMeasuredHeight() / 2;
        } else if (host.getFragmentView() != null && host.getFragmentView().isAttachedToWindow()) {
            host.getFragmentView().getLocationInWindow(pos);
            pos[0] += host.getFragmentView().getMeasuredWidth() / 2;
            pos[1] += Math.max(0, host.getFragmentView().getMeasuredHeight() - AndroidUtilities.dp(40));
        } else {
            pos[0] = AndroidUtilities.displaySize.x / 2;
            pos[1] = AndroidUtilities.displaySize.y / 2;
        }

        NotificationCenter.getGlobalInstance().postNotificationName(
                NotificationCenter.needSetDayNightTheme,
                targetInfo,
                false,
                pos,
                -1,
                toDark,
                null,
                null,
                null,
                true
        );
        Theme.turnOffAutoNight(BulletinFactory.of(host), () ->
                host.presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_NIGHT))
        );
    }

    private static void openCustomChat(@NonNull BaseFragment host, int account, long dialogId) {
        if (dialogId == 0L) {
            showMissingChatError(host);
            return;
        }
        final MessagesController mc = MessagesController.getInstance(account);
        final TLObject obj = resolveCustomChatObject(account, dialogId, true);
        if (obj == null) {
            showMissingChatError(host);
            return;
        }

        final Bundle args = new Bundle();
        if (DialogObject.isEncryptedDialog(dialogId)) {
            args.putInt("enc_id", DialogObject.getEncryptedChatId(dialogId));
            if (mc.checkCanOpenChat(args, host)) {
                host.presentFragment(new ChatActivity(args));
            }
            return;
        }
        if (DialogObject.isUserDialog(dialogId)) {
            args.putLong("user_id", dialogId);
            if (mc.checkCanOpenChat(args, host)) {
                host.presentFragment(new ChatActivity(args));
            }
            return;
        }

        long chatId = -dialogId;
        TLRPC.Chat chat = obj instanceof TLRPC.Chat ? (TLRPC.Chat) obj : mc.getChat(chatId);
        if (chat == null) {
            showMissingChatError(host);
            return;
        }
        if (chat.migrated_to != null && chat.migrated_to.channel_id != 0) {
            args.putLong("migrated_to", dialogId);
            chatId = chat.migrated_to.channel_id;
            TLRPC.Chat migrated = mc.getChat(chatId);
            if (migrated != null) {
                chat = migrated;
            }
        }
        args.putLong("chat_id", chatId);

        if (!mc.checkCanOpenChat(args, host)) {
            return;
        }

        final TLRPC.Dialog dialog = mc.getDialog(-chat.id);
        final boolean viewForumAsMessages = dialog != null && dialog.view_forum_as_messages;
        if (chat.monoforum) {
            args.putInt("chatMode", ChatActivity.MODE_SUGGESTIONS);
            args.putBoolean("isSubscriberSuggestions", !ChatObject.canManageMonoForum(account, chat));
            host.presentFragment(new ChatActivity(args));
        } else if (ChatObject.isCommunity(chat)) {
            Bundle commArgs = new Bundle();
            commArgs.putLong("community_id", chat.id);
            host.presentFragment(new DialogsActivity(commArgs));
        } else if (chat.forum && !viewForumAsMessages) {
            if (ChatObject.areTabsEnabled(chat)) {
                ChatActivity activity = new ChatActivity(args);
                ForumUtilities.applyTopic(activity, MessagesStorage.TopicKey.of(-chat.id, mc.getForumLastTopicId(chat.id)));
                host.presentFragment(activity);
            } else {
                host.presentFragment(new TopicsFragment(args));
            }
        } else {
            host.presentFragment(new ChatActivity(args));
        }
    }

    private static void showMissingChatError(@NonNull BaseFragment host) {
        BulletinFactory.of(host)
                .createErrorBulletin(LocaleController.getString(R.string.MirrlyInterfaceCustomChatMissing))
                .show();
    }

    @Nullable
    public static TLObject resolveCustomChatObject(int account, long dialogId) {
        return resolveCustomChatObject(account, dialogId, false);
    }

    @Nullable
    public static TLObject resolveCustomChatObject(int account, long dialogId, boolean allowStorageFallback) {
        if (dialogId == 0L) {
            return null;
        }
        final MessagesController mc = MessagesController.getInstance(account);
        final MessagesStorage ms = allowStorageFallback ? MessagesStorage.getInstance(account) : null;
        if (DialogObject.isEncryptedDialog(dialogId)) {
            final int encId = DialogObject.getEncryptedChatId(dialogId);
            final TLRPC.EncryptedChat encChat = mc.getEncryptedChat(encId);
            if (encChat == null) {
                return null;
            }
            TLRPC.User user = mc.getUser(encChat.user_id);
            if (user == null && ms != null) {
                user = ms.getUserSync(encChat.user_id);
                if (user != null) {
                    mc.putUser(user, true);
                }
            }
            return user;
        }
        if (DialogObject.isUserDialog(dialogId)) {
            TLRPC.User user = mc.getUser(dialogId);
            if (user == null && ms != null) {
                user = ms.getUserSync(dialogId);
                if (user != null) {
                    mc.putUser(user, true);
                }
            }
            return user;
        }
        final long chatId = -dialogId;
        TLRPC.Chat chat = mc.getChat(chatId);
        if (chat == null && ms != null) {
            chat = ms.getChatSync(chatId);
            if (chat != null) {
                mc.putChat(chat, true);
            }
        }
        return chat;
    }

    @NonNull
    public static String resolveCustomChatTitle(int account, long dialogId, @Nullable String fallbackTitle) {
        final TLObject obj = resolveCustomChatObject(account, dialogId, false);
        if (obj instanceof TLRPC.User) {
            final TLRPC.User user = (TLRPC.User) obj;
            if (UserObject.isUserSelf(user)) {
                return LocaleController.getString(R.string.SavedMessages);
            }
            final String firstName = UserObject.getFirstName(user);
            if (!TextUtils.isEmpty(firstName)) {
                return firstName;
            }
            final String fullName = UserObject.getUserName(user);
            if (!TextUtils.isEmpty(fullName)) {
                return fullName;
            }
        } else if (obj instanceof TLRPC.Chat) {
            final String title = ((TLRPC.Chat) obj).title;
            if (!TextUtils.isEmpty(title)) {
                return title;
            }
        }
        if (!TextUtils.isEmpty(fallbackTitle)) {
            return fallbackTitle;
        }
        return LocaleController.getString(R.string.MirrlyQuickShortCustomChat);
    }

    public static @DrawableRes int resolveIconRes(@Nullable String actionKey) {
        if (actionKey == null) {
            return R.drawable.outline_saved_24;
        }
        switch (actionKey) {
            case MirrlyInterfaceConfig.QUICK_SAVED:
                return R.drawable.outline_saved_24;
            case MirrlyInterfaceConfig.QUICK_NOTES:
                return R.drawable.outline_profile_edit_24;
            case MirrlyInterfaceConfig.QUICK_DELETED:
                return R.drawable.msg_delete;
            case MirrlyInterfaceConfig.QUICK_MIRRLY_SETTINGS:
                return R.drawable.settings_mirrly;
            case MirrlyInterfaceConfig.QUICK_CUSTOMIZATION:
                return R.drawable.msg_palette;
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
                return R.drawable.outline_header_search;
            case MirrlyInterfaceConfig.QUICK_NEW_CHAT:
                return R.drawable.msg_message;
            case MirrlyInterfaceConfig.QUICK_NEW_GROUP:
                return R.drawable.outline_groups_24;
            case MirrlyInterfaceConfig.QUICK_NEW_CHANNEL:
                return R.drawable.msg_channel_create;
            case MirrlyInterfaceConfig.QUICK_FOLDERS:
                return R.drawable.msg_folders;
            case MirrlyInterfaceConfig.QUICK_PROXY_LIST:
                return R.drawable.mirrly_settings_engine;
            case MirrlyInterfaceConfig.QUICK_THEME_TOGGLE:
                return Theme.isCurrentThemeDark() ? R.drawable.menu_day_mode_24 : R.drawable.menu_night_mode_24;
            case MirrlyInterfaceConfig.QUICK_DEVICES:
                return R.drawable.settings_devices;
            case MirrlyInterfaceConfig.QUICK_APPEARANCE:
                return R.drawable.msg_theme;
            case MirrlyInterfaceConfig.QUICK_WALLET:
                return R.drawable.ic_gram;
            case MirrlyInterfaceConfig.QUICK_CUSTOM_CHAT:
                return R.drawable.msg_openin;
            default:
                return R.drawable.outline_saved_24;
        }
    }

    @NonNull
    public static String resolveShortLabel(int account, @Nullable String actionKey, long customDialogId) {
        return resolveShortLabel(account, actionKey, customDialogId, null);
    }

    @NonNull
    public static String resolveShortLabel(int account, @Nullable String actionKey, long customDialogId, @Nullable String storedChatTitle) {
        if (actionKey == null) {
            return LocaleController.getString(R.string.MirrlyQuickShortSaved);
        }
        switch (actionKey) {
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
            case MirrlyInterfaceConfig.QUICK_CUSTOM_CHAT:
                return resolveCustomChatTitle(account, customDialogId, storedChatTitle);
            default:
                return LocaleController.getString(R.string.MirrlyQuickShortSaved);
        }
    }

    @NonNull
    public static String resolveFullLabel(int account, @Nullable String actionKey, long customDialogId) {
        return resolveFullLabel(account, actionKey, customDialogId, null);
    }

    @NonNull
    public static String resolveFullLabel(int account, @Nullable String actionKey, long customDialogId, @Nullable String storedChatTitle) {
        if (actionKey == null) {
            return LocaleController.getString(R.string.MirrlyQuickNameSaved);
        }
        switch (actionKey) {
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
            case MirrlyInterfaceConfig.QUICK_CUSTOM_CHAT: {
                String title = resolveCustomChatTitle(account, customDialogId, storedChatTitle);
                return !TextUtils.isEmpty(title)
                        ? title
                        : LocaleController.getString(R.string.MirrlyQuickNameCustomChat);
            }
            default:
                return LocaleController.getString(R.string.MirrlyQuickNameSaved);
        }
    }

    public static void bindTabView(
            @NonNull GlassTabView tab,
            int account,
            @Nullable String actionKey,
            long customDialogId,
            @Nullable String storedChatTitle
    ) {
        final String action = MirrlyInterfaceConfig.isValidQuickAction(actionKey)
                ? actionKey
                : MirrlyInterfaceConfig.QUICK_SAVED;
        final String shortLabel = resolveShortLabel(account, action, customDialogId, storedChatTitle);
        if (MirrlyInterfaceConfig.QUICK_CUSTOM_CHAT.equals(action)) {
            final TLObject chatObj = resolveCustomChatObject(account, customDialogId, false);
            tab.setCustomAvatar(chatObj, account, shortLabel, R.drawable.msg_openin);
        } else if (MirrlyInterfaceConfig.QUICK_PROFILE.equals(action)) {
            final TLRPC.User self = MessagesController.getInstance(account).getUser(UserConfig.getInstance(account).getClientUserId());
            tab.setCustomAvatar(self, account, shortLabel, R.drawable.msg_openprofile);
        } else {
            tab.setCustomStaticIcon(resolveIconRes(action), shortLabel);
        }
        tab.setContentDescription(resolveFullLabel(account, action, customDialogId, storedChatTitle));
    }
}
