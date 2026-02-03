package migration4o.engine.export;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.reflect.generic.GenericObject;

import migration4o.engine.export.monitoring.ExportResult;
import migration4o.engine.export.monitoring.ExportStatistics;
import migration4o.models.ui.ClassExportConfig;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.util.ClassUtil;
import migration4o.util.ObjectResolverUtil;
import migration4o.util.SchemaUtil;

/**
 * Orchestrates recursive object traversal and export to XML.
 * Now delegates to specialized components for schema lookups, field exports,
 * and reference resolution.
 */
public class ObjectExporter {
    private final DOSchema schema;
    private final DOSchema databaseSchema;
    private final FieldExporter fieldExporter;
    private final XMLWriter xmlWriter;
    private final XSDBuilder xsdBuilder;
    private final ExportStatistics statistics;
    private final Set<Long> exportedObjectIds = new HashSet<>();
    private final Map<Long, EmbeddedObjectInfo> embeddedObjectRefs = new HashMap<>();
    private final ReferencedClassTracker referencedClassTracker;
    private boolean trackReferences = false;
    private ClassExportConfig exportConfig; // Optional export configuration with criteria

    /**
     * Tracks information about embedded objects for detecting duplicates.
     */
    private static class EmbeddedObjectInfo {
        String className;
        String firstFieldName;
        int referenceCount;

        EmbeddedObjectInfo(String className, String fieldName) {
            this.className = className;
            this.firstFieldName = fieldName;
            this.referenceCount = 1;
        }
    }

    public ObjectExporter(DOSchema schema, DOSchema databaseSchema, XMLWriter xmlWriter,
            XSDBuilder xsdBuilder, ExportStatistics statistics) {
        this(schema, databaseSchema, xmlWriter, xsdBuilder, statistics, null);
    }

    public ObjectExporter(DOSchema schema, DOSchema databaseSchema, XMLWriter xmlWriter,
            XSDBuilder xsdBuilder, ExportStatistics statistics, ClassExportConfig exportConfig) {
        this.schema = schema;
        this.databaseSchema = databaseSchema;
        this.exportConfig = exportConfig;
        ReferenceObjectExporter idEntiteResolver = new ReferenceObjectExporter(databaseSchema);
        this.fieldExporter = new FieldExporter(schema, databaseSchema, xmlWriter, xsdBuilder, idEntiteResolver);
        this.xmlWriter = xmlWriter;
        this.xsdBuilder = xsdBuilder;
        this.statistics = statistics;
        this.referencedClassTracker = new ReferencedClassTracker();
    }

    /**
     * Enables automatic tracking of referenced classes during export.
     * When enabled, any class referenced during export that is not in the export
     * request
     * will be automatically registered for export.
     * 
     * @param enabled true to enable tracking
     */
    public void setReferenceTracking(boolean enabled) {
        this.trackReferences = enabled;
        if (enabled) {
            fieldExporter.setReferencedClassTracker(referencedClassTracker);
        } else {
            fieldExporter.setReferencedClassTracker(null);
        }
    }

    /**
     * Gets the reference tracker to query discovered classes.
     * 
     * @return the reference tracker
     */
    public ReferencedClassTracker getReferencedClassTracker() {
        return referencedClassTracker;
    }

    /**
     * Resets the state for a new export operation.
     */
    public void reset() {
        exportedObjectIds.clear();
        embeddedObjectRefs.clear();
        referencedClassTracker.reset();
    }

    /**
     * Recursively exports an object and all its referenced objects.
     * This is the main entry point - assumes objects are NOT embedded by default.
     */
    public void exportObjectRecursively(ExtObjectContainer container, long objectId, int indentLevel)
            throws IOException {
        exportObjectRecursively(container, objectId, indentLevel, false, null, null, null, null);
    }

    /**
     * Recursively exports an object and all its referenced objects.
     * 
     * @param isEmbedded                true if this object is embedded in a parent
     *                                  field
     *                                  (not a
     *                                  top-level export)
     * @param fieldName                 the name of the field this object is
     *                                  embedded in
     *                                  (for
     *                                  warning messages)
     * @param containingClassName       the name of the class that contains the
     *                                  field (for
     *                                  warning messages)
     * @param sourceFieldName           the source field name from schema (e.g.,
     *                                  mVectCompartiment)
     * @param sourceContainingClassName the source class name from schema (e.g.,
     *                                  gest.vehicule.Vehicule)
     */
    private void exportObjectRecursively(ExtObjectContainer container, long objectId, int indentLevel,
            boolean isEmbedded, String fieldName, String containingClassName,
            String sourceFieldName, String sourceContainingClassName) throws IOException {
        // Check if object was already exported
        if (!exportedObjectIds.add(objectId)) {
            // Object already exported

            // If embedContents=false, this should have been handled by ID reference logic
            // in FieldExporter
            // If we reach here with isEmbedded=false, just skip - the object is already
            // exported
            if (!isEmbedded) {
                return;
            }

            // If embedContents=true (embedded), this is a duplicate - warn user
            // Get object info for warning message
            String className = "Unknown";
            String firstFieldName = "unknown";
            int referenceCount = 2; // At least 2 (first export + this attempt)

            if (embeddedObjectRefs.containsKey(objectId)) {
                // Object was tracked, update reference count
                EmbeddedObjectInfo info = embeddedObjectRefs.get(objectId);
                info.referenceCount++;
                className = info.className;
                firstFieldName = info.firstFieldName;
                referenceCount = info.referenceCount;
            } else {
                // Object wasn't tracked but was exported - get class name now
                try {
                    Object obj = container.ext().getByID(objectId);
                    if (obj != null) {
                        className = ClassUtil.getClassName(obj);
                    }
                } catch (Exception e) {
                    // Ignore, use "Unknown"
                }
                firstFieldName = "first export location";
            }

            // Report duplicate reference warning for embedded objects only
            String message = String.format(
                    "Object (ID %d, class %s) already exported, reference from field '%s' will create empty element. " +
                            "First reference: '%s'. Reference count: %d. " +
                            "Consider using embedContents=\"false\" to export as separate object instead.",
                    objectId, className, fieldName != null ? fieldName : "unknown", firstFieldName, referenceCount);
            statistics.addSchemaWarning(
                    ExportResult.SchemaWarning.WarningType.DUPLICATE_EMBEDDED_REFERENCE,
                    objectId,
                    className,
                    fieldName,
                    containingClassName,
                    sourceContainingClassName,
                    sourceFieldName,
                    message,
                    referenceCount);
            return;
        }

        // Track embedded objects for duplicate detection
        if (isEmbedded) {
            String className = null;
            try {
                Object obj = container.ext().getByID(objectId);
                if (obj != null) {
                    className = ClassUtil.getClassName(obj);
                    embeddedObjectRefs.put(objectId, new EmbeddedObjectInfo(className, fieldName));
                }
            } catch (Exception e) {
                // Ignore errors in tracking - we'll still export
            }
        }

        statistics.incrementAttempted();
        String className = null;
        try {
            // Get and activate the object
            Object obj = container.ext().getByID(objectId);
            if (obj == null) {
                return;
            }

            className = ClassUtil.getClassName(obj);
            ObjectResolverUtil.activateObject(container, obj, objectId);

            // Apply export criteria filtering if configured (only for top-level objects)
            if (!isEmbedded && exportConfig != null && !exportConfig.getCriteria().isEmpty()) {
                if (!exportConfig.matchesAllCriteria(obj)) {
                    // Object doesn't match criteria, skip export
                    return;
                }
            }

            // Write object opening tag using destination class name as element name
            DOSchemaClass schemaClass = SchemaUtil.findClassByName(className, schema);
            String elementName = schemaClass != null ? schemaClass.destinationName
                    : ClassUtil.getSimpleName(className);
            xmlWriter.writeStartElement(elementName, indentLevel);

            // XSD: record this class structure
            if (schemaClass != null) {
                xsdBuilder.addClass(schemaClass);
            }

            // If it's a GenericObject, export all its fields
            if (obj instanceof GenericObject) {
                GenericObject genericObj = (GenericObject) obj;
                StoredClass storedClass = container.ext().storedClass(genericObj);
                if (storedClass != null) {
                    final String currentClassName = schemaClass.destinationName;
                    final String currentSourceClassName = schemaClass.source; // Full source class name
                    fieldExporter.exportAllFields(container, genericObj, schemaClass, indentLevel + 1,
                            (objId, indent, embedded, fldName, sourceFldName) -> exportObjectRecursively(container,
                                    objId, indent, embedded, fldName, currentClassName,
                                    sourceFldName, currentSourceClassName));
                }
            }

            // Write object closing tag
            xmlWriter.writeEndElement(elementName, indentLevel);
            statistics.incrementSucceeded();
            statistics.recordClassExport(schemaClass);
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            statistics.addError(objectId, className, errorMsg, e);
            // Still write error marker in XML for debugging
            xmlWriter.writeIndent(indentLevel);
            xmlWriter.write("<!-- ERROR exporting object " + objectId + ": "
                    + XMLWriter.xmlEscape(errorMsg) + " -->\n");
        }
    }
}
