package migration4o.util.previews;

import migration4o.database.DODatabaseDelegate;
import migration4o.util.formatters.FormatterContext;

public interface ObjectPreview {

    public static String generatePreview(DODatabaseDelegate delegate, FormatterContext context, String value, String previewString) {
        if (value == null)
            return null;
        if (previewString == null || previewString.trim().isEmpty()) {
            return null;
        }
        String parameter = null;
        int openParen = previewString.indexOf('(');
        int closeParen = previewString.lastIndexOf(')');
        if (openParen != -1 && closeParen != -1 && closeParen > openParen) {
            parameter = previewString.substring(openParen + 1, closeParen).trim();
            previewString = previewString.substring(0, openParen).trim();
        }

        String normalizedKeyword = previewString.trim().toUpperCase();
        if (normalizedKeyword.startsWith("FILE")) {
            return ObjectPreviewFile.preview.generate(delegate, context, value, parameter);
        }
        return null;
    }

    public String generate(DODatabaseDelegate delegate, FormatterContext context, String value, String parameter);

}
