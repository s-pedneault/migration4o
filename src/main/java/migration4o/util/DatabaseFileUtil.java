package migration4o.util;

import java.io.File;
import java.io.InputStream;

import migration4o.database.DODatabaseDelegate;
import migration4o.util.formatters.FormatterContext;
import migration4o.util.previews.ObjectPreviewFile;

public class DatabaseFileUtil {

    /**
     * Resolves a source file by combining the database folder, a path template, and an object ID.
     * The token {@code [ID]} in the template is substituted with the given mID value.
     *
     * <p>Example: {@code resolveSourceFile("/db/base.db4o", "fichiers/[ID]", "42")} →
     * {@code /db/fichiers/42}.
     *
     * @param dbFilePath   Absolute path to the database file (its parent folder is used as the base).
     * @param pathTemplate Relative path template containing {@code [ID]} as a substitution token.
     * @param mID          Object ID value substituted for {@code [ID]}.
     * @return Resolved {@link File}.
     */
    public static File resolveSourceFile(String dbFilePath, String pathTemplate, String mID) {
        String dbFolder = new File(dbFilePath).getParent();
        String resolved = new File(dbFolder, pathTemplate).getPath().replace("[ID]", mID);
        return new File(resolved);
    }

    /**
     * Loads the bytes of a demo asset from the classpath, trying {@code /assets/demo.<extension>}
     * first and falling back to {@code /assets/demo.pdf}. Returns {@code null} if neither is found.
     *
     * @param extension File extension to try first (e.g. {@code "docx"}).
     * @return Demo file bytes, or {@code null} if no demo asset is available.
     */
    public static byte[] loadDemoAssetBytes(String extension) {
        String[] candidates = { "/assets/demo." + extension, "/assets/demo.pdf" };
        for (String candidate : candidates) {
            try (InputStream is = DatabaseFileUtil.class.getResourceAsStream(candidate)) {
                if (is != null) {
                    return is.readAllBytes();
                }
            } catch (Exception e) {
                // try next candidate
            }
        }
        return null;
    }

    public static String getOriginalFileName(DODatabaseDelegate delegate, Object fileObject) {
        String originalFileName = null;
        if (fileObject != null) {
            Object nomValue = delegate.getStoredFieldValue(fileObject, "mNom");
            if (nomValue != null) {
                originalFileName = nomValue.toString().trim();
            }
        }
        if (originalFileName == null || originalFileName.isEmpty()) {
            System.err.println("No mNom provided for file " + fileObject);
            return null;
        }
        return originalFileName;
    }

    public static File getExportedFile(DODatabaseDelegate delegate, FormatterContext context, Object fileObject, String parameter) {
        String originalFileName = getOriginalFileName(delegate, fileObject);
        if (originalFileName == null) {
            return null;
        }
        String fileName = ObjectPreviewFile.getPreviewFilePath(originalFileName, parameter);
        //   String extension = FileUtil.getExtension(originalFileName, "pdf");
        // String fileName = parameter;// + "." + extension.toLowerCase();
        // String filePath = "file/" + context.schemaClass.attributes.destinationName + "/" + fileName;
        String filePath = "file/" + fileName;

        File destination = new File(context.destinationFolder.toString(), filePath);
        return destination;

    }
}