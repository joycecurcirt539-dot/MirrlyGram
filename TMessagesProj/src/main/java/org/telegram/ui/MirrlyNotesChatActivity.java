package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.math.MathUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.mirrly.MirrlyVaultStorage;
import org.telegram.messenger.utils.ViewOutlineProviderImpl;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.EdgeToEdgeSupportMode;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.ChatAttachAlertDocumentLayout;
import org.telegram.ui.Components.ChatAttachAlertLocationLayout;
import org.telegram.ui.Components.ChatAttachAlertPollLayout;
import org.telegram.ui.Components.ChatAttachAlertContactsLayout;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EmojiView;
import org.telegram.ui.Components.InstantCameraView;
import org.telegram.ui.Components.InstantCameraViewBase;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.StickersAlert;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.ThanosEffect;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.DownscaleScrollableNoiseSuppressor;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProviderThemed;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProvider;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.utils.glass.GlassContrastHelper;
import org.telegram.ui.Components.chat.ChatInputViewsContainer;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.Components.chat.WallpaperBitmapProvider;
import org.telegram.ui.Components.chat.buttons.ChatActivityBlurredRoundPageDownButton;
import org.telegram.ui.Components.chat.layouts.ChatActivityFadeView;
import org.telegram.ui.Components.inset.WindowInsetsStateHolder;
import org.telegram.ui.recyclerview.ChatListItemAnimator;
import org.telegram.ui.recyclerview.LinearSmoothScrollerCustom;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import me.vkryl.core.BitwiseUtils;

/**
 * Telegram-native local notes timeline supporting full offline media attachments,
 * frosted/liquid glass composer chrome, search filters, pinned note bar, and multi-select actions.
 */
public class MirrlyNotesChatActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int MENU_SEARCH_ICON = 1;
    private static final int MENU_SEARCH = 2;
    private static final int MENU_FILTER = 3;

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

    private final WindowInsetsStateHolder windowInsetsStateHolder = new WindowInsetsStateHolder(this::onInsetsUpdated) {
        @Override
        protected WindowInsetsCompat filterInsets(@Nullable WindowInsetsCompat insets) {
            return getNotesWindowInsets(insets);
        }
    };

    private @Nullable String initialHighlightNoteId;
    private @Nullable String activeHighlightNoteId;

    private NotesChatFragmentView contentView;
    private ChatActivityFadeView chatActivityFadeView;
    private ChatAvatarContainer avatarContainer;
    private ActionBarMenuItem searchIconItem;
    private ActionBarMenuItem searchItem;
    private ActionBarMenuItem filterItem;

    private RecyclerListView chatListView;
    private LinearLayoutManager chatLayoutManager;
    private ChatListItemAnimator chatListItemAnimator;
    private NotesAdapter notesAdapter;

    private ChatActionCell floatingDateView;
    private AnimatorSet floatingDateAnimation;
    private boolean currentFloatingDateOnScreen;
    private boolean currentFloatingTopIsNotMessage;
    private boolean scrollingFloatingDate;

    private FrameLayout emptyViewContainer;
    private LinearLayout emptyCardView;
    private RLottieImageView emptyLottieView;
    private TextView emptyTitleView;
    private TextView emptySubtitleView;

    private FrameLayout pinnedNoteBar;
    private ImageView pinnedNoteIconView;
    private TextView pinnedNoteTitleView;
    private TextView pinnedNoteBodyView;
    private ImageView pinnedNoteUnpinBtn;
    private @Nullable MirrlyVaultStorage.DossierNote currentPinnedNote;

    private FrameLayout filterChipsContainer;
    private HorizontalScrollView filterChipsScroll;
    private LinearLayout filterChipsRow;
    private final ArrayList<FilterChipView> filterChipViews = new ArrayList<>();

    private FrameLayout filterBanner;
    private TextView filterBannerText;
    private ImageView filterBannerClear;

    private float floatingDateOffset = 0f;

    private ChatActivityBlurredRoundPageDownButton scrollDownButton;
    private boolean scrollDownVisible = false;

    private ChatInputViewsContainer chatInputViewsContainer;
    private FrameLayout chatInputBubbleContainer;
    private FrameLayout chatInputInAppContainer;
    private LinearLayout composerColumn;
    private LinearLayout editBannerRow;
    private GradientDrawable editBannerAccentDrawable;
    private TextView editBannerTitle;
    private TextView editBannerPreview;
    private ImageView editBannerClose;
    private ChatActivityEnterView chatActivityEnterView;

    private LinearLayout searchBottomBar;
    private TextView searchBottomCountView;
    private ImageView searchPrevBtn;
    private ImageView searchNextBtn;
    private int currentSearchNavPos = -1;

    private LinearLayout actionModeBottomBar;
    private TextView actionModeCountView;
    private TextView actionModeForwardBtn;
    private TextView actionModeDeleteBtn;

    private View overlayView;
    private View roundVideoRecordBackground;
    private @Nullable InstantCameraViewBase instantCameraView;
    private @Nullable ChatAttachAlert chatAttachAlert;
    private @Nullable String currentPicturePath;
    private boolean openKeyboardOnAttachMenuClose;

    private FrameLayout videoPlayerContainer;
    private AspectRatioFrameLayout aspectRatioFrameLayout;
    private TextureView videoTextureView;

    private ThanosEffect thanosEffect;
    private boolean removingFromParent = false;
    private boolean isPaused = false;
    private boolean openAnimationEnded = true;
    private float contentPanTranslation = 0f;

    private final ArrayList<NoteItem> items = new ArrayList<>();
    private final Map<String, Integer> stableMessageIdMap = new HashMap<>();
    private final AtomicInteger nextSyntheticId = new AtomicInteger(10000);
    private final Set<String> selectedNoteIds = new LinkedHashSet<>();

    private String searchQuery = "";
    private boolean isSearching = false;
    private int activeFilter = MirrlyVaultStorage.FILTER_ALL;
    private @Nullable MirrlyVaultStorage.DossierNote editingNote = null;

    private static final class NoteItem {
        final MessageObject messageObject;
        final @Nullable MirrlyVaultStorage.DossierNote note;

        NoteItem(MessageObject messageObject, @Nullable MirrlyVaultStorage.DossierNote note) {
            this.messageObject = messageObject;
            this.note = note;
        }

        boolean isDate() {
            return note == null;
        }
    }

    public MirrlyNotesChatActivity() {
        this(null);
    }

    public MirrlyNotesChatActivity(@Nullable String highlightNoteId) {
        this.initialHighlightNoteId = highlightNoteId;

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
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        blurredBackgroundColorProvider = new BlurredBackgroundColorProviderThemed(resourceProvider, Theme.key_chat_messagePanelBackground) {
            @Override
            public int getBackgroundColor() {
                if (!BlurredBackgroundProviderImpl.checkBlurEnabled(currentAccount, resourceProvider)) {
                    return ColorUtils.setAlphaComponent(getThemedColor(Theme.key_chat_messagePanelBackground), 255);
                }
                return super.getBackgroundColor();
            }
        };
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didApplyNewTheme);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.mirrlyNotesDidUpdate);

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.mirrlyLocalVoiceRecorded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);

        Bulletin.addDelegate(this, new Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                float bubbleH = chatInputViewsContainer != null ? chatInputViewsContainer.getInputBubbleHeight() : dp(48);
                return (int) (bubbleH + dp(18) + windowInsetsStateHolder.getAnimatedMaxBottomInset());
            }
        });
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        removingFromParent = true;
        cancelNotesAdaptiveTick();
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
        MediaController.getInstance().cleanupPlayer(true, true);
        if (chatActivityEnterView != null) {
            chatActivityEnterView.onDestroy();
            chatActivityEnterView = null;
        }
        if (instantCameraView != null) {
            instantCameraView.destroy(true);
            instantCameraView = null;
        }
        if (chatAttachAlert != null) {
            chatAttachAlert.dismissInternal();
            chatAttachAlert.onDestroy();
            chatAttachAlert = null;
        }
        if (thanosEffect != null) {
            thanosEffect.kill();
            thanosEffect = null;
        }
        Bulletin.removeDelegate(this);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didApplyNewTheme);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.mirrlyNotesDidUpdate);

        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.mirrlyLocalVoiceRecorded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);

        MirrlyVaultStorage.cleanupOrphanedMediaFiles();
        if (notesAdaptiveTickPosted && contentView != null) {
            contentView.removeCallbacks(notesAdaptiveTickRunnable);
        }
        notesAdaptiveTickPosted = false;
        super.onFragmentDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        isPaused = false;
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        if (contentView != null) {
            contentView.onResume();
        }
        if (chatActivityEnterView != null) {
            chatActivityEnterView.onResume();
        }
        reloadNotes(false);
    }

    @Override
    public void onPause() {
        super.onPause();
        isPaused = true;
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
        if (contentView != null) {
            contentView.onPause();
        }
        if (chatActivityEnterView != null) {
            chatActivityEnterView.onPause();
            chatActivityEnterView.hidePopup(false);
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        super.onTransitionAnimationEnd(isOpen, backward);
        if (isOpen) {
            openAnimationEnded = true;
            if (initialHighlightNoteId != null) {
                String targetId = initialHighlightNoteId;
                initialHighlightNoteId = null;
                scrollToNoteId(targetId, true);
            }
        }
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public EdgeToEdgeSupportMode getEdgeToEdgeSupportMode() {
        return EdgeToEdgeSupportMode.FULL;
    }

    @Override
    public boolean drawEdgeNavigationBar() {
        return false;
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (chatActivityEnterView != null && chatActivityEnterView.isPopupShowing()) {
            if (invoked) {
                chatActivityEnterView.hidePopup(true);
            }
            return false;
        }
        if (actionBar != null && actionBar.isActionModeShowed()) {
            if (invoked) {
                exitActionMode();
            }
            return false;
        }
        if (isSearching && actionBar != null && actionBar.isSearchFieldVisible()) {
            if (invoked) {
                actionBar.closeSearchField();
            }
            return false;
        }
        if (editingNote != null) {
            if (invoked) {
                cancelEditMode();
            }
            return false;
        }
        return super.onBackPressed(invoked);
    }

    @Override
    public View createView(Context context) {
        Theme.createChatResources(context, false);
        hasOwnBackground = true;

        setupActionBar(context);

        contentView = new NotesChatFragmentView(context, getParentLayout());
        fragmentView = contentView;

        ViewPositionWatcher viewPositionWatcher = new ViewPositionWatcher(contentView);
        glassBackgroundDrawableFactory.setSourceRootView(viewPositionWatcher, contentView);
        glassBackgroundDrawableFactoryFrosted.setSourceRootView(viewPositionWatcher, contentView);
        navbarContentDrawableFactory.setSourceRootView(viewPositionWatcher, contentView);

        contentView.setOccupyStatusBar(!AndroidUtilities.isTablet());
        contentView.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());
        contentView.setClipChildren(false);

        actionBar.setupGlass(glassBackgroundDrawableFactory, BlurredBackgroundProviderImpl.topPanelChatActivity(resourceProvider), false);

        buildListAndEmptyState(context);
        buildFadeAndFloatingOverlays(context);
        buildTopOverlays(context);
        buildScrollDownButton(context);

        overlayView = new View(context);
        overlayView.setOnTouchListener((v, event) -> true);
        overlayView.setVisibility(View.GONE);
        contentView.addView(overlayView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        roundVideoRecordBackground = new View(context);
        roundVideoRecordBackground.setVisibility(View.GONE);
        BlurredBackgroundDrawable d = navbarContentDrawableFactory.create(roundVideoRecordBackground);
        d.setAlpha(232);
        roundVideoRecordBackground.setBackground(d);
        contentView.addView(roundVideoRecordBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        buildBottomInputBar(context);

        contentView.addView(actionBar);

        if (context instanceof LaunchActivity) {
            windowInsetsStateHolder.setupAnimatedInsetsProvider(((LaunchActivity) context).getRootAnimatedInsetsListener(), contentView);
        }
        ViewCompat.setOnApplyWindowInsetsListener(contentView, this::onApplyWindowInsets);

        reloadNotes(false);
        updateHeaderSubtitle();
        updatePinnedNoteBar(false);
        updateFilterBanner(false);
        updateFilterChipsVisibility(false);
        updateBottomModeBars();

        return fragmentView;
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
                    openNotesSearch();
                } else if (id == MENU_FILTER) {
                    showFilterMenu();
                } else if (id == AM_FORWARD) {
                    forwardSelectedNotes();
                } else if (id == AM_DELETE) {
                    confirmDeleteSelectedNotes();
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
        AvatarDrawable notesAvatar = new AvatarDrawable();
        notesAvatar.setAvatarType(AvatarDrawable.AVATAR_TYPE_MY_NOTES);
        notesAvatar.setScaleSize(.82f);
        avatarContainer.getAvatarImageView().setImage(null, null, notesAvatar, null);
        avatarContainer.setTitle(getString(R.string.MirrlyNotesTitle));
        avatarContainer.setEnabled(false);
        // Add without setChatAvatarContainer so ActionBar search glass pill expands cleanly to the right edge.
        actionBar.addView(avatarContainer, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 54, 0, 96, 0));

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
                        if (searchItem != null) {
                            searchItem.setVisibility(View.GONE);
                        }
                        updateFilterChipsVisibility(true);
                        updateFilterBanner(true);
                        updateBottomModeBars();
                        reloadNotes(true);
                        updateEmptyContainerPosition();
                        updateListPaddingAndButtons();
                    }

                    @Override
                    public void onTextChanged(EditText editText) {
                        String next = editText.getText() != null ? editText.getText().toString() : "";
                        if (!TextUtils.equals(searchQuery, next)) {
                            searchQuery = next;
                            currentSearchNavPos = -1;
                            reloadNotes(true);
                            updateSearchBottomBarState();
                        }
                    }

                    @Override
                    public void onSearchPressed(EditText editText) {
                        String next = editText.getText() != null ? editText.getText().toString() : "";
                        searchQuery = next;
                        reloadNotes(true);
                        updateSearchBottomBarState();
                        AndroidUtilities.hideKeyboard(editText);
                    }
                });
        searchItem.setSearchFieldHint(getString(R.string.MirrlyNotesSearchHint));
        searchItem.setSearchPaddingStart(7);
        searchItem.setVisibility(View.GONE);

        filterItem = menu.addItem(MENU_FILTER, R.drawable.menu_tag_filter);
        filterItem.setContentDescription(getString(R.string.MirrlyNotesFilterDesc));

        ActionBarMenu actionMode = actionBar.createActionMode();
        ThemeBind.clear(actionMode);
        actionMode.setBackgroundColor(Color.TRANSPARENT);
    }

    private void openNotesSearch() {
        if (actionBar == null || searchItem == null) {
            return;
        }
        searchItem.setVisibility(View.VISIBLE);
        searchItem.toggleSearch(true);
    }

    private void buildListAndEmptyState(Context context) {
        chatListView = new RecyclerListView(context) {
            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                return super.drawChild(canvas, child, drawingTime);
            }
        };
        chatListView.setVerticalScrollBarEnabled(false);
        chatListView.setClipToPadding(true);
        chatListView.setAdapter(notesAdapter = new NotesAdapter(context));

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
                    notesAdaptiveScrollGen++;
                    scheduleNotesAdaptiveGlassContrastUpdate(true);
                    if (floatingDateView != null && floatingDateView.getAlpha() > 0.01f) {
                        floatingDateView.invalidate();
                    }
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                chatListView.invalidate();
                if (dy != 0) {
                    notesAdaptiveScrollGen++;
                }
                if (dy != 0 && scrollingFloatingDate && !currentFloatingTopIsNotMessage) {
                    showFloatingDateView();
                }
                updateMessagesVisiblePart();
                updateScrollDownButtonVisibility();
                updateTextureViewPosition();
            }
        });

        chatListView.setOnItemClickListener((view, position, x, y) -> {
            if (view instanceof ChatMessageCell) {
                ChatMessageCell cell = (ChatMessageCell) view;
                if (actionBar != null && actionBar.isActionModeShowed()) {
                    MirrlyVaultStorage.DossierNote note = findNoteForMessageObject(cell.getMessageObject());
                    if (note != null) {
                        toggleSelection(note);
                    }
                    return;
                }
                showNoteItemOptions(cell);
            }
        });
        chatListView.setOnItemLongClickListener((view, position, x, y) -> {
            if (view instanceof ChatMessageCell) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                ChatMessageCell cell = (ChatMessageCell) view;
                if (actionBar != null && actionBar.isActionModeShowed()) {
                    MirrlyVaultStorage.DossierNote note = findNoteForMessageObject(cell.getMessageObject());
                    if (note != null) {
                        toggleSelection(note);
                    }
                } else {
                    showNoteItemOptions(cell);
                }
                return true;
            }
            return false;
        });

        attachSwipeToEdit(chatListView);

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
        emptyLottieView.setAnimation(R.raw.utyan_saved_messages, 110, 110);
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
        emptySubtitleView.setLineSpacing(dp(2), 1f);
        ThemeBind.textColor(emptySubtitleView, Theme.key_windowBackgroundWhiteGrayText, resourceProvider);
        emptyCardView.addView(emptySubtitleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        emptyViewContainer.addView(emptyCardView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
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
            private boolean dateTickPosted;
            private final Runnable invalidateSelfRunnable = () -> {
                dateTickPosted = false;
                invalidate();
            };
            private BlurredBackgroundDrawable glassBackground;
            private BlurredBackgroundColorProvider glassColorProvider;
            private int lastServiceBg;
            private int lastTextColor;
            private int lastGlassGeneration = -1;
            private float lastGlassRadius;
            private float dateInvertFactor = 0f;
            private boolean dateAdaptiveSampled;
            private long lastDateSampleMs;
            private int lastDateScrollGen = Integer.MIN_VALUE;
            private float lastDateTransY = Float.NaN;
            private int lastDateSampleGlassGen = -1;
            private int lastDateSampleTextColor;
            private int lastDateValue = Integer.MIN_VALUE;
            private float lastDateUnderLuma;

            private void ensureGlassBackground() {
                if (glassBackground == null && glassBackgroundDrawableFactory != null) {
                    glassColorProvider = createNotesFloatingPillColorProvider(Theme.key_chat_serviceText);
                    glassBackground = glassBackgroundDrawableFactory.create(
                            this,
                            glassColorProvider
                    );
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
                        && (!Theme.isCompactMode(resourceProvider) || org.telegram.messenger.mirrly.MirrlyConfig.isLiquidGlassEverywhere())
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
                final int glassGeneration = org.telegram.utils.glass.GlassEngine.getGlassSettingsGenerationId();
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
                if (glassColorProvider != null) {
                    final long now = SystemClock.uptimeMillis();
                    final float curTransY = getTranslationY();
                    final int curDate = getCustomDate();
                    final boolean moved = lastDateScrollGen != notesAdaptiveScrollGen
                            || Float.isNaN(lastDateTransY)
                            || Math.abs(curTransY - lastDateTransY) > 1f;
                    final boolean needsSample = !dateAdaptiveSampled
                            || lastDateSampleGlassGen != glassGeneration
                            || lastDateSampleTextColor != textColor
                            || lastDateValue != curDate
                            || (moved && (now - lastDateSampleMs >= 80L));
                    if (needsSample) {
                        dateAdaptiveSampled = true;
                        lastDateSampleMs = now;
                        lastDateScrollGen = notesAdaptiveScrollGen;
                        lastDateTransY = curTransY;
                        lastDateSampleGlassGen = glassGeneration;
                        lastDateSampleTextColor = textColor;
                        lastDateValue = curDate;
                        final float bandLeft = Math.max(0f, left - sideMenuWidth / 2f - p);
                        final float bandRight = Math.min(Math.max(bandLeft + 1f, getWidth()), right - sideMenuWidth / 2f + p);
                        lastDateUnderLuma = GlassContrastHelper.estimateUnderlyingLuminanceInBand(
                                this, chatListView, bandLeft, 0f, bandRight, getHeight(),
                                getNotesTopBackdropLuminance(), resourceProvider);
                    }
                    final float effLuma = GlassContrastHelper.computeEffectiveGlassLuminance(
                            glassColorProvider.getBackgroundColor(), lastDateUnderLuma, true);
                    final boolean expectDark = GlassContrastHelper.computeLuminance(textColor) < 0.5f;
                    final float nextFactor = GlassContrastHelper.updateContrastInvertFactor(
                            dateInvertFactor, effLuma, expectDark, 0.28f);
                    if (Math.abs(nextFactor - dateInvertFactor) > 0.008f && !dateTickPosted) {
                        dateTickPosted = true;
                        postOnAnimation(invalidateSelfRunnable);
                    }
                    dateInvertFactor = nextFactor;
                    final int adaptedDateText = dateInvertFactor > 0.005f
                            ? GlassContrastHelper.blendAdaptiveTextColor(textColor, dateInvertFactor, false)
                            : 0;
                    final boolean needHalo = dateInvertFactor > 0.05f || (effLuma > 0.28f && effLuma < 0.72f);
                    setCustomDateAdaptiveStyle(adaptedDateText, needHalo);
                }
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

    private float getFloatingDateRestTop() {
        float top = chatListView != null && chatListView.getPaddingTop() > 0
                ? chatListView.getY() + chatListView.getPaddingTop() - dp(4)
                : AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() + getTopExtraOverlayHeight() + dp(4);
        return top + contentPanTranslation;
    }

    private void buildTopOverlays(Context context) {
        // Pinned note bar
        pinnedNoteBar = new FrameLayout(context);
        pinnedNoteBar.setVisibility(View.GONE);
        pinnedNoteBar.setPadding(dp(4), dp(4), dp(4), dp(4));
        pinnedNoteBar.setOutlineProvider(ViewOutlineProviderImpl.boundsWithPaddingRoundRect(dp(4), dp(16)));
        pinnedNoteBar.setClipToOutline(true);
        pinnedNoteBar.setBackground(glassBackgroundDrawableFactory.create(pinnedNoteBar)
                .setColorProvider(BlurredBackgroundProviderImpl.topPanelChatActivity(resourceProvider))
                .setRadius(dp(16))
                .setPadding(dp(4)));

        LinearLayout pinnedRow = new LinearLayout(context);
        pinnedRow.setOrientation(LinearLayout.HORIZONTAL);
        pinnedRow.setGravity(Gravity.CENTER_VERTICAL);
        pinnedRow.setPadding(LocaleController.isRTL ? dp(4) : dp(10), dp(4), LocaleController.isRTL ? dp(10) : dp(4), dp(4));

        pinnedNoteIconView = new ImageView(context);
        pinnedNoteIconView.setScaleType(ImageView.ScaleType.CENTER);
        pinnedNoteIconView.setImageResource(R.drawable.msg_pin);
        pinnedNoteIconView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelSend, resourceProvider), PorterDuff.Mode.SRC_IN));

        LinearLayout pinnedTexts = new LinearLayout(context);
        pinnedTexts.setOrientation(LinearLayout.VERTICAL);

        pinnedNoteTitleView = new TextView(context);
        pinnedNoteTitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        pinnedNoteTitleView.setTypeface(AndroidUtilities.bold());
        pinnedNoteTitleView.setTextColor(Theme.getColor(Theme.key_chat_messagePanelSend, resourceProvider));
        pinnedNoteTitleView.setText(getString(R.string.MirrlyNotesPinnedHeader));
        pinnedNoteTitleView.setSingleLine(true);
        pinnedNoteTitleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        pinnedTexts.addView(pinnedNoteTitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        pinnedNoteBodyView = new TextView(context);
        pinnedNoteBodyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        pinnedNoteBodyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        pinnedNoteBodyView.setSingleLine(true);
        pinnedNoteBodyView.setEllipsize(TextUtils.TruncateAt.END);
        pinnedNoteBodyView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        pinnedTexts.addView(pinnedNoteBodyView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        pinnedNoteUnpinBtn = new ImageView(context);
        pinnedNoteUnpinBtn.setScaleType(ImageView.ScaleType.CENTER);
        pinnedNoteUnpinBtn.setImageResource(R.drawable.msg_close);
        pinnedNoteUnpinBtn.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_topPanelClose, resourceProvider), PorterDuff.Mode.MULTIPLY));
        pinnedNoteUnpinBtn.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourceProvider), 1));
        pinnedNoteUnpinBtn.setOnClickListener(v -> {
            if (currentPinnedNote != null) {
                MirrlyVaultStorage.toggleNotePinned(currentPinnedNote.id);
                updatePinnedNoteBar(true);
                BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.MirrlyNotesUnpinnedToast)).show();
            }
        });

        if (LocaleController.isRTL) {
            pinnedRow.addView(pinnedNoteUnpinBtn, LayoutHelper.createLinear(32, 32));
            pinnedRow.addView(pinnedTexts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            pinnedRow.addView(pinnedNoteIconView, LayoutHelper.createLinear(22, 22, 8, 0, 0, 0));
        } else {
            pinnedRow.addView(pinnedNoteIconView, LayoutHelper.createLinear(22, 22, 0, 0, 8, 0));
            pinnedRow.addView(pinnedTexts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            pinnedRow.addView(pinnedNoteUnpinBtn, LayoutHelper.createLinear(32, 32));
        }

        pinnedNoteBar.addView(pinnedRow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        pinnedNoteBar.setOnClickListener(v -> {
            if (currentPinnedNote != null) {
                scrollToNoteId(currentPinnedNote.id, true);
            }
        });
        contentView.addView(pinnedNoteBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 8, 0, 8, 0));

        // Stationary glass capsule wrapping horizontally scrollable lightweight Canvas filter chips
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

        addFilterChip(context, MirrlyVaultStorage.FILTER_ALL, getString(R.string.MirrlyNotesFilterChipAll));
        addFilterChip(context, MirrlyVaultStorage.FILTER_MESSAGES, getString(R.string.MirrlyNotesFilterChipMessages));
        addFilterChip(context, MirrlyVaultStorage.FILTER_STANDALONE, getString(R.string.MirrlyNotesFilterChipStandalone));
        addFilterChip(context, MirrlyVaultStorage.FILTER_MEDIA, getString(R.string.MirrlyNotesFilterChipMedia));
        addFilterChip(context, MirrlyVaultStorage.FILTER_FILES, getString(R.string.MirrlyNotesFilterChipFiles));
        addFilterChip(context, MirrlyVaultStorage.FILTER_VOICE, getString(R.string.MirrlyNotesFilterChipVoice));
        addFilterChip(context, MirrlyVaultStorage.FILTER_SPOILER, getString(R.string.MirrlyNotesFilterChipSpoiler));

        filterChipsScroll.addView(filterChipsRow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)));
        filterChipsContainer.addView(filterChipsScroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.CENTER_VERTICAL));
        contentView.addView(filterChipsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.TOP, 8, 0, 8, 0));

        // Filter active pill outside search mode
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
            activeFilter = MirrlyVaultStorage.FILTER_ALL;
            updateFilterBanner(true);
            refreshFilterChipsSelection();
            reloadNotes(true);
        });
        contentView.addView(filterBanner, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 16, 0, 16, 0));
    }

    private void addFilterChip(Context context, int filterId, String label) {
        FilterChipView chip = new FilterChipView(context, filterId, label);
        ScaleStateListAnimator.apply(chip, 0.04f, 1.2f);
        MirrlyUiKit.attachDisallowInterceptTouch(chip);
        chip.setOnClickListener(v -> {
            applyFilter(filterId);
            updateSearchBottomBarState();
        });
        filterChipViews.add(chip);
        filterChipsRow.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 32, 3, 2, 3, 2));
    }

    private final class FilterChipView extends TextView {
        final int filterId;
        private boolean chipSelected;

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
            this.chipSelected = selected;
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
            setBackground(MirrlyUiKit.createTintedSurfaceDrawable(16, bg, border, resourceProvider, true));
        }

        boolean isChipSelected() {
            return chipSelected;
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

    private void buildBottomInputBar(Context context) {
        chatInputViewsContainer = new ChatInputViewsContainer(context);
        chatInputViewsContainer.setClipChildren(false);
        chatInputViewsContainer.setWindowInsetsProvider(windowInsetsStateHolder);
        chatInputViewsContainer.setInputIslandBubbleDrawable(
                glassBackgroundDrawableFactory.create(chatInputViewsContainer, blurredBackgroundColorProvider)
        );
        chatInputViewsContainer.setUnderKeyboardBackgroundDrawable(
                glassBackgroundDrawableFactoryFrosted.create(chatInputViewsContainer, blurredBackgroundColorProvider)
        );
        chatInputViewsContainer.setInputBubbleOffsets(dp(4), dp(4));

        chatInputBubbleContainer = chatInputViewsContainer.getInputIslandBubbleContainer();
        chatInputBubbleContainer.setClipChildren(false);
        chatInputInAppContainer = chatInputViewsContainer.getInAppKeyboardBubbleContainer();

        composerColumn = new LinearLayout(context);
        composerColumn.setOrientation(LinearLayout.VERTICAL);

        // Inline Edit Banner above EnterView
        editBannerRow = new LinearLayout(context);
        editBannerRow.setOrientation(LinearLayout.HORIZONTAL);
        editBannerRow.setGravity(Gravity.CENTER_VERTICAL);
        editBannerRow.setPadding(LocaleController.isRTL ? dp(8) : dp(14), dp(8), LocaleController.isRTL ? dp(14) : dp(8), dp(4));
        editBannerRow.setVisibility(View.GONE);

        View accentStripe = new View(context);
        editBannerAccentDrawable = new GradientDrawable();
        editBannerAccentDrawable.setShape(GradientDrawable.RECTANGLE);
        editBannerAccentDrawable.setCornerRadius(dpf2(1.5f));
        editBannerAccentDrawable.setColor(Theme.getColor(Theme.key_chat_messagePanelSend, resourceProvider));
        accentStripe.setBackground(editBannerAccentDrawable);

        LinearLayout editTextMeta = new LinearLayout(context);
        editTextMeta.setOrientation(LinearLayout.VERTICAL);

        editBannerTitle = new TextView(context);
        editBannerTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        editBannerTitle.setTypeface(AndroidUtilities.bold());
        editBannerTitle.setTextColor(Theme.getColor(Theme.key_chat_messagePanelSend, resourceProvider));
        editBannerTitle.setText(getString(R.string.MirrlyNotesEditBannerTitle));
        editBannerTitle.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        editBannerTitle.setSingleLine(true);
        editTextMeta.addView(editBannerTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        editBannerPreview = new TextView(context);
        editBannerPreview.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        editBannerPreview.setTextColor(Theme.getColor(Theme.key_chat_messagePanelHint, resourceProvider));
        editBannerPreview.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        editBannerPreview.setSingleLine(true);
        editBannerPreview.setEllipsize(TextUtils.TruncateAt.END);
        editTextMeta.addView(editBannerPreview, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        editBannerClose = new ImageView(context);
        editBannerClose.setScaleType(ImageView.ScaleType.CENTER);
        editBannerClose.setImageResource(R.drawable.msg_close);
        editBannerClose.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons, resourceProvider), PorterDuff.Mode.MULTIPLY));
        editBannerClose.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourceProvider), 1));
        editBannerClose.setOnClickListener(v -> cancelEditMode());

        if (LocaleController.isRTL) {
            editBannerRow.addView(editBannerClose, LayoutHelper.createLinear(34, 34));
            editBannerRow.addView(editTextMeta, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            editBannerRow.addView(accentStripe, LayoutHelper.createLinear(3, 28, 8, 0, 0, 0));
        } else {
            editBannerRow.addView(accentStripe, LayoutHelper.createLinear(3, 28, 0, 0, 8, 0));
            editBannerRow.addView(editTextMeta, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            editBannerRow.addView(editBannerClose, LayoutHelper.createLinear(34, 34));
        }

        composerColumn.addView(editBannerRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        chatActivityEnterView = new ChatActivityEnterView(getParentActivity(), contentView, null, true, resourceProvider) {
            @Override
            protected void onChangedIslandTotalHeight(float h) {
                syncInputBubbleHeight();
            }
        };
        chatActivityEnterView.shouldDrawBackground = false;
        chatActivityEnterView.setMinimumHeight(dp(51));
        chatActivityEnterView.setHostFragment(this);
        chatActivityEnterView.setInAppInsetsController(windowInsetsStateHolder);
        chatActivityEnterView.setViewParentForEmoji(chatInputInAppContainer);
        chatActivityEnterView.setDialogId(0L, currentAccount);
        chatActivityEnterView.setAllowStickersAndGifs(true, true, true);
        chatActivityEnterView.setCustomFieldHint(getString(R.string.MirrlyNotesInputHint));

        chatActivityEnterView.setDelegate(new ChatActivityEnterView.ChatActivityEnterViewDelegate() {
            @Override
            public void onMessageSend(CharSequence message, boolean notify, int scheduleDate, int scheduleRepeatPeriod, long payStars) {
                if (editingNote != null) {
                    editingNote = null;
                    if (editBannerRow != null) {
                        editBannerRow.setVisibility(View.GONE);
                    }
                    chatActivityEnterView.setForceShowSendButton(false, true);
                }
                reloadNotes(true);
                scrollToLatest();
                syncInputBubbleHeight();
            }

            @Override
            public void needSendTyping() {
            }

            @Override
            public void onTextChanged(CharSequence text, boolean bigChange, boolean fromDraft) {
                syncInputBubbleHeight();
            }

            @Override
            public void onTextSelectionChanged(int start, int end) {
            }

            @Override
            public void onTextSpansChanged(CharSequence text) {
            }

            @Override
            public void onAttachButtonHidden() {
            }

            @Override
            public void onAttachButtonShow() {
            }

            @Override
            public void onWindowSizeChanged(int size) {
                syncInputBubbleHeight();
            }

            @Override
            public void onStickersTab(boolean opened) {
            }

            @Override
            public void onMessageEditEnd(boolean loading) {
            }

            @Override
            public void didPressAttachButton() {
                openAttachMenu();
            }

            @Override
            public void needStartRecordVideo(int state, boolean notify, int scheduleDate, int scheduleRepeatPeriod, int ttl, long effectId, long stars) {
                checkInstantCameraView();
                if (instantCameraView != null) {
                    if (state == 0) {
                        instantCameraView.showCamera(false);
                        setRoundVideoOverlayVisible(true);
                    } else if (state == 1 || state == 3 || state == 4) {
                        instantCameraView.send(state, notify, scheduleDate, 0, ttl, effectId, stars);
                    } else if (state == 2 || state == 5) {
                        instantCameraView.cancel(state == 2);
                        setRoundVideoOverlayVisible(false);
                    }
                }
            }

            @Override
            public void toggleVideoRecordingPause() {
                if (instantCameraView != null) {
                    instantCameraView.togglePause();
                }
            }

            @Override
            public boolean isVideoRecordingPaused() {
                return instantCameraView != null && instantCameraView.isPaused();
            }

            @Override
            public void needChangeVideoPreviewState(int state, float seekProgress) {
                if (instantCameraView != null) {
                    instantCameraView.changeVideoPreviewState(state, seekProgress);
                }
            }

            @Override
            public void onSwitchRecordMode(boolean video) {
            }

            @Override
            public void onPreAudioVideoRecord() {
            }

            @Override
            public void needStartRecordAudio(int state) {
                if (overlayView != null) {
                    overlayView.setVisibility(state == 0 ? View.GONE : View.VISIBLE);
                }
            }

            @Override
            public void needShowMediaBanHint() {
            }

            @Override
            public void onStickersExpandedChange() {
                boolean isExpanded = chatActivityEnterView != null && chatActivityEnterView.isStickersExpanded();
                if (isExpanded) {
                    AndroidUtilities.setAdjustResizeToNothing(getParentActivity(), classGuid);
                } else {
                    AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
                }
            }

            @Override
            public void onUpdateSlowModeButton(View button, boolean show, CharSequence time) {
            }

            @Override
            public void onSendLongClick() {
            }

            @Override
            public void onAudioVideoInterfaceUpdated() {
                syncInputBubbleHeight();
            }

            @Override
            public int getContentViewHeight() {
                return contentView != null ? contentView.getMeasuredHeight() : 0;
            }

            @Override
            public int measureKeyboardHeight() {
                return contentView != null ? contentView.measureKeyboardHeight() : 0;
            }
        });

        chatActivityEnterView.setLocalSendInterceptor(new ChatActivityEnterView.LocalSendInterceptor() {
            @Override
            public void onSendText(CharSequence text, ArrayList<TLRPC.MessageEntity> entities) {
                saveLocalTextNote(text, entities);
            }

            @Override
            public void onSendVoice(TLRPC.TL_document audio, String path) {
                saveLocalVoiceNote(audio, path);
            }

            @Override
            public void onSendSticker(TLRPC.Document sticker, Object parent) {
                saveLocalStickerNote(sticker);
            }

            @Override
            public void onSendGif(Object gif, Object parent, @Nullable MediaController.PhotoEntry entry) {
                saveLocalGifNote(gif, entry);
            }

            @Override
            public void onSendMediaInfos(ArrayList<SendMessagesHelper.SendingMediaInfo> photos) {
                saveLocalMediaInfos(photos, false);
            }

            @Override
            public void onSendCommittedContent(Uri uri, @Nullable String mimeType) {
                saveLocalCommittedUri(uri, mimeType);
            }
        });

        composerColumn.addView(chatActivityEnterView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        composerColumn.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> syncInputBubbleHeight());
        chatInputBubbleContainer.addView(composerColumn, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 7, 0, 7, 0));

        // Search navigation bottom bar (replaces composer while searching)
        searchBottomBar = new LinearLayout(context);
        searchBottomBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBottomBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBottomBar.setPadding(dp(16), 0, dp(8), 0);
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

        chatInputBubbleContainer.addView(searchBottomBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM));

        // Multi-select ActionMode bottom bar
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
        actionModeForwardBtn.setOnClickListener(v -> forwardSelectedNotes());
        actionModeBottomBar.addView(actionModeForwardBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 6, 0));

        actionModeDeleteBtn = new TextView(context);
        actionModeDeleteBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        actionModeDeleteBtn.setTypeface(AndroidUtilities.bold());
        actionModeDeleteBtn.setGravity(Gravity.CENTER);
        actionModeDeleteBtn.setTextColor(Theme.getColor(Theme.key_text_RedRegular, resourceProvider));
        actionModeDeleteBtn.setText(getString(R.string.Delete));
        actionModeDeleteBtn.setPadding(dp(12), dp(8), dp(12), dp(8));
        actionModeDeleteBtn.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourceProvider), 2));
        actionModeDeleteBtn.setOnClickListener(v -> confirmDeleteSelectedNotes());
        actionModeBottomBar.addView(actionModeDeleteBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        chatInputBubbleContainer.addView(actionModeBottomBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM));

        contentView.addView(chatInputViewsContainer.getFadeView(), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        contentView.addView(chatInputViewsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    private void updateBottomModeBars() {
        if (composerColumn == null || searchBottomBar == null || actionModeBottomBar == null) {
            return;
        }
        boolean actionMode = actionBar != null && actionBar.isActionModeShowed();
        if (actionMode) {
            composerColumn.setVisibility(View.GONE);
            searchBottomBar.setVisibility(View.GONE);
            actionModeBottomBar.setVisibility(View.VISIBLE);
            actionModeCountView.setText(LocaleController.formatString(R.string.MirrlyNotesSelectedCount, selectedNoteIds.size()));
        } else if (isSearching) {
            composerColumn.setVisibility(View.GONE);
            actionModeBottomBar.setVisibility(View.GONE);
            searchBottomBar.setVisibility(View.VISIBLE);
            updateSearchBottomBarState();
        } else {
            searchBottomBar.setVisibility(View.GONE);
            actionModeBottomBar.setVisibility(View.GONE);
            composerColumn.setVisibility(View.VISIBLE);
        }
        syncInputBubbleHeight();
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
        if (chatListView == null || items.isEmpty()) {
            return;
        }
        ArrayList<Integer> matchPositions = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).isDate()) {
                matchPositions.add(i);
            }
        }
        if (matchPositions.isEmpty()) {
            return;
        }
        if (currentSearchNavPos < 0 || currentSearchNavPos >= matchPositions.size()) {
            currentSearchNavPos = delta > 0 ? 0 : matchPositions.size() - 1;
        } else {
            currentSearchNavPos = (currentSearchNavPos + delta + matchPositions.size()) % matchPositions.size();
        }
        int targetAdapterPos = matchPositions.get(currentSearchNavPos);
        chatListView.smoothScrollToPosition(targetAdapterPos);
        NoteItem target = items.get(targetAdapterPos);
        if (target.note != null) {
            flashHighlightNote(target.note.id);
        }
    }

    private void checkInstantCameraView() {
        if (instantCameraView != null || !org.telegram.messenger.camera.CameraView.isCameraAllowed() || getContext() == null) {
            return;
        }
        instantCameraView = InstantCameraViewBase.create(getContext(), new InstantCameraView.Delegate() {
            @Override
            public View getFragmentView() {
                return contentView;
            }

            @Override
            public void sendMedia(MediaController.PhotoEntry entry, VideoEditedInfo videoEditedInfo, boolean notify, int scheduleDate, int scheduleRepeatPeriod, boolean b1, long stars) {
                setRoundVideoOverlayVisible(false);
                if (entry != null && !TextUtils.isEmpty(entry.path)) {
                    saveLocalRoundVideoNote(entry, videoEditedInfo);
                }
                if (instantCameraView != null) {
                    instantCameraView.setIsMessageTransition(false);
                    instantCameraView.hideCamera(true);
                    instantCameraView.setVisibility(View.INVISIBLE);
                }
            }

            @Override
            public Activity getParentActivity() {
                return MirrlyNotesChatActivity.this.getParentActivity();
            }

            @Override
            public int getClassGuid() {
                return classGuid;
            }

            @Override
            public long getDialogId() {
                return 0L;
            }
        }, resourceProvider, true);

        instantCameraView.setTrimCallback((start, end) -> {
            if (chatActivityEnterView != null) {
                chatActivityEnterView.setVideoTimelineTrim(start, end);
            }
        });
        instantCameraView.setRecordingUiFrameCallback(new InstantCameraViewBase.RecordingUiFrameCallback() {
            @Override
            public void onActiveChanged(boolean active) {
                if (chatActivityEnterView != null) {
                    chatActivityEnterView.setRoundVideoUiFrameClockActive(active);
                }
            }

            @Override
            public void onFrame(long durationMs) {
                if (chatActivityEnterView != null) {
                    chatActivityEnterView.onRoundVideoUiFrame(durationMs);
                }
            }
        });
        instantCameraView.setClipToPadding(false);
        instantCameraView.setButtonsBackground(glassBackgroundDrawableFactory, blurredBackgroundColorProvider);

        int index = contentView.indexOfChild(chatInputViewsContainer);
        if (index < 0) {
            index = contentView.getChildCount();
        }
        contentView.addView(instantCameraView, index, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
    }

    private void setRoundVideoOverlayVisible(boolean visible) {
        if (roundVideoRecordBackground == null) {
            return;
        }
        roundVideoRecordBackground.animate().cancel();
        if (visible) {
            roundVideoRecordBackground.setVisibility(View.VISIBLE);
            roundVideoRecordBackground.setAlpha(0f);
            roundVideoRecordBackground.animate().alpha(1f).setDuration(200).start();
        } else {
            roundVideoRecordBackground.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                if (roundVideoRecordBackground != null) {
                    roundVideoRecordBackground.setVisibility(View.GONE);
                }
            }).start();
        }
    }

    private void openAttachMenu() {
        if (getParentActivity() == null) {
            return;
        }
        createChatAttachView();
        chatAttachAlert.getPhotoLayout().loadGalleryPhotos();
        if (Build.VERSION.SDK_INT == 21 || Build.VERSION.SDK_INT == 22) {
            chatActivityEnterView.closeKeyboard();
        }
        chatAttachAlert.setMaxSelectedPhotos(-1, true);
        chatAttachAlert.enableDefaultMode();
        chatAttachAlert.init();
        if (chatActivityEnterView != null) {
            chatAttachAlert.getCommentView().setText(chatActivityEnterView.getFieldText());
        }
        showDialog(chatAttachAlert);
    }

    private void createChatAttachView() {
        if (getParentActivity() == null || getContext() == null || chatAttachAlert != null) {
            return;
        }
        chatAttachAlert = new ChatAttachAlert(getParentActivity(), this, false, false, true, resourceProvider) {
            @Override
            public void dismissInternal() {
                if (chatAttachAlert != null && chatAttachAlert.isShowing()) {
                    AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
                }
                super.dismissInternal();
                onAttachDialogClosed(true);
                if (contentView != null) {
                    ViewCompat.requestApplyInsets(contentView);
                }
            }

            @Override
            public void onDismissAnimationStart() {
                boolean restoreKeyboard = getCurrentAttachLayout().shouldRestoreKeyboardOnDismiss();
                if (!restoreKeyboard) {
                    openKeyboardOnAttachMenuClose = false;
                    if (chatActivityEnterView != null) {
                        chatActivityEnterView.closeKeyboard();
                        if (chatActivityEnterView.getEditField() != null) {
                            chatActivityEnterView.getEditField().clearFocus();
                        }
                    }
                }
                if (chatAttachAlert != null) {
                    chatAttachAlert.setFocusable(false);
                }
                if (restoreKeyboard && chatActivityEnterView != null && chatActivityEnterView.getEditField() != null) {
                    chatActivityEnterView.getEditField().requestFocus();
                }
                if (chatAttachAlert != null && chatAttachAlert.isShowing()) {
                    AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
                }
                onAttachDialogClosed(false);
                if (contentView != null) {
                    ViewCompat.requestApplyInsets(contentView);
                }
            }
        };
        chatAttachAlert.setDialogId(0L);
        chatAttachAlert.setAllowEnterCaption(true);
        chatAttachAlert.allowLivePhotos = true;

        chatAttachAlert.setDelegate(new ChatAttachAlert.ChatAttachViewDelegate() {
            @Override
            public void didPressedButton(int button, boolean arg, boolean notify, int scheduleDate, int scheduleRepeatPeriod, long effectId, boolean invertMedia, boolean forceDocument, long payStars) {
                if (getParentActivity() == null || chatAttachAlert == null) {
                    return;
                }
                if (button == 8 || button == 7 || button == 4 && !chatAttachAlert.getPhotoLayout().getSelectedPhotos().isEmpty()) {
                    if (button != 8) {
                        chatAttachAlert.dismiss(true);
                    }
                    HashMap<Object, Object> selectedPhotos = chatAttachAlert.getPhotoLayout().getSelectedPhotos();
                    ArrayList<Object> selectedPhotosOrder = chatAttachAlert.getPhotoLayout().getSelectedPhotosOrder();
                    if (!selectedPhotos.isEmpty()) {
                        ArrayList<SendMessagesHelper.SendingMediaInfo> photos = new ArrayList<>();
                        for (int a = 0; a < selectedPhotosOrder.size(); a++) {
                            MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) selectedPhotos.get(selectedPhotosOrder.get(a));
                            if (photoEntry == null) {
                                continue;
                            }
                            SendMessagesHelper.SendingMediaInfo info = new SendMessagesHelper.SendingMediaInfo();
                            boolean isVideo = photoEntry.isVideo;
                            if (!isVideo && photoEntry.imagePath != null) {
                                info.path = photoEntry.imagePath;
                            } else if (photoEntry.path != null) {
                                info.path = photoEntry.path;
                            }
                            info.thumbPath = photoEntry.thumbPath;
                            info.coverPath = photoEntry.coverPath;
                            info.isVideo = isVideo;
                            info.caption = photoEntry.caption != null ? photoEntry.caption.toString() : null;
                            info.entities = photoEntry.entities;
                            info.videoEditedInfo = photoEntry.editedInfo;
                            info.hasMediaSpoilers = photoEntry.hasSpoiler;
                            photos.add(info);
                            photoEntry.reset();
                        }
                        saveLocalMediaInfos(photos, button == 4 || forceDocument);
                        if (chatActivityEnterView != null) {
                            chatActivityEnterView.setFieldText("");
                        }
                    }
                    return;
                }
                chatAttachAlert.dismissWithButtonClick(button);
                processSelectedAttachButton(button);
            }

            @Override
            public View getRevealView() {
                return chatActivityEnterView != null ? chatActivityEnterView.getAttachButton() : null;
            }

            @Override
            public void onCameraOpened() {
                if (chatActivityEnterView != null) {
                    chatActivityEnterView.closeKeyboard();
                }
            }

            @Override
            public boolean needEnterComment() {
                if (chatActivityEnterView == null) {
                    return false;
                }
                boolean visible = chatActivityEnterView.isKeyboardVisible();
                if (visible) {
                    chatActivityEnterView.showEmojiView();
                    openKeyboardOnAttachMenuClose = true;
                }
                AndroidUtilities.setAdjustResizeToNothing(getParentActivity(), classGuid);
                if (contentView != null) {
                    contentView.requestLayout();
                }
                return visible;
            }

            @Override
            public void didSelectSearchPhotos(ArrayList<SendMessagesHelper.SendingMediaInfo> photos, boolean notify, int scheduleDate) {
                saveLocalMediaInfos(photos, false);
            }
        });

        chatAttachAlert.setDocumentsDelegate(new ChatAttachAlertDocumentLayout.DocumentSelectActivityDelegate() {
            @Override
            public void didSelectFiles(ArrayList<String> files, String caption, ArrayList<TLRPC.MessageEntity> captionEntities, ArrayList<MessageObject> fmessages, boolean notify, int scheduleDate, int scheduleRepeatPeriod, long effectId, boolean invertMedia, long payStars) {
                saveLocalDocumentPaths(files, caption, captionEntities);
                if (fmessages != null && !fmessages.isEmpty()) {
                    for (int i = 0; i < fmessages.size(); i++) {
                        MessageObject mo = fmessages.get(i);
                        if (mo == null) {
                            continue;
                        }
                        File f = FileLoader.getInstance(currentAccount).getPathToMessage(mo.messageOwner);
                        if (f != null && f.exists()) {
                            ArrayList<String> single = new ArrayList<>();
                            single.add(f.getAbsolutePath());
                            saveLocalDocumentPaths(single, i == 0 ? caption : null, i == 0 ? captionEntities : null);
                        }
                    }
                }
            }

            @Override
            public void didSelectPhotos(ArrayList<SendMessagesHelper.SendingMediaInfo> photos, boolean notify, int scheduleDate, int scheduleRepeatPeriod, long payStars) {
                saveLocalMediaInfos(photos, false);
            }

            @Override
            public void startDocumentSelectActivity() {
                try {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    intent.setType("*/*");
                    startActivityForResult(intent, 21);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });

        chatAttachAlert.setAudioSelectDelegate((audios, caption, notify, scheduleDate, scheduleRepeatPeriod, effectId, invertMedia, payStars) ->
                saveLocalAudioMessages(audios, caption)
        );

        chatAttachAlert.setLocationActivityDelegate((location, live, notify, scheduleDate, payStars) ->
                saveLocalGeoNote(location)
        );

        chatAttachAlert.setContactsDelegate(new ChatAttachAlertContactsLayout.PhonebookShareAlertDelegate() {
            @Override
            public void didSelectContact(TLRPC.User user, boolean notify, int scheduleDate, long effectId, boolean invertMedia, long payStars) {
                saveLocalContactNote(user, null);
            }

            @Override
            public void didSelectContacts(ArrayList<TLRPC.User> users, String caption, boolean notify, int scheduleDate, long effectId, boolean invertMedia, long payStars) {
                if (users != null) {
                    for (int i = 0; i < users.size(); i++) {
                        saveLocalContactNote(users.get(i), i == 0 ? caption : null);
                    }
                }
            }
        });

        chatAttachAlert.setPollDelegate((poll, caption, media, params, notify, scheduleDate, payStars) ->
                saveLocalPollNote(poll, caption)
        );
        chatAttachAlert.setTodoDelegate((poll, caption, media, params, notify, scheduleDate, payStars) ->
                saveLocalPollNote(poll, caption)
        );

        chatAttachAlert.setEmojiViewDelegate(new EmojiView.EmojiViewDelegate() {
            @Override
            public void onStickerSelected(View view, TLRPC.Document sticker, String query, Object parent, MessageObject.SendAnimationData sendAnimationData, boolean notify, int scheduleDate, int scheduleRepeatPeriod) {
                saveLocalStickerNote(sticker);
            }

            @Override
            public void onGifSelected(View view, Object gif, String query, Object parent, boolean notify, int scheduleDate, int scheduleRepeatPeriod) {
                saveLocalGifNote(gif, null);
            }
        });
    }

    private void onAttachDialogClosed(boolean reset) {
        if (openKeyboardOnAttachMenuClose && chatActivityEnterView != null) {
            AndroidUtilities.runOnUIThread(() -> {
                if (chatActivityEnterView != null) {
                    chatActivityEnterView.openKeyboard();
                }
            }, 200);
            if (reset) {
                openKeyboardOnAttachMenuClose = false;
            }
        }
    }

    private void processSelectedAttachButton(int which) {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        if (which == 0) {
            if (Build.VERSION.SDK_INT >= 23 && activity.checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[]{android.Manifest.permission.CAMERA}, 19);
                return;
            }
            try {
                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                File image = AndroidUtilities.generatePicturePath();
                if (image != null) {
                    if (Build.VERSION.SDK_INT >= 24) {
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", image));
                        takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } else {
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(image));
                    }
                    currentPicturePath = image.getAbsolutePath();
                }
                startActivityForResult(takePictureIntent, 0);
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else if (which == 2) {
            if (Build.VERSION.SDK_INT >= 23 && activity.checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[]{android.Manifest.permission.CAMERA}, BasePermissionsActivity.REQUEST_CODE_OPEN_CAMERA);
                return;
            }
            try {
                Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                File video = AndroidUtilities.generateVideoPath();
                if (video != null) {
                    if (Build.VERSION.SDK_INT >= 24) {
                        takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", video));
                        takeVideoIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } else {
                        takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(video));
                    }
                    takeVideoIntent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, FileLoader.DEFAULT_MAX_FILE_SIZE);
                    currentPicturePath = video.getAbsolutePath();
                }
                startActivityForResult(takeVideoIntent, 2);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            return;
        }
        if (requestCode == 0 || requestCode == 2) {
            createChatAttachView();
            if (chatAttachAlert != null) {
                chatAttachAlert.getPhotoLayout().onActivityResultFragment(requestCode, data, currentPicturePath);
            }
            currentPicturePath = null;
        } else if (requestCode == 21 && data != null) {
            ArrayList<Uri> uris = new ArrayList<>();
            if (data.getData() != null) {
                uris.add(data.getData());
            } else if (data.getClipData() != null) {
                ClipData clip = data.getClipData();
                for (int i = 0; i < clip.getItemCount(); i++) {
                    Uri u = clip.getItemAt(i).getUri();
                    if (u != null) {
                        uris.add(u);
                    }
                }
            }
            if (!uris.isEmpty()) {
                for (int i = 0; i < uris.size(); i++) {
                    saveLocalDocumentUri(uris.get(i), null, null);
                }
                if (chatAttachAlert != null) {
                    chatAttachAlert.dismiss();
                }
            }
        } else if (requestCode == 28 && chatAttachAlert != null) {
            chatAttachAlert.onPollAttachFilePicker(data);
        }
    }

    // ==================== Local Note & Media Creation (Zero Cloud Upload) ====================

    private void saveLocalTextNote(CharSequence text, @Nullable ArrayList<TLRPC.MessageEntity> entities) {
        String str = text != null ? text.toString().trim() : "";
        String entitiesB64 = MirrlyVaultStorage.encodeEntities(entities);
        if (editingNote != null) {
            MirrlyVaultStorage.DossierNote target = editingNote;
            if (TextUtils.isEmpty(str) && !target.hasMedia()) {
                deleteNoteWithEffect(null, target);
                cancelEditMode();
                return;
            }
            MirrlyVaultStorage.updateNoteById(target.id, str, entitiesB64, target.spoiler);
            cancelEditMode();
            reloadNotes(true);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.ic_save_to_gallery, getString(R.string.MirrlyNotesUpdatedToast)).show();
            return;
        }
        if (TextUtils.isEmpty(str)) {
            return;
        }
        MirrlyVaultStorage.createRichStandaloneNote(
                str,
                entitiesB64,
                false,
                MirrlyVaultStorage.MEDIA_NONE,
                "",
                "",
                "",
                "",
                "",
                0,
                0,
                0,
                0,
                0L
        );
        scrollToLatest();
    }

    private void saveLocalVoiceNote(@Nullable TLRPC.TL_document audio, @Nullable String path) {
        if (audio == null || TextUtils.isEmpty(path)) {
            return;
        }
        String savedPath = MirrlyVaultStorage.copyFileToNotesMedia(path, null, "voice", "ogg");
        if (TextUtils.isEmpty(savedPath)) {
            return;
        }
        int dur = 0;
        for (int i = 0; i < audio.attributes.size(); i++) {
            TLRPC.DocumentAttribute attr = audio.attributes.get(i);
            if (attr instanceof TLRPC.TL_documentAttributeAudio) {
                dur = (int) Math.round(attr.duration);
                break;
            }
        }
        TLRPC.TL_messageMediaDocument media = new TLRPC.TL_messageMediaDocument();
        media.flags |= 1;
        media.document = audio;
        String b64 = MirrlyVaultStorage.encodeMedia(media);
        MirrlyVaultStorage.createRichStandaloneNote(
                "",
                "",
                false,
                MirrlyVaultStorage.MEDIA_VOICE,
                savedPath,
                "",
                b64,
                "voice.ogg",
                audio.mime_type != null ? audio.mime_type : "audio/ogg",
                dur,
                (int) new File(savedPath).length(),
                0,
                0,
                0L
        );
        scrollToLatest();
    }

    private void saveLocalRoundVideoNote(MediaController.PhotoEntry entry, @Nullable VideoEditedInfo editedInfo) {
        String savedPath = MirrlyVaultStorage.copyFileToNotesMedia(entry.path, null, "round", "mp4");
        if (TextUtils.isEmpty(savedPath)) {
            return;
        }
        Bitmap thumbBmp = SendMessagesHelper.createVideoThumbnail(savedPath, MediaStore.Video.Thumbnails.MINI_KIND);
        String thumbPath = MirrlyVaultStorage.saveBitmapToNotesThumb(thumbBmp, "round");

        int w = editedInfo != null && editedInfo.resultWidth > 0 ? editedInfo.resultWidth : 384;
        int h = editedInfo != null && editedInfo.resultHeight > 0 ? editedInfo.resultHeight : 384;
        double durSec = editedInfo != null && editedInfo.estimatedDuration > 0 ? editedInfo.estimatedDuration / 1000.0 : Math.max(1.0, entry.duration);

        TLRPC.TL_document doc = new TLRPC.TL_document();
        doc.mime_type = "video/mp4";
        doc.date = (int) (System.currentTimeMillis() / 1000);
        doc.size = (int) new File(savedPath).length();
        doc.file_reference = new byte[0];
        TLRPC.TL_documentAttributeVideo attr = new TLRPC.TL_documentAttributeVideo();
        attr.round_message = true;
        attr.supports_streaming = true;
        attr.w = w;
        attr.h = h;
        attr.duration = durSec;
        doc.attributes.add(attr);

        TLRPC.TL_messageMediaDocument media = new TLRPC.TL_messageMediaDocument();
        media.flags |= 1;
        media.document = doc;

        MirrlyVaultStorage.createRichStandaloneNote(
                "",
                "",
                false,
                MirrlyVaultStorage.MEDIA_ROUND,
                savedPath,
                thumbPath,
                MirrlyVaultStorage.encodeMedia(media),
                "round.mp4",
                "video/mp4",
                (int) Math.round(durSec),
                (int) doc.size,
                w,
                h,
                0L
        );
        scrollToLatest();
    }

    private void saveLocalStickerNote(@Nullable TLRPC.Document sticker) {
        if (sticker == null) {
            return;
        }
        TLRPC.TL_messageMediaDocument media = new TLRPC.TL_messageMediaDocument();
        media.flags |= 1;
        media.document = sticker;
        MirrlyVaultStorage.createRichStandaloneNote(
                "",
                "",
                false,
                MirrlyVaultStorage.MEDIA_STICKER,
                "",
                "",
                MirrlyVaultStorage.encodeMedia(media),
                FileLoader.getDocumentFileName(sticker),
                sticker.mime_type,
                0,
                (int) sticker.size,
                0,
                0,
                0L
        );
        scrollToLatest();
    }

    private void saveLocalGifNote(@Nullable Object gif, @Nullable MediaController.PhotoEntry entry) {
        TLRPC.Document doc = null;
        if (gif instanceof TLRPC.Document) {
            doc = (TLRPC.Document) gif;
        } else if (gif instanceof TLRPC.BotInlineResult) {
            TLRPC.BotInlineResult res = (TLRPC.BotInlineResult) gif;
            if (res.document != null) {
                doc = res.document;
            } else if (res.content != null && !TextUtils.isEmpty(res.content.url)) {
                saveLocalTextNote(res.content.url, null);
                return;
            }
        }
        if (doc == null) {
            return;
        }
        TLRPC.TL_messageMediaDocument media = new TLRPC.TL_messageMediaDocument();
        media.flags |= 1;
        media.document = doc;
        String caption = entry != null && entry.caption != null ? entry.caption.toString() : "";
        String entitiesB64 = entry != null ? MirrlyVaultStorage.encodeEntities(entry.entities) : "";
        MirrlyVaultStorage.createRichStandaloneNote(
                caption,
                entitiesB64,
                false,
                MirrlyVaultStorage.MEDIA_GIF,
                "",
                "",
                MirrlyVaultStorage.encodeMedia(media),
                FileLoader.getDocumentFileName(doc),
                doc.mime_type,
                0,
                (int) doc.size,
                0,
                0,
                0L
        );
        scrollToLatest();
    }

    private void saveLocalMediaInfos(@Nullable ArrayList<SendMessagesHelper.SendingMediaInfo> photos, boolean forceDocument) {
        if (photos == null || photos.isEmpty()) {
            return;
        }
        long groupedId = photos.size() > 1 ? Utilities.random.nextLong() : 0L;
        for (int i = 0; i < photos.size(); i++) {
            SendMessagesHelper.SendingMediaInfo info = photos.get(i);
            if (info == null) {
                continue;
            }
            String caption = info.caption != null ? info.caption : "";
            String entitiesB64 = MirrlyVaultStorage.encodeEntities(info.entities);

            if (forceDocument) {
                String name = !TextUtils.isEmpty(info.path) ? new File(info.path).getName() : "file";
                String ext = "";
                int dot = name.lastIndexOf('.');
                if (dot >= 0 && dot < name.length() - 1) {
                    ext = name.substring(dot + 1);
                }
                String savedPath = MirrlyVaultStorage.copyFileToNotesMedia(info.path, info.uri, "doc", TextUtils.isEmpty(ext) ? "dat" : ext);
                if (!TextUtils.isEmpty(savedPath)) {
                    saveCopiedDocumentNote(savedPath, name, resolveMimeFromExt(ext), caption, entitiesB64, info.hasMediaSpoilers, groupedId);
                }
                continue;
            }

            if (info.isVideo) {
                String savedPath = MirrlyVaultStorage.copyFileToNotesMedia(info.path, info.uri, "video", "mp4");
                if (TextUtils.isEmpty(savedPath)) {
                    continue;
                }
                Bitmap thumbBmp = SendMessagesHelper.createVideoThumbnail(savedPath, MediaStore.Video.Thumbnails.MINI_KIND);
                String thumbPath = MirrlyVaultStorage.saveBitmapToNotesThumb(thumbBmp, "video");

                int[] meta = readVideoMetadata(savedPath, info.videoEditedInfo);
                int w = meta[0];
                int h = meta[1];
                int durSec = meta[2];

                TLRPC.TL_document doc = new TLRPC.TL_document();
                doc.mime_type = "video/mp4";
                doc.date = (int) (System.currentTimeMillis() / 1000);
                doc.size = (int) new File(savedPath).length();
                doc.file_reference = new byte[0];
                TLRPC.TL_documentAttributeVideo attr = new TLRPC.TL_documentAttributeVideo();
                attr.supports_streaming = true;
                attr.w = w;
                attr.h = h;
                attr.duration = Math.max(1, durSec);
                doc.attributes.add(attr);
                TLRPC.TL_documentAttributeFilename fname = new TLRPC.TL_documentAttributeFilename();
                fname.file_name = new File(savedPath).getName();
                doc.attributes.add(fname);

                TLRPC.TL_messageMediaDocument media = new TLRPC.TL_messageMediaDocument();
                media.flags |= 1;
                media.document = doc;
                media.spoiler = info.hasMediaSpoilers;

                MirrlyVaultStorage.createRichStandaloneNote(
                        caption,
                        entitiesB64,
                        info.hasMediaSpoilers,
                        MirrlyVaultStorage.MEDIA_VIDEO,
                        savedPath,
                        thumbPath,
                        MirrlyVaultStorage.encodeMedia(media),
                        fname.file_name,
                        "video/mp4",
                        durSec,
                        (int) doc.size,
                        w,
                        h,
                        groupedId
                );
            } else {
                String savedPath = MirrlyVaultStorage.copyFileToNotesMedia(info.path, info.uri, "photo", "jpg");
                if (TextUtils.isEmpty(savedPath)) {
                    continue;
                }
                TLRPC.TL_photo tlPhoto = SendMessagesHelper.getInstance(currentAccount).generatePhotoSizes(savedPath, null);
                int[] dims = readImageDimensions(savedPath);
                if (tlPhoto == null) {
                    tlPhoto = createFallbackLocalPhoto(dims[0], dims[1]);
                }
                TLRPC.TL_messageMediaPhoto media = new TLRPC.TL_messageMediaPhoto();
                media.flags |= 1;
                media.photo = tlPhoto;
                media.spoiler = info.hasMediaSpoilers;

                MirrlyVaultStorage.createRichStandaloneNote(
                        caption,
                        entitiesB64,
                        info.hasMediaSpoilers,
                        MirrlyVaultStorage.MEDIA_PHOTO,
                        savedPath,
                        "",
                        MirrlyVaultStorage.encodeMedia(media),
                        new File(savedPath).getName(),
                        "image/jpeg",
                        0,
                        (int) new File(savedPath).length(),
                        dims[0],
                        dims[1],
                        groupedId
                );
            }
        }
        scrollToLatest();
    }

    private void saveLocalDocumentPaths(@Nullable ArrayList<String> files, @Nullable String caption, @Nullable ArrayList<TLRPC.MessageEntity> entities) {
        if (files == null || files.isEmpty()) {
            return;
        }
        String entitiesB64 = MirrlyVaultStorage.encodeEntities(entities);
        for (int i = 0; i < files.size(); i++) {
            String src = files.get(i);
            if (TextUtils.isEmpty(src)) {
                continue;
            }
            File srcFile = new File(src);
            String origName = srcFile.getName();
            String ext = "";
            int dot = origName.lastIndexOf('.');
            if (dot >= 0 && dot < origName.length() - 1) {
                ext = origName.substring(dot + 1).toLowerCase(Locale.ROOT);
            }
            String savedPath = MirrlyVaultStorage.copyFileToNotesMedia(src, null, "doc", TextUtils.isEmpty(ext) ? "dat" : ext);
            if (TextUtils.isEmpty(savedPath)) {
                continue;
            }
            saveCopiedDocumentNote(savedPath, origName, resolveMimeFromExt(ext), i == 0 ? caption : "", i == 0 ? entitiesB64 : "", false, 0L);
        }
        scrollToLatest();
    }

    private void saveLocalDocumentUri(Uri uri, @Nullable String caption, @Nullable String entitiesB64) {
        if (uri == null || getContext() == null) {
            return;
        }
        String origName = queryDisplayName(getContext(), uri);
        String mime = getContext().getContentResolver() != null ? getContext().getContentResolver().getType(uri) : null;
        String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        if (TextUtils.isEmpty(ext) && origName != null) {
            int dot = origName.lastIndexOf('.');
            if (dot >= 0 && dot < origName.length() - 1) {
                ext = origName.substring(dot + 1);
            }
        }
        String savedPath = MirrlyVaultStorage.copyFileToNotesMedia(null, uri, "doc", TextUtils.isEmpty(ext) ? "dat" : ext);
        if (TextUtils.isEmpty(savedPath)) {
            return;
        }
        String displayName = !TextUtils.isEmpty(origName) ? origName : new File(savedPath).getName();
        saveCopiedDocumentNote(savedPath, displayName, !TextUtils.isEmpty(mime) ? mime : resolveMimeFromExt(ext), caption, entitiesB64, false, 0L);
        scrollToLatest();
    }

    private void saveCopiedDocumentNote(
            String savedPath,
            String displayName,
            String mimeType,
            @Nullable String caption,
            @Nullable String entitiesB64,
            boolean spoiler,
            long groupedId
    ) {
        TLRPC.TL_document doc = new TLRPC.TL_document();
        doc.mime_type = !TextUtils.isEmpty(mimeType) ? mimeType : "application/octet-stream";
        doc.date = (int) (System.currentTimeMillis() / 1000);
        doc.size = (int) new File(savedPath).length();
        doc.file_reference = new byte[0];
        TLRPC.TL_documentAttributeFilename fname = new TLRPC.TL_documentAttributeFilename();
        fname.file_name = !TextUtils.isEmpty(displayName) ? displayName : new File(savedPath).getName();
        doc.attributes.add(fname);

        TLRPC.TL_messageMediaDocument media = new TLRPC.TL_messageMediaDocument();
        media.flags |= 1;
        media.document = doc;

        MirrlyVaultStorage.createRichStandaloneNote(
                caption,
                entitiesB64,
                spoiler,
                MirrlyVaultStorage.MEDIA_DOCUMENT,
                savedPath,
                "",
                MirrlyVaultStorage.encodeMedia(media),
                fname.file_name,
                doc.mime_type,
                0,
                (int) doc.size,
                0,
                0,
                groupedId
        );
    }

    private void saveLocalAudioMessages(@Nullable ArrayList<MessageObject> audios, @Nullable CharSequence caption) {
        if (audios == null || audios.isEmpty()) {
            return;
        }
        String cap = caption != null ? caption.toString() : "";
        for (int i = 0; i < audios.size(); i++) {
            MessageObject mo = audios.get(i);
            if (mo == null) {
                continue;
            }
            String srcPath = mo.messageOwner != null && !TextUtils.isEmpty(mo.messageOwner.attachPath) ? mo.messageOwner.attachPath : null;
            if (TextUtils.isEmpty(srcPath) && mo.messageOwner != null) {
                File f = FileLoader.getInstance(currentAccount).getPathToMessage(mo.messageOwner);
                if (f != null && f.exists()) {
                    srcPath = f.getAbsolutePath();
                }
            }
            if (TextUtils.isEmpty(srcPath)) {
                continue;
            }
            String savedPath = MirrlyVaultStorage.copyFileToNotesMedia(srcPath, null, "audio", "mp3");
            if (TextUtils.isEmpty(savedPath)) {
                continue;
            }
            TLRPC.Document srcDoc = mo.getDocument();
            TLRPC.TL_document doc = new TLRPC.TL_document();
            doc.mime_type = srcDoc != null && !TextUtils.isEmpty(srcDoc.mime_type) ? srcDoc.mime_type : "audio/mpeg";
            doc.date = (int) (System.currentTimeMillis() / 1000);
            doc.size = (int) new File(savedPath).length();
            doc.file_reference = new byte[0];
            if (srcDoc != null) {
                for (int a = 0; a < srcDoc.attributes.size(); a++) {
                    doc.attributes.add(srcDoc.attributes.get(a));
                }
            }
            TLRPC.TL_messageMediaDocument media = new TLRPC.TL_messageMediaDocument();
            media.flags |= 1;
            media.document = doc;

            MirrlyVaultStorage.createRichStandaloneNote(
                    i == 0 ? cap : "",
                    "",
                    false,
                    MirrlyVaultStorage.MEDIA_AUDIO,
                    savedPath,
                    "",
                    MirrlyVaultStorage.encodeMedia(media),
                    !TextUtils.isEmpty(mo.getDocumentName()) ? mo.getDocumentName() : new File(savedPath).getName(),
                    doc.mime_type,
                    (int) Math.round(mo.getDuration()),
                    (int) doc.size,
                    0,
                    0,
                    0L
            );
        }
        scrollToLatest();
    }

    private void saveLocalCommittedUri(@Nullable Uri uri, @Nullable String mimeType) {
        if (uri == null) {
            return;
        }
        String mime = mimeType != null ? mimeType.toLowerCase(Locale.ROOT) : "";
        if (mime.contains("gif") || SendMessagesHelper.shouldSendWebPAsSticker(null, uri)) {
            String ext = mime.contains("webp") ? "webp" : "gif";
            String savedPath = MirrlyVaultStorage.copyFileToNotesMedia(null, uri, "gif", ext);
            if (TextUtils.isEmpty(savedPath)) {
                return;
            }
            int[] dims = readImageDimensions(savedPath);
            TLRPC.TL_document doc = new TLRPC.TL_document();
            doc.mime_type = mime.isEmpty() ? "image/gif" : mime;
            doc.date = (int) (System.currentTimeMillis() / 1000);
            doc.size = (int) new File(savedPath).length();
            doc.file_reference = new byte[0];
            doc.attributes.add(new TLRPC.TL_documentAttributeAnimated());
            TLRPC.TL_documentAttributeImageSize sizeAttr = new TLRPC.TL_documentAttributeImageSize();
            sizeAttr.w = Math.max(256, dims[0]);
            sizeAttr.h = Math.max(256, dims[1]);
            doc.attributes.add(sizeAttr);
            TLRPC.TL_messageMediaDocument media = new TLRPC.TL_messageMediaDocument();
            media.flags |= 1;
            media.document = doc;
            MirrlyVaultStorage.createRichStandaloneNote(
                    "", "", false, MirrlyVaultStorage.MEDIA_GIF,
                    savedPath, "", MirrlyVaultStorage.encodeMedia(media),
                    new File(savedPath).getName(), doc.mime_type, 0, (int) doc.size, dims[0], dims[1], 0L
            );
            scrollToLatest();
        } else {
            SendMessagesHelper.SendingMediaInfo info = new SendMessagesHelper.SendingMediaInfo();
            info.uri = uri;
            ArrayList<SendMessagesHelper.SendingMediaInfo> list = new ArrayList<>(1);
            list.add(info);
            saveLocalMediaInfos(list, false);
        }
    }

    private void saveLocalGeoNote(@Nullable TLRPC.MessageMedia location) {
        if (location == null) {
            return;
        }
        MirrlyVaultStorage.createRichStandaloneNote(
                "",
                "",
                false,
                MirrlyVaultStorage.MEDIA_GEO,
                "",
                "",
                MirrlyVaultStorage.encodeMedia(location),
                "",
                "",
                0,
                0,
                0,
                0,
                0L
        );
        scrollToLatest();
    }

    private void saveLocalContactNote(@Nullable TLRPC.User user, @Nullable String caption) {
        if (user == null) {
            return;
        }
        TLRPC.TL_messageMediaContact media = new TLRPC.TL_messageMediaContact();
        media.phone_number = user.phone != null ? user.phone : "";
        media.first_name = user.first_name != null ? user.first_name : "";
        media.last_name = user.last_name != null ? user.last_name : "";
        media.user_id = user.id;
        media.vcard = "";
        MirrlyVaultStorage.createRichStandaloneNote(
                caption != null ? caption : "",
                "",
                false,
                MirrlyVaultStorage.MEDIA_CONTACT,
                "",
                "",
                MirrlyVaultStorage.encodeMedia(media),
                UserObject.getUserName(user),
                "",
                0,
                0,
                0,
                0,
                0L
        );
        scrollToLatest();
    }

    private void saveLocalPollNote(@Nullable TLRPC.MessageMedia pollMedia, @Nullable CharSequence caption) {
        if (pollMedia == null) {
            return;
        }
        if (pollMedia instanceof TLRPC.TL_messageMediaPoll) {
            TLRPC.TL_messageMediaPoll mp = (TLRPC.TL_messageMediaPoll) pollMedia;
            if (mp.results == null) {
                mp.results = new TLRPC.TL_pollResults();
            }
            if (mp.poll != null && mp.results.results.isEmpty()) {
                for (int i = 0; i < mp.poll.answers.size(); i++) {
                    TLRPC.TL_pollAnswerVoters v = new TLRPC.TL_pollAnswerVoters();
                    v.option = mp.poll.answers.get(i).option;
                    mp.results.results.add(v);
                }
            }
        }
        MirrlyVaultStorage.createRichStandaloneNote(
                caption != null ? caption.toString() : "",
                "",
                false,
                MirrlyVaultStorage.MEDIA_POLL,
                "",
                "",
                MirrlyVaultStorage.encodeMedia(pollMedia),
                "",
                "",
                0,
                0,
                0,
                0,
                0L
        );
        scrollToLatest();
    }

    private void scrollToLatest() {
        if (chatListView != null && !items.isEmpty()) {
            chatListView.smoothScrollToPosition(0);
        }
    }

    private void scrollToNoteId(@Nullable String noteId, boolean highlight) {
        if (TextUtils.isEmpty(noteId) || chatListView == null) {
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            NoteItem item = items.get(i);
            if (!item.isDate() && item.note != null && TextUtils.equals(noteId, item.note.id)) {
                chatListView.smoothScrollToPosition(i);
                if (highlight) {
                    flashHighlightNote(noteId);
                }
                return;
            }
        }
    }

    private void flashHighlightNote(String noteId) {
        activeHighlightNoteId = noteId;
        if (notesAdapter != null) {
            notesAdapter.notifyDataSetChanged();
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (TextUtils.equals(activeHighlightNoteId, noteId)) {
                activeHighlightNoteId = null;
                if (notesAdapter != null) {
                    notesAdapter.notifyDataSetChanged();
                }
            }
        }, 1600);
    }

    private void startEditMode(@NonNull MirrlyVaultStorage.DossierNote note) {
        editingNote = note;
        if (editBannerRow != null) {
            String preview = !TextUtils.isEmpty(note.text) ? note.text.replace('\n', ' ') : resolveMediaBadgePreview(note);
            editBannerPreview.setText(preview);
            if (editBannerRow.getVisibility() != View.VISIBLE) {
                editBannerRow.setVisibility(View.VISIBLE);
                editBannerRow.setAlpha(0f);
                editBannerRow.setTranslationY(dp(6));
                editBannerRow.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(200)
                        .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                        .start();
            }
        }
        if (chatActivityEnterView != null) {
            chatActivityEnterView.setForceShowSendButton(true, true);
            SpannableStringBuilder ssb = new SpannableStringBuilder(note.text);
            ArrayList<TLRPC.MessageEntity> entities = note.decodeEntities();
            MessageObject.addEntitiesToText(ssb, entities, true, false, false, false);
            chatActivityEnterView.setFieldText(ssb);
            if (chatActivityEnterView.getEditField() != null) {
                chatActivityEnterView.getEditField().setSelection(ssb.length());
            }
            chatActivityEnterView.openKeyboard();
        }
        syncInputBubbleHeight();
    }

    private void cancelEditMode() {
        editingNote = null;
        if (editBannerRow != null && editBannerRow.getVisibility() == View.VISIBLE) {
            editBannerRow.setVisibility(View.GONE);
        }
        if (chatActivityEnterView != null) {
            chatActivityEnterView.setForceShowSendButton(false, true);
            chatActivityEnterView.setFieldText("");
        }
        syncInputBubbleHeight();
    }

    private void reloadNotes(boolean animated) {
        List<MirrlyVaultStorage.DossierNote> rawNotes = MirrlyVaultStorage.searchNotes(searchQuery, activeFilter);
        items.clear();
        long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
        Calendar cal = Calendar.getInstance();

        for (int i = 0; i < rawNotes.size(); i++) {
            MirrlyVaultStorage.DossierNote note = rawNotes.get(i);
            MessageObject msgObj = buildSyntheticMessageObject(note, selfId);
            items.add(new NoteItem(msgObj, note));

            int dayKey = getDayKey(cal, note.createdAt);
            int nextDayKey = (i + 1 < rawNotes.size()) ? getDayKey(cal, rawNotes.get(i + 1).createdAt) : -1;
            if (i == rawNotes.size() - 1 || dayKey != nextDayKey) {
                items.add(new NoteItem(buildDateMessageObject(note.createdAt, dayKey), null));
            }
        }

        updateEmptyPlaceholder();
        updateHeaderSubtitle();
        updatePinnedNoteBar(animated);
        if (isSearching) {
            updateSearchBottomBarState();
        }
        if (notesAdapter != null) {
            notesAdapter.notifyDataSetChanged();
        }
        if (chatListView != null && !animated && !items.isEmpty() && TextUtils.isEmpty(initialHighlightNoteId)) {
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
        return dateObj;
    }

    private MessageObject buildSyntheticMessageObject(MirrlyVaultStorage.DossierNote note, long selfId) {
        Integer stableId = stableMessageIdMap.get(note.id);
        if (stableId == null) {
            stableId = nextSyntheticId.getAndIncrement();
            stableMessageIdMap.put(note.id, stableId);
        }

        TLRPC.TL_message msg = new TLRPC.TL_message();
        msg.id = stableId;
        msg.realId = stableId;
        msg.dialog_id = selfId;
        msg.date = note.createdAt;
        msg.out = true;
        msg.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
        msg.flags = TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
        if (note.isEdited()) {
            msg.flags |= TLRPC.MESSAGE_FLAG_EDITED;
            msg.edit_date = note.updatedAt;
        }
        msg.from_id = new TLRPC.TL_peerUser();
        msg.from_id.user_id = selfId;
        msg.peer_id = new TLRPC.TL_peerUser();
        msg.peer_id.user_id = selfId;
        msg.message = note.text;

        ArrayList<TLRPC.MessageEntity> decodedEntities = note.decodeEntities();
        if (!decodedEntities.isEmpty()) {
            msg.entities.addAll(decodedEntities);
            msg.flags |= TLRPC.MESSAGE_FLAG_HAS_ENTITIES;
        }
        if (note.spoiler && !TextUtils.isEmpty(note.text)) {
            boolean hasGlobalSpoiler = false;
            for (int i = 0; i < msg.entities.size(); i++) {
                if (msg.entities.get(i) instanceof TLRPC.TL_messageEntitySpoiler) {
                    hasGlobalSpoiler = true;
                    break;
                }
            }
            if (!hasGlobalSpoiler) {
                TLRPC.TL_messageEntitySpoiler spoilerEntity = new TLRPC.TL_messageEntitySpoiler();
                spoilerEntity.offset = 0;
                spoilerEntity.length = note.text.length();
                msg.entities.add(spoilerEntity);
                msg.flags |= TLRPC.MESSAGE_FLAG_HAS_ENTITIES;
            }
        }

        TLRPC.MessageMedia decodedMedia = note.decodeMedia();
        if (decodedMedia != null) {
            msg.media = decodedMedia;
            msg.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA;
            if (!TextUtils.isEmpty(note.localMediaPath)) {
                msg.attachPath = note.localMediaPath;
                if (decodedMedia instanceof TLRPC.TL_messageMediaPhoto) {
                    File cached = FileLoader.getInstance(currentAccount).getPathToMessage(msg);
                    if ((cached == null || !cached.exists()) && new File(note.localMediaPath).exists()) {
                        TLRPC.TL_photo regenerated = SendMessagesHelper.getInstance(currentAccount).generatePhotoSizes(note.localMediaPath, null);
                        if (regenerated != null) {
                            decodedMedia.photo = regenerated;
                        }
                    }
                }
            }
        } else {
            msg.media = new TLRPC.TL_messageMediaEmpty();
        }

        MessageObject replyObj = null;
        String customReplyTitle = null;
        if (note.dialogId != 0) {
            msg.flags |= TLRPC.MESSAGE_FLAG_REPLY;
            msg.reply_to = new TLRPC.TL_messageReplyHeader();
            msg.reply_to.flags |= 16;
            msg.reply_to.reply_to_msg_id = note.msgId != 0 ? note.msgId : 1;

            TLRPC.TL_message replyMsg = new TLRPC.TL_message();
            replyMsg.id = msg.reply_to.reply_to_msg_id;
            replyMsg.dialog_id = note.dialogId;
            replyMsg.date = note.createdAt;
            replyMsg.media = new TLRPC.TL_messageMediaEmpty();
            replyMsg.message = resolveReplyPreviewBody(note);
            replyObj = new MessageObject(currentAccount, replyMsg, false, false);
            customReplyTitle = resolveReplyHeaderTitle(note);
        }

        MessageObject messageObject = new MessageObject(currentAccount, msg, false, false);
        messageObject.suppressMirrlyNoteBadge = true;
        if (replyObj != null) {
            messageObject.replyMessageObject = replyObj;
            messageObject.customReplyName = customReplyTitle;
            messageObject.overrideLinkColor = note.isChatNote() ? 4 : 5;
        }
        return messageObject;
    }

    private String resolveReplyHeaderTitle(MirrlyVaultStorage.DossierNote note) {
        if (note.secretChat) {
            return getString(R.string.MirrlyNotesSecretChat);
        }
        String peerTitle = note.peerTitle;
        if (TextUtils.isEmpty(peerTitle)) {
            peerTitle = resolvePeerTitleFromController(note.dialogId);
        }
        if (note.isChatNote()) {
            return !TextUtils.isEmpty(peerTitle)
                    ? LocaleController.formatString(R.string.MirrlyNotesChatPrefix, peerTitle)
                    : getString(R.string.MirrlyNotesChatNoteTitle);
        }
        if (!TextUtils.isEmpty(peerTitle) && !TextUtils.isEmpty(note.authorName) && !TextUtils.equals(peerTitle, note.authorName)) {
            return peerTitle + " • " + note.authorName;
        }
        if (!TextUtils.isEmpty(peerTitle)) {
            return peerTitle;
        }
        if (!TextUtils.isEmpty(note.authorName)) {
            return note.authorName;
        }
        return LocaleController.formatString(R.string.MirrlyNotesMessageNumber, note.msgId);
    }

    private String resolveReplyPreviewBody(MirrlyVaultStorage.DossierNote note) {
        if (note.secretChat) {
            return getString(R.string.MirrlyNotesOpenSecretChat);
        }
        if (note.isChatNote()) {
            return getString(R.string.MirrlyNotesOpenChat);
        }
        if (!TextUtils.isEmpty(note.quotePreview)) {
            return note.quotePreview;
        }
        return LocaleController.formatString(R.string.MirrlyNotesGoToMessageNumber, note.msgId);
    }

    private String resolvePeerTitleFromController(long dialogId) {
        if (dialogId == 0) {
            return "";
        }
        if (DialogObject.isEncryptedDialog(dialogId)) {
            return getString(R.string.MirrlyNotesSecretChat);
        }
        if (dialogId > 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            if (user != null) {
                return UserObject.isUserSelf(user) ? getString(R.string.SavedMessages) : UserObject.getUserName(user);
            }
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            if (chat != null) {
                return chat.title;
            }
        }
        return "";
    }

    private String resolveMediaBadgePreview(@NonNull MirrlyVaultStorage.DossierNote note) {
        String label;
        switch (note.mediaKind) {
            case MirrlyVaultStorage.MEDIA_PHOTO:
                label = getString(R.string.MirrlyNotesMediaPhotoLabel);
                break;
            case MirrlyVaultStorage.MEDIA_VIDEO:
                label = getString(R.string.MirrlyNotesMediaVideoLabel);
                break;
            case MirrlyVaultStorage.MEDIA_ROUND:
                label = getString(R.string.MirrlyNotesMediaRoundLabel);
                break;
            case MirrlyVaultStorage.MEDIA_VOICE:
                label = getString(R.string.MirrlyNotesMediaVoiceLabel);
                break;
            case MirrlyVaultStorage.MEDIA_AUDIO:
                label = getString(R.string.MirrlyNotesMediaAudioLabel);
                break;
            case MirrlyVaultStorage.MEDIA_STICKER:
                label = getString(R.string.MirrlyNotesMediaStickerLabel);
                break;
            case MirrlyVaultStorage.MEDIA_GIF:
                label = getString(R.string.MirrlyNotesMediaGifLabel);
                break;
            case MirrlyVaultStorage.MEDIA_GEO:
                label = getString(R.string.MirrlyNotesMediaGeoLabel);
                break;
            case MirrlyVaultStorage.MEDIA_CONTACT:
                label = getString(R.string.MirrlyNotesMediaContactLabel);
                break;
            case MirrlyVaultStorage.MEDIA_POLL:
                label = getString(R.string.MirrlyNotesMediaPollLabel);
                break;
            default:
                label = getString(R.string.MirrlyNotesMediaFileLabel);
                break;
        }
        if (!TextUtils.isEmpty(note.fileName)) {
            return label + " • " + note.fileName;
        }
        return label;
    }

    private void updateHeaderSubtitle() {
        if (avatarContainer == null) {
            return;
        }
        int total = MirrlyVaultStorage.getNotesCount();
        if (total == 0) {
            avatarContainer.setSubtitle(getString(R.string.MirrlyNotesSubtitleEmpty));
            return;
        }
        if (activeFilter != MirrlyVaultStorage.FILTER_ALL || !TextUtils.isEmpty(searchQuery)) {
            int shown = 0;
            for (NoteItem item : items) {
                if (!item.isDate()) {
                    shown++;
                }
            }
            avatarContainer.setSubtitle(LocaleController.formatString(R.string.MirrlyNotesSubtitleShown, shown, total));
            return;
        }
        avatarContainer.setSubtitle(LocaleController.formatPluralString("MirrlyNotesCount", total));
    }

    private void updateEmptyPlaceholder() {
        if (emptyTitleView == null || emptySubtitleView == null) {
            return;
        }
        if (!TextUtils.isEmpty(searchQuery)) {
            emptyTitleView.setText(getString(R.string.MirrlyNotesEmptySearchTitle));
            emptySubtitleView.setText(LocaleController.formatString(R.string.MirrlyNotesEmptySearchSub, searchQuery.trim()));
        } else if (activeFilter != MirrlyVaultStorage.FILTER_ALL) {
            emptyTitleView.setText(getString(R.string.MirrlyNotesEmptyFilterTitle));
            emptySubtitleView.setText(getString(R.string.MirrlyNotesEmptyFilterSub));
        } else {
            emptyTitleView.setText(getString(R.string.MirrlyNotesTitle));
            emptySubtitleView.setText(getString(R.string.MirrlyNotesEmptyDefaultSub));
        }
        if (emptyViewContainer != null) {
            emptyViewContainer.setVisibility(items.isEmpty() ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void updatePinnedNoteBar(boolean animated) {
        if (pinnedNoteBar == null) {
            return;
        }
        MirrlyVaultStorage.DossierNote pinned = MirrlyVaultStorage.getLatestPinnedNote();
        currentPinnedNote = pinned;
        boolean shouldShow = pinned != null && !isSearching;
        if (!shouldShow) {
            if (pinnedNoteBar.getVisibility() == View.VISIBLE) {
                if (animated) {
                    pinnedNoteBar.animate().alpha(0f).setDuration(160).withEndAction(() -> {
                        pinnedNoteBar.setVisibility(View.GONE);
                        updateTopOverlayPositions();
                        updateListPaddingAndButtons();
                    }).start();
                } else {
                    pinnedNoteBar.setVisibility(View.GONE);
                    updateTopOverlayPositions();
                    updateListPaddingAndButtons();
                }
            }
            return;
        }
        String body = !TextUtils.isEmpty(pinned.text) ? pinned.text.replace('\n', ' ') : resolveMediaBadgePreview(pinned);
        pinnedNoteBodyView.setText(body);
        if (pinnedNoteBar.getVisibility() != View.VISIBLE) {
            pinnedNoteBar.setVisibility(View.VISIBLE);
            pinnedNoteBar.setAlpha(0f);
            updateTopOverlayPositions();
            updateListPaddingAndButtons();
            if (animated) {
                pinnedNoteBar.animate().alpha(1f).setDuration(200).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
            } else {
                pinnedNoteBar.setAlpha(1f);
            }
        }
    }

    private void updateFilterChipsVisibility(boolean animated) {
        if (filterChipsContainer == null) {
            return;
        }
        refreshFilterChipsSelection();
        boolean show = isSearching;
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
                filterChipsContainer.animate().alpha(0f).setDuration(160).withEndAction(() -> {
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
        if (activeFilter == MirrlyVaultStorage.FILTER_ALL || isSearching) {
            if (filterBanner.getVisibility() == View.VISIBLE) {
                if (animated) {
                    filterBanner.animate()
                            .alpha(0f)
                            .setDuration(180)
                            .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                            .withEndAction(() -> {
                                filterBanner.setVisibility(View.GONE);
                                updateTopOverlayPositions();
                                updateListPaddingAndButtons();
                            })
                            .start();
                } else {
                    filterBanner.setVisibility(View.GONE);
                    updateTopOverlayPositions();
                    updateListPaddingAndButtons();
                }
            }
            return;
        }
        filterBannerText.setText(resolveFilterBannerLabel(activeFilter));
        if (filterBanner.getVisibility() != View.VISIBLE) {
            filterBanner.setVisibility(View.VISIBLE);
            filterBanner.setAlpha(0f);
            updateTopOverlayPositions();
            updateListPaddingAndButtons();
            if (animated) {
                filterBanner.animate()
                        .alpha(1f)
                        .setDuration(200)
                        .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                        .start();
            } else {
                filterBanner.setAlpha(1f);
            }
        }
    }

    private String resolveFilterBannerLabel(int filter) {
        switch (filter) {
            case MirrlyVaultStorage.FILTER_MESSAGES:
                return getString(R.string.MirrlyNotesFilterBannerMessages);
            case MirrlyVaultStorage.FILTER_STANDALONE:
                return getString(R.string.MirrlyNotesFilterBannerStandalone);
            case MirrlyVaultStorage.FILTER_MEDIA:
                return getString(R.string.MirrlyNotesFilterBannerMedia);
            case MirrlyVaultStorage.FILTER_FILES:
                return getString(R.string.MirrlyNotesFilterBannerFiles);
            case MirrlyVaultStorage.FILTER_VOICE:
                return getString(R.string.MirrlyNotesFilterBannerVoice);
            case MirrlyVaultStorage.FILTER_SPOILER:
                return getString(R.string.MirrlyNotesFilterBannerSpoiler);
            default:
                return "";
        }
    }

    private void showFilterMenu() {
        if (filterItem == null || getParentActivity() == null) {
            return;
        }
        ItemOptions options = ItemOptions.makeOptions(this, filterItem)
                .setBlur(true, false)
                .setBlurBackground(glassBackgroundDrawableFactory, BlurredBackgroundProviderImpl.messageMenuBackground(resourceProvider), false)
                .setGravity(Gravity.RIGHT);

        options.addChecked(activeFilter == MirrlyVaultStorage.FILTER_ALL, getString(R.string.MirrlyNotesFilterAll), () -> applyFilter(MirrlyVaultStorage.FILTER_ALL));
        options.addChecked(activeFilter == MirrlyVaultStorage.FILTER_MESSAGES, getString(R.string.MirrlyNotesFilterMessages), () -> applyFilter(MirrlyVaultStorage.FILTER_MESSAGES));
        options.addChecked(activeFilter == MirrlyVaultStorage.FILTER_STANDALONE, getString(R.string.MirrlyNotesFilterStandalone), () -> applyFilter(MirrlyVaultStorage.FILTER_STANDALONE));
        options.addChecked(activeFilter == MirrlyVaultStorage.FILTER_MEDIA, getString(R.string.MirrlyNotesFilterChipMedia), () -> applyFilter(MirrlyVaultStorage.FILTER_MEDIA));
        options.addChecked(activeFilter == MirrlyVaultStorage.FILTER_FILES, getString(R.string.MirrlyNotesFilterChipFiles), () -> applyFilter(MirrlyVaultStorage.FILTER_FILES));
        options.addChecked(activeFilter == MirrlyVaultStorage.FILTER_VOICE, getString(R.string.MirrlyNotesFilterChipVoice), () -> applyFilter(MirrlyVaultStorage.FILTER_VOICE));
        options.addChecked(activeFilter == MirrlyVaultStorage.FILTER_SPOILER, getString(R.string.MirrlyNotesFilterSpoiler), () -> applyFilter(MirrlyVaultStorage.FILTER_SPOILER));

        if (MirrlyVaultStorage.getNotesCount() > 0) {
            options.addGap();
            options.add(R.drawable.msg_delete, getString(R.string.MirrlyNotesClearAll), true, this::confirmClearAllNotes);
        }
        options.show();
    }

    private void applyFilter(int filter) {
        if (activeFilter == filter) {
            return;
        }
        activeFilter = filter;
        refreshFilterChipsSelection();
        updateFilterBanner(true);
        reloadNotes(true);
    }

    private void confirmClearAllNotes() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), resourceProvider);
        builder.setTitle(getString(R.string.MirrlyVaultClearNotesConfirmTitle));
        builder.setMessage(getString(R.string.MirrlyNotesClearAllConfirmText));
        builder.setPositiveButton(getString(R.string.Delete), (dialog, which) -> {
            MirrlyVaultStorage.clearDossierNotes();
            cancelEditMode();
            reloadNotes(true);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.ic_delete, getString(R.string.MirrlyVaultClearNotesDone)).show();
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        TextView button = (TextView) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourceProvider));
        }
    }

    private void showNoteItemOptions(ChatMessageCell cell) {
        if (cell == null || getParentActivity() == null) {
            return;
        }
        MessageObject msgObj = cell.getMessageObject();
        MirrlyVaultStorage.DossierNote note = findNoteForMessageObject(msgObj);
        if (note == null) {
            return;
        }

        ItemOptions options = ItemOptions.makeOptions(this, cell)
                .setBlur(true, false)
                .setBlurBackground(glassBackgroundDrawableFactory, BlurredBackgroundProviderImpl.messageMenuBackground(resourceProvider), false)
                .setGravity(Gravity.RIGHT);

        options.addIf(note.dialogId != 0, R.drawable.msg_viewintopic, getString(note.isChatNote() ? R.string.MirrlyNotesOpenChat : R.string.MirrlyNotesGoToMessage), () -> openNoteTarget(note));
        options.add(R.drawable.msg_edit, getString(R.string.MirrlyNotesEdit), () -> startEditMode(note));
        options.addIf(!TextUtils.isEmpty(note.text), R.drawable.msg_copy, getString(R.string.Copy), () -> {
            if (AndroidUtilities.addToClipboard(note.text)) {
                BulletinFactory.of(this).createCopyBulletin(getString(R.string.TextCopied)).show();
            }
        });
        options.add(
                note.pinned ? R.drawable.msg_unpin : R.drawable.msg_pin,
                getString(note.pinned ? R.string.MirrlyNotesUnpin : R.string.MirrlyNotesPin),
                () -> {
                    MirrlyVaultStorage.DossierNote updated = MirrlyVaultStorage.toggleNotePinned(note.id);
                    reloadNotes(true);
                    if (updated != null) {
                        BulletinFactory.of(this).createSimpleBulletin(
                                R.raw.info,
                                getString(updated.pinned ? R.string.MirrlyNotesPinnedToast : R.string.MirrlyNotesUnpinnedToast)
                        ).show();
                    }
                }
        );
        options.addIf(!TextUtils.isEmpty(note.text),
                note.spoiler ? R.drawable.msg_spoiler_off : R.drawable.msg_spoiler,
                getString(note.spoiler ? R.string.MirrlyNotesRemoveSpoiler : R.string.MirrlyNotesHideSpoiler),
                () -> {
                    MirrlyVaultStorage.toggleNoteSpoiler(note.id);
                    reloadNotes(true);
                }
        );
        options.add(R.drawable.msg_forward, getString(R.string.MirrlyNotesForwardToChat), () -> {
            ArrayList<MirrlyVaultStorage.DossierNote> single = new ArrayList<>(1);
            single.add(note);
            openForwardPicker(single);
        });
        options.add(R.drawable.msg_select, getString(R.string.MirrlyNotesSelect), () -> enterActionMode(note));
        options.addGap();
        options.add(R.drawable.msg_delete, getString(R.string.Delete), true, () -> deleteNoteWithEffect(cell, note));
        options.show();
    }

    private void enterActionMode(@NonNull MirrlyVaultStorage.DossierNote initialNote) {
        selectedNoteIds.clear();
        selectedNoteIds.add(initialNote.id);
        if (actionBar != null && !actionBar.isActionModeShowed()) {
            actionBar.showActionMode();
            actionBar.setActionModeTopColor(Color.TRANSPARENT);
            ActionBarMenu actionMode = actionBar.createActionMode();
            actionMode.setBackgroundColor(Color.TRANSPARENT);
        }
        updateActionModeTitle();
        updateBottomModeBars();
        if (notesAdapter != null) {
            notesAdapter.notifyDataSetChanged();
        }
    }

    private void toggleSelection(@NonNull MirrlyVaultStorage.DossierNote note) {
        if (selectedNoteIds.contains(note.id)) {
            selectedNoteIds.remove(note.id);
        } else {
            selectedNoteIds.add(note.id);
        }
        if (selectedNoteIds.isEmpty()) {
            exitActionMode();
            return;
        }
        updateActionModeTitle();
        updateBottomModeBars();
        if (notesAdapter != null) {
            notesAdapter.notifyDataSetChanged();
        }
    }

    private void updateActionModeTitle() {
        if (actionModeCountView != null) {
            actionModeCountView.setText(LocaleController.formatString(R.string.MirrlyNotesSelectedCount, selectedNoteIds.size()));
        }
    }

    private void exitActionMode() {
        selectedNoteIds.clear();
        if (actionBar != null && actionBar.isActionModeShowed()) {
            actionBar.hideActionMode();
        }
        updateBottomModeBars();
        if (notesAdapter != null) {
            notesAdapter.notifyDataSetChanged();
        }
    }

    private void forwardSelectedNotes() {
        ArrayList<MirrlyVaultStorage.DossierNote> selected = collectSelectedNotes();
        if (selected.isEmpty()) {
            return;
        }
        exitActionMode();
        openForwardPicker(selected);
    }

    private ArrayList<MirrlyVaultStorage.DossierNote> collectSelectedNotes() {
        ArrayList<MirrlyVaultStorage.DossierNote> list = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            NoteItem item = items.get(i);
            if (!item.isDate() && item.note != null && selectedNoteIds.contains(item.note.id)) {
                list.add(item.note);
            }
        }
        return list;
    }

    private void confirmDeleteSelectedNotes() {
        ArrayList<MirrlyVaultStorage.DossierNote> selected = collectSelectedNotes();
        if (selected.isEmpty() || getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), resourceProvider);
        builder.setTitle(getString(R.string.Delete));
        builder.setMessage(LocaleController.formatString(R.string.MirrlyNotesDeleteSelectedConfirm, selected.size()));
        builder.setPositiveButton(getString(R.string.Delete), (d, w) -> {
            ArrayList<String> ids = new ArrayList<>(selected.size());
            for (int i = 0; i < selected.size(); i++) {
                ids.add(selected.get(i).id);
            }
            MirrlyVaultStorage.deleteNotesByIds(ids);
            exitActionMode();
            reloadNotes(true);
            BulletinFactory.of(this).createUndoBulletin(LocaleController.formatString(R.string.MirrlyNotesDeletedManyToast, selected.size()), () -> {
                for (int i = 0; i < selected.size(); i++) {
                    MirrlyVaultStorage.restoreNote(selected.get(i));
                }
                reloadNotes(true);
            }, null).show();
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        TextView btn = (TextView) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (btn != null) {
            btn.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourceProvider));
        }
    }

    private void openForwardPicker(ArrayList<MirrlyVaultStorage.DossierNote> toForward) {
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
                MessagesStorage.TopicKey key = dids.get(dIdx);
                long targetDialogId = key.dialogId;
                MessageObject replyTop = key.topicId != 0 ? null : null;
                for (int nIdx = toForward.size() - 1; nIdx >= 0; nIdx--) {
                    sendNoteToTelegramChat(targetDialogId, toForward.get(nIdx));
                }
            }
            dialogsActivity.finishFragment();
            BulletinFactory.of(MirrlyNotesChatActivity.this).createSimpleBulletin(R.raw.forward, getString(R.string.MirrlyNotesForwardedToast)).show();
            return true;
        });
        presentFragment(fragment);
    }

    private void sendNoteToTelegramChat(long targetDialogId, MirrlyVaultStorage.DossierNote note) {
        ArrayList<TLRPC.MessageEntity> entities = note.decodeEntities();
        String text = !TextUtils.isEmpty(note.text) ? note.text : null;
        if (!note.hasMedia() || (TextUtils.isEmpty(note.localMediaPath) && note.decodeMedia() == null)) {
            if (!TextUtils.isEmpty(text)) {
                SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(
                        text, targetDialogId, null, null, null, true, entities, null, null, true, 0, 0, null, false
                );
                SendMessagesHelper.getInstance(currentAccount).sendMessage(params);
            }
            return;
        }

        switch (note.mediaKind) {
            case MirrlyVaultStorage.MEDIA_PHOTO:
                if (!TextUtils.isEmpty(note.localMediaPath)) {
                    SendMessagesHelper.prepareSendingPhoto(
                            getAccountInstance(), note.localMediaPath, null, null, targetDialogId, null, null, null, null, entities, null, null, 0, null, null, true, 0, 0, 0, false, text, null, 0, 0, 0, null
                    );
                }
                break;
            case MirrlyVaultStorage.MEDIA_VIDEO:
                if (!TextUtils.isEmpty(note.localMediaPath)) {
                    SendMessagesHelper.prepareSendingVideo(
                            getAccountInstance(), note.localMediaPath, null, note.localThumbPath, null, targetDialogId, null, null, null, null, entities, 0, null, true, 0, 0, false, note.spoiler, text, null, 0, 0, 0, null
                    );
                }
                break;
            case MirrlyVaultStorage.MEDIA_DOCUMENT:
            case MirrlyVaultStorage.MEDIA_VOICE:
            case MirrlyVaultStorage.MEDIA_AUDIO:
            case MirrlyVaultStorage.MEDIA_ROUND:
                if (!TextUtils.isEmpty(note.localMediaPath)) {
                    ArrayList<String> files = new ArrayList<>(1);
                    files.add(note.localMediaPath);
                    SendMessagesHelper.prepareSendingDocuments(
                            getAccountInstance(), files, files, null, text, entities, note.mimeType, targetDialogId, null, null, null, null, null, true, 0, 0, null, null, 0, false, 0, 0, null
                    );
                }
                break;
            default:
                TLRPC.MessageMedia m = note.decodeMedia();
                if (m instanceof TLRPC.TL_messageMediaDocument && m.document != null) {
                    SendMessagesHelper.getInstance(currentAccount).sendSticker(m.document, "", targetDialogId, null, null, null, null, null, true, 0, 0, false, null, null, 0, 0, null);
                } else if (!TextUtils.isEmpty(text)) {
                    SendMessagesHelper.SendMessageParams p = SendMessagesHelper.SendMessageParams.of(
                            text, targetDialogId, null, null, null, true, entities, null, null, true, 0, 0, null, false
                    );
                    SendMessagesHelper.getInstance(currentAccount).sendMessage(p);
                }
                break;
        }
    }

    private void openNoteTarget(MirrlyVaultStorage.DossierNote note) {
        if (note == null || note.dialogId == 0) {
            return;
        }
        Bundle args = new Bundle();
        long dialogId = note.dialogId;
        if (DialogObject.isEncryptedDialog(dialogId)) {
            args.putInt("enc_id", DialogObject.getEncryptedChatId(dialogId));
        } else if (dialogId >= 0) {
            args.putLong("user_id", dialogId);
        } else {
            args.putLong("chat_id", -dialogId);
        }
        if (note.msgId != 0) {
            args.putInt("message_id", note.msgId);
        }
        presentFragment(new ChatActivity(args));
    }

    private void deleteNoteWithEffect(@Nullable ChatMessageCell cell, @NonNull MirrlyVaultStorage.DossierNote note) {
        if (editingNote != null && TextUtils.equals(editingNote.id, note.id)) {
            cancelEditMode();
        }
        Runnable performDelete = () -> {
            MirrlyVaultStorage.DossierNote removed = MirrlyVaultStorage.deleteNoteById(note.id);
            reloadNotes(true);
            if (removed != null) {
                BulletinFactory.of(this).createUndoBulletin(getString(R.string.MirrlyNotesDeletedToast), () -> {
                    MirrlyVaultStorage.restoreNote(removed);
                    reloadNotes(true);
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
                if (removingFromParent || holder[0] == null) {
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

    @Nullable
    private MirrlyVaultStorage.DossierNote findNoteForMessageObject(@Nullable MessageObject obj) {
        if (obj == null) {
            return null;
        }
        for (int i = 0; i < items.size(); i++) {
            NoteItem item = items.get(i);
            if (!item.isDate() && item.messageObject == obj) {
                return item.note;
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
            NoteItem item = items.get(i);
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
                            if (needPreview) {
                                obj.thumb = ir.getBitmapSafe();
                            }
                            obj.radius = ir.getRoundRadius(true);
                            return obj;
                        }
                    }
                }
            }
            return null;
        }
    };

    private TextureView createTextureView(boolean add) {
        if (videoTextureView == null) {
            videoPlayerContainer = new FrameLayout(getParentActivity()) {
                @Override
                public void setTranslationY(float translationY) {
                    super.setTranslationY(translationY);
                    if (contentView != null) {
                        contentView.invalidate();
                    }
                }
            };
            videoPlayerContainer.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
                }
            });
            videoPlayerContainer.setClipToOutline(true);
            videoPlayerContainer.setWillNotDraw(false);

            aspectRatioFrameLayout = new AspectRatioFrameLayout(getParentActivity());
            aspectRatioFrameLayout.setBackgroundColor(0);
            if (add) {
                videoPlayerContainer.addView(aspectRatioFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
            }

            videoTextureView = new TextureView(getParentActivity());
            videoTextureView.setOpaque(false);
            aspectRatioFrameLayout.addView(videoTextureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
        ViewGroup parent = (ViewGroup) videoPlayerContainer.getParent();
        if (parent != null && parent != contentView) {
            parent.removeView(videoPlayerContainer);
            parent = null;
        }
        int roundSize = AndroidUtilities.roundPlayingMessageSize(false, false);
        if (parent == null) {
            contentView.addView(videoPlayerContainer, 1, new FrameLayout.LayoutParams(roundSize, roundSize));
        }
        aspectRatioFrameLayout.setDrawingReady(false);
        return videoTextureView;
    }

    private void destroyTextureView() {
        if (videoPlayerContainer == null || videoPlayerContainer.getParent() == null) {
            return;
        }
        if (chatListView != null) {
            chatListView.invalidateViews();
        }
        aspectRatioFrameLayout.setDrawingReady(false);
        contentView.removeView(videoPlayerContainer);
    }

    private void updateTextureViewPosition() {
        if (contentView == null || chatListView == null || videoPlayerContainer == null || isPaused) {
            return;
        }
        boolean found = false;
        int roundSize = AndroidUtilities.roundPlayingMessageSize(false, false);
        for (int a = 0; a < chatListView.getChildCount(); a++) {
            View child = chatListView.getChildAt(a);
            if (child instanceof ChatMessageCell) {
                ChatMessageCell cell = (ChatMessageCell) child;
                MessageObject msg = cell.getMessageObject();
                if (msg != null && (msg.isRoundVideo() || msg.isVideo()) && MediaController.getInstance().isPlayingMessage(msg)) {
                    ImageReceiver ir = cell.getPhotoImage();
                    videoPlayerContainer.setTranslationX(ir.getImageX() + cell.getX());
                    videoPlayerContainer.setTranslationY(cell.getY() + cell.getPaddingTop() + ir.getImageY() + chatListView.getY() - videoPlayerContainer.getTop());
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) videoPlayerContainer.getLayoutParams();
                    if (msg.isRoundVideo()) {
                        if (lp.width != roundSize || lp.height != roundSize) {
                            lp.width = lp.height = roundSize;
                            aspectRatioFrameLayout.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
                            videoPlayerContainer.setLayoutParams(lp);
                        }
                        float scale = ir.getImageWidth() / (float) roundSize;
                        videoPlayerContainer.setPivotX(0);
                        videoPlayerContainer.setPivotY(0);
                        videoPlayerContainer.setScaleX(scale);
                        videoPlayerContainer.setScaleY(scale);
                    }
                    videoPlayerContainer.setVisibility(View.VISIBLE);
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            videoPlayerContainer.setVisibility(View.INVISIBLE);
        }
    }

    private ArrayList<MessageObject> createVoiceNotesPlaylist(MessageObject startMsg) {
        ArrayList<MessageObject> playlist = new ArrayList<>();
        for (int i = items.size() - 1; i >= 0; i--) {
            NoteItem item = items.get(i);
            if (!item.isDate() && item.messageObject != null && (item.messageObject.isVoice() || item.messageObject.isRoundVideo())) {
                playlist.add(item.messageObject);
            }
        }
        return playlist;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void attachSwipeToEdit(RecyclerListView recyclerView) {
        final int touchSlop = ViewConfiguration.get(recyclerView.getContext()).getScaledTouchSlop();
        final float maxSwipePx = dp(76);
        final float triggerSwipePx = dp(52);
        final Drawable editIcon = ContextCompat.getDrawable(recyclerView.getContext(), R.drawable.msg_edit);
        final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                if (editIcon == null) {
                    return;
                }
                for (int i = 0; i < parent.getChildCount(); i++) {
                    View child = parent.getChildAt(i);
                    float tx = child.getTranslationX();
                    if (tx < -dp(8) && child instanceof ChatMessageCell) {
                        float progress = Math.min(1f, -tx / triggerSwipePx);
                        float cx = child.getRight() + tx + dp(26);
                        float cy = child.getTop() + child.getHeight() / 2f;
                        float radius = dp(16) * (0.6f + 0.4f * progress);
                        badgePaint.setColor(Theme.getColor(Theme.key_chat_serviceBackground, resourceProvider));
                        badgePaint.setAlpha((int) (210 * progress));
                        c.drawCircle(cx, cy, radius, badgePaint);
                        editIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_serviceText, resourceProvider), PorterDuff.Mode.MULTIPLY));
                        editIcon.setAlpha((int) (255 * progress));
                        int iconSize = dp(18);
                        editIcon.setBounds((int) (cx - iconSize / 2f), (int) (cy - iconSize / 2f), (int) (cx + iconSize / 2f), (int) (cy + iconSize / 2f));
                        editIcon.draw(c);
                    }
                }
            }
        });

        recyclerView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            ChatMessageCell activeCell;
            float startX, startY;
            boolean dragging;
            boolean hapticFired;

            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                if (actionBar != null && actionBar.isActionModeShowed()) {
                    return false;
                }
                int action = e.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    View child = rv.findChildViewUnder(e.getX(), e.getY());
                    activeCell = child instanceof ChatMessageCell ? (ChatMessageCell) child : null;
                    startX = e.getX();
                    startY = e.getY();
                    dragging = false;
                    hapticFired = false;
                } else if (action == MotionEvent.ACTION_MOVE && activeCell != null) {
                    float dx = e.getX() - startX;
                    float dy = e.getY() - startY;
                    if (!dragging && dx < -touchSlop && Math.abs(dx) > Math.abs(dy) * 1.4f) {
                        dragging = true;
                        rv.requestDisallowInterceptTouchEvent(true);
                        return true;
                    }
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    activeCell = null;
                    dragging = false;
                }
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                if (activeCell == null) {
                    return;
                }
                int action = e.getActionMasked();
                if (action == MotionEvent.ACTION_MOVE) {
                    float dx = Math.min(0f, e.getX() - startX);
                    float damped = Math.max(-maxSwipePx, dx * 0.72f);
                    activeCell.setTranslationX(damped);
                    rv.invalidate();
                    if (!hapticFired && Math.abs(damped) >= triggerSwipePx) {
                        hapticFired = true;
                        activeCell.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    }
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    boolean triggered = action == MotionEvent.ACTION_UP && Math.abs(activeCell.getTranslationX()) >= triggerSwipePx;
                    ChatMessageCell cellRef = activeCell;
                    MirrlyVaultStorage.DossierNote note = triggered ? findNoteForMessageObject(cellRef.getMessageObject()) : null;

                    SpringAnimation spring = new SpringAnimation(cellRef, DynamicAnimation.TRANSLATION_X, 0f);
                    spring.getSpring().setStiffness(SpringForce.STIFFNESS_MEDIUM);
                    spring.getSpring().setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY);
                    spring.addUpdateListener((animation, value, velocity) -> rv.invalidate());
                    spring.start();

                    if (note != null) {
                        startEditMode(note);
                    }
                    activeCell = null;
                    dragging = false;
                }
            }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            }
        });
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
            View view = chatListView.getChildAt(i);
            if (view instanceof ChatMessageCell) {
                ChatMessageCell cell = (ChatMessageCell) view;
                int top = view.getTop();
                int viewTop = top >= 0 ? 0 : -top;
                int viewBottom = view.getMeasuredHeight();
                if (viewBottom > height) {
                    viewBottom = viewTop + height;
                }
                cell.setVisiblePart(viewTop, viewBottom - viewTop, contentView.getHeightWithKeyboard() - dp(48) - chatListView.getTop(), 0, view.getY() + actionBar.getMeasuredHeight() - contentView.getBackgroundTranslationY(), contentView.getMeasuredWidth(), contentView.getBackgroundSizeY(), 0, 0, 0);
            } else if (view instanceof ChatActionCell) {
                ChatActionCell cell = (ChatActionCell) view;
                cell.setVisiblePart(view.getY() + actionBar.getMeasuredHeight() - contentView.getBackgroundTranslationY(), contentView.getBackgroundSizeY());
            }

            if (view.getBottom() <= chatListView.getPaddingTop()) {
                continue;
            }
            int bottom = view.getBottom();
            if (bottom < minPos) {
                minPos = bottom;
                minChild = view;
            }
            if (view instanceof ChatActionCell && ((ChatActionCell) view).getMessageObject() != null && ((ChatActionCell) view).getMessageObject().isDateObject) {
                if (bottom < minDatePos) {
                    minDatePos = bottom;
                    minDateChild = view;
                }
            }
        }

        if (floatingDateView == null) {
            return;
        }
        if (minChild instanceof ChatMessageCell) {
            MessageObject mo = ((ChatMessageCell) minChild).getMessageObject();
            if (mo != null && mo.messageOwner != null) {
                floatingDateView.setCustomDate(mo.messageOwner.date, false, true);
            }
        } else if (minChild instanceof ChatActionCell) {
            MessageObject mo = ((ChatActionCell) minChild).getMessageObject();
            if (mo != null && mo.messageOwner != null) {
                floatingDateView.setCustomDate(mo.messageOwner.date, false, true);
            }
        }
        currentFloatingTopIsNotMessage = !(minChild instanceof ChatMessageCell || minChild instanceof ChatActionCell);
        currentFloatingDateOnScreen = false;
        if (minDateChild != null) {
            if (minDateChild.getTop() > chatListView.getPaddingTop() || currentFloatingTopIsNotMessage) {
                minDateChild.setAlpha(1.0f);
                hideFloatingDateView(!currentFloatingTopIsNotMessage);
            } else {
                minDateChild.setAlpha(0.0f);
                currentFloatingDateOnScreen = true;
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
            hideFloatingDateView(true);
            floatingDateOffset = 0f;
        }
        floatingDateView.setTranslationY(getFloatingDateRestTop() - floatingDateView.getTop() + floatingDateOffset);
        floatingDateView.setVisiblePart(floatingDateView.getY() - contentView.getBackgroundTranslationY(), contentView.getBackgroundSizeY());
        scheduleNotesAdaptiveGlassContrastUpdate(false);
    }

    private BlurredBackgroundColorProvider createNotesFloatingPillColorProvider(int textColorKey) {
        return new BlurredBackgroundColorProvider() {
            private boolean isLightText() {
                return AndroidUtilities.computePerceivedBrightness(Theme.getColor(textColorKey, resourceProvider)) >= 0.55f;
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
                        return Theme.multAlpha(ColorUtils.setAlphaComponent(serviceBg, 0xFF), org.telegram.messenger.mirrly.MirrlyConfig.getEffectiveGlassAlpha(0.60f));
                    }
                    return Theme.multAlpha(0xFF14181E, org.telegram.messenger.mirrly.MirrlyConfig.getEffectiveGlassAlpha(0.55f));
                }
                if ((serviceBg >>> 24) > 0 && AndroidUtilities.computePerceivedBrightness(serviceBg) > 0.52f) {
                    return Theme.multAlpha(ColorUtils.setAlphaComponent(serviceBg, 0xFF), org.telegram.messenger.mirrly.MirrlyConfig.getEffectiveGlassAlpha(0.65f));
                }
                return Theme.multAlpha(0xFFF5F7FA, org.telegram.messenger.mirrly.MirrlyConfig.getEffectiveGlassAlpha(0.72f));
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

    private BlurredBackgroundProvider notesTopPanelGlassColors;
    private final Rect notesBackZoneRect = new Rect();
    private final Rect notesTitleZoneRect = new Rect();
    private final Rect notesMenuZoneRect = new Rect();
    private final Runnable notesAdaptiveTickRunnable = () -> {
        notesAdaptiveTickPosted = false;
        updateNotesAdaptiveGlassContrast(false);
    };
    private int notesAdaptiveScrollGen;
    private boolean notesAdaptiveTickPosted;
    private long lastNotesAdaptiveSampleMs;
    private float notesBarBackTarget, notesBarTitleTarget, notesBarMenuTarget;
    private float notesBarBackFactor, notesBarTitleFactor, notesBarMenuFactor;
    private float notesPinnedTarget, notesPinnedFactor, lastNotesPinnedApplied = -1f;
    private float notesFilterBannerTarget, notesFilterBannerFactor, lastNotesFilterBannerApplied = -1f;
    private float notesInputTarget, notesInputFactor;
    private float notesScrollDownTarget, notesScrollDownFactor;
    private int lastNotesPinnedCloseColor, lastNotesFilterClearColor;
    private PorterDuffColorFilter notesPinnedCloseFilter, notesFilterClearFilter;

    private float getNotesTopBackdropLuminance() {
        if (contentView != null) {
            final int wpColor = contentView.wallpaperBitmapProvider.getStatusBarColor(navbarContentSourceWallpaper);
            if (Color.alpha(wpColor) > 8) {
                return GlassContrastHelper.computeLuminance(wpColor);
            }
        }
        return GlassContrastHelper.resolveThemeBackdropLuminance(resourceProvider, Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
    }

    private float getNotesBottomBackdropLuminance() {
        if (contentView != null) {
            final int wpColor = contentView.wallpaperBitmapProvider.getNavigationBarColor(navbarContentSourceWallpaper);
            if (Color.alpha(wpColor) > 8) {
                return GlassContrastHelper.computeLuminance(wpColor);
            }
        }
        return GlassContrastHelper.resolveThemeBackdropLuminance(resourceProvider, Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
    }

    private void cancelNotesAdaptiveTick() {
        if (notesAdaptiveTickPosted && contentView != null) {
            contentView.removeCallbacks(notesAdaptiveTickRunnable);
        }
        notesAdaptiveTickPosted = false;
    }

    private void resetNotesAdaptiveState() {
        notesBarBackTarget = notesBarTitleTarget = notesBarMenuTarget = 0f;
        notesBarBackFactor = notesBarTitleFactor = notesBarMenuFactor = 0f;
        notesPinnedTarget = notesPinnedFactor = 0f;
        notesFilterBannerTarget = notesFilterBannerFactor = 0f;
        notesInputTarget = notesInputFactor = 0f;
        notesScrollDownTarget = notesScrollDownFactor = 0f;
        lastNotesPinnedApplied = -1f;
        lastNotesFilterBannerApplied = -1f;
        notesTopPanelGlassColors = null;
        if (actionBar != null) {
            actionBar.applyAdaptiveGlassContrast(0f);
        }
        applyNotesOverlayContrast(0f, 0f);
        if (chatActivityEnterView != null) {
            chatActivityEnterView.applyAdaptiveGlassContrast(0f);
        }
        if (scrollDownButton != null) {
            scrollDownButton.updateColors();
        }
    }

    private void scheduleNotesAdaptiveGlassContrastUpdate(boolean forceSample) {
        updateNotesAdaptiveGlassContrast(forceSample);
    }

    private void updateNotesAdaptiveGlassContrast(boolean forceSample) {
        if (contentView == null || chatListView == null) {
            return;
        }
        if (!BlurredBackgroundProviderImpl.checkBlurEnabled(currentAccount, resourceProvider)) {
            if (notesBarBackFactor > 0.001f || notesBarTitleFactor > 0.001f || notesBarMenuFactor > 0.001f
                    || notesPinnedFactor > 0.001f || notesInputFactor > 0.001f || notesScrollDownFactor > 0.001f) {
                resetNotesAdaptiveState();
            }
            return;
        }

        final long now = SystemClock.uptimeMillis();
        final float topBackdrop = getNotesTopBackdropLuminance();
        final float bottomBackdrop = getNotesBottomBackdropLuminance();
        if (forceSample || (now - lastNotesAdaptiveSampleMs >= 80L)) {
            lastNotesAdaptiveSampleMs = now;
            if (notesTopPanelGlassColors == null) {
                notesTopPanelGlassColors = BlurredBackgroundProviderImpl.topPanelChatActivity(resourceProvider);
            }
            final int topPillBg = notesTopPanelGlassColors.getBackgroundColor();
            final int inputPillBg = blurredBackgroundColorProvider != null ? blurredBackgroundColorProvider.getBackgroundColor() : topPillBg;
            final boolean liquidActive = org.telegram.messenger.mirrly.MirrlyConfig.isGlassLiquidEnabled();

            if (actionBar != null && actionBar.getVisibility() == View.VISIBLE && actionBar.getWidth() > 0) {
                actionBar.getGlassPillZones(notesBackZoneRect, notesTitleZoneRect, notesMenuZoneRect);
                final boolean expectDarkTitle = GlassContrastHelper.computeLuminance(Theme.getColor(Theme.key_actionBarDefaultTitle, resourceProvider)) < 0.5f;
                final boolean expectDarkIcons = GlassContrastHelper.computeLuminance(Theme.getColor(Theme.key_actionBarDefaultIcon, resourceProvider)) < 0.5f;

                final float backEff = GlassContrastHelper.computeEffectiveGlassLuminance(topPillBg,
                        GlassContrastHelper.estimateUnderlyingLuminanceInBand(actionBar, chatListView,
                                notesBackZoneRect.left, notesBackZoneRect.top, notesBackZoneRect.right, notesBackZoneRect.bottom,
                                topBackdrop, resourceProvider), liquidActive);
                final float titleEff = GlassContrastHelper.computeEffectiveGlassLuminance(topPillBg,
                        GlassContrastHelper.estimateUnderlyingLuminanceInBand(actionBar, chatListView,
                                notesTitleZoneRect.left, notesTitleZoneRect.top, notesTitleZoneRect.right, notesTitleZoneRect.bottom,
                                topBackdrop, resourceProvider), liquidActive);
                final float menuEff = GlassContrastHelper.computeEffectiveGlassLuminance(topPillBg,
                        GlassContrastHelper.estimateUnderlyingLuminanceInBand(actionBar, chatListView,
                                notesMenuZoneRect.left, notesMenuZoneRect.top, notesMenuZoneRect.right, notesMenuZoneRect.bottom,
                                topBackdrop, resourceProvider), liquidActive);

                notesBarBackTarget = GlassContrastHelper.updateContrastInvertFactor(notesBarBackTarget, backEff, expectDarkIcons, 1f);
                notesBarTitleTarget = GlassContrastHelper.updateContrastInvertFactor(notesBarTitleTarget, titleEff, expectDarkTitle, 1f);
                notesBarMenuTarget = GlassContrastHelper.updateContrastInvertFactor(notesBarMenuTarget, menuEff, expectDarkIcons, 1f);
            }

            if (pinnedNoteBar != null && pinnedNoteBar.getVisibility() == View.VISIBLE && pinnedNoteBar.getWidth() > 0) {
                final float pEff = GlassContrastHelper.computeEffectiveGlassLuminance(topPillBg,
                        GlassContrastHelper.estimateUnderlyingLuminanceInBand(pinnedNoteBar, chatListView,
                                dp(6), 0f, pinnedNoteBar.getWidth() - dp(6), pinnedNoteBar.getHeight(),
                                topBackdrop, resourceProvider), liquidActive);
                final boolean expectDark = GlassContrastHelper.computeLuminance(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider)) < 0.5f;
                notesPinnedTarget = GlassContrastHelper.updateContrastInvertFactor(notesPinnedTarget, pEff, expectDark, 1f);
            } else {
                notesPinnedTarget = 0f;
            }

            if (filterBanner != null && filterBanner.getVisibility() == View.VISIBLE && filterBanner.getWidth() > 0) {
                final float fEff = GlassContrastHelper.computeEffectiveGlassLuminance(topPillBg,
                        GlassContrastHelper.estimateUnderlyingLuminanceInBand(filterBanner, chatListView,
                                0f, 0f, filterBanner.getWidth(), filterBanner.getHeight(),
                                topBackdrop, resourceProvider), liquidActive);
                final boolean expectDark = GlassContrastHelper.computeLuminance(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider)) < 0.5f;
                notesFilterBannerTarget = GlassContrastHelper.updateContrastInvertFactor(notesFilterBannerTarget, fEff, expectDark, 1f);
            } else {
                notesFilterBannerTarget = 0f;
            }

            if (chatInputViewsContainer != null && chatActivityEnterView != null && chatInputViewsContainer.getVisibility() == View.VISIBLE && chatInputViewsContainer.getWidth() > 0) {
                final float iEff = GlassContrastHelper.computeEffectiveGlassLuminance(inputPillBg,
                        GlassContrastHelper.estimateUnderlyingLuminanceInBand(chatInputViewsContainer, chatListView,
                                dp(10), 0f, Math.max(dp(80), chatInputViewsContainer.getWidth() - dp(56)), chatInputViewsContainer.getHeight(),
                                bottomBackdrop, resourceProvider), liquidActive);
                final boolean expectDark = GlassContrastHelper.computeLuminance(Theme.getColor(Theme.key_chat_messagePanelText, resourceProvider)) < 0.5f;
                notesInputTarget = GlassContrastHelper.updateContrastInvertFactor(notesInputTarget, iEff, expectDark, 1f);
            }

            if (scrollDownButton != null && scrollDownButton.getVisibility() == View.VISIBLE && scrollDownButton.getWidth() > 0) {
                final float sEff = GlassContrastHelper.computeEffectiveGlassLuminance(inputPillBg,
                        GlassContrastHelper.estimateUnderlyingLuminanceInBand(scrollDownButton, chatListView,
                                0f, 0f, scrollDownButton.getWidth(), scrollDownButton.getHeight(),
                                bottomBackdrop, resourceProvider), liquidActive);
                final boolean expectDark = GlassContrastHelper.computeLuminance(Theme.getColor(Theme.key_glass_defaultIcon, resourceProvider)) < 0.5f;
                notesScrollDownTarget = GlassContrastHelper.updateContrastInvertFactor(notesScrollDownTarget, sEff, expectDark, 1f);
            } else {
                notesScrollDownTarget = 0f;
            }
        }

        final float step = forceSample ? 1f : 0.28f;
        notesBarBackFactor = stepNotesFactor(notesBarBackFactor, notesBarBackTarget, step);
        notesBarTitleFactor = stepNotesFactor(notesBarTitleFactor, notesBarTitleTarget, step);
        notesBarMenuFactor = stepNotesFactor(notesBarMenuFactor, notesBarMenuTarget, step);
        notesPinnedFactor = stepNotesFactor(notesPinnedFactor, notesPinnedTarget, step);
        notesFilterBannerFactor = stepNotesFactor(notesFilterBannerFactor, notesFilterBannerTarget, step);
        notesInputFactor = stepNotesFactor(notesInputFactor, notesInputTarget, step);
        notesScrollDownFactor = stepNotesFactor(notesScrollDownFactor, notesScrollDownTarget, step);

        if (actionBar != null) {
            actionBar.applyAdaptiveGlassContrast(notesBarBackFactor, notesBarTitleFactor, notesBarMenuFactor);
        }
        applyNotesOverlayContrast(notesPinnedFactor, notesFilterBannerFactor);
        if (chatActivityEnterView != null) {
            chatActivityEnterView.applyAdaptiveGlassContrast(notesInputFactor);
        }
        if (searchBottomCountView != null) {
            final int baseSearch = Theme.getColor(Theme.key_chat_messagePanelText, resourceProvider);
            searchBottomCountView.setTextColor(GlassContrastHelper.blendAdaptiveTextColor(baseSearch, notesInputFactor, false));
        }
        if (actionModeCountView != null) {
            final int baseAction = Theme.getColor(Theme.key_chat_messagePanelText, resourceProvider);
            actionModeCountView.setTextColor(GlassContrastHelper.blendAdaptiveTextColor(baseAction, notesInputFactor, false));
        }
        if (scrollDownButton != null) {
            final View inner = scrollDownButton.getChildAt(0);
            if (inner instanceof org.telegram.ui.Components.chat.buttons.ChatActivityBlurredRoundButton) {
                ((org.telegram.ui.Components.chat.buttons.ChatActivityBlurredRoundButton) inner).applyAdaptiveGlassContrast(notesScrollDownFactor);
            }
        }

        final boolean settled = Math.abs(notesBarBackFactor - notesBarBackTarget) < 0.006f
                && Math.abs(notesBarTitleFactor - notesBarTitleTarget) < 0.006f
                && Math.abs(notesBarMenuFactor - notesBarMenuTarget) < 0.006f
                && Math.abs(notesPinnedFactor - notesPinnedTarget) < 0.006f
                && Math.abs(notesFilterBannerFactor - notesFilterBannerTarget) < 0.006f
                && Math.abs(notesInputFactor - notesInputTarget) < 0.006f
                && Math.abs(notesScrollDownFactor - notesScrollDownTarget) < 0.006f;

        if (!settled && !notesAdaptiveTickPosted && !removingFromParent) {
            notesAdaptiveTickPosted = true;
            contentView.postOnAnimation(notesAdaptiveTickRunnable);
        }
    }

    private static float stepNotesFactor(float current, float target, float step) {
        final float next = current + (target - current) * step;
        if (Math.abs(target - next) < 0.008f) {
            return target;
        }
        return MathUtils.clamp(next, 0f, 1f);
    }

    private void applyNotesOverlayContrast(float pinnedFactor, float filterFactor) {
        if (lastNotesPinnedApplied < 0f || Math.abs(lastNotesPinnedApplied - pinnedFactor) >= 0.006f) {
            lastNotesPinnedApplied = pinnedFactor;
            if (pinnedNoteTitleView != null) {
                final int baseAccent = Theme.getColor(Theme.key_chat_messagePanelSend, resourceProvider);
                pinnedNoteTitleView.setTextColor(pinnedFactor > 0.001f
                        ? GlassContrastHelper.blendAdaptiveAccentColor(baseAccent, pinnedFactor)
                        : baseAccent);
            }
            if (pinnedNoteBodyView != null) {
                final int baseBody = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider);
                pinnedNoteBodyView.setTextColor(GlassContrastHelper.blendAdaptiveTextColor(baseBody, pinnedFactor, false));
            }
            if (pinnedNoteUnpinBtn != null) {
                if (pinnedFactor <= 0.001f) {
                    pinnedNoteUnpinBtn.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_topPanelClose, resourceProvider), PorterDuff.Mode.MULTIPLY));
                } else {
                    final int adapted = GlassContrastHelper.blendAdaptiveIconColor(Theme.getColor(Theme.key_chat_topPanelClose, resourceProvider), pinnedFactor);
                    if (notesPinnedCloseFilter == null || lastNotesPinnedCloseColor != adapted) {
                        lastNotesPinnedCloseColor = adapted;
                        notesPinnedCloseFilter = new PorterDuffColorFilter(adapted, PorterDuff.Mode.SRC_IN);
                    }
                    pinnedNoteUnpinBtn.setColorFilter(notesPinnedCloseFilter);
                }
            }
        }
        if (lastNotesFilterBannerApplied < 0f || Math.abs(lastNotesFilterBannerApplied - filterFactor) >= 0.006f) {
            lastNotesFilterBannerApplied = filterFactor;
            if (filterBannerText != null) {
                if (filterFactor <= 0.001f) {
                    ThemeBind.textColor(filterBannerText, Theme.key_windowBackgroundWhiteBlackText, resourceProvider);
                } else {
                    filterBannerText.setTextColor(GlassContrastHelper.blendAdaptiveTextColor(
                            Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider), filterFactor, false));
                }
            }
            if (filterBannerClear != null) {
                if (filterFactor <= 0.001f) {
                    ThemeBind.colorFilter(filterBannerClear, Theme.key_chat_topPanelClose, resourceProvider, PorterDuff.Mode.MULTIPLY);
                } else {
                    final int adapted = GlassContrastHelper.blendAdaptiveIconColor(Theme.getColor(Theme.key_chat_topPanelClose, resourceProvider), filterFactor);
                    if (notesFilterClearFilter == null || lastNotesFilterClearColor != adapted) {
                        lastNotesFilterClearColor = adapted;
                        notesFilterClearFilter = new PorterDuffColorFilter(adapted, PorterDuff.Mode.SRC_IN);
                    }
                    filterBannerClear.setColorFilter(notesFilterClearFilter);
                }
            }
        }
    }

    private float calculateCurrentBubbleHeight() {
        boolean actionMode = actionBar != null && actionBar.isActionModeShowed();
        if (actionMode || isSearching) {
            return dp(48);
        }
        float enterH = chatActivityEnterView != null ? chatActivityEnterView.getIslandTotalHeight(false) : dp(51);
        float bannerH = editBannerRow != null && editBannerRow.getVisibility() == View.VISIBLE ? Math.max(dp(38), editBannerRow.getMeasuredHeight()) : 0;
        return Math.max(dp(51), enterH + bannerH);
    }

    private void syncInputBubbleHeight() {
        if (chatInputViewsContainer == null) {
            return;
        }
        float h = calculateCurrentBubbleHeight();
        chatInputViewsContainer.setInputBubbleHeight(h);
        if (chatActivityFadeView != null) {
            chatActivityFadeView.setFadeZoneBottom((int) (windowInsetsStateHolder.getAnimatedMaxBottomInset() + h + dp(16)));
        }
        updateEmptyContainerPosition();
        updateListPaddingAndButtons();
    }

    private float getTopExtraOverlayHeight() {
        float extra = 0f;
        if (filterChipsContainer != null && filterChipsContainer.getVisibility() == View.VISIBLE) {
            extra += dp(46);
        }
        if (pinnedNoteBar != null && pinnedNoteBar.getVisibility() == View.VISIBLE) {
            extra += Math.max(dp(46), pinnedNoteBar.getMeasuredHeight()) + dp(6);
        }
        if (filterBanner != null && filterBanner.getVisibility() == View.VISIBLE) {
            extra += Math.max(dp(38), filterBanner.getMeasuredHeight()) + dp(4);
        }
        return extra;
    }

    private void updateTopOverlayPositions() {
        float y = AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() + dp(4) + contentPanTranslation;
        if (filterChipsContainer != null && filterChipsContainer.getVisibility() == View.VISIBLE) {
            filterChipsContainer.setTranslationY(y);
            y += Math.max(dp(44), filterChipsContainer.getMeasuredHeight()) + dp(2);
        }
        if (pinnedNoteBar != null && pinnedNoteBar.getVisibility() == View.VISIBLE) {
            pinnedNoteBar.setTranslationY(y);
            y += Math.max(dp(46), pinnedNoteBar.getMeasuredHeight()) + dp(6);
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
        float bubbleH = chatInputViewsContainer != null ? chatInputViewsContainer.getInputBubbleHeight() : dp(51);
        float topExtra = getTopExtraOverlayHeight();
        float bottomCovered = dp(9) + windowInsetsStateHolder.getAnimatedImeBottomInset() + bubbleH;
        emptyViewContainer.setTranslationY(0.5f * (topExtra + contentPanTranslation - bottomCovered));
    }

    private void updateListPaddingAndButtons() {
        updateTopOverlayPositions();
        if (chatListView == null) {
            return;
        }
        float bubbleH = chatInputViewsContainer != null ? chatInputViewsContainer.getInputBubbleHeight() : dp(51);
        int bottomPad = (int) (recommendedAdditionalSizeY + windowInsetsStateHolder.getAnimatedMaxBottomInset() + bubbleH + dp(18));
        int topPad = (int) (recommendedAdditionalSizeY + AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() + getTopExtraOverlayHeight() + dp(8));
        if (chatListView.getPaddingTop() != topPad || chatListView.getPaddingBottom() != bottomPad) {
            chatListView.setPadding(chatListView.getPaddingLeft(), topPad, chatListView.getPaddingRight(), bottomPad);
        }
        if (scrollDownButton != null) {
            scrollDownButton.setTranslationY(-(windowInsetsStateHolder.getAnimatedMaxBottomInset() + bubbleH + dp(20)));
        }
        if (chatActivityFadeView != null) {
            chatActivityFadeView.setFadeZoneTop((int) (AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() + getTopExtraOverlayHeight()));
        }
    }

    private void onInsetsUpdated() {
        if (chatInputViewsContainer != null) {
            chatInputViewsContainer.checkInsets();
        }
        syncInputBubbleHeight();
    }

    @Nullable
    private WindowInsetsCompat getNotesWindowInsets(@Nullable WindowInsetsCompat insets) {
        if (insets != null && chatAttachAlert != null && chatAttachAlert.isShowing() && !chatAttachAlert.isDismissed()) {
            return new WindowInsetsCompat.Builder(insets)
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
                    .setVisible(WindowInsetsCompat.Type.ime(), false)
                    .build();
        }
        return insets;
    }

    @NonNull
    private WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
        WindowInsetsCompat filtered = getNotesWindowInsets(insets);
        final Insets systemInsets = AndroidUtilities.getDefaultWindowInsets(filtered, false);
        windowInsetsStateHolder.setInsets(filtered);
        if (chatListView != null) {
            chatListView.setPadding(systemInsets.left, chatListView.getPaddingTop(), systemInsets.right, chatListView.getPaddingBottom());
        }
        updateListPaddingAndButtons();
        return WindowInsetsCompat.CONSUMED;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetNewWallpapper || id == NotificationCenter.didApplyNewTheme) {
            if (contentView != null) {
                contentView.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());
            }
            if (chatListView != null) {
                chatListView.invalidateViews();
            }
            if (pinnedNoteIconView != null) {
                pinnedNoteIconView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelSend, resourceProvider), PorterDuff.Mode.SRC_IN));
            }
            if (pinnedNoteTitleView != null) {
                pinnedNoteTitleView.setTextColor(Theme.getColor(Theme.key_chat_messagePanelSend, resourceProvider));
            }
            if (pinnedNoteBodyView != null) {
                pinnedNoteBodyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
            }
            if (pinnedNoteUnpinBtn != null) {
                pinnedNoteUnpinBtn.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_topPanelClose, resourceProvider), PorterDuff.Mode.MULTIPLY));
            }
            if (editBannerAccentDrawable != null) {
                editBannerAccentDrawable.setColor(Theme.getColor(Theme.key_chat_messagePanelSend, resourceProvider));
            }
            if (editBannerTitle != null) {
                editBannerTitle.setTextColor(Theme.getColor(Theme.key_chat_messagePanelSend, resourceProvider));
            }
            if (editBannerPreview != null) {
                editBannerPreview.setTextColor(Theme.getColor(Theme.key_chat_messagePanelHint, resourceProvider));
            }
            if (editBannerClose != null) {
                editBannerClose.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons, resourceProvider), PorterDuff.Mode.MULTIPLY));
            }
            if (pinnedNoteBar != null) {
                pinnedNoteBar.invalidate();
            }
            if (filterChipsContainer != null) {
                filterChipsContainer.invalidate();
            }
            if (filterBanner != null) {
                filterBanner.invalidate();
            }
            if (floatingDateView != null) {
                floatingDateView.invalidate();
            }
            refreshFilterChipsSelection();
            resetNotesAdaptiveState();
            scheduleNotesAdaptiveGlassContrastUpdate(true);
        } else if (id == NotificationCenter.emojiLoaded) {
            if (chatListView != null) {
                chatListView.invalidateViews();
            }
        } else if (id == NotificationCenter.mirrlyNotesDidUpdate) {
            reloadNotes(true);
        } else if (id == NotificationCenter.mirrlyLocalVoiceRecorded) {
            int guid = (Integer) args[0];
            if (guid == classGuid) {
                saveLocalVoiceNote((TLRPC.TL_document) args[1], (String) args[2]);
            }
        } else if (id == NotificationCenter.messagePlayingDidStart) {
            MessageObject mo = (MessageObject) args[0];
            if ((mo.isRoundVideo() || mo.isVideo()) && contentView != null && contentView.getParent() != null) {
                MediaController.getInstance().setTextureView(createTextureView(true), aspectRatioFrameLayout, videoPlayerContainer, true);
                updateTextureViewPosition();
            }
            refreshPlayingCells();
        } else if (id == NotificationCenter.messagePlayingDidReset || id == NotificationCenter.messagePlayingPlayStateChanged) {
            if (id == NotificationCenter.messagePlayingDidReset) {
                destroyTextureView();
            }
            refreshPlayingCells();
        } else if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            Integer mid = (Integer) args[0];
            if (chatListView != null) {
                for (int i = 0; i < chatListView.getChildCount(); i++) {
                    View v = chatListView.getChildAt(i);
                    if (v instanceof ChatMessageCell) {
                        ChatMessageCell cell = (ChatMessageCell) v;
                        MessageObject mo = cell.getMessageObject();
                        if (mo != null && mo.getId() == mid) {
                            cell.updatePlayingMessageProgress();
                        }
                    }
                }
            }
        }
    }

    private void refreshPlayingCells() {
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

    private class NotesChatFragmentView extends SizeNotifierFrameLayout {
        private final WallpaperBitmapProvider wallpaperBitmapProvider = new WallpaperBitmapProvider();

        NotesChatFragmentView(Context context, INavigationLayout parentLayout) {
            super(context, parentLayout);
            adjustPanLayoutHelper = new AdjustPanLayoutHelper(this) {
                @Override
                protected void onTransitionStart(boolean keyboardVisible, int contentHeight) {
                    if (chatActivityEnterView != null) {
                        chatActivityEnterView.onAdjustPanTransitionStart(keyboardVisible, contentHeight);
                    }
                }

                @Override
                protected void onTransitionEnd() {
                    if (chatActivityEnterView != null) {
                        chatActivityEnterView.onAdjustPanTransitionEnd();
                    }
                }

                @Override
                protected void onPanTranslationUpdate(float y, float progress, boolean keyboardVisible) {
                    if (getParentLayout() != null && getParentLayout().isPreviewOpenAnimationInProgress()) {
                        return;
                    }
                    contentPanTranslation = y;
                    if (chatAttachAlert != null && chatAttachAlert.isShowing()) {
                        return;
                    }
                    actionBar.setTranslationY(y);
                    updateTopOverlayPositions();
                    updateEmptyContainerPosition();
                    contentView.setBackgroundTranslation((int) y);
                    if (instantCameraView != null) {
                        instantCameraView.onPanTranslationUpdate(y);
                    }
                    if (chatListView != null) {
                        chatListView.invalidate();
                    }
                    updateMessagesVisiblePart();
                    if (chatActivityEnterView != null) {
                        chatActivityEnterView.onAdjustPanTransitionUpdate(y, progress, keyboardVisible);
                    }
                }

                @Override
                protected boolean heightAnimationEnabled() {
                    INavigationLayout actionBarLayout = getParentLayout();
                    if (AndroidUtilities.isInMultiwindow || actionBarLayout == null) {
                        return false;
                    }
                    if ((MirrlyNotesChatActivity.this == actionBarLayout.getLastFragment() && actionBarLayout.isTransitionAnimationInProgress()) || actionBarLayout.isPreviewOpenAnimationInProgress() || isPaused || !openAnimationEnded || (chatAttachAlert != null && chatAttachAlert.isShowing())) {
                        return false;
                    }
                    return true;
                }

                @Override
                protected int startOffset() {
                    int keyboardSize = getKeyboardHeight();
                    if (keyboardSize <= dp(20) && chatActivityEnterView != null && chatActivityEnterView.isPopupShowing()) {
                        return chatActivityEnterView.getEmojiPadding();
                    }
                    return 0;
                }
            };
        }

        @Override
        public int measureKeyboardHeight() {
            if (chatAttachAlert != null && chatAttachAlert.isShowing()) {
                return keyboardHeight = 0;
            }
            return super.measureKeyboardHeight();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (adjustPanLayoutHelper != null) {
                adjustPanLayoutHelper.onAttach();
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (adjustPanLayoutHelper != null) {
                adjustPanLayoutHelper.onDetach();
            }
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
            updateTextureViewPosition();
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

    private class NotesAdapter extends RecyclerListView.SelectionAdapter {
        private final Context mContext;

        NotesAdapter(Context context) {
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
            NoteItem item = items.get(position);
            if (item.isDate()) {
                return item.messageObject.getId();
            }
            return item.note.id.hashCode() & 0x7FFFFFFFL;
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
                        MirrlyVaultStorage.DossierNote note = findNoteForMessageObject(cell1.getMessageObject());
                        if (note != null && note.dialogId != 0) {
                            openNoteTarget(note);
                        }
                    }

                    @Override
                    public void didPressImage(ChatMessageCell cell1, float x, float y, boolean fullPreview) {
                        if (actionBar != null && actionBar.isActionModeShowed()) {
                            MirrlyVaultStorage.DossierNote note = findNoteForMessageObject(cell1.getMessageObject());
                            if (note != null) {
                                toggleSelection(note);
                            }
                            return;
                        }
                        MessageObject msg = cell1.getMessageObject();
                        if (msg == null || getParentActivity() == null) {
                            return;
                        }
                        if (msg.getInputStickerSet() != null) {
                            StickersAlert alert = new StickersAlert(getParentActivity(), MirrlyNotesChatActivity.this, msg.getInputStickerSet(), null, chatActivityEnterView, resourceProvider, false);
                            showDialog(alert);
                        } else if (msg.isVideo() || msg.type == MessageObject.TYPE_PHOTO || msg.isGif()) {
                            openPhotoViewerForMessage(cell1, msg);
                        } else if (msg.type == MessageObject.TYPE_GEO) {
                            if (AndroidUtilities.isMapsInstalled(MirrlyNotesChatActivity.this)) {
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
                                    BulletinFactory.of(MirrlyNotesChatActivity.this).createSimpleBulletin(R.raw.info, getString(R.string.MirrlyNotesMediaMissingFile)).show();
                                    return;
                                }
                                try {
                                    AndroidUtilities.openForView(msg, getParentActivity(), resourceProvider, false);
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                            }
                        } else {
                            showNoteItemOptions(cell1);
                        }
                    }

                    @Override
                    public boolean needPlayMessage(ChatMessageCell cell1, MessageObject messageObject, boolean muted) {
                        if (messageObject != null && (messageObject.isVoice() || messageObject.isMusic() || messageObject.isRoundVideo())) {
                            if (messageObject.messageOwner != null && !TextUtils.isEmpty(messageObject.messageOwner.attachPath) && !new File(messageObject.messageOwner.attachPath).exists()) {
                                BulletinFactory.of(MirrlyNotesChatActivity.this).createSimpleBulletin(R.raw.info, getString(R.string.MirrlyNotesMediaMissingFile)).show();
                                return false;
                            }
                            boolean res = MediaController.getInstance().playMessage(messageObject, muted);
                            if (res && (messageObject.isVoice() || messageObject.isRoundVideo())) {
                                MediaController.getInstance().setVoiceMessagesPlaylist(createVoiceNotesPlaylist(messageObject), false);
                            }
                            return res;
                        }
                        return false;
                    }

                    @Override
                    public void didPressVoteButtons(ChatMessageCell cell1, ArrayList<TLRPC.PollAnswer> buttons, int showCount, int x, int y) {
                        MirrlyVaultStorage.DossierNote note = findNoteForMessageObject(cell1.getMessageObject());
                        if (note == null || !(cell1.getMessageObject().messageOwner.media instanceof TLRPC.TL_messageMediaPoll) || buttons == null || buttons.isEmpty()) {
                            return;
                        }
                        TLRPC.TL_messageMediaPoll mp = (TLRPC.TL_messageMediaPoll) cell1.getMessageObject().messageOwner.media;
                        if (mp.results == null) {
                            return;
                        }
                        byte[] targetOpt = buttons.get(0).option;
                        for (int i = 0; i < mp.results.results.size(); i++) {
                            TLRPC.PollAnswerVoters v = mp.results.results.get(i);
                            if (java.util.Arrays.equals(v.option, targetOpt)) {
                                v.chosen = !v.chosen;
                                v.voters = v.chosen ? 1 : 0;
                            } else if (mp.poll != null && !mp.poll.multiple_choice) {
                                v.chosen = false;
                                v.voters = 0;
                            }
                        }
                        MirrlyVaultStorage.DossierNote updated = new MirrlyVaultStorage.DossierNote(
                                note.id, note.prefKey, note.dialogId, note.msgId, note.text,
                                note.createdAt, note.updatedAt, note.spoiler, note.pinned,
                                note.peerTitle, note.authorName, note.quotePreview, note.secretChat,
                                note.mediaKind, note.localMediaPath, note.localThumbPath,
                                MirrlyVaultStorage.encodeMedia(mp), note.serializedEntitiesBase64,
                                note.fileName, note.mimeType, note.mediaDuration, note.mediaSize,
                                note.mediaWidth, note.mediaHeight, note.groupedId
                        );
                        MirrlyVaultStorage.restoreNote(updated);
                        cell1.invalidate();
                    }

                    @Override
                    public void didLongPress(ChatMessageCell cell1, float x, float y) {
                        cell1.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        showNoteItemOptions(cell1);
                    }
                });
                view = cell;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            NoteItem item = items.get(position);
            if (holder.itemView instanceof ChatActionCell) {
                ChatActionCell actionCell = (ChatActionCell) holder.itemView;
                actionCell.setMessageObject(item.messageObject);
                actionCell.setAlpha(1.0f);
            } else if (holder.itemView instanceof ChatMessageCell) {
                ChatMessageCell cell = (ChatMessageCell) holder.itemView;
                cell.isChat = false;
                boolean pinnedBottom = false;
                if (position - 1 >= 0 && !items.get(position - 1).isDate()) {
                    NoteItem below = items.get(position - 1);
                    if (item.note != null && below.note != null && Math.abs(item.note.createdAt - below.note.createdAt) <= 5 * 60) {
                        pinnedBottom = true;
                    }
                }
                boolean pinnedTop = false;
                if (position + 1 < items.size() && !items.get(position + 1).isDate()) {
                    NoteItem above = items.get(position + 1);
                    if (item.note != null && above.note != null && Math.abs(item.note.createdAt - above.note.createdAt) <= 5 * 60) {
                        pinnedTop = true;
                    }
                }
                cell.setMessageObject(item.messageObject, null, pinnedBottom, pinnedTop, false);
                boolean isSelected = actionBar != null && actionBar.isActionModeShowed() && item.note != null && selectedNoteIds.contains(item.note.id);
                boolean isHighlighted = item.note != null && TextUtils.equals(activeHighlightNoteId, item.note.id);
                cell.setChecked(false, isSelected, false);
                cell.setHighlighted(isHighlighted);
                cell.setHighlightedText(searchQuery);
            }
        }

        @Override
        public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
            View view = holder.itemView;
            if (view instanceof ChatMessageCell || view instanceof ChatActionCell) {
                view.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        view.getViewTreeObserver().removeOnPreDrawListener(this);
                        if (chatListView == null || contentView == null) {
                            return true;
                        }
                        int height = chatListView.getMeasuredHeight();
                        int top = view.getTop();
                        int viewTop = top >= 0 ? 0 : -top;
                        int viewBottom = view.getMeasuredHeight();
                        if (viewBottom > height) {
                            viewBottom = viewTop + height;
                        }
                        if (view instanceof ChatMessageCell) {
                            ((ChatMessageCell) view).setVisiblePart(viewTop, viewBottom - viewTop, contentView.getHeightWithKeyboard() - dp(48) - chatListView.getTop(), 0, view.getY() + actionBar.getMeasuredHeight() - contentView.getBackgroundTranslationY(), contentView.getMeasuredWidth(), contentView.getBackgroundSizeY(), 0, 0, 0);
                        } else {
                            ((ChatActionCell) view).setVisiblePart(view.getY() + actionBar.getMeasuredHeight() - contentView.getBackgroundTranslationY(), contentView.getBackgroundSizeY());
                        }
                        return true;
                    }
                });
            }
        }
    }

    // ==================== File & Metadata Helpers ====================

    private static int[] readImageDimensions(String path) {
        int[] out = new int[]{800, 600};
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, opts);
            if (opts.outWidth > 0 && opts.outHeight > 0) {
                out[0] = opts.outWidth;
                out[1] = opts.outHeight;
            }
        } catch (Throwable ignore) {
        }
        return out;
    }

    private static int[] readVideoMetadata(String path, @Nullable VideoEditedInfo editedInfo) {
        int[] out = new int[]{
                editedInfo != null && editedInfo.resultWidth > 0 ? editedInfo.resultWidth : 1280,
                editedInfo != null && editedInfo.resultHeight > 0 ? editedInfo.resultHeight : 720,
                editedInfo != null && editedInfo.estimatedDuration > 0 ? (int) Math.max(1, editedInfo.estimatedDuration / 1000L) : 1
        };
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(path);
            String wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            String rotStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            String durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            int w = TextUtils.isEmpty(wStr) ? out[0] : Integer.parseInt(wStr);
            int h = TextUtils.isEmpty(hStr) ? out[1] : Integer.parseInt(hStr);
            int rot = TextUtils.isEmpty(rotStr) ? 0 : Integer.parseInt(rotStr);
            if (rot == 90 || rot == 270) {
                int tmp = w;
                w = h;
                h = tmp;
            }
            if (w > 0 && h > 0) {
                out[0] = w;
                out[1] = h;
            }
            if (!TextUtils.isEmpty(durStr)) {
                out[2] = (int) Math.max(1L, Long.parseLong(durStr) / 1000L);
            }
        } catch (Throwable ignore) {
        } finally {
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (Throwable ignore) {
                }
            }
        }
        return out;
    }

    private static TLRPC.TL_photo createFallbackLocalPhoto(int w, int h) {
        TLRPC.TL_photo photo = new TLRPC.TL_photo();
        photo.date = (int) (System.currentTimeMillis() / 1000);
        photo.file_reference = new byte[0];
        TLRPC.TL_photoSize size = new TLRPC.TL_photoSize();
        size.w = Math.max(1, w);
        size.h = Math.max(1, h);
        size.size = 1;
        size.location = new TLRPC.TL_fileLocationUnavailable();
        size.type = "x";
        photo.sizes.add(size);
        return photo;
    }

    private static String resolveMimeFromExt(@Nullable String ext) {
        if (TextUtils.isEmpty(ext)) {
            return "application/octet-stream";
        }
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase(Locale.ROOT));
        return !TextUtils.isEmpty(mime) ? mime : "application/octet-stream";
    }

    @Nullable
    private static String queryDisplayName(Context context, Uri uri) {
        try (android.database.Cursor cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    return cursor.getString(idx);
                }
            }
        } catch (Throwable ignore) {
        }
        return null;
    }
}
