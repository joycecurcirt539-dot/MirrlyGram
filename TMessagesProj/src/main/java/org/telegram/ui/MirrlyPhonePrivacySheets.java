package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.mirrly.MirrlyPhonePrivacyManager;
import org.telegram.messenger.utils.ViewOutlineProviderImpl;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.RadioCell;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.Premium.StarParticlesView;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

/**
 * Native frosted bottom sheets for configuring local own-number spoofing
 * and per-user phone spoiler / replacement rules.
 */
public final class MirrlyPhonePrivacySheets {

    private MirrlyPhonePrivacySheets() {
    }

    public static void showOwnSpoofSheet(
            @Nullable BaseFragment fragment,
            @Nullable Theme.ResourcesProvider resourcesProvider,
            int account,
            @Nullable Runnable onUpdated
    ) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        Context context = fragment.getParentActivity();
        TLRPC.User selfUser = UserConfig.getInstance(account).getCurrentUser();
        String initialSpoof = MirrlyPhonePrivacyManager.getOwnSpoofPhone(account);

        int textPrimary = Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider);
        int textSecondary = Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider);
        int blueAccent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(14), dp(20), dp(18));
        scrollView.addView(root, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        FrameLayout headerBox = new FrameLayout(context);
        headerBox.setClipChildren(false);
        headerBox.setClipToPadding(false);
        StarParticlesView particles = MirrlyUiKit.createHeaderParticles(context, resourcesProvider, false);
        headerBox.addView(particles, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 92, Gravity.CENTER));

        RLottieImageView iconView = new RLottieImageView(context);
        iconView.setAutoRepeat(true);
        iconView.setAnimation(R.raw.utyan_private, 72, 72);
        iconView.playAnimation();
        ScaleStateListAnimator.apply(iconView);
        iconView.setOnClickListener(v -> {
            AndroidUtilities.vibrate(iconView);
            iconView.playAnimation();
            particles.flingParticles(110f);
            iconView.animate().cancel();
            iconView.setScaleX(0.88f);
            iconView.setScaleY(0.88f);
            iconView.animate().scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(new OvershootInterpolator(2.2f)).start();
        });
        headerBox.addView(iconView, LayoutHelper.createFrame(72, 72, Gravity.CENTER));
        root.addView(headerBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 92, 0, 0, 0, 2));

        TextView titleView = new TextView(context);
        titleView.setText(LocaleController.getString(R.string.MirrlyPhoneSpoofSheetTitle));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER);
        titleView.setTextColor(textPrimary);
        root.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        TextView subtitleView = new TextView(context);
        subtitleView.setText(LocaleController.getString(R.string.MirrlyPhoneSpoofSheetSub));
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setTextColor(textSecondary);
        root.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        AnimatedTextView livePreview = new AnimatedTextView(context, true, true, true);
        livePreview.setAnimationProperties(0.35f, 0, 220, CubicBezierInterpolator.EASE_OUT_QUINT);
        livePreview.setTextSize(dp(16));
        livePreview.setTypeface(AndroidUtilities.bold());
        livePreview.setGravity(Gravity.CENTER);
        livePreview.setTextColor(blueAccent);
        livePreview.setPadding(dp(14), dp(10), dp(14), dp(10));
        livePreview.setBackground(MirrlyUiKit.createTintedSurfaceDrawable(
                12,
                ColorUtils.setAlphaComponent(blueAccent, 0x16),
                ColorUtils.setAlphaComponent(blueAccent, 0x2E),
                resourcesProvider,
                false
        ));
        root.addView(livePreview, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 42, 0, 0, 0, 10));

        boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
        int surfaceFill = ColorUtils.setAlphaComponent(isDark ? Color.WHITE : Color.BLACK, isDark ? 0x16 : 0x0D);
        int surfaceRim = MirrlyUiKit.resolveSurfaceHairlineColor(resourcesProvider);

        FrameLayout inputBox = new FrameLayout(context);
        inputBox.setBackground(MirrlyUiKit.createTintedSurfaceDrawable(12, surfaceFill, surfaceRim, resourcesProvider, false));
        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(textPrimary);
        editText.setHintTextColor(ColorUtils.blendARGB(textSecondary, textPrimary, 0.25f));
        editText.setCursorColor(blueAccent);
        editText.setCursorSize(dp(20));
        editText.setCursorWidth(1.5f);
        editText.setBackground(null);
        editText.setSingleLine(true);
        editText.setMaxLines(1);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editText.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        editText.setHint(LocaleController.getString(R.string.MirrlyPhoneSpoofInputHint));
        editText.setPadding(dp(14), dp(12), dp(14), dp(12));
        if (!TextUtils.isEmpty(initialSpoof)) {
            editText.setText(initialSpoof);
            editText.setSelection(initialSpoof.length());
        }
        inputBox.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(inputBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        Runnable syncPreview = () -> {
            String raw = editText.getText() != null ? editText.getText().toString().trim() : "";
            String formatted;
            if (TextUtils.isEmpty(raw)) {
                formatted = selfUser != null && !TextUtils.isEmpty(selfUser.phone)
                        ? MirrlyPhonePrivacyManager.formatDisplayPhone(selfUser.phone)
                        : LocaleController.getString(R.string.MirrlyPhoneSpoofOff);
            } else {
                formatted = MirrlyPhonePrivacyManager.formatDisplayPhone(raw);
            }
            livePreview.setText(formatted, true);
        };
        syncPreview.run();
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                syncPreview.run();
            }
        });

        LinearLayout presetsRow = new LinearLayout(context);
        presetsRow.setOrientation(LinearLayout.HORIZONTAL);
        addPresetChip(presetsRow, context, resourcesProvider, LocaleController.getString(R.string.MirrlyPhoneSpoofPresetCollectible), () -> {
            String val = MirrlyPhonePrivacyManager.generatePreset(MirrlyPhonePrivacyManager.PRESET_COLLECTIBLE_888, selfUser);
            editText.setText(val);
            editText.setSelection(val.length());
        });
        addPresetChip(presetsRow, context, resourcesProvider, LocaleController.getString(R.string.MirrlyPhoneSpoofPresetMasked), () -> {
            String val = MirrlyPhonePrivacyManager.generatePreset(MirrlyPhonePrivacyManager.PRESET_MASKED_TAIL, selfUser);
            editText.setText(val);
            editText.setSelection(val.length());
        });
        addPresetChip(presetsRow, context, resourcesProvider, LocaleController.getString(R.string.MirrlyPhoneSpoofPresetRandom), () -> {
            String val = MirrlyPhonePrivacyManager.generatePreset(MirrlyPhonePrivacyManager.PRESET_RANDOM, selfUser);
            editText.setText(val);
            editText.setSelection(val.length());
        });
        root.addView(presetsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        final BottomSheet[] sheetHolder = new BottomSheet[1];

        ButtonWithCounterView saveBtn = MirrlyUiKit.createFlickerButton(context, resourcesProvider, true);
        saveBtn.setText(LocaleController.getString(R.string.MirrlyPhoneSpoofSave), false);
        saveBtn.setOnClickListener(v -> {
            String val = editText.getText() != null ? editText.getText().toString().trim() : "";
            if (TextUtils.isEmpty(val)) {
                AndroidUtilities.vibrateCursor(saveBtn);
                AndroidUtilities.hideKeyboard(editText);
                MirrlyPhonePrivacyManager.clearOwnRule(account);
                if (sheetHolder[0] != null) {
                    sheetHolder[0].dismiss();
                }
                BulletinFactory.of(fragment)
                        .createSimpleBulletin(R.raw.ic_delete, LocaleController.getString(R.string.MirrlyPhoneSpoofResetToast))
                        .show();
                if (onUpdated != null) {
                    onUpdated.run();
                }
                return;
            }
            if (!MirrlyPhonePrivacyManager.setOwnSpoof(account, val)) {
                AndroidUtilities.shakeViewSpring(inputBox, 6f);
                BotWebViewVibrationEffect.APP_ERROR.vibrate();
                if (sheetHolder[0] != null && sheetHolder[0].getContainer() != null) {
                    BulletinFactory.of(sheetHolder[0].getContainer(), resourcesProvider)
                            .createErrorBulletin(LocaleController.getString(R.string.MirrlyPhoneConflictHideActive))
                            .show();
                }
                return;
            }
            AndroidUtilities.vibrateCursor(saveBtn);
            AndroidUtilities.hideKeyboard(editText);
            if (sheetHolder[0] != null) {
                sheetHolder[0].dismiss();
            }
            BulletinFactory.of(fragment)
                    .createSimpleBulletin(R.raw.done, LocaleController.getString(R.string.MirrlyPhoneSpoofSavedToast))
                    .show();
            if (onUpdated != null) {
                onUpdated.run();
            }
        });
        root.addView(saveBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 0, 0, MirrlyPhonePrivacyManager.isOwnSpoofed(account) ? 8 : 0));

        if (MirrlyPhonePrivacyManager.isOwnSpoofed(account)) {
            ButtonWithCounterView resetBtn = new ButtonWithCounterView(context, resourcesProvider).setRound().setNeutral();
            resetBtn.setText(LocaleController.getString(R.string.MirrlyPhoneSpoofReset), false);
            resetBtn.setOnClickListener(v -> {
                AndroidUtilities.vibrateCursor(resetBtn);
                AndroidUtilities.hideKeyboard(editText);
                MirrlyPhonePrivacyManager.clearOwnRule(account);
                if (sheetHolder[0] != null) {
                    sheetHolder[0].dismiss();
                }
                BulletinFactory.of(fragment)
                        .createSimpleBulletin(R.raw.ic_delete, LocaleController.getString(R.string.MirrlyPhoneSpoofResetToast))
                        .show();
                if (onUpdated != null) {
                    onUpdated.run();
                }
            });
            root.addView(resetBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        }

        BottomSheet sheet = MirrlyUiKit.createFrostedBottomSheet(context, true, resourcesProvider, scrollView);
        sheetHolder[0] = sheet;
        sheet.setApplyBottomPadding(false);
        sheet.setOnDismissListener(d -> AndroidUtilities.hideKeyboard(editText));
        fragment.showDialog(sheet);
        editText.postDelayed(() -> {
            if (sheetHolder[0] != null && sheetHolder[0].isShowing() && editText.isAttachedToWindow()) {
                editText.requestFocus();
                AndroidUtilities.showKeyboard(editText);
            }
        }, 120);
    }

    public static void showPeerRuleSheet(
            @Nullable BaseFragment fragment,
            @Nullable Theme.ResourcesProvider resourcesProvider,
            int account,
            @Nullable TLRPC.User user,
            long fallbackUserId,
            @Nullable String fallbackName,
            @Nullable Runnable onUpdated
    ) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        long userId = user != null ? user.id : fallbackUserId;
        if (userId == 0) {
            return;
        }
        Context context = fragment.getParentActivity();
        MirrlyPhonePrivacyManager.PeerRule existing = MirrlyPhonePrivacyManager.getPeerRule(account, userId);
        String displayName = user != null
                ? MirrlyPhonePrivacyManager.resolveUserDisplayName(user, userId)
                : (!TextUtils.isEmpty(fallbackName) ? fallbackName : ("ID " + userId));
        boolean hasServerPhone = user != null && !TextUtils.isEmpty(user.phone);

        int textPrimary = Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider);
        int textSecondary = Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider);
        int blueAccent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(14), dp(20), dp(18));
        scrollView.addView(root, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        FrameLayout headerBox = new FrameLayout(context);
        headerBox.setClipChildren(false);
        headerBox.setClipToPadding(false);
        StarParticlesView particles = MirrlyUiKit.createHeaderParticles(context, resourcesProvider, false);
        headerBox.addView(particles, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 84, Gravity.CENTER));

        BackupImageView avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(dp(30));
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        if (user != null) {
            avatarDrawable.setInfo(account, user);
            avatarView.setForUserOrChat(user, avatarDrawable);
        } else {
            avatarDrawable.setInfo(userId, displayName, null);
            avatarView.setImageDrawable(avatarDrawable);
        }
        ScaleStateListAnimator.apply(avatarView);
        avatarView.setOnClickListener(v -> {
            AndroidUtilities.vibrate(avatarView);
            particles.flingParticles(105f);
        });
        headerBox.addView(avatarView, LayoutHelper.createFrame(60, 60, Gravity.CENTER));
        root.addView(headerBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 84, 0, 0, 0, 4));

        TextView titleView = new TextView(context);
        titleView.setText(LocaleController.formatString(R.string.MirrlyPhonePeerSheetTitle, displayName));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER);
        titleView.setTextColor(textPrimary);
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        root.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        final int[] selectedMode = new int[]{
                existing != null ? existing.mode : (hasServerPhone ? MirrlyPhonePrivacyManager.PEER_MODE_SPOILER : MirrlyPhonePrivacyManager.PEER_MODE_SPOOF)
        };

        boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
        int surfaceFill = ColorUtils.setAlphaComponent(isDark ? Color.WHITE : Color.BLACK, isDark ? 0x16 : 0x0D);
        int surfaceRim = MirrlyUiKit.resolveSurfaceHairlineColor(resourcesProvider);

        LinearLayout radioCard = new LinearLayout(context);
        radioCard.setOrientation(LinearLayout.VERTICAL);
        radioCard.setBackground(MirrlyUiKit.createTintedSurfaceDrawable(14, surfaceFill, surfaceRim, resourcesProvider, false));
        radioCard.setOutlineProvider(ViewOutlineProviderImpl.boundsWithPaddingRoundRect(0, dp(14)));
        radioCard.setClipToOutline(true);

        RadioCell defaultCell = new RadioCell(context, true, 16, resourcesProvider);
        defaultCell.setText(LocaleController.getString(R.string.MirrlyPhonePeerModeDefault), selectedMode[0] == MirrlyPhonePrivacyManager.PEER_MODE_DEFAULT, true);
        defaultCell.setBackground(Theme.getSelectorDrawable(false));
        RadioCell spoilerCell = new RadioCell(context, true, 16, resourcesProvider);
        spoilerCell.setText(LocaleController.getString(R.string.MirrlyPhonePeerModeSpoiler), selectedMode[0] == MirrlyPhonePrivacyManager.PEER_MODE_SPOILER, true);
        spoilerCell.setBackground(Theme.getSelectorDrawable(false));
        RadioCell spoofCell = new RadioCell(context, true, 16, resourcesProvider);
        spoofCell.setText(LocaleController.getString(R.string.MirrlyPhonePeerModeSpoof), selectedMode[0] == MirrlyPhonePrivacyManager.PEER_MODE_SPOOF, false);
        spoofCell.setBackground(Theme.getSelectorDrawable(false));

        radioCard.addView(defaultCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        radioCard.addView(spoilerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        radioCard.addView(spoofCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(radioCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        LinearLayout spoofContainer = new LinearLayout(context);
        spoofContainer.setOrientation(LinearLayout.VERTICAL);

        FrameLayout inputBox = new FrameLayout(context);
        inputBox.setBackground(MirrlyUiKit.createTintedSurfaceDrawable(12, surfaceFill, surfaceRim, resourcesProvider, false));
        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(textPrimary);
        editText.setHintTextColor(ColorUtils.blendARGB(textSecondary, textPrimary, 0.25f));
        editText.setCursorColor(blueAccent);
        editText.setCursorSize(dp(20));
        editText.setCursorWidth(1.5f);
        editText.setBackground(null);
        editText.setSingleLine(true);
        editText.setMaxLines(1);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editText.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        editText.setHint(LocaleController.getString(R.string.MirrlyPhoneSpoofInputHint));
        editText.setPadding(dp(14), dp(12), dp(14), dp(12));
        if (existing != null && !TextUtils.isEmpty(existing.customPhone)) {
            editText.setText(existing.customPhone);
            editText.setSelection(existing.customPhone.length());
        }
        inputBox.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        spoofContainer.addView(inputBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        LinearLayout peerPresets = new LinearLayout(context);
        peerPresets.setOrientation(LinearLayout.HORIZONTAL);
        addPresetChip(peerPresets, context, resourcesProvider, LocaleController.getString(R.string.MirrlyPhoneSpoofPresetCollectible), () -> {
            String val = MirrlyPhonePrivacyManager.generatePreset(MirrlyPhonePrivacyManager.PRESET_COLLECTIBLE_888, null);
            editText.setText(val);
            editText.setSelection(val.length());
        });
        addPresetChip(peerPresets, context, resourcesProvider, LocaleController.getString(R.string.MirrlyPhoneSpoofPresetRandom), () -> {
            String val = MirrlyPhonePrivacyManager.generatePreset(MirrlyPhonePrivacyManager.PRESET_RANDOM, null);
            editText.setText(val);
            editText.setSelection(val.length());
        });
        spoofContainer.addView(peerPresets, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        CheckBoxCell alsoSpoilerCheck = new CheckBoxCell(context, CheckBoxCell.TYPE_CHECK_BOX_DEFAULT, 14, resourcesProvider);
        alsoSpoilerCheck.setText(
                LocaleController.getString(R.string.MirrlyPhonePeerSpoofAlsoSpoiler),
                "",
                existing != null && existing.mode == MirrlyPhonePrivacyManager.PEER_MODE_SPOOF && existing.alsoSpoiler,
                false
        );
        alsoSpoilerCheck.setBackground(MirrlyUiKit.createTintedSurfaceDrawable(12, surfaceFill, surfaceRim, resourcesProvider, true));
        alsoSpoilerCheck.setOutlineProvider(ViewOutlineProviderImpl.boundsWithPaddingRoundRect(0, dp(12)));
        alsoSpoilerCheck.setClipToOutline(true);
        alsoSpoilerCheck.setOnClickListener(v -> {
            AndroidUtilities.vibrateCursor(alsoSpoilerCheck);
            alsoSpoilerCheck.setChecked(!alsoSpoilerCheck.isChecked(), true);
        });
        spoofContainer.addView(alsoSpoilerCheck, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 0, 0, 12));

        root.addView(spoofContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final BottomSheet[] sheetHolder = new BottomSheet[1];

        Runnable syncUi = () -> {
            defaultCell.setChecked(selectedMode[0] == MirrlyPhonePrivacyManager.PEER_MODE_DEFAULT, true);
            spoilerCell.setChecked(selectedMode[0] == MirrlyPhonePrivacyManager.PEER_MODE_SPOILER, true);
            spoofCell.setChecked(selectedMode[0] == MirrlyPhonePrivacyManager.PEER_MODE_SPOOF, true);
            boolean showSpoof = selectedMode[0] == MirrlyPhonePrivacyManager.PEER_MODE_SPOOF;
            spoofContainer.setVisibility(showSpoof ? View.VISIBLE : View.GONE);
            if (!showSpoof) {
                AndroidUtilities.hideKeyboard(editText);
            }
        };
        syncUi.run();

        defaultCell.setOnClickListener(v -> {
            AndroidUtilities.vibrateCursor(v);
            selectedMode[0] = MirrlyPhonePrivacyManager.PEER_MODE_DEFAULT;
            syncUi.run();
        });
        spoilerCell.setOnClickListener(v -> {
            if (!hasServerPhone) {
                AndroidUtilities.shakeViewSpring(spoilerCell, 5f);
                BotWebViewVibrationEffect.APP_ERROR.vibrate();
                if (sheetHolder[0] != null) {
                    BulletinFactory.of(sheetHolder[0].getContainer(), resourcesProvider)
                            .createSimpleBulletin(R.raw.info, LocaleController.getString(R.string.MirrlyPhonePeerMissingError))
                            .show();
                }
                return;
            }
            AndroidUtilities.vibrateCursor(v);
            selectedMode[0] = MirrlyPhonePrivacyManager.PEER_MODE_SPOILER;
            syncUi.run();
        });
        spoofCell.setOnClickListener(v -> {
            AndroidUtilities.vibrateCursor(v);
            selectedMode[0] = MirrlyPhonePrivacyManager.PEER_MODE_SPOOF;
            syncUi.run();
            editText.postDelayed(() -> {
                if (sheetHolder[0] != null && sheetHolder[0].isShowing() && editText.isAttachedToWindow()) {
                    editText.requestFocus();
                    AndroidUtilities.showKeyboard(editText);
                }
            }, 80);
        });

        ButtonWithCounterView saveBtn = MirrlyUiKit.createFlickerButton(context, resourcesProvider, true);
        saveBtn.setText(LocaleController.getString(R.string.Save), false);
        saveBtn.setOnClickListener(v -> {
            int mode = selectedMode[0];
            String customPhone = editText.getText() != null ? editText.getText().toString().trim() : "";
            if (mode == MirrlyPhonePrivacyManager.PEER_MODE_SPOOF && TextUtils.isEmpty(customPhone)) {
                mode = MirrlyPhonePrivacyManager.PEER_MODE_DEFAULT;
            }
            AndroidUtilities.vibrateCursor(saveBtn);
            AndroidUtilities.hideKeyboard(editText);
            if (mode == MirrlyPhonePrivacyManager.PEER_MODE_DEFAULT) {
                MirrlyPhonePrivacyManager.removePeerRule(account, userId);
            } else {
                MirrlyPhonePrivacyManager.setPeerRule(
                        account,
                        userId,
                        mode,
                        customPhone,
                        alsoSpoilerCheck.isChecked(),
                        displayName
                );
            }
            if (sheetHolder[0] != null) {
                sheetHolder[0].dismiss();
            }
            BulletinFactory.of(fragment)
                    .createSimpleBulletin(
                            mode == MirrlyPhonePrivacyManager.PEER_MODE_DEFAULT ? R.raw.ic_delete : R.raw.done,
                            LocaleController.getString(mode == MirrlyPhonePrivacyManager.PEER_MODE_DEFAULT
                                    ? R.string.MirrlyPhonePeerRemovedToast
                                    : R.string.MirrlyPhonePeerSavedToast)
                    )
                    .show();
            if (onUpdated != null) {
                onUpdated.run();
            }
        });
        root.addView(saveBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 0, 0, existing != null ? 8 : 0));

        if (existing != null) {
            ButtonWithCounterView removeBtn = new ButtonWithCounterView(context, resourcesProvider).setRound().setNeutral();
            removeBtn.setText(LocaleController.getString(R.string.MirrlyPhonePeerModeDefault), false);
            removeBtn.setOnClickListener(v -> {
                AndroidUtilities.vibrateCursor(removeBtn);
                AndroidUtilities.hideKeyboard(editText);
                MirrlyPhonePrivacyManager.removePeerRule(account, userId);
                if (sheetHolder[0] != null) {
                    sheetHolder[0].dismiss();
                }
                BulletinFactory.of(fragment)
                        .createSimpleBulletin(R.raw.ic_delete, LocaleController.getString(R.string.MirrlyPhonePeerRemovedToast))
                        .show();
                if (onUpdated != null) {
                    onUpdated.run();
                }
            });
            root.addView(removeBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        }

        BottomSheet sheet = MirrlyUiKit.createFrostedBottomSheet(context, true, resourcesProvider, scrollView);
        sheetHolder[0] = sheet;
        sheet.setApplyBottomPadding(false);
        sheet.setOnDismissListener(d -> AndroidUtilities.hideKeyboard(editText));
        fragment.showDialog(sheet);
    }

    private static void addPresetChip(
            LinearLayout parent,
            Context context,
            @Nullable Theme.ResourcesProvider resourcesProvider,
            String label,
            Runnable onClick
    ) {
        int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider);
        TextView chip = new TextView(context);
        chip.setText(label);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        chip.setTypeface(AndroidUtilities.bold());
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setGravity(Gravity.CENTER);
        chip.setTextColor(accent);
        chip.setPadding(dp(8), dp(7), dp(8), dp(7));
        chip.setBackground(MirrlyUiKit.createTintedSurfaceDrawable(
                10,
                ColorUtils.setAlphaComponent(accent, 0x16),
                ColorUtils.setAlphaComponent(accent, 0x32),
                resourcesProvider,
                true
        ));
        ScaleStateListAnimator.apply(chip, 0.03f, 1.4f);
        chip.setOnClickListener(v -> {
            AndroidUtilities.vibrateCursor(chip);
            onClick.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1f);
        lp.leftMargin = parent.getChildCount() > 0 ? dp(6) : 0;
        lp.rightMargin = 0;
        parent.addView(chip, lp);
    }
}
