package org.telegram.messenger.mirrly;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class MirrlyVaultStorage {

    private static final String PREFS_NAME = "mirrly_vault_store";
    private static final String PREFIX_EDIT = "edit_";
    private static final String PREFIX_DOSSIER = "dossier_";
    private static final String PREFIX_DOSSIER_CHAT = "dossier_chat_";
    private static final String PREFIX_DOSSIER_QUICK = "dossier_quick_";
    private static final int MAX_REVISIONS_PER_MSG = 20;
    private static final int NOTE_SCHEMA_VERSION = 2;
    private static final int MAX_QUOTE_PREVIEW_LEN = 240;

    public static final int FILTER_ALL = 0;
    public static final int FILTER_MESSAGES = 1;
    public static final int FILTER_STANDALONE = 2;
    public static final int FILTER_SPOILER = 3;
    public static final int FILTER_MEDIA = 4;
    public static final int FILTER_FILES = 5;
    public static final int FILTER_VOICE = 6;

    public static final String MEDIA_NONE = "";
    public static final String MEDIA_PHOTO = "photo";
    public static final String MEDIA_VIDEO = "video";
    public static final String MEDIA_ROUND = "round";
    public static final String MEDIA_VOICE = "voice";
    public static final String MEDIA_AUDIO = "audio";
    public static final String MEDIA_DOCUMENT = "document";
    public static final String MEDIA_STICKER = "sticker";
    public static final String MEDIA_GIF = "gif";
    public static final String MEDIA_GEO = "geo";
    public static final String MEDIA_CONTACT = "contact";
    public static final String MEDIA_POLL = "poll";

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

        public final String mediaKind;
        public final String localMediaPath;
        public final String localThumbPath;
        public final String serializedMediaBase64;
        public final String serializedEntitiesBase64;
        public final String fileName;
        public final String mimeType;
        public final int mediaDuration;
        public final int mediaSize;
        public final int mediaWidth;
        public final int mediaHeight;
        public final long groupedId;

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
            this(
                    id, prefKey, dialogId, msgId, text, createdAt, updatedAt, spoiler, pinned,
                    peerTitle, authorName, quotePreview, secretChat,
                    MEDIA_NONE, "", "", "", "", "", "", 0, 0, 0, 0, 0L
            );
        }

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
                boolean secretChat,
                String mediaKind,
                String localMediaPath,
                String localThumbPath,
                String serializedMediaBase64,
                String serializedEntitiesBase64,
                String fileName,
                String mimeType,
                int mediaDuration,
                int mediaSize,
                int mediaWidth,
                int mediaHeight,
                long groupedId
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
            this.mediaKind = mediaKind != null ? mediaKind : MEDIA_NONE;
            this.localMediaPath = localMediaPath != null ? localMediaPath : "";
            this.localThumbPath = localThumbPath != null ? localThumbPath : "";
            this.serializedMediaBase64 = serializedMediaBase64 != null ? serializedMediaBase64 : "";
            this.serializedEntitiesBase64 = serializedEntitiesBase64 != null ? serializedEntitiesBase64 : "";
            this.fileName = fileName != null ? fileName : "";
            this.mimeType = mimeType != null ? mimeType : "";
            this.mediaDuration = Math.max(0, mediaDuration);
            this.mediaSize = Math.max(0, mediaSize);
            this.mediaWidth = Math.max(0, mediaWidth);
            this.mediaHeight = Math.max(0, mediaHeight);
            this.groupedId = groupedId;
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

        public boolean hasMedia() {
            return !TextUtils.isEmpty(mediaKind);
        }

        public boolean hasContent() {
            return !TextUtils.isEmpty(text) || hasMedia();
        }

        public boolean isMediaFilterGroup() {
            return MEDIA_PHOTO.equals(mediaKind)
                    || MEDIA_VIDEO.equals(mediaKind)
                    || MEDIA_STICKER.equals(mediaKind)
                    || MEDIA_GIF.equals(mediaKind)
                    || MEDIA_GEO.equals(mediaKind)
                    || MEDIA_CONTACT.equals(mediaKind)
                    || MEDIA_POLL.equals(mediaKind);
        }

        public boolean isFilesFilterGroup() {
            return MEDIA_DOCUMENT.equals(mediaKind) || MEDIA_AUDIO.equals(mediaKind);
        }

        public boolean isVoiceFilterGroup() {
            return MEDIA_VOICE.equals(mediaKind) || MEDIA_ROUND.equals(mediaKind);
        }

        @Nullable
        public TLRPC.MessageMedia decodeMedia() {
            return MirrlyVaultStorage.decodeMedia(serializedMediaBase64);
        }

        public ArrayList<TLRPC.MessageEntity> decodeEntities() {
            return MirrlyVaultStorage.decodeEntities(serializedEntitiesBase64);
        }

        public DossierNote withUpdatedContent(String newText, String newEntitiesBase64, boolean newSpoiler, boolean touchTimestamp) {
            return new DossierNote(
                    id, prefKey, dialogId, msgId, newText,
                    createdAt, touchTimestamp ? nowSec() : updatedAt,
                    newSpoiler, pinned, peerTitle, authorName, quotePreview, secretChat,
                    mediaKind, localMediaPath, localThumbPath, serializedMediaBase64,
                    newEntitiesBase64 != null ? newEntitiesBase64 : serializedEntitiesBase64,
                    fileName, mimeType, mediaDuration, mediaSize, mediaWidth, mediaHeight, groupedId
            );
        }

        public DossierNote withPinned(boolean newPinned) {
            return new DossierNote(
                    id, prefKey, dialogId, msgId, text,
                    createdAt, updatedAt, spoiler, newPinned, peerTitle, authorName, quotePreview, secretChat,
                    mediaKind, localMediaPath, localThumbPath, serializedMediaBase64, serializedEntitiesBase64,
                    fileName, mimeType, mediaDuration, mediaSize, mediaWidth, mediaHeight, groupedId
            );
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
            if (parsed == null || !parsed.hasContent()) {
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
            return obj.optInt("mirrly_note_v", 0) >= 1;
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
                String mediaKind = obj.optString("media_kind", MEDIA_NONE);
                String localMediaPath = obj.optString("local_media_path", "");
                String localThumbPath = obj.optString("local_thumb_path", "");
                String serializedMedia = obj.optString("media_tl_b64", "");
                String serializedEntities = obj.optString("entities_tl_b64", "");
                String fileName = obj.optString("file_name", "");
                String mimeType = obj.optString("mime_type", "");
                int mediaDuration = obj.optInt("media_duration", 0);
                int mediaSize = obj.optInt("media_size", 0);
                int mediaWidth = obj.optInt("media_w", 0);
                int mediaHeight = obj.optInt("media_h", 0);
                long groupedId = obj.optLong("grouped_id", 0L);
                if (TextUtils.isEmpty(id)) {
                    id = dialogId == 0 ? prefKey.substring(PREFIX_DOSSIER.length()) : idForTarget(dialogId, msgId);
                }
                return new DossierNote(
                        id, prefKey, dialogId, msgId, text, createdAt, updatedAt, spoiler, pinned,
                        peerTitle, authorName, quotePreview, secretChat,
                        mediaKind, localMediaPath, localThumbPath, serializedMedia, serializedEntities,
                        fileName, mimeType, mediaDuration, mediaSize, mediaWidth, mediaHeight, groupedId
                );
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
            if (!TextUtils.isEmpty(note.mediaKind)) {
                obj.put("media_kind", note.mediaKind);
            }
            if (!TextUtils.isEmpty(note.localMediaPath)) {
                obj.put("local_media_path", note.localMediaPath);
            }
            if (!TextUtils.isEmpty(note.localThumbPath)) {
                obj.put("local_thumb_path", note.localThumbPath);
            }
            if (!TextUtils.isEmpty(note.serializedMediaBase64)) {
                obj.put("media_tl_b64", note.serializedMediaBase64);
            }
            if (!TextUtils.isEmpty(note.serializedEntitiesBase64)) {
                obj.put("entities_tl_b64", note.serializedEntitiesBase64);
            }
            if (!TextUtils.isEmpty(note.fileName)) {
                obj.put("file_name", note.fileName);
            }
            if (!TextUtils.isEmpty(note.mimeType)) {
                obj.put("mime_type", note.mimeType);
            }
            if (note.mediaDuration > 0) {
                obj.put("media_duration", note.mediaDuration);
            }
            if (note.mediaSize > 0) {
                obj.put("media_size", note.mediaSize);
            }
            if (note.mediaWidth > 0) {
                obj.put("media_w", note.mediaWidth);
            }
            if (note.mediaHeight > 0) {
                obj.put("media_h", note.mediaHeight);
            }
            if (note.groupedId != 0L) {
                obj.put("grouped_id", note.groupedId);
            }
            return obj.toString();
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    public static File getNotesMediaDir() {
        File root = ApplicationLoader.getFilesDirFixed();
        File dir = new File(root, "mirrly_notes_media");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static String copyFileToNotesMedia(
            @Nullable String srcPath,
            @Nullable Uri srcUri,
            String prefix,
            @Nullable String fallbackExt
    ) {
        try {
            File dir = getNotesMediaDir();
            String ext = fallbackExt != null ? fallbackExt : "";
            if (!TextUtils.isEmpty(srcPath)) {
                int dot = srcPath.lastIndexOf('.');
                if (dot >= 0 && dot < srcPath.length() - 1) {
                    String candidate = srcPath.substring(dot + 1).replaceAll("[^A-Za-z0-9]", "");
                    if (!TextUtils.isEmpty(candidate) && candidate.length() <= 8) {
                        ext = candidate;
                    }
                }
            }
            String safeExt = TextUtils.isEmpty(ext) ? "dat" : ext;
            File dst = new File(dir, prefix + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + safeExt);
            InputStream in = null;
            if (!TextUtils.isEmpty(srcPath)) {
                File srcFile = new File(srcPath);
                if (srcFile.exists() && srcFile.isFile()) {
                    if (srcFile.getParentFile() != null && srcFile.getParentFile().equals(dir)) {
                        return srcFile.getAbsolutePath();
                    }
                    in = new FileInputStream(srcFile);
                }
            }
            if (in == null && srcUri != null && ApplicationLoader.applicationContext != null) {
                ContentResolver cr = ApplicationLoader.applicationContext.getContentResolver();
                if (cr != null) {
                    in = cr.openInputStream(srcUri);
                }
            }
            if (in == null) {
                return "";
            }
            try (InputStream streamIn = in; FileOutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[16384];
                int read;
                while ((read = streamIn.read(buf)) != -1) {
                    out.write(buf, 0, read);
                }
                out.flush();
            }
            return dst.getAbsolutePath();
        } catch (Throwable t) {
            FileLog.e(t);
            return "";
        }
    }

    public static String saveBitmapToNotesThumb(@Nullable Bitmap bitmap, String prefix) {
        if (bitmap == null || bitmap.isRecycled()) {
            return "";
        }
        try {
            File dir = getNotesMediaDir();
            File dst = new File(dir, prefix + "_thumb_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6) + ".jpg");
            try (FileOutputStream out = new FileOutputStream(dst)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out);
                out.flush();
            }
            return dst.getAbsolutePath();
        } catch (Throwable t) {
            FileLog.e(t);
            return "";
        }
    }

    public static String encodeMedia(@Nullable TLRPC.MessageMedia media) {
        if (media == null) {
            return "";
        }
        SerializedData data = new SerializedData();
        try {
            media.serializeToStream(data);
            return Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP);
        } catch (Throwable t) {
            FileLog.e(t);
            return "";
        } finally {
            data.cleanup();
        }
    }

    @Nullable
    public static TLRPC.MessageMedia decodeMedia(@Nullable String base64) {
        if (TextUtils.isEmpty(base64)) {
            return null;
        }
        SerializedData data = null;
        try {
            byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);
            data = new SerializedData(bytes);
            return TLRPC.MessageMedia.TLdeserialize(data, false);
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        } finally {
            if (data != null) {
                data.cleanup();
            }
        }
    }

    public static String encodeEntities(@Nullable ArrayList<TLRPC.MessageEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return "";
        }
        SerializedData data = new SerializedData();
        try {
            data.writeInt32(entities.size());
            for (int i = 0; i < entities.size(); i++) {
                entities.get(i).serializeToStream(data);
            }
            return Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP);
        } catch (Throwable t) {
            FileLog.e(t);
            return "";
        } finally {
            data.cleanup();
        }
    }

    public static ArrayList<TLRPC.MessageEntity> decodeEntities(@Nullable String base64) {
        ArrayList<TLRPC.MessageEntity> out = new ArrayList<>();
        if (TextUtils.isEmpty(base64)) {
            return out;
        }
        SerializedData data = null;
        try {
            byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);
            data = new SerializedData(bytes);
            int count = data.readInt32(false);
            for (int i = 0; i < count; i++) {
                int constructor = data.readInt32(false);
                TLRPC.MessageEntity entity = TLRPC.MessageEntity.TLdeserialize(data, constructor, false);
                if (entity != null) {
                    out.add(entity);
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        } finally {
            if (data != null) {
                data.cleanup();
            }
        }
        return out;
    }

    public static void cleanupOrphanedMediaFiles() {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                File dir = getNotesMediaDir();
                File[] files = dir.listFiles();
                if (files == null || files.length == 0) {
                    return;
                }
                ensureInit();
                HashSet<String> activePaths = new HashSet<>();
                for (DossierNote note : notesById.values()) {
                    if (!TextUtils.isEmpty(note.localMediaPath)) {
                        activePaths.add(note.localMediaPath);
                    }
                    if (!TextUtils.isEmpty(note.localThumbPath)) {
                        activePaths.add(note.localThumbPath);
                    }
                }
                long nowMs = System.currentTimeMillis();
                for (File f : files) {
                    if (f != null && f.isFile() && !activePaths.contains(f.getAbsolutePath())) {
                        // Spare files written in the last 30s so active Undo or in-flight capture is never disturbed.
                        if (nowMs - f.lastModified() > 30_000L) {
                            f.delete();
                        }
                    }
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
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
                secretChat,
                existing != null ? existing.mediaKind : MEDIA_NONE,
                existing != null ? existing.localMediaPath : "",
                existing != null ? existing.localThumbPath : "",
                existing != null ? existing.serializedMediaBase64 : "",
                existing != null ? existing.serializedEntitiesBase64 : "",
                existing != null ? existing.fileName : "",
                existing != null ? existing.mimeType : "",
                existing != null ? existing.mediaDuration : 0,
                existing != null ? existing.mediaSize : 0,
                existing != null ? existing.mediaWidth : 0,
                existing != null ? existing.mediaHeight : 0,
                existing != null ? existing.groupedId : 0L
        );
        persistNote(updated);
        return updated;
    }

    public static DossierNote createStandaloneNote(String noteText, boolean spoiler) {
        return createRichStandaloneNote(
                noteText, "", spoiler,
                MEDIA_NONE, "", "", "", "", "", 0, 0, 0, 0, 0L
        );
    }

    public static DossierNote createRichStandaloneNote(
            @Nullable String noteText,
            @Nullable String serializedEntitiesBase64,
            boolean spoiler,
            @Nullable String mediaKind,
            @Nullable String localMediaPath,
            @Nullable String localThumbPath,
            @Nullable String serializedMediaBase64,
            @Nullable String fileName,
            @Nullable String mimeType,
            int mediaDuration,
            int mediaSize,
            int mediaWidth,
            int mediaHeight,
            long groupedId
    ) {
        ensureInit();
        String trimmed = noteText != null ? noteText.trim() : "";
        String resolvedKind = mediaKind != null ? mediaKind : MEDIA_NONE;
        if (TextUtils.isEmpty(trimmed) && TextUtils.isEmpty(resolvedKind)) {
            return null;
        }
        int now = nowSec();
        String id = "quick_" + System.currentTimeMillis() + "_" + quickSeq.getAndIncrement();
        String prefKey = PREFIX_DOSSIER + id;
        DossierNote note = new DossierNote(
                id, prefKey, 0L, 0, trimmed, now, now, spoiler, false, "", "", "", false,
                resolvedKind,
                localMediaPath != null ? localMediaPath : "",
                localThumbPath != null ? localThumbPath : "",
                serializedMediaBase64 != null ? serializedMediaBase64 : "",
                serializedEntitiesBase64 != null ? serializedEntitiesBase64 : "",
                fileName != null ? fileName : "",
                mimeType != null ? mimeType : "",
                mediaDuration, mediaSize, mediaWidth, mediaHeight, groupedId
        );
        persistNote(note);
        return note;
    }

    public static DossierNote updateNoteById(String id, String newText, boolean spoiler) {
        return updateNoteById(id, newText, null, spoiler);
    }

    public static DossierNote updateNoteById(String id, String newText, @Nullable String newEntitiesBase64, boolean spoiler) {
        ensureInit();
        if (TextUtils.isEmpty(id)) {
            return null;
        }
        DossierNote existing = notesById.get(id);
        if (existing == null) {
            return null;
        }
        String trimmed = newText != null ? newText.trim() : "";
        if (TextUtils.isEmpty(trimmed) && !existing.hasMedia()) {
            deleteNoteById(id);
            return null;
        }
        DossierNote updated = existing.withUpdatedContent(trimmed, newEntitiesBase64, spoiler, true);
        persistNote(updated);
        return updated;
    }

    public static DossierNote toggleNoteSpoiler(String id) {
        ensureInit();
        DossierNote existing = TextUtils.isEmpty(id) ? null : notesById.get(id);
        if (existing == null) {
            return null;
        }
        DossierNote updated = existing.withUpdatedContent(existing.text, existing.serializedEntitiesBase64, !existing.spoiler, false);
        persistNote(updated);
        return updated;
    }

    public static DossierNote toggleNotePinned(String id) {
        ensureInit();
        DossierNote existing = TextUtils.isEmpty(id) ? null : notesById.get(id);
        if (existing == null) {
            return null;
        }
        DossierNote updated = existing.withPinned(!existing.pinned);
        persistNote(updated);
        return updated;
    }

    public static void deleteNotesByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        ensureInit();
        SharedPreferences.Editor editor = ApplicationLoader.applicationContext != null ? getPrefs().edit() : null;
        boolean changed = false;
        for (int i = 0; i < ids.size(); i++) {
            DossierNote removed = notesById.remove(ids.get(i));
            if (removed != null) {
                notesByPrefKey.remove(removed.prefKey);
                if (editor != null) {
                    editor.remove(removed.prefKey);
                }
                changed = true;
            }
        }
        if (editor != null && changed) {
            editor.apply();
        }
        if (changed) {
            notifyNotesUpdated();
        }
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
        if (note == null || !note.hasContent()) {
            return;
        }
        ensureInit();
        persistNote(note);
    }

    public static boolean hasDossierNote(long dialogId, int msgId) {
        DossierNote record = getDossierNoteRecord(dialogId, msgId);
        return record != null && record.hasContent();
    }

    @Nullable
    public static DossierNote getLatestPinnedNote() {
        ensureInit();
        DossierNote best = null;
        for (DossierNote note : notesById.values()) {
            if (note.pinned && note.hasContent()) {
                if (best == null || note.updatedAt > best.updatedAt || (note.updatedAt == best.updatedAt && note.createdAt > best.createdAt)) {
                    best = note;
                }
            }
        }
        return best;
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
        String normalizedQuery = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
        boolean hasQuery = !TextUtils.isEmpty(normalizedQuery);
        List<DossierNote> result = new ArrayList<>();
        for (DossierNote note : notesById.values()) {
            if (filter == FILTER_MESSAGES && note.isStandaloneNote()) {
                continue;
            } else if (filter == FILTER_STANDALONE && (!note.isStandaloneNote() || note.hasMedia())) {
                continue;
            } else if (filter == FILTER_SPOILER && !note.spoiler) {
                continue;
            } else if (filter == FILTER_MEDIA && !note.isMediaFilterGroup()) {
                continue;
            } else if (filter == FILTER_FILES && !note.isFilesFilterGroup()) {
                continue;
            } else if (filter == FILTER_VOICE && !note.isVoiceFilterGroup()) {
                continue;
            }
            if (hasQuery) {
                boolean matches = note.text.toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        || note.peerTitle.toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        || note.authorName.toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        || note.fileName.toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        || note.mediaKind.toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        || (!note.secretChat && note.quotePreview.toLowerCase(Locale.ROOT).contains(normalizedQuery));
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
        cleanupAllMediaFilesNow();
        notifyNotesUpdated();
    }

    private static void cleanupAllMediaFilesNow() {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                File dir = getNotesMediaDir();
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f != null && f.isFile()) {
                            f.delete();
                        }
                    }
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }

    public static void clearAllVaultData() {
        clearEditHistory();
        clearDossierNotes();
    }
}
