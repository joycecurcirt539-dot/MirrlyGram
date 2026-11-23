package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.mirrly.MirrlyPhonePrivacyManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MirrlyPhoneEyeDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Dedicated Mirrlygram sub-screen for local phone privacy (hide/spoof per account),
 * interactive live preview, and per-user spoiler/replacement rules.
 */
public class MirrlyProfileSettingsActivity extends MirrlyBaseSettingsFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int ID_HERO = 1;
    private static final int ID_PREVIEW = 2;

    private static final int ID_HIDE_OWN = 10;
    private static final int ID_AUTO_REHIDE = 11;
    private static final int ID_MASK_CHECK_BANNER = 12;
    private static final int ID_SPOOF_OWN = 13;

    private static final int ID_PEER_ADD = 20;
    private static final int ID_PEER_RESET_ALL = 21;
    private static final int ID_PEER_BASE = 1000;

    private boolean previewTempRevealed = false;

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.mirrlyPhonePrivacyChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.mirrlyPhonePrivacyChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
        super.onFragmentDestroy();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (previewTempRevealed && MirrlyPhonePrivacyManager.isAutoRehideOnLeave(currentAccount)) {
            previewTempRevealed = false;
        }
    }

    @Override
    protected CharSequence getScreenTitle() {
        return LocaleController.getString(R.string.MirrlyProfileTitle);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        TLRPC.User selfUser = getUserConfig().getCurrentUser();
        long selfId = getUserConfig().getClientUserId();
        int ownMode = MirrlyPhonePrivacyManager.getOwnMode(currentAccount);
        boolean isOwnHidden = ownMode == MirrlyPhonePrivacyManager.OWN_MODE_HIDE;
        boolean isOwnSpoofed = ownMode == MirrlyPhonePrivacyManager.OWN_MODE_SPOOF;
        String ownSpoof = MirrlyPhonePrivacyManager.getOwnSpoofPhone(currentAccount);
        String heroName = MirrlyPhonePrivacyManager.resolveUserDisplayName(selfUser, selfId);

        items.add(MirrlyUiKit.HeroCardView.Factory.of(
                ID_HERO,
                new MirrlyUiKit.HeroSpec(
                        MirrlyUiKit.HeroSpec.MODE_LOTTIE,
                        R.raw.utyan_private,
                        false,
                        LocaleController.getString(R.string.MirrlyProfileTitle),
                        new CharSequence[]{
                                LocaleController.formatString(R.string.MirrlyProfileHeroPillLocal, heroName)
                        },
                        LocaleController.getString(R.string.MirrlyProfileTitle)
                )
        ));

        items.add(PhonePrivacyPreviewCell.Factory.of(
                ID_PREVIEW,
                new PreviewSpec(
                        currentAccount,
                        selfId,
                        ownMode,
                        ownSpoof,
                        selfUser != null && selfUser.phone != null ? selfUser.phone : "",
                        UserObject.getUserName(selfUser),
                        UserObject.getPublicUsername(selfUser),
                        previewTempRevealed,
                        () -> {
                            previewTempRevealed = !previewTempRevealed;
                            updateList(true);
                        },
                        textToCopy -> MirrlyUiKit.copyTextWithBulletin(
                                this,
                                textToCopy,
                                LocaleController.getString(R.string.PhoneCopied),
                                null,
                                getResourceProvider()
                        )
                )
        ));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyPhoneOwnHeader)));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_HIDE_OWN,
                R.drawable.msg_spoiler,
                LocaleController.getString(R.string.MirrlyPhoneHideOwn),
                isOwnHidden
        ));
        if (isOwnHidden) {
            items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                    ID_AUTO_REHIDE,
                    R.drawable.msg_autodelete,
                    LocaleController.getString(R.string.MirrlyPhoneAutoRehide),
                    MirrlyPhonePrivacyManager.isAutoRehideOnLeave(currentAccount)
            ));
        }
        if (isOwnHidden || isOwnSpoofed) {
            items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                    ID_MASK_CHECK_BANNER,
                    R.drawable.outline_shield_check,
                    LocaleController.getString(R.string.MirrlyPhoneMaskCheckBanner),
                    MirrlyPhonePrivacyManager.isMaskCheckPhoneBanner(currentAccount)
            ));
        }
        items.add(UItem.asButton(
                ID_SPOOF_OWN,
                R.drawable.msg_edit,
                LocaleController.getString(R.string.MirrlyPhoneSpoofOwn),
                isOwnSpoofed
                        ? MirrlyPhonePrivacyManager.formatDisplayPhone(ownSpoof)
                        : LocaleController.getString(R.string.MirrlyPhoneSpoofOff)
        ));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MirrlyPhoneOwnHint)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyPhonePeersHeader)));
        items.add(UItem.asButton(
                ID_PEER_ADD,
                R.drawable.msg_addcontact,
                LocaleController.getString(R.string.MirrlyPhonePeersAdd)
        ).accent());

        List<MirrlyPhonePrivacyManager.PeerRule> peerRules = MirrlyPhonePrivacyManager.getPeerRules(currentAccount);
        for (int i = 0; i < peerRules.size(); i++) {
            MirrlyPhonePrivacyManager.PeerRule rule = peerRules.get(i);
            TLRPC.User peerUser = getMessagesController().getUser(rule.userId);
            String peerName = peerUser != null
                    ? MirrlyPhonePrivacyManager.resolveUserDisplayName(peerUser, rule.userId)
                    : (!TextUtils.isEmpty(rule.cachedName) ? rule.cachedName : ("ID " + rule.userId));
            items.add(PeerRuleCell.Factory.of(ID_PEER_BASE + i, currentAccount, rule, peerName));
        }
        if (!peerRules.isEmpty()) {
            items.add(UItem.asButton(
                    ID_PEER_RESET_ALL,
                    R.drawable.msg_delete,
                    LocaleController.getString(R.string.MirrlyPhonePeersResetAll)
            ).red());
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.MirrlyPhonePeersHint)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item == null) {
            return;
        }
        if (item.id == ID_HIDE_OWN) {
            boolean target = !MirrlyPhonePrivacyManager.isOwnHidden(currentAccount);
            if (!MirrlyPhonePrivacyManager.setOwnHide(currentAccount, target)) {
                triggerConflictFeedback(
                        view,
                        LocaleController.getString(R.string.MirrlyPhoneConflictSpoofActive),
                        LocaleController.getString(R.string.MirrlyPhoneDisableSpoofAction),
                        () -> {
                            MirrlyPhonePrivacyManager.clearOwnRule(currentAccount);
                            previewTempRevealed = false;
                            updateList(true);
                        }
                );
                return;
            }
            if (view instanceof TextCell) {
                ((TextCell) view).setChecked(target);
            }
            previewTempRevealed = false;
            updateList(true);
            return;
        }
        if (item.id == ID_AUTO_REHIDE) {
            boolean target = !MirrlyPhonePrivacyManager.isAutoRehideOnLeave(currentAccount);
            if (view instanceof TextCell) {
                ((TextCell) view).setChecked(target);
            }
            MirrlyPhonePrivacyManager.setAutoRehideOnLeave(currentAccount, target);
            if (listView != null && listView.adapter != null) {
                listView.adapter.updateWithoutNotify();
            }
            return;
        }
        if (item.id == ID_MASK_CHECK_BANNER) {
            boolean target = !MirrlyPhonePrivacyManager.isMaskCheckPhoneBanner(currentAccount);
            if (view instanceof TextCell) {
                ((TextCell) view).setChecked(target);
            }
            MirrlyPhonePrivacyManager.setMaskCheckPhoneBanner(currentAccount, target);
            if (listView != null && listView.adapter != null) {
                listView.adapter.updateWithoutNotify();
            }
            return;
        }
        if (item.id == ID_SPOOF_OWN) {
            if (MirrlyPhonePrivacyManager.isOwnHidden(currentAccount)) {
                triggerConflictFeedback(
                        view,
                        LocaleController.getString(R.string.MirrlyPhoneConflictHideActive),
                        LocaleController.getString(R.string.MirrlyPhoneDisableHideAction),
                        () -> {
                            MirrlyPhonePrivacyManager.setOwnHide(currentAccount, false);
                            previewTempRevealed = false;
                            updateList(true);
                        }
                );
                return;
            }
            MirrlyPhonePrivacySheets.showOwnSpoofSheet(this, getResourceProvider(), currentAccount, () -> updateList(true));
            return;
        }
        if (item.id == ID_PEER_ADD) {
            Bundle args = new Bundle();
            args.putBoolean("onlyUsers", true);
            args.putBoolean("destroyAfterSelect", true);
            args.putBoolean("returnAsResult", true);
            args.putBoolean("allowSelf", false);
            args.putBoolean("allowBots", false);
            ContactsActivity contactsActivity = new ContactsActivity(args);
            contactsActivity.setDelegate((user, param, activity) -> {
                if (user != null) {
                    AndroidUtilities.runOnUIThread(() -> MirrlyPhonePrivacySheets.showPeerRuleSheet(
                            this,
                            getResourceProvider(),
                            currentAccount,
                            user,
                            user.id,
                            MirrlyPhonePrivacyManager.resolveUserDisplayName(user, user.id),
                            () -> updateList(true)
                    ), 180);
                }
            });
            presentFragment(contactsActivity);
            return;
        }
        if (item.id == ID_PEER_RESET_ALL) {
            List<MirrlyPhonePrivacyManager.PeerRule> rules = MirrlyPhonePrivacyManager.getPeerRules(currentAccount);
            if (rules.isEmpty()) {
                return;
            }
            MirrlyUiKit.confirmDangerAction(
                    this,
                    getResourceProvider(),
                    LocaleController.getString(R.string.MirrlyPhonePeersResetConfirmTitle),
                    LocaleController.formatString(R.string.MirrlyPhonePeersResetConfirmText, rules.size()),
                    LocaleController.getString(R.string.Delete),
                    () -> {
                        MirrlyPhonePrivacyManager.clearAllPeerRules(currentAccount);
                        updateList(true);
                        BulletinFactory.of(this)
                                .createSimpleBulletin(R.raw.ic_delete, LocaleController.getString(R.string.MirrlyPhonePeersResetDone))
                                .show();
                    }
            );
            return;
        }
        if (item.id >= ID_PEER_BASE && item.object instanceof MirrlyPhonePrivacyManager.PeerRule) {
            MirrlyPhonePrivacyManager.PeerRule rule = (MirrlyPhonePrivacyManager.PeerRule) item.object;
            TLRPC.User peerUser = getMessagesController().getUser(rule.userId);
            MirrlyPhonePrivacySheets.showPeerRuleSheet(
                    this,
                    getResourceProvider(),
                    currentAccount,
                    peerUser,
                    rule.userId,
                    rule.cachedName,
                    () -> updateList(true)
            );
        }
    }

    private void triggerConflictFeedback(
            @Nullable View rowView,
            String message,
            String actionLabel,
            Runnable onAction
    ) {
        AndroidUtilities.shakeViewSpring(rowView, 6f);
        if (listView != null) {
            for (int i = 0; i < listView.getChildCount(); i++) {
                View child = listView.getChildAt(i);
                if (child instanceof PhonePrivacyPreviewCell) {
                    ((PhonePrivacyPreviewCell) child).shakePreview();
                    break;
                }
            }
        }
        BotWebViewVibrationEffect.APP_ERROR.vibrate();
        BulletinFactory.of(this)
                .createSimpleBulletin(R.raw.error, message, actionLabel, Bulletin.DURATION_LONG, onAction)
                .show();
    }

    private void updateList(boolean animated) {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(animated);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.mirrlyPhonePrivacyChanged) {
            updateList(true);
        } else if (id == NotificationCenter.didSetNewTheme) {
            if (listView != null && listView.adapter != null) {
                listView.adapter.updateWithoutNotify();
                for (int i = 0; i < listView.getChildCount(); i++) {
                    View child = listView.getChildAt(i);
                    if (child instanceof Theme.Colorable) {
                        ((Theme.Colorable) child).updateColors();
                    }
                }
                listView.invalidate();
            }
        } else if (id == NotificationCenter.updateInterfaces && account == currentAccount) {
            int mask = args.length > 0 && args[0] instanceof Integer ? (Integer) args[0] : 0;
            if ((mask & (MessagesController.UPDATE_MASK_NAME | MessagesController.UPDATE_MASK_AVATAR)) != 0) {
                updateList(true);
            }
        }
    }

    /**
     * Immutable payload for {@link PhonePrivacyPreviewCell} DiffUtil checks.
     */
    private static final class PreviewSpec {
        final int account;
        final long selfId;
        final int ownMode;
        final String ownSpoof;
        final String realPhone;
        final String userName;
        final String username;
        final boolean tempRevealed;
        final Runnable onToggleEye;
        final org.telegram.messenger.Utilities.Callback<String> onCopy;

        PreviewSpec(
                int account,
                long selfId,
                int ownMode,
                String ownSpoof,
                String realPhone,
                String userName,
                @Nullable String username,
                boolean tempRevealed,
                Runnable onToggleEye,
                org.telegram.messenger.Utilities.Callback<String> onCopy
        ) {
            this.account = account;
            this.selfId = selfId;
            this.ownMode = ownMode;
            this.ownSpoof = ownSpoof != null ? ownSpoof : "";
            this.realPhone = realPhone != null ? realPhone : "";
            this.userName = userName != null ? userName : "";
            this.username = username != null ? username : "";
            this.tempRevealed = tempRevealed;
            this.onToggleEye = onToggleEye;
            this.onCopy = onCopy;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PreviewSpec)) return false;
            PreviewSpec that = (PreviewSpec) o;
            return account == that.account
                    && selfId == that.selfId
                    && ownMode == that.ownMode
                    && tempRevealed == that.tempRevealed
                    && Objects.equals(ownSpoof, that.ownSpoof)
                    && Objects.equals(realPhone, that.realPhone)
                    && Objects.equals(userName, that.userName)
                    && Objects.equals(username, that.username);
        }
    }

    /**
     * Interactive live preview card reflecting Settings header and Profile row states in real time.
     */
    public static class PhonePrivacyPreviewCell extends LinearLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        private final TextView headerLabel;
        private final AnimatedTextView badgeView;

        private final LinearLayout settingsCard;
        private final BackupImageView avatarView;
        private final AvatarDrawable avatarDrawable = new AvatarDrawable();
        private final TextView settingsCaptionView;
        private final TextView settingsNameView;
        private final AnimatedTextView settingsSubtitleView;
        private final ImageView settingsEyeBtn;
        private final MirrlyPhoneEyeDrawable settingsEyeDrawable;

        private final FrameLayout profileCard;
        private final LinearLayout profileTextCol;
        private final TextView profileCaptionView;
        private final AnimatedTextView profilePhoneView;
        private final AnimatedTextView profileHintView;
        private final ImageView profileEyeBtn;
        private final MirrlyPhoneEyeDrawable profileEyeDrawable;

        private PreviewSpec boundSpec;
        private int lastMode = -1;

        public PhonePrivacyPreviewCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setOrientation(VERTICAL);
            setPadding(dp(16), dp(12), dp(16), dp(14));

            LinearLayout topRow = new LinearLayout(context);
            topRow.setOrientation(HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            headerLabel = new TextView(context);
            headerLabel.setText(LocaleController.getString(R.string.MirrlyPhonePreviewHeader));
            headerLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            headerLabel.setTypeface(AndroidUtilities.bold());
            headerLabel.setSingleLine(true);
            headerLabel.setEllipsize(TextUtils.TruncateAt.END);
            headerLabel.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);

            badgeView = new AnimatedTextView(context, true, true, true);
            badgeView.setAnimationProperties(0.35f, 0, 220, CubicBezierInterpolator.EASE_OUT_QUINT);
            badgeView.setTextSize(dp(11.5f));
            badgeView.setTypeface(AndroidUtilities.bold());
            badgeView.setGravity(Gravity.CENTER);
            badgeView.setPadding(dp(10), dp(3), dp(10), dp(3));

            if (LocaleController.isRTL) {
                topRow.addView(badgeView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 24, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));
                topRow.addView(headerLabel, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));
            } else {
                topRow.addView(headerLabel, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));
                topRow.addView(badgeView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 24, Gravity.CENTER_VERTICAL));
            }
            addView(topRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

            // Mini Settings header preview
            settingsCard = new LinearLayout(context);
            settingsCard.setOrientation(HORIZONTAL);
            settingsCard.setGravity(Gravity.CENTER_VERTICAL);
            settingsCard.setPadding(dp(12), dp(10), dp(12), dp(10));

            avatarView = new BackupImageView(context);
            avatarView.setRoundRadius(dp(21));

            LinearLayout settingsTexts = new LinearLayout(context);
            settingsTexts.setOrientation(VERTICAL);

            settingsCaptionView = new TextView(context);
            settingsCaptionView.setText(LocaleController.getString(R.string.MirrlyPhonePreviewSettingsLabel));
            settingsCaptionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
            settingsCaptionView.setSingleLine(true);
            settingsCaptionView.setEllipsize(TextUtils.TruncateAt.END);
            settingsCaptionView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            settingsTexts.addView(settingsCaptionView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 1));

            settingsNameView = new TextView(context);
            settingsNameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15.5f);
            settingsNameView.setTypeface(AndroidUtilities.bold());
            settingsNameView.setSingleLine(true);
            settingsNameView.setEllipsize(TextUtils.TruncateAt.END);
            settingsNameView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            settingsTexts.addView(settingsNameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            LinearLayout subRow = new LinearLayout(context);
            subRow.setOrientation(HORIZONTAL);
            subRow.setGravity(Gravity.CENTER_VERTICAL);

            settingsSubtitleView = new AnimatedTextView(context, true, true, true);
            settingsSubtitleView.setAnimationProperties(0.35f, 0, 220, CubicBezierInterpolator.EASE_OUT_QUINT);
            settingsSubtitleView.setTextSize(dp(12.5f));
            settingsSubtitleView.setEllipsizeByGradient(true);
            settingsSubtitleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);

            settingsEyeDrawable = new MirrlyPhoneEyeDrawable(16);
            settingsEyeBtn = new ImageView(context);
            settingsEyeBtn.setScaleType(ImageView.ScaleType.CENTER);
            settingsEyeBtn.setImageDrawable(settingsEyeDrawable);
            ScaleStateListAnimator.apply(settingsEyeBtn, 0.08f, 1.4f);

            if (LocaleController.isRTL) {
                subRow.addView(settingsEyeBtn, LayoutHelper.createLinear(26, 24, Gravity.CENTER_VERTICAL, 0, 0, 4, 0));
                subRow.addView(settingsSubtitleView, LayoutHelper.createLinear(0, 20, 1f, Gravity.CENTER_VERTICAL));
            } else {
                subRow.addView(settingsSubtitleView, LayoutHelper.createLinear(0, 20, 1f, Gravity.CENTER_VERTICAL, 0, 0, 4, 0));
                subRow.addView(settingsEyeBtn, LayoutHelper.createLinear(26, 24, Gravity.CENTER_VERTICAL));
            }
            settingsTexts.addView(subRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));

            if (LocaleController.isRTL) {
                settingsCard.addView(settingsTexts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));
                settingsCard.addView(avatarView, LayoutHelper.createLinear(42, 42, Gravity.CENTER_VERTICAL));
            } else {
                settingsCard.addView(avatarView, LayoutHelper.createLinear(42, 42, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));
                settingsCard.addView(settingsTexts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));
            }
            addView(settingsCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

            // Mini Profile phone row preview
            profileCard = new FrameLayout(context);
            profileCard.setPadding(dp(12), dp(10), dp(10), dp(10));

            profileTextCol = new LinearLayout(context);
            profileTextCol.setOrientation(VERTICAL);

            profileCaptionView = new TextView(context);
            profileCaptionView.setText(LocaleController.getString(R.string.MirrlyPhonePreviewProfileLabel));
            profileCaptionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
            profileCaptionView.setSingleLine(true);
            profileCaptionView.setEllipsize(TextUtils.TruncateAt.END);
            profileCaptionView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            profileTextCol.addView(profileCaptionView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));

            profilePhoneView = new AnimatedTextView(context, true, true, true);
            profilePhoneView.setAnimationProperties(0.35f, 0, 220, CubicBezierInterpolator.EASE_OUT_QUINT);
            profilePhoneView.setTextSize(dp(15.5f));
            profilePhoneView.setEllipsizeByGradient(true);
            profilePhoneView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            profileTextCol.addView(profilePhoneView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 24));

            profileHintView = new AnimatedTextView(context, true, true, true);
            profileHintView.setAnimationProperties(0.35f, 0, 220, CubicBezierInterpolator.EASE_OUT_QUINT);
            profileHintView.setTextSize(dp(12.5f));
            profileHintView.setEllipsizeByGradient(true);
            profileHintView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            profileTextCol.addView(profileHintView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 20, 0, 1, 0, 0));

            profileEyeDrawable = new MirrlyPhoneEyeDrawable(18);
            profileEyeBtn = new ImageView(context);
            profileEyeBtn.setScaleType(ImageView.ScaleType.CENTER);
            profileEyeBtn.setImageDrawable(profileEyeDrawable);
            ScaleStateListAnimator.apply(profileEyeBtn, 0.08f, 1.4f);

            profileCard.addView(profileTextCol, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT,
                    Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT),
                    LocaleController.isRTL ? 44 : 0,
                    0,
                    LocaleController.isRTL ? 0 : 44,
                    0
            ));
            profileCard.addView(profileEyeBtn, LayoutHelper.createFrame(
                    40,
                    40,
                    Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT)
            ));
            addView(profileCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            View.OnClickListener eyeClick = v -> {
                if (boundSpec != null && boundSpec.ownMode == MirrlyPhonePrivacyManager.OWN_MODE_HIDE && boundSpec.onToggleEye != null) {
                    AndroidUtilities.vibrateCursor(v);
                    boundSpec.onToggleEye.run();
                }
            };
            settingsEyeBtn.setOnClickListener(eyeClick);
            profileEyeBtn.setOnClickListener(eyeClick);

            View.OnClickListener copyClick = v -> {
                if (boundSpec == null) {
                    return;
                }
                TLRPC.User current = UserConfig.getInstance(boundSpec.account).getCurrentUser();
                MirrlyPhonePrivacyManager.ResolvedPhone resolved = MirrlyPhonePrivacyManager.resolveSelfPhone(
                        boundSpec.account,
                        current,
                        boundSpec.tempRevealed
                );
                if (resolved.canCopy && boundSpec.onCopy != null) {
                    AndroidUtilities.vibrateCursor(v);
                    boundSpec.onCopy.run(resolved.copyValue);
                } else {
                    AndroidUtilities.shakeViewSpring(v, 4f);
                    BotWebViewVibrationEffect.APP_ERROR.vibrate();
                }
            };
            settingsCard.setOnClickListener(copyClick);
            profileCard.setOnClickListener(copyClick);

            updateColors();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    heightMeasureSpec
            );
        }

        public void shakePreview() {
            AndroidUtilities.shakeViewSpring(settingsCard, 5f);
            AndroidUtilities.shakeViewSpring(profileCard, 5f);
        }

        public void bind(PreviewSpec spec, boolean animated) {
            this.boundSpec = spec;
            TLRPC.User selfUser = UserConfig.getInstance(spec.account).getCurrentUser();
            if (selfUser != null) {
                avatarDrawable.setInfo(spec.account, selfUser);
                avatarView.getImageReceiver().setCurrentAccount(spec.account);
                avatarView.setForUserOrChat(selfUser, avatarDrawable);
            }
            settingsNameView.setText(TextUtils.isEmpty(spec.userName)
                    ? MirrlyPhonePrivacyManager.resolveUserDisplayName(selfUser, spec.selfId)
                    : spec.userName);

            MirrlyPhonePrivacyManager.ResolvedPhone resolved = MirrlyPhonePrivacyManager.resolveSelfPhone(
                    spec.account,
                    selfUser,
                    spec.tempRevealed
            );

            StringBuilder subSb = new StringBuilder();
            subSb.append(resolved.plainDisplayText);
            if (!TextUtils.isEmpty(spec.username)) {
                subSb.append(" • @").append(spec.username);
            }
            settingsSubtitleView.setText(subSb.toString(), animated);
            profilePhoneView.setText(resolved.plainDisplayText, animated);

            String hint = resolved.hiddenLabel
                    ? LocaleController.getString(R.string.MirrlyPhoneTapToChangeHint)
                    : LocaleController.getString(R.string.MirrlyPhoneTapToCopyHint);
            profileHintView.setText(hint, animated);

            boolean showEye = spec.ownMode == MirrlyPhonePrivacyManager.OWN_MODE_HIDE;
            settingsEyeBtn.setVisibility(showEye ? VISIBLE : GONE);
            profileEyeBtn.setVisibility(showEye ? VISIBLE : GONE);
            FrameLayout.LayoutParams colLp = (FrameLayout.LayoutParams) profileTextCol.getLayoutParams();
            if (colLp != null) {
                int endMargin = showEye ? dp(44) : dp(6);
                int targetLeft = LocaleController.isRTL ? endMargin : 0;
                int targetRight = LocaleController.isRTL ? 0 : endMargin;
                if (colLp.leftMargin != targetLeft || colLp.rightMargin != targetRight) {
                    colLp.leftMargin = targetLeft;
                    colLp.rightMargin = targetRight;
                    profileTextCol.setLayoutParams(colLp);
                }
            }
            if (showEye) {
                settingsEyeDrawable.setSlashed(spec.tempRevealed, animated);
                profileEyeDrawable.setSlashed(spec.tempRevealed, animated);
                String desc = LocaleController.getString(spec.tempRevealed ? R.string.MirrlyPhoneHideDesc : R.string.MirrlyPhoneShowDesc);
                settingsEyeBtn.setContentDescription(desc);
                profileEyeBtn.setContentDescription(desc);
            }

            if (spec.ownMode == MirrlyPhonePrivacyManager.OWN_MODE_HIDE) {
                badgeView.setText(LocaleController.getString(R.string.MirrlyPhonePreviewBadgeHidden), animated);
            } else if (spec.ownMode == MirrlyPhonePrivacyManager.OWN_MODE_SPOOF) {
                badgeView.setText(LocaleController.getString(R.string.MirrlyPhonePreviewBadgeSpoofed), animated);
            } else {
                badgeView.setText(LocaleController.getString(R.string.MirrlyPhonePreviewBadgeVisible), animated);
            }
            lastMode = spec.ownMode;
            updateColors();
        }

        @Override
        public void updateColors() {
            if (headerLabel == null || badgeView == null || settingsCard == null || profileCard == null) {
                return;
            }
            int black = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
            int gray = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);
            int blue = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider);
            int green = Theme.getColor(Theme.key_color_green, resourcesProvider);
            int blueHeader = Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider);

            headerLabel.setTextColor(blueHeader);
            if (settingsCaptionView != null) {
                settingsCaptionView.setTextColor(gray);
            }
            settingsNameView.setTextColor(black);
            settingsSubtitleView.setTextColor(gray);
            profileCaptionView.setTextColor(gray);
            profilePhoneView.setTextColor(black);
            profileHintView.setTextColor(gray);

            int eyeColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider);
            settingsEyeDrawable.setColor(blue);
            profileEyeDrawable.setColor(eyeColor);
            settingsEyeBtn.setBackground(Theme.createSimpleSelectorCircleDrawable(dp(26), Color.TRANSPARENT, Theme.getColor(Theme.key_listSelector, resourcesProvider)));
            profileEyeBtn.setBackground(Theme.createSimpleSelectorCircleDrawable(dp(40), Color.TRANSPARENT, Theme.getColor(Theme.key_listSelector, resourcesProvider)));

            int badgeColor = lastMode == MirrlyPhonePrivacyManager.OWN_MODE_HIDE
                    ? blue
                    : (lastMode == MirrlyPhonePrivacyManager.OWN_MODE_SPOOF ? green : gray);
            badgeView.setTextColor(badgeColor);
            badgeView.setBackground(MirrlyUiKit.createTintedSurfaceDrawable(
                    12,
                    ColorUtils.setAlphaComponent(badgeColor, 0x1E),
                    ColorUtils.setAlphaComponent(badgeColor, 0x38),
                    resourcesProvider,
                    false
            ));

            boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
            int innerBg = ColorUtils.setAlphaComponent(black, isDark ? 0x12 : 0x0A);
            int innerRim = MirrlyUiKit.resolveSurfaceHairlineColor(resourcesProvider);
            settingsCard.setBackground(MirrlyUiKit.createTintedSurfaceDrawable(14, innerBg, innerRim, resourcesProvider, true));
            profileCard.setBackground(MirrlyUiKit.createTintedSurfaceDrawable(14, innerBg, innerRim, resourcesProvider, true));
        }

        public static class Factory extends UItem.UItemFactory<PhonePrivacyPreviewCell> {
            static {
                setup(new Factory());
            }

            @Override
            public PhonePrivacyPreviewCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new PhonePrivacyPreviewCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                if (item.object instanceof PreviewSpec) {
                    PhonePrivacyPreviewCell cell = (PhonePrivacyPreviewCell) view;
                    boolean animated = cell.boundSpec != null && cell.boundSpec.account == ((PreviewSpec) item.object).account;
                    cell.bind((PreviewSpec) item.object, animated);
                }
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a != null && b != null && a.id == b.id && a.viewType == b.viewType;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return a != null && b != null && a.id == b.id && Objects.equals(a.object, b.object);
            }

            public static UItem of(int id, PreviewSpec spec) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = spec;
                return item;
            }
        }
    }

    /**
     * Compact row displaying a peer user's avatar, name, and local phone rule badge.
     */
    public static class PeerRuleCell extends LinearLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        private final BackupImageView avatarView;
        private final AvatarDrawable avatarDrawable = new AvatarDrawable();
        private final TextView titleView;
        private final TextView subtitleView;
        private final TextView badgeView;

        public PeerRuleCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(18), dp(9), dp(18), dp(9));

            avatarView = new BackupImageView(context);
            avatarView.setRoundRadius(dp(18));

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(VERTICAL);

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15.5f);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            titleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            texts.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            subtitleView = new TextView(context);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
            subtitleView.setSingleLine(true);
            subtitleView.setEllipsize(TextUtils.TruncateAt.END);
            subtitleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            texts.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

            badgeView = new TextView(context);
            badgeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
            badgeView.setTypeface(AndroidUtilities.bold());
            badgeView.setSingleLine(true);
            badgeView.setEllipsize(TextUtils.TruncateAt.END);
            badgeView.setMaxWidth(dp(128));
            badgeView.setPadding(dp(8), dp(3.5f), dp(8), dp(3.5f));

            if (LocaleController.isRTL) {
                addView(badgeView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));
                addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));
                addView(avatarView, LayoutHelper.createLinear(36, 36, Gravity.CENTER_VERTICAL));
            } else {
                addView(avatarView, LayoutHelper.createLinear(36, 36, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));
                addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));
                addView(badgeView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
            }
            updateColors();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    heightMeasureSpec
            );
        }

        public void bind(int account, MirrlyPhonePrivacyManager.PeerRule rule, String displayName) {
            TLRPC.User user = org.telegram.messenger.MessagesController.getInstance(account).getUser(rule.userId);
            if (user != null) {
                avatarDrawable.setInfo(account, user);
                avatarView.getImageReceiver().setCurrentAccount(account);
                avatarView.setForUserOrChat(user, avatarDrawable);
            } else {
                avatarDrawable.setInfo(rule.userId, displayName, null);
                avatarView.setImageDrawable(avatarDrawable);
            }
            titleView.setText(displayName);
            if (rule.mode == MirrlyPhonePrivacyManager.PEER_MODE_SPOILER) {
                subtitleView.setText(LocaleController.getString(R.string.MirrlyPhonePeerModeSpoiler));
                badgeView.setText(LocaleController.getString(R.string.MirrlyPhonePeerBadgeSpoiler));
            } else {
                subtitleView.setText(MirrlyPhonePrivacyManager.formatDisplayPhone(rule.customPhone));
                badgeView.setText(LocaleController.getString(
                        rule.alsoSpoiler ? R.string.MirrlyPhonePeerBadgeSpoofSpoiler : R.string.MirrlyPhonePreviewBadgeSpoofed
                ));
            }
            updateColors();
        }

        @Override
        public void updateColors() {
            if (titleView == null || subtitleView == null || badgeView == null) {
                return;
            }
            int black = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
            int gray = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);
            int blue = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider);
            titleView.setTextColor(black);
            subtitleView.setTextColor(gray);
            badgeView.setTextColor(blue);
            badgeView.setBackground(Theme.createRoundRectDrawable(dp(10), ColorUtils.setAlphaComponent(blue, 0x1A)));
        }

        public static class Factory extends UItem.UItemFactory<PeerRuleCell> {
            static {
                setup(new Factory());
            }

            @Override
            public PeerRuleCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new PeerRuleCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                if (item.object instanceof MirrlyPhonePrivacyManager.PeerRule) {
                    MirrlyPhonePrivacyManager.PeerRule rule = (MirrlyPhonePrivacyManager.PeerRule) item.object;
                    ((PeerRuleCell) view).bind(item.intValue, rule, item.text != null ? item.text.toString() : "");
                }
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a != null && b != null && a.id == b.id && a.viewType == b.viewType;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                if (a == null || b == null || a.id != b.id || a.intValue != b.intValue || !TextUtils.equals(a.text, b.text)) {
                    return false;
                }
                if (!(a.object instanceof MirrlyPhonePrivacyManager.PeerRule) || !(b.object instanceof MirrlyPhonePrivacyManager.PeerRule)) {
                    return Objects.equals(a.object, b.object);
                }
                MirrlyPhonePrivacyManager.PeerRule ra = (MirrlyPhonePrivacyManager.PeerRule) a.object;
                MirrlyPhonePrivacyManager.PeerRule rb = (MirrlyPhonePrivacyManager.PeerRule) b.object;
                return ra.userId == rb.userId
                        && ra.mode == rb.mode
                        && ra.alsoSpoiler == rb.alsoSpoiler
                        && ra.updatedAt == rb.updatedAt
                        && Objects.equals(ra.customPhone, rb.customPhone);
            }

            public static UItem of(int id, int account, MirrlyPhonePrivacyManager.PeerRule rule, String displayName) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.intValue = account;
                item.text = displayName;
                item.object = rule;
                return item;
            }
        }
    }
}
