package org.telegram.ui;

import android.view.View;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.mirrly.MirrlyConfig;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

/**
 * Chats and media behavior sub-screen with icon-aligned switch controls.
 */
public class MirrlyChatsSettingsActivity extends MirrlyBaseSettingsFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int ID_HERO = 1;
    private static final int ID_CONFIRM_MEDIA_SEND = 10;
    private static final int ID_ROUND_VIDEO_BACK_CAMERA = 11;

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        super.onFragmentDestroy();
    }

    @Override
    protected CharSequence getScreenTitle() {
        return LocaleController.getString(R.string.MirrlySettingsChatsTitle);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        MirrlyConfig.load();

        items.add(MirrlyUiKit.HeroCardView.Factory.of(
                ID_HERO,
                new MirrlyUiKit.HeroSpec(
                        MirrlyUiKit.HeroSpec.MODE_LOTTIE,
                        R.raw.utyan_gallery,
                        false,
                        LocaleController.getString(R.string.MirrlySettingsChatsTitle),
                        new CharSequence[]{LocaleController.getString(R.string.MirrlyChatsHeroPill)},
                        LocaleController.getString(R.string.MirrlySettingsChatsTitle)
                )
        ));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyChatsBehaviorHeader)));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_CONFIRM_MEDIA_SEND,
                R.drawable.msg_sticker,
                LocaleController.getString(R.string.MirrlyChatsConfirmStickers),
                MirrlyConfig.confirmMediaSend
        ));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_ROUND_VIDEO_BACK_CAMERA,
                R.drawable.outline_attach_camera_24,
                LocaleController.getString(R.string.MirrlyChatsBackCameraRounds),
                MirrlyConfig.isRoundVideoBackCamera()
        ));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MirrlyChatsBehaviorHint)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item == null) {
            return;
        }
        if (item.id == ID_CONFIRM_MEDIA_SEND) {
            MirrlyConfig.setConfirmMediaSend(!MirrlyConfig.confirmMediaSend);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        } else if (item.id == ID_ROUND_VIDEO_BACK_CAMERA) {
            MirrlyConfig.setRoundVideoBackCamera(!MirrlyConfig.isRoundVideoBackCamera());
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetNewTheme && listView != null && listView.adapter != null) {
            listView.adapter.update(false);
        }
    }
}
