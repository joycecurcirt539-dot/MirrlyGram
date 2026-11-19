package org.telegram.ui;

import android.view.View;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.mirrly.MirrlyConfig;
import org.telegram.messenger.mirrly.MirrlyVaultStorage;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Storage and notes sub-screen unifying local dossier notes and message edit history
 * with live counters and confirmation prompts before destructive wipes.
 */
public class MirrlyVaultSettingsActivity extends MirrlyBaseSettingsFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int ID_HERO = 1;
    private static final int ID_OPEN_NOTES_CHAT = 10;
    private static final int ID_CLEAR_NOTES = 11;
    private static final int ID_SAVE_EDIT_HISTORY = 20;
    private static final int ID_CLEAR_EDIT_HISTORY = 21;

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.mirrlyNotesDidUpdate);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.mirrlyNotesDidUpdate);
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
                MirrlyConfig.setSaveEditHistory(!MirrlyConfig.saveEditHistory);
                updateList(true);
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

    private void updateList(boolean animated) {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(animated);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.mirrlyNotesDidUpdate || id == NotificationCenter.didSetNewTheme) {
            updateList(id == NotificationCenter.mirrlyNotesDidUpdate);
        }
    }
}
