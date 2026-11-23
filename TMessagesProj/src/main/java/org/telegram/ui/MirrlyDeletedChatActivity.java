package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatThemeController;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.mirrly.MirrlyConfig;
import org.telegram.messenger.mirrly.MirrlyDeletedChatsStorage;
import org.telegram.messenger.mirrly.MirrlyDeletedStorage;
import org.telegram.messenger.mirrly.MirrlyVaultStorage;
import org.telegram.messenger.utils.ViewOutlineProviderImpl;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.EdgeToEdgeSupportMode;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Stories.recorder.PreviewView;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.StickersAlert;
import org.telegram.ui.Components.ThanosEffect;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.DownscaleScrollableNoiseSuppressor;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProviderThemed;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.Components.chat.WallpaperBitmapProvider;
import org.telegram.ui.Components.chat.buttons.ChatActivityBlurredRoundPageDownButton;
import org.telegram.ui.Components.chat.layouts.ChatActivityFadeView;
import org.telegram.ui.Components.inset.WindowInsetsStateHolder;
import org.telegram.ui.recyclerview.ChatListItemAnimator;
import org.telegram.ui.recyclerview.LinearSmoothScrollerCustom;
import me.vkryl.core.BitwiseUtils;
import org.telegram.utils.glass.GlassContrastHelper;
import org.telegram.utils.glass.GlassEngine;
import org.telegram.utils.glass.positions.GlassPositionsArray;
import org.telegram.utils.glass.positions.GlassPositionsMerger;
import org.telegram.utils.glass.source.GlassSource;
import org.telegram.utils.glass.source.GlassSourceBitmap;
import org.telegram.utils.glass.source.GlassSourceRenderNode;
import org.telegram.utils.glass.source.GlassSourceWrapped;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Local deleted and disappearing messages chat archive displaying saved messages
 * across chats with category filters, search, playback, media export to MirrlyGram,
 * jump-to-source, and native Telegram Thanos disintegration animation on delete.
 */
public class MirrlyDeletedChatActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int MENU_SEARCH_ICON = 1;
    private static final int MENU_SEARCH = 2;
    private static final int MENU_FILTER = 3;
    private static final int MENU_MORE = 4;
    private static final int SUB_SETTINGS = 20;
    private static final int SUB_CLEAR_ALL = 21;

    private static final int AM_FORWARD = 10;
    private static final int AM_DELETE = 11;

    private final @NonNull GlassSourceWrapped navbarContentSourceWallpaper;
    private final @NonNull BlurredBackgroundDrawableViewFactory navbarContentDrawableFactory;
    private final @Nullable GlassSourceRenderNode glassBackgroundSourceRenderNode;
    private final @Nullable GlassSourceRenderNode glassBackgroundSourceFrostedRenderNode;
    private final @NonNull BlurredBackgroundDrawableViewFactory glassBackgroundDrawableFactory;
    private final @NonNull BlurredBackgroundDrawableViewFactory glassBackgroundDrawableFactoryFrosted;
    private final @Nullable DownscaleScrollableNoiseSuppressor scrollableViewNoiseSuppressor;
    private final int recommendedAdditionalSizeY;
    private BlurredBackgroundColorProviderThemed blurredBackgroundColorProvider;

    private final WindowInsetsStateHolder windowInsetsStateHolder = new WindowInsetsStateHolder(this::onInsetsUpdated);

    private DeletedChatFragmentView contentView;
    private ChatActivityFadeView chatActivityFadeView;
    private ChatAvatarContainer avatarContainer;
    private ActionBarMenuItem searchIconItem;
    private ActionBarMenuItem searchItem;
    private ActionBarMenuItem filterItem;
    private ActionBarMenuItem moreItem;

    private RecyclerListView chatListView;
    private LinearLayoutManager chatLayoutManager;
    private ChatListItemAnimator chatListItemAnimator;
    private DeletedAdapter deletedAdapter;

    private ChatActionCell floatingDateView;
    private AnimatorSet floatingDateAnimation;
    private boolean currentFloatingDateOnScreen;
    private boolean currentFloatingTopIsNotMessage;
    private boolean scrollingFloatingDate;
    private float floatingDateOffset = 0f;

    private FrameLayout emptyViewContainer;
    private LinearLayout emptyCardView;
    private RLottieImageView emptyLottieView;
    private TextView emptyTitleView;
    private TextView emptySubtitleView;
    private TextView emptyEnableButton;

    private FrameLayout filterChipsContainer;
    private HorizontalScrollView filterChipsScroll;
    private LinearLayout filterChipsRow;
    private final ArrayList<FilterChipView> filterChipViews = new ArrayList<>();
    private boolean filterChipsToggleOpen = false;

    private FrameLayout filterBanner;
    private TextView filterBannerText;
    private ImageView filterBannerClear;

    private ChatActivityBlurredRoundPageDownButton scrollDownButton;
    private boolean scrollDownVisible = false;

    private FrameLayout bottomBarContainer;
    private LinearLayout searchBottomBar;
    private TextView searchBottomCountView;
    private ImageView searchPrevBtn;
    private ImageView searchNextBtn;

    private LinearLayout actionModeBottomBar;
    private TextView actionModeCountView;
    private TextView actionModeForwardBtn;
    private TextView actionModeDeleteBtn;

    private ThanosEffect thanosEffect;
    private final GlassEngine glassEngine = new GlassEngine();
    private int adaptiveScrollGen;

    private final ArrayList<DeletedItem> items = new ArrayList<>();
    private final Map<String, Integer> stableMessageIdMap = new HashMap<>();
    private final HashMap<Long, MessageObject.GroupedMessages> groupedMessagesMap = new HashMap<>();
    private final AtomicInteger nextSyntheticId = new AtomicInteger(-200000);
    private final Set<String> selectedUids = new LinkedHashSet<>();

    private final long filterDialogId;
    private final boolean isArchivedChatMode;
    private final @Nullable Theme.ResourcesProvider customResourceProvider;
    private @Nullable MirrlyDeletedChatsStorage.DeletedChatSummary chatSummary;
    private @Nullable Drawable customChatBackgroundDrawable;
    private boolean customChatBackgroundMotion;

    private int activeFilter = MirrlyDeletedStorage.FILTER_ALL;
    private String searchQuery = "";
    private boolean isSearching = false;
    private int currentSearchNavPos = -1;

    private static final class DeletedItem {
        final MessageObject messageObject;
        final @Nullable MirrlyDeletedStorage.DeletedRecord record;

        DeletedItem(MessageObject messageObject, @Nullable MirrlyDeletedStorage.DeletedRecord record) {
            this.messageObject = messageObject;
            this.record = record;
        }

        boolean isDate() {
            return record == null;
        }
    }

    public MirrlyDeletedChatActivity() {
        this(0L, null, null);
    }

    public MirrlyDeletedChatActivity(long filterDialogId, @Nullable MirrlyDeletedChatsStorage.DeletedChatSummary summary) {
        this(filterDialogId, summary, null);
    }

    public MirrlyDeletedChatActivity(
            long filterDialogId,
            @Nullable MirrlyDeletedChatsStorage.DeletedChatSummary summary,
            @Nullable Theme.ResourcesProvider customResourceProvider
    ) {
        this.filterDialogId = filterDialogId;
        this.isArchivedChatMode = summary != null;
        this.chatSummary = summary;
        this.customResourceProvider = customResourceProvider;
        if (customResourceProvider != null) {
            this.resourceProvider = customResourceProvider;
        }
        glassEngine.setGlassInvalidationListener(this::invalidateMergedVisibleBlurredPositionsAndSourcesImpl);
        glassEngine.setPositionsMerger(new GlassPositionsMerger(Float.POSITIVE_INFINITY, dp(48)));

        navbarContentSourceWallpaper = new GlassSourceWrapped();
        navbarContentDrawableFactory = new BlurredBackgroundDrawableViewFactory(navbarContentSourceWallpaper);
        navbarContentDrawableFactory.setGlassEngine(glassEngine);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SharedConfig.chatBlurEnabled()) {
            scrollableViewNoiseSuppressor = new DownscaleScrollableNoiseSuppressor();
            glassEngine.addNoiseSuppressor(scrollableViewNoiseSuppressor);

            glassBackgroundSourceFrostedRenderNode = new GlassSourceRenderNode(navbarContentSourceWallpaper);
            glassBackgroundSourceFrostedRenderNode.setScrollableNoiseSuppressor(scrollableViewNoiseSuppressor, DownscaleScrollableNoiseSuppressor.DRAW_FROSTED_GLASS);
            glassBackgroundSourceFrostedRenderNode.setUnderSource(navbarContentSourceWallpaper);

            if (Build.VERSION.SDK_INT >= 33) {
                glassBackgroundSourceRenderNode = new GlassSourceRenderNode(navbarContentSourceWallpaper);
                glassBackgroundSourceRenderNode.setScrollableNoiseSuppressor(scrollableViewNoiseSuppressor, DownscaleScrollableNoiseSuppressor.DRAW_GLASS);
                glassBackgroundSourceRenderNode.setUnderSource(navbarContentSourceWallpaper);
                glassBackgroundDrawableFactory = new BlurredBackgroundDrawableViewFactory(
                        glassBackgroundSourceRenderNode,
                        glassBackgroundSourceFrostedRenderNode,
                        navbarContentSourceWallpaper
                );
                glassBackgroundDrawableFactory.setGlassEngine(glassEngine);
                glassBackgroundDrawableFactory.setOutset(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? dp(8) : dp(48));
                glassBackgroundDrawableFactory.setLiquidGlassEffectAllowed(true);

                glassBackgroundDrawableFactoryFrosted = new BlurredBackgroundDrawableViewFactory(
                        glassBackgroundSourceRenderNode,
                        glassBackgroundSourceFrostedRenderNode,
                        navbarContentSourceWallpaper
                );
                glassBackgroundDrawableFactoryFrosted.setGlassEngine(glassEngine);
                glassBackgroundDrawableFactoryFrosted.setOutset(dp(48));
                recommendedAdditionalSizeY = LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? 0 : dp(48);
            } else {
                glassBackgroundSourceRenderNode = null;
                glassBackgroundDrawableFactoryFrosted = new BlurredBackgroundDrawableViewFactory(glassBackgroundSourceFrostedRenderNode);
                glassBackgroundDrawableFactoryFrosted.setGlassEngine(glassEngine);
                glassBackgroundDrawableFactoryFrosted.setOutset(dp(48));
                glassBackgroundDrawableFactory = glassBackgroundDrawableFactoryFrosted;
                recommendedAdditionalSizeY = dp(48);
            }
        } else {
            scrollableViewNoiseSuppressor = null;
            recommendedAdditionalSizeY = 0;
            glassBackgroundSourceRenderNode = null;
            glassBackgroundSourceFrostedRenderNode = null;
            glassBackgroundDrawableFactory = new BlurredBackgroundDrawableViewFactory(navbarContentSourceWallpaper);
            glassBackgroundDrawableFactory.setGlassEngine(glassEngine);
            glassBackgroundDrawableFactoryFrosted = glassBackgroundDrawableFactory;
        }
    }

    @Override
    public EdgeToEdgeSupportMode getEdgeToEdgeSupportMode() {
        return EdgeToEdgeSupportMode.FULL;
    }

    @Override
    public Theme.ResourcesProvider getResourceProvider() {
        return customResourceProvider != null ? customResourceProvider : super.getResourceProvider();
    }

    @Override
    public boolean isLightStatusBar() {
        Theme.ResourcesProvider rp = getResourceProvider();
        if (rp instanceof ChatActivity.ThemeDelegate) {
            return !((ChatActivity.ThemeDelegate) rp).isDark();
        }
        return super.isLightStatusBar();
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        if (customResourceProvider != null) {
            this.resourceProvider = customResourceProvider;
        }
        blurredBackgroundColorProvider = new BlurredBackgroundColorProviderThemed(resourceProvider, Theme.key_chat_messagePanelBackground) {
            @Override
            public int getBackgroundColor() {
                if (!BlurredBackgroundProviderImpl.checkBlurEnabled(currentAccount, resourceProvider)) {
                    return ColorUtils.setAlphaComponent(getThemedColor(Theme.key_chat_messagePanelBackground), 255);
                }
                return super.getBackgroundColor();
            }
        };
        if (isArchivedChatMode && filterDialogId != 0) {
            MirrlyDeletedChatsStorage chatsStorage = MirrlyDeletedChatsStorage.getInstance(currentAccount);
            chatsStorage.hydratePeersIntoMemory();
            MirrlyDeletedChatsStorage.DeletedChatSummary latest = chatsStorage.getChatSummary(filterDialogId);
            if (latest != null) {
                chatSummary = latest;
            }
        }
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didApplyNewTheme);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.mirrlyDeletedDidUpdate);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.mirrlyDeletedAppearanceChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.mirrlyDeletedChatsDidUpdate);

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);

        Bulletin.addDelegate(this, new Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                return (int) (windowInsetsStateHolder.getAnimatedMaxBottomInset() + getBottomBarOccupiedHeight() + dp(8));
            }
        });
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didApplyNewTheme);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.mirrlyDeletedDidUpdate);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.mirrlyDeletedAppearanceChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.mirrlyDeletedChatsDidUpdate);

        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);

        Bulletin.removeDelegate(this);
        if (thanosEffect != null) {
            thanosEffect.kill();
            thanosEffect = null;
        }
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        Theme.createChatResources(context, false);
        hasOwnBackground = true;

        setupActionBar(context);

        contentView = new DeletedChatFragmentView(context, getParentLayout());
        fragmentView = contentView;

        ViewPositionWatcher viewPositionWatcher = new ViewPositionWatcher(contentView);
        glassBackgroundDrawableFactory.setSourceRootView(viewPositionWatcher, contentView);
        glassBackgroundDrawableFactoryFrosted.setSourceRootView(viewPositionWatcher, contentView);
        navbarContentDrawableFactory.setSourceRootView(viewPositionWatcher, contentView);

        contentView.setOccupyStatusBar(!AndroidUtilities.isTablet());
        applyChatBackground();
        contentView.setClipChildren(false);

        actionBar.setupGlass(glassBackgroundDrawableFactory, BlurredBackgroundProviderImpl.topPanelChatActivity(resourceProvider), false);

        buildListAndEmptyState(context);
        buildFadeAndFloatingOverlays(context);
        buildTopOverlays(context);
        buildScrollDownButton(context);
        buildBottomBars(context);

        contentView.addView(actionBar);

        ViewCompat.setOnApplyWindowInsetsListener(contentView, this::onApplyWindowInsets);

        reloadRecords(false);
        updateHeaderSubtitle();
        updateFilterBanner(false);
        updateFilterChipsVisibility(false);
        updateBottomModeBars();

        return fragmentView;
    }

    private void applyChatBackground() {
        if (contentView == null) {
            return;
        }
        customChatBackgroundDrawable = null;
        customChatBackgroundMotion = false;
        if (customResourceProvider instanceof ChatActivity.ThemeDelegate) {
            ChatActivity.ThemeDelegate td = (ChatActivity.ThemeDelegate) customResourceProvider;
            if (td.isCompactMode()) {
                customChatBackgroundDrawable = new ColorDrawable(td.getColor(Theme.key_chat_compact_background));
                customChatBackgroundMotion = false;
            } else {
                customChatBackgroundDrawable = td.getWallpaperDrawable();
                customChatBackgroundMotion = td.isWallpaperMotion();
            }
        } else if (filterDialogId != 0) {
            TLRPC.WallPaper wp = ChatThemeController.getInstance(currentAccount).getDialogWallpaper(filterDialogId);
            if (wp != null) {
                boolean dark = Theme.isCurrentThemeDark();
                if (!TextUtils.isEmpty(ChatThemeController.getWallpaperEmoticon(wp))) {
                    customChatBackgroundDrawable = PreviewView.getBackgroundDrawable(null, currentAccount, wp, dark);
                } else {
                    customChatBackgroundDrawable = ChatBackgroundDrawable.getOrCreate(null, wp, dark);
                }
                customChatBackgroundMotion = wp.settings != null && wp.settings.motion;
            }
        }
        Drawable targetDrawable = customChatBackgroundDrawable != null ? customChatBackgroundDrawable : Theme.getCachedWallpaper();
        boolean targetMotion = customChatBackgroundDrawable != null ? customChatBackgroundMotion : Theme.isWallpaperMotion();
        contentView.setBackgroundImage(targetDrawable, targetMotion);
    }

    @NonNull
    private Drawable createDeletedAvatarDrawable(Context context) {
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFEF5350, 0xFFC62828}
        );
        bg.setShape(GradientDrawable.OVAL);

        Drawable icon = ContextCompat.getDrawable(context, R.drawable.msg_delete);
        if (icon != null) {
            Drawable mutated = icon.mutate();
            mutated.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
            LayerDrawable layers = new LayerDrawable(new Drawable[]{bg, mutated});
            int inset = dp(9);
            layers.setLayerInset(1, inset, inset, inset, inset);
            return layers;
        }
        return bg;
    }

    private void setupActionBar(Context context) {
        actionBar.setAddToContainer(false);
        actionBar.setCastShadows(false);
        actionBar.setBackground(null);
        actionBar.setOccupyStatusBar(!AndroidUtilities.isTablet());
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (actionBar.isActionModeShowed()) {
                        exitActionMode();
                    } else if (isSearching) {
                        actionBar.closeSearchField();
                    } else {
                        finishFragment();
                    }
                } else if (id == MENU_SEARCH_ICON) {
                    openSearch();
                } else if (id == MENU_FILTER) {
                    filterChipsToggleOpen = !filterChipsToggleOpen;
                    updateFilterChipsVisibility(true);
                    updateFilterBanner(true);
                    updateListPaddingAndButtons();
                } else if (id == SUB_SETTINGS) {
                    presentFragment(new MirrlyVaultSettingsActivity());
                } else if (id == SUB_CLEAR_ALL) {
                    confirmClearAllRecords();
                } else if (id == AM_FORWARD) {
                    forwardSelectedRecords();
                } else if (id == AM_DELETE) {
                    confirmDeleteSelectedRecords();
                }
            }
        });

        avatarContainer = new ChatAvatarContainer(context, this, false, resourceProvider) {
            @Override
            protected boolean useAnimatedSubtitle() {
                return true;
            }
        };
        avatarContainer.setGlassMode();
        avatarContainer.setOccupyStatusBar(!AndroidUtilities.isTablet());
        avatarContainer.setEnabled(false);
        if (filterDialogId != 0) {
            bindDeletedChatAvatarAndTitle();
        } else {
            avatarContainer.getAvatarImageView().setImageDrawable(createDeletedAvatarDrawable(context));
            avatarContainer.setTitle(getString(R.string.MirrlyDeletedTitle));
        }
        actionBar.addView(avatarContainer, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 54, 0, 128, 0));

        ActionBarMenu menu = actionBar.createMenu();
        actionBar.setGlassMenuIndividualCircles(true);
        searchIconItem = menu.addItem(MENU_SEARCH_ICON, R.drawable.outline_header_search);
        searchIconItem.setContentDescription(getString(R.string.Search));

        searchItem = menu.addItem(MENU_SEARCH, R.drawable.outline_header_search)
                .setIsSearchField(true)
                .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                    @Override
                    public void onSearchExpand() {
                        isSearching = true;
                        actionBar.setSearchFactor(1f);
                        avatarContainer.setVisibility(View.GONE);
                        if (searchIconItem != null) {
                            searchIconItem.setVisibility(View.GONE);
                        }
                        if (filterItem != null) {
                            filterItem.setVisibility(View.GONE);
                        }
                        if (moreItem != null) {
                            moreItem.setVisibility(View.GONE);
                        }
                        if (searchItem != null) {
                            searchItem.setVisibility(View.VISIBLE);
                        }
                        currentSearchNavPos = -1;
                        updateFilterChipsVisibility(true);
                        updateFilterBanner(false);
                        updateBottomModeBars();
                        updateEmptyContainerPosition();
                        updateListPaddingAndButtons();
                    }

                    @Override
                    public void onSearchCollapse() {
                        isSearching = false;
                        actionBar.setSearchFactor(0f);
                        searchQuery = "";
                        currentSearchNavPos = -1;
                        avatarContainer.setVisibility(View.VISIBLE);
                        if (searchIconItem != null) {
                            searchIconItem.setVisibility(View.VISIBLE);
                        }
                        if (filterItem != null) {
                            filterItem.setVisibility(View.VISIBLE);
                        }
                        if (moreItem != null) {
                            moreItem.setVisibility(View.VISIBLE);
                        }
                        if (searchItem != null) {
                            searchItem.setVisibility(View.GONE);
                        }
                        updateFilterChipsVisibility(true);
                        updateFilterBanner(true);
                        updateBottomModeBars();
                        reloadRecords(true);
                        updateEmptyContainerPosition();
                        updateListPaddingAndButtons();
                    }

                    @Override
                    public void onTextChanged(EditText editText) {
                        String next = editText.getText() != null ? editText.getText().toString() : "";
                        if (!TextUtils.equals(searchQuery, next)) {
                            searchQuery = next;
                            currentSearchNavPos = -1;
                            reloadRecords(true);
                            updateSearchBottomBarState();
                        }
                    }

                    @Override
                    public void onSearchPressed(EditText editText) {
                        String next = editText.getText() != null ? editText.getText().toString() : "";
                        searchQuery = next;
                        reloadRecords(true);
                        updateSearchBottomBarState();
                        AndroidUtilities.hideKeyboard(editText);
                    }
                });
        searchItem.setSearchFieldHint(getString(R.string.MirrlyDeletedSearchHint));
        searchItem.setSearchPaddingStart(7);
        searchItem.setVisibility(View.GONE);

        filterItem = menu.addItem(MENU_FILTER, R.drawable.menu_tag_filter);
        filterItem.setContentDescription(getString(R.string.MirrlyNotesFilterDesc));

        moreItem = menu.addItem(MENU_MORE, R.drawable.ic_ab_other);
        moreItem.addSubItem(SUB_SETTINGS, R.drawable.msg_settings, getString(R.string.MirrlyDeletedMenuSettings));
        moreItem.addSubItem(
                SUB_CLEAR_ALL,
                R.drawable.msg_delete,
                getString(filterDialogId != 0
                        ? (isArchivedChatMode ? R.string.MirrlyDeletedChatsRemoveSingle : R.string.MirrlyChatDeletedClearAction)
                        : R.string.MirrlyVaultClearDeleted)
        );

        ActionBarMenu actionMode = actionBar.createActionMode();
        ThemeBind.clear(actionMode);
        actionMode.setBackgroundColor(Color.TRANSPARENT);
    }

    private void openSearch() {
        if (actionBar == null || searchItem == null) {
            return;
        }
        searchItem.setVisibility(View.VISIBLE);
        searchItem.toggleSearch(true);
    }

    private void buildListAndEmptyState(Context context) {
        chatListView = new RecyclerListView(context);
        chatListView.setVerticalScrollBarEnabled(false);
        chatListView.setClipToPadding(true);
        chatListView.setAdapter(deletedAdapter = new DeletedAdapter(context));

        chatLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, true) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return true;
            }

            @Override
            public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
                LinearSmoothScrollerCustom scroller = new LinearSmoothScrollerCustom(recyclerView.getContext(), LinearSmoothScrollerCustom.POSITION_MIDDLE);
                scroller.setTargetPosition(position);
                startSmoothScroll(scroller);
            }
        };
        chatListView.setLayoutManager(chatLayoutManager);

        chatListItemAnimator = new ChatListItemAnimator(null, chatListView, resourceProvider);
        chatListItemAnimator.setShouldAnimateEnterFromBottom(true);
        chatListView.setItemAnimator(chatListItemAnimator);

        chatListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    scrollingFloatingDate = true;
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    scrollingFloatingDate = false;
                    hideFloatingDateView(true);
                    adaptiveScrollGen++;
                    if (floatingDateView != null && floatingDateView.getAlpha() > 0.01f) {
                        floatingDateView.invalidate();
                    }
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                chatListView.invalidate();
                if (dy != 0) {
                    adaptiveScrollGen++;
                }
                if (dy != 0 && scrollingFloatingDate && !currentFloatingTopIsNotMessage) {
                    showFloatingDateView();
                }
                updateMessagesVisiblePart();
                updateScrollDownButtonVisibility();
            }
        });

        chatListView.setOnItemClickListener((view, position, x, y) -> {
            if (view instanceof ChatMessageCell) {
                ChatMessageCell cell = (ChatMessageCell) view;
                if (actionBar != null && actionBar.isActionModeShowed()) {
                    MirrlyDeletedStorage.DeletedRecord rec = findRecordForMessageObject(cell.getMessageObject());
                    if (rec != null) {
                        toggleSelection(rec);
                    }
                    return;
                }
                showDeletedItemOptions(cell);
            }
        });
        chatListView.setOnItemLongClickListener((view, position, x, y) -> {
            if (view instanceof ChatMessageCell) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                ChatMessageCell cell = (ChatMessageCell) view;
                if (actionBar != null && actionBar.isActionModeShowed()) {
                    MirrlyDeletedStorage.DeletedRecord rec = findRecordForMessageObject(cell.getMessageObject());
                    if (rec != null) {
                        toggleSelection(rec);
                    }
                } else {
                    showDeletedItemOptions(cell);
                }
                return true;
            }
            return false;
        });

        contentView.addView(chatListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        glassEngine.addScrolledView(chatListView);

        emptyViewContainer = new FrameLayout(context);
        emptyViewContainer.setVisibility(View.INVISIBLE);
        contentView.addView(emptyViewContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 28, 0, 28, 0));

        emptyCardView = new LinearLayout(context);
        emptyCardView.setOrientation(LinearLayout.VERTICAL);
        emptyCardView.setGravity(Gravity.CENTER_HORIZONTAL);
        emptyCardView.setPadding(dp(24), dp(22), dp(24), dp(22));
        emptyCardView.setOutlineProvider(ViewOutlineProviderImpl.boundsWithPaddingRoundRect(dp(4), dp(20)));
        emptyCardView.setClipToOutline(true);
        emptyCardView.setBackground(glassBackgroundDrawableFactory.create(emptyCardView)
                .setColorProvider(BlurredBackgroundProviderImpl.topPanelChatActivity(resourceProvider))
                .setRadius(dp(20))
                .setPadding(dp(4)));

        emptyLottieView = new RLottieImageView(context);
        emptyLottieView.setAutoRepeat(true);
        emptyLottieView.setAnimation(R.raw.utyan_empty, 110, 110);
        emptyLottieView.playAnimation();
        emptyCardView.addView(emptyLottieView, LayoutHelper.createLinear(110, 110, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 6));

        emptyTitleView = new TextView(context);
        emptyTitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        emptyTitleView.setTypeface(AndroidUtilities.bold());
        emptyTitleView.setGravity(Gravity.CENTER);
        ThemeBind.textColor(emptyTitleView, Theme.key_windowBackgroundWhiteBlackText, resourceProvider);
        emptyCardView.addView(emptyTitleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 6));

        emptySubtitleView = new TextView(context);
        emptySubtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptySubtitleView.setGravity(Gravity.CENTER);
        ThemeBind.textColor(emptySubtitleView, Theme.key_windowBackgroundWhiteGrayText, resourceProvider);
        emptyCardView.addView(emptySubtitleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));

        emptyEnableButton = new TextView(context);
        emptyEnableButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptyEnableButton.setTypeface(AndroidUtilities.bold());
        emptyEnableButton.setGravity(Gravity.CENTER);
        emptyEnableButton.setPadding(dp(18), dp(10), dp(18), dp(10));
        emptyEnableButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourceProvider));
        emptyEnableButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                dp(14),
                Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourceProvider)
        ));
        emptyEnableButton.setText(getString(R.string.MirrlyDeletedEnableBtn));
        emptyEnableButton.setOnClickListener(v -> {
            MirrlyConfig.setSaveDeletedMessages(true);
            updateEmptyPlaceholder();
            updateHeaderSubtitle();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.MirrlyDeletedEnabledToast)).show();
        });
        if (filterDialogId != 0) {
            updateEmptyPlaceholder();
        }
        emptyCardView.addView(emptyEnableButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 14, 0, 0));

        emptyViewContainer.addView(emptyCardView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private void buildFadeAndFloatingOverlays(Context context) {
        chatActivityFadeView = new ChatActivityFadeView(context);
        chatActivityFadeView.setResourcesProvider(resourceProvider);
        chatActivityFadeView.setup(navbarContentDrawableFactory);
        chatActivityFadeView.setFadeHeightTop(dp(48));
        chatActivityFadeView.setFadeHeightBottom(dp(48));
        chatActivityFadeView.setFadeZoneTop(AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight());
        contentView.addView(chatActivityFadeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        floatingDateView = new ChatActionCell(context, false, resourceProvider) {
            private BlurredBackgroundDrawable glassBackground;
            private BlurredBackgroundColorProvider glassColorProvider;
            private int lastServiceBg;
            private int lastTextColor;
            private int lastGlassGeneration = -1;
            private float lastGlassRadius;

            private void ensureGlassBackground() {
                if (glassBackground == null && glassBackgroundDrawableFactory != null) {
                    glassColorProvider = createFloatingPillColorProvider();
                    glassBackground = glassBackgroundDrawableFactory.create(this, glassColorProvider);
                    glassBackground.setRadius(dp(12));
                    glassBackground.setPadding(dp(4));
                    glassBackground.setCallback(this);
                }
            }

            @Override
            protected boolean verifyDrawable(@NonNull Drawable who) {
                return who == glassBackground || super.verifyDrawable(who);
            }

            @Override
            public void drawBackground(Canvas canvas, boolean fromParent) {
                final boolean useGlass = glassBackgroundDrawableFactory != null
                        && (!Theme.isCompactMode(resourceProvider) || MirrlyConfig.isLiquidGlassEverywhere())
                        && BlurredBackgroundProviderImpl.checkBlurEnabled(currentAccount, resourceProvider);
                if (!useGlass) {
                    super.drawBackground(canvas, fromParent);
                    return;
                }
                canvas.save();
                canvas.clipRect(0, 0, 0, 0);
                super.drawBackground(canvas, fromParent);
                canvas.restore();

                final int left = getBoundsLeft();
                final int right = getBoundsRight();
                final int top = getPaddingTop() + dp(4);
                final int bottom = getMeasuredHeight() - getPaddingBottom() - dp(4);
                if (right <= left || bottom <= top) {
                    return;
                }
                ensureGlassBackground();
                if (glassBackground == null) {
                    return;
                }
                final int serviceBg = Theme.getColor(Theme.key_chat_serviceBackground, resourceProvider);
                final int textColor = Theme.getColor(Theme.key_chat_serviceText, resourceProvider);
                final int glassGeneration = GlassEngine.getGlassSettingsGenerationId();
                if (lastServiceBg != serviceBg || lastTextColor != textColor || lastGlassGeneration != glassGeneration) {
                    lastServiceBg = serviceBg;
                    lastTextColor = textColor;
                    lastGlassGeneration = glassGeneration;
                    glassBackground.updateColors();
                }
                final float radius = Math.max(dp(12), (bottom - top) / 2f);
                if (lastGlassRadius != radius) {
                    lastGlassRadius = radius;
                    glassBackground.setRadius(radius);
                }
                final int p = dp(4);
                glassBackground.setBounds(left - p, top - p, right + p, bottom + p);
                canvas.save();
                canvas.translate(-sideMenuWidth / 2f, -getPaddingTop());
                glassBackground.draw(canvas);
                canvas.restore();
            }

            @Override
            public boolean isFloating() {
                return true;
            }

            @Override
            public void setTranslationY(float translationY) {
                if (getTranslationY() != translationY) {
                    invalidate();
                }
                super.setTranslationY(translationY);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                float clipTop = getFloatingDateRestTop() - getY();
                if (clipTop > 0f) {
                    if (clipTop < getMeasuredHeight()) {
                        canvas.save();
                        canvas.clipRect(0, clipTop, getMeasuredWidth(), getMeasuredHeight());
                        super.onDraw(canvas);
                        canvas.restore();
                    }
                } else {
                    super.onDraw(canvas);
                }
            }

            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                int nextVisibility = alpha > 0f ? View.VISIBLE : View.INVISIBLE;
                if (getVisibility() != nextVisibility) {
                    setVisibility(nextVisibility);
                }
            }
        };
        floatingDateView.setCustomDate((int) (System.currentTimeMillis() / 1000), false, false);
        floatingDateView.setAlpha(0f);
        floatingDateView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        floatingDateView.setInvalidateColors(true);
        contentView.addView(floatingDateView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));
    }

    private BlurredBackgroundColorProvider createFloatingPillColorProvider() {
        return new BlurredBackgroundColorProvider() {
            private boolean isLightText() {
                return AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_chat_serviceText, resourceProvider)) >= 0.55f;
            }

            @Override
            public int getShadowColor() {
                return isLightText() ? 0x28000000 : 0x1A000000;
            }

            @Override
            public int getBackgroundColor() {
                final boolean lightText = isLightText();
                final int serviceBg = Theme.getColor(Theme.key_chat_serviceBackground, resourceProvider);
                if (lightText) {
                    if ((serviceBg >>> 24) > 0 && AndroidUtilities.computePerceivedBrightness(serviceBg) < 0.52f) {
                        return Theme.multAlpha(ColorUtils.setAlphaComponent(serviceBg, 0xFF), MirrlyConfig.getEffectiveGlassAlpha(0.60f));
                    }
                    return Theme.multAlpha(0xFF14181E, MirrlyConfig.getEffectiveGlassAlpha(0.55f));
                }
                if ((serviceBg >>> 24) > 0 && AndroidUtilities.computePerceivedBrightness(serviceBg) > 0.52f) {
                    return Theme.multAlpha(ColorUtils.setAlphaComponent(serviceBg, 0xFF), MirrlyConfig.getEffectiveGlassAlpha(0.65f));
                }
                return Theme.multAlpha(0xFFF5F7FA, MirrlyConfig.getEffectiveGlassAlpha(0.72f));
            }

            @Override
            public int getStrokeColorTop() {
                return isLightText() ? 0x3DFFFFFF : 0xB8FFFFFF;
            }

            @Override
            public int getStrokeColorBottom() {
                return isLightText() ? 0x1AFFFFFF : 0x45FFFFFF;
            }
        };
    }

    private void buildTopOverlays(Context context) {
        filterChipsContainer = new FrameLayout(context) {
            private final Path clipPath = new Path();
            private final RectF clipRect = new RectF();
            private int lastClipW = -1;
            private int lastClipH = -1;

            @Override
            protected void dispatchDraw(Canvas canvas) {
                final int w = getWidth();
                final int h = getHeight();
                if (w != lastClipW || h != lastClipH) {
                    lastClipW = w;
                    lastClipH = h;
                    float inset = dp(4);
                    clipRect.set(inset, inset, w - inset, h - inset);
                    float radius = Math.min(dp(18), clipRect.height() * 0.5f);
                    clipPath.rewind();
                    clipPath.addRoundRect(clipRect, radius, radius, Path.Direction.CW);
                }
                canvas.save();
                canvas.clipPath(clipPath);
                super.dispatchDraw(canvas);
                canvas.restore();
            }
        };
        filterChipsContainer.setVisibility(View.GONE);
        filterChipsContainer.setAlpha(0f);
        filterChipsContainer.setPadding(dp(4), dp(4), dp(4), dp(4));
        filterChipsContainer.setBackground(glassBackgroundDrawableFactory.create(filterChipsContainer)
                .setColorProvider(BlurredBackgroundProviderImpl.topPanelChatActivity(resourceProvider))
                .setPadding(dp(4))
                .setRadius(dp(18)));

        filterChipsScroll = new HorizontalScrollView(context);
        filterChipsScroll.setHorizontalScrollBarEnabled(false);
        filterChipsScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        filterChipsScroll.setClipChildren(true);

        filterChipsRow = new LinearLayout(context);
        filterChipsRow.setOrientation(LinearLayout.HORIZONTAL);
        filterChipsRow.setGravity(Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
        filterChipsRow.setPadding(dp(6), dp(2), dp(6), dp(2));

        addFilterChip(context, MirrlyDeletedStorage.FILTER_ALL, getString(R.string.MirrlyDeletedFilterAll));
        addFilterChip(context, MirrlyDeletedStorage.FILTER_TEXT, getString(R.string.MirrlyDeletedFilterText));
        addFilterChip(context, MirrlyDeletedStorage.FILTER_MEDIA, getString(R.string.MirrlyDeletedFilterMedia));
        addFilterChip(context, MirrlyDeletedStorage.FILTER_VOICE_ROUND, getString(R.string.MirrlyDeletedFilterVoice));
        addFilterChip(context, MirrlyDeletedStorage.FILTER_FILES, getString(R.string.MirrlyDeletedFilterFiles));
        addFilterChip(context, MirrlyDeletedStorage.FILTER_EPHEMERAL, getString(R.string.MirrlyDeletedFilterEphemeral));
        addFilterChip(context, MirrlyDeletedStorage.FILTER_BY_ME, getString(R.string.MirrlyDeletedFilterByMe));

        filterChipsScroll.addView(filterChipsRow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)));
        filterChipsContainer.addView(filterChipsScroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.CENTER_VERTICAL));
        contentView.addView(filterChipsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.TOP, 8, 0, 8, 0));

        filterBanner = new FrameLayout(context);
        filterBanner.setVisibility(View.GONE);
        filterBanner.setAlpha(0f);
        filterBanner.setPadding(dp(4), dp(4), dp(4), dp(4));
        filterBanner.setOutlineProvider(ViewOutlineProviderImpl.boundsWithPaddingRoundRect(dp(4), dp(16)));
        filterBanner.setClipToOutline(true);
        filterBanner.setBackground(glassBackgroundDrawableFactory.create(filterBanner)
                .setColorProvider(BlurredBackgroundProviderImpl.topPanelChatActivity(resourceProvider))
                .setRadius(dp(16))
                .setPadding(dp(4)));

        filterBannerText = new TextView(context);
        filterBannerText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        filterBannerText.setTypeface(AndroidUtilities.bold());
        ThemeBind.textColor(filterBannerText, Theme.key_windowBackgroundWhiteBlackText, resourceProvider);
        filterBanner.addView(
                filterBannerText,
                LayoutHelper.createFrame(
                        LayoutHelper.WRAP_CONTENT,
                        LayoutHelper.WRAP_CONTENT,
                        Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT),
                        LocaleController.isRTL ? 34 : 12,
                        5,
                        LocaleController.isRTL ? 12 : 34,
                        5
                )
        );

        filterBannerClear = new ImageView(context);
        filterBannerClear.setScaleType(ImageView.ScaleType.CENTER);
        filterBannerClear.setImageResource(R.drawable.msg_close);
        ThemeBind.colorFilter(filterBannerClear, Theme.key_chat_topPanelClose, resourceProvider, PorterDuff.Mode.MULTIPLY);
        filterBanner.addView(
                filterBannerClear,
                LayoutHelper.createFrame(
                        28,
                        28,
                        Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT),
                        LocaleController.isRTL ? 4 : 0,
                        0,
                        LocaleController.isRTL ? 0 : 4,
                        0
                )
        );

        filterBanner.setOnClickListener(v -> {
            activeFilter = MirrlyDeletedStorage.FILTER_ALL;
            updateFilterBanner(true);
            refreshFilterChipsSelection();
            reloadRecords(true);
        });
        contentView.addView(filterBanner, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 16, 0, 16, 0));
    }

    private void addFilterChip(Context context, int filterId, String label) {
        FilterChipView chip = new FilterChipView(context, filterId, label);
        ScaleStateListAnimator.apply(chip, 0.04f, 1.2f);
        MirrlyUiKit.attachDisallowInterceptTouch(chip);
        chip.setOnClickListener(v -> {
            activeFilter = filterId;
            refreshFilterChipsSelection();
            updateFilterBanner(true);
            reloadRecords(true);
            updateSearchBottomBarState();
        });
        filterChipViews.add(chip);
        filterChipsRow.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 32, 3, 2, 3, 2));
    }

    private final class FilterChipView extends TextView {
        final int filterId;

        FilterChipView(Context context, int filterId, String label) {
            super(context);
            this.filterId = filterId;
            setText(label);
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            setTypeface(AndroidUtilities.bold());
            setGravity(Gravity.CENTER);
            setPadding(dp(12), 0, dp(12), 0);
            setSingleLine(true);
            updateVisual(filterId == activeFilter);
        }

        void updateVisual(boolean selected) {
            int bg;
            int border;
            int textColor;
            if (selected) {
                bg = Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider);
                if (Color.alpha(bg) == 0) {
                    bg = Theme.getColor(Theme.key_chat_messagePanelSend, resourceProvider);
                }
                textColor = ColorUtils.calculateLuminance(bg) > 0.45 ? 0xFF101318 : Color.WHITE;
                border = ColorUtils.setAlphaComponent(textColor == Color.WHITE ? Color.WHITE : Color.BLACK, 0x46);
            } else {
                int panelBg = Theme.getColor(Theme.key_chat_topPanelBackground, resourceProvider);
                boolean isDark = AndroidUtilities.computePerceivedBrightness(panelBg) < 0.5f;
                int primaryText = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider);
                bg = ColorUtils.setAlphaComponent(primaryText, isDark ? 0x1A : 0x12);
                border = ColorUtils.setAlphaComponent(isDark ? Color.WHITE : primaryText, isDark ? 0x36 : 0x26);
                textColor = ColorUtils.setAlphaComponent(primaryText, 0xE6);
            }
            setTextColor(textColor);
            setBackground(org.telegram.ui.MirrlyUiKit.createTintedSurfaceDrawable(16, bg, border, resourceProvider, true));
        }
    }

    private void refreshFilterChipsSelection() {
        for (int i = 0; i < filterChipViews.size(); i++) {
            FilterChipView chip = filterChipViews.get(i);
            chip.updateVisual(chip.filterId == activeFilter);
        }
    }

    private void buildScrollDownButton(Context context) {
        scrollDownButton = ChatActivityBlurredRoundPageDownButton.create(
                context,
                56, 48,
                resourceProvider,
                glassBackgroundDrawableFactory,
                BlurredBackgroundProviderImpl.bottomPanelChatActivity(resourceProvider),
                R.drawable.pagedown
        );
        scrollDownButton.setVisibility(View.GONE);
        scrollDownButton.setAlpha(0f);
        scrollDownButton.setScaleX(0.6f);
        scrollDownButton.setScaleY(0.6f);
        scrollDownButton.setPivotX(dp(28));
        scrollDownButton.setPivotY(dp(36));
        scrollDownButton.setContentDescription(getString(R.string.AccDescrPageDown));
        scrollDownButton.setOnClickListener(v -> {
            if (chatListView != null && !items.isEmpty()) {
                chatListView.smoothScrollToPosition(0);
            }
        });
        contentView.addView(scrollDownButton, LayoutHelper.createFrame(56, 64, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 10, 0));
    }

    private void buildBottomBars(Context context) {
        bottomBarContainer = new FrameLayout(context);
        bottomBarContainer.setVisibility(View.GONE);
        bottomBarContainer.setPadding(dp(4), dp(4), dp(4), dp(4));
        bottomBarContainer.setOutlineProvider(ViewOutlineProviderImpl.boundsWithPaddingRoundRect(dp(4), dp(24)));
        bottomBarContainer.setClipToOutline(true);
        bottomBarContainer.setBackground(glassBackgroundDrawableFactory.create(bottomBarContainer)
                .setColorProvider(BlurredBackgroundProviderImpl.bottomPanelChatActivity(resourceProvider))
                .setRadius(dp(24))
                .setPadding(dp(4)));

        searchBottomBar = new LinearLayout(context);
        searchBottomBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBottomBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBottomBar.setPadding(dp(14), 0, dp(6), 0);
        searchBottomBar.setVisibility(View.GONE);

        searchBottomCountView = new TextView(context);
        searchBottomCountView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        searchBottomCountView.setTypeface(AndroidUtilities.bold());
        searchBottomCountView.setTextColor(Theme.getColor(Theme.key_chat_messagePanelText, resourceProvider));
        searchBottomBar.addView(searchBottomCountView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        searchPrevBtn = new ImageView(context);
        searchPrevBtn.setScaleType(ImageView.ScaleType.CENTER);
        searchPrevBtn.setImageResource(R.drawable.search_arrow);
        searchPrevBtn.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons, resourceProvider), PorterDuff.Mode.MULTIPLY));
        searchPrevBtn.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourceProvider), 1));
        searchPrevBtn.setOnClickListener(v -> stepSearchResult(+1));
        searchBottomBar.addView(searchPrevBtn, LayoutHelper.createLinear(44, 44));

        searchNextBtn = new ImageView(context);
        searchNextBtn.setScaleType(ImageView.ScaleType.CENTER);
        searchNextBtn.setImageResource(R.drawable.search_arrow);
        searchNextBtn.setRotation(180f);
        searchNextBtn.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons, resourceProvider), PorterDuff.Mode.MULTIPLY));
        searchNextBtn.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourceProvider), 1));
        searchNextBtn.setOnClickListener(v -> stepSearchResult(-1));
        searchBottomBar.addView(searchNextBtn, LayoutHelper.createLinear(44, 44));

        bottomBarContainer.addView(searchBottomBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));

        actionModeBottomBar = new LinearLayout(context);
        actionModeBottomBar.setOrientation(LinearLayout.HORIZONTAL);
        actionModeBottomBar.setGravity(Gravity.CENTER_VERTICAL);
        actionModeBottomBar.setPadding(dp(16), 0, dp(10), 0);
        actionModeBottomBar.setVisibility(View.GONE);

        actionModeCountView = new TextView(context);
        actionModeCountView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        actionModeCountView.setTypeface(AndroidUtilities.bold());
        actionModeCountView.setTextColor(Theme.getColor(Theme.key_chat_messagePanelText, resourceProvider));
        actionModeBottomBar.addView(actionModeCountView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        actionModeForwardBtn = new TextView(context);
        actionModeForwardBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        actionModeForwardBtn.setTypeface(AndroidUtilities.bold());
        actionModeForwardBtn.setGravity(Gravity.CENTER);
        actionModeForwardBtn.setTextColor(Theme.getColor(Theme.key_chat_messagePanelSend, resourceProvider));
        actionModeForwardBtn.setText(getString(R.string.Forward));
        actionModeForwardBtn.setPadding(dp(12), dp(8), dp(12), dp(8));
        actionModeForwardBtn.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourceProvider), 2));
        actionModeForwardBtn.setOnClickListener(v -> forwardSelectedRecords());
        actionModeBottomBar.addView(actionModeForwardBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 6, 0));

        actionModeDeleteBtn = new TextView(context);
        actionModeDeleteBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        actionModeDeleteBtn.setTypeface(AndroidUtilities.bold());
        actionModeDeleteBtn.setGravity(Gravity.CENTER);
        actionModeDeleteBtn.setTextColor(Theme.getColor(Theme.key_text_RedRegular, resourceProvider));
        actionModeDeleteBtn.setText(getString(R.string.Delete));
        actionModeDeleteBtn.setPadding(dp(12), dp(8), dp(12), dp(8));
        actionModeDeleteBtn.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourceProvider), 2));
        actionModeDeleteBtn.setOnClickListener(v -> confirmDeleteSelectedRecords());
        actionModeBottomBar.addView(actionModeDeleteBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        bottomBarContainer.addView(actionModeBottomBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));
        contentView.addView(bottomBarContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.BOTTOM, 10, 0, 10, 10));
    }

    private float getBottomBarOccupiedHeight() {
        if (bottomBarContainer != null && bottomBarContainer.getVisibility() == View.VISIBLE) {
            return dp(66);
        }
        return dp(8);
    }

    private void updateBottomModeBars() {
        if (bottomBarContainer == null || searchBottomBar == null || actionModeBottomBar == null) {
            return;
        }
        boolean actionMode = actionBar != null && actionBar.isActionModeShowed();
        if (actionMode) {
            bottomBarContainer.setVisibility(View.VISIBLE);
            searchBottomBar.setVisibility(View.GONE);
            actionModeBottomBar.setVisibility(View.VISIBLE);
            actionModeCountView.setText(LocaleController.formatString(R.string.MirrlyNotesSelectedCount, selectedUids.size()));
        } else if (isSearching) {
            bottomBarContainer.setVisibility(View.VISIBLE);
            actionModeBottomBar.setVisibility(View.GONE);
            searchBottomBar.setVisibility(View.VISIBLE);
            updateSearchBottomBarState();
        } else {
            searchBottomBar.setVisibility(View.GONE);
            actionModeBottomBar.setVisibility(View.GONE);
            bottomBarContainer.setVisibility(View.GONE);
        }
        bottomBarContainer.setTranslationY(-windowInsetsStateHolder.getAnimatedMaxBottomInset());
        updateListPaddingAndButtons();
        updateEmptyContainerPosition();
    }

    private void updateSearchBottomBarState() {
        if (searchBottomCountView == null) {
            return;
        }
        int matches = 0;
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).isDate()) {
                matches++;
            }
        }
        if (matches == 0) {
            searchBottomCountView.setText(getString(R.string.MirrlyNotesSearchNoMatches));
            searchPrevBtn.setEnabled(false);
            searchNextBtn.setEnabled(false);
            searchPrevBtn.setAlpha(0.35f);
            searchNextBtn.setAlpha(0.35f);
        } else {
            searchBottomCountView.setText(LocaleController.formatString(R.string.MirrlyNotesSearchFoundCount, matches));
            searchPrevBtn.setEnabled(true);
            searchNextBtn.setEnabled(true);
            searchPrevBtn.setAlpha(1f);
            searchNextBtn.setAlpha(1f);
        }
    }

    private void stepSearchResult(int delta) {
        if (items.isEmpty() || chatListView == null) {
            return;
        }
        ArrayList<Integer> positions = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).isDate()) {
                positions.add(i);
            }
        }
        if (positions.isEmpty()) {
            return;
        }
        if (currentSearchNavPos < 0 || currentSearchNavPos >= positions.size()) {
            currentSearchNavPos = delta > 0 ? 0 : positions.size() - 1;
        } else {
            currentSearchNavPos = (currentSearchNavPos + delta + positions.size()) % positions.size();
        }
        int targetAdapterPos = positions.get(currentSearchNavPos);
        chatListView.smoothScrollToPosition(targetAdapterPos);
    }

    @Nullable
    private TLObject resolveCurrentPeerObject() {
        if (filterDialogId == 0) {
            return null;
        }
        MessagesController mc = MessagesController.getInstance(currentAccount);
        TLObject peerObj = null;
        if (DialogObject.isEncryptedDialog(filterDialogId)) {
            TLRPC.EncryptedChat ec = mc.getEncryptedChat(DialogObject.getEncryptedChatId(filterDialogId));
            if (ec == null && chatSummary != null) {
                ec = MirrlyDeletedChatsStorage.deserializeEncryptedChat(chatSummary.encChatTlBlob);
            }
            if (ec != null && ec.user_id != 0) {
                peerObj = mc.getUser(ec.user_id);
            }
            if (peerObj == null && chatSummary != null) {
                peerObj = MirrlyDeletedChatsStorage.deserializeUser(chatSummary.peerTlBlob);
            }
        } else if (DialogObject.isUserDialog(filterDialogId)) {
            peerObj = mc.getUser(filterDialogId);
            if (peerObj == null && chatSummary != null) {
                peerObj = MirrlyDeletedChatsStorage.deserializeUser(chatSummary.peerTlBlob);
            }
        } else if (DialogObject.isChatDialog(filterDialogId)) {
            peerObj = mc.getChat(-filterDialogId);
            if (peerObj == null && chatSummary != null) {
                peerObj = MirrlyDeletedChatsStorage.deserializeChat(chatSummary.peerTlBlob);
            }
        }
        return peerObj;
    }

    private String resolveCurrentChatTitle(@Nullable TLObject peerObj) {
        if (peerObj instanceof TLRPC.User) {
            TLRPC.User user = (TLRPC.User) peerObj;
            if (user.self) {
                return getString(R.string.SavedMessages);
            }
            String name = UserObject.getUserName(user);
            if (!TextUtils.isEmpty(name)) {
                return name;
            }
        } else if (peerObj instanceof TLRPC.Chat) {
            TLRPC.Chat chat = (TLRPC.Chat) peerObj;
            if (!TextUtils.isEmpty(chat.title)) {
                return chat.title;
            }
        }
        if (chatSummary != null && !TextUtils.isEmpty(chatSummary.title)) {
            return chatSummary.title;
        }
        return getString(isArchivedChatMode ? R.string.MirrlyDeletedChatsTitle : R.string.MirrlyDeletedTitle);
    }

    private void openPeerProfile() {
        if (filterDialogId == 0 || isArchivedChatMode) {
            return;
        }
        Bundle args = new Bundle();
        if (DialogObject.isEncryptedDialog(filterDialogId)) {
            TLRPC.EncryptedChat ec = MessagesController.getInstance(currentAccount).getEncryptedChat(DialogObject.getEncryptedChatId(filterDialogId));
            if (ec == null) {
                return;
            }
            args.putLong("user_id", ec.user_id);
            args.putInt("enc_id", ec.id);
        } else if (DialogObject.isUserDialog(filterDialogId)) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(filterDialogId);
            if (user == null || user.self) {
                return;
            }
            args.putLong("user_id", filterDialogId);
        } else if (DialogObject.isChatDialog(filterDialogId)) {
            args.putLong("chat_id", -filterDialogId);
        } else {
            return;
        }
        presentFragment(new ProfileActivity(args));
    }

    private void bindDeletedChatAvatarAndTitle() {
        if (avatarContainer == null) {
            return;
        }
        TLObject peerObj = resolveCurrentPeerObject();
        String title = resolveCurrentChatTitle(peerObj);
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        if (peerObj instanceof TLRPC.User) {
            TLRPC.User user = (TLRPC.User) peerObj;
            if (user.self) {
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                avatarContainer.getAvatarImageView().setImageDrawable(avatarDrawable);
            } else {
                avatarDrawable.setInfo(currentAccount, user);
            }
        } else if (peerObj instanceof TLRPC.Chat) {
            avatarDrawable.setInfo(currentAccount, (TLRPC.Chat) peerObj);
        } else {
            avatarDrawable.setInfo(filterDialogId, title, null);
        }
        if (!(peerObj instanceof TLRPC.User && ((TLRPC.User) peerObj).self)) {
            String localAvatar = chatSummary != null ? chatSummary.localAvatarPath : null;
            if (ImageLocation.getForUserOrChat(peerObj, ImageLocation.TYPE_SMALL) == null
                    && !TextUtils.isEmpty(localAvatar)
                    && new File(localAvatar).exists()) {
                avatarContainer.getAvatarImageView().setImage(ImageLocation.getForPath(localAvatar), "50_50", avatarDrawable, peerObj);
            } else {
                avatarContainer.getAvatarImageView().setForUserOrChat(peerObj, avatarDrawable);
            }
        }
        avatarContainer.setTitle(title);
        boolean canOpenProfile = !isArchivedChatMode && peerObj != null
                && !(peerObj instanceof TLRPC.User && ((TLRPC.User) peerObj).self);
        avatarContainer.setEnabled(canOpenProfile);
        avatarContainer.setOnClickListener(canOpenProfile ? v -> openPeerProfile() : null);
    }

    private void reloadRecords(boolean animated) {
        List<MirrlyDeletedStorage.DeletedRecord> raw;
        if (filterDialogId != 0) {
            raw = isArchivedChatMode
                    ? MirrlyDeletedChatsStorage.getInstance(currentAccount).getChatMessagesAsDeletedRecords(filterDialogId, searchQuery, activeFilter)
                    : MirrlyDeletedStorage.getInstance(currentAccount).getRecordsForDialog(filterDialogId, searchQuery, activeFilter);
        } else {
            raw = MirrlyDeletedStorage.getInstance(currentAccount).searchRecords(searchQuery, activeFilter);
        }
        items.clear();
        groupedMessagesMap.clear();
        long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
        Calendar cal = Calendar.getInstance();

        if (filterDialogId != 0) {
            // Single-chat archive comes back chronological (oldest -> newest); reverseLayout puts index 0 at the bottom.
            for (int i = raw.size() - 1; i >= 0; i--) {
                MirrlyDeletedStorage.DeletedRecord rec = raw.get(i);
                MessageObject msgObj = buildSyntheticMessageObject(rec, selfId);
                if (msgObj.hasValidGroupId()) {
                    MessageObject.GroupedMessages group = groupedMessagesMap.get(msgObj.getGroupId());
                    if (group == null) {
                        group = new MessageObject.GroupedMessages();
                        group.reversed = true;
                        group.groupId = msgObj.getGroupId();
                        groupedMessagesMap.put(group.groupId, group);
                    }
                    group.messages.add(msgObj);
                }
                items.add(new DeletedItem(msgObj, rec));

                int ts = rec.sendDate > 0 ? rec.sendDate : rec.deletedAt;
                int dayKey = getDayKey(cal, ts);
                int prevTs = (i - 1 >= 0) ? (raw.get(i - 1).sendDate > 0 ? raw.get(i - 1).sendDate : raw.get(i - 1).deletedAt) : -1;
                int prevDayKey = (i - 1 >= 0) ? getDayKey(cal, prevTs) : -1;
                if (i == 0 || dayKey != prevDayKey) {
                    items.add(new DeletedItem(buildDateMessageObject(ts, dayKey), null));
                }
            }
            for (MessageObject.GroupedMessages group : groupedMessagesMap.values()) {
                group.calculate();
            }
        } else {
            for (int i = 0; i < raw.size(); i++) {
                MirrlyDeletedStorage.DeletedRecord rec = raw.get(i);
                MessageObject msgObj = buildSyntheticMessageObject(rec, selfId);
                items.add(new DeletedItem(msgObj, rec));

                int dayKey = getDayKey(cal, rec.deletedAt);
                int nextDayKey = (i + 1 < raw.size()) ? getDayKey(cal, raw.get(i + 1).deletedAt) : -1;
                if (i == raw.size() - 1 || dayKey != nextDayKey) {
                    items.add(new DeletedItem(buildDateMessageObject(rec.deletedAt, dayKey), null));
                }
            }
        }

        updateEmptyPlaceholder();
        updateHeaderSubtitle();
        if (isSearching) {
            updateSearchBottomBarState();
        }
        if (deletedAdapter != null) {
            deletedAdapter.notifyDataSetChanged();
        }
        if (chatListView != null && !animated && !items.isEmpty()) {
            chatLayoutManager.scrollToPositionWithOffset(0, 0);
        }
    }

    private int getDayKey(Calendar cal, int timestampSec) {
        cal.setTimeInMillis(timestampSec * 1000L);
        return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR);
    }

    private MessageObject buildDateMessageObject(int timestampSec, int dayKey) {
        TLRPC.TL_message dateMsg = new TLRPC.TL_message();
        dateMsg.message = LocaleController.formatDateChat(timestampSec);
        dateMsg.id = -dayKey;
        dateMsg.date = timestampSec;
        MessageObject dateObj = new MessageObject(currentAccount, dateMsg, false, false);
        dateObj.type = MessageObject.TYPE_DATE;
        dateObj.contentType = 1;
        dateObj.isDateObject = true;
        dateObj.suppressMirrlyNoteBadge = true;
        dateObj.suppressMirrlyDeletedVisuals = true;
        return dateObj;
    }

    private MessageObject buildSyntheticMessageObject(MirrlyDeletedStorage.DeletedRecord rec, long selfId) {
        Integer stableId = stableMessageIdMap.get(rec.uid);
        if (stableId == null) {
            stableId = nextSyntheticId.getAndDecrement();
            stableMessageIdMap.put(rec.uid, stableId);
        }

        TLRPC.Message msg = rec.decodeMessage(currentAccount);
        if (msg == null) {
            TLRPC.TL_message fallback = new TLRPC.TL_message();
            fallback.date = rec.deletedAt > 0 ? rec.deletedAt : (int) (System.currentTimeMillis() / 1000L);
            fallback.message = rec.text != null ? rec.text : "";
            fallback.media = new TLRPC.TL_messageMediaEmpty();
            fallback.peer_id = new TLRPC.TL_peerUser();
            fallback.peer_id.user_id = selfId;
            msg = fallback;
        }

        msg.id = stableId;
        msg.dialog_id = filterDialogId != 0 ? filterDialogId : selfId;
        if (filterDialogId != 0) {
            if (rec.sendDate > 0) {
                msg.date = rec.sendDate;
            }
        } else if (rec.deletedAt > 0) {
            msg.date = rec.deletedAt;
        }

        if (filterDialogId == 0 && msg.fwd_from == null) {
            String header = !TextUtils.isEmpty(rec.peerTitle) ? rec.peerTitle : "";
            if (!TextUtils.isEmpty(rec.senderName) && !TextUtils.equals(rec.senderName, rec.peerTitle)) {
                header = TextUtils.isEmpty(header) ? rec.senderName : (rec.senderName + " • " + header);
            }
            if (rec.ephemeral) {
                header = TextUtils.isEmpty(header)
                        ? getString(R.string.MirrlyDeletedTagEphemeralShort)
                        : (header + " • " + getString(R.string.MirrlyDeletedTagEphemeralShort));
            }
            if (rec.deletedByMe) {
                header = TextUtils.isEmpty(header)
                        ? getString(R.string.MirrlyDeletedTagByMeShort)
                        : (header + " • " + getString(R.string.MirrlyDeletedTagByMeShort));
            }
            if (!TextUtils.isEmpty(header)) {
                TLRPC.TL_messageFwdHeader fwd = new TLRPC.TL_messageFwdHeader();
                fwd.from_name = header;
                fwd.date = rec.sendDate > 0 ? rec.sendDate : rec.deletedAt;
                msg.fwd_from = fwd;
                msg.flags |= TLRPC.MESSAGE_FLAG_FWD;
            }
        }

        MessageObject obj = new MessageObject(currentAccount, msg, false, false);
        obj.suppressMirrlyNoteBadge = true;
        obj.suppressMirrlyDeletedVisuals = filterDialogId == 0 || !isArchivedChatMode || !rec.deletedByMe;
        obj.checkMediaExistance();
        obj.generateThumbs(false);
        return obj;
    }

    private void updateHeaderSubtitle() {
        if (avatarContainer == null) {
            return;
        }
        if (filterDialogId != 0) {
            if (!isArchivedChatMode) {
                int msgTotal = MirrlyDeletedStorage.getInstance(currentAccount).getCountForDialog(filterDialogId);
                String filterLabel = activeFilter != MirrlyDeletedStorage.FILTER_ALL ? getFilterTitle(activeFilter) : null;
                if (msgTotal > 0) {
                    String base = LocaleController.formatPluralString("messages", msgTotal);
                    avatarContainer.setSubtitle((filterLabel != null ? filterLabel : getString(R.string.MirrlyDeletedTitle)) + " • " + base);
                } else {
                    avatarContainer.setSubtitle(getString(MirrlyConfig.isSaveDeletedMessages() ? R.string.MirrlyDeletedTitle : R.string.MirrlyDeletedStatusOff));
                }
                return;
            }
            MirrlyDeletedChatsStorage.DeletedChatSummary latest = MirrlyDeletedChatsStorage.getInstance(currentAccount).getChatSummary(filterDialogId);
            if (latest != null) {
                chatSummary = latest;
            }
            int msgTotal = chatSummary != null ? chatSummary.msgCount : 0;
            String base = msgTotal > 0
                    ? LocaleController.formatPluralString("messages", msgTotal)
                    : getString(R.string.MirrlyDeletedChatNoCachedMessages);
            String filterLabel = activeFilter != MirrlyDeletedStorage.FILTER_ALL ? getFilterTitle(activeFilter) : null;
            if (filterLabel != null && msgTotal > 0) {
                avatarContainer.setSubtitle(filterLabel + " • " + base);
            } else {
                avatarContainer.setSubtitle(getString(R.string.MirrlyDeletedChatTagShort) + " • " + base);
            }
            return;
        }
        int total = MirrlyDeletedStorage.getInstance(currentAccount).getCount();
        String base = total > 0
                ? LocaleController.formatPluralString("messages", total)
                : getString(MirrlyConfig.isSaveDeletedMessages() ? R.string.MirrlyVaultCountEmpty : R.string.MirrlyDeletedStatusOff);
        String filterLabel = activeFilter != MirrlyDeletedStorage.FILTER_ALL ? getFilterTitle(activeFilter) : null;
        if (filterLabel != null && total > 0) {
            avatarContainer.setSubtitle(filterLabel + " • " + base);
        } else {
            avatarContainer.setSubtitle(base);
        }
    }

    private String getFilterTitle(int filter) {
        switch (filter) {
            case MirrlyDeletedStorage.FILTER_TEXT:
                return getString(R.string.MirrlyDeletedFilterText);
            case MirrlyDeletedStorage.FILTER_MEDIA:
                return getString(R.string.MirrlyDeletedFilterMedia);
            case MirrlyDeletedStorage.FILTER_VOICE_ROUND:
                return getString(R.string.MirrlyDeletedFilterVoice);
            case MirrlyDeletedStorage.FILTER_FILES:
                return getString(R.string.MirrlyDeletedFilterFiles);
            case MirrlyDeletedStorage.FILTER_EPHEMERAL:
                return getString(R.string.MirrlyDeletedFilterEphemeral);
            case MirrlyDeletedStorage.FILTER_BY_ME:
                return getString(R.string.MirrlyDeletedFilterByMe);
            default:
                return getString(R.string.MirrlyDeletedFilterAll);
        }
    }

    private void updateEmptyPlaceholder() {
        if (emptyTitleView == null || emptySubtitleView == null) {
            return;
        }
        boolean showEnableCta = false;
        if (!TextUtils.isEmpty(searchQuery)) {
            emptyTitleView.setText(getString(R.string.MirrlyNotesEmptySearchTitle));
            emptySubtitleView.setText(LocaleController.formatString(R.string.MirrlyNotesEmptySearchSub, searchQuery.trim()));
        } else if (activeFilter != MirrlyDeletedStorage.FILTER_ALL) {
            emptyTitleView.setText(getString(R.string.MirrlyNotesEmptyFilterTitle));
            emptySubtitleView.setText(getString(R.string.MirrlyDeletedEmptyFilterSub));
        } else if (filterDialogId != 0) {
            if (!isArchivedChatMode && !MirrlyConfig.isSaveDeletedMessages()) {
                emptyTitleView.setText(getString(R.string.MirrlyDeletedEmptyDisabledTitle));
                emptySubtitleView.setText(getString(R.string.MirrlyDeletedEmptyDisabledSub));
                showEnableCta = true;
            } else if (!isArchivedChatMode) {
                emptyTitleView.setText(resolveCurrentChatTitle(resolveCurrentPeerObject()));
                emptySubtitleView.setText(getString(R.string.MirrlyChatDeletedEmptySub));
            } else {
                emptyTitleView.setText(chatSummary != null && !TextUtils.isEmpty(chatSummary.title) ? chatSummary.title : getString(R.string.MirrlyDeletedChatsTitle));
                emptySubtitleView.setText(getString(R.string.MirrlyDeletedChatEmptyMessagesSub));
            }
        } else if (!MirrlyConfig.isSaveDeletedMessages()) {
            emptyTitleView.setText(getString(R.string.MirrlyDeletedEmptyDisabledTitle));
            emptySubtitleView.setText(getString(R.string.MirrlyDeletedEmptyDisabledSub));
            showEnableCta = true;
        } else {
            emptyTitleView.setText(getString(R.string.MirrlyDeletedTitle));
            emptySubtitleView.setText(getString(R.string.MirrlyDeletedEmptyDefaultSub));
        }
        if (emptyEnableButton != null) {
            emptyEnableButton.setVisibility(showEnableCta ? View.VISIBLE : View.GONE);
        }
        if (emptyViewContainer != null) {
            emptyViewContainer.setVisibility(items.isEmpty() ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void updateFilterChipsVisibility(boolean animated) {
        if (filterChipsContainer == null) {
            return;
        }
        refreshFilterChipsSelection();
        boolean show = isSearching || filterChipsToggleOpen;
        if (show) {
            if (filterChipsContainer.getVisibility() != View.VISIBLE) {
                filterChipsContainer.setVisibility(View.VISIBLE);
                filterChipsContainer.setAlpha(0f);
                updateTopOverlayPositions();
                updateListPaddingAndButtons();
                if (animated) {
                    filterChipsContainer.animate().alpha(1f).setDuration(200).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
                } else {
                    filterChipsContainer.setAlpha(1f);
                }
            }
        } else if (filterChipsContainer.getVisibility() == View.VISIBLE) {
            if (animated) {
                filterChipsContainer.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                    filterChipsContainer.setVisibility(View.GONE);
                    updateTopOverlayPositions();
                    updateListPaddingAndButtons();
                }).start();
            } else {
                filterChipsContainer.setVisibility(View.GONE);
                updateTopOverlayPositions();
                updateListPaddingAndButtons();
            }
        }
    }

    private void updateFilterBanner(boolean animated) {
        if (filterBanner == null || filterBannerText == null) {
            return;
        }
        boolean show = activeFilter != MirrlyDeletedStorage.FILTER_ALL && !isSearching && !filterChipsToggleOpen;
        if (show) {
            filterBannerText.setText(getFilterTitle(activeFilter));
            if (filterBanner.getVisibility() != View.VISIBLE) {
                filterBanner.setVisibility(View.VISIBLE);
                filterBanner.setAlpha(0f);
                updateTopOverlayPositions();
                updateListPaddingAndButtons();
                if (animated) {
                    filterBanner.animate().alpha(1f).setDuration(200).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
                } else {
                    filterBanner.setAlpha(1f);
                }
            }
        } else if (filterBanner.getVisibility() == View.VISIBLE) {
            if (animated) {
                filterBanner.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                    filterBanner.setVisibility(View.GONE);
                    updateTopOverlayPositions();
                    updateListPaddingAndButtons();
                }).start();
            } else {
                filterBanner.setVisibility(View.GONE);
                updateTopOverlayPositions();
                updateListPaddingAndButtons();
            }
        }
    }

    private void showDeletedItemOptions(ChatMessageCell cell) {
        if (cell == null || getParentActivity() == null) {
            return;
        }
        MessageObject msgObj = cell.getMessageObject();
        MirrlyDeletedStorage.DeletedRecord rec = findRecordForMessageObject(msgObj);
        if (rec == null) {
            return;
        }

        ItemOptions options = ItemOptions.makeOptions(this, cell)
                .setBlur(true, false)
                .setBlurBackground(glassBackgroundDrawableFactory, BlurredBackgroundProviderImpl.messageMenuBackground(resourceProvider), false)
                .setGravity(Gravity.RIGHT);

        options.addIf(filterDialogId == 0 && rec.dialogId != 0, R.drawable.msg_viewintopic, getString(R.string.MirrlyNotesGoToMessage), () -> openSourceChat(rec));
        options.addIf(
                rec.dialogId != 0 && rec.msgId != 0 && MirrlyVaultStorage.hasEditHistory(rec.dialogId, rec.msgId),
                R.drawable.msg_recent,
                getString(R.string.MirrlyChatMenuEditHistory),
                () -> MirrlyVaultSheets.showEditHistorySheet(this, rec.dialogId, rec.msgId, rec.text)
        );
        options.addIf(!TextUtils.isEmpty(rec.text), R.drawable.msg_copy, getString(R.string.Copy), () -> {
            if (AndroidUtilities.addToClipboard(rec.text)) {
                BulletinFactory.of(this).createCopyBulletin(getString(R.string.TextCopied)).show();
            }
        });
        options.addIf(rec.hasLocalFile(), R.drawable.msg_gallery, getString(R.string.MirrlyDeletedSaveToDownloads), () -> exportRecordToPublicMirrlyGram(rec));
        options.add(R.drawable.msg_forward, getString(R.string.MirrlyNotesForwardToChat), () -> {
            ArrayList<MirrlyDeletedStorage.DeletedRecord> single = new ArrayList<>(1);
            single.add(rec);
            openForwardPicker(single);
        });
        options.add(R.drawable.msg_select, getString(R.string.MirrlyNotesSelect), () -> enterActionMode(rec));
        options.addGap();
        options.add(R.drawable.msg_delete, getString(R.string.Delete), true, () -> deleteRecordWithEffect(cell, rec));
        options.show();
    }

    private void exportRecordToPublicMirrlyGram(@NonNull MirrlyDeletedStorage.DeletedRecord rec) {
        if (getParentActivity() == null || !rec.hasLocalFile()) {
            return;
        }
        int saveType = 2;
        if (MirrlyDeletedStorage.MEDIA_PHOTO.equals(rec.mediaKind)) {
            saveType = 0;
        } else if (MirrlyDeletedStorage.MEDIA_VIDEO.equals(rec.mediaKind) || MirrlyDeletedStorage.MEDIA_ROUND.equals(rec.mediaKind) || MirrlyDeletedStorage.MEDIA_GIF.equals(rec.mediaKind)) {
            saveType = 1;
        } else if (MirrlyDeletedStorage.MEDIA_VOICE.equals(rec.mediaKind) || MirrlyDeletedStorage.MEDIA_AUDIO.equals(rec.mediaKind)) {
            saveType = 3;
        }
        File src = new File(rec.localMediaPath);
        String name = !TextUtils.isEmpty(rec.fileName) ? rec.fileName : src.getName();
        String mime = !TextUtils.isEmpty(rec.mimeType) ? rec.mimeType : "application/octet-stream";
        MediaController.saveFile(rec.localMediaPath, getParentActivity(), saveType, name, mime, uri -> {
            BulletinFactory.of(this).createSimpleBulletin(
                    R.raw.info,
                    getString(uri != null ? R.string.MirrlyDeletedSavedToDownloadsToast : R.string.ErrorOccurred)
            ).show();
        }, true);
    }

    private void openSourceChat(@NonNull MirrlyDeletedStorage.DeletedRecord rec) {
        if (rec.dialogId == 0) {
            return;
        }
        Bundle args = new Bundle();
        long dialogId = rec.dialogId;
        if (DialogObject.isEncryptedDialog(dialogId)) {
            args.putInt("enc_id", DialogObject.getEncryptedChatId(dialogId));
        } else if (dialogId >= 0) {
            args.putLong("user_id", dialogId);
        } else {
            args.putLong("chat_id", -dialogId);
        }
        if (rec.msgId != 0) {
            args.putInt("message_id", rec.msgId);
        }
        presentFragment(new ChatActivity(args));
    }

    private void deleteRecordWithEffect(@Nullable ChatMessageCell cell, @NonNull MirrlyDeletedStorage.DeletedRecord rec) {
        Runnable performDelete = () -> {
            if (filterDialogId != 0 && isArchivedChatMode) {
                MirrlyDeletedChatsStorage.getInstance(currentAccount).deleteMessagesByUids(filterDialogId, Collections.singletonList(rec.uid));
                reloadRecords(true);
                BulletinFactory.of(this).createSimpleBulletin(R.raw.ic_delete, getString(R.string.MirrlyDeletedRemovedToast)).show();
            } else {
                MirrlyDeletedStorage.getInstance(currentAccount).deleteRecordByUid(rec.uid);
                reloadRecords(true);
                BulletinFactory.of(this).createUndoBulletin(getString(R.string.MirrlyDeletedRemovedToast), () -> {
                    MirrlyDeletedStorage.getInstance(currentAccount).restoreRecord(rec);
                    reloadRecords(true);
                }, null).show();
            }
        };

        if (cell != null && cell.isAttachedToWindow() && ThanosEffect.supports() && SharedConfig.getDevicePerformanceClass() != SharedConfig.PERFORMANCE_CLASS_LOW) {
            ThanosEffect effect = ensureThanosEffect();
            if (effect != null) {
                effect.animate(cell, performDelete);
                return;
            }
        }
        performDelete.run();
    }

    @Nullable
    private ThanosEffect ensureThanosEffect() {
        if (thanosEffect == null || thanosEffect.destroyed) {
            if (getContext() == null || !ThanosEffect.supports() || chatListView == null || contentView == null) {
                return null;
            }
            if (thanosEffect != null) {
                AndroidUtilities.removeFromParent(thanosEffect);
            }
            final ThanosEffect[] holder = new ThanosEffect[1];
            ThanosEffect effect = new ThanosEffect(getContext(), () -> {
                if (holder[0] == null) {
                    return;
                }
                ThanosEffect self = holder[0];
                holder[0] = null;
                if (thanosEffect == self) {
                    thanosEffect = null;
                }
                AndroidUtilities.removeFromParent(self);
            });
            holder[0] = thanosEffect = effect;
            contentView.addView(effect, 1 + contentView.indexOfChild(chatListView), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
        return thanosEffect;
    }

    private void confirmClearAllRecords() {
        if (filterDialogId != 0 && isArchivedChatMode) {
            String chatTitle = chatSummary != null && !TextUtils.isEmpty(chatSummary.title) ? chatSummary.title : "";
            MirrlyUiKit.confirmDangerAction(
                    this,
                    resourceProvider,
                    getString(R.string.MirrlyDeletedChatsRemoveSingle),
                    LocaleController.formatString(R.string.MirrlyDeletedChatsRemoveSingleConfirm, chatTitle),
                    getString(R.string.Delete),
                    () -> {
                        MirrlyDeletedChatsStorage.getInstance(currentAccount).deleteChat(filterDialogId);
                        finishFragment();
                    }
            );
            return;
        }
        if (filterDialogId != 0) {
            int dialogCount = MirrlyDeletedStorage.getInstance(currentAccount).getCountForDialog(filterDialogId);
            if (dialogCount == 0) {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.MirrlyVaultClearDeletedEmpty)).show();
                return;
            }
            MirrlyUiKit.confirmDangerAction(
                    this,
                    resourceProvider,
                    getString(R.string.MirrlyChatDeletedClearAction),
                    getString(R.string.MirrlyChatDeletedClearConfirm),
                    getString(R.string.Delete),
                    () -> {
                        MirrlyDeletedStorage.getInstance(currentAccount).clearDialogRecords(filterDialogId);
                        reloadRecords(true);
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.ic_delete, getString(R.string.MirrlyVaultClearDeletedDone)).show();
                    }
            );
            return;
        }
        int count = MirrlyDeletedStorage.getInstance(currentAccount).getCount();
        if (count == 0) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.MirrlyVaultClearDeletedEmpty)).show();
            return;
        }
        MirrlyUiKit.confirmDangerAction(
                this,
                resourceProvider,
                getString(R.string.MirrlyVaultClearDeletedConfirmTitle),
                LocaleController.formatString(R.string.MirrlyVaultClearDeletedConfirmText, count),
                getString(R.string.Delete),
                () -> {
                    MirrlyDeletedStorage.getInstance(currentAccount).clearAll();
                    reloadRecords(true);
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.ic_delete, getString(R.string.MirrlyVaultClearDeletedDone)).show();
                }
        );
    }

    private void enterActionMode(@NonNull MirrlyDeletedStorage.DeletedRecord initial) {
        selectedUids.clear();
        selectedUids.add(initial.uid);
        if (actionBar != null && !actionBar.isActionModeShowed()) {
            actionBar.showActionMode();
            actionBar.setActionModeTopColor(Color.TRANSPARENT);
            ActionBarMenu actionMode = actionBar.createActionMode();
            actionMode.setBackgroundColor(Color.TRANSPARENT);
        }
        updateBottomModeBars();
        if (deletedAdapter != null) {
            deletedAdapter.notifyDataSetChanged();
        }
    }

    private void toggleSelection(@NonNull MirrlyDeletedStorage.DeletedRecord rec) {
        if (selectedUids.contains(rec.uid)) {
            selectedUids.remove(rec.uid);
        } else {
            selectedUids.add(rec.uid);
        }
        if (selectedUids.isEmpty()) {
            exitActionMode();
            return;
        }
        updateBottomModeBars();
        if (deletedAdapter != null) {
            deletedAdapter.notifyDataSetChanged();
        }
    }

    private void exitActionMode() {
        selectedUids.clear();
        if (actionBar != null && actionBar.isActionModeShowed()) {
            actionBar.hideActionMode();
        }
        updateBottomModeBars();
        if (deletedAdapter != null) {
            deletedAdapter.notifyDataSetChanged();
        }
    }

    private ArrayList<MirrlyDeletedStorage.DeletedRecord> collectSelectedRecords() {
        ArrayList<MirrlyDeletedStorage.DeletedRecord> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            DeletedItem item = items.get(i);
            if (!item.isDate() && item.record != null && selectedUids.contains(item.record.uid)) {
                out.add(item.record);
            }
        }
        return out;
    }

    private void confirmDeleteSelectedRecords() {
        ArrayList<MirrlyDeletedStorage.DeletedRecord> selected = collectSelectedRecords();
        if (selected.isEmpty() || getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), resourceProvider);
        builder.setTitle(getString(R.string.Delete));
        builder.setMessage(LocaleController.formatString(R.string.MirrlyDeletedDeleteSelectedConfirm, selected.size()));
        builder.setPositiveButton(getString(R.string.Delete), (d, w) -> {
            ArrayList<String> uids = new ArrayList<>(selected.size());
            for (int i = 0; i < selected.size(); i++) {
                uids.add(selected.get(i).uid);
            }
            if (filterDialogId != 0 && isArchivedChatMode) {
                MirrlyDeletedChatsStorage.getInstance(currentAccount).deleteMessagesByUids(filterDialogId, uids);
                exitActionMode();
                reloadRecords(true);
                BulletinFactory.of(this).createSimpleBulletin(R.raw.ic_delete, LocaleController.formatString(R.string.MirrlyDeletedRemovedManyToast, selected.size())).show();
            } else {
                MirrlyDeletedStorage.getInstance(currentAccount).deleteRecordsByUids(uids);
                exitActionMode();
                reloadRecords(true);
                BulletinFactory.of(this).createUndoBulletin(LocaleController.formatString(R.string.MirrlyDeletedRemovedManyToast, selected.size()), () -> {
                    for (int i = 0; i < selected.size(); i++) {
                        MirrlyDeletedStorage.getInstance(currentAccount).restoreRecord(selected.get(i));
                    }
                    reloadRecords(true);
                }, null).show();
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        TextView btn = (TextView) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (btn != null) {
            btn.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourceProvider));
        }
    }

    private void forwardSelectedRecords() {
        ArrayList<MirrlyDeletedStorage.DeletedRecord> selected = collectSelectedRecords();
        if (selected.isEmpty()) {
            return;
        }
        exitActionMode();
        openForwardPicker(selected);
    }

    private void openForwardPicker(ArrayList<MirrlyDeletedStorage.DeletedRecord> toForward) {
        if (toForward.isEmpty() || getParentActivity() == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        args.putInt("messagesCount", toForward.size());
        args.putBoolean("canSelectTopics", true);
        DialogsActivity fragment = new DialogsActivity(args);
        fragment.setDelegate((dialogsActivity, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            if (dids == null || dids.isEmpty()) {
                return true;
            }
            for (int dIdx = 0; dIdx < dids.size(); dIdx++) {
                long targetDialogId = dids.get(dIdx).dialogId;
                for (int rIdx = toForward.size() - 1; rIdx >= 0; rIdx--) {
                    sendRecordToTelegramChat(targetDialogId, toForward.get(rIdx));
                }
            }
            dialogsActivity.finishFragment();
            BulletinFactory.of(MirrlyDeletedChatActivity.this).createSimpleBulletin(R.raw.forward, getString(R.string.MirrlyNotesForwardedToast)).show();
            return true;
        });
        presentFragment(fragment);
    }

    private void sendRecordToTelegramChat(long targetDialogId, MirrlyDeletedStorage.DeletedRecord rec) {
        TLRPC.Message decoded = rec.decodeMessage(currentAccount);
        ArrayList<TLRPC.MessageEntity> entities = decoded != null ? decoded.entities : null;
        String text = !TextUtils.isEmpty(rec.text) ? rec.text : null;
        if (!rec.hasLocalFile()) {
            if (!TextUtils.isEmpty(text)) {
                SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(
                        text, targetDialogId, null, null, null, true, entities, null, null, true, 0, 0, null, false
                );
                SendMessagesHelper.getInstance(currentAccount).sendMessage(params);
            }
            return;
        }
        switch (rec.mediaKind) {
            case MirrlyDeletedStorage.MEDIA_PHOTO:
                SendMessagesHelper.prepareSendingPhoto(
                        getAccountInstance(), rec.localMediaPath, null, null, targetDialogId, null, null, null, null, entities, null, null, 0, null, null, true, 0, 0, 0, false, text, null, 0, 0, 0, null
                );
                break;
            case MirrlyDeletedStorage.MEDIA_VIDEO:
                SendMessagesHelper.prepareSendingVideo(
                        getAccountInstance(), rec.localMediaPath, null, rec.localThumbPath, null, targetDialogId, null, null, null, null, entities, 0, null, true, 0, 0, false, false, text, null, 0, 0, 0, null
                );
                break;
            default:
                ArrayList<String> files = new ArrayList<>(1);
                files.add(rec.localMediaPath);
                SendMessagesHelper.prepareSendingDocuments(
                        getAccountInstance(), files, files, null, text, entities, rec.mimeType, targetDialogId, null, null, null, null, null, true, 0, 0, null, null, 0, false, 0, 0, null
                );
                break;
        }
    }

    @Nullable
    private MirrlyDeletedStorage.DeletedRecord findRecordForMessageObject(@Nullable MessageObject obj) {
        if (obj == null) {
            return null;
        }
        for (int i = 0; i < items.size(); i++) {
            DeletedItem item = items.get(i);
            if (!item.isDate() && item.messageObject == obj) {
                return item.record;
            }
        }
        return null;
    }

    private void openPhotoViewerForMessage(@Nullable ChatMessageCell cell, @NonNull MessageObject message) {
        if (getParentActivity() == null) {
            return;
        }
        ArrayList<MessageObject> gallery = new ArrayList<>();
        int index = -1;
        for (int i = 0; i < items.size(); i++) {
            DeletedItem item = items.get(i);
            if (!item.isDate() && item.messageObject != null && (item.messageObject.type == MessageObject.TYPE_PHOTO || item.messageObject.isVideo() || item.messageObject.isGif())) {
                if (item.messageObject == message || item.messageObject.getId() == message.getId()) {
                    index = gallery.size();
                }
                gallery.add(item.messageObject);
            }
        }
        PhotoViewer.getInstance().setParentActivity(this, resourceProvider);
        if (gallery.size() > 1 && index >= 0) {
            PhotoViewer.getInstance().openPhoto(gallery, index, 0L, 0L, 0L, photoViewerProvider);
        } else {
            PhotoViewer.getInstance().openPhoto(message, 0L, 0L, 0L, photoViewerProvider, false);
        }
    }

    private ArrayList<MessageObject> createVoicePlaylist(MessageObject startMsg) {
        ArrayList<MessageObject> list = new ArrayList<>();
        for (int i = items.size() - 1; i >= 0; i--) {
            DeletedItem item = items.get(i);
            if (!item.isDate() && item.messageObject != null && (item.messageObject.isVoice() || item.messageObject.isRoundVideo())) {
                list.add(item.messageObject);
            }
        }
        if (startMsg != null && !list.contains(startMsg)) {
            list.add(0, startMsg);
        }
        return list;
    }

    private final PhotoViewer.PhotoViewerProvider photoViewerProvider = new PhotoViewer.EmptyPhotoViewerProvider() {
        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview, boolean closing) {
            if (chatListView == null) {
                return null;
            }
            for (int a = 0; a < chatListView.getChildCount(); a++) {
                View view = chatListView.getChildAt(a);
                if (view instanceof ChatMessageCell) {
                    ChatMessageCell cell = (ChatMessageCell) view;
                    MessageObject msg = cell.getMessageObject();
                    if (msg != null && messageObject != null && msg.getId() == messageObject.getId()) {
                        ImageReceiver ir = cell.getPhotoImage(index);
                        if (ir != null) {
                            int[] coords = new int[2];
                            view.getLocationInWindow(coords);
                            PhotoViewer.PlaceProviderObject obj = new PhotoViewer.PlaceProviderObject();
                            obj.viewX = coords[0];
                            obj.viewY = coords[1] + view.getPaddingTop();
                            obj.parentView = chatListView;
                            obj.imageReceiver = ir;
                            obj.thumb = ir.getBitmapSafe();
                            obj.radius = ir.getRoundRadius(true);
                            return obj;
                        }
                    }
                }
            }
            return null;
        }
    };

    private float getTopExtraOverlayHeight() {
        float extra = 0f;
        if (filterChipsContainer != null && filterChipsContainer.getVisibility() == View.VISIBLE) {
            extra += dp(46);
        }
        if (filterBanner != null && filterBanner.getVisibility() == View.VISIBLE) {
            extra += Math.max(dp(38), filterBanner.getMeasuredHeight()) + dp(4);
        }
        return extra;
    }

    private float getFloatingDateRestTop() {
        return chatListView != null && chatListView.getPaddingTop() > 0
                ? chatListView.getY() + chatListView.getPaddingTop() - dp(4)
                : AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() + getTopExtraOverlayHeight() + dp(4);
    }

    private void updateTopOverlayPositions() {
        float y = AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() + dp(4);
        if (filterChipsContainer != null && filterChipsContainer.getVisibility() == View.VISIBLE) {
            filterChipsContainer.setTranslationY(y);
            y += Math.max(dp(44), filterChipsContainer.getMeasuredHeight()) + dp(2);
        }
        if (filterBanner != null && filterBanner.getVisibility() == View.VISIBLE) {
            filterBanner.setTranslationY(y + dp(2));
        }
        if (floatingDateView != null) {
            floatingDateView.setTranslationY(getFloatingDateRestTop() - floatingDateView.getTop() + floatingDateOffset);
            if (contentView != null) {
                floatingDateView.setVisiblePart(floatingDateView.getY() - contentView.getBackgroundTranslationY(), contentView.getBackgroundSizeY());
            }
        }
    }

    private void updateEmptyContainerPosition() {
        if (emptyViewContainer == null) {
            return;
        }
        float topExtra = getTopExtraOverlayHeight();
        float bottomCovered = windowInsetsStateHolder.getAnimatedMaxBottomInset() + getBottomBarOccupiedHeight();
        emptyViewContainer.setTranslationY(0.5f * (topExtra - bottomCovered));
    }

    private void updateListPaddingAndButtons() {
        updateTopOverlayPositions();
        if (chatListView == null) {
            return;
        }
        int bottomPad = (int) (recommendedAdditionalSizeY + windowInsetsStateHolder.getAnimatedMaxBottomInset() + getBottomBarOccupiedHeight() + dp(12));
        int topPad = (int) (recommendedAdditionalSizeY + AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() + getTopExtraOverlayHeight() + dp(8));
        if (chatListView.getPaddingTop() != topPad || chatListView.getPaddingBottom() != bottomPad) {
            chatListView.setPadding(chatListView.getPaddingLeft(), topPad, chatListView.getPaddingRight(), bottomPad);
        }
        if (scrollDownButton != null) {
            scrollDownButton.setTranslationY(-(windowInsetsStateHolder.getAnimatedMaxBottomInset() + getBottomBarOccupiedHeight() + dp(14)));
        }
        if (chatActivityFadeView != null) {
            chatActivityFadeView.setFadeZoneTop((int) (AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() + getTopExtraOverlayHeight()));
        }
    }

    private void updateScrollDownButtonVisibility() {
        if (scrollDownButton == null || chatLayoutManager == null) {
            return;
        }
        int firstVisible = chatLayoutManager.findFirstVisibleItemPosition();
        boolean show = firstVisible > 2 && !items.isEmpty();
        if (scrollDownVisible == show) {
            return;
        }
        scrollDownVisible = show;
        scrollDownButton.animate().cancel();
        if (show) {
            scrollDownButton.setVisibility(View.VISIBLE);
            scrollDownButton.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                    .start();
        } else {
            scrollDownButton.animate()
                    .alpha(0f)
                    .scaleX(0.6f)
                    .scaleY(0.6f)
                    .setDuration(160)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                    .withEndAction(() -> {
                        if (!scrollDownVisible && scrollDownButton != null) {
                            scrollDownButton.setVisibility(View.GONE);
                        }
                    })
                    .start();
        }
    }

    private void showFloatingDateView() {
        if (floatingDateView == null || items.isEmpty()) {
            return;
        }
        if (floatingDateView.getTag() == null) {
            if (floatingDateAnimation != null) {
                floatingDateAnimation.cancel();
            }
            floatingDateView.setTag(1);
            floatingDateAnimation = new AnimatorSet();
            floatingDateAnimation.setDuration(180);
            floatingDateAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            floatingDateAnimation.playTogether(ObjectAnimator.ofFloat(floatingDateView, View.ALPHA, 1.0f));
            floatingDateAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animation.equals(floatingDateAnimation)) {
                        floatingDateAnimation = null;
                    }
                }
            });
            floatingDateAnimation.start();
        }
    }

    private void hideFloatingDateView(boolean animated) {
        if (floatingDateView == null || floatingDateView.getTag() == null || currentFloatingDateOnScreen) {
            return;
        }
        floatingDateView.setTag(null);
        if (floatingDateAnimation != null) {
            floatingDateAnimation.cancel();
            floatingDateAnimation = null;
        }
        if (animated) {
            floatingDateAnimation = new AnimatorSet();
            floatingDateAnimation.setDuration(220);
            floatingDateAnimation.setStartDelay(500);
            floatingDateAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            floatingDateAnimation.playTogether(ObjectAnimator.ofFloat(floatingDateView, View.ALPHA, 0.0f));
            floatingDateAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animation.equals(floatingDateAnimation)) {
                        floatingDateAnimation = null;
                    }
                }
            });
            floatingDateAnimation.start();
        } else {
            floatingDateView.setAlpha(0f);
        }
    }

    private void updateMessagesVisiblePart() {
        if (chatListView == null || contentView == null) {
            return;
        }
        int height = chatListView.getMeasuredHeight();
        int count = chatListView.getChildCount();
        int minPos = Integer.MAX_VALUE;
        int minDatePos = Integer.MAX_VALUE;
        View minChild = null;
        View minDateChild = null;

        for (int i = 0; i < count; i++) {
            View child = chatListView.getChildAt(i);
            if (child instanceof ChatMessageCell) {
                ChatMessageCell cell = (ChatMessageCell) child;
                int top = child.getTop();
                int viewTop = top >= 0 ? 0 : -top;
                int viewBottom = child.getMeasuredHeight();
                if (viewBottom > height) {
                    viewBottom = viewTop + height;
                }
                cell.setVisiblePart(viewTop, viewBottom - viewTop, contentView.getMeasuredHeight() - dp(48) - chatListView.getTop(), 0, child.getY() + actionBar.getMeasuredHeight() - contentView.getBackgroundTranslationY(), contentView.getMeasuredWidth(), contentView.getBackgroundSizeY(), 0, 0, 0);
            }
            int adapterPos = chatListView.getChildAdapterPosition(child);
            if (adapterPos == RecyclerView.NO_POSITION) {
                continue;
            }
            if (child.getBottom() > chatListView.getPaddingTop() && child.getTop() < minPos) {
                minPos = adapterPos;
                minChild = child;
            }
            if (child instanceof ChatActionCell && adapterPos < minDatePos) {
                minDatePos = adapterPos;
                minDateChild = child;
            }
        }
        if (floatingDateView == null) {
            return;
        }
        if (minPos != Integer.MAX_VALUE && minPos >= 0 && minPos < items.size()) {
            DeletedItem item = items.get(minPos);
            currentFloatingTopIsNotMessage = item.isDate();
            int dateSec = item.isDate()
                    ? item.messageObject.messageOwner.date
                    : ((filterDialogId != 0 && item.record.sendDate > 0) ? item.record.sendDate : item.record.deletedAt);
            floatingDateView.setCustomDate(dateSec, false, false);
        }
        if (minDateChild != null && minDateChild.getTop() < chatListView.getPaddingTop()) {
            currentFloatingDateOnScreen = true;
            if (floatingDateView.getTag() == null) {
                floatingDateView.setTag(1);
                floatingDateView.setAlpha(1.0f);
            }
            int offset = minDateChild.getBottom() - chatListView.getPaddingTop();
            if (offset > floatingDateView.getMeasuredHeight() && offset < floatingDateView.getMeasuredHeight() * 2) {
                floatingDateOffset = -floatingDateView.getMeasuredHeight() * 2 + offset;
            } else {
                floatingDateOffset = 0f;
            }
        } else {
            currentFloatingDateOnScreen = false;
            hideFloatingDateView(true);
            floatingDateOffset = 0f;
        }
        floatingDateView.setTranslationY(getFloatingDateRestTop() - floatingDateView.getTop() + floatingDateOffset);
        floatingDateView.setVisiblePart(floatingDateView.getY() - contentView.getBackgroundTranslationY(), contentView.getBackgroundSizeY());
    }

    private void onInsetsUpdated() {
        if (bottomBarContainer != null) {
            bottomBarContainer.setTranslationY(-windowInsetsStateHolder.getAnimatedMaxBottomInset());
        }
        updateListPaddingAndButtons();
        updateEmptyContainerPosition();
    }

    @NonNull
    private WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
        final Insets systemInsets = AndroidUtilities.getDefaultWindowInsets(insets, false);
        windowInsetsStateHolder.setInsets(insets);
        if (chatListView != null) {
            chatListView.setPadding(systemInsets.left, chatListView.getPaddingTop(), systemInsets.right, chatListView.getPaddingBottom());
        }
        onInsetsUpdated();
        return WindowInsetsCompat.CONSUMED;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetNewWallpapper || id == NotificationCenter.didApplyNewTheme) {
            applyChatBackground();
            if (chatListView != null) {
                chatListView.invalidateViews();
            }
        } else if (id == NotificationCenter.emojiLoaded) {
            if (chatListView != null) {
                chatListView.invalidateViews();
            }
        } else if (id == NotificationCenter.mirrlyDeletedDidUpdate || id == NotificationCenter.mirrlyDeletedChatsDidUpdate) {
            reloadRecords(true);
        } else if (id == NotificationCenter.mirrlyDeletedAppearanceChanged) {
            updateEmptyPlaceholder();
            updateHeaderSubtitle();
        } else if (id == NotificationCenter.messagePlayingDidStart
                || id == NotificationCenter.messagePlayingDidReset
                || id == NotificationCenter.messagePlayingPlayStateChanged
                || id == NotificationCenter.messagePlayingProgressDidChanged) {
            updatePlayingCells();
        }
    }

    private void updatePlayingCells() {
        if (chatListView == null) {
            return;
        }
        for (int i = 0; i < chatListView.getChildCount(); i++) {
            View v = chatListView.getChildAt(i);
            if (v instanceof ChatMessageCell) {
                ChatMessageCell cell = (ChatMessageCell) v;
                MessageObject mo = cell.getMessageObject();
                if (mo == null) {
                    continue;
                }
                if (mo.isRoundVideo()) {
                    cell.checkVideoPlayback(!MediaController.getInstance().isPlayingMessage(mo), null);
                    cell.updateButtonState(false, true, false);
                } else if (mo.isVoice() || mo.isMusic() || mo.isVideo()) {
                    cell.updateButtonState(false, true, false);
                }
            }
        }
    }

    private void invalidateMergedVisibleBlurredPositionsAndSourcesImpl(int flags) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || scrollableViewNoiseSuppressor == null || contentView == null || chatListView == null) {
            return;
        }
        if (BitwiseUtils.hasFlag(flags, GlassEngine.FLAG_INVALIDATED_POSITIONS)) {
            GlassPositionsArray glassPositionsArray = glassEngine.getAllVisibleDrawablesWithDisplayListPositionsMerged();
            glassPositionsArray.clip(0, chatListView.getY(), contentView.getWidth(), chatListView.getY() + chatListView.getHeight());
            scrollableViewNoiseSuppressor.setupRenderNodes(glassPositionsArray);
        }
        scrollableViewNoiseSuppressor.invalidateResultRenderNodes(contentView::drawList, contentView.getWidth(), contentView.getHeight());
    }

    private final RectF tmpViewRectF = new RectF();

    private boolean quickRejectChild(View child, @Nullable RectF position) {
        if (position == null || chatListView == null || child == null) {
            return false;
        }
        tmpViewRectF.set(child.getX(), child.getY() + chatListView.getY(), child.getX() + child.getWidth(), child.getY() + chatListView.getY() + child.getHeight());
        return !tmpViewRectF.intersect(position);
    }

    private class DeletedChatFragmentView extends SizeNotifierFrameLayout {
        private final WallpaperBitmapProvider wallpaperBitmapProvider = new WallpaperBitmapProvider();

        DeletedChatFragmentView(Context context, INavigationLayout parentLayout) {
            super(context, parentLayout);
        }

        @Override
        protected Theme.ResourcesProvider getResourceProvider() {
            return MirrlyDeletedChatActivity.this.getResourceProvider();
        }

        @Override
        protected Drawable getNewDrawable() {
            if (customChatBackgroundDrawable != null) {
                return customChatBackgroundDrawable;
            }
            return super.getNewDrawable();
        }

        @Override
        protected boolean getNewDrawableMotion() {
            if (customChatBackgroundDrawable != null) {
                return customChatBackgroundMotion;
            }
            return super.getNewDrawableMotion();
        }

        @Override
        protected boolean isActionBarVisible() {
            return false;
        }

        @Override
        protected boolean isStatusBarVisible() {
            return false;
        }

        @Override
        public void onUpdateBackgroundDrawable(Drawable drawable) {
            super.onUpdateBackgroundDrawable(drawable);
            if (drawable instanceof MotionBackgroundDrawable) {
                ((MotionBackgroundDrawable) drawable).setFastRenderAllowed();
            }
            final GlassSource source = wallpaperBitmapProvider.updateSourceFromBackgroundViewDrawable(drawable);
            navbarContentSourceWallpaper.setSource(source);
            if (chatActivityFadeView != null) {
                chatActivityFadeView.invalidate();
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int widthSize = MeasureSpec.getSize(widthMeasureSpec);
            int heightSize = MeasureSpec.getSize(heightMeasureSpec);

            if (navbarContentSourceWallpaper.getSource() instanceof GlassSourceBitmap) {
                ((GlassSourceBitmap) navbarContentSourceWallpaper.getSource()).setParentSize(widthSize, heightSize, 0);
            }

            setMeasuredDimension(widthSize, heightSize);
            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                if (child == null || child.getVisibility() == GONE) {
                    continue;
                }
                if (child == chatListView) {
                    int wSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY);
                    int hSpec = MeasureSpec.makeMeasureSpec(recommendedAdditionalSizeY * 2 + Math.max(dp(10), heightSize), MeasureSpec.EXACTLY);
                    child.measure(wSpec, hSpec);
                } else {
                    measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                }
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            if (chatListView != null && recommendedAdditionalSizeY != 0) {
                chatListView.layout(0, -recommendedAdditionalSizeY, chatListView.getMeasuredWidth(), chatListView.getMeasuredHeight() - recommendedAdditionalSizeY);
            }
            updateMessagesVisiblePart();
            updateListPaddingAndButtons();
        }

        void drawList(Canvas blurCanvas, RectF position) {
            if (chatListView == null) {
                return;
            }
            final long drawingTime = SystemClock.uptimeMillis();
            blurCanvas.save();
            blurCanvas.clipRect(position);
            blurCanvas.translate(0, chatListView.getY());
            for (int i = 0; i < chatListView.getChildCount(); i++) {
                View child = chatListView.getChildAt(i);
                if (quickRejectChild(child, position)) {
                    continue;
                }
                if (child instanceof ChatMessageCell) {
                    ChatMessageCell cell = (ChatMessageCell) child;
                    if (cell.drawBackgroundInParent()) {
                        blurCanvas.save();
                        blurCanvas.translate(cell.getX(), cell.getY() + cell.starsPriceTopPadding);
                        cell.drawBackgroundInternal(blurCanvas, true);
                        blurCanvas.restore();
                    }
                    chatListView.drawChild(blurCanvas, child, drawingTime);
                    if (cell.hasOutboundsContent()) {
                        blurCanvas.save();
                        blurCanvas.translate(cell.getX(), cell.getY());
                        cell.drawOutboundsContent(blurCanvas);
                        blurCanvas.restore();
                    }
                } else if (child instanceof ChatActionCell) {
                    chatListView.drawChild(blurCanvas, child, drawingTime);
                    blurCanvas.save();
                    blurCanvas.translate(child.getX(), child.getY());
                    ((ChatActionCell) child).drawOutboundsContent(blurCanvas);
                    blurCanvas.restore();
                } else {
                    chatListView.drawChild(blurCanvas, child, drawingTime);
                }
            }
            blurCanvas.restore();
        }
    }

    private class DeletedAdapter extends RecyclerListView.SelectionAdapter {
        private final Context mContext;

        DeletedAdapter(Context context) {
            this.mContext = context;
            setHasStableIds(true);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.itemView instanceof ChatMessageCell;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public long getItemId(int position) {
            if (position < 0 || position >= items.size()) {
                return RecyclerView.NO_ID;
            }
            DeletedItem item = items.get(position);
            if (item.isDate()) {
                return item.messageObject.getId();
            }
            return item.record.uid.hashCode() & 0x7FFFFFFFL;
        }

        @Override
        public int getItemViewType(int position) {
            if (position < 0 || position >= items.size()) {
                return 0;
            }
            return items.get(position).isDate() ? 1 : 0;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == 1) {
                view = new ChatActionCell(mContext, false, resourceProvider);
            } else {
                ChatMessageCell cell = new ChatMessageCell(mContext, currentAccount, false, null, resourceProvider);
                cell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
                    @Override
                    public boolean canPerformActions() {
                        return !(actionBar != null && actionBar.isActionModeShowed());
                    }

                    @Override
                    public boolean doNotShowLoadingReply(MessageObject messageObject) {
                        return true;
                    }

                    @Override
                    public void didPressReplyMessage(ChatMessageCell cell1, int id, float x, float y, boolean longpress) {
                        MirrlyDeletedStorage.DeletedRecord rec = findRecordForMessageObject(cell1.getMessageObject());
                        if (rec != null && rec.dialogId != 0) {
                            openSourceChat(rec);
                        }
                    }

                    @Override
                    public void didPressImage(ChatMessageCell cell1, float x, float y, boolean fullPreview) {
                        if (actionBar != null && actionBar.isActionModeShowed()) {
                            MirrlyDeletedStorage.DeletedRecord rec = findRecordForMessageObject(cell1.getMessageObject());
                            if (rec != null) {
                                toggleSelection(rec);
                            }
                            return;
                        }
                        MessageObject msg = cell1.getMessageObject();
                        if (msg == null || getParentActivity() == null) {
                            return;
                        }
                        if (msg.getInputStickerSet() != null) {
                            StickersAlert alert = new StickersAlert(getParentActivity(), MirrlyDeletedChatActivity.this, msg.getInputStickerSet(), null, null, resourceProvider, false);
                            showDialog(alert);
                        } else if (msg.isVideo() || msg.type == MessageObject.TYPE_PHOTO || msg.isGif()) {
                            openPhotoViewerForMessage(cell1, msg);
                        } else if (msg.type == MessageObject.TYPE_GEO) {
                            if (AndroidUtilities.isMapsInstalled(MirrlyDeletedChatActivity.this)) {
                                LocationActivity fragment = new LocationActivity(3);
                                fragment.setMessageObject(msg);
                                presentFragment(fragment);
                            }
                        } else if (msg.type == MessageObject.TYPE_FILE || msg.isMusic()) {
                            if (msg.canPreviewDocument()) {
                                openPhotoViewerForMessage(cell1, msg);
                            } else {
                                File checkFile = !TextUtils.isEmpty(msg.messageOwner.attachPath) ? new File(msg.messageOwner.attachPath) : null;
                                if (checkFile != null && !checkFile.exists()) {
                                    BulletinFactory.of(MirrlyDeletedChatActivity.this).createSimpleBulletin(R.raw.info, getString(R.string.MirrlyNotesMediaMissingFile)).show();
                                    return;
                                }
                                try {
                                    AndroidUtilities.openForView(msg, getParentActivity(), resourceProvider, false);
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                            }
                        } else {
                            showDeletedItemOptions(cell1);
                        }
                    }

                    @Override
                    public boolean needPlayMessage(ChatMessageCell cell1, MessageObject messageObject, boolean muted) {
                        if (messageObject != null && (messageObject.isVoice() || messageObject.isMusic() || messageObject.isRoundVideo())) {
                            if (messageObject.messageOwner != null && !TextUtils.isEmpty(messageObject.messageOwner.attachPath) && !new File(messageObject.messageOwner.attachPath).exists()) {
                                BulletinFactory.of(MirrlyDeletedChatActivity.this).createSimpleBulletin(R.raw.info, getString(R.string.MirrlyNotesMediaMissingFile)).show();
                                return false;
                            }
                            boolean res = MediaController.getInstance().playMessage(messageObject, muted);
                            if (res && (messageObject.isVoice() || messageObject.isRoundVideo())) {
                                MediaController.getInstance().setVoiceMessagesPlaylist(createVoicePlaylist(messageObject), false);
                            }
                            return res;
                        }
                        return false;
                    }

                    @Override
                    public void didLongPress(ChatMessageCell cell1, float x, float y) {
                        cell1.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        showDeletedItemOptions(cell1);
                    }

                    @Override
                    public String getAdminRank(long uid) {
                        return null;
                    }
                });
                view = cell;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            DeletedItem item = items.get(position);
            if (holder.itemView instanceof ChatActionCell) {
                ((ChatActionCell) holder.itemView).setMessageObject(item.messageObject, true);
            } else if (holder.itemView instanceof ChatMessageCell) {
                ChatMessageCell cell = (ChatMessageCell) holder.itemView;
                cell.isChat = filterDialogId != 0 && DialogObject.isChatDialog(filterDialogId);
                boolean pinnedBottom = false;
                boolean pinnedTop = false;
                if (filterDialogId != 0 && item.messageObject != null && item.record != null) {
                    if (position - 1 >= 0 && !items.get(position - 1).isDate()) {
                        DeletedItem below = items.get(position - 1);
                        if (below.record != null && below.messageObject != null
                                && item.messageObject.isOutOwner() == below.messageObject.isOutOwner()
                                && item.record.senderId == below.record.senderId
                                && Math.abs(item.messageObject.messageOwner.date - below.messageObject.messageOwner.date) <= 5 * 60) {
                            pinnedBottom = true;
                        }
                    }
                    if (position + 1 < items.size() && !items.get(position + 1).isDate()) {
                        DeletedItem above = items.get(position + 1);
                        if (above.record != null && above.messageObject != null
                                && item.messageObject.isOutOwner() == above.messageObject.isOutOwner()
                                && item.record.senderId == above.record.senderId
                                && Math.abs(item.messageObject.messageOwner.date - above.messageObject.messageOwner.date) <= 5 * 60) {
                            pinnedTop = true;
                        }
                    }
                }
                MessageObject.GroupedMessages group = item.messageObject != null && item.messageObject.hasValidGroupId()
                        ? groupedMessagesMap.get(item.messageObject.getGroupId())
                        : null;
                cell.setMessageObject(item.messageObject, group, pinnedBottom, pinnedTop, false);
                cell.setHighlightedText(searchQuery);
                boolean inActionMode = actionBar != null && actionBar.isActionModeShowed();
                boolean selected = inActionMode && item.record != null && selectedUids.contains(item.record.uid);
                cell.setCheckPressed(false, false);
                cell.setChecked(selected, inActionMode, true);
            }
        }
    }
}
