package org.telegram.messenger.mirrly;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageCustomParamsHelper;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-account persistent archive for fully deleted chats (DMs, groups, channels, secret chats)
 * including serialized peer metadata, cached message history, and rescued local attachments.
 */
public final class MirrlyDeletedChatsStorage {

    public static final int FILTER_ALL = 0;
    public static final int FILTER_PRIVATE = 1;
    public static final int FILTER_GROUPS_CHANNELS = 2;
    public static final int FILTER_SECRET = 3;

    public static final int PEER_TYPE_PRIVATE = 0;
    public static final int PEER_TYPE_BOT = 1;
    public static final int PEER_TYPE_GROUP = 2;
    public static final int PEER_TYPE_CHANNEL = 3;
    public static final int PEER_TYPE_SECRET = 4;

    private static final String TABLE_CHATS = "deleted_chats";
    private static final String TABLE_MESSAGES = "deleted_chat_messages";
    private static final String TABLE_PEERS = "deleted_chat_peers";
    private static final int DB_VERSION = 1;
    private static final int MAX_ARCHIVED_MESSAGES_PER_CHAT = 2500;
    private static final long INTENT_TTL_MS = 120_000L;
    private static final long STAGED_TTL_MS = 90_000L;
    private static final int MAX_STAGED_PER_CHAT = 400;

    private static final MirrlyDeletedChatsStorage[] INSTANCES = new MirrlyDeletedChatsStorage[UserConfig.MAX_ACCOUNT_COUNT];

    public static MirrlyDeletedChatsStorage getInstance(int account) {
        int safeAccount = account >= 0 && account < INSTANCES.length ? account : 0;
        MirrlyDeletedChatsStorage local = INSTANCES[safeAccount];
        if (local == null) {
            synchronized (MirrlyDeletedChatsStorage.class) {
                local = INSTANCES[safeAccount];
                if (local == null) {
                    local = new MirrlyDeletedChatsStorage(safeAccount);
                    INSTANCES[safeAccount] = local;
                }
            }
        }
        return local;
    }

    public static final class DeletedChatSummary {
        public final long dialogId;
        public final int peerType;
        public final String title;
        public final String username;
        public final int deletedAt;
        public final boolean deletedByMe;
        public final int lastMsgDate;
        public final int lastMsgId;
        public final String lastMsgText;
        public final boolean lastMsgOut;
        public final int msgCount;
        public final byte[] peerTlBlob;
        public final byte[] encChatTlBlob;
        public final byte[] lastMsgTlBlob;
        public final String localAvatarPath;

        public DeletedChatSummary(
                long dialogId,
                int peerType,
                String title,
                String username,
                int deletedAt,
                boolean deletedByMe,
                int lastMsgDate,
                int lastMsgId,
                String lastMsgText,
                boolean lastMsgOut,
                int msgCount,
                byte[] peerTlBlob,
                byte[] encChatTlBlob,
                byte[] lastMsgTlBlob,
                String localAvatarPath
        ) {
            this.dialogId = dialogId;
            this.peerType = peerType;
            this.title = title != null ? title : "";
            this.username = username != null ? username : "";
            this.deletedAt = deletedAt;
            this.deletedByMe = deletedByMe;
            this.lastMsgDate = lastMsgDate;
            this.lastMsgId = lastMsgId;
            this.lastMsgText = lastMsgText != null ? lastMsgText : "";
            this.lastMsgOut = lastMsgOut;
            this.msgCount = Math.max(0, msgCount);
            this.peerTlBlob = peerTlBlob;
            this.encChatTlBlob = encChatTlBlob;
            this.lastMsgTlBlob = lastMsgTlBlob;
            this.localAvatarPath = localAvatarPath != null ? localAvatarPath : "";
        }

        public int getSortTimestamp() {
            return Math.max(lastMsgDate, deletedAt);
        }

        public boolean matchesFilter(int filter) {
            switch (filter) {
                case FILTER_PRIVATE:
                    return peerType == PEER_TYPE_PRIVATE || peerType == PEER_TYPE_BOT;
                case FILTER_GROUPS_CHANNELS:
                    return peerType == PEER_TYPE_GROUP || peerType == PEER_TYPE_CHANNEL;
                case FILTER_SECRET:
                    return peerType == PEER_TYPE_SECRET;
                default:
                    return true;
            }
        }
    }

    private static final class StagedClearedEntry {
        final TLRPC.Message message;
        final String localMediaPath;
        final String localThumbPath;
        final long stagedAtMs;

        StagedClearedEntry(TLRPC.Message message, String localMediaPath, String localThumbPath, long stagedAtMs) {
            this.message = message;
            this.localMediaPath = localMediaPath;
            this.localThumbPath = localThumbPath;
            this.stagedAtMs = stagedAtMs;
        }
    }

    private static final class DbHelper extends SQLiteOpenHelper {
        DbHelper(Context context, int account) {
            super(context, "mirrly_deleted_chats_" + account + ".db", null, DB_VERSION);
            try {
                setWriteAheadLoggingEnabled(true);
            } catch (Throwable ignore) {
            }
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_CHATS + " ("
                    + "dialog_id INTEGER PRIMARY KEY,"
                    + "peer_type INTEGER NOT NULL DEFAULT 0,"
                    + "title TEXT,"
                    + "username TEXT,"
                    + "deleted_at INTEGER NOT NULL DEFAULT 0,"
                    + "deleted_by_me INTEGER NOT NULL DEFAULT 0,"
                    + "last_msg_date INTEGER NOT NULL DEFAULT 0,"
                    + "last_msg_id INTEGER NOT NULL DEFAULT 0,"
                    + "last_msg_text TEXT,"
                    + "last_msg_out INTEGER NOT NULL DEFAULT 0,"
                    + "msg_count INTEGER NOT NULL DEFAULT 0,"
                    + "peer_tl_blob BLOB,"
                    + "enc_chat_tl_blob BLOB,"
                    + "last_msg_tl_blob BLOB,"
                    + "local_avatar_path TEXT"
                    + ")");
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_MESSAGES + " ("
                    + "row_key TEXT PRIMARY KEY,"
                    + "dialog_id INTEGER NOT NULL,"
                    + "msg_id INTEGER NOT NULL,"
                    + "grouped_id INTEGER NOT NULL DEFAULT 0,"
                    + "sender_id INTEGER NOT NULL DEFAULT 0,"
                    + "sender_name TEXT,"
                    + "msg_text TEXT,"
                    + "send_date INTEGER NOT NULL DEFAULT 0,"
                    + "is_out INTEGER NOT NULL DEFAULT 0,"
                    + "is_mirrly_deleted INTEGER NOT NULL DEFAULT 0,"
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
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_dcm_dialog_date ON " + TABLE_MESSAGES + " (dialog_id, send_date ASC, msg_id ASC)");
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_PEERS + " ("
                    + "peer_id INTEGER PRIMARY KEY,"
                    + "peer_tl_blob BLOB"
                    + ")");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            onCreate(db);
        }
    }

    private final int currentAccount;
    private final Object dbLock = new Object();
    private DbHelper dbHelper;
    private volatile boolean warmedUp;
    private volatile boolean warmupQueued;
    private final AtomicInteger totalChatsCount = new AtomicInteger(0);
    private final ConcurrentHashMap<Long, Long> pendingDeletedByMeTimestamps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<StagedClearedEntry>> stagedClearedBatches = new ConcurrentHashMap<>();

    private MirrlyDeletedChatsStorage(int account) {
        this.currentAccount = account;
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
            try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_CHATS, null)) {
                if (c.moveToFirst()) {
                    count = c.getInt(0);
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
            totalChatsCount.set(count);
            warmedUp = true;
        }
    }

    public int getChatCount() {
        ensureLoaded();
        return Math.max(0, totalChatsCount.get());
    }

    public void markPendingDeletedByMe(long dialogId) {
        if (dialogId != 0) {
            pendingDeletedByMeTimestamps.put(dialogId, System.currentTimeMillis());
        }
    }

    public boolean consumePendingDeletedByMe(long dialogId) {
        Long ts = pendingDeletedByMeTimestamps.remove(dialogId);
        return ts != null && (System.currentTimeMillis() - ts) < INTENT_TTL_MS;
    }

    /**
     * Stages messages that are being physically deleted when {@code saveDeletedMessages} is disabled
     * while {@code saveDeletedChats} is enabled, so that if Telegram immediately deletes the empty
     * dialog afterward ({@code deleteDialog(did, 3)}), the messages are still available to archive.
     */
    public void stageClearedMessageSync(@NonNull TLRPC.Message message, long dialogId) {
        if (!MirrlyConfig.isSaveDeletedChats() || dialogId == 0 || dialogId == UserConfig.getInstance(currentAccount).getClientUserId()) {
            return;
        }
        if (message instanceof TLRPC.TL_messageService && !(message.action instanceof TLRPC.TL_messageActionPhoneCall)) {
            return;
        }
        try {
            MirrlyDeletedStorage delStorage = MirrlyDeletedStorage.getInstance(currentAccount);
            String mediaPath = delStorage.rescueMessageMediaForArchive(message, dialogId, message.id);
            String thumbPath = delStorage.rescueMessageThumbForArchive(message, dialogId, message.id);
            long now = System.currentTimeMillis();
            List<StagedClearedEntry> list = stagedClearedBatches.computeIfAbsent(dialogId, k -> Collections.synchronizedList(new ArrayList<>()));
            synchronized (list) {
                list.removeIf(e -> now - e.stagedAtMs > STAGED_TTL_MS);
                if (list.size() >= MAX_STAGED_PER_CHAT) {
                    list.remove(0);
                }
                list.add(new StagedClearedEntry(message, mediaPath, thumbPath, now));
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    /**
     * Captures a deleted chat snapshot synchronously on {@code storageQueue} immediately before
     * {@link MessagesStorage#deleteDialog(long, int)} purges dialog rows, cached messages, or files.
     */
    public void captureDeletedDialogSync(
            @NonNull org.telegram.SQLite.SQLiteDatabase mainDb,
            @NonNull MessagesStorage storage,
            long did,
            int messagesOnly
    ) {
        if (!MirrlyConfig.isSaveDeletedChats() || did == 0 || (messagesOnly != 0 && messagesOnly != 3)) {
            return;
        }
        long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
        if (did == selfId) {
            return;
        }
        ensureLoaded();
        boolean deletedByMe = consumePendingDeletedByMe(did);

        MessagesController mc = MessagesController.getInstance(currentAccount);
        TLRPC.User peerUser = null;
        TLRPC.Chat peerChat = null;
        TLRPC.EncryptedChat peerEncChat = null;
        int peerType = PEER_TYPE_PRIVATE;
        String title = "";
        String username = "";

        if (DialogObject.isEncryptedDialog(did)) {
            peerType = PEER_TYPE_SECRET;
            int encId = DialogObject.getEncryptedChatId(did);
            peerEncChat = mc.getEncryptedChat(encId);
            if (peerEncChat == null) {
                peerEncChat = readEncryptedChatFromDb(mainDb, encId);
            }
            if (peerEncChat != null && peerEncChat.user_id != 0) {
                peerUser = mc.getUser(peerEncChat.user_id);
                if (peerUser == null) {
                    peerUser = readUserFromDb(mainDb, peerEncChat.user_id);
                }
            }
            if (peerUser != null) {
                title = UserObject.getUserName(peerUser);
                username = !TextUtils.isEmpty(peerUser.username) ? peerUser.username : "";
            }
        } else if (DialogObject.isUserDialog(did)) {
            peerUser = mc.getUser(did);
            if (peerUser == null) {
                peerUser = readUserFromDb(mainDb, did);
            }
            if (peerUser != null) {
                peerType = peerUser.bot ? PEER_TYPE_BOT : PEER_TYPE_PRIVATE;
                title = UserObject.getUserName(peerUser);
                username = !TextUtils.isEmpty(peerUser.username) ? peerUser.username : "";
            }
        } else if (DialogObject.isChatDialog(did)) {
            peerChat = mc.getChat(-did);
            if (peerChat == null) {
                peerChat = readChatFromDb(mainDb, -did);
            }
            if (peerChat != null) {
                peerType = (ChatObject.isChannel(peerChat) && !peerChat.megagroup) ? PEER_TYPE_CHANNEL : PEER_TYPE_GROUP;
                title = !TextUtils.isEmpty(peerChat.title) ? peerChat.title : "";
                username = !TextUtils.isEmpty(peerChat.username) ? peerChat.username : "";
            }
        }

        if (TextUtils.isEmpty(title)) {
            title = peerType == PEER_TYPE_CHANNEL || peerType == PEER_TYPE_GROUP
                    ? "Chat " + (-did)
                    : "User " + did;
        }

        int dialogLastDate = 0;
        boolean dialogExisted = false;
        SQLiteCursor dlgCursor = null;
        try {
            dlgCursor = mainDb.queryFinalized("SELECT date FROM dialogs WHERE did = " + did);
            if (dlgCursor.next()) {
                dialogExisted = true;
                dialogLastDate = dlgCursor.intValue(0);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        } finally {
            if (dlgCursor != null) {
                dlgCursor.dispose();
            }
        }

        HashMap<Integer, ArchiveMsgEntry> collectedMap = new HashMap<>();
        SQLiteCursor msgCursor = null;
        try {
            msgCursor = mainDb.queryFinalized(String.format(
                    Locale.US,
                    "SELECT mid, data, read_state, send_state, date, out, custom_params FROM messages_v2 WHERE uid = %d ORDER BY date DESC, mid DESC LIMIT %d",
                    did,
                    MAX_ARCHIVED_MESSAGES_PER_CHAT
            ));
            while (msgCursor.next()) {
                int mid = msgCursor.intValue(0);
                NativeByteBuffer data = msgCursor.byteBufferValue(1);
                if (data == null) {
                    continue;
                }
                TLRPC.Message msg = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                if (msg != null) {
                    msg.readAttachPath(data, selfId);
                }
                data.reuse();
                if (msg == null) {
                    continue;
                }
                if (msg.id == 0) {
                    msg.id = mid;
                }
                if (msg.dialog_id == 0) {
                    msg.dialog_id = did;
                }
                MessageObject.setUnreadFlags(msg, msgCursor.intValue(2));
                msg.send_state = msgCursor.intValue(3);
                if (msg.date == 0) {
                    msg.date = msgCursor.intValue(4);
                }
                msg.out = msgCursor.intValue(5) != 0;
                if (!msgCursor.isNull(6)) {
                    NativeByteBuffer customParams = msgCursor.byteBufferValue(6);
                    if (customParams != null) {
                        MessageCustomParamsHelper.readLocalParams(msg, customParams);
                        customParams.reuse();
                    }
                }
                if (msg instanceof TLRPC.TL_messageService && !(msg.action instanceof TLRPC.TL_messageActionPhoneCall)) {
                    continue;
                }
                collectedMap.put(msg.id, new ArchiveMsgEntry(msg, "", "", msg.mirrlyDeleted));
            }
        } catch (Throwable t) {
            FileLog.e(t);
        } finally {
            if (msgCursor != null) {
                msgCursor.dispose();
            }
        }

        List<StagedClearedEntry> staged = stagedClearedBatches.remove(did);
        if (staged != null) {
            long nowMs = System.currentTimeMillis();
            synchronized (staged) {
                for (StagedClearedEntry s : staged) {
                    if (s == null || s.message == null || (nowMs - s.stagedAtMs) > STAGED_TTL_MS) {
                        continue;
                    }
                    if (!collectedMap.containsKey(s.message.id)) {
                        collectedMap.put(s.message.id, new ArchiveMsgEntry(s.message, s.localMediaPath, s.localThumbPath, false));
                    }
                }
            }
        }

        // Also pull individually saved deleted messages for this chat if any were captured earlier.
        List<MirrlyDeletedStorage.DeletedRecord> prevDeleted = MirrlyDeletedStorage.getInstance(currentAccount).getRecordsForDialog(did);
        for (MirrlyDeletedStorage.DeletedRecord rec : prevDeleted) {
            if (rec == null || collectedMap.containsKey(rec.msgId)) {
                continue;
            }
            TLRPC.Message restoredMsg = deserializeMessage(rec.msgTlBlob);
            if (restoredMsg != null) {
                collectedMap.put(rec.msgId, new ArchiveMsgEntry(restoredMsg, rec.localMediaPath, rec.localThumbPath, true));
            }
        }

        if (collectedMap.isEmpty() && !dialogExisted && !deletedByMe) {
            return;
        }

        MirrlyDeletedStorage delStorage = MirrlyDeletedStorage.getInstance(currentAccount);
        String avatarPath = delStorage.rescueAvatarForArchive(peerUser, peerChat, did);

        ArrayList<ArchiveMsgEntry> entries = new ArrayList<>(collectedMap.values());
        Collections.sort(entries, (a, b) -> {
            int c = Integer.compare(a.message.date, b.message.date);
            if (c != 0) {
                return c;
            }
            return Integer.compare(a.message.id, b.message.id);
        });

        HashMap<Long, byte[]> senderPeers = new HashMap<>();
        if (peerUser != null) {
            byte[] pb = MirrlyDeletedStorage.serializeTlObject(peerUser);
            if (pb != null) {
                senderPeers.put(peerUser.id, pb);
            }
        }
        if (peerChat != null) {
            byte[] pb = MirrlyDeletedStorage.serializeTlObject(peerChat);
            if (pb != null) {
                senderPeers.put(-peerChat.id, pb);
            }
        }

        TLRPC.Message lastMsg = null;
        int lastMsgDate = dialogLastDate;
        int lastMsgId = 0;
        String lastMsgText = "";
        boolean lastMsgOut = false;

        for (ArchiveMsgEntry entry : entries) {
            TLRPC.Message msg = entry.message;
            if (TextUtils.isEmpty(entry.localMediaPath)) {
                entry.localMediaPath = delStorage.rescueMessageMediaForArchive(msg, did, msg.id);
            }
            if (TextUtils.isEmpty(entry.localThumbPath)) {
                entry.localThumbPath = delStorage.rescueMessageThumbForArchive(msg, did, msg.id);
            }
            if (!TextUtils.isEmpty(entry.localMediaPath)) {
                msg.attachPath = entry.localMediaPath;
            }
            long sid = resolveSenderId(msg, did);
            if (sid != 0 && !senderPeers.containsKey(sid)) {
                byte[] sb = resolvePeerBlob(mc, mainDb, sid);
                if (sb != null) {
                    senderPeers.put(sid, sb);
                }
            }
            entry.senderId = sid;
            entry.senderName = resolveSenderName(mc, msg, sid, did, title);

            if (lastMsg == null || msg.date > lastMsgDate || (msg.date == lastMsgDate && msg.id >= lastMsgId)) {
                lastMsg = msg;
                lastMsgDate = msg.date;
                lastMsgId = msg.id;
                lastMsgText = extractMessageText(msg);
                lastMsgOut = msg.out;
            }
        }

        int nowSec = (int) (System.currentTimeMillis() / 1000L);
        byte[] peerBlob = MirrlyDeletedStorage.serializeTlObject(peerChat != null ? peerChat : peerUser);
        byte[] encBlob = MirrlyDeletedStorage.serializeTlObject(peerEncChat);
        byte[] lastMsgBlob = MirrlyDeletedStorage.serializeTlObject(lastMsg);

        synchronized (dbLock) {
            SQLiteDatabase db = getDbLocked(true);
            if (db == null) {
                return;
            }
            db.beginTransaction();
            try {
                for (ArchiveMsgEntry entry : entries) {
                    TLRPC.Message msg = entry.message;
                    String rowKey = did + ":" + msg.id;
                    ContentValues mcv = new ContentValues();
                    mcv.put("row_key", rowKey);
                    mcv.put("dialog_id", did);
                    mcv.put("msg_id", msg.id);
                    mcv.put("grouped_id", msg.grouped_id);
                    mcv.put("sender_id", entry.senderId);
                    mcv.put("sender_name", entry.senderName);
                    mcv.put("msg_text", extractMessageText(msg));
                    mcv.put("send_date", msg.date);
                    mcv.put("is_out", msg.out ? 1 : 0);
                    mcv.put("is_mirrly_deleted", entry.mirrlyDeleted ? 1 : 0);
                    mcv.put("is_ephemeral", isEphemeral(msg) ? 1 : 0);
                    mcv.put("media_kind", MirrlyDeletedStorage.resolveMediaKind(msg));
                    mcv.put("file_name", MirrlyDeletedStorage.resolveFileName(msg));
                    mcv.put("mime_type", resolveMimeType(msg));
                    mcv.put("media_duration", resolveDuration(msg));
                    mcv.put("media_size", resolveSize(msg, entry.localMediaPath));
                    int[] dims = resolveDimensions(msg);
                    mcv.put("media_w", dims[0]);
                    mcv.put("media_h", dims[1]);
                    mcv.put("local_media_path", entry.localMediaPath);
                    mcv.put("local_thumb_path", entry.localThumbPath);
                    mcv.put("msg_tl_blob", MirrlyDeletedStorage.serializeTlObject(msg));
                    db.insertWithOnConflict(TABLE_MESSAGES, null, mcv, SQLiteDatabase.CONFLICT_REPLACE);
                }

                for (HashMap.Entry<Long, byte[]> pe : senderPeers.entrySet()) {
                    if (pe.getKey() == null || pe.getValue() == null) {
                        continue;
                    }
                    ContentValues pcv = new ContentValues();
                    pcv.put("peer_id", pe.getKey());
                    pcv.put("peer_tl_blob", pe.getValue());
                    db.insertWithOnConflict(TABLE_PEERS, null, pcv, SQLiteDatabase.CONFLICT_REPLACE);
                }

                int actualMsgCount = entries.size();
                try (Cursor countC = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_MESSAGES + " WHERE dialog_id = ?", new String[]{String.valueOf(did)})) {
                    if (countC.moveToFirst()) {
                        actualMsgCount = countC.getInt(0);
                    }
                }

                ContentValues ccv = new ContentValues();
                ccv.put("dialog_id", did);
                ccv.put("peer_type", peerType);
                ccv.put("title", title);
                ccv.put("username", username);
                ccv.put("deleted_at", nowSec);
                ccv.put("deleted_by_me", deletedByMe ? 1 : 0);
                ccv.put("last_msg_date", lastMsgDate > 0 ? lastMsgDate : nowSec);
                ccv.put("last_msg_id", lastMsgId);
                ccv.put("last_msg_text", lastMsgText);
                ccv.put("last_msg_out", lastMsgOut ? 1 : 0);
                ccv.put("msg_count", actualMsgCount);
                if (peerBlob != null) {
                    ccv.put("peer_tl_blob", peerBlob);
                }
                if (encBlob != null) {
                    ccv.put("enc_chat_tl_blob", encBlob);
                }
                if (lastMsgBlob != null) {
                    ccv.put("last_msg_tl_blob", lastMsgBlob);
                }
                if (!TextUtils.isEmpty(avatarPath)) {
                    ccv.put("local_avatar_path", avatarPath);
                }
                long chatSaveStartNs = System.nanoTime();
                db.insertWithOnConflict(TABLE_CHATS, null, ccv, SQLiteDatabase.CONFLICT_REPLACE);

                try (Cursor totalC = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_CHATS, null)) {
                    if (totalC.moveToFirst()) {
                        totalChatsCount.set(totalC.getInt(0));
                    }
                }
                db.setTransactionSuccessful();
                long estChatBytes = (peerBlob != null ? peerBlob.length : 0L)
                        + (encBlob != null ? encBlob.length : 0L)
                        + (lastMsgBlob != null ? lastMsgBlob.length : 0L)
                        + Math.max(1, actualMsgCount) * 512L;
                MirrlyPowerTelemetry.recordVaultIo(MirrlyPowerTelemetry.VAULT_KIND_DELETED_CHAT, estChatBytes, System.nanoTime() - chatSaveStartNs);
            } catch (Throwable t) {
                FileLog.e(t);
            } finally {
                db.endTransaction();
            }
        }
        notifyChatsUpdated();
    }

    @NonNull
    public List<DeletedChatSummary> searchChats(@Nullable String query, int filter) {
        ensureLoaded();
        List<DeletedChatSummary> out = new ArrayList<>();
        String norm = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
        boolean hasQuery = !TextUtils.isEmpty(norm);
        synchronized (dbLock) {
            SQLiteDatabase db = getDbLocked(false);
            if (db == null) {
                return out;
            }
            try (Cursor c = db.rawQuery(
                    "SELECT dialog_id, peer_type, title, username, deleted_at, deleted_by_me, last_msg_date, last_msg_id, last_msg_text, last_msg_out, msg_count, peer_tl_blob, enc_chat_tl_blob, last_msg_tl_blob, local_avatar_path "
                            + "FROM " + TABLE_CHATS + " ORDER BY MAX(last_msg_date, deleted_at) DESC, dialog_id ASC",
                    null
            )) {
                while (c.moveToNext()) {
                    DeletedChatSummary item = readChatSummaryFromCursor(c);
                    if (!item.matchesFilter(filter)) {
                        continue;
                    }
                    if (hasQuery) {
                        boolean hit = item.title.toLowerCase(Locale.ROOT).contains(norm)
                                || item.username.toLowerCase(Locale.ROOT).contains(norm)
                                || item.lastMsgText.toLowerCase(Locale.ROOT).contains(norm);
                        if (!hit) {
                            continue;
                        }
                    }
                    out.add(item);
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
        return out;
    }

    @Nullable
    public DeletedChatSummary getChatSummary(long dialogId) {
        ensureLoaded();
        if (dialogId == 0) {
            return null;
        }
        synchronized (dbLock) {
            SQLiteDatabase db = getDbLocked(false);
            if (db == null) {
                return null;
            }
            try (Cursor c = db.rawQuery(
                    "SELECT dialog_id, peer_type, title, username, deleted_at, deleted_by_me, last_msg_date, last_msg_id, last_msg_text, last_msg_out, msg_count, peer_tl_blob, enc_chat_tl_blob, last_msg_tl_blob, local_avatar_path "
                            + "FROM " + TABLE_CHATS + " WHERE dialog_id = ?",
                    new String[]{String.valueOf(dialogId)}
            )) {
                if (c.moveToFirst()) {
                    return readChatSummaryFromCursor(c);
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
        return null;
    }

    @NonNull
    public List<MirrlyDeletedStorage.DeletedRecord> getChatMessagesAsDeletedRecords(long dialogId, @Nullable String query, int filter) {
        ensureLoaded();
        List<MirrlyDeletedStorage.DeletedRecord> out = new ArrayList<>();
        if (dialogId == 0) {
            return out;
        }
        DeletedChatSummary summary = getChatSummary(dialogId);
        String peerTitle = summary != null ? summary.title : "";
        int deletedAtDefault = summary != null ? summary.deletedAt : 0;
        String norm = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
        boolean hasQuery = !TextUtils.isEmpty(norm);

        synchronized (dbLock) {
            SQLiteDatabase db = getDbLocked(false);
            if (db == null) {
                return out;
            }
            try (Cursor c = db.rawQuery(
                    "SELECT row_key, dialog_id, msg_id, grouped_id, sender_id, sender_name, msg_text, send_date, is_mirrly_deleted, is_ephemeral, media_kind, file_name, mime_type, media_duration, media_size, media_w, media_h, local_media_path, local_thumb_path, msg_tl_blob "
                            + "FROM " + TABLE_MESSAGES + " WHERE dialog_id = ? ORDER BY send_date ASC, msg_id ASC",
                    new String[]{String.valueOf(dialogId)}
            )) {
                while (c.moveToNext()) {
                    boolean isMirrlyDel = c.getInt(8) != 0;
                    MirrlyDeletedStorage.DeletedRecord rec = new MirrlyDeletedStorage.DeletedRecord(
                            c.getString(0),
                            c.getLong(1),
                            c.getInt(2),
                            c.getLong(3),
                            c.getLong(4),
                            peerTitle,
                            c.getString(5),
                            c.getString(6),
                            c.getInt(7),
                            deletedAtDefault,
                            isMirrlyDel,
                            c.getInt(9) != 0,
                            c.getString(10),
                            c.getString(11),
                            c.getString(12),
                            c.getInt(13),
                            c.getLong(14),
                            c.getInt(15),
                            c.getInt(16),
                            c.getString(17),
                            c.getString(18),
                            c.getBlob(19)
                    );
                    if (filter == MirrlyDeletedStorage.FILTER_TEXT && rec.hasMedia()) {
                        continue;
                    } else if (filter == MirrlyDeletedStorage.FILTER_MEDIA && !rec.isVisualMediaFilterGroup()) {
                        continue;
                    } else if (filter == MirrlyDeletedStorage.FILTER_VOICE_ROUND && !rec.isVoiceRoundFilterGroup()) {
                        continue;
                    } else if (filter == MirrlyDeletedStorage.FILTER_FILES && !rec.isFilesFilterGroup()) {
                        continue;
                    } else if (filter == MirrlyDeletedStorage.FILTER_EPHEMERAL && !rec.ephemeral) {
                        continue;
                    } else if (filter == MirrlyDeletedStorage.FILTER_BY_ME && !isMirrlyDel) {
                        continue;
                    }
                    if (hasQuery) {
                        boolean hit = rec.text.toLowerCase(Locale.ROOT).contains(norm)
                                || rec.senderName.toLowerCase(Locale.ROOT).contains(norm)
                                || rec.fileName.toLowerCase(Locale.ROOT).contains(norm);
                        if (!hit) {
                            continue;
                        }
                    }
                    out.add(rec);
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
        return out;
    }

    /**
     * Pushes serialized archived peers into {@link MessagesController} cache ({@code fromCache = true})
     * when missing so {@code DialogCell}, {@code ChatActivityAvatarContainer}, and {@code ChatMessageCell}
     * render authentic avatars, names, badges, and secret chat locks even after server-side deletion.
     */
    public void hydratePeersIntoMemory() {
        ensureLoaded();
        MessagesController mc = MessagesController.getInstance(currentAccount);
        synchronized (dbLock) {
            SQLiteDatabase db = getDbLocked(false);
            if (db == null) {
                return;
            }
            try (Cursor c = db.rawQuery("SELECT peer_id, peer_tl_blob FROM " + TABLE_PEERS, null)) {
                while (c.moveToNext()) {
                    long pid = c.getLong(0);
                    byte[] blob = c.getBlob(1);
                    putSerializedPeerIfMissing(mc, pid, blob);
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
            try (Cursor c = db.rawQuery("SELECT dialog_id, peer_type, peer_tl_blob, enc_chat_tl_blob FROM " + TABLE_CHATS, null)) {
                while (c.moveToNext()) {
                    long did = c.getLong(0);
                    int type = c.getInt(1);
                    byte[] peerBlob = c.getBlob(2);
                    byte[] encBlob = c.getBlob(3);
                    if (type == PEER_TYPE_SECRET && encBlob != null) {
                        TLRPC.EncryptedChat ec = deserializeEncryptedChat(encBlob);
                        if (ec != null && mc.getEncryptedChat(ec.id) == null) {
                            mc.putEncryptedChat(ec, true);
                        }
                        if (peerBlob != null) {
                            TLRPC.User u = deserializeUser(peerBlob);
                            if (u != null && mc.getUser(u.id) == null) {
                                mc.putUser(u, true);
                            }
                        }
                    } else if (peerBlob != null) {
                        putSerializedPeerIfMissing(mc, did, peerBlob);
                    }
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
    }

    public void deleteChat(long dialogId) {
        if (dialogId == 0) {
            return;
        }
        ensureLoaded();
        synchronized (dbLock) {
            SQLiteDatabase db = getDbLocked(true);
            if (db == null) {
                return;
            }
            db.beginTransaction();
            try {
                db.delete(TABLE_MESSAGES, "dialog_id = ?", new String[]{String.valueOf(dialogId)});
                db.delete(TABLE_CHATS, "dialog_id = ?", new String[]{String.valueOf(dialogId)});
                try (Cursor countC = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_CHATS, null)) {
                    if (countC.moveToFirst()) {
                        totalChatsCount.set(countC.getInt(0));
                    }
                }
                db.setTransactionSuccessful();
            } catch (Throwable t) {
                FileLog.e(t);
            } finally {
                db.endTransaction();
            }
        }
        MirrlyDeletedStorage.getInstance(currentAccount).cleanupUnusedAttachmentFilesAsync();
        notifyChatsUpdated();
    }

    public void deleteMessagesByUids(long dialogId, @Nullable List<String> rowKeys) {
        if (dialogId == 0 || rowKeys == null || rowKeys.isEmpty()) {
            return;
        }
        ensureLoaded();
        synchronized (dbLock) {
            SQLiteDatabase db = getDbLocked(true);
            if (db == null) {
                return;
            }
            db.beginTransaction();
            try {
                for (String key : rowKeys) {
                    if (!TextUtils.isEmpty(key)) {
                        db.delete(TABLE_MESSAGES, "row_key = ? AND dialog_id = ?", new String[]{key, String.valueOf(dialogId)});
                    }
                }
                updateChatSummaryAfterMessageChangeLocked(db, dialogId);
                db.setTransactionSuccessful();
            } catch (Throwable t) {
                FileLog.e(t);
            } finally {
                db.endTransaction();
            }
        }
        MirrlyDeletedStorage.getInstance(currentAccount).cleanupUnusedAttachmentFilesAsync();
        notifyChatsUpdated();
    }

    public void clearAll() {
        ensureLoaded();
        synchronized (dbLock) {
            SQLiteDatabase db = getDbLocked(true);
            if (db != null) {
                db.beginTransaction();
                try {
                    db.delete(TABLE_MESSAGES, null, null);
                    db.delete(TABLE_CHATS, null, null);
                    db.delete(TABLE_PEERS, null, null);
                    totalChatsCount.set(0);
                    db.setTransactionSuccessful();
                } catch (Throwable t) {
                    FileLog.e(t);
                } finally {
                    db.endTransaction();
                }
            }
        }
        MirrlyDeletedStorage.getInstance(currentAccount).cleanupUnusedAttachmentFilesAsync();
        notifyChatsUpdated();
    }

    public static void collectReferencedAttachmentPaths(int account, @NonNull Set<String> out) {
        MirrlyDeletedChatsStorage inst = INSTANCES[account >= 0 && account < INSTANCES.length ? account : 0];
        if (inst == null) {
            return;
        }
        synchronized (inst.dbLock) {
            SQLiteDatabase db = inst.getDbLocked(false);
            if (db == null) {
                return;
            }
            try (Cursor c = db.rawQuery("SELECT local_media_path, local_thumb_path FROM " + TABLE_MESSAGES, null)) {
                while (c.moveToNext()) {
                    String p1 = c.getString(0);
                    String p2 = c.getString(1);
                    if (!TextUtils.isEmpty(p1)) {
                        out.add(p1);
                    }
                    if (!TextUtils.isEmpty(p2)) {
                        out.add(p2);
                    }
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
            try (Cursor c = db.rawQuery("SELECT local_avatar_path FROM " + TABLE_CHATS, null)) {
                while (c.moveToNext()) {
                    String ap = c.getString(0);
                    if (!TextUtils.isEmpty(ap)) {
                        out.add(ap);
                    }
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
    }

    private void updateChatSummaryAfterMessageChangeLocked(@NonNull SQLiteDatabase db, long dialogId) {
        int remaining = 0;
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_MESSAGES + " WHERE dialog_id = ?", new String[]{String.valueOf(dialogId)})) {
            if (c.moveToFirst()) {
                remaining = c.getInt(0);
            }
        }
        ContentValues cv = new ContentValues();
        cv.put("msg_count", remaining);
        if (remaining > 0) {
            try (Cursor lc = db.rawQuery(
                    "SELECT send_date, msg_id, msg_text, is_out, msg_tl_blob FROM " + TABLE_MESSAGES + " WHERE dialog_id = ? ORDER BY send_date DESC, msg_id DESC LIMIT 1",
                    new String[]{String.valueOf(dialogId)}
            )) {
                if (lc.moveToFirst()) {
                    cv.put("last_msg_date", lc.getInt(0));
                    cv.put("last_msg_id", lc.getInt(1));
                    cv.put("last_msg_text", lc.getString(2));
                    cv.put("last_msg_out", lc.getInt(3));
                    byte[] blob = lc.getBlob(4);
                    if (blob != null) {
                        cv.put("last_msg_tl_blob", blob);
                    }
                }
            }
        } else {
            cv.put("last_msg_id", 0);
            cv.put("last_msg_text", "");
            cv.putNull("last_msg_tl_blob");
        }
        db.update(TABLE_CHATS, cv, "dialog_id = ?", new String[]{String.valueOf(dialogId)});
    }

    @NonNull
    private static DeletedChatSummary readChatSummaryFromCursor(@NonNull Cursor c) {
        return new DeletedChatSummary(
                c.getLong(0),
                c.getInt(1),
                c.getString(2),
                c.getString(3),
                c.getInt(4),
                c.getInt(5) != 0,
                c.getInt(6),
                c.getInt(7),
                c.getString(8),
                c.getInt(9) != 0,
                c.getInt(10),
                c.getBlob(11),
                c.getBlob(12),
                c.getBlob(13),
                c.getString(14)
        );
    }

    private void notifyChatsUpdated() {
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mirrlyDeletedChatsDidUpdate));
    }

    private static void putSerializedPeerIfMissing(@NonNull MessagesController mc, long pid, @Nullable byte[] blob) {
        if (blob == null || blob.length == 0) {
            return;
        }
        if (pid > 0) {
            if (mc.getUser(pid) == null) {
                TLRPC.User u = deserializeUser(blob);
                if (u != null) {
                    mc.putUser(u, true);
                }
            }
        } else if (pid < 0 && !DialogObject.isEncryptedDialog(pid)) {
            long chatId = -pid;
            if (mc.getChat(chatId) == null) {
                TLRPC.Chat ch = deserializeChat(blob);
                if (ch != null) {
                    mc.putChat(ch, true);
                }
            }
        }
    }

    @Nullable
    public static TLRPC.User deserializeUser(@Nullable byte[] blob) {
        if (blob == null || blob.length == 0) {
            return null;
        }
        SerializedData data = new SerializedData(blob);
        try {
            return TLRPC.User.TLdeserialize(data, data.readInt32(false), false);
        } catch (Throwable ignore) {
            return null;
        } finally {
            data.cleanup();
        }
    }

    @Nullable
    public static TLRPC.Chat deserializeChat(@Nullable byte[] blob) {
        if (blob == null || blob.length == 0) {
            return null;
        }
        SerializedData data = new SerializedData(blob);
        try {
            return TLRPC.Chat.TLdeserialize(data, data.readInt32(false), false);
        } catch (Throwable ignore) {
            return null;
        } finally {
            data.cleanup();
        }
    }

    @Nullable
    public static TLRPC.EncryptedChat deserializeEncryptedChat(@Nullable byte[] blob) {
        if (blob == null || blob.length == 0) {
            return null;
        }
        SerializedData data = new SerializedData(blob);
        try {
            return TLRPC.EncryptedChat.TLdeserialize(data, data.readInt32(false), false);
        } catch (Throwable ignore) {
            return null;
        } finally {
            data.cleanup();
        }
    }

    @Nullable
    public static TLRPC.Message deserializeMessage(@Nullable byte[] blob) {
        if (blob == null || blob.length == 0) {
            return null;
        }
        SerializedData data = new SerializedData(blob);
        try {
            return TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
        } catch (Throwable ignore) {
            return null;
        } finally {
            data.cleanup();
        }
    }

    @Nullable
    private static TLRPC.User readUserFromDb(@NonNull org.telegram.SQLite.SQLiteDatabase mainDb, long userId) {
        SQLiteCursor cursor = null;
        try {
            cursor = mainDb.queryFinalized("SELECT data FROM users WHERE uid = " + userId);
            if (cursor.next()) {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                if (data != null) {
                    TLRPC.User u = TLRPC.User.TLdeserialize(data, data.readInt32(false), false);
                    data.reuse();
                    return u;
                }
            }
        } catch (Throwable ignore) {
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
        return null;
    }

    @Nullable
    private static TLRPC.Chat readChatFromDb(@NonNull org.telegram.SQLite.SQLiteDatabase mainDb, long chatId) {
        SQLiteCursor cursor = null;
        try {
            cursor = mainDb.queryFinalized("SELECT data FROM chats WHERE uid = " + chatId);
            if (cursor.next()) {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                if (data != null) {
                    TLRPC.Chat ch = TLRPC.Chat.TLdeserialize(data, data.readInt32(false), false);
                    data.reuse();
                    return ch;
                }
            }
        } catch (Throwable ignore) {
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
        return null;
    }

    @Nullable
    private static TLRPC.EncryptedChat readEncryptedChatFromDb(@NonNull org.telegram.SQLite.SQLiteDatabase mainDb, int encId) {
        SQLiteCursor cursor = null;
        try {
            cursor = mainDb.queryFinalized("SELECT data FROM enc_chats WHERE uid = " + encId);
            if (cursor.next()) {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                if (data != null) {
                    TLRPC.EncryptedChat ec = TLRPC.EncryptedChat.TLdeserialize(data, data.readInt32(false), false);
                    data.reuse();
                    return ec;
                }
            }
        } catch (Throwable ignore) {
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
        return null;
    }

    @Nullable
    private static byte[] resolvePeerBlob(@NonNull MessagesController mc, @NonNull org.telegram.SQLite.SQLiteDatabase mainDb, long sid) {
        if (sid > 0) {
            TLRPC.User u = mc.getUser(sid);
            if (u == null) {
                u = readUserFromDb(mainDb, sid);
            }
            return MirrlyDeletedStorage.serializeTlObject(u);
        } else if (sid < 0 && !DialogObject.isEncryptedDialog(sid)) {
            TLRPC.Chat ch = mc.getChat(-sid);
            if (ch == null) {
                ch = readChatFromDb(mainDb, -sid);
            }
            return MirrlyDeletedStorage.serializeTlObject(ch);
        }
        return null;
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
    private String resolveSenderName(@NonNull MessagesController mc, @NonNull TLRPC.Message message, long senderId, long dialogId, @NonNull String fallbackTitle) {
        try {
            if (message.out) {
                TLRPC.User me = UserConfig.getInstance(currentAccount).getCurrentUser();
                if (me != null) {
                    return ContactsController.formatName(me.first_name, me.last_name);
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
        } catch (Throwable ignore) {
        }
        return fallbackTitle;
    }

    @NonNull
    private static String extractMessageText(@NonNull TLRPC.Message msg) {
        return !TextUtils.isEmpty(msg.message) ? msg.message : "";
    }

    private static boolean isEphemeral(@NonNull TLRPC.Message msg) {
        if (msg.ttl > 0 || msg.destroyTime > 0 || msg instanceof TLRPC.TL_message_secret) {
            return true;
        }
        return msg.media != null && msg.media.ttl_seconds != 0;
    }

    @NonNull
    private static String resolveMimeType(@NonNull TLRPC.Message message) {
        TLRPC.Document doc = MessageObject.getDocument(message);
        if (doc != null && !TextUtils.isEmpty(doc.mime_type)) {
            return doc.mime_type;
        }
        if (message.media instanceof TLRPC.TL_messageMediaPhoto) {
            return "image/jpeg";
        }
        return "";
    }

    private static int resolveDuration(@NonNull TLRPC.Message message) {
        TLRPC.Document doc = MessageObject.getDocument(message);
        if (doc != null && doc.attributes != null) {
            for (int i = 0; i < doc.attributes.size(); i++) {
                TLRPC.DocumentAttribute attr = doc.attributes.get(i);
                if (attr instanceof TLRPC.TL_documentAttributeAudio) {
                    return (int) Math.max(0, Math.round(attr.duration));
                }
                if (attr instanceof TLRPC.TL_documentAttributeVideo) {
                    return (int) Math.max(0, Math.round(attr.duration));
                }
            }
        }
        return 0;
    }

    private static long resolveSize(@NonNull TLRPC.Message message, @Nullable String rescuedPath) {
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

    @NonNull
    private static int[] resolveDimensions(@NonNull TLRPC.Message message) {
        int[] dims = new int[]{0, 0};
        TLRPC.Document doc = MessageObject.getDocument(message);
        if (doc != null && doc.attributes != null) {
            for (int i = 0; i < doc.attributes.size(); i++) {
                TLRPC.DocumentAttribute attr = doc.attributes.get(i);
                if (attr instanceof TLRPC.TL_documentAttributeVideo || attr instanceof TLRPC.TL_documentAttributeImageSize) {
                    if (attr.w > 0 && attr.h > 0) {
                        dims[0] = attr.w;
                        dims[1] = attr.h;
                        return dims;
                    }
                }
            }
        }
        TLRPC.Photo photo = MessageObject.getPhoto(message);
        if (photo != null && photo.sizes != null) {
            for (int i = 0; i < photo.sizes.size(); i++) {
                TLRPC.PhotoSize ps = photo.sizes.get(i);
                if (ps != null && ps.w > dims[0] && ps.h > dims[1]) {
                    dims[0] = ps.w;
                    dims[1] = ps.h;
                }
            }
        }
        return dims;
    }

    private static final class ArchiveMsgEntry {
        final TLRPC.Message message;
        String localMediaPath;
        String localThumbPath;
        final boolean mirrlyDeleted;
        long senderId;
        String senderName = "";

        ArchiveMsgEntry(TLRPC.Message message, String localMediaPath, String localThumbPath, boolean mirrlyDeleted) {
            this.message = message;
            this.localMediaPath = localMediaPath != null ? localMediaPath : "";
            this.localThumbPath = localThumbPath != null ? localThumbPath : "";
            this.mirrlyDeleted = mirrlyDeleted;
        }
    }
}
