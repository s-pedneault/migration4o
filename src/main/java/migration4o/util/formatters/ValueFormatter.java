package migration4o.util.formatters;

import migration4o.database.DODatabaseDelegate;

public interface ValueFormatter {

    public static String formatValue(DODatabaseDelegate delegate, FormatterContext context, String value, String formatString) {
        if (value == null)
            return null;
        if (formatString == null || formatString.trim().isEmpty()) {
            return value;
        }
        String parameter = null;
        int openParen = formatString.indexOf('(');
        int closeParen = formatString.lastIndexOf(')');
        if (openParen != -1 && closeParen != -1 && closeParen > openParen) {
            parameter = formatString.substring(openParen + 1, closeParen).trim();
            formatString = formatString.substring(0, openParen).trim();
        }

        String normalizedKeyword = formatString.trim().toUpperCase();
        if (normalizedKeyword.equals("LOWERCASE")) {
            return ValueFormatterLowercase.formatter.format(delegate, context, value, parameter);
        }
        if (normalizedKeyword.equals("UPPERCASE")) {
            return ValueFormatterUppercase.formatter.format(delegate, context, value, parameter);
        }
        if (normalizedKeyword.equals("TRIM")) {
            return ValueFormatterTrim.formatter.format(delegate, context, value, parameter);
        }
        if (normalizedKeyword.startsWith("FILE")) {
            return ValueFormatterFile.formatter.format(delegate, context, value, parameter);
        }
        return value;
    }

    public String format(DODatabaseDelegate delegate, FormatterContext context, String value, String parameter);

}
