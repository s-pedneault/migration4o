package migration4o.engine.migration;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import migration4o.engine.DOEngine;
import migration4o.models.database.DODatabaseField;
import migration4o.models.database.DOCollectionReference;
import migration4o.models.database.DODatabaseClass;
import migration4o.models.database.DODatabaseObject;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.schema.DOSchemaModule;
import migration4o.util.ObjectResolverUtil;

/**
 * Exports database objects to schema-compliant XML files using our new format.
 * Produces XML with type definitions and clean data structure.
 */
public class XMLDataExporter {

    private final DOEngine engine;
    private final String namespace;
    private final String targetNamespace = "http://migration4o/schema";
    private final Set<Long> exportedObjectIds = new HashSet<>();

    public XMLDataExporter(DOEngine engine, String namespace) {
        this.engine = engine;
        this.namespace = namespace;
    }

    /**
     * Export a single module to an XML file using schema-compliant format.
     */
    public void exportModule(DOSchemaModule module, String outputPath) throws IOException {
        exportedObjectIds.clear(); // Reset for each module

        XMLOutputFactory factory = XMLOutputFactory.newInstance();

        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            XMLStreamWriter writer = factory.createXMLStreamWriter(fos, "UTF-8");

            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeCharacters("\n");

            // Root migration element with namespace
            writer.writeStartElement("migration");
            writer.writeDefaultNamespace(targetNamespace);
            writer.writeAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
            writer.writeAttribute("xsi:schemaLocation", targetNamespace + " migration-schema.xsd");
            writer.writeCharacters("\n\n");

            // Types section (simplified for single module)
            writer.writeCharacters("  ");
            writer.writeStartElement("types");
            writer.writeCharacters("\n");

            if (module.getClasses() != null) {
                for (DOSchemaClass schemaClass : module.getClasses()) {
                    // Only export classes that are marked for migration
                    if (!schemaClass.isMigrate()) {
                        continue;
                    }
                    writer.writeCharacters("    ");
                    writer.writeStartElement("type");
                    writer.writeAttribute("name", schemaClass.getShortName());
                    writer.writeAttribute("class",
                            schemaClass.getDatabaseClass() != null ? schemaClass.getDatabaseClass().getAbsoluteName()
                                    : schemaClass.getShortName());
                    writer.writeAttribute("module", XMLExportUtils.sanitizeName(module.getName()));
                    writer.writeEndElement();
                    writer.writeCharacters("\n");
                }
            }

            writer.writeCharacters("  ");
            writer.writeEndElement(); // types
            writer.writeCharacters("\n\n");

            // Modules section
            writer.writeCharacters("  ");
            writer.writeStartElement("modules");
            writer.writeCharacters("\n");

            writer.writeCharacters("    ");
            writer.writeStartElement("module");
            writer.writeAttribute("name", XMLExportUtils.sanitizeName(module.getName()));
            writer.writeCharacters("\n");

            writer.writeCharacters("      ");
            writer.writeStartElement("objects");
            writer.writeCharacters("\n");

            // Export objects for each class in the module
            if (module.getClasses() != null) {
                for (DOSchemaClass schemaClass : module.getClasses()) {
                    // Only export classes that are marked for migration
                    if (schemaClass.isMigrate()) {
                        exportClassObjects(writer, schemaClass);
                    }
                }
            }

            writer.writeCharacters("      ");
            writer.writeEndElement(); // objects
            writer.writeCharacters("\n");

            writer.writeCharacters("    ");
            writer.writeEndElement(); // module
            writer.writeCharacters("\n");

            writer.writeCharacters("  ");
            writer.writeEndElement(); // modules
            writer.writeCharacters("\n\n");

            writer.writeEndElement(); // migration root
            writer.writeCharacters("\n");
            writer.writeEndDocument();
            writer.flush();
            writer.close();

        } catch (XMLStreamException e) {
            throw new IOException("Error exporting module: " + module.getName(), e);
        }
    }

    /**
     * Export unreached objects to a separate XML file.
     */
    public void exportUnreachedObjects(String outputPath) throws IOException {
        // Get unreached objects - we need to avoid duplicates from inheritance
        // Collect all unreached object IDs (unique set)
        Set<Long> unreachedObjectIds = new HashSet<>();
        Map<Long, DODatabaseClass> objectIdToMostSpecificClass = new HashMap<>();

        // Iterate through all database classes to find unreached objects
        for (DODatabaseClass dbClass : engine.getDatabase().getClasses()) {
            DODatabaseObject[] objects = dbClass.getResolvedObjects();
            if (objects != null) {
                for (DODatabaseObject obj : objects) {
                    if (!obj.isReachable()) {
                        Long objectId = obj.getObjectId();
                        // Only add if not already seen (use most specific class)
                        if (!objectIdToMostSpecificClass.containsKey(objectId)) {
                            objectIdToMostSpecificClass.put(objectId, dbClass);
                            unreachedObjectIds.add(objectId);
                        }
                    }
                }
            }
        }

        if (unreachedObjectIds.isEmpty()) {
            System.out.println("No unreached objects found - all objects are reachable!");
            // Still create an empty file
            createEmptyUnreachedFile(outputPath);
            return;
        }

        System.out.println("Found " + unreachedObjectIds.size() + " unique unreached objects");

        XMLOutputFactory factory = XMLOutputFactory.newInstance();

        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            XMLStreamWriter writer = factory.createXMLStreamWriter(fos, "UTF-8");

            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeCharacters("\n");

            writer.writeStartElement("unreached");
            writer.writeDefaultNamespace(namespace);
            writer.writeCharacters("\n");

            // Export each unreached object using its most specific class
            for (Long objectId : unreachedObjectIds) {
                DODatabaseClass mostSpecificClass = objectIdToMostSpecificClass.get(objectId);
                if (mostSpecificClass != null) {
                    exportUnreachedObject(writer, mostSpecificClass, objectId);
                }
            }

            writer.writeEndElement(); // unreached
            writer.writeCharacters("\n");
            writer.writeEndDocument();
            writer.flush();
            writer.close();

        } catch (XMLStreamException e) {
            throw new IOException("Error exporting unreached objects", e);
        }
    }

    /**
     * Create an empty unreached.xml file.
     */
    private void createEmptyUnreachedFile(String outputPath) throws IOException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();

        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            XMLStreamWriter writer = factory.createXMLStreamWriter(fos, "UTF-8");

            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeCharacters("\n");
            writer.writeStartElement("unreached");
            writer.writeDefaultNamespace(namespace);
            writer.writeComment(" All objects are reachable - no unreached objects found ");
            writer.writeEndElement();
            writer.writeCharacters("\n");
            writer.writeEndDocument();
            writer.flush();
            writer.close();

        } catch (XMLStreamException e) {
            throw new IOException("Error creating empty unreached file", e);
        }
    }

    /**
     * Export all objects for a class using schema-compliant format.
     */
    private void exportClassObjects(XMLStreamWriter writer, DOSchemaClass schemaClass) throws XMLStreamException {
        DODatabaseClass dbClass = schemaClass.getDatabaseClass();
        if (dbClass == null) {
            return;
        }

        DODatabaseObject[] objects = dbClass.getResolvedObjects();
        if (objects == null || objects.length == 0) {
            return;
        }

        String typeName = schemaClass.getShortName();

        // Export ALL objects from this class using new format
        for (DODatabaseObject obj : objects) {
            if (obj.isReachable()) { // Only export reachable objects in module sections
                exportObject(writer, obj, typeName, schemaClass, true);
            }
        }
    }

    /**
     * Export a single object using schema-compliant format.
     * 
     * @param writer        The XML writer
     * @param obj           The object to export
     * @param typeName      The type name to use
     * @param schemaClass   The schema class (for field filtering)
     * @param trackExported Whether to track this as exported (to avoid duplicates)
     */
    private void exportObject(XMLStreamWriter writer, DODatabaseObject obj, String typeName, DOSchemaClass schemaClass,
            boolean trackExported)
            throws XMLStreamException {

        Long objectId = obj.getObjectId();

        // Check if already exported (avoid duplicates)
        if (trackExported && exportedObjectIds.contains(objectId)) {
            return; // Skip duplicate
        }

        if (trackExported) {
            exportedObjectIds.add(objectId);
        }

        writer.writeCharacters("        ");
        writer.writeStartElement("object");
        writer.writeAttribute("type", typeName);
        writer.writeAttribute("id", String.valueOf(objectId));
        writer.writeCharacters("\n");

        // Get the most specific class
        DODatabaseClass mostSpecificClass = obj.getMostSpecificClass();

        // Export all fields from the inheritance chain
        exportObjectFields(writer, obj, mostSpecificClass, schemaClass);

        writer.writeCharacters("        ");
        writer.writeEndElement(); // object
        writer.writeCharacters("\n");
    }

    /**
     * Export an unreached object with type information.
     */
    private void exportUnreachedObject(XMLStreamWriter writer, DODatabaseClass dbClass, Long objectId)
            throws XMLStreamException {

        writer.writeCharacters("  ");
        writer.writeStartElement("object");
        writer.writeAttribute("type", dbClass.getAbsoluteName());
        writer.writeAttribute("id", String.valueOf(objectId));

        // Try to get the actual object to export its fields
        com.db4o.ext.ExtObjectContainer container = engine.getDatabase().getContainer();
        Object actualObj = container.getByID(objectId);

        if (actualObj != null) {
            ObjectResolverUtil.activateObject(container, actualObj, objectId);

            // Create a temporary DODatabaseObject representation
            // Export fields based on the database class
            writer.writeCharacters("\n");
            exportFieldsFromActualObject(writer, actualObj, dbClass, objectId);
            writer.writeCharacters("  ");
        }

        writer.writeEndElement(); // object
        writer.writeCharacters("\n");
    }

    /**
     * Export fields from a resolved DODatabaseObject.
     */
    private void exportObjectFields(XMLStreamWriter writer, DODatabaseObject obj, DODatabaseClass clazz,
            DOSchemaClass schemaClass)
            throws XMLStreamException {

        if (clazz == null) {
            return;
        }

        DODatabaseField[] fields = clazz.getFields();
        if (fields == null) {
            return;
        }

        com.db4o.ext.ExtObjectContainer container = engine.getDatabase().getContainer();
        Object actualObj = container.getByID(obj.getObjectId());
        if (actualObj != null) {
            ObjectResolverUtil.activateObject(container, actualObj, obj.getObjectId());
        }

        // Get primitive field values
        Map<String, ObjectResolverUtil.PrimitiveFieldValue> primitiveValues = ObjectResolverUtil
                .extractPrimitiveFieldValues(container, obj.getObjectId(), obj.getAllClasses());

        for (DODatabaseField field : fields) {
            // Check if this field should be exported according to schema
            if (schemaClass != null && !shouldExportField(field, schemaClass)) {
                continue;
            }
            exportField(writer, field, obj, actualObj, primitiveValues);
        }

        // Handle parent class fields (inheritance)
        String superClassName = clazz.getSuperClassAbsoluteName();
        if (superClassName != null && !superClassName.equals("java.lang.Object")) {
            // Find parent class and export its fields
            DODatabaseClass parentClass = findClassByName(superClassName);
            if (parentClass != null) {
                // Note: We pass null for schemaClass in parent to avoid double-filtering
                // The schema should define fields at the appropriate level
                exportObjectFields(writer, obj, parentClass, null);
            }
        }
    }

    /**
     * Export fields from an actual object (for unreached objects).
     */
    private void exportFieldsFromActualObject(XMLStreamWriter writer, Object actualObj,
            DODatabaseClass dbClass, Long objectId) throws XMLStreamException {

        DODatabaseField[] fields = dbClass.getFields();
        if (fields == null) {
            return;
        }

        com.db4o.ext.ExtObjectContainer container = engine.getDatabase().getContainer();

        for (DODatabaseField field : fields) {
            Object fieldValue = ObjectResolverUtil.getFieldValue(container, actualObj, field);
            exportFieldValue(writer, field, fieldValue);
        }
    }

    /**
     * Export a single field using schema-compliant format with field name cleaning.
     */
    private void exportField(XMLStreamWriter writer, DODatabaseField field, DODatabaseObject obj, Object actualObj,
            Map<String, ObjectResolverUtil.PrimitiveFieldValue> primitiveValues) throws XMLStreamException {

        String cleanedFieldName = XMLExportUtils.cleanFieldName(field.getName());

        // Check if it's in primitive values
        ObjectResolverUtil.PrimitiveFieldValue primitiveValue = primitiveValues.get(field.getName());

        if (primitiveValue != null && primitiveValue.value != null
                && !XMLExportUtils.isEmptyValue(primitiveValue.value, field)) {
            // Export primitive value with schema-compliant format
            writer.writeCharacters("          ");
            writer.writeStartElement("field");
            writer.writeAttribute("name", cleanedFieldName);
            writer.writeAttribute("type", XMLExportUtils.getFieldType(field));
            writer.writeCharacters(XMLExportUtils.formatFieldValue(primitiveValue.value, field));
            writer.writeEndElement();
            writer.writeCharacters("\n");
            return;
        }

        // Handle collections
        if (field.isArray()) {
            exportCollection(writer, field, obj, actualObj, cleanedFieldName);
            return;
        }

        // Handle object references
        if (!field.isPrimitive() && actualObj != null) {
            com.db4o.ext.ExtObjectContainer container = engine.getDatabase().getContainer();
            Object fieldValue = ObjectResolverUtil.getFieldValue(container, actualObj, field);

            if (fieldValue != null) {
                Long refObjectId = ObjectResolverUtil.getObjectId(container, fieldValue);
                if (refObjectId != null) {
                    // For now, always export as reference (embedding can be added later)
                    writer.writeCharacters("          ");
                    writer.writeStartElement("field");
                    writer.writeAttribute("name", cleanedFieldName);
                    writer.writeAttribute("type", "reference");
                    writer.writeAttribute("targetType",
                            XMLExportUtils.getSimpleTypeName(fieldValue.getClass().getName()));
                    writer.writeCharacters(String.valueOf(refObjectId));
                    writer.writeEndElement();
                    writer.writeCharacters("\n");
                }
            }
        }
    }

    /**
     * Export a field value (for unreached objects).
     */
    private void exportFieldValue(XMLStreamWriter writer, DODatabaseField field, Object value)
            throws XMLStreamException {
        if (value == null || XMLExportUtils.isEmptyValue(value, field)) {
            return;
        }

        writer.writeCharacters("    ");
        writer.writeStartElement(XMLExportUtils.sanitizeName(field.getName()));

        if (field.isPrimitive() || value instanceof String || value instanceof Number || value instanceof Boolean) {
            writer.writeCharacters(String.valueOf(value));
        } else {
            // Object reference - just write the ID
            com.db4o.ext.ExtObjectContainer container = engine.getDatabase().getContainer();
            Long refId = ObjectResolverUtil.getObjectId(container, value);
            if (refId != null) {
                writer.writeAttribute("ref", String.valueOf(refId));
            }
        }

        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    /**
     * Export a collection field using schema-compliant format.
     */
    private void exportCollection(XMLStreamWriter writer, DODatabaseField field, DODatabaseObject obj, Object actualObj,
            String cleanedFieldName)
            throws XMLStreamException {

        if (actualObj == null) {
            return;
        }

        com.db4o.ext.ExtObjectContainer container = engine.getDatabase().getContainer();
        Object collectionObj = ObjectResolverUtil.getFieldValue(container, actualObj, field);

        if (collectionObj == null || !ObjectResolverUtil.isAnyCollectionType(collectionObj)) {
            return;
        }

        // Find the collection reference for this field
        DOCollectionReference collectionRef = findCollectionReference(obj, field);
        if (collectionRef == null || collectionRef.getSize() == 0) {
            return;
        }

        Long[] containedIds = collectionRef.getContainedObjectIds();
        if (containedIds == null || containedIds.length == 0) {
            return;
        }

        // Start collection field with schema-compliant format
        writer.writeCharacters("          ");
        writer.writeStartElement("field");
        writer.writeAttribute("name", cleanedFieldName);
        writer.writeAttribute("type", "collection");
        writer.writeAttribute("elementType", "reference");
        // Could add elementClass here if we can determine it
        writer.writeCharacters("\n");

        // Export each item in the collection as a value
        for (Long itemId : containedIds) {
            writer.writeCharacters("            ");
            writer.writeStartElement("value");
            writer.writeCharacters(String.valueOf(itemId));
            writer.writeEndElement();
            writer.writeCharacters("\n");
        }

        writer.writeCharacters("          ");
        writer.writeEndElement(); // field
        writer.writeCharacters("\n");
    }

    // /**
    // * Export an object as a nested element.
    // */
    // private void exportNestedObject(XMLStreamWriter writer, Object obj, Long
    // objectId, String elementName)
    // throws XMLStreamException {

    // writer.writeStartElement(sanitizeName(elementName));
    // writer.writeAttribute("id", String.valueOf(objectId));

    // // Mark as exported
    // exportedObjectIds.add(objectId);

    // // Export fields of the nested object
    // com.db4o.ext.ExtObjectContainer container =
    // engine.getDatabase().getContainer();
    // ObjectResolverUtil.activateObject(container, obj, objectId);

    // // Get the class and export its fields
    // // For now, we'll use a simplified approach
    // writer.writeCharacters("<!-- Nested object: " + obj.getClass().getName() + "
    // -->");

    // writer.writeEndElement();
    // }

    // /**
    // * Determine if an object should be nested or referenced by ID.
    // * Objects referenced by multiple parents should use ID/IDREF.
    // * Objects referenced by only one parent can be nested.
    // */
    // private boolean shouldNestObject(Long objectId) {
    // // Count how many times this object is referenced
    // int referenceCount = countReferencesToObject(objectId);
    // return referenceCount <= 1;
    // }

    // /**
    // * Count how many objects reference the given object ID.
    // */
    // private int countReferencesToObject(Long targetObjectId) {
    // int count = 0;

    // // Scan all resolved objects to count references
    // for (DODatabaseClass dbClass : engine.getDatabase().getClasses()) {
    // DODatabaseObject[] objects = dbClass.getResolvedObjects();
    // if (objects == null) {
    // continue;
    // }

    // for (DODatabaseObject obj : objects) {
    // // Check direct references
    // DOObjectReference[] refs = obj.getReferences();
    // if (refs != null) {
    // for (DOObjectReference ref : refs) {
    // if (ref.getTargetObjectId().equals(targetObjectId)) {
    // count++;
    // }
    // }
    // }

    // // Check collection references
    // DOCollectionReference[] collections = obj.getCollections();
    // if (collections != null) {
    // for (DOCollectionReference collection : collections) {
    // Long[] containedIds = collection.getContainedObjectIds();
    // if (containedIds != null) {
    // for (Long id : containedIds) {
    // if (id.equals(targetObjectId)) {
    // count++;
    // }
    // }
    // }
    // }
    // }
    // }
    // }

    // return count;
    // }

    /**
     * Check if a database field should be exported according to the schema.
     * Matches the database field to its corresponding schema field and checks the
     * isExported flag.
     */
    private boolean shouldExportField(DODatabaseField dbField, DOSchemaClass schemaClass) {
        if (schemaClass == null || schemaClass.getFields() == null) {
            return true; // Export all fields if no schema filtering available
        }

        // Try to match database field to schema field by name
        String dbFieldName = dbField.getName();
        for (DOSchemaField schemaField : schemaClass.getFields()) {
            // Match by source name (the database field name)
            if (schemaField.source.equals(dbFieldName)) {
                return schemaField.isExported;
            }
        }

        // If no schema field found, default to exporting
        return true;
    }

    /**
     * Find a collection reference for a specific field in an object.
     */
    private DOCollectionReference findCollectionReference(DODatabaseObject obj, DODatabaseField field) {
        DOCollectionReference[] collections = obj.getCollections();
        if (collections == null) {
            return null;
        }

        for (DOCollectionReference collection : collections) {
            if (collection.getField().getName().equals(field.getName())) {
                return collection;
            }
        }

        return null;
    }

    /**
     * Find a class by its absolute name.
     */
    private DODatabaseClass findClassByName(String absoluteName) {
        for (DODatabaseClass dbClass : engine.getDatabase().getClasses()) {
            if (dbClass.getAbsoluteName().equals(absoluteName)) {
                return dbClass;
            }
        }
        return null;
    }
}
