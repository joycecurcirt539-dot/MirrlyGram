package org.telegram.messenger.mirrly;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.secretmedia.EncryptedFileInputStream;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-account persistent archive and interceptor state for deleted, temporary, and one-time messages.
 * Stores serialized TLRPC payloads in a dedicated SQLite WAL database and rescues media files
 * (including encrypted view-once/TTL caches) into {@code MirrlyGram/saved attachments}.
 */
public final class MirrlyDeletedStorage {

    public static final int FILTER_ALL = 0;
    public static final int FILTER_TEXT = 1;
    public static final int FILTER_MEDIA = 2;
    public static final int FILTER_VOICE_ROUND = 3;
    public static final int FILTER_FILES = 4;
    public static final int FILTER_EPHEMERAL = 5;
    public static final int FILTER_BY_ME = 6;

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

    private static final String TABLE_DELETED = "deleted_messages";
    private static final int DB_VERSION = 1;
    private static final int MAX_CACHED_KEYS = 20000;

    private static final MirrlyDeletedStorage[] INSTANCES = new MirrlyDeletedStorage[UserConfig.MAX_ACCOUNT_COUNT];

    public static MirrlyDeletedStorage getInstance(int account) {
        int safeAccount = account >= 0 && account < INSTANCES.length ? account : 0;
        MirrlyDeletedStorage local = INSTANCES[safeAccount];
        if (local == null) {
            synchronized (MirrlyDeletedStorage.class) {
                local = INSTANCES[safeAccount];
                if (local == null) {
                    local = new MirrlyDeletedStorage(safeAccount);
                    INSTANCES[safeAccount] = local;
                }
            }
        }
        return local;
    }

    public static final class DeletedRecord {
        public final String uid;
        public final long dialogId;
        public final int msgId;
        public final long groupedId;
        public final long senderId;
        public final String peerTitle;
        public final String senderName;
        public final String text;
        public final int sendDate;
        public final int deletedAt;
        public final boolean deletedByMe;
        public final boolean ephemeral;
        public final String mediaKind;
        public final String fileName;
        public final String mimeType;
        public final int mediaDuration;
        public final long mediaSize;
        public final int mediaWidth;
        public final int mediaHeight;
        public final String localMediaPath;
        public final String localThumbPath;
        public final byte[] msgTlBlob;

        public DeletedRecord(
                String uid,
                long dialogId,
                int msgId,
                long groupedId,
                long senderId,
                String peerTitle,
                String senderName,
                String text,
                int sendDate,
                int deletedAt,
                boolean deletedByMe,
                boolean ephemeral,
                String mediaKind,
                String fileName,
                String mimeType,
                int mediaDuration,
                long mediaSize,
                int mediaWidth,
                int mediaHeight,
                String localMediaPath,
                String localThumbPath,
                byte[] msgTlBlob
        ) {
            this.uid = uid;
            this.dialogId = dialogId;
            this.msgId = msgId;
            this.groupedId = groupedId;
            this.senderId = senderId;
            this.peerTitle = peerTitle != null ? peerTitle : "";
            this.senderName = senderName != null ? senderName : "";
            this.text = text != null ? text : "";
            this.sendDate = sendDate;
            this.deletedAt = deletedAt;
            this.deletedByMe = deletedByMe;
            this.ephemeral = ephemeral;
            this.mediaKind = mediaKind != null ? mediaKind : MEDIA_NONE;
            this.fileName = fileName != null ? fileName : "";
            this.mimeType = mimeType != null ? mimeType : "";
            this.mediaDuration = Math.max(0, mediaDuration);
            this.mediaSize = Math.max(0L, mediaSize);
            this.mediaWidth = Math.max(0, mediaWidth);
            this.mediaHeight = Math.max(0, mediaHeight);
            this.localMediaPath = localMediaPath != null ? localMediaPath : "";
            this.localThumbPath = localThumbPath != null ? localThumbPath : "";
            this.msgTlBlob = msgTlBlob;
        }

        public boolean hasMedia() {
            return !TextUtils.isEmpty(mediaKind);
        }

        public boolean hasLocalFile() {
            if (TextUtils.isEmpty(localMediaPath)) {
                return false;
            }
            File f = new File(localMediaPath);
            return f.exists() && f.length() > 0;
        }

        public boolean isVisualMediaFilterGroup() {
            return MEDIA_PHOTO.equals(mediaKind)
                    || MEDIA_VIDEO.equals(mediaKind)
                    || MEDIA_STICKER.equals(mediaKind)
                    || MEDIA_GIF.equals(mediaKind)
                    || MEDIA_GEO.equals(mediaKind)
                    || MEDIA_CONTACT.equals(mediaKind)
                    || MEDIA_POLL.equals(mediaKind);
        }

        public boolean isVoiceRoundFilterGroup() {
            return MEDIA_VOICE.equals(mediaKind) || MEDIA_ROUND.equals(mediaKind);
        }

        public boolean isFilesFilterGroup() {
            return MEDIA_DOCUMENT.equals(mediaKind) || MEDIA_AUDIO.equals(mediaKind);
        }

        @Nullable
        public TLRPC.Message decodeMessage(int account) {
            if (msgTlBlob == null || msgTlBlob.length == 0) {
                return null;
            }
            SerializedData data = null;
            try {
                data = new SerializedData(msgTlBlob);
                int constructor = data.readInt32(false);
                TLRPC.Message msg = TLRPC.Message.TLdeserialize(data, constructor, false);
                if (msg != null) {
                    msg.dialog_id = dialogId;
                    msg.id = msgId;
                    if (msg.date == 0) {
                        msg.date = sendDate;
                    }
                    msg.mirrlyDeleted = true;
                    msg.mirrlyDeletedAt = deletedAt;
                    msg.mirrlyDeletedByMe = deletedByMe;
                    msg.ttl = 0;
                    msg.destroyTime = 0;
                    msg.destroyTimeMillis = 0;
                    if (msg.media != null) {
                        msg.media.ttl_seconds = 0;
                    }
                    if (!TextUtils.isEmpty(localMediaPath) && new File(localMediaPath).exists()) {
                        msg.attachPath = localMediaPath;
                        if (msg.media instanceof TLRPC.TL_messageMediaPhoto &&
                                (msg.media.photo == null || msg.media.photo instanceof TLRPC.TL_photoEmpty)) {
                            TLRPC.TL_photo regenerated = SendMessagesHelper.getInstance(account).generatePhotoSizes(localMediaPath, null);
                            if (regenerated != null) {
                                msg.media.photo = regenerated;
                            }
                        }
                    }
                }
                return msg;
            } catch (Throwable t) {
                FileLog.e(t);
                return null;
            } finally {
                if (data != null) {
                    data.cleanup();
                }
            }
        }
    }

    private final int currentAccount;
    private final Object dbLock = new Object();
    private final ConcurrentHashMap<String, Boolean> deletedIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> precachedMediaPaths = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> forcePhysicalDeleteKeys = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> pendingByMeKeepKeys = new ConcurrentHashMap<>();
    private final AtomicInteger totalCount = new AtomicInteger(0);

    @Nullable
    private DbHelper dbHelper;
    private volatile boolean warmedUp = false;
    private volatile boolean warmupQueued = false;

    private MirrlyDeletedStorage(int account) {
        this.currentAccount = account;
    }

    private static final class DbHelper extends SQLiteOpenHelper {
        DbHelper(Context context, int account) {
            super(context, "mirrly_deleted_" + account + ".db", null, DB_VERSION);
            setWriteAheadLoggingEnabled(true);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_DELETED + " ("
                    + "uid TEXT PRIMARY KEY,"
                    + "dialog_id INTEGER NOT NULL,"
                    + "msg_id INTEGER NOT NULL,"
                    + "grouped_id INTEGER NOT NULL DEFAULT 0,"
                    + "sender_id INTEGER NOT NULL DEFAULT 0,"
                    + "peer_title TEXT,"
                    + "sender_name TEXT,"
                    + "msg_text TEXT,"
                    + "send_date INTEGER NOT NULL DEFAULT 0,"
                    + "deleted_at INTEGER NOT NULL DEFAULT 0,"
                    + "deleted_by_me INTEGER NOT NULL DEFAULT 0,"
                    + "is_ephemeral INTEGER NOT NULL DEFAULT 0,"
                    + "media_kind TEXT,"
                    + "file_name TEXT,"
                    + "mime_type TEXT,"
                    + "media_duration INTEGER NOT NULL DEFAULT 0,"
                    + "media_size INTEGER NOT NULL DEFAULT 0,"
                    + "media_w INTEGER NOT NULL DEFAULT 0,"
                    + "media_h INTEGER NOT NULL DEFAULT 0,"
                    + "local_media_path TEXT,"
                    + "local_thumb_path TEXT,"
                    + "msg_tl_blob BLOB"
                    + ")");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_deleted_at ON " + TABLE_DELETED + " (deleted_at DESC, msg_id DESC)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_dialog_msg ON " + TABLE_DELETED + " (dialog_id, msg_id)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            onCreate(db);
        }
    }

    @Nullable
    private SQLiteDatabase getDbLocked(boolean writable) {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) {
            return null;
        }
        if (dbHelper == null) {
            dbHelper = new DbHelper(ctx, currentAccount);
        }
        try {
            return writable ? dbHelper.getWritableDatabase() : dbHelper.getReadableDatabase();
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    public static void warmupAllAccountsAsync() {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            if (UserConfig.getInstance(i).isClientActivated()) {
                getInstance(i).warmupAsync();
            }
        }
        MirrlyDeletedChatsStorage.warmupAllAccountsAsync();
    }

    public void warmupAsync() {
        if (warmedUp || warmupQueued || ApplicationLoader.applicationContext == null) {
            return;
        }
        warmupQueued = true;
        Utilities.globalQueue.postRunnable(this::ensureLoaded);
    }

    private void ensureLoaded() {
        if (warmedUp) {
            return;
        }
        synchronized (dbLock) {
            if (warmedUp) {
                return;
            }
            SQLiteDatabase db = getDbLocked(false);
            if (db == null) {
                return;
            }
            int count = 0;
            try (Cursor c = db.rawQuery("SELECT uid FROM " + TABLE_DELETED + " ORDER BY deleted_at DESC LIMIT " + MAX_CACHED_KEYS, null)) {
                while (c.moveToNext()) {
                    String uid = c.getString(0);
                    if (!TextUtils.isEmpty(uid)) {
                        deletedIndex.put(uid, Boolean.TRUE);
                    }
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
            try (Cursor c2 = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_DELETED, null)) {
                if (c2.moveToFirst()) {
                    count = c2.getInt(0);
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
            totalCount.set(count);
            warmedUp = true;
        }
    }

    public static String buildUid(long dialogId, int msgId) {
        return dialogId + "_" + msgId;
    }

    public int getCount() {
        ensureLoaded();
        return totalCount.get();
    }

    public boolean hasDeletedRecord(long dialogId, int msgId) {
        if (dialogId == 0 || msgId == 0) {
            return false;
        }
        if (!warmedUp) {
            warmupAsync();
            return false;
        }
        return deletedIndex.containsKey(buildUid(dialogId, msgId));
    }

    /**
     * Returns the {@code MirrlyGram/saved attachments} directory inside app media/files storage
     * with a {@code .nomedia} file so deleted/one-time media stays invisible to system Gallery.
     */
    @NonNull
    public static File getSavedAttachmentsDir() {
        Context ctx = ApplicationLoader.applicationContext;
        File base = null;
        if (ctx != null) {
            base = ctx.getExternalFilesDir(null);
        }
        if (base == null) {
            base = ApplicationLoader.getFilesDirFixed();
        }
        File mirrlyRoot = new File(base, "MirrlyGram");
        if (!mirrlyRoot.exists()) {
            mirrlyRoot.mkdirs();
        }
        File dir = new File(mirrlyRoot, "saved attachments");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        AndroidUtilities.createEmptyFile(new File(dir, ".nomedia"));
        return dir;
    }

    private static final long PENDING_INTENT_TTL_MS = 120_000L;
    private final ConcurrentHashMap<String, Long> forcePhysicalDeleteTimestamps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> pendingByMeKeepTimestamps = new ConcurrentHashMap<>();

    private static void pruneExpiredIntents(@NonNull ConcurrentHashMap<String, Long> map, long nowMs) {
        if (map.size() <= 128) {
            return;
        }
        map.entrySet().removeIf(entry -> entry.getValue() == null || (nowMs - entry.getValue()) >= PENDING_INTENT_TTL_MS);
    }

    private static boolean hasActiveIntent(@NonNull ConcurrentHashMap<String, Long> map, @Nullable ConcurrentHashMap<String, Boolean> legacyMap, String uid) {
        Long ts = map.get(uid);
        if (ts != null) {
            if (System.currentTimeMillis() - ts < PENDING_INTENT_TTL_MS) {
                return true;
            }
            map.remove(uid, ts);
        }
        return legacyMap != null && Boolean.TRUE.equals(legacyMap.get(uid));
    }

    public void markForcePhysicalDelete(long dialogId, int msgId) {
        if (dialogId != 0 && msgId != 0) {
            String uid = buildUid(dialogId, msgId);
            long now = System.currentTimeMillis();
            forcePhysicalDeleteTimestamps.put(uid, now);
            forcePhysicalDeleteKeys.put(uid, Boolean.TRUE);
            pruneExpiredIntents(forcePhysicalDeleteTimestamps, now);
        }
    }

    public boolean peekForcePhysicalDelete(long dialogId, int msgId) {
        if (dialogId == 0 || msgId == 0) {
            return false;
        }
        return hasActiveIntent(forcePhysicalDeleteTimestamps, forcePhysicalDeleteKeys, buildUid(dialogId, msgId));
    }

    public boolean consumeForcePhysicalDelete(long dialogId, int msgId) {
        if (dialogId == 0 || msgId == 0) {
            return false;
        }
        String uid = buildUid(dialogId, msgId);
        // Keep timestamped intent active across UI and storageQueue threads to prevent race conditions.
        forcePhysicalDeleteKeys.remove(uid);
        return hasActiveIntent(forcePhysicalDeleteTimestamps, null, uid);
    }

    public void markPendingLocalDeleteAsKeepByMe(long dialogId, int msgId) {
        if (dialogId != 0 && msgId != 0) {
            String uid = buildUid(dialogId, msgId);
            long now = System.currentTimeMillis();
            pendingByMeKeepTimestamps.put(uid, now);
            pendingByMeKeepKeys.put(uid, Boolean.TRUE);
            pruneExpiredIntents(pendingByMeKeepTimestamps, now);
        }
    }

    public boolean peekPendingKeepByMe(long dialogId, int msgId) {
        if (dialogId == 0 || msgId == 0) {
            return false;
        }
        return hasActiveIntent(pendingByMeKeepTimestamps, pendingByMeKeepKeys, buildUid(dialogId, msgId));
    }

    public boolean consumePendingKeepByMe(long dialogId, int msgId) {
        if (dialogId == 0 || msgId == 0) {
            return false;
        }
        String uid = buildUid(dialogId, msgId);
        pendingByMeKeepKeys.remove(uid);
        return hasActiveIntent(pendingByMeKeepTimestamps, null, uid);
    }

    public void precacheEphemeralMediaAsync(@Nullable TLRPC.Message message) {
        if (message == null || !MirrlyConfig.isSaveDeletedMessages()) {
            return;
        }
        if (!isEphemeralOrOnce(message)) {
            return;
        }
        final TLRPC.Message copy = message;
        Utilities.globalQueue.postRunnable(() -> {
            try {
                long dialogId = MessageObject.getDialogId(copy);
                if (dialogId == 0 || copy.id == 0) {
                    return;
                }
                String uid = buildUid(dialogId, copy.id);
                String existing = precachedMediaPaths.get(uid);
                if (!TextUtils.isEmpty(existing) && new File(existing).exists()) {
                    return;
                }
                String savedPath = rescueMessageMediaFile(copy, dialogId, copy.id);
                if (!TextUtils.isEmpty(savedPath)) {
                    precachedMediaPaths.put(uid, savedPath);
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }

    public void precacheEphemeralMediaAsync(@Nullable MessageObject messageObject) {
        if (messageObject != null && messageObject.messageOwner != null) {
            precacheEphemeralMediaAsync(messageObject.messageOwner);
        }
    }

    /**
     * Synchronously archives a message on the caller background thread before Telegram destroys
     * the local cache file or deletes/empties the row from {@code messages_v2}.
     * Mutates {@code message} with {@code mirrlyDeleted} metadata and restored {@code attachPath}.
     */
    public boolean captureDeletedMessageSync(@Nullable TLRPC.Message message, long fallbackDialogId, boolean deletedByMe, boolean forceEphemeral) {
        if (message == null || !MirrlyConfig.isSaveDeletedMessages()) {
            return false;
        }
        if (message instanceof TLRPC.TL_messageService && !(message.action instanceof TLRPC.TL_messageActionPhoneCall)) {
            return false;
        }
        long dialogId = message.dialog_id != 0 ? message.dialog_id : MessageObject.getDialogId(message);
        if (dialogId == 0) {
            dialogId = fallbackDialogId;
        }
        if (dialogId == 0 || message.id == 0) {
            return false;
        }

        ensureLoaded();
        int deletedAt = (int) (System.currentTimeMillis() / 1000L);
        boolean isEphemeral = forceEphemeral || isEphemeralOrOnce(message);
        String mediaKind = resolveMediaKind(message);
        String rescuedMedia = rescueMessageMediaFile(message, dialogId, message.id);
        String rescuedThumb = rescueMessageThumbFile(message, dialogId, message.id);

        if (!TextUtils.isEmpty(rescuedMedia)) {
            message.mirrlySavedAttachPath = rescuedMedia;
            message.attachPath = rescuedMedia;
            precachedMediaPaths.remove(buildUid(dialogId, message.id));
            if (isEphemeral) {
                message.ttl = 0;
                message.destroyTime = 0;
                message.destroyTimeMillis = 0;
                if (message.media != null) {
                    message.media.ttl_seconds = 0;
                    if (message.media instanceof TLRPC.TL_messageMediaPhoto &&
                            (message.media.photo == null || message.media.photo instanceof TLRPC.TL_photoEmpty)) {
                        TLRPC.TL_photo regenerated = SendMessagesHelper.getInstance(currentAccount).generatePhotoSizes(rescuedMedia, null);
                        if (regenerated != null) {
                            message.media.photo = regenerated;
                        }
                    }
                }
            }
        }

        message.mirrlyDeleted = true;
        message.mirrlyDeletedAt = deletedAt;
        message.mirrlyDeletedByMe = deletedByMe;

        byte[] blob = serializeMessage(message);
        long senderId = resolveSenderId(message, dialogId);
        String peerTitle = resolvePeerTitle(dialogId);
        String senderName = resolveSenderName(message, senderId, dialogId);
        String fileName = resolveFileName(message);
        String mimeType = resolveMimeType(message);
        int[] whd = resolveMediaDimsAndDuration(message);
        long mediaSize = resolveMediaSize(message, rescuedMedia);

        String uid = buildUid(dialogId, message.id);
        ContentValues cv = new ContentValues();
        cv.put("uid", uid);
        cv.put("dialog_id", dialogId);
        cv.put("msg_id", message.id);
        cv.put("grouped_id", (message.flags & 131072) != 0 ? message.grouped_id : 0L);
        cv.put("sender_id", senderId);
        cv.put("peer_title", peerTitle);
        cv.put("sender_name", senderName);
        cv.put("msg_text", message.message != null ? message.message : "");
        cv.put("send_date", message.date);
        cv.put("deleted_at", deletedAt);
        cv.put("deleted_by_me", deletedByMe ? 1 : 0);
        cv.put("is_ephemeral", isEphemeral ? 1 : 0);
        cv.put("media_kind", mediaKind);
        cv.put("file_name", fileName);
        cv.put("mime_type", mimeType);
        cv.put("media_duration", whd[2]);
        cv.put("media_size", mediaSize);
        cv.put("media_w", whd[0]);
        cv.put("media_h", whd[1]);
        cv.put("local_media_path", rescuedMedia);
        cv.put("local_thumb_path", rescuedThumb);
        cv.put("msg_tl_blob", blob);

        long saveStartNs = System.nanoTime();
        synchronized (dbLock) {
            SQLiteDatabase db = getDbLocked(true);
            if (db == null) {
                return false;
            }
            boolean isNew = !deletedIndex.containsKey(uid);
            db.insertWithOnConflict(TABLE_DELETED, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            deletedIndex.put(uid, Boolean.TRUE);
            if (isNew) {
                totalCount.incrementAndGet();
            }
        }
        long estBytes = (blob != null ? blob.length : 0L) + (message.message != null ? message.message.length() * 2L : 0L) + 256L;
        MirrlyPowerTelemetry.recordVaultIo(MirrlyPowerTelemetry.VAULT_KIND_DELETED_MSG, estBytes, System.nanoTime() - saveStartNs);
        notifyDeletedUpdated();
        return true;
    }

    public static void notifyDeletedUpdated() {
        AndroidUtilities.runOnUIThread(() ->
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mirrlyDeletedDidUpdate)
        );
    }

    private static boolean matchesFilterAndQuery(@NonNull DeletedRecord rec, int filter, String normalizedQuery, boolean hasQuery) {
        if (filter == FILTER_TEXT && rec.hasMedia()) {
            return false;
        } else if (filter == FILTER_MEDIA && !rec.isVisualMediaFilterGroup()) {
            return false;
        } else if (filter == FILTER_VOICE_ROUND && !rec.isVoiceRoundFilterGroup()) {
            return false;
        } else if (filter == FILTER_FILES && !rec.isFilesFilterGroup()) {
            return false;
        } else if (filter == FILTER_EPHEMERAL && !rec.ephemeral) {
            return false;
        } else if (filter == FILTER_BY_ME && !rec.deletedByMe) {
            return false;
        }
        if (hasQuery) {
            return rec.text.toLowerCase(Locale.ROOT).contains(normalizedQuery)
                    || rec.peerTitle.toLowerCase(Locale.ROOT).contains(normalizedQuery)
                    || rec.senderName.toLowerCase(Locale.ROOT).contains(normalizedQuery)
                    || rec.fileName.toLowerCase(Locale.ROOT).contains(normalizedQuery)
                    || rec.mediaKind.toLowerCase(Locale.ROOT).contains(normalizedQuery);
        }
        return true;
    }

    public List<DeletedRecord> searchRecords(@Nullable String query, int filter) {
        ensureLoaded();
        List<DeletedRecord> result = new ArrayList<>();
        String normalizedQuery = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
        boolean hasQuery = !TextUtils.isEmpty(normalizedQuery);

        synchronized (dbLock) {
            SQLiteDatabase db = getDbLocked(false);
            if (db == null) {
                return result;
            }
            try (Cursor c = db.rawQuery("SELECT uid, dialog_id, msg_id, grouped_id, sender_id, peer_title, sender_name, msg_text, send_date, deleted_at, deleted_by_me, is_ephemeral, media_kind, file_name, mime_type, media_duration, media_size, media_w, media_h, local_media_path, local_thumb_path, msg_tl_blob FROM " + TABLE_DELETED + " ORDER BY deleted_at DESC, msg_id DESC", null)) {
                while (c.moveToNext()) {
                    DeletedRecord rec = readRecordFromCursor(c);
                    if (matchesFilterAndQuery(rec, filter, normalizedQuery, hasQuery)) {
                        result.add(rec);
                    }
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
        return result;
    }

    @NonNull
    public List<DeletedRecord> getRecordsForDialog(long dialogId) {
        return getRecordsForDialog(dialogId, null, FILTER_ALL);
    }

    @NonNull
    public List<DeletedRecord> getRecordsForDialog(long dialogId, @Nullable String query, int filter) {
        ensureLoaded();
        List<DeletedRecord> result = new ArrayList<>();
        if (dialogId == 0) {
            return result;
        }
        String normalizedQuery = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
        boolean hasQuery = !TextUtils.isEmpty(normalizedQuery);
        synchronized (dbLock) {
            SQLiteDatabase db = getDbLocked(false);
            if (db == null) {
                return result;
            }
            try (Cursor c = db.rawQuery("SELECT uid, dialog_id, msg_id, grouped_id, sender_id, peer_title, sender_name, msg_text, send_date, deleted_at, deleted_by_me, is_ephemeral, media_kind, file_name, mime_type, media_duration, media_size, media_w, media_h, local_media_path, local_thumb_path, msg_tl_blob FROM " + TABLE_DELETED + " WHERE dialog_id = ? ORDER BY send_date ASC, msg_id ASC", new String[]{String.valueOf(dialogId)})) {
                while (c.moveToNext()) {
                    DeletedRecord rec = readRecordFromCursor(c);
                    if (matchesFilterAndQuery(rec, filter, normalizedQuery, hasQuery)) {
                        result.add(rec);
                    }
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
        return result;
    }

    public int getCountForDialog(long dialogId) {
        if (dialogId == 0) {
            return 0;
        }
        ensureLoaded();
        synchronized (dbLock) {
            SQLiteDatabase db = getDbLocked(false);
            if (db == null) {
                return 0;
            }
            try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_DELETED + " WHERE dialog_id = ?", new String[]{String.valueOf(dialogId)})) {
                if (c.moveToFirst()) {
                    return c.getInt(0);
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
        return 0;
    }

    @NonNull
    private static DeletedRecord readRecordFromCursor(@NonNull Cursor c) {
        return new DeletedRecord(
                c.getString(0),
                c.getLong(1),
                c.getInt(2),
                c.getLong(3),
                c.getLong(4),
                c.getString(5),
                c.getString(6),
                c.getString(7),
                c.getInt(8),
                c.getInt(9),
                c.getInt(10) != 0,
                c.getInt(11) != 0,
                c.getString(12),
                c.getString(13),
                c.getString(14),
                c.getInt(15),
                c.getLong(16),
                c.getInt(17),
                c.getInt(18),
                c.getString(19),
                c.getString(20),
                c.getBlob(21)
        );
    }

    public void deleteRecordByUid(@Nullable String uid) {
        if (TextUtils.isEmpty(uid)) {
            return;
        }
        deleteRecordsByUids(Collections.singletonList(uid));
    }

    public void deleteRecordsByUids(@Nullable List<String> uids) {
        if (uids == null || uids.isEmpty()) {
            return;
        }
        ensureLoaded();
        List<String> filesToDelete = new ArrayList<>();
        synchronized (dbLock) {
            SQLiteDatabase db = getDbLocked(true);
            if (db == null) {
                return;
            }
            db.beginTransaction();
            try {
                for (int i = 0; i < uids.size(); i++) {
                    String uid = uids.get(i);
                    if (TextUtils.isEmpty(uid)) {
                        continue;
                    }
                    try (Cursor c = db.rawQuery("SELECT local_media_path, local_thumb_path FROM " + TABLE_DELETED + " WHERE uid = ?", new String[]{uid})) {
                        if (c.moveToFirst()) {
                            addPathIfOwned(filesToDelete, c.getString(0));
                            addPathIfOwned(filesToDelete, c.getString(1));
                        }
                    }
                    int deleted = db.delete(TABLE_DELETED, "uid = ?", new String[]{uid});
                    deletedIndex.remove(uid);
                    if (deleted > 0) {
                        totalCount.updateAndGet(v -> Math.max(0, v - deleted));
                    }
                }
                db.setTransactionSuccessful();
            } catch (Throwable t) {
                FileLog.e(t);
            } finally {
                db.endTransaction();
            }
        }
        if (!filesToDelete.isEmpty()) {
            Utilities.globalQueue.postRunnable(() -> {
                for (int i = 0; i < filesToDelete.size(); i++) {
                    try {
                        new File(filesToDelete.get(i)).delete();
                    } catch (Throwable ignore) {
                    }
                }
            });
        }
        notifyDeletedUpdated();
    }

    public void restoreRecord(@Nullable DeletedRecord rec) {
        if (rec == null || TextUtils.isEmpty(rec.uid)) {
            return;
        }
        ensureLoaded();
        ContentValues cv = new ContentValues();
        cv.put("uid", rec.uid);
        cv.put("dialog_id", rec.dialogId);
        cv.put("msg_id", rec.msgId);
        cv.put("grouped_id", rec.groupedId);
        cv.put("sender_id", rec.senderId);
        cv.put("peer_title", rec.peerTitle);
        cv.put("sender_name", rec.senderName);
        cv.put("msg_text", rec.text);
        cv.put("send_date", rec.sendDate);
        cv.put("deleted_at", rec.deletedAt);
        cv.put("deleted_by_me", rec.deletedByMe ? 1 : 0);
        cv.put("is_ephemeral", rec.ephemeral ? 1 : 0);
        cv.put("media_kind", rec.mediaKind);
        cv.put("file_name", rec.fileName);
        cv.put("mime_type", rec.mimeType);
        cv.put("media_duration", rec.mediaDuration);
        cv.put("media_size", rec.mediaSize);
        cv.put("media_w", rec.mediaWidth);
        cv.put("media_h", rec.mediaHeight);
        cv.put("local_media_path", rec.localMediaPath);
        cv.put("local_thumb_path", rec.localThumbPath);
        cv.put("msg_tl_blob", rec.msgTlBlob);

        synchronized (dbLock) {
            SQLiteDatabase db = getDbLocked(true);
            if (db != null) {
                boolean isNew = !deletedIndex.containsKey(rec.uid);
                db.insertWithOnConflict(TABLE_DELETED, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                deletedIndex.put(rec.uid, Boolean.TRUE);
                if (isNew) {
                    totalCount.incrementAndGet();
                }
            }
        }
        notifyDeletedUpdated();
    }

    public void clearAll() {
        ensureLoaded();
        synchronized (dbLock) {
            SQLiteDatabase db = getDbLocked(true);
            if (db != null) {
                try {
                    db.delete(TABLE_DELETED, null, null);
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            }
            deletedIndex.clear();
            precachedMediaPaths.clear();
            totalCount.set(0);
        }
        cleanupUnusedAttachmentFilesAsync();
        notifyDeletedUpdated();
    }

    public void clearDialogRecords(long dialogId) {
        if (dialogId == 0) {
            return;
        }
        ensureLoaded();
        List<String> uidsToRemove = new ArrayList<>();
        synchronized (dbLock) {
            SQLiteDatabase db = getDbLocked(true);
            if (db == null) {
                return;
            }
            try (Cursor c = db.rawQuery("SELECT uid FROM " + TABLE_DELETED + " WHERE dialog_id = ?", new String[]{String.valueOf(dialogId)})) {
                while (c.moveToNext()) {
                    String uid = c.getString(0);
                    if (!TextUtils.isEmpty(uid)) {
                        uidsToRemove.add(uid);
                    }
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
            try {
                int deleted = db.delete(TABLE_DELETED, "dialog_id = ?", new String[]{String.valueOf(dialogId)});
                for (int i = 0; i < uidsToRemove.size(); i++) {
                    String uid = uidsToRemove.get(i);
                    deletedIndex.remove(uid);
                    precachedMediaPaths.remove(uid);
                }
                if (deleted > 0) {
                    totalCount.updateAndGet(v -> Math.max(0, v - deleted));
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
        cleanupUnusedAttachmentFilesAsync();
        notifyDeletedUpdated();
    }

    public void cleanupUnusedAttachmentFilesAsync() {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                File dir = getSavedAttachmentsDir();
                File[] files = dir.listFiles();
                if (files == null || files.length == 0) {
                    return;
                }
                Set<String> referenced = collectAllReferencedAttachmentPathsAcrossAccounts();
                long nowMs = System.currentTimeMillis();
                for (File f : files) {
                    if (f == null || !f.isFile()) {
                        continue;
                    }
                    String name = f.getName();
                    if (".nomedia".equals(name)) {
                        continue;
                    }
                    if (!referenced.contains(f.getAbsolutePath()) && nowMs - f.lastModified() > 30_000L) {
                        f.delete();
                    }
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }

    private static Set<String> collectAllReferencedAttachmentPathsAcrossAccounts() {
        HashSet<String> set = new HashSet<>();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            MirrlyDeletedChatsStorage.collectReferencedAttachmentPaths(a, set);
            MirrlyDeletedStorage inst = INSTANCES[a];
            if (inst == null) {
                continue;
            }
            set.addAll(inst.precachedMediaPaths.values());
            synchronized (inst.dbLock) {
                SQLiteDatabase db = inst.getDbLocked(false);
                if (db == null) {
                    continue;
                }
                try (Cursor c = db.rawQuery("SELECT local_media_path, local_thumb_path FROM " + TABLE_DELETED, null)) {
                    while (c.moveToNext()) {
                        String p1 = c.getString(0);
                        String p2 = c.getString(1);
                        if (!TextUtils.isEmpty(p1)) {
                            set.add(p1);
                        }
                        if (!TextUtils.isEmpty(p2)) {
                            set.add(p2);
                        }
                    }
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            }
        }
        return set;
    }

    private static void addPathIfOwned(List<String> list, @Nullable String path) {
        if (TextUtils.isEmpty(path)) {
            return;
        }
        try {
            File f = new File(path);
            File dir = getSavedAttachmentsDir();
            if (f.getParentFile() != null && f.getParentFile().equals(dir)) {
                list.add(f.getAbsolutePath());
            }
        } catch (Throwable ignore) {
        }
    }

    private static boolean isEphemeralOrOnce(@NonNull TLRPC.Message message) {
        if (message.ttl > 0 || message.destroyTime > 0 || message instanceof TLRPC.TL_message_secret) {
            return true;
        }
        return message.media != null && message.media.ttl_seconds != 0;
    }

    @NonNull
    private String rescueMessageMediaFile(@NonNull TLRPC.Message message, long dialogId, int msgId) {
        String uid = buildUid(dialogId, msgId);
        String precached = precachedMediaPaths.get(uid);
        if (!TextUtils.isEmpty(precached) && new File(precached).exists()) {
            return precached;
        }
        File savedDir = getSavedAttachmentsDir();
        if (!TextUtils.isEmpty(message.attachPath)) {
            File attach = new File(message.attachPath);
            if (attach.exists() && attach.isFile()) {
                if (attach.getParentFile() != null && attach.getParentFile().equals(savedDir)) {
                    return attach.getAbsolutePath();
                }
                String copied = copyOrDecryptToSavedAttachments(attach, null, buildPrefix(message), resolveFallbackExtension(message), resolveFileName(message));
                if (!TextUtils.isEmpty(copied)) {
                    return copied;
                }
            }
        }

        FileLoader fileLoader = FileLoader.getInstance(currentAccount);
        String prefix = buildPrefix(message);
        String fallbackExt = resolveFallbackExtension(message);
        String docName = resolveFileName(message);

        File cacheCandidate = fileLoader.getPathToMessage(message, true, true);
        String rescued = tryRescueCandidateFile(cacheCandidate, prefix, fallbackExt, docName);
        if (!TextUtils.isEmpty(rescued)) {
            return rescued;
        }

        File normalCandidate = fileLoader.getPathToMessage(message, false, true);
        if (normalCandidate != null && !normalCandidate.equals(cacheCandidate)) {
            rescued = tryRescueCandidateFile(normalCandidate, prefix, fallbackExt, docName);
            if (!TextUtils.isEmpty(rescued)) {
                return rescued;
            }
        }

        TLRPC.Document doc = MessageObject.getDocument(message);
        if (doc != null) {
            rescued = tryRescueCandidateFile(fileLoader.getPathToAttach(doc, null, true, true), prefix, fallbackExt, docName);
            if (!TextUtils.isEmpty(rescued)) {
                return rescued;
            }
            rescued = tryRescueCandidateFile(fileLoader.getPathToAttach(doc, null, false, true), prefix, fallbackExt, docName);
            if (!TextUtils.isEmpty(rescued)) {
                return rescued;
            }
        }

        TLRPC.Photo photo = MessageObject.getPhoto(message);
        if (photo != null && photo.sizes != null && !photo.sizes.isEmpty()) {
            TLRPC.PhotoSize big = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize(true), false, null, true);
            if (big != null) {
                rescued = tryRescueCandidateFile(fileLoader.getPathToAttach(big, null, true, true), prefix, "jpg", null);
                if (!TextUtils.isEmpty(rescued)) {
                    return rescued;
                }
                rescued = tryRescueCandidateFile(fileLoader.getPathToAttach(big, null, false, true), prefix, "jpg", null);
                if (!TextUtils.isEmpty(rescued)) {
                    return rescued;
                }
            }
        }
        return "";
    }

    @NonNull
    private String rescueMessageThumbFile(@NonNull TLRPC.Message message, long dialogId, int msgId) {
        TLRPC.Document doc = MessageObject.getDocument(message);
        if (doc != null && doc.thumbs != null && !doc.thumbs.isEmpty()) {
            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(doc.thumbs, 320, false, null, true);
            if (thumb != null) {
                File f = FileLoader.getInstance(currentAccount).getPathToAttach(thumb, null, true, true);
                String res = tryRescueCandidateFile(f, "thumb_" + dialogId + "_" + msgId, "jpg", null);
                if (!TextUtils.isEmpty(res)) {
                    return res;
                }
            }
        }
        return "";
    }

    @NonNull
    public String rescueMessageMediaForArchive(@NonNull TLRPC.Message message, long dialogId, int msgId) {
        return rescueMessageMediaFile(message, dialogId, msgId);
    }

    @NonNull
    public String rescueMessageThumbForArchive(@NonNull TLRPC.Message message, long dialogId, int msgId) {
        return rescueMessageThumbFile(message, dialogId, msgId);
    }

    @NonNull
    public String rescueAvatarForArchive(@Nullable TLRPC.User user, @Nullable TLRPC.Chat chat, long dialogId) {
        try {
            TLRPC.FileLocation loc = null;
            if (user != null && user.photo != null) {
                loc = user.photo.photo_small != null ? user.photo.photo_small : user.photo.photo_big;
            } else if (chat != null && chat.photo != null) {
                loc = chat.photo.photo_small != null ? chat.photo.photo_small : chat.photo.photo_big;
            }
            if (loc != null) {
                File f = FileLoader.getInstance(currentAccount).getPathToAttach(loc, null, true, true);
                String res = tryRescueCandidateFile(f, "avatar_" + dialogId, "jpg", null);
                if (!TextUtils.isEmpty(res)) {
                    return res;
                }
                File f2 = FileLoader.getInstance(currentAccount).getPathToAttach(loc, null, false, true);
                if (f2 != null && !f2.equals(f)) {
                    return tryRescueCandidateFile(f2, "avatar_" + dialogId, "jpg", null);
                }
            }
        } catch (Throwable ignore) {
        }
        return "";
    }

    @NonNull
    private String tryRescueCandidateFile(@Nullable File baseFile, String prefix, String fallbackExt, @Nullable String preferredName) {
        if (baseFile == null || TextUtils.isEmpty(baseFile.getPath())) {
            return "";
        }
        try {
            File encFile = new File(baseFile.getAbsolutePath() + ".enc");
            File keyFile = new File(FileLoader.getInternalCacheDir(), baseFile.getName() + ".enc.key");
            if (encFile.exists() && encFile.length() > 0 && keyFile.exists()) {
                String res = copyOrDecryptToSavedAttachments(encFile, keyFile, prefix, fallbackExt, preferredName);
                if (!TextUtils.isEmpty(res)) {
                    return res;
                }
            }
            if (baseFile.exists() && baseFile.isFile() && baseFile.length() > 0) {
                String res = copyOrDecryptToSavedAttachments(baseFile, null, prefix, fallbackExt, preferredName);
                if (!TextUtils.isEmpty(res)) {
                    return res;
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return "";
    }

    @NonNull
    private static String copyOrDecryptToSavedAttachments(
            @NonNull File sourceFile,
            @Nullable File encKeyFile,
            @NonNull String prefix,
            @Nullable String fallbackExt,
            @Nullable String preferredName
    ) {
        try {
            File dir = getSavedAttachmentsDir();
            String ext = fallbackExt != null ? fallbackExt : "";
            String nameForExt = !TextUtils.isEmpty(preferredName) ? preferredName : sourceFile.getName();
            if (nameForExt.endsWith(".enc")) {
                nameForExt = nameForExt.substring(0, nameForExt.length() - 4);
            }
            int dot = nameForExt.lastIndexOf('.');
            if (dot >= 0 && dot < nameForExt.length() - 1) {
                String cand = nameForExt.substring(dot + 1).replaceAll("[^A-Za-z0-9]", "");
                if (!TextUtils.isEmpty(cand) && cand.length() <= 8) {
                    ext = cand;
                }
            }
            String safeExt = TextUtils.isEmpty(ext) ? "dat" : ext;
            String shortUuid = UUID.randomUUID().toString().substring(0, 8);
            File dst = new File(dir, prefix + "_" + shortUuid + "." + safeExt);

            long copyStartNs = System.nanoTime();
            long copiedBytes = 0L;
            try (InputStream in = (encKeyFile != null && encKeyFile.exists())
                    ? new EncryptedFileInputStream(sourceFile, encKeyFile)
                    : new FileInputStream(sourceFile);
                 FileOutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[16384];
                int read;
                while ((read = in.read(buf)) != -1) {
                    out.write(buf, 0, read);
                    copiedBytes += read;
                }
                out.flush();
            }
            if (copiedBytes > 0) {
                MirrlyPowerTelemetry.recordVaultIo(MirrlyPowerTelemetry.VAULT_KIND_DELETED_MSG, copiedBytes, System.nanoTime() - copyStartNs);
            }
            return dst.getAbsolutePath();
        } catch (Throwable t) {
            FileLog.e(t);
            return "";
        }
    }

    @NonNull
    private static String buildPrefix(@NonNull TLRPC.Message message) {
        long did = MessageObject.getDialogId(message);
        return resolveMediaKind(message) + "_" + did + "_" + message.id;
    }

    @NonNull
    public static String resolveMediaKind(@NonNull TLRPC.Message message) {
        TLRPC.MessageMedia media = message.media;
        if (media == null || media instanceof TLRPC.TL_messageMediaEmpty) {
            return MEDIA_NONE;
        }
        if (media instanceof TLRPC.TL_messageMediaPhoto) {
            return MEDIA_PHOTO;
        }
        if (media instanceof TLRPC.TL_messageMediaGeo || media instanceof TLRPC.TL_messageMediaGeoLive || media instanceof TLRPC.TL_messageMediaVenue) {
            return MEDIA_GEO;
        }
        if (media instanceof TLRPC.TL_messageMediaContact) {
            return MEDIA_CONTACT;
        }
        if (media instanceof TLRPC.TL_messageMediaPoll) {
            return MEDIA_POLL;
        }
        TLRPC.Document doc = MessageObject.getDocument(message);
        if (doc != null) {
            if (MessageObject.isRoundVideoDocument(doc)) {
                return MEDIA_ROUND;
            }
            if (MessageObject.isVoiceDocument(doc)) {
                return MEDIA_VOICE;
            }
            if (MessageObject.isStickerDocument(doc) || MessageObject.isAnimatedStickerDocument(doc, true)) {
                return MEDIA_STICKER;
            }
            if (MessageObject.isGifDocument(doc)) {
                return MEDIA_GIF;
            }
            if (MessageObject.isVideoDocument(doc)) {
                return MEDIA_VIDEO;
            }
            if (MessageObject.isMusicDocument(doc)) {
                return MEDIA_AUDIO;
            }
            return MEDIA_DOCUMENT;
        }
        if (media instanceof TLRPC.TL_messageMediaDocument) {
            return MEDIA_DOCUMENT;
        }
        return MEDIA_NONE;
    }

    @NonNull
    private static String resolveFallbackExtension(@NonNull TLRPC.Message message) {
        String kind = resolveMediaKind(message);
        switch (kind) {
            case MEDIA_PHOTO:
                return "jpg";
            case MEDIA_VIDEO:
            case MEDIA_ROUND:
            case MEDIA_GIF:
                return "mp4";
            case MEDIA_VOICE:
                return "ogg";
            case MEDIA_AUDIO:
                return "mp3";
            case MEDIA_STICKER:
                return "webp";
            default:
                return "bin";
        }
    }

    @NonNull
    public static String resolveFileName(@NonNull TLRPC.Message message) {
        TLRPC.Document doc = MessageObject.getDocument(message);
        if (doc != null) {
            String name = FileLoader.getDocumentFileName(doc);
            if (!TextUtils.isEmpty(name)) {
                return name;
            }
        }
        return "";
    }

    @NonNull
    private static String resolveMimeType(@NonNull TLRPC.Message message) {
        TLRPC.Document doc = MessageObject.getDocument(message);
        if (doc != null && !TextUtils.isEmpty(doc.mime_type)) {
            return doc.mime_type;
        }
        if (MEDIA_PHOTO.equals(resolveMediaKind(message))) {
            return "image/jpeg";
        }
        return "";
    }

    private static int[] resolveMediaDimsAndDuration(@NonNull TLRPC.Message message) {
        int w = 0;
        int h = 0;
        int duration = 0;
        TLRPC.Document doc = MessageObject.getDocument(message);
        if (doc != null && doc.attributes != null) {
            for (int i = 0; i < doc.attributes.size(); i++) {
                TLRPC.DocumentAttribute attr = doc.attributes.get(i);
                if (attr instanceof TLRPC.TL_documentAttributeVideo) {
                    w = attr.w;
                    h = attr.h;
                    duration = (int) Math.round(attr.duration);
                } else if (attr instanceof TLRPC.TL_documentAttributeAudio) {
                    duration = (int) Math.round(attr.duration);
                } else if (attr instanceof TLRPC.TL_documentAttributeImageSize) {
                    w = attr.w;
                    h = attr.h;
                }
            }
        } else {
            TLRPC.Photo photo = MessageObject.getPhoto(message);
            if (photo != null && photo.sizes != null && !photo.sizes.isEmpty()) {
                TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize(true));
                if (size != null) {
                    w = size.w;
                    h = size.h;
                }
            }
        }
        return new int[]{w, h, duration};
    }

    private static long resolveMediaSize(@NonNull TLRPC.Message message, @Nullable String rescuedPath) {
        if (!TextUtils.isEmpty(rescuedPath)) {
            File f = new File(rescuedPath);
            if (f.exists()) {
                return f.length();
            }
        }
        TLRPC.Document doc = MessageObject.getDocument(message);
        if (doc != null && doc.size > 0) {
            return doc.size;
        }
        return 0L;
    }

    private static long resolveSenderId(@NonNull TLRPC.Message message, long dialogId) {
        if (message.from_id != null) {
            if (message.from_id.user_id != 0) {
                return message.from_id.user_id;
            }
            if (message.from_id.channel_id != 0) {
                return -message.from_id.channel_id;
            }
            if (message.from_id.chat_id != 0) {
                return -message.from_id.chat_id;
            }
        }
        return dialogId;
    }

    @NonNull
    private String resolvePeerTitle(long dialogId) {
        try {
            MessagesController mc = MessagesController.getInstance(currentAccount);
            if (DialogObject.isEncryptedDialog(dialogId)) {
                TLRPC.EncryptedChat ec = mc.getEncryptedChat(DialogObject.getEncryptedChatId(dialogId));
                if (ec != null) {
                    TLRPC.User u = mc.getUser(ec.user_id);
                    if (u != null) {
                        return UserObject.getUserName(u);
                    }
                }
                return "";
            }
            if (dialogId >= 0) {
                TLRPC.User u = mc.getUser(dialogId);
                if (u != null) {
                    return UserObject.getUserName(u);
                }
            } else {
                TLRPC.Chat chat = mc.getChat(-dialogId);
                if (chat != null && !TextUtils.isEmpty(chat.title)) {
                    return chat.title;
                }
            }
        } catch (Throwable ignore) {
        }
        return "";
    }

    @NonNull
    private String resolveSenderName(@NonNull TLRPC.Message message, long senderId, long dialogId) {
        try {
            MessagesController mc = MessagesController.getInstance(currentAccount);
            if (message.out) {
                TLRPC.User me = UserConfig.getInstance(currentAccount).getCurrentUser();
                if (me != null) {
                    return UserObject.getUserName(me);
                }
            }
            if (senderId > 0) {
                TLRPC.User u = mc.getUser(senderId);
                if (u != null) {
                    return UserObject.getUserName(u);
                }
            } else if (senderId < 0) {
                TLRPC.Chat c = mc.getChat(-senderId);
                if (c != null && !TextUtils.isEmpty(c.title)) {
                    return c.title;
                }
            }
            if (DialogObject.isEncryptedDialog(dialogId)) {
                return resolvePeerTitle(dialogId);
            }
        } catch (Throwable ignore) {
        }
        return "";
    }

    @Nullable
    public static byte[] serializeTlObject(@Nullable org.telegram.tgnet.TLObject obj) {
        if (obj == null) {
            return null;
        }
        SerializedData data = new SerializedData();
        try {
            obj.serializeToStream(data);
            return data.toByteArray();
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        } finally {
            data.cleanup();
        }
    }

    @Nullable
    private static byte[] serializeMessage(@NonNull TLRPC.Message message) {
        return serializeTlObject(message);
    }
}
