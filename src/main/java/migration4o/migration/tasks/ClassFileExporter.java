package migration4o.migration.tasks;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.HashSet;

import migration4o.migration.ExportOperation;
import migration4o.migration.ObjectExporter;
import migration4o.migration.XSDBuilder;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.ui.ClassExportConfig;
import migration4o.util.tools.structuredwriter.StructuredWriter;

/**
 * Exports a single schema class to an output file (XML / JSON / JS / …) plus an
 * optional per-class XSD file.
 * <p>
 * Coordinates the following atomic tasks:
 * <ul>
 * <li>{@link ObjectExportLoop} – object iteration, limit enforcement, reference
 * propagation
 * <li>{@link ClassXsdWriter} – per-class XSD generation
 * <li>{@link HtmlViewerTask} – HTML viewer generation
 * </ul>
 */
public class ClassFileExporter {

    private final ExportOperation operation;

    public ClassFileExporter(ExportOperation operation) {
        this.operation = operation;
    }

    /**
     * Exports {@code schemaClass} to {@code xmlPath}.
     *
     * @param schemaClass Reference-schema class definition (drives field
     * mapping)
     * @param dbSchemaClass Database-schema class (carries object IDs)
     * @param xmlPath Destination data file
     * @param xsdPath Destination per-class XSD file; may be {@code null}
     * @param config Optional export config with criteria / destination name
     * overrides; {@code null} → export all objects
     */
    public void exportClassToFile(DOSchemaClass schemaClass, DOSchemaClass dbSchemaClass, Path xmlPath, Path xsdPath, ClassExportConfig config) throws Exception {

        // Use shared XSD builder if available, otherwise create a fresh one
        operation.xsdBuilder = operation.sharedXSDBuilder != null ? operation.sharedXSDBuilder : new XSDBuilder(operation.dbContext);
        if (operation.sharedXSDBuilder == null) {
            operation.xsdBuilder.startExportRoot();
        }

        Writer outputWriter = null;
        try {
            outputWriter = new FileWriter(xmlPath.toFile());
            operation.xmlWriter = new StructuredWriter(operation.getStructuredWriterAPI(), outputWriter, xmlPath);

            operation.baseOutputPath = xmlPath.getParent().getParent().toString();
            operation.exportNativeIds = operation.shouldExportNativeIdsForCurrentFormat();
            operation.exportConfig = config;
            if (!operation.useSharedTracking) {
                operation.exportedObjectIds = new HashSet<>();
            }

            ObjectExporter objectExporter = new ObjectExporter(operation, operation.xmlWriter, operation.xsdBuilder);
            objectExporter.reset();

            // Re-register any already-referenced classes so the tracker is
            // consistent
            if (operation.referencedClassTracker != null) {
                for (String className : operation.referencedClassTracker.getReferencedClasses()) {
                    operation.referencedClassTracker.registerReferencedClass(className);
                }
            }

            String module = ModulePathUtil.getModuleNameForXml(xmlPath, operation);

            // Write the root element with optional schema reference
            if (operation.isXMLFormat() && operation.sharedXSDBuilder != null) {
                String relativeSchemaPath = ModulePathUtil.getSchemaLocationForXml(xmlPath, operation.baseOutputPath, operation);
                operation.xmlWriter.openRootStructure("export", relativeSchemaPath);
                operation.xmlWriter.metadata(schemaClass.getMetadata(module));
            } else if (operation.isXMLFormat()) {
                operation.xmlWriter.openRootStructure("export", null);
                operation.xmlWriter.metadata(schemaClass.getMetadata(module));
            } else {
                operation.xmlWriter.openStructure("export");
                operation.xmlWriter.metadata(schemaClass.getMetadata(module));
            }
            operation.xmlWriter.openStructure("objects");

            new ObjectExportLoop(operation).run(schemaClass, dbSchemaClass, objectExporter);

            operation.xmlWriter.closeStructure("objects");
            operation.xmlWriter.closeStructure("export");

            if (outputWriter != null) {
                outputWriter.close();
                outputWriter = null;
            }

            new HtmlViewerTask(operation).generateIfNeeded(xmlPath, schemaClass, config);
            new ClassXsdWriter(operation).writeIfNeeded(xsdPath);

        } finally {
            if (outputWriter != null) {
                try {
                    outputWriter.close();
                } catch (IOException e) {
                    /* ignore */ }
            }
        }
    }
}
