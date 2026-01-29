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
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
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
        this.schema = schema;
        this.databaseSchema = databaseSchema;
        ReferenceObjectExporter idEntiteResolver = new ReferenceObjectExporter(databaseSchema);
        this.fieldExporter = new FieldExporter(schema, databaseSchema, xmlWriter, xsdBuilder, idEntiteResolver);
        this.xmlWriter = xmlWriter;
        this.xsdBuilder = xsdBuilder;
        this.statistics = statistics;
    }

    /**
     * Resets the state for a new export operation.
     */
    public void reset() {
        exportedObjectIds.clear();
        embeddedObjectRefs.clear();
    }

    /**
     * Recursively exports an object and all its referenced objects.
     * This is the main entry point - assumes objects are NOT embedded by default.
     */
    public void exportObjectRecursively(ExtObjectContainer container, long objectId, int indentLevel)
            throws IOException {
        exportObjectRecursively(container, objectId, indentLevel, false, null);
    }

    /**
     * Recursively exports an object and all its referenced objects.
     * 
     * @param isEmbedded true if this object is embedded in a parent field (not a
     *                   top-level export)
     * @param fieldName  the name of the field this object is embedded in (for
     *                   warning messages)
     */
    private void exportObjectRecursively(ExtObjectContainer container, long objectId, int indentLevel,
            boolean isEmbedded, String fieldName) throws IOException {
        // Check if object was already exported
        if (!exportedObjectIds.add(objectId)) {
            // Object already exported - this causes empty collection elements
            System.err.println("DEBUG: Object " + objectId + " already exported. isEmbedded=" + isEmbedded
                    + ", fieldName=" + fieldName + ", inMap=" + embeddedObjectRefs.containsKey(objectId));

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

            // Always report duplicate reference warning
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
                    message,
                    referenceCount);
            System.err.println("WARNING: " + message);
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
                    System.err.println("DEBUG: Tracking embedded object " + objectId + " (class=" + className
                            + ", field=" + fieldName + ")");
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

            // Write object opening tag using destination class name as element name
            DOSchemaClass schemaClass = SchemaUtil.findClassByName(className, schema);
            String elementName = schemaClass != null ? schemaClass.destinationName
                    : XMLWriter.getSimpleClassName(className);
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
                    fieldExporter.exportAllFields(container, genericObj, schemaClass, indentLevel + 1,
                            (objId, indent, embedded, fldName) -> exportObjectRecursively(container, objId, indent,
                                    embedded, fldName));
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
