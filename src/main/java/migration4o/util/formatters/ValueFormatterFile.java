package migration4o.util.formatters;

import java.io.File;

import migration4o.database.DODatabaseDelegate;
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

        String sourcePath = new File(new File(delegate.getFilePath()).getParent(), parameter).getPath();
        sourcePath = sourcePath.replace("[ID]", value);
        File sourceFile = new File(sourcePath);
        // System.out.println("Processing file for value " + value + " with source path: " + sourcePath + " (exists: " + sourceFile.exists() + ", isFile: " + sourceFile.isFile() + ")");

        String originalFileName = DatabaseFileUtil.getOriginalFileName(delegate, context.currentObject);
        String extension = FileUtil.getExtension(originalFileName, "pdf");

        if (!sourceFile.exists() || !sourceFile.isFile()) {
            sourceFile = new File(getClass().getResource("/assets/demo." + extension).getFile());
            if (!sourceFile.exists() || !sourceFile.isFile()) {
                sourceFile = new File(getClass().getResource("/assets/demo.pdf").getFile());
                System.err.println("Unable to use default demo asset for " + value + " with extension " + extension);
                return null;
            }
        }
        String fileName = ObjectPreviewFile.getPreviewFilePath(originalFileName, value);
        String filePath = "file/" + fileName;

        File destinationPath = new File(context.destinationFolder.toString(), filePath);

        FileUtil.copyFile(sourceFile, destinationPath);

        return filePath;
    }

}
