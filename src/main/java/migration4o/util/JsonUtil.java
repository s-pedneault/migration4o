package migration4o.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight JSON read/write helpers — no external dependencies.
 * <p>
 * Write-side methods produce JSON fragments (escaped strings, arrays).
 * Read-side methods extract values from a JSON string by key name using simple
 * index-based scanning (adequate for small, well-formed config files).
 */
public class JsonUtil {

    private JsonUtil() {
    }

    // ── Write helpers ────────────────────────────────────────────────────────

    /**
     * Returns a JSON string literal with proper escaping, or the token
     * {@code null} when {@code s} is null.
     */
    public static String jsonString(String s) {
        if (s == null)
            return "null";
        return "\"" + escape(s) + "\"";
    }

    /**
     * Returns a JSON array of string literals, e.g. {@code ["a", "b"]}.
     */
    public static String jsonStringArray(List<String> list) {
        if (list == null || list.isEmpty())
            return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(jsonString(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Escapes a string for embedding inside JSON double-quotes. Returns an
     * empty string when {@code s} is null.
     */
    public static String escape(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    // ── Read helpers ─────────────────────────────────────────────────────────

    /**
     * Reads a JSON string value by key, or {@code null} if the key is absent or
     * the value is the JSON token {@code null}.
     */
    public static String readString(String json, String key) {
        String search = "\"" + key + "\"";
        int ki = json.indexOf(search);
        if (ki < 0)
            return null;
        int colon = json.indexOf(':', ki + search.length());
        if (colon < 0)
            return null;
        // skip whitespace after colon
        int afterColon = colon + 1;
        while (afterColon < json.length() && json.charAt(afterColon) == ' ')
            afterColon++;
        // check for null token
        if (json.startsWith("null", afterColon))
            return null;
        int start = json.indexOf('"', colon + 1);
        if (start < 0)
            return null;
        start++;
        return unescape(json, start);
    }

    /**
     * Reads a JSON boolean value by key, returning {@code defaultVal} if the
     * key is absent.
     */
    public static boolean readBool(String json, String key, boolean defaultVal) {
        String search = "\"" + key + "\"";
        int ki = json.indexOf(search);
        if (ki < 0)
            return defaultVal;
        int colon = json.indexOf(':', ki + search.length());
        if (colon < 0)
            return defaultVal;
        String after = json.substring(colon + 1).trim();
        if (after.startsWith("true"))
            return true;
        if (after.startsWith("false"))
            return false;
        return defaultVal;
    }

    /**
     * Reads a JSON integer value by key, returning {@code defaultVal} if the
     * key is absent or not a valid integer.
     */
    public static int readInt(String json, String key, int defaultVal) {
        String search = "\"" + key + "\"";
        int ki = json.indexOf(search);
        if (ki < 0)
            return defaultVal;
        int colon = json.indexOf(':', ki + search.length());
        if (colon < 0)
            return defaultVal;
        String after = json.substring(colon + 1).trim();
        StringBuilder num = new StringBuilder();
        for (int i = 0; i < after.length(); i++) {
            char ch = after.charAt(i);
            if (ch == '-' || (ch >= '0' && ch <= '9'))
                num.append(ch);
            else
                break;
        }
        try {
            return Integer.parseInt(num.toString());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    /**
     * Reads a JSON string value by key and converts it to an enum constant,
     * returning {@code defaultVal} if the key is absent or the value is not a
     * valid constant name.
     */
    public static <E extends Enum<E>> E readEnum(String json, String key, Class<E> enumClass, E defaultVal) {
        String val = readString(json, key);
        if (val == null)
            return defaultVal;
        try {
            return Enum.valueOf(enumClass, val);
        } catch (IllegalArgumentException e) {
            return defaultVal;
        }
    }

    /**
     * Reads a JSON array of strings by key, returning an empty list if the key
     * is absent.
     */
    public static List<String> readStringList(String json, String key) {
        List<String> result = new ArrayList<>();
        String search = "\"" + key + "\"";
        int ki = json.indexOf(search);
        if (ki < 0)
            return result;
        int colon = json.indexOf(':', ki + search.length());
        if (colon < 0)
            return result;
        int open = json.indexOf('[', colon);
        if (open < 0)
            return result;
        int close = findMatchingBracket(json, open);
        if (close < 0)
            return result;
        String arr = json.substring(open + 1, close);
        int i = 0;
        while (i < arr.length()) {
            int q1 = arr.indexOf('"', i);
            if (q1 < 0)
                break;
            int q2 = findClosingQuote(arr, q1 + 1);
            if (q2 < 0)
                break;
            result.add(unescapeSegment(arr.substring(q1 + 1, q2)));
            i = q2 + 1;
        }
        return result;
    }

    // ── Structural helpers (public for domain-specific parsers) ──────────────

    /**
     * Finds the index of the {@code ]} that matches the {@code [} at
     * {@code openPos}, correctly skipping nested brackets and strings.
     *
     * @return index of matching bracket, or -1
     */
    public static int findMatchingBracket(String s, int openPos) {
        int depth = 0;
        boolean inString = false;
        for (int i = openPos; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && inString) {
                i++;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString)
                continue;
            if (ch == '[')
                depth++;
            if (ch == ']') {
                depth--;
                if (depth == 0)
                    return i;
            }
        }
        return -1;
    }

    /**
     * Finds the index of the {@code }} that matches the <code>{</code> at
     * {@code openPos}, correctly skipping nested braces and strings.
     *
     * @return index of matching brace, or -1
     */
    public static int findMatchingBrace(String s, int openPos) {
        int depth = 0;
        boolean inString = false;
        for (int i = openPos; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && inString) {
                i++;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString)
                continue;
            if (ch == '{')
                depth++;
            if (ch == '}') {
                depth--;
                if (depth == 0)
                    return i;
            }
        }
        return -1;
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private static int findClosingQuote(String s, int startAfterOpenQuote) {
        for (int i = startAfterOpenQuote; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\') {
                i++;
                continue;
            }
            if (ch == '"')
                return i;
        }
        return -1;
    }

    /**
     * Unescapes a JSON string value starting at {@code start} (the character
     * after the opening quote) and stopping at the closing quote.
     */
    private static String unescape(String json, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                switch (next) {
                case '"':
                    sb.append('"');
                    i++;
                    break;
                case '\\':
                    sb.append('\\');
                    i++;
                    break;
                case 'n':
                    sb.append('\n');
                    i++;
                    break;
                case 'r':
                    sb.append('\r');
                    i++;
                    break;
                case 't':
                    sb.append('\t');
                    i++;
                    break;
                default:
                    sb.append(ch);
                    break;
                }
            } else if (ch == '"') {
                break;
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    /**
     * Unescapes a pre-extracted JSON string segment (between quotes,
     * exclusive).
     */
    private static String unescapeSegment(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                case '"':
                    sb.append('"');
                    i++;
                    break;
                case '\\':
                    sb.append('\\');
                    i++;
                    break;
                case 'n':
                    sb.append('\n');
                    i++;
                    break;
                case 'r':
                    sb.append('\r');
                    i++;
                    break;
                case 't':
                    sb.append('\t');
                    i++;
                    break;
                default:
                    sb.append(ch);
                    break;
                }
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
