package org.telegram.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

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
import org.telegram.messenger.mirrly.MirrlyConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BackgroundGradientDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.utils.glass.GlassContrastHelper;
import org.telegram.utils.glass.source.GlassSourceColor;

import java.util.ArrayList;

/**
 * Chats and media behavior sub-screen with icon-aligned switch controls.
 */
public class MirrlyChatsSettingsActivity extends MirrlyBaseSettingsFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int ID_HERO = 1;
    private static final int ID_CONFIRM_MEDIA_SEND = 10;
    private static final int ID_ROUND_VIDEO_BACK_CAMERA = 11;
    private static final int ID_IN_CHAT_SEND_SOUND = 12;
    private static final int ID_IN_CHAT_RECEIVE_SOUND = 13;
    private static final int ID_SHOW_OWN_CHAT_TAG = 14;
    private static final int ID_OWN_CHAT_TAG_STYLE = 15;
    private static final int ID_OWN_TAG_PREVIEW = 16;
    private static final int ID_OPEN_TRANSLATOR_SETTINGS = 17;
    private static final int ID_DISABLE_ALL_VIBRATIONS = 18;

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.mirrlyChatTagSettingsChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.mirrlyTranslatorSettingsChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.mirrlyVibrationSettingsChanged);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.mirrlyChatTagSettingsChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.mirrlyTranslatorSettingsChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.mirrlyVibrationSettingsChanged);
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

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyChatsSoundsHeader)));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_IN_CHAT_SEND_SOUND,
                R.drawable.msg_send,
                LocaleController.getString(R.string.MirrlyChatsSendSound),
                MirrlyConfig.isInChatSendSoundEnabled()
        ));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_IN_CHAT_RECEIVE_SOUND,
                R.drawable.msg_notifications,
                LocaleController.getString(R.string.MirrlyChatsReceiveSound),
                MirrlyConfig.isInChatReceiveSoundEnabled()
        ));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MirrlyChatsSoundsHint)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyVibrationHeader)));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_DISABLE_ALL_VIBRATIONS,
                R.drawable.msg_silent,
                LocaleController.getString(R.string.MirrlyVibrationDisableAll),
                MirrlyConfig.isDisableAllVibrations()
        ));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MirrlyVibrationDisableAllHint)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyChatsTagsHeader)));
        items.add(OwnTagPreviewCell.Factory.of(ID_OWN_TAG_PREVIEW));
        boolean showOwnTag = MirrlyConfig.isShowOwnChatTagEnabled();
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_SHOW_OWN_CHAT_TAG,
                R.drawable.filled_crown_on,
                LocaleController.getString(R.string.MirrlyChatsShowOwnTag),
                showOwnTag
        ));
        if (showOwnTag) {
            items.add(MirrlyUiKit.SegmentedCardCell.Factory.of(
                    ID_OWN_CHAT_TAG_STYLE,
                    new MirrlyUiKit.SegmentedSpec(
                            R.drawable.msg_blur_radial,
                            LocaleController.getString(R.string.MirrlyChatsOwnTagStylePill),
                            R.drawable.msg_customize,
                            LocaleController.getString(R.string.MirrlyChatsOwnTagStyleInside),
                            MirrlyConfig.getOwnChatTagStyle() == MirrlyConfig.OWN_CHAT_TAG_INSIDE_BUBBLE ? 1 : 0,
                            index -> MirrlyConfig.setOwnChatTagStyle(
                                    index == 1 ? MirrlyConfig.OWN_CHAT_TAG_INSIDE_BUBBLE : MirrlyConfig.OWN_CHAT_TAG_PILL_ABOVE
                            )
                    )
            ));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.MirrlyChatsTagsHint)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyTranslatorSettingsTitle)));
        items.add(UItem.asButton(
                ID_OPEN_TRANSLATOR_SETTINGS,
                R.drawable.msg_translate,
                LocaleController.getString(R.string.MirrlyTranslatorSettingsTitle),
                org.telegram.messenger.mirrly.MirrlyTranslatorEngine.getProviderShortName(MirrlyConfig.getTranslatorProvider())
        ));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MirrlyChatsTranslatorShortcutHint)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item == null) {
            return;
        }
        if (item.id == ID_CONFIRM_MEDIA_SEND) {
            boolean next = !MirrlyConfig.confirmMediaSend;
            MirrlyConfig.setConfirmMediaSend(next);
            if (view instanceof TextCell) {
                ((TextCell) view).setChecked(next);
            }
            if (listView != null && listView.adapter != null) {
                listView.adapter.updateWithoutNotify();
            }
        } else if (item.id == ID_ROUND_VIDEO_BACK_CAMERA) {
            boolean next = !MirrlyConfig.isRoundVideoBackCamera();
            MirrlyConfig.setRoundVideoBackCamera(next);
            if (view instanceof TextCell) {
                ((TextCell) view).setChecked(next);
            }
            if (listView != null && listView.adapter != null) {
                listView.adapter.updateWithoutNotify();
            }
        } else if (item.id == ID_IN_CHAT_SEND_SOUND) {
            boolean next = !MirrlyConfig.isInChatSendSoundEnabled();
            MirrlyConfig.setInChatSendSound(next);
            if (view instanceof TextCell) {
                ((TextCell) view).setChecked(next);
            }
            if (listView != null && listView.adapter != null) {
                listView.adapter.updateWithoutNotify();
            }
        } else if (item.id == ID_IN_CHAT_RECEIVE_SOUND) {
            boolean next = !MirrlyConfig.isInChatReceiveSoundEnabled();
            MirrlyConfig.setInChatReceiveSound(next);
            if (view instanceof TextCell) {
                ((TextCell) view).setChecked(next);
            }
            if (listView != null && listView.adapter != null) {
                listView.adapter.updateWithoutNotify();
            }
        } else if (item.id == ID_SHOW_OWN_CHAT_TAG) {
            boolean next = !MirrlyConfig.isShowOwnChatTagEnabled();
            MirrlyConfig.setShowOwnChatTag(next);
            if (view instanceof TextCell) {
                ((TextCell) view).setChecked(next);
            }
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        } else if (item.id == ID_OPEN_TRANSLATOR_SETTINGS) {
            presentFragment(new MirrlyTranslatorSettingsActivity());
        } else if (item.id == ID_DISABLE_ALL_VIBRATIONS) {
            boolean next = !MirrlyConfig.isDisableAllVibrations();
            MirrlyConfig.setDisableAllVibrations(next);
            if (view instanceof TextCell) {
                ((TextCell) view).setChecked(next);
            }
            if (listView != null && listView.adapter != null) {
                listView.adapter.updateWithoutNotify();
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (listView == null || listView.adapter == null) {
            return;
        }
        if (id == NotificationCenter.didSetNewTheme) {
            listView.adapter.updateWithoutNotify();
            for (int i = 0; i < listView.getChildCount(); i++) {
                View child = listView.getChildAt(i);
                if (child instanceof Theme.Colorable) {
                    ((Theme.Colorable) child).updateColors();
                }
            }
            listView.invalidate();
        } else if (id == NotificationCenter.mirrlyTranslatorSettingsChanged || id == NotificationCenter.mirrlyVibrationSettingsChanged) {
            listView.adapter.update(true);
        } else if (id == NotificationCenter.didSetNewWallpapper || id == NotificationCenter.mirrlyChatTagSettingsChanged) {
            for (int i = 0; i < listView.getChildCount(); i++) {
                View child = listView.getChildAt(i);
                if (child instanceof OwnTagPreviewCell) {
                    ((OwnTagPreviewCell) child).refreshPreview(id == NotificationCenter.didSetNewWallpapper, true);
                }
            }
        }
    }

    /**
     * Live interactive preview showing two consecutive own outgoing bubbles on the active chat
     * wallpaper with tap-to-cycle role example (Owner -> Admin -> Member).
     */
    private static class OwnTagPreviewCell extends FrameLayout implements Theme.Colorable {

        private static final int ROLE_OWNER = 0;
        private static final int ROLE_ADMIN = 1;
        private static final int ROLE_MEMBER = 2;
        private static final int ROLE_COUNT = 3;
        private static final long PREVIEW_CHAT_ID = 1000001L;

        private final int currentAccount;
        @Nullable
        private final Theme.ResourcesProvider resourcesProvider;

        private final FrameLayout previewContainer;
        private final LinearLayout messagesContainer;
        private final LinearLayout roleChip;
        private final View roleDotView;
        private final AnimatedTextView roleTextView;

        private final ChatMessageCell firstMessageCell;
        private final ChatMessageCell secondMessageCell;

        private final GlassSourceColor glassColorSource = new GlassSourceColor();
        private final BlurredBackgroundDrawableViewFactory glassFactory;
        private final BlurredBackgroundDrawable roleChipGlass;

        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF borderRect = new RectF();
        private final Rect savedDrawableBounds = new Rect();

        @Nullable
        private Drawable wallpaperDrawable;
        @Nullable
        private BackgroundGradientDrawable.Disposable bgDisposable;
        private boolean wallpaperLoadRequested;

        private int previewRole = ROLE_OWNER;
        private int backdropColor;
        private float backdropLuminance;

        private MessageObject firstMessageObject;
        private MessageObject secondMessageObject;

        public OwnTagPreviewCell(Context context, int currentAccount, @Nullable Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.currentAccount = currentAccount;
            this.resourcesProvider = resourcesProvider;
            this.glassFactory = new BlurredBackgroundDrawableViewFactory(glassColorSource);

            setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10));
            setClipToPadding(false);

            previewContainer = new FrameLayout(context) {
                @Override
                protected void dispatchDraw(Canvas canvas) {
                    drawChatWallpaper(canvas, getWidth(), getHeight());
                    super.dispatchDraw(canvas);
                    final float radius = AndroidUtilities.dp(14);
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
            previewContainer.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), AndroidUtilities.dp(14));
                }
            });
            previewContainer.setClickable(true);
            previewContainer.setFocusable(true);
            previewContainer.setOnClickListener(v -> cycleRole());
            addView(previewContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            messagesContainer = new LinearLayout(context) {
                @Override
                public boolean onInterceptTouchEvent(MotionEvent ev) {
                    return true;
                }
            };
            messagesContainer.setOrientation(LinearLayout.VERTICAL);
            messagesContainer.setClipChildren(false);
            messagesContainer.setClipToPadding(false);
            messagesContainer.setPadding(0, AndroidUtilities.dp(38), 0, AndroidUtilities.dp(8));
            messagesContainer.setClickable(true);
            messagesContainer.setOnClickListener(v -> cycleRole());
            previewContainer.addView(messagesContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            final ChatMessageCell.ChatMessageCellDelegate cellDelegate = new ChatMessageCell.ChatMessageCellDelegate() {
                @Override
                public boolean isOwner(long uid) {
                    return previewRole == ROLE_OWNER;
                }

                @Override
                public boolean isAdmin(long uid) {
                    return previewRole == ROLE_ADMIN;
                }

                @Override
                public String getSelfChatRank(long chatId) {
                    return resolveTagLabel();
                }

                @Override
                public float getChatBackdropLuminance() {
                    return backdropLuminance;
                }
            };

            firstMessageCell = createPreviewMessageCell(context, cellDelegate);
            secondMessageCell = createPreviewMessageCell(context, cellDelegate);
            messagesContainer.addView(firstMessageCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            messagesContainer.addView(secondMessageCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            roleChip = new LinearLayout(context);
            roleChip.setOrientation(LinearLayout.HORIZONTAL);
            roleChip.setGravity(Gravity.CENTER_VERTICAL);
            roleChip.setPadding(AndroidUtilities.dp(9), AndroidUtilities.dp(4), AndroidUtilities.dp(10), AndroidUtilities.dp(4));
            roleChip.setClickable(true);
            roleChip.setFocusable(true);
            roleChip.setOnClickListener(v -> cycleRole());
            ScaleStateListAnimator.apply(roleChip, 0.06f, 1.5f);
            MirrlyUiKit.attachDisallowInterceptTouch(roleChip);

            roleChipGlass = glassFactory.create(roleChip, new BlurredBackgroundColorProvider() {
                @Override
                public int getShadowColor() {
                    return backdropLuminance < 0.50f ? 0x28000000 : 0x18000000;
                }

                @Override
                public int getBackgroundColor() {
                    if (backdropLuminance < 0.50f) {
                        return ColorUtils.setAlphaComponent(0xFF14181E, 0xA6);
                    }
                    return ColorUtils.setAlphaComponent(0xFFF6F8FA, 0xCC);
                }

                @Override
                public int getStrokeColorTop() {
                    return backdropLuminance < 0.50f ? 0x38FFFFFF : 0x16000000;
                }

                @Override
                public int getStrokeColorBottom() {
                    return backdropLuminance < 0.50f ? 0x14FFFFFF : 0x22000000;
                }
            }).setRadius(AndroidUtilities.dp(13));
            roleChip.setBackground(roleChipGlass);

            dotStrokePaint.setStyle(Paint.Style.STROKE);
            dotStrokePaint.setStrokeWidth(AndroidUtilities.dpf2(1f));
            roleDotView = new View(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    final float cx = getWidth() * 0.5f;
                    final float cy = getHeight() * 0.5f;
                    final float r = AndroidUtilities.dpf2(3.5f);
                    canvas.drawCircle(cx, cy, r, dotPaint);
                    canvas.drawCircle(cx, cy, r, dotStrokePaint);
                }
            };
            roleChip.addView(roleDotView, LayoutHelper.createLinear(8, 8, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));

            roleTextView = new AnimatedTextView(context, false, true, true);
            roleTextView.setTextSize(AndroidUtilities.dp(12));
            roleTextView.setTypeface(AndroidUtilities.bold());
            roleTextView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            roleTextView.setAnimationProperties(0.4f, 0L, 260L, CubicBezierInterpolator.EASE_OUT_QUINT);
            roleChip.addView(roleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 18, Gravity.CENTER_VERTICAL));

            previewContainer.addView(roleChip, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 26, Gravity.TOP | Gravity.LEFT, 10, 10, 10, 0));

            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(AndroidUtilities.dpf2(1f));

            refreshPreview(true, false);
        }

        private ChatMessageCell createPreviewMessageCell(Context context, ChatMessageCell.ChatMessageCellDelegate delegate) {
            ChatMessageCell cell = new ChatMessageCell(context, currentAccount, false, null, resourcesProvider);
            cell.allowOwnTagInPreview = true;
            cell.isChat = true;
            cell.setFullyDraw(true);
            cell.setDelegate(delegate);
            cell.setSelfChatTagGlassFactory(glassFactory);
            cell.setClickable(false);
            cell.setLongClickable(false);
            return cell;
        }

        private void cycleRole() {
            previewRole = (previewRole + 1) % ROLE_COUNT;
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            refreshPreview(false, true);
        }

        private String resolveRoleTitle() {
            if (previewRole == ROLE_OWNER) {
                return LocaleController.getString(R.string.ChatTagOwner);
            }
            if (previewRole == ROLE_ADMIN) {
                return LocaleController.getString(R.string.ChatTagAdmin);
            }
            return LocaleController.getString(R.string.MirrlyChatsPreviewRoleMember);
        }

        private String resolveTagLabel() {
            if (previewRole == ROLE_OWNER) {
                return LocaleController.getString(R.string.ChatTagOwner);
            }
            if (previewRole == ROLE_ADMIN) {
                return LocaleController.getString(R.string.ChatTagAdmin);
            }
            return LocaleController.getString(R.string.MirrlyChatsPreviewMemberTag);
        }

        private int resolveRoleAccentColor() {
            if (previewRole == ROLE_OWNER) {
                return Theme.getColor(Theme.key_chat_tagCreator, resourcesProvider);
            }
            if (previewRole == ROLE_ADMIN) {
                return Theme.getColor(Theme.key_chat_tagAdmin, resourcesProvider);
            }
            return backdropLuminance < 0.50f ? 0xFFB0B8C4 : 0xFF6B7280;
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
            glassColorSource.setColor(backdropColor);
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
                BackgroundGradientDrawable gradientDrawable = (BackgroundGradientDrawable) wallpaperDrawable;
                bgDisposable = gradientDrawable.drawExactBoundsSize(canvas, previewContainer, 0.5f);
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
            TLRPC.User currentUser = UserConfig.getInstance(currentAccount).getCurrentUser();
            if (currentUser != null && MessagesController.getInstance(currentAccount).getUser(currentUser.id) == null) {
                MessagesController.getInstance(currentAccount).putUser(currentUser, true);
            }
            final String tagLabel = resolveTagLabel();
            final int now = (int) (System.currentTimeMillis() / 1000L);

            if (firstMessageObject == null) {
                TLRPC.TL_message msg1 = createOutgoingMessage(1001, selfId, now - 60, LocaleController.getString(R.string.MirrlyChatsPreviewMsg1), tagLabel);
                firstMessageObject = new MessageObject(currentAccount, msg1, true, false);
            } else {
                firstMessageObject.messageOwner.from_rank = tagLabel;
                firstMessageObject.messageOwner.message = LocaleController.getString(R.string.MirrlyChatsPreviewMsg1);
                firstMessageObject.messageText = firstMessageObject.messageOwner.message;
                firstMessageObject.generateCaption();
                firstMessageObject.resetLayout();
            }
            firstMessageObject.sendPreview = true;

            if (secondMessageObject == null) {
                TLRPC.TL_message msg2 = createOutgoingMessage(1002, selfId, now - 30, LocaleController.getString(R.string.MirrlyChatsPreviewMsg2), tagLabel);
                secondMessageObject = new MessageObject(currentAccount, msg2, true, false);
            } else {
                secondMessageObject.messageOwner.from_rank = tagLabel;
                secondMessageObject.messageOwner.message = LocaleController.getString(R.string.MirrlyChatsPreviewMsg2);
                secondMessageObject.messageText = secondMessageObject.messageOwner.message;
                secondMessageObject.generateCaption();
                secondMessageObject.resetLayout();
            }
            secondMessageObject.sendPreview = true;
        }

        private TLRPC.TL_message createOutgoingMessage(int id, long selfId, int date, String text, String rank) {
            TLRPC.TL_message msg = new TLRPC.TL_message();
            msg.id = id;
            msg.flags = 259;
            msg.out = true;
            msg.unread = false;
            msg.dialog_id = -PREVIEW_CHAT_ID;
            msg.peer_id = new TLRPC.TL_peerChannel();
            msg.peer_id.channel_id = PREVIEW_CHAT_ID;
            msg.from_id = new TLRPC.TL_peerUser();
            msg.from_id.user_id = selfId;
            msg.date = date;
            msg.message = text;
            msg.from_rank = rank;
            msg.media = new TLRPC.TL_messageMediaEmpty();
            return msg;
        }

        private void updateChipVisuals(boolean animated) {
            final int roleColor = ColorUtils.setAlphaComponent(resolveRoleAccentColor(), 0xFF);
            dotPaint.setColor(roleColor);
            dotStrokePaint.setColor(backdropLuminance < 0.50f ? 0x33FFFFFF : 0x26000000);
            borderPaint.setColor(backdropLuminance < 0.50f ? 0x22FFFFFF : 0x18000000);
            roleDotView.invalidate();

            roleTextView.setTextColor(backdropLuminance < 0.50f ? GlassContrastHelper.ADAPTIVE_LIGHT_TEXT : GlassContrastHelper.ADAPTIVE_DARK_TEXT);
            final String chipLabel = LocaleController.formatString(R.string.MirrlyChatsPreviewRolePrefix, resolveRoleTitle());
            if (!TextUtils.equals(roleTextView.getText(), chipLabel)) {
                roleTextView.setText(chipLabel, animated);
            }
            if (roleChipGlass != null) {
                roleChipGlass.invalidateSelf();
            }
        }

        public void refreshPreview(boolean refreshWallpaper, boolean animated) {
            if (refreshWallpaper || wallpaperDrawable == null) {
                ensureWallpaperAndBackdrop();
            }
            updateChipVisuals(animated);
            ensurePreviewMessages();

            firstMessageCell.forceResetMessageObject();
            firstMessageCell.setMessageObject(firstMessageObject, null, true, false, false);
            firstMessageCell.invalidate();

            secondMessageCell.forceResetMessageObject();
            secondMessageCell.setMessageObject(secondMessageObject, null, false, true, false);
            secondMessageCell.invalidate();

            previewContainer.invalidate();
            requestLayout();
        }

        @Override
        public void updateColors() {
            refreshPreview(true, false);
        }

        public static final class Factory extends UItem.UItemFactory<OwnTagPreviewCell> {
            static {
                setup(new Factory());
            }

            public static UItem of(int id) {
                return UItem.ofFactory(Factory.class).setId(id);
            }

            @Override
            public OwnTagPreviewCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new OwnTagPreviewCell(context, currentAccount, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                if (view instanceof OwnTagPreviewCell) {
                    ((OwnTagPreviewCell) view).refreshPreview(true, false);
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
                return a.id == b.id && a.viewType == b.viewType;
            }
        }
    }
}
