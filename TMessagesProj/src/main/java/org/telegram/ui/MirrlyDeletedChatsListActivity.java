package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.mirrly.MirrlyConfig;
import org.telegram.messenger.mirrly.MirrlyDeletedChatsStorage;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.util.ArrayList;
import java.util.List;

/**
 * Native Telegram main-list-styled viewer for archived deleted chats.
 * Uses stock {@link DialogCell} rows backed by serialized peer metadata and last messages.
 */
public class MirrlyDeletedChatsListActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int MENU_SEARCH = 1;
    private static final int MENU_MORE = 2;
    private static final int SUB_CLEAR_ALL = 10;
    private static final int SUB_VAULT_SETTINGS = 11;

    private static final int[] FILTER_TYPES = new int[]{
            MirrlyDeletedChatsStorage.FILTER_ALL,
            MirrlyDeletedChatsStorage.FILTER_PRIVATE,
            MirrlyDeletedChatsStorage.FILTER_GROUPS_CHANNELS,
            MirrlyDeletedChatsStorage.FILTER_SECRET
    };

    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private DeletedChatsAdapter adapter;

    private CategoryTabsStrip tabsStrip;
    private View tabsDivider;

    private FrameLayout emptyContainer;
    private RLottieImageView emptyLottieView;
    private TextView emptyTitleView;
    private TextView emptySubtitleView;
    private TextView emptyEnableButton;

    private final ArrayList<MirrlyDeletedChatsStorage.DeletedChatSummary> chats = new ArrayList<>();
    private int activeFilter = MirrlyDeletedChatsStorage.FILTER_ALL;
    private String searchQuery = "";
    private boolean searching = false;

    @Override
    public boolean onFragmentCreate() {
        MirrlyDeletedChatsStorage.getInstance(currentAccount).hydratePeersIntoMemory();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.mirrlyDeletedChatsDidUpdate);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didApplyNewTheme);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.mirrlyDeletedChatsDidUpdate);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didApplyNewTheme);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        super.onFragmentDestroy();
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.MirrlyDeletedChatsTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == SUB_CLEAR_ALL) {
                    confirmClearAllChats();
                } else if (id == SUB_VAULT_SETTINGS) {
                    presentFragment(new MirrlyVaultSettingsActivity());
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem searchItem = menu.addItem(MENU_SEARCH, R.drawable.outline_header_search)
                .setIsSearchField(true)
                .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                    @Override
                    public void onSearchExpand() {
                        searching = true;
                        reloadChats();
                    }

                    @Override
                    public void onSearchCollapse() {
                        searching = false;
                        searchQuery = "";
                        reloadChats();
                    }

                    @Override
                    public void onTextChanged(EditText editText) {
                        String next = editText.getText() != null ? editText.getText().toString() : "";
                        if (!TextUtils.equals(searchQuery, next)) {
                            searchQuery = next;
                            reloadChats();
                        }
                    }

                    @Override
                    public void onSearchPressed(EditText editText) {
                        String next = editText.getText() != null ? editText.getText().toString() : "";
                        searchQuery = next;
                        reloadChats();
                        AndroidUtilities.hideKeyboard(editText);
                    }
                });
        searchItem.setSearchFieldHint(getString(R.string.MirrlyDeletedChatsSearchHint));

        ActionBarMenuItem moreItem = menu.addItem(MENU_MORE, R.drawable.ic_ab_other);
        moreItem.addSubItem(SUB_VAULT_SETTINGS, R.drawable.msg_settings, getString(R.string.MirrlyDeletedMenuSettings));
        moreItem.addSubItem(SUB_CLEAR_ALL, R.drawable.msg_delete, getString(R.string.MirrlyVaultClearDeletedChats));

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
        ThemeBind.backgroundColor(root, Theme.key_windowBackgroundWhite, resourceProvider);

        tabsStrip = new CategoryTabsStrip(context);
        root.addView(tabsStrip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.TOP));

        tabsDivider = new View(context);
        tabsDivider.setBackgroundColor(Theme.getColor(Theme.key_divider, resourceProvider));
        ThemeBind.backgroundColor(tabsDivider, Theme.key_divider, resourceProvider);
        root.addView(tabsDivider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.TOP, 0, 44, 0, 0));

        listView = new RecyclerListView(context);
        listView.setVerticalScrollBarEnabled(true);
        listView.setClipToPadding(false);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDurations(280);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        listView.setItemAnimator(itemAnimator);
        listView.setAdapter(adapter = new DeletedChatsAdapter(context));
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= chats.size()) {
                return;
            }
            MirrlyDeletedChatsStorage.DeletedChatSummary summary = chats.get(position);
            presentFragment(new MirrlyDeletedChatActivity(summary.dialogId, summary));
        });
        listView.setOnItemLongClickListener((view, position) -> {
            if (position < 0 || position >= chats.size()) {
                return false;
            }
            MirrlyDeletedChatsStorage.DeletedChatSummary summary = chats.get(position);
            AndroidUtilities.vibrate(view);
            showChatOptions(view, summary);
            return true;
        });
        listView.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottom = insets.getSystemWindowInsetBottom();
            v.setPadding(0, 0, 0, bottom);
            return insets;
        });
        root.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 45, 0, 0));

        buildEmptyState(context, root);

        reloadChats();
        fragmentView = root;
        return fragmentView;
    }

    private void buildEmptyState(Context context, FrameLayout root) {
        emptyContainer = new FrameLayout(context);
        emptyContainer.setVisibility(View.INVISIBLE);
        root.addView(emptyContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER, 28, 45, 28, 0));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        emptyContainer.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        emptyLottieView = new RLottieImageView(context);
        emptyLottieView.setAutoRepeat(true);
        emptyLottieView.setAnimation(R.raw.utyan_empty, 110, 110);
        emptyLottieView.playAnimation();
        column.addView(emptyLottieView, LayoutHelper.createLinear(110, 110, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 8));

        emptyTitleView = new TextView(context);
        emptyTitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        emptyTitleView.setTypeface(AndroidUtilities.bold());
        emptyTitleView.setGravity(Gravity.CENTER);
        ThemeBind.textColor(emptyTitleView, Theme.key_windowBackgroundWhiteBlackText, resourceProvider);
        column.addView(emptyTitleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 6));

        emptySubtitleView = new TextView(context);
        emptySubtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptySubtitleView.setGravity(Gravity.CENTER);
        ThemeBind.textColor(emptySubtitleView, Theme.key_windowBackgroundWhiteGrayText, resourceProvider);
        column.addView(emptySubtitleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));

        emptyEnableButton = new TextView(context);
        emptyEnableButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptyEnableButton.setTypeface(AndroidUtilities.bold());
        emptyEnableButton.setGravity(Gravity.CENTER);
        emptyEnableButton.setPadding(dp(20), dp(11), dp(20), dp(11));
        emptyEnableButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourceProvider));
        emptyEnableButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                dp(14),
                Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourceProvider)
        ));
        emptyEnableButton.setText(getString(R.string.MirrlyDeletedChatsEnableBtn));
        ScaleStateListAnimator.apply(emptyEnableButton, 0.03f, 1.5f);
        emptyEnableButton.setOnClickListener(v -> {
            MirrlyConfig.setSaveDeletedChats(true);
            reloadChats();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.MirrlyDeletedChatsEnabledToast)).show();
        });
        column.addView(emptyEnableButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 14, 0, 0));
    }

    private void showChatOptions(View anchor, @NonNull MirrlyDeletedChatsStorage.DeletedChatSummary summary) {
        ItemOptions options = ItemOptions.makeOptions(this, anchor);
        options.add(R.drawable.msg_message, getString(R.string.MirrlyDeletedChatsOpenChat), () -> presentFragment(new MirrlyDeletedChatActivity(summary.dialogId, summary)));
        options.addGap();
        options.add(R.drawable.msg_delete, getString(R.string.MirrlyDeletedChatsRemoveSingle), true, () -> {
            MirrlyUiKit.confirmDangerAction(
                    this,
                    resourceProvider,
                    getString(R.string.MirrlyDeletedChatsRemoveSingle),
                    LocaleController.formatString(R.string.MirrlyDeletedChatsRemoveSingleConfirm, summary.title),
                    getString(R.string.Delete),
                    () -> {
                        MirrlyDeletedChatsStorage.getInstance(currentAccount).deleteChat(summary.dialogId);
                        reloadChats();
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.ic_delete, getString(R.string.MirrlyDeletedChatsRemovedSingleToast)).show();
                    }
            );
        });
        options.show();
    }

    private void confirmClearAllChats() {
        int count = MirrlyDeletedChatsStorage.getInstance(currentAccount).getChatCount();
        if (count == 0) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.MirrlyVaultClearDeletedChatsEmpty)).show();
            return;
        }
        MirrlyUiKit.confirmDangerAction(
                this,
                resourceProvider,
                getString(R.string.MirrlyVaultClearDeletedChatsConfirmTitle),
                LocaleController.formatString(R.string.MirrlyVaultClearDeletedChatsConfirmText, count),
                getString(R.string.Delete),
                () -> {
                    MirrlyDeletedChatsStorage.getInstance(currentAccount).clearAll();
                    reloadChats();
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.ic_delete, getString(R.string.MirrlyVaultClearDeletedChatsDone)).show();
                }
        );
    }

    private void reloadChats() {
        MirrlyDeletedChatsStorage storage = MirrlyDeletedChatsStorage.getInstance(currentAccount);
        storage.hydratePeersIntoMemory();
        List<MirrlyDeletedChatsStorage.DeletedChatSummary> loaded = storage.searchChats(searchQuery, activeFilter);
        chats.clear();
        chats.addAll(loaded);

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateActionBarSubtitle();
        updateEmptyState();
    }

    private void updateActionBarSubtitle() {
        if (actionBar == null) {
            return;
        }
        int total = MirrlyDeletedChatsStorage.getInstance(currentAccount).getChatCount();
        if (total > 0) {
            actionBar.setSubtitle(LocaleController.formatPluralString("Chats", total));
        } else {
            actionBar.setSubtitle(getString(MirrlyConfig.isSaveDeletedChats() ? R.string.MirrlyVaultCountEmpty : R.string.MirrlyDeletedChatsStatusOff));
        }
    }

    private void updateEmptyState() {
        if (emptyContainer == null) {
            return;
        }
        boolean showEnableCta = false;
        if (!TextUtils.isEmpty(searchQuery)) {
            emptyTitleView.setText(getString(R.string.MirrlyNotesEmptySearchTitle));
            emptySubtitleView.setText(LocaleController.formatString(R.string.MirrlyNotesEmptySearchSub, searchQuery.trim()));
        } else if (activeFilter != MirrlyDeletedChatsStorage.FILTER_ALL) {
            emptyTitleView.setText(getString(R.string.MirrlyNotesEmptyFilterTitle));
            emptySubtitleView.setText(getString(R.string.MirrlyDeletedChatsEmptyFilterSub));
        } else if (!MirrlyConfig.isSaveDeletedChats()) {
            emptyTitleView.setText(getString(R.string.MirrlyDeletedChatsEmptyDisabledTitle));
            emptySubtitleView.setText(getString(R.string.MirrlyDeletedChatsEmptyDisabledSub));
            showEnableCta = true;
        } else {
            emptyTitleView.setText(getString(R.string.MirrlyDeletedChatsTitle));
            emptySubtitleView.setText(getString(R.string.MirrlyDeletedChatsEmptyDefaultSub));
        }
        if (emptyEnableButton != null) {
            emptyEnableButton.setVisibility(showEnableCta ? View.VISIBLE : View.GONE);
        }
        boolean empty = chats.isEmpty();
        emptyContainer.setVisibility(empty ? View.VISIBLE : View.INVISIBLE);
        if (listView != null) {
            listView.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.mirrlyDeletedChatsDidUpdate) {
            reloadChats();
        } else if (id == NotificationCenter.didApplyNewTheme || id == NotificationCenter.emojiLoaded) {
            if (listView != null) {
                for (int i = 0; i < listView.getChildCount(); i++) {
                    View child = listView.getChildAt(i);
                    if (child instanceof DialogCell) {
                        ((DialogCell) child).update(0);
                    }
                }
            }
            if (tabsStrip != null) {
                tabsStrip.updateColors();
            }
        }
    }

    @NonNull
    private MessageObject buildLastMessageObject(@NonNull MirrlyDeletedChatsStorage.DeletedChatSummary summary) {
        TLRPC.Message msg = MirrlyDeletedChatsStorage.deserializeMessage(summary.lastMsgTlBlob);
        if (msg == null) {
            TLRPC.TL_message fallback = new TLRPC.TL_message();
            fallback.id = summary.lastMsgId != 0 ? summary.lastMsgId : 1;
            fallback.date = summary.getSortTimestamp();
            fallback.out = summary.lastMsgOut;
            fallback.media = new TLRPC.TL_messageMediaEmpty();
            if (!TextUtils.isEmpty(summary.lastMsgText)) {
                fallback.message = summary.lastMsgText;
            } else if (summary.msgCount > 0) {
                fallback.message = LocaleController.formatPluralString("messages", summary.msgCount);
            } else {
                fallback.message = getString(R.string.MirrlyDeletedChatNoCachedMessages);
            }
            msg = fallback;
        }
        msg.dialog_id = summary.dialogId;
        MessageObject obj = new MessageObject(currentAccount, msg, false, false);
        obj.suppressMirrlyNoteBadge = true;
        obj.suppressMirrlyDeletedVisuals = true;
        return obj;
    }

    private static String getFilterLabel(int filter) {
        switch (filter) {
            case MirrlyDeletedChatsStorage.FILTER_PRIVATE:
                return getString(R.string.MirrlyDeletedChatsFilterPrivate);
            case MirrlyDeletedChatsStorage.FILTER_GROUPS_CHANNELS:
                return getString(R.string.MirrlyDeletedChatsFilterGroups);
            case MirrlyDeletedChatsStorage.FILTER_SECRET:
                return getString(R.string.MirrlyDeletedChatsFilterSecret);
            default:
                return getString(R.string.MirrlyDeletedFilterAll);
        }
    }

    private final class DeletedChatsAdapter extends RecyclerListView.SelectionAdapter {
        private final Context context;

        DeletedChatsAdapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemCount() {
            return chats.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            DialogCell cell = new DialogCell(null, context, false, false, currentAccount, resourceProvider);
            cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position < 0 || position >= chats.size()) {
                return;
            }
            DialogCell cell = (DialogCell) holder.itemView;
            MirrlyDeletedChatsStorage.DeletedChatSummary summary = chats.get(position);
            cell.useSeparator = position < chats.size() - 1;
            MessageObject lastMsg = buildLastMessageObject(summary);
            cell.setDialog(summary.dialogId, lastMsg, summary.getSortTimestamp(), false, false);
        }
    }

    private final class CategoryTabsStrip extends HorizontalScrollView {
        private final LinearLayout row;
        private final ArrayList<TextView> tabViews = new ArrayList<>();
        private final Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float indicatorLeft;
        private float indicatorRight;
        private ValueAnimator indicatorAnimator;

        CategoryTabsStrip(Context context) {
            super(context);
            setHorizontalScrollBarEnabled(false);
            setFillViewport(true);
            setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault, resourceProvider));

            row = new LinearLayout(context) {
                @Override
                protected void dispatchDraw(Canvas canvas) {
                    super.dispatchDraw(canvas);
                    if (indicatorRight > indicatorLeft) {
                        indicatorPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourceProvider));
                        float top = getHeight() - dp(3);
                        canvas.drawRoundRect(indicatorLeft, top, indicatorRight, getHeight() + dp(3), dp(2), dp(2), indicatorPaint);
                    }
                }
            };
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(8), 0, dp(8), 0);
            addView(row, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

            for (int i = 0; i < FILTER_TYPES.length; i++) {
                final int filter = FILTER_TYPES[i];
                final int index = i;
                TextView tv = new TextView(context);
                tv.setText(getFilterLabel(filter));
                tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                tv.setTypeface(AndroidUtilities.bold());
                tv.setSingleLine(true);
                tv.setGravity(Gravity.CENTER);
                tv.setPadding(dp(14), 0, dp(14), 0);
                tv.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourceProvider), 2));
                tv.setOnClickListener(v -> {
                    if (activeFilter != filter) {
                        activeFilter = filter;
                        updateTabSelection(true, index);
                        reloadChats();
                    }
                });
                tabViews.add(tv);
                row.addView(tv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));
            }
            updateColors();
            post(() -> updateTabSelection(false, 0));
        }

        void updateColors() {
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
            updateTabSelection(false, findActiveTabIndex());
        }

        private int findActiveTabIndex() {
            for (int i = 0; i < FILTER_TYPES.length; i++) {
                if (FILTER_TYPES[i] == activeFilter) {
                    return i;
                }
            }
            return 0;
        }

        private void updateTabSelection(boolean animated, int activeIdx) {
            int activeColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourceProvider);
            int normalColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider);
            for (int i = 0; i < tabViews.size(); i++) {
                tabViews.get(i).setTextColor(i == activeIdx ? activeColor : normalColor);
            }
            if (activeIdx < 0 || activeIdx >= tabViews.size()) {
                return;
            }
            TextView target = tabViews.get(activeIdx);
            Runnable moveIndicator = () -> {
                float targetL = target.getLeft() + dp(10);
                float targetR = target.getRight() - dp(10);
                if (targetR <= targetL) {
                    return;
                }
                if (indicatorAnimator != null) {
                    indicatorAnimator.cancel();
                }
                if (!animated || indicatorRight <= indicatorLeft) {
                    indicatorLeft = targetL;
                    indicatorRight = targetR;
                    row.invalidate();
                    return;
                }
                final float startL = indicatorLeft;
                final float startR = indicatorRight;
                indicatorAnimator = ValueAnimator.ofFloat(0f, 1f);
                indicatorAnimator.setDuration(260);
                indicatorAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                indicatorAnimator.addUpdateListener(anim -> {
                    float f = (float) anim.getAnimatedValue();
                    indicatorLeft = AndroidUtilities.lerp(startL, targetL, f);
                    indicatorRight = AndroidUtilities.lerp(startR, targetR, f);
                    row.invalidate();
                });
                indicatorAnimator.start();
            };
            if (target.getWidth() > 0) {
                moveIndicator.run();
            } else {
                target.post(moveIndicator);
            }
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> list = new ArrayList<>();
        list.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        list.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        list.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        list.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        list.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBTITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultSubtitle));
        list.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        list.add(new ThemeDescription(tabsDivider, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_divider));
        list.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        list.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, null, Theme.dialogs_namePaint, null, null, Theme.key_chats_name));
        list.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, null, Theme.dialogs_messagePaint, null, null, Theme.key_chats_message));
        list.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, null, null, null, Theme.key_chats_secretName));
        list.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, null, new android.graphics.Paint[]{Theme.dividerPaint}, null, null, Theme.key_divider));
        return list;
    }
}
