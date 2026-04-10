package migration4o.util.previews;

import java.io.File;

import migration4o.database.DODatabaseDelegate;
import migration4o.util.DatabaseFileUtil;
import migration4o.util.FileUtil;
import migration4o.util.formatters.FormatterContext;

public class ObjectPreviewFile implements ObjectPreview {

    public static final ObjectPreviewFile preview = new ObjectPreviewFile();

    public static final String getPreviewFilePath(String originalFileName, String fileID) {
        String extension = FileUtil.getExtension(originalFileName, "pdf");
        String fileName = fileID;//+ "." + extension.toLowerCase();
        return fileName;
    }

    @Override
    public String generate(DODatabaseDelegate delegate, FormatterContext context, String value, String parameter) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        if (parameter == null || parameter.trim().isEmpty()) {
            return null;
        }

        // context.currentObject is the Fichier DB4O object; value is the mID numeric string (e.g. "84833")
        File file = DatabaseFileUtil.getExportedFile(delegate, context, context.currentObject, value);
        if (file == null)
            return null;
        String altText = DatabaseFileUtil.getOriginalFileName(delegate, context.currentObject);

        // Build a base-href-relative path: strip everything up to and including "file/"
        String absPath = file.getPath();
        int fileIdx = absPath.replace('\\', '/').lastIndexOf("/file/");
        String relativePath = "../" + (fileIdx >= 0 ? absPath.substring(fileIdx + 1) : absPath);

        return "<img src=\"" + escapeAttr(relativePath) + "\" alt=\"" + escapeAttr(altText != null ? altText : value) + "\" />";
    }

    private static String escapeAttr(String s) {
        if (s == null)
            return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

}
