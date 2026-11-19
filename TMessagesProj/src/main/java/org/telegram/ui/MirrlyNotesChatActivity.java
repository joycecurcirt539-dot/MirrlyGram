package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.mirrly.MirrlyVaultStorage;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.ThanosEffect;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.DownscaleScrollableNoiseSuppressor;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import me.vkryl.core.BitwiseUtils;

public class MirrlyNotesChatActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int MENU_SEARCH = 1;
    private static final int MENU_FILTER = 2;

    private final GlassEngine glassEngine = new GlassEngine();
    private final @NonNull GlassSourceWrapped navbarContentSourceWallpaper;
    private final @NonNull BlurredBackgroundDrawableViewFactory navbarContentDrawableFactory;
    private final @Nullable GlassSourceRenderNode glassBackgroundSourceRenderNode;
    private final @Nullable GlassSourceRenderNode glassBackgroundSourceFrostedRenderNode;
    private final @NonNull BlurredBackgroundDrawableViewFactory glassBackgroundDrawableFactory;
    private final @NonNull BlurredBackgroundDrawableViewFactory glassBackgroundDrawableFactoryFrosted;
    private final @Nullable DownscaleScrollableNoiseSuppressor scrollableViewNoiseSuppressor;
    private final int recommendedAdditionalSizeY;

    private final WindowInsetsStateHolder windowInsetsStateHolder = new WindowInsetsStateHolder(this::onInsetsUpdated);

    private NotesChatFragmentView contentView;
    private ChatActivityFadeView chatActivityFadeView;
    private ChatAvatarContainer avatarContainer;
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

    private FrameLayout filterBanner;
    private TextView filterBannerText;
    private ImageView filterBannerClear;

    private ChatActivityBlurredRoundPageDownButton scrollDownButton;
    private boolean scrollDownVisible = false;

    private ChatInputViewsContainer chatInputViewsContainer;
    private FrameLayout chatInputBubbleContainer;
    private LinearLayout inputPanelColumn;
    private LinearLayout editBannerRow;
    private TextView editBannerTitle;
    private TextView editBannerPreview;
    private ImageView editBannerClose;
    private ImageView spoilerToggleBtn;
    private EditTextBoldCursor noteEditText;
    private FrameLayout sendBtnContainer;
    private ImageView sendBtnIcon;

    private ThanosEffect thanosEffect;
    private boolean removingFromParent = false;

    private final ArrayList<NoteItem> items = new ArrayList<>();
    private final Map<String, Integer> stableMessageIdMap = new HashMap<>();
    private final AtomicInteger nextSyntheticId = new AtomicInteger(10000);

    private String searchQuery = "";
    private int activeFilter = MirrlyVaultStorage.FILTER_ALL;
    private boolean composerSpoiler = false;
    private @Nullable MirrlyVaultStorage.DossierNote editingNote = null;
    private boolean composerButtonActive = false;

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

            glassBackgroundDrawableFactoryFrosted = new BlurredBackgroundDrawableViewFactory(glassBackgroundSourceFrostedRenderNode);
            glassBackgroundDrawableFactoryFrosted.setGlassEngine(glassEngine);
            glassBackgroundDrawableFactoryFrosted.setOutset(dp(48));

            if (LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS)) {
                glassBackgroundSourceRenderNode = new GlassSourceRenderNode(navbarContentSourceWallpaper);
                glassBackgroundSourceRenderNode.setScrollableNoiseSuppressor(scrollableViewNoiseSuppressor, DownscaleScrollableNoiseSuppressor.DRAW_GLASS);
                glassBackgroundSourceRenderNode.setUnderSource(navbarContentSourceWallpaper);
                glassBackgroundDrawableFactory = new BlurredBackgroundDrawableViewFactory(glassBackgroundSourceRenderNode);
                glassBackgroundDrawableFactory.setGlassEngine(glassEngine);
                glassBackgroundDrawableFactory.setOutset(dp(8));
                glassBackgroundDrawableFactory.setLiquidGlassEffectAllowed(true);
                recommendedAdditionalSizeY = 0;
            } else {
                glassBackgroundSourceRenderNode = null;
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
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.mirrlyNotesDidUpdate);
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
        if (thanosEffect != null) {
            thanosEffect.kill();
            thanosEffect = null;
        }
        Bulletin.removeDelegate(this);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.mirrlyNotesDidUpdate);
        super.onFragmentDestroy();
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public boolean drawEdgeNavigationBar() {
        return false;
    }

    @Override
    public View createView(Context context) {
        Theme.createChatResources(context, false);
        hasOwnBackground = true;

        setupActionBar(context);

        contentView = new NotesChatFragmentView(context);
        fragmentView = contentView;

        ViewPositionWatcher viewPositionWatcher = new ViewPositionWatcher(contentView);
        glassBackgroundDrawableFactory.setSourceRootView(viewPositionWatcher, contentView);
        glassBackgroundDrawableFactoryFrosted.setSourceRootView(viewPositionWatcher, contentView);
        navbarContentDrawableFactory.setSourceRootView(viewPositionWatcher, contentView);

        contentView.setOccupyStatusBar(!AndroidUtilities.isTablet());
        contentView.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());
        actionBar.setupGlass(glassBackgroundDrawableFactory, BlurredBackgroundProviderImpl.topPanelChatActivity(resourceProvider), false);
        actionBar.setChatAvatarContainer(avatarContainer);
        avatarContainer.setActionBar(actionBar);

        buildListAndEmptyState(context);
        buildFadeAndFloatingOverlays(context);
        buildScrollDownButton(context);
        buildFilterBanner(context);
        buildBottomInputBar(context);

        contentView.addView(actionBar);

        if (context instanceof LaunchActivity) {
            windowInsetsStateHolder.setupAnimatedInsetsProvider(((LaunchActivity) context).getRootAnimatedInsetsListener(), contentView);
        }
        ViewCompat.setOnApplyWindowInsetsListener(contentView, this::onApplyWindowInsets);

        reloadNotes(false);
        updateHeaderSubtitle();
        updateFilterBanner(false);
        updateComposerVisualState(false);

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
                    finishFragment();
                } else if (id == MENU_FILTER) {
                    showFilterMenu();
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
        actionBar.addView(avatarContainer, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 54, 0, 96, 0));

        ActionBarMenu menu = actionBar.createMenu();
        searchItem = menu.addItem(MENU_SEARCH, R.drawable.outline_header_search)
                .setIsSearchField(true)
                .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                    @Override
                    public void onSearchExpand() {
                        avatarContainer.setVisibility(View.GONE);
                        if (filterItem != null) {
                            filterItem.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onSearchCollapse() {
                        searchQuery = "";
                        avatarContainer.setVisibility(View.VISIBLE);
                        if (filterItem != null) {
                            filterItem.setVisibility(View.VISIBLE);
                        }
                        reloadNotes(true);
                    }

                    @Override
                    public void onTextChanged(EditText editText) {
                        String next = editText.getText() != null ? editText.getText().toString() : "";
                        if (!TextUtils.equals(searchQuery, next)) {
                            searchQuery = next;
                            reloadNotes(true);
                        }
                    }

                    @Override
                    public void onSearchPressed(EditText editText) {
                        String next = editText.getText() != null ? editText.getText().toString() : "";
                        searchQuery = next;
                        reloadNotes(true);
                        AndroidUtilities.hideKeyboard(editText);
                    }
                });
        searchItem.setSearchFieldHint(getString(R.string.MirrlyNotesSearchHint));
        searchItem.setSearchPaddingStart(7);

        filterItem = menu.addItem(MENU_FILTER, R.drawable.menu_tag_filter);
        filterItem.setContentDescription(getString(R.string.MirrlyNotesFilterDesc));
    }

    private void buildListAndEmptyState(Context context) {
        chatListView = new RecyclerListView(context) {
            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                return super.drawChild(canvas, child, drawingTime);
            }
        };
        chatListView.setVerticalScrollBarEnabled(false);
        chatListView.setClipToPadding(false);
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
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                chatListView.invalidate();
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
                showNoteItemOptions(cell);
            }
        });
        chatListView.setOnItemLongClickListener((view, position, x, y) -> {
            if (view instanceof ChatMessageCell) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                showNoteItemOptions((ChatMessageCell) view);
                return true;
            }
            return false;
        });

        attachSwipeToEdit(chatListView);

        contentView.addView(chatListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        glassEngine.addScrolledView(chatListView);

        emptyViewContainer = new FrameLayout(context);
        emptyViewContainer.setVisibility(View.INVISIBLE);
        contentView.addView(emptyViewContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 28, 0, 28, 40));

        emptyCardView = new LinearLayout(context);
        emptyCardView.setOrientation(LinearLayout.VERTICAL);
        emptyCardView.setGravity(Gravity.CENTER_HORIZONTAL);
        emptyCardView.setPadding(dp(20), dp(18), dp(20), dp(18));
        emptyCardView.setBackground(Theme.createServiceDrawable(dp(16), emptyCardView, contentView));

        emptyLottieView = new RLottieImageView(context);
        emptyLottieView.setAutoRepeat(true);
        emptyLottieView.setAnimation(R.raw.utyan_saved_messages, 110, 110);
        emptyLottieView.playAnimation();
        emptyCardView.addView(emptyLottieView, LayoutHelper.createLinear(110, 110, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 8));

        emptyTitleView = new TextView(context);
        emptyTitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptyTitleView.setTypeface(AndroidUtilities.bold());
        emptyTitleView.setGravity(Gravity.CENTER);
        ThemeBind.textColor(emptyTitleView, Theme.key_chat_serviceText, resourceProvider);
        emptyCardView.addView(emptyTitleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 4));

        emptySubtitleView = new TextView(context);
        emptySubtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        emptySubtitleView.setGravity(Gravity.CENTER);
        ThemeBind.textColor(emptySubtitleView, Theme.key_chat_serviceText, resourceProvider);
        emptyCardView.addView(emptySubtitleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        emptyViewContainer.addView(emptyCardView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        chatListView.setEmptyView(emptyViewContainer);
        chatListView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA_SCALE);
    }

    private void buildFadeAndFloatingOverlays(Context context) {
        chatActivityFadeView = new ChatActivityFadeView(context);
        chatActivityFadeView.setup(navbarContentDrawableFactory);
        chatActivityFadeView.setFadeZoneTop(AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() + dp(2));
        chatActivityFadeView.setFadeHeightTop(dp(52));
        chatActivityFadeView.setFadeZoneBottom(AndroidUtilities.navigationBarHeight + dp(68));
        chatActivityFadeView.setFadeHeightBottom(dp(52));
        contentView.addView(chatActivityFadeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        floatingDateView = new ChatActionCell(context);
        floatingDateView.setAlpha(0.0f);
        floatingDateView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        contentView.addView(floatingDateView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));
    }

    private void buildScrollDownButton(Context context) {
        scrollDownButton = ChatActivityBlurredRoundPageDownButton.create(
                context,
                44,
                24,
                resourceProvider,
                glassBackgroundDrawableFactory,
                BlurredBackgroundProviderImpl.bottomPanelChatActivity(resourceProvider),
                R.drawable.pagedown
        );
        scrollDownButton.setVisibility(View.GONE);
        scrollDownButton.setAlpha(0f);
        scrollDownButton.setScaleX(0.6f);
        scrollDownButton.setScaleY(0.6f);
        scrollDownButton.setPivotX(dp(22));
        scrollDownButton.setPivotY(dp(22));
        scrollDownButton.setContentDescription(getString(R.string.AccDescrPageDown));
        scrollDownButton.setOnClickListener(v -> {
            if (chatListView != null && !items.isEmpty()) {
                chatListView.smoothScrollToPosition(0);
            }
        });
        contentView.addView(scrollDownButton, LayoutHelper.createFrame(44, 52, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 12, 0));
    }

    private void buildFilterBanner(Context context) {
        filterBanner = new FrameLayout(context);
        filterBanner.setVisibility(View.GONE);
        filterBanner.setAlpha(0f);
        filterBanner.setBackground(Theme.createServiceDrawable(dp(14), filterBanner, contentView));

        filterBannerText = new TextView(context);
        filterBannerText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        filterBannerText.setTypeface(AndroidUtilities.bold());
        ThemeBind.textColor(filterBannerText, Theme.key_chat_serviceText, resourceProvider);
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
        ThemeBind.colorFilter(filterBannerClear, Theme.key_chat_serviceText, resourceProvider, PorterDuff.Mode.MULTIPLY);
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
            reloadNotes(true);
        });
        contentView.addView(filterBanner, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 16, 0, 16, 0));
    }

    private void buildBottomInputBar(Context context) {
        chatInputViewsContainer = new ChatInputViewsContainer(context);
        chatInputViewsContainer.setClipChildren(false);
        chatInputViewsContainer.setWindowInsetsProvider(windowInsetsStateHolder);
        chatInputViewsContainer.setInputIslandBubbleDrawable(
                glassBackgroundDrawableFactory.create(chatInputViewsContainer, BlurredBackgroundProviderImpl.bottomPanelChatActivity(resourceProvider))
        );
        chatInputViewsContainer.setUnderKeyboardBackgroundDrawable(
                glassBackgroundDrawableFactoryFrosted.create(chatInputViewsContainer, BlurredBackgroundProviderImpl.bottomPanelChatActivity(resourceProvider))
        );
        chatInputViewsContainer.setInputBubbleOffsets(dp(4), dp(4));

        chatInputBubbleContainer = chatInputViewsContainer.getInputIslandBubbleContainer();
        chatInputBubbleContainer.setClipChildren(false);

        inputPanelColumn = new LinearLayout(context);
        inputPanelColumn.setOrientation(LinearLayout.VERTICAL);
        inputPanelColumn.setPadding(dp(8), dp(2), dp(8), dp(2));

        editBannerRow = new LinearLayout(context);
        editBannerRow.setOrientation(LinearLayout.HORIZONTAL);
        editBannerRow.setGravity(Gravity.CENTER_VERTICAL);
        editBannerRow.setPadding(dp(8), dp(6), dp(4), dp(4));
        editBannerRow.setVisibility(View.GONE);

        View accentStripe = new View(context);
        accentStripe.setBackgroundColor(Theme.getColor(Theme.key_chat_messagePanelSend, resourceProvider));
        editBannerRow.addView(accentStripe, LayoutHelper.createLinear(2, 28, 0, 0, 8, 0));

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

        editBannerRow.addView(editTextMeta, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        editBannerClose = new ImageView(context);
        editBannerClose.setScaleType(ImageView.ScaleType.CENTER);
        editBannerClose.setImageResource(R.drawable.msg_close);
        editBannerClose.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons, resourceProvider), PorterDuff.Mode.MULTIPLY));
        editBannerClose.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourceProvider), 1));
        editBannerClose.setOnClickListener(v -> cancelEditMode());
        editBannerRow.addView(editBannerClose, LayoutHelper.createLinear(34, 34));

        inputPanelColumn.addView(editBannerRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout inputRow = new LinearLayout(context);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.BOTTOM);
        inputRow.setMinimumHeight(dp(44));

        spoilerToggleBtn = new ImageView(context);
        spoilerToggleBtn.setScaleType(ImageView.ScaleType.CENTER);
        spoilerToggleBtn.setImageResource(R.drawable.msg_spoiler);
        spoilerToggleBtn.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourceProvider), 1));
        spoilerToggleBtn.setContentDescription(getString(R.string.MirrlyNotesSpoilerToggleDesc));
        spoilerToggleBtn.setOnClickListener(v -> {
            composerSpoiler = !composerSpoiler;
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            updateSpoilerToggleIcon();
            BulletinFactory.of(this).createSimpleBulletin(
                    R.raw.info,
                    getString(composerSpoiler ? R.string.MirrlyNotesSpoilerOnToast : R.string.MirrlyNotesSpoilerOffToast)
            ).show();
        });
        inputRow.addView(spoilerToggleBtn, LayoutHelper.createLinear(40, 44, Gravity.BOTTOM));

        noteEditText = new EditTextBoldCursor(context);
        noteEditText.setBackground(null);
        noteEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        noteEditText.setTextColor(Theme.getColor(Theme.key_chat_messagePanelText, resourceProvider));
        noteEditText.setHintTextColor(Theme.getColor(Theme.key_chat_messagePanelHint, resourceProvider));
        noteEditText.setCursorColor(Theme.getColor(Theme.key_chat_messagePanelCursor, resourceProvider));
        noteEditText.setCursorSize(dp(20));
        noteEditText.setCursorWidth(1.5f);
        noteEditText.setHint(getString(R.string.MirrlyNotesInputHint));
        noteEditText.setMinLines(1);
        noteEditText.setMaxLines(5);
        noteEditText.setGravity(Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
        noteEditText.setPadding(dp(6), dp(10), dp(6), dp(10));
        noteEditText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        noteEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateComposerVisualState(true);
                syncInputBubbleHeight();
            }
        });
        inputRow.addView(noteEditText, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

        sendBtnContainer = new FrameLayout(context);
        sendBtnContainer.setBackground(Theme.createSimpleSelectorCircleDrawable(dp(36), Theme.getColor(Theme.key_chat_messagePanelSend, resourceProvider), Theme.multAlpha(Theme.getColor(Theme.key_chat_messagePanelSend, resourceProvider), 0.85f)));
        sendBtnIcon = new ImageView(context);
        sendBtnIcon.setScaleType(ImageView.ScaleType.CENTER);
        sendBtnIcon.setImageResource(R.drawable.ic_send);
        sendBtnIcon.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));
        sendBtnContainer.addView(sendBtnIcon, LayoutHelper.createFrame(36, 36, Gravity.CENTER));
        sendBtnContainer.setOnClickListener(v -> submitComposerNote());
        inputRow.addView(sendBtnContainer, LayoutHelper.createLinear(36, 36, Gravity.BOTTOM, 4, 4, 4, 4));

        inputPanelColumn.addView(inputRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        inputPanelColumn.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> syncInputBubbleHeight());

        chatInputBubbleContainer.addView(inputPanelColumn, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 6, 0, 6, 0));
        contentView.addView(chatInputViewsContainer.getFadeView(), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        contentView.addView(chatInputViewsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        updateSpoilerToggleIcon();
    }

    private void syncInputBubbleHeight() {
        if (inputPanelColumn == null || chatInputViewsContainer == null) {
            return;
        }
        int h = Math.max(dp(44), inputPanelColumn.getMeasuredHeight());
        chatInputViewsContainer.setInputBubbleHeight(h);
        if (chatActivityFadeView != null) {
            chatActivityFadeView.setFadeZoneBottom((int) (windowInsetsStateHolder.getAnimatedMaxBottomInset() + h + dp(14)));
        }
        updateListPaddingAndButtons();
    }

    private void updateListPaddingAndButtons() {
        if (chatListView == null) {
            return;
        }
        float bubbleH = chatInputViewsContainer != null ? chatInputViewsContainer.getInputBubbleHeight() : dp(48);
        int bottomPad = (int) (recommendedAdditionalSizeY + windowInsetsStateHolder.getAnimatedMaxBottomInset() + bubbleH + dp(18));
        int topPad = recommendedAdditionalSizeY + AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() + dp(filterBanner != null && filterBanner.getVisibility() == View.VISIBLE ? 42 : 8);
        if (chatListView.getPaddingTop() != topPad || chatListView.getPaddingBottom() != bottomPad) {
            chatListView.setPadding(0, topPad, 0, bottomPad);
        }
        if (scrollDownButton != null) {
            scrollDownButton.setTranslationY(-(windowInsetsStateHolder.getAnimatedMaxBottomInset() + bubbleH + dp(20)));
        }
        if (filterBanner != null) {
            filterBanner.setTranslationY(AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() + dp(8));
        }
    }

    private void updateSpoilerToggleIcon() {
        if (spoilerToggleBtn == null) {
            return;
        }
        int color = composerSpoiler
                ? Theme.getColor(Theme.key_chat_messagePanelSend, resourceProvider)
                : Theme.getColor(Theme.key_chat_messagePanelIcons, resourceProvider);
        spoilerToggleBtn.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
    }

    private void updateComposerVisualState(boolean animated) {
        if (sendBtnContainer == null) {
            return;
        }
        boolean hasText = noteEditText != null && !TextUtils.isEmpty(noteEditText.getText().toString().trim());
        boolean shouldEnable = hasText || editingNote != null;
        if (composerButtonActive == shouldEnable && animated) {
            return;
        }
        composerButtonActive = shouldEnable;
        float targetScale = shouldEnable ? 1f : 0.72f;
        float targetAlpha = shouldEnable ? 1f : 0.45f;
        sendBtnContainer.setEnabled(shouldEnable);
        if (animated) {
            sendBtnContainer.animate()
                    .scaleX(targetScale)
                    .scaleY(targetScale)
                    .alpha(targetAlpha)
                    .setDuration(180)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                    .start();
        } else {
            sendBtnContainer.setScaleX(targetScale);
            sendBtnContainer.setScaleY(targetScale);
            sendBtnContainer.setAlpha(targetAlpha);
        }
    }

    private void submitComposerNote() {
        if (noteEditText == null) {
            return;
        }
        String text = noteEditText.getText() != null ? noteEditText.getText().toString().trim() : "";
        if (editingNote != null) {
            MirrlyVaultStorage.DossierNote target = editingNote;
            if (TextUtils.isEmpty(text)) {
                deleteNoteWithEffect(null, target);
                cancelEditMode();
                return;
            }
            MirrlyVaultStorage.updateNoteById(target.id, text, composerSpoiler);
            cancelEditMode();
            reloadNotes(true);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.ic_save_to_gallery, getString(R.string.MirrlyNotesUpdatedToast)).show();
            return;
        }
        if (TextUtils.isEmpty(text)) {
            return;
        }
        MirrlyVaultStorage.createStandaloneNote(text, composerSpoiler);
        noteEditText.setText("");
        if (chatListView != null && !items.isEmpty()) {
            chatListView.smoothScrollToPosition(0);
        }
    }

    private void startEditMode(@NonNull MirrlyVaultStorage.DossierNote note) {
        editingNote = note;
        composerSpoiler = note.spoiler;
        updateSpoilerToggleIcon();
        if (editBannerRow != null) {
            editBannerPreview.setText(note.text.replace('\n', ' '));
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
        if (noteEditText != null) {
            noteEditText.setText(note.text);
            noteEditText.setSelection(note.text.length());
            noteEditText.requestFocus();
            AndroidUtilities.showKeyboard(noteEditText);
        }
        updateComposerVisualState(true);
        syncInputBubbleHeight();
    }

    private void cancelEditMode() {
        editingNote = null;
        composerSpoiler = false;
        updateSpoilerToggleIcon();
        if (editBannerRow != null && editBannerRow.getVisibility() == View.VISIBLE) {
            editBannerRow.setVisibility(View.GONE);
        }
        if (noteEditText != null) {
            noteEditText.setText("");
        }
        updateComposerVisualState(true);
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
        if (notesAdapter != null) {
            notesAdapter.notifyDataSetChanged();
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
        msg.flags = TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
        if (note.isEdited()) {
            msg.flags |= TLRPC.MESSAGE_FLAG_EDITED;
            msg.edit_date = note.updatedAt;
        }
        msg.from_id = new TLRPC.TL_peerUser();
        msg.from_id.user_id = selfId;
        msg.peer_id = new TLRPC.TL_peerUser();
        msg.peer_id.user_id = selfId;
        msg.media = new TLRPC.TL_messageMediaEmpty();
        msg.message = note.text;

        if (note.spoiler && !TextUtils.isEmpty(note.text)) {
            TLRPC.TL_messageEntitySpoiler spoilerEntity = new TLRPC.TL_messageEntitySpoiler();
            spoilerEntity.offset = 0;
            spoilerEntity.length = note.text.length();
            msg.entities.add(spoilerEntity);
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
    }

    private void updateFilterBanner(boolean animated) {
        if (filterBanner == null || filterBannerText == null) {
            return;
        }
        if (activeFilter == MirrlyVaultStorage.FILTER_ALL) {
            if (filterBanner.getVisibility() == View.VISIBLE) {
                if (animated) {
                    filterBanner.animate()
                            .alpha(0f)
                            .translationY(AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight())
                            .setDuration(180)
                            .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                            .withEndAction(() -> {
                                filterBanner.setVisibility(View.GONE);
                                updateListPaddingAndButtons();
                            })
                            .start();
                } else {
                    filterBanner.setVisibility(View.GONE);
                    updateListPaddingAndButtons();
                }
            }
            return;
        }
        String label;
        switch (activeFilter) {
            case MirrlyVaultStorage.FILTER_MESSAGES:
                label = getString(R.string.MirrlyNotesFilterBannerMessages);
                break;
            case MirrlyVaultStorage.FILTER_STANDALONE:
                label = getString(R.string.MirrlyNotesFilterBannerStandalone);
                break;
            case MirrlyVaultStorage.FILTER_SPOILER:
                label = getString(R.string.MirrlyNotesFilterBannerSpoiler);
                break;
            default:
                label = "";
                break;
        }
        filterBannerText.setText(label);
        if (filterBanner.getVisibility() != View.VISIBLE) {
            filterBanner.setVisibility(View.VISIBLE);
            filterBanner.setAlpha(0f);
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

    private void showFilterMenu() {
        if (filterItem == null || getParentActivity() == null) {
            return;
        }
        ItemOptions options = ItemOptions.makeOptions(this, filterItem)
                .setBlur(true)
                .setBlurBackground(glassBackgroundDrawableFactory, BlurredBackgroundProviderImpl.messageMenuBackground(resourceProvider), false)
                .setGravity(Gravity.RIGHT);

        options.addChecked(activeFilter == MirrlyVaultStorage.FILTER_ALL, getString(R.string.MirrlyNotesFilterAll), () -> applyFilter(MirrlyVaultStorage.FILTER_ALL));
        options.addChecked(activeFilter == MirrlyVaultStorage.FILTER_MESSAGES, getString(R.string.MirrlyNotesFilterMessages), () -> applyFilter(MirrlyVaultStorage.FILTER_MESSAGES));
        options.addChecked(activeFilter == MirrlyVaultStorage.FILTER_STANDALONE, getString(R.string.MirrlyNotesFilterStandalone), () -> applyFilter(MirrlyVaultStorage.FILTER_STANDALONE));
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
                .setBlur(true)
                .setBlurBackground(glassBackgroundDrawableFactory, BlurredBackgroundProviderImpl.messageMenuBackground(resourceProvider), false)
                .setGravity(Gravity.RIGHT);

        options.addIf(note.dialogId != 0, R.drawable.msg_viewintopic, getString(note.isChatNote() ? R.string.MirrlyNotesOpenChat : R.string.MirrlyNotesGoToMessage), () -> openNoteTarget(note));
        options.add(R.drawable.msg_edit, getString(R.string.MirrlyNotesEdit), () -> startEditMode(note));
        options.add(R.drawable.msg_copy, getString(R.string.Copy), () -> {
            if (AndroidUtilities.addToClipboard(note.text)) {
                BulletinFactory.of(this).createCopyBulletin(getString(R.string.TextCopied)).show();
            }
        });
        options.add(
                note.spoiler ? R.drawable.msg_spoiler_off : R.drawable.msg_spoiler,
                getString(note.spoiler ? R.string.MirrlyNotesRemoveSpoiler : R.string.MirrlyNotesHideSpoiler),
                () -> {
                    MirrlyVaultStorage.toggleNoteSpoiler(note.id);
                    reloadNotes(true);
                }
        );
        options.addGap();
        options.add(R.drawable.msg_delete, getString(R.string.Delete), true, () -> deleteNoteWithEffect(cell, note));
        options.show();
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
        boolean shouldShow = firstVisible > 1;
        if (shouldShow == scrollDownVisible) {
            return;
        }
        scrollDownVisible = shouldShow;
        if (shouldShow) {
            scrollDownButton.setVisibility(View.VISIBLE);
            scrollDownButton.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                    .withEndAction(null)
                    .start();
        } else {
            scrollDownButton.animate()
                    .alpha(0f)
                    .scaleX(0.6f)
                    .scaleY(0.6f)
                    .setDuration(180)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                    .withEndAction(() -> {
                        if (!scrollDownVisible) {
                            scrollDownButton.setVisibility(View.GONE);
                        }
                    })
                    .start();
        }
    }

    private void showFloatingDateView() {
        if (floatingDateView == null || floatingDateView.getTag() != null) {
            return;
        }
        if (floatingDateAnimation != null) {
            floatingDateAnimation.cancel();
        }
        floatingDateView.setTag(1);
        floatingDateAnimation = new AnimatorSet();
        floatingDateAnimation.setDuration(150);
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
            floatingDateAnimation.setDuration(180);
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
                floatingDateView.setTranslationY(-floatingDateView.getMeasuredHeight() * 2 + offset);
            } else {
                floatingDateView.setTranslationY(0);
            }
        } else {
            hideFloatingDateView(true);
            floatingDateView.setTranslationY(0);
        }
    }

    private void onInsetsUpdated() {
        if (chatInputViewsContainer != null) {
            chatInputViewsContainer.checkInsets();
        }
        syncInputBubbleHeight();
    }

    @NonNull
    private WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
        final Insets systemInsets = AndroidUtilities.getDefaultWindowInsets(insets, false);
        windowInsetsStateHolder.setInsets(insets);
        if (chatListView != null) {
            chatListView.setPadding(systemInsets.left, chatListView.getPaddingTop(), systemInsets.right, chatListView.getPaddingBottom());
        }
        updateListPaddingAndButtons();
        return WindowInsetsCompat.CONSUMED;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetNewWallpapper) {
            if (contentView != null) {
                contentView.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());
            }
            if (chatListView != null) {
                chatListView.invalidateViews();
            }
        } else if (id == NotificationCenter.emojiLoaded) {
            if (chatListView != null) {
                chatListView.invalidateViews();
            }
        } else if (id == NotificationCenter.mirrlyNotesDidUpdate) {
            reloadNotes(true);
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

        NotesChatFragmentView(Context context) {
            super(context);
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
                ChatActionCell cell = new ChatActionCell(mContext, false, resourceProvider);
                view = cell;
            } else {
                ChatMessageCell cell = new ChatMessageCell(mContext, currentAccount, false, null, resourceProvider);
                cell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
                    @Override
                    public boolean canPerformActions() {
                        return true;
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
                // reverseLayout = true: position - 1 is below (newer), position + 1 is above (older).
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
                cell.setHighlighted(false);
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
}
