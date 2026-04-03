package migration4o.util;

import java.io.File;

import migration4o.database.DODatabaseDelegate;
import migration4o.util.formatters.FormatterContext;

public class DatabaseFileUtil {

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
        String extension = FileUtil.getExtension(originalFileName, "pdf");
        String fileName = parameter + "." + extension.toLowerCase();
        // String filePath = "file/" + context.schemaClass.attributes.destinationName + "/" + fileName;
        String filePath = "file/" + fileName;

        File destination = new File(context.destinationFolder.toString(), filePath);
        return destination;

    }
}