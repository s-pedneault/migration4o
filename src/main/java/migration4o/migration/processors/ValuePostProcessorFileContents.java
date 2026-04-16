package migration4o.migration.processors;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import migration4o.migration.format.ExportCurrentState;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.schema.DOPostProcessorAttribute;
import migration4o.util.DatabaseFileUtil;
import migration4o.util.FileUtil;

/**
 * Postprocessor for file-holder objects. Replaces a {@code contents} byte[] field with
 * the actual bytes read from disk. Falls back to a demo asset when the file is not found.
 *
 * <p>Expected spec: {@code fileContents(path=relative/path/[ID])} where {@code [ID]} is
 * substituted with the object's {@code mID} value at runtime.
 */
public class ValuePostProcessorFileContents implements ValuePostProcessor {

    @Override
    public Object processField(Object object, Object fieldValue, DOSchemaField schemaField, ExportCurrentState ctx, DOPostProcessorAttribute attributes) {
        String effectiveSource = schemaField.isVirtualField() ? schemaField.getVirtualFieldName() : schemaField.attributes.source;
        if (!effectiveSource.equals("contents")) {
            return fieldValue;
        }

        // 1. Get mID value
        Object mIDValue = ctx.delegate.getStoredFieldValue(object, "mID");
        if (mIDValue == null) {
            return fieldValue;
        }
        String mID = mIDValue.toString();

        // 2. Locate file using attributes.path + mID
        String pathParam = attributes.params.get("path");
        if (pathParam == null || pathParam.isBlank()) {
            System.err.println("[WARN] ValuePostProcessorFileContents: no 'path' parameter configured (mID=" + mID + ")");
            return fieldValue;
        }
        File sourceFile = DatabaseFileUtil.resolveSourceFile(ctx.delegate.getFilePath(), pathParam, mID);

        // 3. If file exists, read and return its bytes
        if (sourceFile.exists() && sourceFile.isFile()) {
            try {
                return Files.readAllBytes(sourceFile.toPath());
            } catch (IOException e) {
                System.err.println("[WARN] ValuePostProcessorFileContents: could not read " + sourceFile + ": " + e.getMessage());
            }
        }

        // 4. Fall back to a demo asset
        String originalFileName = DatabaseFileUtil.getOriginalFileName(ctx.delegate, object);
        byte[] demoBytes = loadDemoAssetBytes(originalFileName);
        if (demoBytes != null) {
            return demoBytes;
        }

        String extension = FileUtil.getExtension(originalFileName, "pdf");
        System.err.println("[WARN] ValuePostProcessorFileContents: no demo asset found for extension '" + extension + "' (mID=" + mID + ")");
        return fieldValue;
    }

    private byte[] loadDemoAssetBytes(String originalFileName) {
        String extension = FileUtil.getExtension(originalFileName, "pdf");
        return DatabaseFileUtil.loadDemoAssetBytes(extension);
    }

}
