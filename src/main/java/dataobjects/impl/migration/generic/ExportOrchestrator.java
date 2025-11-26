package dataobjects.impl.migration.generic;

import dataobjects.api.engine.DOEngine;
import dataobjects.api.migration.generic.ExportColumn;
import dataobjects.api.migration.generic.*;
import dataobjects.api.models.schema.DOSchema;
import dataobjects.api.models.schema.DOSchemaModule;
import dataobjects.api.models.schema.DOSchemaClass;
import dataobjects.api.models.database.DODatabaseObject;
import dataobjects.api.models.database.DODatabaseClass;

import java.io.IOException;
import java.text.Normalizer;
import java.util.*;

/**
 * Orchestrates the entire export process by coordinating between specialized
 * components.
 * This class is clean and focused - it delegates all complex work to
 * specialized components.
 */
public class ExportOrchestrator {

    private final DOEngine engine;
    private final DataExtractor dataExtractor;
    private final ColumnBuilder columnBuilder;
    private final ValueFormatter valueFormatter;

    public ExportOrchestrator(DOEngine engine) {
        this.engine = engine;
        this.dataExtractor = new DataExtractor(engine);
        this.columnBuilder = new ColumnBuilder(engine);
        this.valueFormatter = new ValueFormatter();
    }

    /**
     * Export using the handler's default output directory.
     */
    public void export(ExportFormatHandler handler) throws IOException {
        export(handler, handler.getDefaultOutputDirectory());
    }

    /**
     * Export to a specific output directory.
     */
    public void export(ExportFormatHandler handler, String outputDirectory) throws IOException {
        validateSchema();

        // Initialize the format handler
        handler.initialize(outputDirectory);

        // Process each module
        for (DOSchemaModule module : engine.getSchema().getModules()) {
            exportModule(module, handler, outputDirectory);
        }

        // Finalize the export
        handler.cleanup();
    }

    /**
     * Export a single module.
     */
    private void exportModule(DOSchemaModule module, ExportFormatHandler handler, String outputDirectory)
            throws IOException {
        System.out.println("Exporting module: " + module.getName());

        // Create module context
        String sanitizedName = sanitizeModuleName(module.getName());
        ModuleExportContext moduleContext = new ModuleExportContext(outputDirectory, module, sanitizedName);

        // Begin module processing
        Object moduleHandle = handler.beginModule(moduleContext);

        try {
            // Export all classes in the module
            exportModuleClasses(module, moduleHandle, moduleContext, handler);
        } finally {
            // Always end module, even if there's an error
            handler.endModule(moduleHandle, moduleContext);
            System.out.println("Module exported successfully: " + module.getName());
        }
    }

    /**
     * Export all classes within a module.
     */
    private void exportModuleClasses(DOSchemaModule module, Object moduleHandle,
            ModuleExportContext moduleContext, ExportFormatHandler handler)
            throws IOException {

        Set<DODatabaseClass> exportedDbClasses = new HashSet<>();

        if (module.getClasses() != null) {
            for (DOSchemaClass schemaClass : module.getClasses()) {
                DODatabaseClass dbClass = schemaClass.getDatabaseClass();

                if (shouldExportClass(dbClass, exportedDbClasses)) {
                    exportedDbClasses.add(dbClass);
                    exportClass(moduleHandle, moduleContext, schemaClass, handler);
                } else if (dbClass != null) {
                    System.out.println("Skipping duplicate database class: " + schemaClass.getShortName());
                }
            }
        }
    }

    /**
     * Export a single class.
     */
    private void exportClass(Object moduleHandle, ModuleExportContext moduleContext,
            DOSchemaClass schemaClass, ExportFormatHandler handler) throws IOException {

        DODatabaseClass dbClass = schemaClass.getDatabaseClass();
        if (dbClass == null) {
            System.out.println("Skipping class with no database class: " + schemaClass.getShortName());
            return;
        }

        String exportName = getClassExportName(schemaClass);
        System.out.println("  Exporting class: " + exportName);

        // Build export columns and get objects
        List<ExportColumn> columns = columnBuilder.buildColumns(dbClass);
        DODatabaseObject[] objectArray = dbClass.getResolvedObjects();
        List<DODatabaseObject> objects = objectArray != null ? Arrays.asList(objectArray) : new ArrayList<>();

        // Create class context
        ClassExportContext classContext = new ClassExportContext(
                moduleContext, schemaClass, dbClass, columns, exportName, objects.size());

        // Begin class processing
        Object classHandle = handler.beginClass(moduleHandle, classContext);
        int exportedCount = 0;

        try {
            // Export all objects
            exportedCount = exportClassObjects(classHandle, classContext, objects, handler);
        } finally {
            // Always end class
            handler.endClass(classHandle, classContext, exportedCount);
            System.out.println("    Exported " + exportedCount + " objects for " + exportName);
        }
    }

    /**
     * Export all objects for a class.
     */
    private int exportClassObjects(Object classHandle, ClassExportContext classContext,
            List<DODatabaseObject> objects, ExportFormatHandler handler)
            throws IOException {
        int exportedCount = 0;

        for (DODatabaseObject obj : objects) {
            try {
                exportObject(classHandle, classContext, obj, exportedCount, handler);
                exportedCount++;
            } catch (Exception e) {
                System.err.println("Error exporting object " + obj.getObjectId() +
                        " of class " + classContext.getExportName() + ": " + e.getMessage());
                e.printStackTrace();
                // Continue with next object
            }
        }

        return exportedCount;
    }

    /**
     * Export a single object.
     */
    private void exportObject(Object classHandle, ClassExportContext classContext,
            DODatabaseObject obj, int rowIndex, ExportFormatHandler handler)
            throws IOException {

        // Create object context
        ObjectExportContext objectContext = new ObjectExportContext(classContext, obj, rowIndex);

        // Extract raw values
        List<Object> rawValues = dataExtractor.extractValues(obj, classContext.getColumns());

        // Format values
        List<FormattedValue> formattedValues = valueFormatter.formatValues(rawValues, classContext.getColumns());

        // Export the object
        handler.exportObject(classHandle, objectContext, formattedValues);
    }

    /**
     * Check if a class should be exported.
     */
    private boolean shouldExportClass(DODatabaseClass dbClass, Set<DODatabaseClass> exportedClasses) {
        return dbClass != null && !exportedClasses.contains(dbClass);
    }

    /**
     * Get the export name for a schema class.
     */
    private String getClassExportName(DOSchemaClass schemaClass) {
        String exportName = schemaClass.getExportName();
        if (exportName == null || exportName.isEmpty()) {
            exportName = schemaClass.getShortName();
        }
        return exportName;
    }

    /**
     * Validate that the schema is available and valid.
     */
    private void validateSchema() throws IOException {
        DOSchema schema = engine.getSchema();
        if (schema == null || schema.getModules() == null) {
            throw new IOException("Schema or modules not available");
        }
    }

    /**
     * Sanitize module names for file system use.
     */
    public static String sanitizeModuleName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Unnamed_Module";
        }

        String sanitized = removeAccents(name.trim())
                .replaceAll("[^a-zA-Z0-9\\s\\-_]", "")
                .replaceAll("\\s+", "_")
                .replaceAll("[-_]+", "_");

        if (sanitized.isEmpty()) {
            return "Unnamed_Module";
        }

        return sanitized;
    }

    /**
     * Remove accents from text.
     */
    public static String removeAccents(String text) {
        if (text == null) {
            return null;
        }

        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "");
    }
}