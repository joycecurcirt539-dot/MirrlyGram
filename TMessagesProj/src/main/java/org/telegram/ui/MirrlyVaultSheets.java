package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.content.Context;
import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.mirrly.MirrlyVaultStorage;
import org.telegram.messenger.utils.ViewOutlineProviderImpl;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.spoilers.SpoilersTextView;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MirrlyVaultSheets {

    private MirrlyVaultSheets() {
    }

    public static void showEditHistorySheet(BaseFragment fragment, long dialogId, int msgId, CharSequence currentMessageText) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        Context context = fragment.getParentActivity();
        Theme.ResourcesProvider resourcesProvider = fragment.getResourceProvider();
        List<MirrlyVaultStorage.EditRevision> history = MirrlyVaultStorage.getEditHistory(dialogId, msgId);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(14), 0, dp(16));

        HeaderCell headerCell = new HeaderCell(context, resourcesProvider);
        headerCell.setText(LocaleController.getString(R.string.MirrlyEditHistorySheetTitle));
        root.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(context);
        LinearLayout listLayout = new LinearLayout(context);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
        final BottomSheet[] sheetHolder = new BottomSheet[1];

        boolean hasCurrent = !TextUtils.isEmpty(currentMessageText);
        if (history.isEmpty() && !hasCurrent) {
            TextInfoPrivacyCell emptyCell = new TextInfoPrivacyCell(context, resourcesProvider);
            emptyCell.setText(LocaleController.getString(R.string.MirrlyEditHistoryEmpty));
            listLayout.addView(emptyCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        } else {
            if (history.isEmpty()) {
                TextInfoPrivacyCell emptyCell = new TextInfoPrivacyCell(context, resourcesProvider);
                emptyCell.setText(LocaleController.getString(R.string.MirrlyEditHistoryEmpty));
                listLayout.addView(emptyCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
            LinearLayout revisionsCard = new LinearLayout(context);
            revisionsCard.setOrientation(LinearLayout.VERTICAL);
            revisionsCard.setBackground(MirrlyUiKit.createTintedSurfaceDrawable(14, resourcesProvider));
            revisionsCard.setOutlineProvider(ViewOutlineProviderImpl.boundsWithPaddingRoundRect(0, dp(14)));
            revisionsCard.setClipToOutline(true);

            int totalRows = history.size() + (hasCurrent ? 1 : 0);
            int rowIndex = 0;
            int versionNum = 1;
            for (int i = 0; i < history.size(); i++) {
                MirrlyVaultStorage.EditRevision rev = history.get(i);
                String formattedDate = sdf.format(new Date(rev.timestamp * 1000L));
                String timeLabel = rev.isOriginal
                        ? LocaleController.formatString(R.string.MirrlyEditHistoryOriginal, formattedDate)
                        : LocaleController.formatString(R.string.MirrlyEditHistoryVersion, versionNum++, formattedDate);
                addRevisionRow(context, revisionsCard, timeLabel, rev.text, resourcesProvider, fragment, sheetHolder);
                rowIndex++;
                if (rowIndex < totalRows) {
                    addRevisionDivider(context, revisionsCard, resourcesProvider);
                }
            }
            if (hasCurrent) {
                addRevisionRow(context, revisionsCard, LocaleController.getString(R.string.MirrlyEditHistoryCurrent), currentMessageText.toString(), resourcesProvider, fragment, sheetHolder);
            }
            listLayout.addView(revisionsCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 4, 16, 4));
        }

        root.addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f));

        ButtonWithCounterView closeBtn = new ButtonWithCounterView(context, resourcesProvider).setRound();
        closeBtn.setText(LocaleController.getString(R.string.Close), false);
        root.addView(closeBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 16, 12, 16, 0));

        BottomSheet sheet = MirrlyUiKit.createFrostedBottomSheet(context, false, resourcesProvider, root);
        sheetHolder[0] = sheet;
        closeBtn.setOnClickListener(v -> sheet.dismiss());
        fragment.showDialog(sheet);
    }

    private static void addRevisionDivider(
            Context context,
            LinearLayout parent,
            Theme.ResourcesProvider resourcesProvider
    ) {
        View divider = new View(context);
        divider.setBackgroundColor(Theme.getColor(Theme.key_divider, resourcesProvider));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, Math.round(dpf2(0.5f)))
        );
        lp.leftMargin = LocaleController.isRTL ? 0 : dp(16);
        lp.rightMargin = LocaleController.isRTL ? dp(16) : 0;
        parent.addView(divider, lp);
    }

    private static void addRevisionRow(
            Context context,
            LinearLayout parent,
            String subtitle,
            String text,
            Theme.ResourcesProvider resourcesProvider,
            BaseFragment fragment,
            BottomSheet[] sheetHolder
    ) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(11), dp(16), dp(11));
        card.setBackground(Theme.getSelectorDrawable(false));

        TextView metaView = new TextView(context);
        metaView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        metaView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        metaView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        metaView.setText(subtitle);
        card.addView(metaView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView bodyView = new TextView(context);
        bodyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        bodyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        bodyView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        bodyView.setText(text);
        card.addView(bodyView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

        card.setOnClickListener(v -> {
            if (AndroidUtilities.addToClipboard(text)) {
                BulletinFactory factory = sheetHolder != null && sheetHolder[0] != null
                        ? BulletinFactory.of(sheetHolder[0].getContainer(), resourcesProvider)
                        : BulletinFactory.of(fragment);
                factory.createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
            }
        });
        parent.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    public static void showDossierSheet(BaseFragment fragment, long dialogId, int msgId, @Nullable Runnable onSaved) {
        showDossierSheet(fragment, dialogId, msgId, null, onSaved);
    }

    public static void showDossierSheet(BaseFragment fragment, @Nullable MessageObject messageObject, @Nullable Runnable onSaved) {
        if (messageObject == null) {
            return;
        }
        showDossierSheet(fragment, messageObject.getDialogId(), messageObject.getId(), messageObject, onSaved);
    }

    public static void showDossierSheet(
            BaseFragment fragment,
            long dialogId,
            int msgId,
            @Nullable MessageObject messageObject,
            @Nullable Runnable onSaved
    ) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        Context context = fragment.getParentActivity();
        Theme.ResourcesProvider resourcesProvider = fragment.getResourceProvider();
        int currentAccount = fragment.getCurrentAccount();

        boolean secretChat = DialogObject.isEncryptedDialog(dialogId);
        String resolvedPeerTitle = resolvePeerTitle(currentAccount, dialogId);
        String resolvedAuthorName = resolveAuthorName(currentAccount, messageObject, resolvedPeerTitle);
        String resolvedQuotePreview = secretChat ? "" : extractQuotePreview(messageObject);

        MirrlyVaultStorage.DossierNote existing = MirrlyVaultStorage.getDossierNoteRecord(dialogId, msgId);
        if (existing != null) {
            if (TextUtils.isEmpty(resolvedPeerTitle)) {
                resolvedPeerTitle = existing.peerTitle;
            }
            if (TextUtils.isEmpty(resolvedAuthorName)) {
                resolvedAuthorName = existing.authorName;
            }
            if (TextUtils.isEmpty(resolvedQuotePreview) && !secretChat) {
                resolvedQuotePreview = existing.quotePreview;
            }
        }
        final String finalPeerTitle = resolvedPeerTitle;
        final String finalAuthorName = resolvedAuthorName;
        final String finalQuotePreview = resolvedQuotePreview;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(16), 0, dp(16));

        int blueAccent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider);
        int textPrimary = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        int textSecondary = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider);

        // Top bar: Title + "Open in notes chat" jump button
        FrameLayout headerRow = new FrameLayout(context);
        headerRow.setPadding(dp(20), dp(6), dp(14), dp(8));

        TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        titleView.setText(LocaleController.getString(msgId != 0 ? R.string.MirrlyDossierMessageNoteTitle : R.string.MirrlyNotesChatNoteTitle));
        titleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        headerRow.addView(
                titleView,
                LayoutHelper.createFrame(
                        LayoutHelper.WRAP_CONTENT,
                        LayoutHelper.WRAP_CONTENT,
                        (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL,
                        LocaleController.isRTL ? 136 : 0,
                        0,
                        LocaleController.isRTL ? 0 : 136,
                        0
                )
        );

        TextView openAllBtn = new TextView(context);
        openAllBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        openAllBtn.setTypeface(AndroidUtilities.bold());
        openAllBtn.setTextColor(ColorUtils.blendARGB(blueAccent, textPrimary, 0.18f));
        openAllBtn.setText(LocaleController.getString(R.string.MirrlyNotesOpenInNotesChat));
        openAllBtn.setPadding(dp(12), dp(7), dp(12), dp(7));
        openAllBtn.setBackground(MirrlyUiKit.createTintedSurfaceDrawable(
                14,
                Theme.multAlpha(blueAccent, 0.16f),
                ColorUtils.setAlphaComponent(blueAccent, 0x4C),
                resourcesProvider,
                true
        ));
        ScaleStateListAnimator.apply(openAllBtn, 0.03f, 1.0f);
        if (fragment instanceof MirrlyNotesChatActivity) {
            openAllBtn.setVisibility(View.GONE);
        }
        headerRow.addView(openAllBtn, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL));

        root.addView(headerRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Native quote preview card if attached to a chat/message
        if (dialogId != 0 && (msgId != 0 || !TextUtils.isEmpty(finalPeerTitle))) {
            LinearLayout quoteCard = new LinearLayout(context);
            quoteCard.setOrientation(LinearLayout.HORIZONTAL);
            quoteCard.setPadding(LocaleController.isRTL ? dp(12) : dp(10), dp(8), LocaleController.isRTL ? dp(10) : dp(12), dp(8));
            quoteCard.setBackground(MirrlyUiKit.createTintedSurfaceDrawable(
                    12,
                    Theme.multAlpha(blueAccent, 0.10f),
                    ColorUtils.setAlphaComponent(blueAccent, 0x30),
                    resourcesProvider,
                    false
            ));

            View stripe = new View(context);
            stripe.setBackground(Theme.createRoundRectDrawable(dp(2), blueAccent));

            LinearLayout quoteTexts = new LinearLayout(context);
            quoteTexts.setOrientation(LinearLayout.VERTICAL);

            TextView quoteTitle = new TextView(context);
            quoteTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            quoteTitle.setTypeface(AndroidUtilities.bold());
            quoteTitle.setTextColor(blueAccent);
            quoteTitle.setSingleLine(true);
            quoteTitle.setEllipsize(TextUtils.TruncateAt.END);
            quoteTitle.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            if (secretChat) {
                quoteTitle.setText(LocaleController.getString(R.string.MirrlyNotesSecretChat));
            } else if (!TextUtils.isEmpty(finalPeerTitle) && !TextUtils.isEmpty(finalAuthorName) && !TextUtils.equals(finalPeerTitle, finalAuthorName)) {
                quoteTitle.setText(finalPeerTitle + " • " + finalAuthorName);
            } else if (!TextUtils.isEmpty(finalPeerTitle)) {
                quoteTitle.setText(finalPeerTitle);
            } else if (!TextUtils.isEmpty(finalAuthorName)) {
                quoteTitle.setText(finalAuthorName);
            } else {
                quoteTitle.setText(LocaleController.formatString(R.string.MirrlyNotesMessageNumber, msgId));
            }
            quoteTexts.addView(quoteTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            TextView quoteBody = new TextView(context);
            quoteBody.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            quoteBody.setTextColor(textPrimary);
            quoteBody.setMaxLines(2);
            quoteBody.setEllipsize(TextUtils.TruncateAt.END);
            quoteBody.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            if (secretChat) {
                quoteBody.setText(LocaleController.getString(R.string.MirrlyDossierSecretHidden));
            } else if (msgId == 0) {
                quoteBody.setText(LocaleController.getString(R.string.MirrlyDossierWholeChatHint));
            } else {
                quoteBody.setText(!TextUtils.isEmpty(finalQuotePreview) ? finalQuotePreview : LocaleController.formatString(R.string.MirrlyNotesMessageNumber, msgId));
            }
            quoteTexts.addView(quoteBody, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

            if (LocaleController.isRTL) {
                quoteCard.addView(quoteTexts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
                quoteCard.addView(stripe, LayoutHelper.createLinear(3, LayoutHelper.MATCH_PARENT, 9, 0, 0, 0));
            } else {
                quoteCard.addView(stripe, LayoutHelper.createLinear(3, LayoutHelper.MATCH_PARENT, 0, 0, 9, 0));
                quoteCard.addView(quoteTexts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            }
            root.addView(quoteCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 2, 20, 10));
        }

        // Read-first mode container (shown when note already exists)
        LinearLayout viewPanel = new LinearLayout(context);
        viewPanel.setOrientation(LinearLayout.VERTICAL);

        // Edit mode container
        LinearLayout editPanel = new LinearLayout(context);
        editPanel.setOrientation(LinearLayout.VERTICAL);

        final boolean[] spoilerState = new boolean[]{existing != null && existing.spoiler};

        // Build Read-first panel
        LinearLayout noteReadCard = new LinearLayout(context);
        noteReadCard.setOrientation(LinearLayout.VERTICAL);
        noteReadCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        noteReadCard.setBackground(MirrlyUiKit.createTintedSurfaceDrawable(14, resourcesProvider));

        if (existing != null && existing.hasMedia()) {
            TextView mediaTagView = new TextView(context);
            mediaTagView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            mediaTagView.setTypeface(AndroidUtilities.bold());
            mediaTagView.setTextColor(blueAccent);
            String label = resolveMediaBadgeLabel(existing);
            mediaTagView.setText(label);
            mediaTagView.setPadding(dp(8), dp(4), dp(8), dp(4));
            mediaTagView.setBackground(Theme.createRoundRectDrawable(dp(6), Theme.multAlpha(blueAccent, 0.12f)));
            noteReadCard.addView(mediaTagView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 0, 0, 0, 6));
        }

        SpoilersTextView noteReadText = new SpoilersTextView(context, true, resourcesProvider);
        noteReadText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        noteReadText.setTextColor(textPrimary);
        noteReadText.setTextIsSelectable(true);
        noteReadText.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        if (existing != null && !TextUtils.isEmpty(existing.text)) {
            if (existing.spoiler) {
                SpannableStringBuilder ssb = new SpannableStringBuilder(existing.text);
                TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                run.flags = TextStyleSpan.FLAG_STYLE_SPOILER;
                ssb.setSpan(new TextStyleSpan(run), 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                noteReadText.setText(ssb);
            } else {
                noteReadText.setText(existing.text);
            }
        } else {
            noteReadText.setVisibility(View.GONE);
        }
        noteReadCard.addView(noteReadText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView noteMetaView = new TextView(context);
        noteMetaView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        noteMetaView.setTextColor(textSecondary);
        noteMetaView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        if (existing != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
            StringBuilder meta = new StringBuilder();
            meta.append(LocaleController.formatString(existing.isEdited() ? R.string.MirrlyDossierEditedAt : R.string.MirrlyDossierCreatedAt, sdf.format(new Date(existing.updatedAt * 1000L))));
            if (existing.spoiler) {
                meta.append(" ").append(LocaleController.getString(R.string.MirrlyDossierSpoilerMetaSuffix));
            }
            noteMetaView.setText(meta.toString());
        }
        noteReadCard.addView(noteMetaView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

        viewPanel.addView(noteReadCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 2, 20, 12));

        LinearLayout readActionsRow = new LinearLayout(context);
        readActionsRow.setOrientation(LinearLayout.HORIZONTAL);
        readActionsRow.setPadding(dp(16), 0, dp(16), 0);

        ButtonWithCounterView editActionBtn = new ButtonWithCounterView(context, resourcesProvider).setRound();
        editActionBtn.setText(LocaleController.getString(R.string.MirrlyNotesEdit), false);

        ButtonWithCounterView copyActionBtn = new ButtonWithCounterView(context, resourcesProvider).setRound();
        copyActionBtn.setUseWrapContent(true);
        copyActionBtn.setPadding(dp(14), 0, dp(14), 0);
        copyActionBtn.setColor(Theme.multAlpha(blueAccent, 0.14f));
        copyActionBtn.setTextColor(blueAccent);
        copyActionBtn.setText(LocaleController.getString(R.string.Copy), false);

        int redColor = Theme.getColor(Theme.key_text_RedRegular, resourcesProvider);
        ButtonWithCounterView deleteActionBtn = new ButtonWithCounterView(context, resourcesProvider).setRound();
        deleteActionBtn.setUseWrapContent(true);
        deleteActionBtn.setPadding(dp(14), 0, dp(14), 0);
        deleteActionBtn.setColor(Theme.multAlpha(redColor, 0.12f));
        deleteActionBtn.setTextColor(redColor);
        deleteActionBtn.setText(LocaleController.getString(R.string.Delete), false);

        if (LocaleController.isRTL) {
            readActionsRow.addView(deleteActionBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 48, 0, 0, 6, 0));
            readActionsRow.addView(copyActionBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 48, 0, 0, 6, 0));
            readActionsRow.addView(editActionBtn, LayoutHelper.createLinear(0, 48, 1f));
        } else {
            readActionsRow.addView(editActionBtn, LayoutHelper.createLinear(0, 48, 1f, 0, 0, 6, 0));
            readActionsRow.addView(copyActionBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 48, 0, 0, 6, 0));
            readActionsRow.addView(deleteActionBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 48));
        }

        viewPanel.addView(readActionsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Build Edit panel
        boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
        int surfaceFill = ColorUtils.setAlphaComponent(isDark ? Color.WHITE : Color.BLACK, isDark ? 0x16 : 0x0D);
        int surfaceRim = MirrlyUiKit.resolveSurfaceHairlineColor(resourcesProvider);

        FrameLayout inputBox = new FrameLayout(context);
        inputBox.setBackground(MirrlyUiKit.createTintedSurfaceDrawable(14, surfaceFill, surfaceRim, resourcesProvider, false));

        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setHintTextColor(ColorUtils.blendARGB(textSecondary, textPrimary, 0.28f));
        editText.setTextColor(textPrimary);
        editText.setBackground(null);
        editText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider));
        editText.setCursorSize(dp(20));
        editText.setCursorWidth(1.5f);
        editText.setMinLines(2);
        editText.setMaxLines(7);
        editText.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        editText.setHint(LocaleController.getString(R.string.MirrlyDossierInputHint));
        editText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        editText.setPadding(dp(14), dp(12), dp(14), dp(12));
        if (existing != null && !TextUtils.isEmpty(existing.text)) {
            editText.setText(existing.text);
            editText.setSelection(existing.text.length());
        }
        inputBox.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        editPanel.addView(inputBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 4, 20, 8));

        TextCheckCell spoilerCheckCell = new TextCheckCell(context, 16, true, resourcesProvider);
        spoilerCheckCell.getCheckBox().setColors(
                Theme.key_windowBackgroundWhiteGrayText,
                Theme.key_switchTrackChecked,
                Theme.key_windowBackgroundWhite,
                Theme.key_windowBackgroundWhite
        );
        spoilerCheckCell.setTextAndCheck(LocaleController.getString(R.string.MirrlyDossierSpoilerCheck), spoilerState[0], false);
        spoilerCheckCell.setBackground(MirrlyUiKit.createTintedSurfaceDrawable(14, surfaceFill, surfaceRim, resourcesProvider, true));
        spoilerCheckCell.setOutlineProvider(ViewOutlineProviderImpl.boundsWithPaddingRoundRect(0, dp(14)));
        spoilerCheckCell.setClipToOutline(true);
        spoilerCheckCell.setOnClickListener(v -> {
            spoilerState[0] = !spoilerState[0];
            spoilerCheckCell.setChecked(spoilerState[0]);
        });
        editPanel.addView(spoilerCheckCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 0, 20, 4));

        TextInfoPrivacyCell infoCell = new TextInfoPrivacyCell(context, resourcesProvider);
        infoCell.setBackground(null);
        if (infoCell.getTextView() != null) {
            infoCell.getTextView().setTextColor(ColorUtils.blendARGB(textSecondary, textPrimary, 0.18f));
        }
        infoCell.setText(LocaleController.getString(R.string.MirrlyDossierPrivacyInfo));
        editPanel.addView(infoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout editButtonsRow = new LinearLayout(context);
        editButtonsRow.setOrientation(LinearLayout.HORIZONTAL);
        editButtonsRow.setPadding(dp(16), dp(6), dp(16), 0);

        ButtonWithCounterView cancelEditBtn = null;
        if (existing != null) {
            cancelEditBtn = new ButtonWithCounterView(context, resourcesProvider).setRound().setNeutral();
            cancelEditBtn.setUseWrapContent(true);
            cancelEditBtn.setPadding(dp(18), 0, dp(18), 0);
            cancelEditBtn.setText(LocaleController.getString(R.string.Cancel), false);
        }

        ButtonWithCounterView saveBtn = new ButtonWithCounterView(context, resourcesProvider).setRound();
        saveBtn.setText(LocaleController.getString(R.string.Save), false);

        if (cancelEditBtn != null) {
            if (LocaleController.isRTL) {
                editButtonsRow.addView(saveBtn, LayoutHelper.createLinear(0, 48, 1f, 0, 0, 8, 0));
                editButtonsRow.addView(cancelEditBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 48));
            } else {
                editButtonsRow.addView(cancelEditBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 48, 0, 0, 8, 0));
                editButtonsRow.addView(saveBtn, LayoutHelper.createLinear(0, 48, 1f));
            }
        } else {
            editButtonsRow.addView(saveBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        }

        editPanel.addView(editButtonsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (existing != null) {
            viewPanel.setVisibility(View.VISIBLE);
            editPanel.setVisibility(View.GONE);
        } else {
            viewPanel.setVisibility(View.GONE);
            editPanel.setVisibility(View.VISIBLE);
        }

        root.addView(viewPanel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(editPanel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        BottomSheet sheet = MirrlyUiKit.createFrostedBottomSheet(context, true, resourcesProvider, root);

        openAllBtn.setOnClickListener(v -> {
            AndroidUtilities.hideKeyboard(editText);
            sheet.dismiss();
            if (!(fragment instanceof MirrlyNotesChatActivity)) {
                fragment.presentFragment(new MirrlyNotesChatActivity(existing != null ? existing.id : null));
            }
        });

        editActionBtn.setOnClickListener(v -> {
            viewPanel.setVisibility(View.GONE);
            editPanel.setVisibility(View.VISIBLE);
            editPanel.setAlpha(0f);
            editPanel.animate().alpha(1f).setDuration(180).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
            editText.postDelayed(() -> {
                editText.requestFocus();
                editText.setSelection(editText.getText() != null ? editText.getText().length() : 0);
                AndroidUtilities.showKeyboard(editText);
            }, 80);
        });

        if (cancelEditBtn != null) {
            cancelEditBtn.setOnClickListener(v -> {
                AndroidUtilities.hideKeyboard(editText);
                editPanel.setVisibility(View.GONE);
                viewPanel.setVisibility(View.VISIBLE);
                viewPanel.setAlpha(0f);
                viewPanel.animate().alpha(1f).setDuration(180).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
            });
        }

        copyActionBtn.setOnClickListener(v -> {
            if (existing != null && AndroidUtilities.addToClipboard(existing.text)) {
                BulletinFactory.of(sheet.getContainer(), resourcesProvider).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
            }
        });

        deleteActionBtn.setOnClickListener(v -> {
            if (existing == null) {
                sheet.dismiss();
                return;
            }
            MirrlyVaultStorage.DossierNote removed = MirrlyVaultStorage.deleteNoteById(existing.id);
            sheet.dismiss();
            if (onSaved != null) {
                onSaved.run();
            }
            if (removed != null) {
                BulletinFactory.of(fragment).createUndoBulletin(LocaleController.getString(R.string.MirrlyNotesDeletedToast), () -> {
                    MirrlyVaultStorage.restoreNote(removed);
                    if (onSaved != null) {
                        onSaved.run();
                    }
                }, null).show();
            }
        });

        saveBtn.setOnClickListener(v -> {
            String noteText = editText.getText() != null ? editText.getText().toString() : "";
            MirrlyVaultStorage.DossierNote previous = existing;
            MirrlyVaultStorage.DossierNote saved = MirrlyVaultStorage.saveMessageOrChatNote(
                    dialogId,
                    msgId,
                    noteText,
                    spoilerState[0],
                    finalPeerTitle,
                    finalAuthorName,
                    finalQuotePreview,
                    secretChat
            );
            AndroidUtilities.hideKeyboard(editText);
            sheet.dismiss();
            if (onSaved != null) {
                onSaved.run();
            }
            if (saved == null && previous != null) {
                BulletinFactory.of(fragment).createUndoBulletin(LocaleController.getString(R.string.MirrlyNotesDeletedToast), () -> {
                    MirrlyVaultStorage.restoreNote(previous);
                    if (onSaved != null) {
                        onSaved.run();
                    }
                }, null).show();
            } else if (saved != null) {
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.ic_save_to_gallery, LocaleController.getString(R.string.MirrlyDossierSavedToast)).show();
            }
        });

        fragment.showDialog(sheet);
        if (existing == null) {
            editText.postDelayed(() -> {
                editText.requestFocus();
                AndroidUtilities.showKeyboard(editText);
            }, 150);
        }
    }

    private static String resolvePeerTitle(int currentAccount, long dialogId) {
        if (dialogId == 0) {
            return "";
        }
        if (DialogObject.isEncryptedDialog(dialogId)) {
            return LocaleController.getString(R.string.MirrlyNotesSecretChat);
        }
        if (dialogId > 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            if (user != null) {
                return UserObject.isUserSelf(user) ? LocaleController.getString(R.string.SavedMessages) : UserObject.getUserName(user);
            }
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            if (chat != null) {
                return chat.title;
            }
        }
        return "";
    }

    private static String resolveAuthorName(int currentAccount, @Nullable MessageObject messageObject, String fallbackPeerTitle) {
        if (messageObject == null) {
            return fallbackPeerTitle;
        }
        long fromId = messageObject.getFromChatId();
        if (fromId > 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(fromId);
            if (user != null) {
                return UserObject.getUserName(user);
            }
        } else if (fromId < 0) {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-fromId);
            if (chat != null) {
                return chat.title;
            }
        }
        return fallbackPeerTitle;
    }

    private static String extractQuotePreview(@Nullable MessageObject msg) {
        if (msg == null) {
            return "";
        }
        CharSequence raw = !TextUtils.isEmpty(msg.messageText) ? msg.messageText : msg.caption;
        if (!TextUtils.isEmpty(raw)) {
            return raw.toString();
        }
        if (msg.messageOwner != null && !TextUtils.isEmpty(msg.messageOwner.message)) {
            return msg.messageOwner.message;
        }
        if (msg.isPhoto()) {
            return LocaleController.getString(R.string.AttachPhoto);
        } else if (msg.isVideo()) {
            return LocaleController.getString(R.string.AttachVideo);
        } else if (msg.isVoice()) {
            return LocaleController.getString(R.string.AttachAudio);
        } else if (msg.isRoundVideo()) {
            return LocaleController.getString(R.string.AttachRound);
        } else if (msg.isSticker() || msg.isAnimatedSticker()) {
            return LocaleController.getString(R.string.AttachSticker);
        } else if (msg.isGif()) {
            return LocaleController.getString(R.string.AttachGif);
        } else if (msg.isMusic()) {
            return LocaleController.getString(R.string.AttachMusic);
        } else if (msg.isDocument()) {
            String docName = msg.getDocumentName();
            return !TextUtils.isEmpty(docName) ? docName : LocaleController.getString(R.string.AttachDocument);
        } else if (msg.isPoll()) {
            return LocaleController.getString(R.string.Poll);
        }
        return "";
    }

    private static String resolveMediaBadgeLabel(MirrlyVaultStorage.DossierNote note) {
        if (note == null || !note.hasMedia()) {
            return "";
        }
        String base;
        switch (note.mediaKind) {
            case MirrlyVaultStorage.MEDIA_PHOTO:
                base = LocaleController.getString(R.string.MirrlyNotesMediaPhotoLabel);
                break;
            case MirrlyVaultStorage.MEDIA_VIDEO:
                base = LocaleController.getString(R.string.MirrlyNotesMediaVideoLabel);
                break;
            case MirrlyVaultStorage.MEDIA_ROUND:
                base = LocaleController.getString(R.string.MirrlyNotesMediaRoundLabel);
                break;
            case MirrlyVaultStorage.MEDIA_VOICE:
                base = LocaleController.getString(R.string.MirrlyNotesMediaVoiceLabel);
                break;
            case MirrlyVaultStorage.MEDIA_AUDIO:
                base = LocaleController.getString(R.string.MirrlyNotesMediaAudioLabel);
                break;
            case MirrlyVaultStorage.MEDIA_STICKER:
                base = LocaleController.getString(R.string.MirrlyNotesMediaStickerLabel);
                break;
            case MirrlyVaultStorage.MEDIA_GIF:
                base = LocaleController.getString(R.string.MirrlyNotesMediaGifLabel);
                break;
            case MirrlyVaultStorage.MEDIA_GEO:
                base = LocaleController.getString(R.string.MirrlyNotesMediaGeoLabel);
                break;
            case MirrlyVaultStorage.MEDIA_CONTACT:
                base = LocaleController.getString(R.string.MirrlyNotesMediaContactLabel);
                break;
            case MirrlyVaultStorage.MEDIA_POLL:
                base = LocaleController.getString(R.string.MirrlyNotesMediaPollLabel);
                break;
            default:
                base = LocaleController.getString(R.string.MirrlyNotesMediaFileLabel);
                break;
        }
        if (!TextUtils.isEmpty(note.fileName)) {
            return base + " • " + note.fileName;
        }
        return base;
    }
}
