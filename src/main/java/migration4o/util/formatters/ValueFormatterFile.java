package migration4o.util.formatters;

import java.io.File;

import migration4o.database.DODatabaseDelegate;
import migration4o.migration.FilesDestination;
import migration4o.util.DatabaseFileUtil;
import migration4o.util.FileUtil;
import migration4o.util.previews.ObjectPreviewFile;

public class ValueFormatterFile implements ValueFormatter {

    public static final ValueFormatterFile formatter = new ValueFormatterFile();

    @Override
    public String format(DODatabaseDelegate delegate, FormatterContext context, String value, String parameter) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        if (parameter == null || parameter.trim().isEmpty()) {
            return null;
        }

        // When embedding files inline, skip the disk copy entirely.
        // The contenu (@contents) virtual field carries the Base64 bytes instead.
        if (context.filesDestination == FilesDestination.EMBED) {
            return null;
        }

        String originalFileName = DatabaseFileUtil.getOriginalFileName(delegate, context.currentObject);
        String extension = FileUtil.getExtension(originalFileName, "pdf");
        String fileName = ObjectPreviewFile.getPreviewFilePath(originalFileName, value);
        String filePath = "file/" + fileName;
        File destinationPath = new File(context.destinationFolder.toString(), filePath);

        File sourceFile = DatabaseFileUtil.resolveSourceFile(delegate.getFilePath(), parameter, value);

        if (sourceFile.exists() && sourceFile.isFile()) {
            FileUtil.copyFile(sourceFile, destinationPath);
        } else {
            byte[] demoBytes = DatabaseFileUtil.loadDemoAssetBytes(extension);
            if (demoBytes == null) {
                System.err.println("[ValueFormatterFile] No demo asset found for extension '" + extension + "' (value=" + value + ")");
                return null;
            }
            FileUtil.writeBytes(demoBytes, destinationPath);
        }

        return filePath;
    }

}
