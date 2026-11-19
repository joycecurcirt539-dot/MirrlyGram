package org.telegram.ui;

import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.mirrly.MirrlyConfig;
import org.telegram.messenger.mirrly.MirrlyEngineController;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

/**
 * Network engine sub-screen featuring segmented transport selector,
 * icon-aligned TLS controls, and native diagnostics access.
 */
public class MirrlyNetworkSettingsActivity extends MirrlyBaseSettingsFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int ID_HERO = 1;
    private static final int ID_SEGMENT_TRANSPORT = 2;
    private static final int ID_TLS_ENABLED = 3;
    private static final int ID_TLS_SPLIT = 4;
    private static final int ID_TLS_SHADOW = 5;
    private static final int ID_SOFT_RECONNECT = 7;
    private static final int ID_OPEN_PROXY_LIST = 8;

    private boolean pollingStats = false;

    private final Runnable statsTicker = new Runnable() {
        @Override
        public void run() {
            if (!pollingStats) {
                return;
            }
            MirrlyUiKit.EngineSnapshot s = MirrlyUiKit.EngineSnapshot.capture();
            refreshHeroSubtitles(buildHeroSubtitles(s), true);
            AndroidUtilities.runOnUIThread(this, 1000);
        }
    };

    @Override
    public boolean onFragmentCreate() {
        MirrlyEngineController.ensureStarted();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        pollingStats = false;
        AndroidUtilities.cancelRunOnUIThread(statsTicker);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        super.onFragmentDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        pollingStats = true;
        AndroidUtilities.cancelRunOnUIThread(statsTicker);
        AndroidUtilities.runOnUIThread(statsTicker, 250);
    }

    @Override
    public void onPause() {
        super.onPause();
        pollingStats = false;
        AndroidUtilities.cancelRunOnUIThread(statsTicker);
    }

    @Override
    protected CharSequence getScreenTitle() {
        return LocaleController.getString(R.string.MirrlySettingsNetworkTitle);
    }

    private CharSequence[] buildHeroSubtitles(MirrlyUiKit.EngineSnapshot snapshot) {
        String endpointOrStatus = snapshot.isLocalOnline()
                ? snapshot.getEndpointString() + " • " + LocaleController.formatShortDuration((int) Math.min(Integer.MAX_VALUE, snapshot.uptimeSecs))
                : snapshot.getEndpointString();
        return new CharSequence[]{
                endpointOrStatus,
                snapshot.getHeroEngineLine()
        };
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        MirrlyUiKit.EngineSnapshot snapshot = MirrlyUiKit.EngineSnapshot.capture();

        items.add(MirrlyUiKit.HeroCardView.Factory.of(
                ID_HERO,
                new MirrlyUiKit.HeroSpec(
                        MirrlyUiKit.HeroSpec.MODE_LOTTIE,
                        R.raw.utyan_private,
                        false,
                        LocaleController.getString(R.string.MirrlySettingsNetworkTitle),
                        buildHeroSubtitles(snapshot),
                        snapshot.getHeroEngineLine()
                )
        ));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyNetworkModeHeader)));
        int selectedMode = snapshot.isMtproto ? 1 : 0;
        items.add(MirrlyUiKit.SegmentedCardCell.Factory.of(
                ID_SEGMENT_TRANSPORT,
                new MirrlyUiKit.SegmentedSpec(
                        R.drawable.mirrly_seg_socks5,
                        LocaleController.getString(R.string.MirrlyNetworkModeSocks5),
                        R.drawable.mirrly_seg_mtproto,
                        LocaleController.getString(R.string.MirrlyNetworkModeMtproto),
                        selectedMode,
                        this::handleTransportSelected
                )
        ));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MirrlyNetworkModeHint)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyNetworkTlsHeader)));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_TLS_ENABLED,
                R.drawable.outline_shield_check,
                LocaleController.getString(R.string.MirrlyNetworkTlsEnable),
                MirrlyConfig.tlsEnabled
        ));
        if (MirrlyConfig.tlsEnabled) {
            items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                    ID_TLS_SPLIT,
                    R.drawable.msg_instant,
                    LocaleController.getString(R.string.MirrlyNetworkTlsSplit),
                    MirrlyConfig.tlsClientHelloSplit
            ));
        }
        items.add(UItem.asShadow(
                ID_TLS_SHADOW,
                LocaleController.getString(MirrlyConfig.tlsEnabled ? R.string.MirrlyNetworkTlsHintOn : R.string.MirrlyNetworkTlsHintOff)
        ));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyNetworkControlHeader)));
        items.add(UItem.asButton(
                ID_SOFT_RECONNECT,
                R.drawable.msg_retry,
                LocaleController.getString(R.string.MirrlyNetworkReconnect)
        ).accent());
        items.add(UItem.asButton(
                ID_OPEN_PROXY_LIST,
                R.drawable.msg_settings,
                LocaleController.getString(R.string.MirrlyNetworkProxyList)
        ));
    }

    private void handleTransportSelected(int index) {
        boolean useMtproto = index == 1;
        MirrlyEngineController.switchActiveTransport(useMtproto);
        refreshAll(true);
        BulletinFactory.of(this).createSimpleBulletin(
                R.raw.info,
                LocaleController.getString(useMtproto ? R.string.MirrlyNetworkMtprotoActivated : R.string.MirrlyNetworkSocks5Activated)
        ).show();
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item == null) {
            return;
        }
        switch (item.id) {
            case ID_TLS_ENABLED:
                boolean nextTls = !MirrlyConfig.tlsEnabled;
                MirrlyConfig.setTlsEnabled(nextTls);
                refreshAll(true);
                BulletinFactory.of(this).createSimpleBulletin(
                        R.raw.info,
                        LocaleController.getString(nextTls ? R.string.MirrlyNetworkTlsOnToast : R.string.MirrlyNetworkTlsOffToast)
                ).show();
                break;

            case ID_TLS_SPLIT:
                MirrlyConfig.setTlsClientHelloSplit(!MirrlyConfig.tlsClientHelloSplit);
                refreshAll(true);
                break;

            case ID_SOFT_RECONNECT:
                MirrlyEngineController.softReconnect();
                refreshHeroSubtitles(buildHeroSubtitles(MirrlyUiKit.EngineSnapshot.capture()), true);
                BulletinFactory.of(this).createSimpleBulletin(
                        R.raw.ic_save_to_gallery,
                        LocaleController.getString(R.string.MirrlyNetworkReconnectedToast)
                ).show();
                break;

            case ID_OPEN_PROXY_LIST:
                presentFragment(new ProxyListActivity());
                break;
        }
    }

    private void refreshAll(boolean animated) {
        refreshHeroSubtitles(buildHeroSubtitles(MirrlyUiKit.EngineSnapshot.capture()), animated);
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(animated);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.proxySettingsChanged || id == NotificationCenter.didSetNewTheme) {
            refreshAll(id == NotificationCenter.proxySettingsChanged);
        }
    }
}
