package org.telegram.messenger.mirrly;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class MirrlyVaultStorage {

    private static final String PREFS_NAME = "mirrly_vault_store";
    private static final String PREFIX_EDIT = "edit_";
    private static final String PREFIX_DOSSIER = "dossier_";
    private static final String PREFIX_DOSSIER_CHAT = "dossier_chat_";
    private static final String PREFIX_DOSSIER_QUICK = "dossier_quick_";
    private static final int MAX_REVISIONS_PER_MSG = 20;
    private static final int NOTE_SCHEMA_VERSION = 1;
    private static final int MAX_QUOTE_PREVIEW_LEN = 240;

    public static final int FILTER_ALL = 0;
    public static final int FILTER_MESSAGES = 1;
    public static final int FILTER_STANDALONE = 2;
    public static final int FILTER_SPOILER = 3;

    public static final class EditRevision {
        public final int timestamp;
        public final String text;

        public EditRevision(int timestamp, String text) {
            this.timestamp = timestamp;
            this.text = text != null ? text : "";
        }
    }

    public static final class DossierNote {
        public final String id;
        public final String prefKey;
        public final long dialogId;
        public final int msgId;
        public final String text;
        public final int createdAt;
        public final int updatedAt;
        public final boolean spoiler;
        public final boolean pinned;
        public final String peerTitle;
        public final String authorName;
        public final String quotePreview;
        public final boolean secretChat;

        public DossierNote(
                String id,
                String prefKey,
                long dialogId,
                int msgId,
                String text,
                int createdAt,
                int updatedAt,
                boolean spoiler,
                boolean pinned,
                String peerTitle,
                String authorName,
                String quotePreview,
                boolean secretChat
        ) {
            this.id = id;
            this.prefKey = prefKey;
            this.dialogId = dialogId;
            this.msgId = msgId;
            this.text = text != null ? text : "";
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.spoiler = spoiler;
            this.pinned = pinned;
            this.peerTitle = peerTitle != null ? peerTitle : "";
            this.authorName = authorName != null ? authorName : "";
            this.quotePreview = secretChat ? "" : (quotePreview != null ? quotePreview : "");
            this.secretChat = secretChat;
        }

        public boolean isMessageNote() {
            return dialogId != 0 && msgId != 0;
        }

        public boolean isChatNote() {
            return dialogId != 0 && msgId == 0;
        }

        public boolean isStandaloneNote() {
            return dialogId == 0;
        }

        public boolean isEdited() {
            return updatedAt > createdAt + 1;
        }
    }

    private static final ConcurrentHashMap<String, List<EditRevision>> editCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, DossierNote> notesById = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, DossierNote> notesByPrefKey = new ConcurrentHashMap<>();
    private static final AtomicInteger quickSeq = new AtomicInteger(1);
    private static volatile boolean initialized = false;

    private MirrlyVaultStorage() {
    }

    private static synchronized void ensureInit() {
        if (initialized) {
            return;
        }
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return;
        }
        loadAllNotesFromPrefs();
        initialized = true;
    }

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
    }

    private static String msgKey(long dialogId, int msgId) {
        return dialogId + "_" + msgId;
    }

    private static String prefKeyForTarget(long dialogId, int msgId) {
        return msgId != 0 ? (PREFIX_DOSSIER + dialogId + "_" + msgId) : (PREFIX_DOSSIER_CHAT + dialogId);
    }

    private static String idForTarget(long dialogId, int msgId) {
        return msgId != 0 ? ("msg_" + dialogId + "_" + msgId) : ("chat_" + dialogId);
    }

    private static int nowSec() {
        return (int) (System.currentTimeMillis() / 1000L);
    }

    private static String sanitizePreview(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return "";
        }
        String singleLine = raw.replace('\n', ' ').replace('\r', ' ').trim();
        if (singleLine.length() > MAX_QUOTE_PREVIEW_LEN) {
            return singleLine.substring(0, MAX_QUOTE_PREVIEW_LEN - 1) + "…";
        }
        return singleLine;
    }

    private static void notifyNotesUpdated() {
        AndroidUtilities.runOnUIThread(() ->
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mirrlyNotesDidUpdate)
        );
    }

    private static void loadAllNotesFromPrefs() {
        if (ApplicationLoader.applicationContext == null) {
            return;
        }
        SharedPreferences prefs = getPrefs();
        Map<String, ?> all = prefs.getAll();
        if (all == null || all.isEmpty()) {
            return;
        }
        SharedPreferences.Editor migrationEditor = null;
        int now = nowSec();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.startsWith(PREFIX_DOSSIER)) {
                continue;
            }
            Object rawVal = entry.getValue();
            if (!(rawVal instanceof String)) {
                continue;
            }
            String strVal = (String) rawVal;
            if (TextUtils.isEmpty(strVal)) {
                continue;
            }
            DossierNote parsed = parseNoteEntry(key, strVal, now);
            if (parsed == null || TextUtils.isEmpty(parsed.text)) {
                continue;
            }
            notesById.put(parsed.id, parsed);
            notesByPrefKey.put(parsed.prefKey, parsed);
            // Persist migrated legacy plain-string notes so timestamps stay stable across restarts.
            if (!isJsonSchemaNote(strVal)) {
                if (migrationEditor == null) {
                    migrationEditor = prefs.edit();
                }
                String json = serializeNote(parsed);
                if (json != null) {
                    migrationEditor.putString(parsed.prefKey, json);
                }
            }
        }
        if (migrationEditor != null) {
            migrationEditor.apply();
        }
    }

    private static boolean isJsonSchemaNote(String raw) {
        if (raw == null || !raw.startsWith("{")) {
            return false;
        }
        try {
            JSONObject obj = new JSONObject(raw);
            return obj.optInt("mirrly_note_v", 0) >= NOTE_SCHEMA_VERSION;
        } catch (Throwable ignore) {
            return false;
        }
    }

    private static DossierNote parseNoteEntry(String prefKey, String raw, int fallbackTime) {
        if (isJsonSchemaNote(raw)) {
            try {
                JSONObject obj = new JSONObject(raw);
                String id = obj.optString("id", "");
                long dialogId = obj.optLong("dialog_id", 0L);
                int msgId = obj.optInt("msg_id", 0);
                String text = obj.optString("text", "");
                int createdAt = obj.optInt("created_at", fallbackTime);
                int updatedAt = obj.optInt("updated_at", createdAt);
                boolean spoiler = obj.optBoolean("spoiler", false);
                boolean pinned = obj.optBoolean("pinned", false);
                String peerTitle = obj.optString("peer_title", "");
                String authorName = obj.optString("author_name", "");
                boolean secretChat = obj.optBoolean("secret_chat", DialogObject.isEncryptedDialog(dialogId));
                String quotePreview = secretChat ? "" : obj.optString("quote_preview", "");
                if (TextUtils.isEmpty(id)) {
                    id = dialogId == 0 ? prefKey.substring(PREFIX_DOSSIER.length()) : idForTarget(dialogId, msgId);
                }
                return new DossierNote(id, prefKey, dialogId, msgId, text, createdAt, updatedAt, spoiler, pinned, peerTitle, authorName, quotePreview, secretChat);
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }

        String trimmed = raw.trim();
        if (TextUtils.isEmpty(trimmed)) {
            return null;
        }
        long dialogId = 0L;
        int msgId = 0;
        String id;
        if (prefKey.startsWith(PREFIX_DOSSIER_CHAT)) {
            try {
                dialogId = Long.parseLong(prefKey.substring(PREFIX_DOSSIER_CHAT.length()));
            } catch (Throwable ignore) {
            }
            id = idForTarget(dialogId, 0);
        } else if (prefKey.startsWith(PREFIX_DOSSIER_QUICK)) {
            id = prefKey.substring(PREFIX_DOSSIER.length());
        } else {
            String suffix = prefKey.substring(PREFIX_DOSSIER.length());
            int sep = suffix.lastIndexOf('_');
            if (sep > 0) {
                try {
                    dialogId = Long.parseLong(suffix.substring(0, sep));
                    msgId = Integer.parseInt(suffix.substring(sep + 1));
                } catch (Throwable ignore) {
                }
            }
            id = idForTarget(dialogId, msgId);
        }
        boolean secretChat = dialogId != 0 && DialogObject.isEncryptedDialog(dialogId);
        return new DossierNote(id, prefKey, dialogId, msgId, trimmed, fallbackTime, fallbackTime, false, false, "", "", "", secretChat);
    }

    private static String serializeNote(DossierNote note) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("mirrly_note_v", NOTE_SCHEMA_VERSION);
            obj.put("id", note.id);
            obj.put("dialog_id", note.dialogId);
            obj.put("msg_id", note.msgId);
            obj.put("text", note.text);
            obj.put("created_at", note.createdAt);
            obj.put("updated_at", note.updatedAt);
            obj.put("spoiler", note.spoiler);
            obj.put("pinned", note.pinned);
            obj.put("peer_title", note.peerTitle);
            obj.put("author_name", note.authorName);
            obj.put("secret_chat", note.secretChat);
            obj.put("quote_preview", note.secretChat ? "" : note.quotePreview);
            return obj.toString();
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    private static void persistNote(DossierNote note) {
        notesById.put(note.id, note);
        notesByPrefKey.put(note.prefKey, note);
        if (ApplicationLoader.applicationContext != null) {
            String json = serializeNote(note);
            if (json != null) {
                getPrefs().edit().putString(note.prefKey, json).apply();
            }
        }
        notifyNotesUpdated();
    }

    public static void recordEdit(long dialogId, int msgId, String previousText, int timestampSec) {
        if (TextUtils.isEmpty(previousText)) {
            return;
        }
        ensureInit();
        String key = msgKey(dialogId, msgId);
        List<EditRevision> list = getEditHistory(dialogId, msgId);
        if (!list.isEmpty()) {
            EditRevision last = list.get(list.size() - 1);
            if (TextUtils.equals(last.text, previousText)) {
                return;
            }
        }
        List<EditRevision> updated = new ArrayList<>(list);
        updated.add(new EditRevision(timestampSec > 0 ? timestampSec : nowSec(), previousText));
        if (updated.size() > MAX_REVISIONS_PER_MSG) {
            updated = new ArrayList<>(updated.subList(updated.size() - MAX_REVISIONS_PER_MSG, updated.size()));
        }
        editCache.put(key, updated);
        saveEditListToPrefs(key, updated);
    }

    public static boolean hasEditHistory(long dialogId, int msgId) {
        return !getEditHistory(dialogId, msgId).isEmpty();
    }

    public static List<EditRevision> getEditHistory(long dialogId, int msgId) {
        ensureInit();
        String key = msgKey(dialogId, msgId);
        List<EditRevision> cached = editCache.get(key);
        if (cached != null) {
            return cached;
        }
        if (ApplicationLoader.applicationContext == null) {
            return Collections.emptyList();
        }
        String raw = getPrefs().getString(PREFIX_EDIT + key, null);
        if (TextUtils.isEmpty(raw)) {
            return Collections.emptyList();
        }
        List<EditRevision> parsed = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                parsed.add(new EditRevision(obj.optInt("t", 0), obj.optString("v", "")));
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        editCache.put(key, parsed);
        return parsed;
    }

    private static void saveEditListToPrefs(String key, List<EditRevision> list) {
        if (ApplicationLoader.applicationContext == null) {
            return;
        }
        try {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < list.size(); i++) {
                EditRevision rev = list.get(i);
                JSONObject obj = new JSONObject();
                obj.put("t", rev.timestamp);
                obj.put("v", rev.text);
                arr.put(obj);
            }
            getPrefs().edit().putString(PREFIX_EDIT + key, arr.toString()).apply();
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public static DossierNote getDossierNoteRecord(long dialogId, int msgId) {
        ensureInit();
        return notesByPrefKey.get(prefKeyForTarget(dialogId, msgId));
    }

    public static DossierNote getDossierNoteById(String id) {
        if (TextUtils.isEmpty(id)) {
            return null;
        }
        ensureInit();
        return notesById.get(id);
    }

    public static String getDossierNote(long dialogId, int msgId) {
        DossierNote record = getDossierNoteRecord(dialogId, msgId);
        return record != null ? record.text : "";
    }

    public static void setDossierNote(long dialogId, int msgId, String note) {
        DossierNote existing = getDossierNoteRecord(dialogId, msgId);
        saveMessageOrChatNote(
                dialogId,
                msgId,
                note,
                existing != null && existing.spoiler,
                existing != null ? existing.peerTitle : "",
                existing != null ? existing.authorName : "",
                existing != null ? existing.quotePreview : "",
                existing != null ? existing.secretChat : DialogObject.isEncryptedDialog(dialogId)
        );
    }

    public static DossierNote saveMessageOrChatNote(
            long dialogId,
            int msgId,
            String noteText,
            boolean spoiler,
            String peerTitle,
            String authorName,
            String quotePreview,
            boolean secretChat
    ) {
        ensureInit();
        String prefKey = prefKeyForTarget(dialogId, msgId);
        String trimmed = noteText != null ? noteText.trim() : "";
        DossierNote existing = notesByPrefKey.get(prefKey);
        if (TextUtils.isEmpty(trimmed)) {
            if (existing != null) {
                deleteNoteById(existing.id);
            }
            return null;
        }
        int now = nowSec();
        String resolvedId = existing != null ? existing.id : idForTarget(dialogId, msgId);
        int createdAt = existing != null ? existing.createdAt : now;
        boolean pinned = existing != null && existing.pinned;
        String resolvedPeerTitle = !TextUtils.isEmpty(peerTitle) ? peerTitle : (existing != null ? existing.peerTitle : "");
        String resolvedAuthorName = !TextUtils.isEmpty(authorName) ? authorName : (existing != null ? existing.authorName : "");
        String resolvedQuote = secretChat
                ? ""
                : (!TextUtils.isEmpty(quotePreview) ? sanitizePreview(quotePreview) : (existing != null ? existing.quotePreview : ""));
        DossierNote updated = new DossierNote(
                resolvedId,
                prefKey,
                dialogId,
                msgId,
                trimmed,
                createdAt,
                now,
                spoiler,
                pinned,
                resolvedPeerTitle,
                resolvedAuthorName,
                resolvedQuote,
                secretChat
        );
        persistNote(updated);
        return updated;
    }

    public static DossierNote createStandaloneNote(String noteText, boolean spoiler) {
        ensureInit();
        String trimmed = noteText != null ? noteText.trim() : "";
        if (TextUtils.isEmpty(trimmed)) {
            return null;
        }
        int now = nowSec();
        String id = "quick_" + System.currentTimeMillis() + "_" + quickSeq.getAndIncrement();
        String prefKey = PREFIX_DOSSIER + id;
        DossierNote note = new DossierNote(id, prefKey, 0L, 0, trimmed, now, now, spoiler, false, "", "", "", false);
        persistNote(note);
        return note;
    }

    public static DossierNote updateNoteById(String id, String newText, boolean spoiler) {
        ensureInit();
        if (TextUtils.isEmpty(id)) {
            return null;
        }
        DossierNote existing = notesById.get(id);
        if (existing == null) {
            return null;
        }
        String trimmed = newText != null ? newText.trim() : "";
        if (TextUtils.isEmpty(trimmed)) {
            deleteNoteById(id);
            return null;
        }
        DossierNote updated = new DossierNote(
                existing.id,
                existing.prefKey,
                existing.dialogId,
                existing.msgId,
                trimmed,
                existing.createdAt,
                nowSec(),
                spoiler,
                existing.pinned,
                existing.peerTitle,
                existing.authorName,
                existing.quotePreview,
                existing.secretChat
        );
        persistNote(updated);
        return updated;
    }

    public static DossierNote toggleNoteSpoiler(String id) {
        ensureInit();
        DossierNote existing = TextUtils.isEmpty(id) ? null : notesById.get(id);
        if (existing == null) {
            return null;
        }
        DossierNote updated = new DossierNote(
                existing.id,
                existing.prefKey,
                existing.dialogId,
                existing.msgId,
                existing.text,
                existing.createdAt,
                existing.updatedAt,
                !existing.spoiler,
                existing.pinned,
                existing.peerTitle,
                existing.authorName,
                existing.quotePreview,
                existing.secretChat
        );
        persistNote(updated);
        return updated;
    }

    public static DossierNote toggleNotePinned(String id) {
        ensureInit();
        DossierNote existing = TextUtils.isEmpty(id) ? null : notesById.get(id);
        if (existing == null) {
            return null;
        }
        DossierNote updated = new DossierNote(
                existing.id,
                existing.prefKey,
                existing.dialogId,
                existing.msgId,
                existing.text,
                existing.createdAt,
                existing.updatedAt,
                existing.spoiler,
                !existing.pinned,
                existing.peerTitle,
                existing.authorName,
                existing.quotePreview,
                existing.secretChat
        );
        persistNote(updated);
        return updated;
    }

    public static DossierNote deleteNoteById(String id) {
        ensureInit();
        if (TextUtils.isEmpty(id)) {
            return null;
        }
        DossierNote removed = notesById.remove(id);
        if (removed != null) {
            notesByPrefKey.remove(removed.prefKey);
            if (ApplicationLoader.applicationContext != null) {
                getPrefs().edit().remove(removed.prefKey).apply();
            }
            notifyNotesUpdated();
        }
        return removed;
    }

    public static void restoreNote(DossierNote note) {
        if (note == null || TextUtils.isEmpty(note.text)) {
            return;
        }
        ensureInit();
        persistNote(note);
    }

    public static boolean hasDossierNote(long dialogId, int msgId) {
        DossierNote record = getDossierNoteRecord(dialogId, msgId);
        return record != null && !TextUtils.isEmpty(record.text);
    }

    public static int getNotesCount() {
        ensureInit();
        return notesById.size();
    }

    public static int getEditHistoryMessagesCount() {
        ensureInit();
        if (ApplicationLoader.applicationContext == null) {
            return editCache.size();
        }
        Map<String, ?> all = getPrefs().getAll();
        if (all == null || all.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String key : all.keySet()) {
            if (key != null && key.startsWith(PREFIX_EDIT)) {
                count++;
            }
        }
        return count;
    }

    public static List<DossierNote> getAllNotes() {
        return searchNotes(null, FILTER_ALL);
    }

    public static List<DossierNote> searchNotes(String query, int filter) {
        ensureInit();
        String normalizedQuery = query != null ? query.trim().toLowerCase() : "";
        boolean hasQuery = !TextUtils.isEmpty(normalizedQuery);
        List<DossierNote> result = new ArrayList<>();
        for (DossierNote note : notesById.values()) {
            if (filter == FILTER_MESSAGES && note.isStandaloneNote()) {
                continue;
            } else if (filter == FILTER_STANDALONE && !note.isStandaloneNote()) {
                continue;
            } else if (filter == FILTER_SPOILER && !note.spoiler) {
                continue;
            }
            if (hasQuery) {
                boolean matches = note.text.toLowerCase().contains(normalizedQuery)
                        || note.peerTitle.toLowerCase().contains(normalizedQuery)
                        || note.authorName.toLowerCase().contains(normalizedQuery)
                        || (!note.secretChat && note.quotePreview.toLowerCase().contains(normalizedQuery));
                if (!matches) {
                    continue;
                }
            }
            result.add(note);
        }
        // Newest first so reverseLayout RecyclerView shows latest notes at the bottom like a Telegram chat.
        Collections.sort(result, new Comparator<DossierNote>() {
            @Override
            public int compare(DossierNote a, DossierNote b) {
                int byTime = Integer.compare(b.createdAt, a.createdAt);
                if (byTime != 0) {
                    return byTime;
                }
                return b.id.compareTo(a.id);
            }
        });
        return result;
    }

    public static void clearEditHistory() {
        ensureInit();
        editCache.clear();
        if (ApplicationLoader.applicationContext != null) {
            SharedPreferences prefs = getPrefs();
            Map<String, ?> all = prefs.getAll();
            if (all != null && !all.isEmpty()) {
                SharedPreferences.Editor editor = prefs.edit();
                for (String key : all.keySet()) {
                    if (key != null && key.startsWith(PREFIX_EDIT)) {
                        editor.remove(key);
                    }
                }
                editor.apply();
            }
        }
    }

    public static void clearDossierNotes() {
        ensureInit();
        notesById.clear();
        notesByPrefKey.clear();
        if (ApplicationLoader.applicationContext != null) {
            SharedPreferences prefs = getPrefs();
            Map<String, ?> all = prefs.getAll();
            if (all != null && !all.isEmpty()) {
                SharedPreferences.Editor editor = prefs.edit();
                for (String key : all.keySet()) {
                    if (key != null && key.startsWith(PREFIX_DOSSIER)) {
                        editor.remove(key);
                    }
                }
                editor.apply();
            }
        }
        notifyNotesUpdated();
    }

    public static void clearAllVaultData() {
        clearEditHistory();
        clearDossierNotes();
    }
}
