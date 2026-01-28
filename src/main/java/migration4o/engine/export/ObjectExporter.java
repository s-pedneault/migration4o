package migration4o.engine.export;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.reflect.generic.GenericObject;

import migration4o.engine.export.monitoring.ExportStatistics;
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
    }

    /**
     * Recursively exports an object and all its referenced objects.
     */
    public void exportObjectRecursively(ExtObjectContainer container, long objectId, int indentLevel)
            throws IOException {
        // Avoid exporting the same object twice
        if (!exportedObjectIds.add(objectId)) {
            return;
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
                            (objId, indent) -> exportObjectRecursively(container, objId, indent));
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
