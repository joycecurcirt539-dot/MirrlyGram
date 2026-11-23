package org.telegram.messenger.mirrly;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formats localized and cloud-loaded UI strings by replacing standalone Telegram brand
 * references with Mirrlygram while keeping protected product names, FAQ/Features entries,
 * Telegram Passport, URLs, and handles untouched.
 */
public final class MirrlyBrandFormatter {

    public static final String BRAND_NAME = "Mirrlygram";
    public static final String BRAND_NAME_UPPER = "MIRRLYGRAM";

    private static final Set<String> EXCLUDED_KEYS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "TelegramFAQ",
            "TelegramFaq",
            "TelegramFeatures",
            "TelegramPremium",
            "TelegramPremiumShort",
            "TelegramStars",
            "TelegramBusiness",
            "TelegramPassport",
            "TelegramPassportDelete",
            "TelegramPassportDeleteTitle",
            "TelegramPassportCreatePassword",
            "MenuTelegramStars",
            "SponsoredMessageAlertLearnMoreUrl",
            "PromoteUrl",
            "BotDefaultPrivacyPolicy",
            "MirrlyInterfaceQuickCatTelegram",
            "MirrlyInterfaceQuickCatMirrly",
            "MirrlyQuickNameSettings",
            "MirrlyQuickNameMirrlySettings",
            "MirrlyInterfaceTopSettingsDesc",
            "MirrlyDrawerItemTgSettings",
            "MirrlyDrawerItemMirrlySettings",
            "MirrlyAboutClientDesc",
            "MirrlyAboutVersionFooter"
    )));

    private static final Pattern PROTECTED_PATTERN = Pattern.compile(
            // 1. URLs, Telegram domains, emails, handles, and internal Java package names
            "https?://[^\\s)\"'<>\\]]+"
            + "|(?<![\\p{L}\\p{N}_-])(?:[a-z0-9-]+\\.)*telegram\\.(?:org|me|dog)(?:/[^\\s)\"'<>\\]]*)?"
            + "|(?<![\\p{L}\\p{N}_-])t\\.me(?:/[^\\s)\"'<>\\]]*)?"
            + "|[A-Za-z0-9._%+-]+@telegram\\.org"
            + "|@Telegram[A-Za-z0-9_]*"
            + "|org\\.telegram\\.[A-Za-z0-9_.]+"
            // 2. Protected product names: Premium, Stars, Business, Features, FAQ, Passport
            + "|(?:Telegram|Телеграм|تيليجرام|텔레그램)[*_]{0,2}\\s+[*_]{0,2}Premium"
            + "|Телеграм[*_]{0,2}\\s+[*_]{0,2}Премиум"
            + "|تيليجرام[*_]{0,2}\\s+[*_]{0,2}المميز"
            + "|(?:Telegram|Телеграм|تيليجرام|텔레그램)[*_]{0,2}\\s+[*_]{0,2}Stars"
            + "|Зв[её]зд[аы]?[*_]{0,2}\\s+[*_]{0,2}(?:Telegram|Телеграм)"
            + "|(?:Telegram|Телеграм)[*_]{0,2}\\s+[*_]{0,2}Зв[её]зд[\\p{L}]*"
            + "|(?:Telegram|Телеграм|تيليجرام|텔레그램)[*_]{0,2}\\s+[*_]{0,2}(?:for\\s+)?Business"
            + "|(?:Telegram|Телеграм)[*_]{0,2}\\s+[*_]{0,2}(?:для\\s+)?бизнес[\\p{L}]*"
            + "|for\\s+business"
            + "|(?:Telegram|Телеграм|تيليجرام|텔레그램)[*_]{0,2}\\s+[*_]{0,2}Features?"
            + "|exclusive\\s+Telegram\\s+features"
            + "|Возможност[\\p{L}]+[*_]{0,2}\\s+[*_]{0,2}(?:Telegram|Телеграм)"
            + "|(?:Telegram|Телеграм)[\\s-]+(?:Funktionen|functies)"
            + "|(?:Funzioni\\s+di|Recursos\\s+do|Aprende\\s+sobre|ميزات)\\s+(?:Telegram|تيليجرام)"
            + "|텔레그램\\s+기능\\s+소개"
            + "|(?:Telegram|Телеграм|تيليجرام|텔레그램)[*_]{0,2}\\s+[*_]{0,2}FAQ"
            + "|(?:Вопросы|Частые\\s+вопросы)\\s+о\\s+(?:Telegram|Телеграм)"
            + "|FAQ\\s+(?:di\\s+)?Telegram"
            + "|preguntas\\s+frecuentes\\s+de\\s+Telegram"
            + "|veelgestelde\\s+vragen(?:</a>\\]\\]>)?\\s+van\\s+Telegram"
            + "|الأسئلة\\s+الشائعة\\s+عن\\s+تيليجرام"
            + "|(?:Telegram|Телеграм|تيليجرام|텔레그램)[*_]{0,2}\\s+[*_]{0,2}Passport"
            + "|Телеграм[*_]{0,2}\\s+[*_]{0,2}Паспорт",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern DUTCH_TELEGRAM_MESSAGES = Pattern.compile("(?:(?<![\\p{L}\\p{N}_])|(?<=\\\\n))Telegramberichten(?![\\p{L}\\p{N}_])");
    private static final Pattern ALL_CAPS_TELEGRAM = Pattern.compile("(?:(?<![\\p{L}\\p{N}_])|(?<=\\\\n))TELEGRAM(?![\\p{L}\\p{N}_])");
    private static final Pattern TITLE_TELEGRAM = Pattern.compile("(?:(?<![\\p{L}\\p{N}_])|(?<=\\\\n))Telegram(?![\\p{L}\\p{N}_])");
    private static final Pattern CYRILLIC_TELEGRAM = Pattern.compile("(?:(?<![\\p{L}\\p{N}_])|(?<=\\\\n))Телеграм(?:а|у|е|ом)?(?![\\p{L}\\p{N}_])");
    private static final Pattern MIRRLY_GRAM_CAMEL = Pattern.compile("MirrlyGram");
    private static final Pattern ARABIC_TELEGRAM = Pattern.compile("تيليجرام");
    private static final Pattern KOREAN_TELEGRAM = Pattern.compile("텔레그램");

    private static final int MAX_CACHE_SIZE = 1024;
    private static final ConcurrentHashMap<String, String> FORMAT_CACHE = new ConcurrentHashMap<>(256);

    private MirrlyBrandFormatter() {
    }

    public static void clearCache() {
        FORMAT_CACHE.clear();
    }

    public static boolean isExcludedKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        return EXCLUDED_KEYS.contains(key);
    }

    public static String format(String key, String value) {
        if (value == null || value.isEmpty() || isExcludedKey(key)) {
            return value;
        }
        final boolean cacheable = value.length() <= 512;
        if (cacheable) {
            String cached = FORMAT_CACHE.get(value);
            if (cached != null) {
                return cached;
            }
        }
        if (!containsBrandCandidateFast(value)) {
            if (cacheable) {
                putCache(value, value);
            }
            return value;
        }

        Matcher matcher = PROTECTED_PATTERN.matcher(value);
        final String formatted;
        if (!matcher.find()) {
            formatted = replaceUnprotected(value);
        } else {
            StringBuilder out = new StringBuilder(value.length());
            int lastEnd = 0;
            do {
                if (matcher.start() > lastEnd) {
                    out.append(replaceUnprotected(value.substring(lastEnd, matcher.start())));
                }
                out.append(matcher.group());
                lastEnd = matcher.end();
            } while (matcher.find());

            if (lastEnd < value.length()) {
                out.append(replaceUnprotected(value.substring(lastEnd)));
            }
            formatted = out.toString();
        }
        if (cacheable) {
            putCache(value, formatted);
        }
        return formatted;
    }

    private static void putCache(String key, String value) {
        if (FORMAT_CACHE.size() >= MAX_CACHE_SIZE) {
            FORMAT_CACHE.clear();
        }
        FORMAT_CACHE.put(key, value);
    }

    private static boolean containsBrandCandidateFast(String value) {
        int len = value.length();
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            if (c == 'T' || c == 'M' || c == '\u0422' || c == '\u062A' || c == '\uD154') {
                return containsBrandCandidate(value);
            }
        }
        return false;
    }

    private static boolean containsBrandCandidate(String value) {
        return value.contains("Telegram")
                || value.contains("TELEGRAM")
                || value.contains("Телеграм")
                || value.contains("MirrlyGram")
                || value.contains("تيليجرام")
                || value.contains("텔레그램");
    }

    private static String replaceUnprotected(String chunk) {
        if (chunk.isEmpty() || !containsBrandCandidate(chunk)) {
            return chunk;
        }
        String result = MIRRLY_GRAM_CAMEL.matcher(chunk).replaceAll(BRAND_NAME);
        result = DUTCH_TELEGRAM_MESSAGES.matcher(result).replaceAll(BRAND_NAME + "berichten");
        result = ALL_CAPS_TELEGRAM.matcher(result).replaceAll(BRAND_NAME_UPPER);
        result = TITLE_TELEGRAM.matcher(result).replaceAll(BRAND_NAME);
        result = CYRILLIC_TELEGRAM.matcher(result).replaceAll(BRAND_NAME);
        result = ARABIC_TELEGRAM.matcher(result).replaceAll(BRAND_NAME);
        result = KOREAN_TELEGRAM.matcher(result).replaceAll(BRAND_NAME);
        return result;
    }
}
