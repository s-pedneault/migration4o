package migration4o.util;

public class XMLUtil {
    /**
     * Removes characters that are invalid in XML 1.0. Control characters (except
     * tab, newline, carriage return) are replaced with space.
     */
    public static String sanitizeXMLCharacters(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // Valid XML 1.0 characters
            if (c == 0x9 || c == 0xA || c == 0xD || (c >= 0x20 && c <= 0xD7FF) || (c >= 0xE000 && c <= 0xFFFD)) {
                sb.append(c);
            } else {
                // Replace invalid characters with space
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    /**
     * Escapes special XML characters in text content and removes invalid XML
     * characters. XML 1.0 only allows: - #x9 (tab), #xA (line feed), #xD (carriage
     * return) - #x20-#xD7FF, #xE000-#xFFFD, #x10000-#x10FFFF
     */
    public static String xmlEscape(String text) {
        if (text == null) {
            return "";
        }

        // First, sanitize invalid XML characters
        text = sanitizeXMLCharacters(text);

        // Then, escape XML entities
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

}
