package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
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

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.mirrly.MirrlyVaultStorage;
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
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.spoilers.SpoilersTextView;

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

        BottomSheet.Builder builder = new BottomSheet.Builder(context, false, resourcesProvider);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(8), 0, dp(16));

        HeaderCell headerCell = new HeaderCell(context, resourcesProvider);
        headerCell.setText(LocaleController.getString(R.string.MirrlyEditHistorySheetTitle));
        root.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(context);
        LinearLayout listLayout = new LinearLayout(context);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());

        if (history.isEmpty()) {
            TextInfoPrivacyCell emptyCell = new TextInfoPrivacyCell(context, resourcesProvider);
            emptyCell.setText(LocaleController.getString(R.string.MirrlyEditHistoryEmpty));
            listLayout.addView(emptyCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        } else {
            for (int i = 0; i < history.size(); i++) {
                MirrlyVaultStorage.EditRevision rev = history.get(i);
                String timeLabel = LocaleController.formatString(R.string.MirrlyEditHistoryVersion, i + 1, sdf.format(new Date(rev.timestamp * 1000L)));
                addRevisionRow(context, listLayout, timeLabel, rev.text, resourcesProvider, fragment);
            }
        }

        if (!TextUtils.isEmpty(currentMessageText)) {
            addRevisionRow(context, listLayout, LocaleController.getString(R.string.MirrlyEditHistoryCurrent), currentMessageText.toString(), resourcesProvider, fragment);
        }

        root.addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f));

        TextView closeBtn = new TextView(context);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        closeBtn.setTypeface(AndroidUtilities.bold());
        closeBtn.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider));
        closeBtn.setBackground(Theme.AdaptiveRipple.filledRectByKey(Theme.key_featuredStickers_addButton, 8));
        closeBtn.setText(LocaleController.getString(R.string.Close));
        root.addView(closeBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 16, 12, 16, 0));

        builder.setCustomView(root);
        BottomSheet sheet = builder.create();
        closeBtn.setOnClickListener(v -> sheet.dismiss());
        fragment.showDialog(sheet);
    }

    private static void addRevisionRow(
            Context context,
            LinearLayout parent,
            String subtitle,
            String text,
            Theme.ResourcesProvider resourcesProvider,
            BaseFragment fragment
    ) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(10), dp(20), dp(10));
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
                BulletinFactory.of(fragment).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
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

        BottomSheet.Builder builder = new BottomSheet.Builder(context, true, resourcesProvider);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(10), 0, dp(16));

        int blueAccent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider);
        int textPrimary = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        int textSecondary = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider);

        // Top bar: Title + "All notes" jump button
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
                        LocaleController.isRTL ? 120 : 0,
                        0,
                        LocaleController.isRTL ? 0 : 120,
                        0
                )
        );

        TextView openAllBtn = new TextView(context);
        openAllBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        openAllBtn.setTypeface(AndroidUtilities.bold());
        openAllBtn.setTextColor(blueAccent);
        openAllBtn.setText(LocaleController.getString(R.string.MirrlyNotesFilterAll));
        openAllBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
        openAllBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(8), Theme.multAlpha(blueAccent, 0.10f), Theme.multAlpha(blueAccent, 0.22f)));
        headerRow.addView(openAllBtn, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL));

        root.addView(headerRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Native quote preview card if attached to a chat/message
        if (dialogId != 0 && (msgId != 0 || !TextUtils.isEmpty(finalPeerTitle))) {
            LinearLayout quoteCard = new LinearLayout(context);
            quoteCard.setOrientation(LinearLayout.HORIZONTAL);
            quoteCard.setPadding(dp(10), dp(8), dp(12), dp(8));
            quoteCard.setBackground(Theme.createRoundRectDrawable(dp(8), Theme.multAlpha(blueAccent, 0.10f)));

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
                quoteCard.addView(quoteTexts, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
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
        noteReadCard.setBackground(Theme.createRoundRectDrawable(dp(12), Theme.multAlpha(textPrimary, 0.05f)));

        SpoilersTextView noteReadText = new SpoilersTextView(context, true, resourcesProvider);
        noteReadText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        noteReadText.setTextColor(textPrimary);
        noteReadText.setTextIsSelectable(true);
        noteReadText.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        if (existing != null) {
            if (existing.spoiler) {
                SpannableStringBuilder ssb = new SpannableStringBuilder(existing.text);
                TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                run.flags = TextStyleSpan.FLAG_STYLE_SPOILER;
                ssb.setSpan(new TextStyleSpan(run), 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                noteReadText.setText(ssb);
            } else {
                noteReadText.setText(existing.text);
            }
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

        TextView editActionBtn = new TextView(context);
        editActionBtn.setGravity(Gravity.CENTER);
        editActionBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        editActionBtn.setTypeface(AndroidUtilities.bold());
        editActionBtn.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider));
        editActionBtn.setBackground(Theme.AdaptiveRipple.filledRectByKey(Theme.key_featuredStickers_addButton, 8));
        editActionBtn.setText(LocaleController.getString(R.string.MirrlyNotesEdit));
        readActionsRow.addView(editActionBtn, LayoutHelper.createLinear(0, 44, 1f, 0, 0, 6, 0));

        TextView copyActionBtn = new TextView(context);
        copyActionBtn.setGravity(Gravity.CENTER);
        copyActionBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        copyActionBtn.setTypeface(AndroidUtilities.bold());
        copyActionBtn.setTextColor(blueAccent);
        copyActionBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(8), Theme.multAlpha(blueAccent, 0.12f), Theme.multAlpha(blueAccent, 0.24f)));
        copyActionBtn.setText(LocaleController.getString(R.string.Copy));
        copyActionBtn.setPadding(dp(14), 0, dp(14), 0);
        readActionsRow.addView(copyActionBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 44, 0, 0, 6, 0));

        int redColor = Theme.getColor(Theme.key_text_RedRegular, resourcesProvider);
        TextView deleteActionBtn = new TextView(context);
        deleteActionBtn.setGravity(Gravity.CENTER);
        deleteActionBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        deleteActionBtn.setTypeface(AndroidUtilities.bold());
        deleteActionBtn.setTextColor(redColor);
        deleteActionBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(8), Theme.multAlpha(redColor, 0.10f), Theme.multAlpha(redColor, 0.22f)));
        deleteActionBtn.setText(LocaleController.getString(R.string.Delete));
        deleteActionBtn.setPadding(dp(14), 0, dp(14), 0);
        readActionsRow.addView(deleteActionBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 44));

        viewPanel.addView(readActionsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Build Edit panel
        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourcesProvider));
        editText.setTextColor(textPrimary);
        editText.setBackground(Theme.createEditTextDrawable(context, false));
        editText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider));
        editText.setCursorSize(dp(20));
        editText.setCursorWidth(1.5f);
        editText.setMinLines(2);
        editText.setMaxLines(7);
        editText.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        editText.setHint(LocaleController.getString(R.string.MirrlyDossierInputHint));
        editText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        editText.setPadding(0, dp(8), 0, dp(8));
        if (existing != null && !TextUtils.isEmpty(existing.text)) {
            editText.setText(existing.text);
            editText.setSelection(existing.text.length());
        }
        editPanel.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 4, 20, 6));

        TextCheckCell spoilerCheckCell = new TextCheckCell(context, resourcesProvider);
        spoilerCheckCell.setTextAndCheck(LocaleController.getString(R.string.MirrlyDossierSpoilerCheck), spoilerState[0], false);
        spoilerCheckCell.setBackground(Theme.getSelectorDrawable(false));
        spoilerCheckCell.setOnClickListener(v -> {
            spoilerState[0] = !spoilerState[0];
            spoilerCheckCell.setChecked(spoilerState[0]);
        });
        editPanel.addView(spoilerCheckCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextInfoPrivacyCell infoCell = new TextInfoPrivacyCell(context, resourcesProvider);
        infoCell.setText(LocaleController.getString(R.string.MirrlyDossierPrivacyInfo));
        editPanel.addView(infoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout editButtonsRow = new LinearLayout(context);
        editButtonsRow.setOrientation(LinearLayout.HORIZONTAL);
        editButtonsRow.setPadding(dp(16), dp(6), dp(16), 0);

        TextView cancelEditBtn = null;
        if (existing != null) {
            cancelEditBtn = new TextView(context);
            cancelEditBtn.setGravity(Gravity.CENTER);
            cancelEditBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            cancelEditBtn.setTypeface(AndroidUtilities.bold());
            cancelEditBtn.setTextColor(blueAccent);
            cancelEditBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(8), Theme.multAlpha(blueAccent, 0.12f), Theme.multAlpha(blueAccent, 0.22f)));
            cancelEditBtn.setText(LocaleController.getString(R.string.Cancel));
            cancelEditBtn.setPadding(dp(18), 0, dp(18), 0);
            editButtonsRow.addView(cancelEditBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 48, 0, 0, 8, 0));
        }

        TextView saveBtn = new TextView(context);
        saveBtn.setGravity(Gravity.CENTER);
        saveBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        saveBtn.setTypeface(AndroidUtilities.bold());
        saveBtn.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider));
        saveBtn.setBackground(Theme.AdaptiveRipple.filledRectByKey(Theme.key_featuredStickers_addButton, 8));
        saveBtn.setText(LocaleController.getString(R.string.Save));
        editButtonsRow.addView(saveBtn, LayoutHelper.createLinear(0, 48, 1f));

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

        builder.setCustomView(root);
        BottomSheet sheet = builder.create();

        openAllBtn.setOnClickListener(v -> {
            AndroidUtilities.hideKeyboard(editText);
            sheet.dismiss();
            if (!(fragment instanceof MirrlyNotesChatActivity)) {
                fragment.presentFragment(new MirrlyNotesChatActivity());
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
                BulletinFactory.of(fragment).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
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
}
