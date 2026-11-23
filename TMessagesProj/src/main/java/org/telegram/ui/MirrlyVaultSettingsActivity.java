package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.mirrly.MirrlyConfig;
import org.telegram.messenger.mirrly.MirrlyDeletedChatsStorage;
import org.telegram.messenger.mirrly.MirrlyDeletedStorage;
import org.telegram.messenger.mirrly.MirrlyVaultStorage;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BackgroundGradientDrawable;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ColorPicker;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.utils.glass.GlassContrastHelper;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Vault & Local Storage settings screen managing Deleted & Disappearing Messages,
 * live deleted bubble preview, opacity & trash badge customization, Local Notes, and Edit History.
 */
public class MirrlyVaultSettingsActivity extends MirrlyBaseSettingsFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int ID_HERO = 1;
    private static final int ID_OPEN_NOTES_CHAT = 10;
    private static final int ID_CLEAR_NOTES = 11;
    private static final int ID_SAVE_EDIT_HISTORY = 20;
    private static final int ID_CLEAR_EDIT_HISTORY = 21;

    private static final int ID_DELETED_PREVIEW = 30;
    private static final int ID_SAVE_DELETED = 31;
    private static final int ID_OPEN_DELETED_CHAT = 32;
    private static final int ID_DELETED_ALPHA_SLIDER = 33;
    private static final int ID_DELETED_TRASH_ICON = 34;
    private static final int ID_DELETED_TRASH_COLOR = 35;
    private static final int ID_DELETED_LOCAL_POLICY = 36;
    private static final int ID_CLEAR_DELETED = 37;
    private static final int ID_SAVE_DELETED_CHATS = 40;
    private static final int ID_OPEN_DELETED_CHATS_LIST = 41;
    private static final int ID_CLEAR_DELETED_CHATS = 42;

    private static final int[] TRASH_COLOR_PRESETS = new int[]{
            MirrlyConfig.DEFAULT_DELETED_TRASH_COLOR, // Classic Red #E53935
            0xFFFF5252, // Coral Red
            0xFFFB8C00, // Amber Orange
            0xFF2E7D32, // Emerald Green
            0xFF00897B, // Teal
            0xFF1E88E5, // Azure Blue
            0xFF8E24AA  // Deep Violet
    };

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.mirrlyNotesDidUpdate);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.mirrlyDeletedDidUpdate);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.mirrlyDeletedChatsDidUpdate);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.mirrlyDeletedAppearanceChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.mirrlyNotesDidUpdate);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.mirrlyDeletedDidUpdate);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.mirrlyDeletedChatsDidUpdate);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.mirrlyDeletedAppearanceChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        super.onFragmentDestroy();
    }

    @Override
    protected CharSequence getScreenTitle() {
        return LocaleController.getString(R.string.MirrlySettingsVaultTitle);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        MirrlyConfig.load();
        int notesCount = MirrlyVaultStorage.getNotesCount();
        int editCount = MirrlyVaultStorage.getEditHistoryMessagesCount();
        int deletedCount = MirrlyDeletedStorage.getInstance(currentAccount).getCount();
        int deletedChatsCount = MirrlyDeletedChatsStorage.getInstance(currentAccount).getChatCount();

        CharSequence[] heroSubtitles = new CharSequence[]{
                LocaleController.getString(R.string.MirrlyVaultPillLocal)
        };
        items.add(MirrlyUiKit.HeroCardView.Factory.of(
                ID_HERO,
                new MirrlyUiKit.HeroSpec(
                        MirrlyUiKit.HeroSpec.MODE_LOTTIE,
                        R.raw.utyan_saved_messages,
                        false,
                        LocaleController.getString(R.string.MirrlySettingsVaultTitle),
                        heroSubtitles,
                        heroSubtitles[0]
                )
        ));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyVaultDeletedHeader)));
        items.add(DeletedMessagePreviewCell.Factory.of(ID_DELETED_PREVIEW));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_SAVE_DELETED,
                R.drawable.msg_delete,
                LocaleController.getString(R.string.MirrlyVaultSaveDeleted),
                MirrlyConfig.isSaveDeletedMessages()
        ));
        items.add(UItem.asButton(
                ID_OPEN_DELETED_CHAT,
                R.drawable.msg_viewintopic,
                LocaleController.getString(R.string.MirrlyVaultOpenDeletedChat),
                deletedCount > 0 ? String.valueOf(deletedCount) : LocaleController.getString(R.string.MirrlyVaultCountEmpty)
        ));
        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyVaultDeletedAlphaHeader)));
        items.add(DeletedOpacitySeekBarCell.Factory.of(ID_DELETED_ALPHA_SLIDER, this));
        boolean trashEnabled = MirrlyConfig.isDeletedTrashIconEnabled();
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_DELETED_TRASH_ICON,
                R.drawable.msg_delete,
                LocaleController.getString(R.string.MirrlyVaultDeletedTrashIcon),
                trashEnabled
        ));
        if (trashEnabled) {
            items.add(TrashColorSettingCell.Factory.of(
                    ID_DELETED_TRASH_COLOR,
                    MirrlyConfig.getEffectiveDeletedTrashIconColor(),
                    MirrlyConfig.isDeletedTrashCustomColor()
            ));
        }
        UItem policyItem = UItem.asButton(
                ID_DELETED_LOCAL_POLICY,
                R.drawable.msg_info,
                LocaleController.getString(R.string.MirrlyVaultDeletedLocalPolicy)
        );
        policyItem.subtext = MirrlyConfig.getDeletedLocalDeletePolicy() == MirrlyConfig.DELETED_LOCAL_POLICY_ALWAYS_KEEP
                ? LocaleController.getString(R.string.MirrlyVaultDeletedPolicyAlwaysKeep)
                : LocaleController.getString(R.string.MirrlyVaultDeletedPolicyAsk);
        items.add(policyItem);
        items.add(UItem.asButton(
                ID_CLEAR_DELETED,
                R.drawable.msg_delete,
                LocaleController.getString(R.string.MirrlyVaultClearDeleted),
                ""
        ).red());
        items.add(UItem.asShadow(LocaleController.getString(R.string.MirrlyVaultDeletedHint)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyVaultDeletedChatsHeader)));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_SAVE_DELETED_CHATS,
                R.drawable.msg_message,
                LocaleController.getString(R.string.MirrlyVaultSaveDeletedChats),
                MirrlyConfig.isSaveDeletedChats()
        ));
        items.add(UItem.asButton(
                ID_OPEN_DELETED_CHATS_LIST,
                R.drawable.msg_list,
                LocaleController.getString(R.string.MirrlyVaultOpenDeletedChatsList),
                deletedChatsCount > 0 ? String.valueOf(deletedChatsCount) : LocaleController.getString(R.string.MirrlyVaultCountEmpty)
        ));
        items.add(UItem.asButton(
                ID_CLEAR_DELETED_CHATS,
                R.drawable.msg_delete,
                LocaleController.getString(R.string.MirrlyVaultClearDeletedChats),
                ""
        ).red());
        items.add(UItem.asShadow(LocaleController.getString(R.string.MirrlyVaultDeletedChatsHint)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyVaultNotesHeader)));
        items.add(UItem.asButton(
                ID_OPEN_NOTES_CHAT,
                R.drawable.outline_saved_24,
                LocaleController.getString(R.string.MirrlyVaultOpenNotesChat),
                notesCount > 0 ? String.valueOf(notesCount) : LocaleController.getString(R.string.MirrlyVaultCountEmpty)
        ));
        items.add(UItem.asButton(
                ID_CLEAR_NOTES,
                R.drawable.msg_delete,
                LocaleController.getString(R.string.MirrlyVaultClearNotes),
                ""
        ).red());
        items.add(UItem.asShadow(LocaleController.getString(R.string.MirrlyVaultNotesHint)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyVaultHistoryHeader)));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_SAVE_EDIT_HISTORY,
                R.drawable.msg_edit,
                LocaleController.getString(R.string.MirrlyVaultSaveHistory),
                MirrlyConfig.saveEditHistory
        ));
        items.add(UItem.asButton(
                ID_CLEAR_EDIT_HISTORY,
                R.drawable.msg_delete,
                LocaleController.getString(R.string.MirrlyVaultClearHistory),
                editCount > 0 ? String.valueOf(editCount) : ""
        ).red());
        items.add(UItem.asShadow(LocaleController.getString(R.string.MirrlyVaultHistoryHint)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item == null) {
            return;
        }
        switch (item.id) {
            case ID_SAVE_DELETED: {
                boolean next = !MirrlyConfig.isSaveDeletedMessages();
                MirrlyConfig.setSaveDeletedMessages(next);
                if (view instanceof TextCell) {
                    ((TextCell) view).setChecked(next);
                }
                if (listView != null && listView.adapter != null) {
                    listView.adapter.updateWithoutNotify();
                }
                break;
            }

            case ID_OPEN_DELETED_CHAT:
                presentFragment(new MirrlyDeletedChatActivity());
                break;

            case ID_DELETED_TRASH_ICON: {
                boolean next = !MirrlyConfig.isDeletedTrashIconEnabled();
                MirrlyConfig.setDeletedTrashIconEnabled(next);
                updateList(true);
                refreshPreviewInList(false);
                break;
            }

            case ID_DELETED_TRASH_COLOR:
                showTrashColorPickerSheet();
                break;

            case ID_DELETED_LOCAL_POLICY: {
                int current = MirrlyConfig.getDeletedLocalDeletePolicy();
                int next = current == MirrlyConfig.DELETED_LOCAL_POLICY_ALWAYS_KEEP
                        ? MirrlyConfig.DELETED_LOCAL_POLICY_ASK
                        : MirrlyConfig.DELETED_LOCAL_POLICY_ALWAYS_KEEP;
                MirrlyConfig.setDeletedLocalDeletePolicy(next);
                updateList(true);
                BulletinFactory.of(this).createSimpleBulletin(
                        R.raw.info,
                        LocaleController.getString(next == MirrlyConfig.DELETED_LOCAL_POLICY_ALWAYS_KEEP
                                ? R.string.MirrlyVaultDeletedPolicyAlwaysKeep
                                : R.string.MirrlyVaultDeletedPolicyAsk)
                ).show();
                break;
            }

            case ID_CLEAR_DELETED: {
                int deleted = MirrlyDeletedStorage.getInstance(currentAccount).getCount();
                if (deleted == 0) {
                    BulletinFactory.of(this).createSimpleBulletin(
                            R.raw.info,
                            LocaleController.getString(R.string.MirrlyVaultClearDeletedEmpty)
                    ).show();
                    break;
                }
                MirrlyUiKit.confirmDangerAction(
                        this,
                        getResourceProvider(),
                        LocaleController.getString(R.string.MirrlyVaultClearDeletedConfirmTitle),
                        String.format(Locale.US, LocaleController.getString(R.string.MirrlyVaultClearDeletedConfirmText), deleted),
                        LocaleController.getString(R.string.Delete),
                        () -> {
                            MirrlyDeletedStorage.getInstance(currentAccount).clearAll();
                            updateList(true);
                            BulletinFactory.of(this).createSimpleBulletin(
                                    R.raw.ic_delete,
                                    LocaleController.getString(R.string.MirrlyVaultClearDeletedDone)
                            ).show();
                        }
                );
                break;
            }

            case ID_SAVE_DELETED_CHATS: {
                boolean next = !MirrlyConfig.isSaveDeletedChats();
                MirrlyConfig.setSaveDeletedChats(next);
                if (view instanceof TextCell) {
                    ((TextCell) view).setChecked(next);
                }
                if (listView != null && listView.adapter != null) {
                    listView.adapter.updateWithoutNotify();
                }
                break;
            }

            case ID_OPEN_DELETED_CHATS_LIST:
                presentFragment(new MirrlyDeletedChatsListActivity());
                break;

            case ID_CLEAR_DELETED_CHATS: {
                int deletedChats = MirrlyDeletedChatsStorage.getInstance(currentAccount).getChatCount();
                if (deletedChats == 0) {
                    BulletinFactory.of(this).createSimpleBulletin(
                            R.raw.info,
                            LocaleController.getString(R.string.MirrlyVaultClearDeletedChatsEmpty)
                    ).show();
                    break;
                }
                MirrlyUiKit.confirmDangerAction(
                        this,
                        getResourceProvider(),
                        LocaleController.getString(R.string.MirrlyVaultClearDeletedChatsConfirmTitle),
                        LocaleController.formatString(R.string.MirrlyVaultClearDeletedChatsConfirmText, deletedChats),
                        LocaleController.getString(R.string.Delete),
                        () -> {
                            MirrlyDeletedChatsStorage.getInstance(currentAccount).clearAll();
                            updateList(true);
                            BulletinFactory.of(this).createSimpleBulletin(
                                    R.raw.ic_delete,
                                    LocaleController.getString(R.string.MirrlyVaultClearDeletedChatsDone)
                            ).show();
                        }
                );
                break;
            }

            case ID_OPEN_NOTES_CHAT:
                presentFragment(new MirrlyNotesChatActivity());
                break;

            case ID_CLEAR_NOTES:
                if (MirrlyVaultStorage.getNotesCount() == 0) {
                    BulletinFactory.of(this).createSimpleBulletin(
                            R.raw.info,
                            LocaleController.getString(R.string.MirrlyVaultClearNotesEmpty)
                    ).show();
                    break;
                }
                MirrlyUiKit.confirmDangerAction(
                        this,
                        getResourceProvider(),
                        LocaleController.getString(R.string.MirrlyVaultClearNotesConfirmTitle),
                        LocaleController.getString(R.string.MirrlyVaultClearNotesConfirmText),
                        LocaleController.getString(R.string.Delete),
                        () -> {
                            MirrlyVaultStorage.clearDossierNotes();
                            updateList(true);
                            BulletinFactory.of(this).createSimpleBulletin(
                                    R.raw.ic_delete,
                                    LocaleController.getString(R.string.MirrlyVaultClearNotesDone)
                            ).show();
                        }
                );
                break;

            case ID_SAVE_EDIT_HISTORY:
                boolean nextSave = !MirrlyConfig.saveEditHistory;
                MirrlyConfig.setSaveEditHistory(nextSave);
                if (view instanceof TextCell) {
                    ((TextCell) view).setChecked(nextSave);
                }
                if (listView != null && listView.adapter != null) {
                    listView.adapter.updateWithoutNotify();
                }
                break;

            case ID_CLEAR_EDIT_HISTORY:
                int edits = MirrlyVaultStorage.getEditHistoryMessagesCount();
                if (edits == 0) {
                    BulletinFactory.of(this).createSimpleBulletin(
                            R.raw.info,
                            LocaleController.getString(R.string.MirrlyVaultClearHistoryEmpty)
                    ).show();
                    break;
                }
                MirrlyUiKit.confirmDangerAction(
                        this,
                        getResourceProvider(),
                        LocaleController.getString(R.string.MirrlyVaultClearHistoryConfirmTitle),
                        String.format(Locale.US, LocaleController.getString(R.string.MirrlyVaultClearHistoryConfirmText), edits),
                        LocaleController.getString(R.string.Delete),
                        () -> {
                            MirrlyVaultStorage.clearEditHistory();
                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mirrlyNotesDidUpdate);
                            updateList(true);
                            BulletinFactory.of(this).createSimpleBulletin(
                                    R.raw.ic_delete,
                                    LocaleController.getString(R.string.MirrlyVaultClearHistoryDone)
                            ).show();
                        }
                );
                break;
        }
    }

    private void onOpacitySliderChanged(int percent, boolean stop) {
        MirrlyConfig.setDeletedMessageAlphaPercent(percent, stop);
        refreshPreviewInList(false);
    }

    private void refreshPreviewInList(boolean refreshWallpaper) {
        if (listView == null) {
            return;
        }
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof DeletedMessagePreviewCell) {
                ((DeletedMessagePreviewCell) child).refreshPreview(refreshWallpaper);
            }
        }
    }

    private void showTrashColorPickerSheet() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        Theme.ResourcesProvider rp = getResourceProvider();
        final int initialColor = MirrlyConfig.getEffectiveDeletedTrashIconColor();
        final int[] workingColor = new int[]{initialColor};
        final boolean[] workingCustom = new boolean[]{MirrlyConfig.isDeletedTrashCustomColor()};

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(8), dp(16), dp(12));

        TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        titleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        titleView.setText(LocaleController.getString(R.string.MirrlyVaultDeletedTrashColor));
        container.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 12));

        LinearLayout presetsRow = new LinearLayout(context);
        presetsRow.setOrientation(LinearLayout.HORIZONTAL);
        presetsRow.setGravity(Gravity.CENTER);
        final ArrayList<PresetSwatchView> swatchViews = new ArrayList<>();
        final ColorPicker[] pickerRef = new ColorPicker[1];

        for (int presetColor : TRASH_COLOR_PRESETS) {
            PresetSwatchView swatch = new PresetSwatchView(context, presetColor);
            swatch.setChecked(presetColor == initialColor);
            swatch.setOnClickListener(v -> {
                AndroidUtilities.vibrateCursor(v);
                workingColor[0] = presetColor;
                workingCustom[0] = (presetColor != MirrlyConfig.DEFAULT_DELETED_TRASH_COLOR);
                for (PresetSwatchView sw : swatchViews) {
                    sw.setChecked(sw.swatchColor == presetColor);
                }
                if (pickerRef[0] != null) {
                    pickerRef[0].setColor(presetColor, 0);
                }
                MirrlyConfig.setDeletedTrashIconColor(workingCustom[0] ? presetColor : 0);
                refreshPreviewInList(false);
            });
            swatchViews.add(swatch);
            presetsRow.addView(swatch, LayoutHelper.createLinear(36, 36, 4, 0, 4, 0));
        }
        container.addView(presetsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        ColorPicker colorPicker = new ColorPicker(context, false, new ColorPicker.ColorPickerDelegate() {
            @Override
            public void setColor(int color, int num, boolean applyNow) {
                workingColor[0] = ColorUtils.setAlphaComponent(color, 0xFF);
                workingCustom[0] = (workingColor[0] != MirrlyConfig.DEFAULT_DELETED_TRASH_COLOR);
                for (PresetSwatchView sw : swatchViews) {
                    sw.setChecked(sw.swatchColor == workingColor[0]);
                }
                MirrlyConfig.setDeletedTrashIconColor(workingCustom[0] ? workingColor[0] : 0);
                refreshPreviewInList(false);
            }

            @Override
            public int getDefaultColor(int num) {
                return MirrlyConfig.DEFAULT_DELETED_TRASH_COLOR;
            }
        });
        colorPicker.setType(-1, false, 1, 1, false, 0, false);
        colorPicker.setColor(initialColor, 0);
        pickerRef[0] = colorPicker;
        container.addView(colorPicker, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 310));

        LinearLayout buttonsRow = new LinearLayout(context);
        buttonsRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonsRow.setGravity(Gravity.CENTER_VERTICAL);
        buttonsRow.setPadding(0, dp(8), 0, dp(4));

        final BottomSheet sheet = MirrlyUiKit.createFrostedBottomSheet(context, true, rp, container);

        TextView resetBtn = new TextView(context);
        resetBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        resetBtn.setTypeface(AndroidUtilities.bold());
        resetBtn.setGravity(Gravity.CENTER);
        resetBtn.setPadding(dp(14), dp(10), dp(14), dp(10));
        resetBtn.setTextColor(Theme.getColor(Theme.key_text_RedRegular, rp));
        resetBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                dp(12),
                Theme.multAlpha(Theme.getColor(Theme.key_text_RedRegular, rp), 0.10f),
                Theme.multAlpha(Theme.getColor(Theme.key_text_RedRegular, rp), 0.22f)
        ));
        resetBtn.setText(LocaleController.getString(R.string.MirrlyVaultDeletedColorReset));
        resetBtn.setOnClickListener(v -> {
            MirrlyConfig.setDeletedTrashIconColor(0);
            refreshPreviewInList(false);
            updateList(true);
            sheet.dismiss();
        });
        buttonsRow.addView(resetBtn, LayoutHelper.createLinear(0, 44, 1f, 0, 0, 8, 0));

        TextView doneBtn = new TextView(context);
        doneBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        doneBtn.setTypeface(AndroidUtilities.bold());
        doneBtn.setGravity(Gravity.CENTER);
        doneBtn.setPadding(dp(14), dp(10), dp(14), dp(10));
        doneBtn.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, rp));
        doneBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                dp(12),
                Theme.getColor(Theme.key_featuredStickers_addButton, rp),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed, rp)
        ));
        doneBtn.setText(LocaleController.getString(R.string.Done));
        doneBtn.setOnClickListener(v -> {
            updateList(true);
            sheet.dismiss();
        });
        buttonsRow.addView(doneBtn, LayoutHelper.createLinear(0, 44, 1f));

        container.addView(buttonsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        showDialog(sheet);
    }

    private void updateList(boolean animated) {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(animated);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.mirrlyNotesDidUpdate
                || id == NotificationCenter.mirrlyDeletedDidUpdate
                || id == NotificationCenter.mirrlyDeletedChatsDidUpdate) {
            updateList(true);
        } else if (id == NotificationCenter.mirrlyDeletedAppearanceChanged) {
            refreshPreviewInList(false);
            if (listView != null && listView.adapter != null) {
                listView.adapter.updateWithoutNotify();
            }
        } else if (id == NotificationCenter.didSetNewWallpapper) {
            refreshPreviewInList(true);
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

    private static class PresetSwatchView extends View {
        final int swatchColor;
        private boolean checked;
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        PresetSwatchView(Context context, int color) {
            super(context);
            this.swatchColor = color;
            fillPaint.setColor(color);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(dp(2.5f));
            ringPaint.setColor(color);
        }

        void setChecked(boolean checked) {
            this.checked = checked;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() * 0.5f;
            float cy = getHeight() * 0.5f;
            float r = dp(13);
            if (checked) {
                canvas.drawCircle(cx, cy, r + dp(2), ringPaint);
                canvas.drawCircle(cx, cy, r - dp(2.5f), fillPaint);
            } else {
                canvas.drawCircle(cx, cy, r, fillPaint);
            }
        }
    }

    /**
     * Native SeekBarView slider controlling deleted message opacity (15%..100%).
     */
    private static class DeletedOpacitySeekBarCell extends FrameLayout implements Theme.Colorable {

        private static final int MIN_ALPHA = MirrlyConfig.MIN_DELETED_ALPHA_PERCENT;
        private static final int MAX_ALPHA = MirrlyConfig.MAX_DELETED_ALPHA_PERCENT;

        private final Theme.ResourcesProvider resourcesProvider;
        private final SeekBarView sizeBar;
        private final AnimatedTextView valueText;
        private int lastReportedAlpha = -1;
        private WeakReference<MirrlyVaultSettingsActivity> hostRef;

        public DeletedOpacitySeekBarCell(@NonNull Context context, @Nullable Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setWillNotDraw(false);

            valueText = new AnimatedTextView(context, false, true, true);
            valueText.setAnimationProperties(.3f, 0, 220, CubicBezierInterpolator.EASE_OUT_QUINT);
            valueText.setTextSize(dp(15));
            valueText.setTypeface(AndroidUtilities.bold());
            valueText.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
            valueText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText, resourcesProvider));
            valueText.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(valueText, LayoutHelper.createFrame(58, 24, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 18, 0, 18, 0));

            sizeBar = new SeekBarView(context, resourcesProvider) {
                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN && getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return super.onTouchEvent(event);
                }
            };
            sizeBar.setReportChanges(true);
            sizeBar.setSeparatorsCount(18);
            sizeBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override
                public void onSeekBarDrag(boolean stop, float progress) {
                    int val = Math.round(MIN_ALPHA + (MAX_ALPHA - MIN_ALPHA) * Utilities.clamp01(progress));
                    if (val != lastReportedAlpha) {
                        AndroidUtilities.vibrateCursor(sizeBar);
                        lastReportedAlpha = val;
                        valueText.setText(val + "%", true);
                    }
                    MirrlyVaultSettingsActivity host = hostRef != null ? hostRef.get() : null;
                    if (host != null) {
                        host.onOpacitySliderChanged(val, stop);
                    }
                }

                @Override
                public CharSequence getContentDescription() {
                    return MirrlyConfig.getDeletedMessageAlphaPercent() + "%";
                }

                @Override
                public int getStepsCount() {
                    return MAX_ALPHA - MIN_ALPHA;
                }
            });
            addView(sizeBar, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, 38,
                    Gravity.CENTER_VERTICAL,
                    LocaleController.isRTL ? 76 : 8, 0,
                    LocaleController.isRTL ? 8 : 76, 0
            ));
            syncFromConfig(false);
        }

        public void setHost(MirrlyVaultSettingsActivity host) {
            this.hostRef = new WeakReference<>(host);
        }

        public void syncFromConfig(boolean animated) {
            if (sizeBar == null || sizeBar.isDragging()) {
                return;
            }
            int current = MirrlyConfig.getDeletedMessageAlphaPercent();
            lastReportedAlpha = current;
            float progress = (current - MIN_ALPHA) / (float) (MAX_ALPHA - MIN_ALPHA);
            sizeBar.setProgress(progress, animated);
            valueText.setText(current + "%", animated);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(dp(54), MeasureSpec.EXACTLY)
            );
        }

        @Override
        public void updateColors() {
            valueText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText, resourcesProvider));
            invalidate();
        }

        public static final class Factory extends UItem.UItemFactory<DeletedOpacitySeekBarCell> {
            static {
                setup(new Factory());
            }

            public static UItem of(int id, MirrlyVaultSettingsActivity host) {
                UItem item = UItem.ofFactory(Factory.class).setId(id);
                item.object = host;
                return item;
            }

            @Override
            public DeletedOpacitySeekBarCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new DeletedOpacitySeekBarCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                if (view instanceof DeletedOpacitySeekBarCell) {
                    DeletedOpacitySeekBarCell cell = (DeletedOpacitySeekBarCell) view;
                    if (item.object instanceof MirrlyVaultSettingsActivity) {
                        cell.setHost((MirrlyVaultSettingsActivity) item.object);
                    }
                    cell.syncFromConfig(false);
                }
            }

            @Override
            public boolean isClickable() {
                return false;
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a.id == b.id && a.viewType == b.viewType;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return true;
            }
        }
    }

    /**
     * Native row displaying Trash Icon Color title, hex value and live circular swatch.
     */
    private static class TrashColorSettingCell extends FrameLayout implements Theme.Colorable {
        private final Theme.ResourcesProvider resourcesProvider;
        private final android.widget.ImageView iconView;
        private final TextView titleView;
        private final View swatchCircle;
        private int swatchColor;
        private boolean needDivider;

        private final Paint swatchFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint swatchStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public TrashColorSettingCell(@NonNull Context context, @Nullable Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setWillNotDraw(false);

            iconView = new android.widget.ImageView(context);
            iconView.setScaleType(android.widget.ImageView.ScaleType.CENTER);
            iconView.setImageResource(R.drawable.msg_palette);
            iconView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider), PorterDuff.Mode.MULTIPLY));
            addView(iconView, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 20, 0, 20, 0));

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            titleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            titleView.setText(LocaleController.getString(R.string.MirrlyVaultDeletedTrashColor));
            addView(titleView, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL,
                    LocaleController.isRTL ? 64 : 68, 0,
                    LocaleController.isRTL ? 68 : 64, 0
            ));

            swatchStrokePaint.setStyle(Paint.Style.STROKE);
            swatchStrokePaint.setStrokeWidth(dp(1.25f));
            swatchStrokePaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), 0.18f));

            swatchCircle = new View(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    float cx = getWidth() * 0.5f;
                    float cy = getHeight() * 0.5f;
                    float r = dp(10.5f);
                    swatchFillPaint.setColor(swatchColor);
                    canvas.drawCircle(cx, cy, r, swatchFillPaint);
                    canvas.drawCircle(cx, cy, r, swatchStrokePaint);
                }
            };
            addView(swatchCircle, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 20, 0, 20, 0));
        }

        void bind(int color, boolean divider) {
            this.swatchColor = color;
            this.needDivider = divider;
            swatchCircle.invalidate();
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(dp(50), MeasureSpec.EXACTLY)
            );
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (needDivider) {
                canvas.drawLine(
                        LocaleController.isRTL ? 0 : dp(68),
                        getMeasuredHeight() - 1,
                        getMeasuredWidth() - (LocaleController.isRTL ? dp(68) : 0),
                        getMeasuredHeight() - 1,
                        Theme.dividerPaint
                );
            }
        }

        @Override
        public void updateColors() {
            iconView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider), PorterDuff.Mode.MULTIPLY));
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            swatchStrokePaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), 0.18f));
            swatchCircle.invalidate();
            invalidate();
        }

        public static final class Factory extends UItem.UItemFactory<TrashColorSettingCell> {
            static {
                setup(new Factory());
            }

            public static UItem of(int id, int color, boolean isCustom) {
                UItem item = UItem.ofFactory(Factory.class).setId(id);
                item.intValue = color;
                item.checked = isCustom;
                return item;
            }

            @Override
            public TrashColorSettingCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new TrashColorSettingCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                if (view instanceof TrashColorSettingCell) {
                    ((TrashColorSettingCell) view).bind(item.intValue, divider);
                }
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a.id == b.id && a.viewType == b.viewType;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return a.intValue == b.intValue && a.checked == b.checked;
            }
        }
    }

    /**
     * Live chat wallpaper preview showing an ordinary message above a saved deleted
     * message with real-time opacity and customizable trash badge rendering.
     */
    private static class DeletedMessagePreviewCell extends FrameLayout implements Theme.Colorable {
        private static final long PREVIEW_USER_ID = 1000002L;

        private final int currentAccount;
        @Nullable
        private final Theme.ResourcesProvider resourcesProvider;
        private final FrameLayout previewContainer;
        private final LinearLayout messagesContainer;
        private final ChatMessageCell normalCell;
        private final ChatMessageCell deletedCell;
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF borderRect = new RectF();
        private final Rect savedDrawableBounds = new Rect();

        @Nullable
        private Drawable wallpaperDrawable;
        @Nullable
        private BackgroundGradientDrawable.Disposable bgDisposable;
        private boolean wallpaperLoadRequested;
        private int backdropColor;
        private float backdropLuminance;

        private MessageObject normalMsg;
        private MessageObject deletedMsg;

        public DeletedMessagePreviewCell(@NonNull Context context, int currentAccount, @Nullable Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.currentAccount = currentAccount;
            this.resourcesProvider = resourcesProvider;
            setPadding(dp(10), dp(10), dp(10), dp(10));
            setClipToPadding(false);

            previewContainer = new FrameLayout(context) {
                @Override
                protected void dispatchDraw(Canvas canvas) {
                    drawChatWallpaper(canvas, getWidth(), getHeight());
                    super.dispatchDraw(canvas);
                    final float radius = dp(14);
                    borderRect.set(0.5f, 0.5f, getWidth() - 0.5f, getHeight() - 0.5f);
                    canvas.drawRoundRect(borderRect, radius, radius, borderPaint);
                }

                @Override
                protected void onDetachedFromWindow() {
                    disposeWallpaper();
                    super.onDetachedFromWindow();
                }
            };
            previewContainer.setClipToOutline(true);
            previewContainer.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(14));
                }
            });

            messagesContainer = new LinearLayout(context);
            messagesContainer.setOrientation(LinearLayout.VERTICAL);
            messagesContainer.setPadding(dp(4), dp(10), dp(4), dp(10));

            normalCell = new ChatMessageCell(context, currentAccount, false, null, resourcesProvider);
            deletedCell = new ChatMessageCell(context, currentAccount, false, null, resourcesProvider);

            messagesContainer.addView(normalCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));
            messagesContainer.addView(deletedCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            previewContainer.addView(messagesContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            addView(previewContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dp(1));

            refreshPreview(true);
        }

        private void ensureWallpaperAndBackdrop() {
            Drawable cached = Theme.getCachedWallpaperNonBlocking();
            if (cached == null && !wallpaperLoadRequested) {
                wallpaperLoadRequested = true;
                Theme.loadWallpaper(true);
                cached = Theme.getCachedWallpaperNonBlocking();
            }
            if (cached != wallpaperDrawable) {
                disposeWallpaper();
                wallpaperDrawable = cached;
            }
            backdropColor = computeBackdropColor(wallpaperDrawable);
            backdropLuminance = GlassContrastHelper.computeLuminance(backdropColor);
            borderPaint.setColor(backdropLuminance < 0.50f ? 0x22FFFFFF : 0x18000000);
        }

        private int computeBackdropColor(@Nullable Drawable drawable) {
            if (SharedConfig.compactMode) {
                return Theme.getColor(Theme.key_chat_compact_background, resourcesProvider);
            }
            if (drawable instanceof ColorDrawable) {
                return ColorUtils.setAlphaComponent(((ColorDrawable) drawable).getColor(), 0xFF);
            }
            if (drawable instanceof BackgroundGradientDrawable) {
                int[] colors = ((BackgroundGradientDrawable) drawable).getColorsList();
                if (colors != null && colors.length > 0) {
                    return ColorUtils.setAlphaComponent(
                            colors.length > 1 ? AndroidUtilities.getAverageColor(colors[0], colors[1]) : colors[0],
                            0xFF
                    );
                }
            }
            if (drawable instanceof MotionBackgroundDrawable) {
                int[] colors = ((MotionBackgroundDrawable) drawable).getColors();
                if (colors != null && colors.length > 0) {
                    int blended = colors[0];
                    if (colors.length > 1) {
                        blended = AndroidUtilities.getAverageColor(blended, colors[1]);
                    }
                    return ColorUtils.setAlphaComponent(blended, 0xFF);
                }
            }
            if (drawable instanceof BitmapDrawable) {
                Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                if (bitmap != null && !bitmap.isRecycled()) {
                    return ColorUtils.setAlphaComponent(AndroidUtilities.calcBitmapColor(bitmap), 0xFF);
                }
            }
            int wallpaperFallback = Theme.getColor(Theme.key_chat_wallpaper, resourcesProvider);
            if ((wallpaperFallback >>> 24) == 0) {
                wallpaperFallback = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
            }
            return ColorUtils.setAlphaComponent(wallpaperFallback, 0xFF);
        }

        private void drawChatWallpaper(Canvas canvas, int width, int height) {
            if (width <= 0 || height <= 0) {
                return;
            }
            if (SharedConfig.compactMode) {
                canvas.drawColor(Theme.getColor(Theme.key_chat_compact_background, resourcesProvider));
                return;
            }
            if (wallpaperDrawable == null) {
                wallpaperDrawable = Theme.getCachedWallpaperNonBlocking();
            }
            if (wallpaperDrawable == null) {
                canvas.drawColor(backdropColor);
                return;
            }
            wallpaperDrawable.copyBounds(savedDrawableBounds);
            if (wallpaperDrawable instanceof BackgroundGradientDrawable) {
                wallpaperDrawable.setBounds(0, 0, width, height);
                bgDisposable = ((BackgroundGradientDrawable) wallpaperDrawable).drawExactBoundsSize(canvas, previewContainer, 0.5f);
            } else if (wallpaperDrawable instanceof BitmapDrawable) {
                int iw = wallpaperDrawable.getIntrinsicWidth();
                int ih = wallpaperDrawable.getIntrinsicHeight();
                if (iw > 0 && ih > 0) {
                    float scale = Math.max((float) width / iw, (float) height / ih);
                    int sw = Math.round(iw * scale);
                    int sh = Math.round(ih * scale);
                    int dx = (width - sw) / 2;
                    int dy = (height - sh) / 2;
                    wallpaperDrawable.setBounds(dx, dy, dx + sw, dy + sh);
                } else {
                    wallpaperDrawable.setBounds(0, 0, width, height);
                }
                wallpaperDrawable.draw(canvas);
            } else {
                wallpaperDrawable.setBounds(0, 0, width, height);
                wallpaperDrawable.draw(canvas);
            }
            wallpaperDrawable.setBounds(savedDrawableBounds);
        }

        private void disposeWallpaper() {
            if (bgDisposable != null) {
                bgDisposable.dispose();
                bgDisposable = null;
            }
        }

        private void ensurePreviewMessages() {
            long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
            if (selfId <= 0) {
                selfId = 1L;
            }
            final int now = (int) (System.currentTimeMillis() / 1000L);
            if (normalMsg == null) {
                TLRPC.TL_message m1 = createIncomingMessage(2001, PREVIEW_USER_ID, selfId, now - 45, LocaleController.getString(R.string.MirrlyVaultDeletedPreviewNormal), false);
                normalMsg = new MessageObject(currentAccount, m1, false, false);
                normalMsg.sendPreview = true;
            }
            if (deletedMsg == null) {
                TLRPC.TL_message m2 = createIncomingMessage(2002, PREVIEW_USER_ID, selfId, now - 15, LocaleController.getString(R.string.MirrlyVaultDeletedPreviewDeleted), true);
                deletedMsg = new MessageObject(currentAccount, m2, false, false);
                deletedMsg.sendPreview = true;
            }
        }

        private TLRPC.TL_message createIncomingMessage(int id, long peerUserId, long selfId, int date, String text, boolean isDeleted) {
            TLRPC.TL_message msg = new TLRPC.TL_message();
            msg.id = id;
            msg.flags = 256;
            msg.out = false;
            msg.unread = false;
            msg.dialog_id = peerUserId;
            msg.peer_id = new TLRPC.TL_peerUser();
            msg.peer_id.user_id = selfId;
            msg.from_id = new TLRPC.TL_peerUser();
            msg.from_id.user_id = peerUserId;
            msg.date = date;
            msg.message = text;
            msg.media = new TLRPC.TL_messageMediaEmpty();
            msg.mirrlyDeleted = isDeleted;
            msg.mirrlyDeletedAt = isDeleted ? date + 5 : 0;
            return msg;
        }

        public void refreshPreview(boolean refreshWallpaper) {
            if (refreshWallpaper || wallpaperDrawable == null) {
                ensureWallpaperAndBackdrop();
            }
            ensurePreviewMessages();

            normalCell.forceResetMessageObject();
            normalCell.setMessageObject(normalMsg, null, false, false, false);
            normalCell.updateMirrlyDeletedState(false);

            deletedCell.forceResetMessageObject();
            deletedCell.setMessageObject(deletedMsg, null, false, false, false);
            deletedCell.updateMirrlyDeletedState(false);

            previewContainer.invalidate();
            requestLayout();
        }

        @Override
        public void updateColors() {
            refreshPreview(true);
        }

        public static final class Factory extends UItem.UItemFactory<DeletedMessagePreviewCell> {
            static {
                setup(new Factory());
            }

            public static UItem of(int id) {
                return UItem.ofFactory(Factory.class).setId(id);
            }

            @Override
            public DeletedMessagePreviewCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new DeletedMessagePreviewCell(context, currentAccount, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                if (view instanceof DeletedMessagePreviewCell) {
                    ((DeletedMessagePreviewCell) view).refreshPreview(true);
                }
            }

            @Override
            public boolean isClickable() {
                return false;
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a.id == b.id && a.viewType == b.viewType;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return true;
            }
        }
    }
}
