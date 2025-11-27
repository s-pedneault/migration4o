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

        // Create reference tracker for cross-module reference detection
        ReferenceTracker tracker = new ReferenceTracker();

        // Initialize the format handler with the tracker
        handler.initialize(outputDirectory, tracker);

        // Process each module
        for (DOSchemaModule module : engine.getSchema().getModules()) {
            exportModule(module, handler, outputDirectory);
        }

        // Export General.xml with multi-module referenced objects
        exportGeneralModule(handler, tracker, outputDirectory);

        // Finalize the export
        handler.cleanup();
    }

    /**
     * Export the General module containing objects referenced by multiple modules.
     */
    private void exportGeneralModule(ExportFormatHandler handler, ReferenceTracker tracker, String outputDirectory)
            throws IOException {

        // Get objects that were referenced but never exported
        Set<Long> unexportedRefs = tracker.getUnexportedReferences();

        if (unexportedRefs.isEmpty()) {
            System.out.println("No unexported referenced objects found - skipping General.xml");
            return;
        }

        // Group unexported references by object type
        Map<String, Set<Long>> objectsByType = new HashMap<>();
        for (Long objectId : unexportedRefs) {
            String objectType = tracker.getObjectType(objectId);
            if (objectType != null) {
                objectsByType.computeIfAbsent(objectType, k -> new HashSet<>()).add(objectId);
            } else {
                System.err.println("Warning: Referenced object " + objectId + " has no type information");
            }
        }

        System.out.println("Exporting General module with unexported referenced objects:");
        for (Map.Entry<String, Set<Long>> entry : objectsByType.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue().size() + " objects");
        }

        // Create a synthetic module context for General
        ModuleExportContext generalContext = new ModuleExportContext(
                outputDirectory, engine, null, "General");

        // Begin General module
        Object moduleHandle = handler.beginModule(generalContext);

        try {
            // Export each object type
            for (Map.Entry<String, Set<Long>> entry : objectsByType.entrySet()) {
                String objectType = entry.getKey();
                Set<Long> objectIds = entry.getValue();

                exportGeneralClass(moduleHandle, generalContext, objectType, objectIds, handler);
            }
        } finally {
            handler.endModule(moduleHandle, generalContext);
            System.out.println("General module exported successfully");
        }
    }

    /**
     * Export a single class in the General module.
     */
    private void exportGeneralClass(Object moduleHandle, ModuleExportContext moduleContext,
            String objectType, Set<Long> objectIds, ExportFormatHandler handler) throws IOException {

        System.out.println("  Exporting General class: " + objectType + " (" + objectIds.size() + " objects)");

        // Find the database class for this object type
        DODatabaseClass dbClass = findDatabaseClass(objectType);
        if (dbClass == null) {
            System.err.println("    Could not find database class for type: " + objectType);
            return;
        }

        // Build columns
        List<ExportColumn> columns = columnBuilder.buildColumns(dbClass);

        // Create class context
        ClassExportContext classContext = new ClassExportContext(
                moduleContext, null, dbClass, columns, objectType, objectIds.size());

        // Begin class
        Object classHandle = handler.beginClass(moduleHandle, classContext);
        int exportedCount = 0;

        try {
            // Get all objects from this database class and filter by ID
            DODatabaseObject[] allObjects = dbClass.getResolvedObjects();
            if (allObjects != null) {
                for (DODatabaseObject obj : allObjects) {
                    if (objectIds.contains(obj.getObjectId())) {
                        try {
                            exportObject(classHandle, classContext, obj, exportedCount, handler);
                            exportedCount++;
                        } catch (Exception e) {
                            System.err
                                    .println("    Error exporting object " + obj.getObjectId() + ": " + e.getMessage());
                        }
                    }
                }
            }
        } finally {
            handler.endClass(classHandle, classContext, exportedCount);
            System.out.println("    Exported " + exportedCount + " objects for " + objectType);
        }
    }

    /**
     * Find the database class for a given object type name.
     */
    private DODatabaseClass findDatabaseClass(String objectType) {
        for (DOSchemaModule module : engine.getSchema().getModules()) {
            if (module.getClasses() != null) {
                for (DOSchemaClass schemaClass : module.getClasses()) {
                    String exportName = getClassExportName(schemaClass);
                    if (exportName.equals(objectType)) {
                        return schemaClass.getDatabaseClass();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Export a single module.
     */
    private void exportModule(DOSchemaModule module, ExportFormatHandler handler, String outputDirectory)
            throws IOException {
        System.out.println("Exporting module: " + module.getName());

        // Create module context with engine reference
        String sanitizedName = sanitizeModuleName(module.getName());
        ModuleExportContext moduleContext = new ModuleExportContext(outputDirectory, engine, module, sanitizedName);

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