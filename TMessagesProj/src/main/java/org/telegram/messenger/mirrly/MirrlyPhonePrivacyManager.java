package org.telegram.messenger.mirrly;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.TextStyleSpan;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Local per-account manager for hiding and spoofing phone numbers on this device.
 * Enforces strict mutual exclusion between hiding and spoofing for the current account,
 * and supports per-user spoiler/spoof overrides for other users.
 */
public final class MirrlyPhonePrivacyManager {

    private static final String PREFS_NAME = "mirrly_phone_privacy";

    public static final int OWN_MODE_DEFAULT = 0;
    public static final int OWN_MODE_HIDE = 1;
    public static final int OWN_MODE_SPOOF = 2;

    public static final int PEER_MODE_DEFAULT = 0;
    public static final int PEER_MODE_SPOILER = 1;
    public static final int PEER_MODE_SPOOF = 2;

    public static final int PRESET_COLLECTIBLE_888 = 0;
    public static final int PRESET_MASKED_TAIL = 1;
    public static final int PRESET_RANDOM = 2;

    private static final String KEY_OWN_MODE_PREFIX = "own_mode_";
    private static final String KEY_OWN_SPOOF_PREFIX = "own_spoof_";
    private static final String KEY_OWN_REHIDE_PREFIX = "own_rehide_";
    private static final String KEY_OWN_MASK_BANNER_PREFIX = "own_mask_banner_";
    private static final String KEY_PEER_PREFIX = "peer_";

    private static final SecureRandom RANDOM = new SecureRandom();

    public static final class PeerRule {
        public final long userId;
        public final int mode;
        public final String customPhone;
        public final boolean alsoSpoiler;
        public final String cachedName;
        public final long updatedAt;

        public PeerRule(long userId, int mode, @Nullable String customPhone, boolean alsoSpoiler, @Nullable String cachedName, long updatedAt) {
            this.userId = userId;
            this.mode = mode;
            this.customPhone = customPhone != null ? customPhone : "";
            this.alsoSpoiler = mode == PEER_MODE_SPOILER || alsoSpoiler;
            this.cachedName = cachedName != null ? cachedName : "";
            this.updatedAt = updatedAt;
        }
    }

    public static final class ResolvedPhone {
        public final boolean hasDisplayRow;
        public final boolean isSelf;
        public final boolean hiddenLabel;
        public final boolean spoilered;
        public final boolean spoofed;
        public final boolean isCustomRule;
        public final boolean canEyeToggle;
        public final boolean canCopy;
        public final boolean isFragmentNumber;
        public final CharSequence displayText;
        public final String plainDisplayText;
        public final String copyValue;

        private ResolvedPhone(
                boolean hasDisplayRow,
                boolean isSelf,
                boolean hiddenLabel,
                boolean spoilered,
                boolean spoofed,
                boolean isCustomRule,
                boolean canEyeToggle,
                boolean canCopy,
                boolean isFragmentNumber,
                CharSequence displayText,
                String plainDisplayText,
                String copyValue
        ) {
            this.hasDisplayRow = hasDisplayRow;
            this.isSelf = isSelf;
            this.hiddenLabel = hiddenLabel;
            this.spoilered = spoilered;
            this.spoofed = spoofed;
            this.isCustomRule = isCustomRule;
            this.canEyeToggle = canEyeToggle;
            this.canCopy = canCopy;
            this.isFragmentNumber = isFragmentNumber;
            this.displayText = displayText;
            this.plainDisplayText = plainDisplayText;
            this.copyValue = copyValue;
        }
    }

    private MirrlyPhonePrivacyManager() {
    }

    @Nullable
    private static SharedPreferences getPrefs() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return null;
        }
        return context.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
    }

    @NonNull
    public static String getAccountScopeKey(int account) {
        long clientUserId = UserConfig.getInstance(account).getClientUserId();
        if (clientUserId > 0) {
            return "u_" + clientUserId;
        }
        return "slot_" + account;
    }

    public static int getOwnMode(int account) {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) {
            return OWN_MODE_DEFAULT;
        }
        String scope = getAccountScopeKey(account);
        int mode = prefs.getInt(KEY_OWN_MODE_PREFIX + scope, OWN_MODE_DEFAULT);
        if (mode == OWN_MODE_SPOOF && TextUtils.isEmpty(getOwnSpoofPhone(account))) {
            return OWN_MODE_DEFAULT;
        }
        if (mode != OWN_MODE_HIDE && mode != OWN_MODE_SPOOF) {
            return OWN_MODE_DEFAULT;
        }
        return mode;
    }

    public static boolean isOwnHidden(int account) {
        return getOwnMode(account) == OWN_MODE_HIDE;
    }

    public static boolean isOwnSpoofed(int account) {
        return getOwnMode(account) == OWN_MODE_SPOOF;
    }

    @NonNull
    public static String getOwnSpoofPhone(int account) {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) {
            return "";
        }
        String raw = prefs.getString(KEY_OWN_SPOOF_PREFIX + getAccountScopeKey(account), "");
        return raw != null ? raw.trim() : "";
    }

    public static boolean isAutoRehideOnLeave(int account) {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) {
            return true;
        }
        return prefs.getBoolean(KEY_OWN_REHIDE_PREFIX + getAccountScopeKey(account), true);
    }

    public static void setAutoRehideOnLeave(int account, boolean enabled) {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) {
            return;
        }
        prefs.edit().putBoolean(KEY_OWN_REHIDE_PREFIX + getAccountScopeKey(account), enabled).apply();
        notifyChanged(account);
    }

    public static boolean isMaskCheckPhoneBanner(int account) {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) {
            return true;
        }
        return prefs.getBoolean(KEY_OWN_MASK_BANNER_PREFIX + getAccountScopeKey(account), true);
    }

    public static void setMaskCheckPhoneBanner(int account, boolean enabled) {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) {
            return;
        }
        prefs.edit().putBoolean(KEY_OWN_MASK_BANNER_PREFIX + getAccountScopeKey(account), enabled).apply();
        notifyChanged(account);
    }

    /**
     * Enables or disables own phone hiding.
     * Refuses if local spoof is active to preserve strict mutual exclusion.
     */
    public static boolean setOwnHide(int account, boolean hide) {
        if (hide && isOwnSpoofed(account)) {
            return false;
        }
        SharedPreferences prefs = getPrefs();
        if (prefs == null) {
            return false;
        }
        String scope = getAccountScopeKey(account);
        prefs.edit().putInt(KEY_OWN_MODE_PREFIX + scope, hide ? OWN_MODE_HIDE : OWN_MODE_DEFAULT).apply();
        notifyChanged(account);
        return true;
    }

    /**
     * Sets or clears own local phone spoof.
     * Refuses non-empty spoof if hiding is currently active to preserve strict mutual exclusion.
     */
    public static boolean setOwnSpoof(int account, @Nullable String rawPhone) {
        String trimmed = rawPhone != null ? rawPhone.trim() : "";
        if (TextUtils.isEmpty(trimmed)) {
            clearOwnRule(account);
            return true;
        }
        if (isOwnHidden(account)) {
            return false;
        }
        SharedPreferences prefs = getPrefs();
        if (prefs == null) {
            return false;
        }
        String scope = getAccountScopeKey(account);
        prefs.edit()
                .putString(KEY_OWN_SPOOF_PREFIX + scope, trimmed)
                .putInt(KEY_OWN_MODE_PREFIX + scope, OWN_MODE_SPOOF)
                .apply();
        notifyChanged(account);
        return true;
    }

    public static void clearOwnRule(int account) {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) {
            return;
        }
        String scope = getAccountScopeKey(account);
        prefs.edit()
                .putInt(KEY_OWN_MODE_PREFIX + scope, OWN_MODE_DEFAULT)
                .remove(KEY_OWN_SPOOF_PREFIX + scope)
                .apply();
        notifyChanged(account);
    }

    @Nullable
    public static PeerRule getPeerRule(int account, long userId) {
        if (userId == 0) {
            return null;
        }
        long selfId = UserConfig.getInstance(account).getClientUserId();
        if (selfId > 0 && userId == selfId) {
            return null;
        }
        SharedPreferences prefs = getPrefs();
        if (prefs == null) {
            return null;
        }
        String key = buildPeerPrefKey(account, userId);
        String json = prefs.getString(key, null);
        if (TextUtils.isEmpty(json)) {
            return null;
        }
        return parsePeerRule(userId, json);
    }

    public static void setPeerRule(
            int account,
            long userId,
            int mode,
            @Nullable String customPhone,
            boolean alsoSpoiler,
            @Nullable String cachedName
    ) {
        if (userId == 0) {
            return;
        }
        long selfId = UserConfig.getInstance(account).getClientUserId();
        if (selfId > 0 && userId == selfId) {
            return;
        }
        String trimmedPhone = customPhone != null ? customPhone.trim() : "";
        if (mode == PEER_MODE_DEFAULT || (mode == PEER_MODE_SPOOF && TextUtils.isEmpty(trimmedPhone))) {
            removePeerRule(account, userId);
            return;
        }
        SharedPreferences prefs = getPrefs();
        if (prefs == null) {
            return;
        }
        try {
            JSONObject obj = new JSONObject();
            obj.put("mode", mode);
            obj.put("phone", trimmedPhone);
            obj.put("spoiler", mode == PEER_MODE_SPOILER || alsoSpoiler);
            obj.put("name", cachedName != null ? cachedName.trim() : "");
            obj.put("updatedAt", System.currentTimeMillis());
            prefs.edit().putString(buildPeerPrefKey(account, userId), obj.toString()).apply();
            notifyChanged(account);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void removePeerRule(int account, long userId) {
        SharedPreferences prefs = getPrefs();
        if (prefs == null || userId == 0) {
            return;
        }
        prefs.edit().remove(buildPeerPrefKey(account, userId)).apply();
        notifyChanged(account);
    }

    @NonNull
    public static List<PeerRule> getPeerRules(int account) {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) {
            return Collections.emptyList();
        }
        String prefix = KEY_PEER_PREFIX + getAccountScopeKey(account) + "_";
        ArrayList<PeerRule> rules = new ArrayList<>();
        Map<String, ?> all = prefs.getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.startsWith(prefix) || !(entry.getValue() instanceof String)) {
                continue;
            }
            try {
                long uid = Long.parseLong(key.substring(prefix.length()));
                PeerRule rule = parsePeerRule(uid, (String) entry.getValue());
                if (rule != null && rule.mode != PEER_MODE_DEFAULT) {
                    rules.add(rule);
                }
            } catch (Exception ignore) {
            }
        }
        Collections.sort(rules, (a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        return rules;
    }

    public static int clearAllPeerRules(int account) {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) {
            return 0;
        }
        String prefix = KEY_PEER_PREFIX + getAccountScopeKey(account) + "_";
        SharedPreferences.Editor editor = prefs.edit();
        int removed = 0;
        for (String key : prefs.getAll().keySet()) {
            if (key != null && key.startsWith(prefix)) {
                editor.remove(key);
                removed++;
            }
        }
        if (removed > 0) {
            editor.apply();
            notifyChanged(account);
        }
        return removed;
    }

    @Nullable
    private static PeerRule parsePeerRule(long userId, String raw) {
        try {
            JSONObject obj = new JSONObject(raw);
            int mode = obj.optInt("mode", PEER_MODE_DEFAULT);
            String phone = obj.optString("phone", "");
            boolean spoiler = obj.optBoolean("spoiler", mode == PEER_MODE_SPOILER);
            String name = obj.optString("name", "");
            long updatedAt = obj.optLong("updatedAt", 0L);
            if (mode == PEER_MODE_SPOOF && TextUtils.isEmpty(phone.trim())) {
                return null;
            }
            return new PeerRule(userId, mode, phone, spoiler, name, updatedAt);
        } catch (Exception e) {
            return null;
        }
    }

    private static String buildPeerPrefKey(int account, long userId) {
        return KEY_PEER_PREFIX + getAccountScopeKey(account) + "_" + userId;
    }

    public static String formatDisplayPhone(@Nullable String raw) {
        if (TextUtils.isEmpty(raw)) {
            return "";
        }
        String trimmed = raw.trim();
        boolean hasMaskOrLetters = false;
        boolean hasCustomPunctuation = false;
        int digitCount = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isDigit(c)) {
                digitCount++;
            } else if (c == '+') {
                if (i != 0) {
                    hasCustomPunctuation = true;
                }
            } else if (c == ' ' || c == '-' || c == '(' || c == ')') {
                hasCustomPunctuation = true;
            } else {
                hasMaskOrLetters = true;
            }
        }
        if (hasMaskOrLetters || digitCount < 3) {
            return trimmed;
        }
        if (hasCustomPunctuation) {
            return trimmed;
        }
        String digits = PhoneFormat.stripExceptNumbers(trimmed);
        if (TextUtils.isEmpty(digits)) {
            return trimmed;
        }
        String formatted = PhoneFormat.getInstance().format("+" + digits);
        return !TextUtils.isEmpty(formatted) ? formatted : ("+" + digits);
    }

    public static CharSequence applySpoilerSpan(@NonNull CharSequence text) {
        SpannableStringBuilder ssb = new SpannableStringBuilder(text);
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags = TextStyleSpan.FLAG_STYLE_SPOILER;
        ssb.setSpan(new TextStyleSpan(run), 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return ssb;
    }

    @NonNull
    public static String resolveCheckPhoneBannerText(int account, @Nullable TLRPC.User selfUser) {
        if (selfUser == null) {
            return "";
        }
        String fallbackReal = !TextUtils.isEmpty(selfUser.phone)
                ? PhoneFormat.getInstance().format("+" + selfUser.phone)
                : "";
        if (!isMaskCheckPhoneBanner(account)) {
            return LocaleController.formatString(R.string.CheckPhoneNumber, fallbackReal);
        }
        int ownMode = getOwnMode(account);
        if (ownMode == OWN_MODE_HIDE) {
            return LocaleController.getString(R.string.MirrlyPhoneCheckBannerHidden);
        }
        if (ownMode == OWN_MODE_SPOOF) {
            String spoofed = formatDisplayPhone(getOwnSpoofPhone(account));
            if (!TextUtils.isEmpty(spoofed)) {
                return LocaleController.formatString(R.string.CheckPhoneNumber, spoofed);
            }
        }
        return LocaleController.formatString(R.string.CheckPhoneNumber, fallbackReal);
    }

    @NonNull
    public static ResolvedPhone resolveSelfPhone(int account, @Nullable TLRPC.User selfUser, boolean tempRevealed) {
        String realRaw = selfUser != null && !TextUtils.isEmpty(selfUser.phone) ? selfUser.phone : "";
        String realFormatted = !TextUtils.isEmpty(realRaw) ? PhoneFormat.getInstance().format("+" + realRaw) : "";
        int ownMode = getOwnMode(account);

        if (ownMode == OWN_MODE_HIDE) {
            if (!tempRevealed) {
                String hidden = LocaleController.getString(R.string.MirrlyPhoneHiddenLabel);
                return new ResolvedPhone(
                        true,
                        true,
                        true,
                        false,
                        false,
                        true,
                        true,
                        false,
                        false,
                        hidden,
                        hidden,
                        null
                );
            }
            boolean hasReal = !TextUtils.isEmpty(realRaw);
            String shown = hasReal ? realFormatted : LocaleController.getString(R.string.NumberUnknown);
            return new ResolvedPhone(
                    true,
                    true,
                    false,
                    false,
                    false,
                    true,
                    true,
                    hasReal,
                    hasReal && realRaw.matches("888\\d{8}"),
                    shown,
                    shown,
                    hasReal ? ("+" + realRaw) : null
            );
        }

        if (ownMode == OWN_MODE_SPOOF) {
            String spoofFormatted = formatDisplayPhone(getOwnSpoofPhone(account));
            return new ResolvedPhone(
                    true,
                    true,
                    false,
                    false,
                    true,
                    true,
                    false,
                    !TextUtils.isEmpty(spoofFormatted),
                    false,
                    spoofFormatted,
                    spoofFormatted,
                    spoofFormatted
            );
        }

        boolean hasReal = !TextUtils.isEmpty(realRaw);
        String shown = hasReal ? realFormatted : LocaleController.getString(R.string.NumberUnknown);
        return new ResolvedPhone(
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                hasReal,
                hasReal && realRaw.matches("888\\d{8}"),
                shown,
                shown,
                hasReal ? ("+" + realRaw) : null
        );
    }

    @NonNull
    public static ResolvedPhone resolveUserPhone(
            int account,
            @Nullable TLRPC.User user,
            @Nullable String vcardPhone,
            boolean tempRevealed
    ) {
        long selfId = UserConfig.getInstance(account).getClientUserId();
        if (user != null && selfId > 0 && user.id == selfId) {
            return resolveSelfPhone(account, user, tempRevealed);
        }

        PeerRule peerRule = user != null ? getPeerRule(account, user.id) : null;
        String baseRaw = !TextUtils.isEmpty(vcardPhone)
                ? vcardPhone
                : (user != null && !TextUtils.isEmpty(user.phone) ? user.phone : "");
        boolean hasServerOrVcard = !TextUtils.isEmpty(baseRaw);

        if (peerRule != null && peerRule.mode == PEER_MODE_SPOOF) {
            String spoofFormatted = formatDisplayPhone(peerRule.customPhone);
            boolean useSpoiler = peerRule.alsoSpoiler;
            boolean showSpoiler = useSpoiler && !tempRevealed;
            CharSequence display = showSpoiler ? applySpoilerSpan(spoofFormatted) : spoofFormatted;
            return new ResolvedPhone(
                    true,
                    false,
                    false,
                    showSpoiler,
                    true,
                    true,
                    useSpoiler,
                    !showSpoiler && !TextUtils.isEmpty(spoofFormatted),
                    false,
                    display,
                    spoofFormatted,
                    !showSpoiler ? spoofFormatted : null
            );
        }

        if (peerRule != null && peerRule.mode == PEER_MODE_SPOILER && hasServerOrVcard) {
            String formatted = PhoneFormat.getInstance().format("+" + baseRaw);
            if (!tempRevealed) {
                return new ResolvedPhone(
                        true,
                        false,
                        false,
                        true,
                        false,
                        true,
                        true,
                        false,
                        false,
                        applySpoilerSpan(formatted),
                        formatted,
                        null
                );
            }
            return new ResolvedPhone(
                    true,
                    false,
                    false,
                    false,
                    false,
                    true,
                    true,
                    true,
                    baseRaw.matches("888\\d{8}"),
                    formatted,
                    formatted,
                    "+" + baseRaw
            );
        }

        if (hasServerOrVcard) {
            String formatted = PhoneFormat.getInstance().format("+" + baseRaw);
            return new ResolvedPhone(
                    true,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    true,
                    baseRaw.matches("888\\d{8}"),
                    formatted,
                    formatted,
                    "+" + baseRaw
            );
        }

        String hidden = LocaleController.getString(R.string.PhoneHidden);
        return new ResolvedPhone(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                hidden,
                hidden,
                null
        );
    }

    @NonNull
    public static String generatePreset(int presetType, @Nullable TLRPC.User selfUser) {
        if (presetType == PRESET_COLLECTIBLE_888) {
            int p1 = RANDOM.nextInt(10000);
            int p2 = RANDOM.nextInt(10000);
            return String.format(Locale.US, "+888 %04d %04d", p1, p2);
        }
        if (presetType == PRESET_MASKED_TAIL) {
            String raw = selfUser != null && !TextUtils.isEmpty(selfUser.phone)
                    ? PhoneFormat.stripExceptNumbers(selfUser.phone)
                    : "";
            if (raw.length() >= 6) {
                String tail = raw.substring(raw.length() - 4);
                String prefix = raw.length() >= 11 && raw.startsWith("7")
                        ? "+7"
                        : (raw.startsWith("1") ? "+1" : ("+" + raw.substring(0, Math.min(2, raw.length() - 4))));
                return prefix + " ••• •••-" + tail.substring(0, 2) + "-" + tail.substring(2, 4);
            }
            return "+7 ••• •••-00-42";
        }
        int a = 200 + RANDOM.nextInt(799);
        int b = 100 + RANDOM.nextInt(899);
        int c = 1000 + RANDOM.nextInt(9000);
        return String.format(Locale.US, "+1 (%03d) %03d-%04d", a, b, c);
    }

    @NonNull
    public static String resolveUserDisplayName(@Nullable TLRPC.User user, long fallbackUserId) {
        if (user != null) {
            String name = UserObject.getUserName(user);
            if (!TextUtils.isEmpty(name)) {
                return name;
            }
            String username = UserObject.getPublicUsername(user);
            if (!TextUtils.isEmpty(username)) {
                return "@" + username;
            }
        }
        return "ID " + fallbackUserId;
    }

    public static void notifyChanged(int account) {
        AndroidUtilities.runOnUIThread(() -> {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mirrlyPhonePrivacyChanged, account);
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_PHONE);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_PHONE);
        });
    }
}
