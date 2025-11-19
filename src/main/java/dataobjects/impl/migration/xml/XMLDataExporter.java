package dataobjects.impl.migration.xml;

import dataobjects.api.engine.DOEngine;
import dataobjects.api.models.DOClass;
import dataobjects.api.models.DOField;
import dataobjects.api.models.database.DOCollectionReference;
import dataobjects.api.models.database.DODatabaseClass;
import dataobjects.api.models.database.DODatabaseObject;
import dataobjects.api.models.database.DOObjectReference;
import dataobjects.api.models.schema.DOSchemaClass;
import dataobjects.api.models.schema.DOSchemaModule;
import dataobjects.util.ObjectResolverUtil;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Exports database objects to XML files using streaming for performance.
 * Handles nested objects vs ID/IDREF references based on reference count.
 */
public class XMLDataExporter {

    private final DOEngine engine;
    private final String namespace;
    private final Set<Long> exportedObjectIds = new HashSet<>();

    public XMLDataExporter(DOEngine engine, String namespace) {
        this.engine = engine;
        this.namespace = namespace;
    }

    /**
     * Export a single module to an XML file.
     */
    public void exportModule(DOSchemaModule module, String outputPath) throws IOException {
        exportedObjectIds.clear(); // Reset for each module

        XMLOutputFactory factory = XMLOutputFactory.newInstance();

        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            XMLStreamWriter writer = factory.createXMLStreamWriter(fos, "UTF-8");

            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeCharacters("\n");

            // Root element for the module
            writer.writeStartElement(sanitizeName(module.getName()));
            writer.writeDefaultNamespace(namespace);
            writer.writeCharacters("\n");

            // Export objects for each class in the module
            if (module.getClasses() != null) {
                for (DOSchemaClass schemaClass : module.getClasses()) {
                    exportClassObjects(writer, schemaClass);
                }
            }

            writer.writeEndElement(); // module root
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
     * Export all objects for a class.
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

        String className = schemaClass.getShortName();

        // Export ALL objects from this class (reachability is tracked separately)
        for (DODatabaseObject obj : objects) {
            writer.writeCharacters("  ");
            exportObject(writer, obj, className, true);
            writer.writeCharacters("\n");
        }
    }

    /**
     * Export a single object as XML.
     * 
     * @param writer        The XML writer
     * @param obj           The object to export
     * @param elementName   The XML element name to use
     * @param trackExported Whether to track this as exported (to avoid duplicates)
     */
    private void exportObject(XMLStreamWriter writer, DODatabaseObject obj, String elementName, boolean trackExported)
            throws XMLStreamException {

        Long objectId = obj.getObjectId();

        // Check if already exported (avoid duplicates)
        if (trackExported && exportedObjectIds.contains(objectId)) {
            return; // Skip duplicate
        }

        if (trackExported) {
            exportedObjectIds.add(objectId);
        }

        writer.writeStartElement(sanitizeName(elementName));
        writer.writeAttribute("id", String.valueOf(objectId));
        writer.writeCharacters("\n");

        // Get the most specific class
        DOClass mostSpecificClass = obj.getMostSpecificClass();

        // Export all fields from the inheritance chain
        exportObjectFields(writer, obj, mostSpecificClass);

        writer.writeCharacters("  ");
        writer.writeEndElement(); // element
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
    private void exportObjectFields(XMLStreamWriter writer, DODatabaseObject obj, DOClass clazz)
            throws XMLStreamException {

        if (clazz == null) {
            return;
        }

        DOField[] fields = clazz.getFields();
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

        for (DOField field : fields) {
            exportField(writer, field, obj, actualObj, primitiveValues);
        }

        // Handle parent class fields (inheritance)
        String superClassName = clazz.getSuperClassAbsoluteName();
        if (superClassName != null && !superClassName.equals("java.lang.Object")) {
            // Find parent class and export its fields
            DOClass parentClass = findClassByName(superClassName);
            if (parentClass != null) {
                exportObjectFields(writer, obj, parentClass);
            }
        }
    }

    /**
     * Export fields from an actual object (for unreached objects).
     */
    private void exportFieldsFromActualObject(XMLStreamWriter writer, Object actualObj,
            DODatabaseClass dbClass, Long objectId) throws XMLStreamException {

        DOField[] fields = dbClass.getFields();
        if (fields == null) {
            return;
        }

        com.db4o.ext.ExtObjectContainer container = engine.getDatabase().getContainer();

        for (DOField field : fields) {
            Object fieldValue = ObjectResolverUtil.getFieldValue(container, actualObj, field);
            exportFieldValue(writer, field, fieldValue);
        }
    }

    /**
     * Export a single field.
     */
    private void exportField(XMLStreamWriter writer, DOField field, DODatabaseObject obj, Object actualObj,
            Map<String, ObjectResolverUtil.PrimitiveFieldValue> primitiveValues) throws XMLStreamException {

        // Check if it's in primitive values
        ObjectResolverUtil.PrimitiveFieldValue primitiveValue = primitiveValues.get(field.getName());

        if (primitiveValue != null && primitiveValue.value != null) {
            // Export primitive value
            writer.writeCharacters("    ");
            writer.writeStartElement(sanitizeName(field.getName()));
            writer.writeCharacters(String.valueOf(primitiveValue.value));
            writer.writeEndElement();
            writer.writeCharacters("\n");
            return;
        }

        // Handle collections
        if (field.isArray()) {
            exportCollection(writer, field, obj, actualObj);
            return;
        }

        // Handle object references
        if (!field.isPrimitive() && actualObj != null) {
            com.db4o.ext.ExtObjectContainer container = engine.getDatabase().getContainer();
            Object fieldValue = ObjectResolverUtil.getFieldValue(container, actualObj, field);

            if (fieldValue != null) {
                Long refObjectId = ObjectResolverUtil.getObjectId(container, fieldValue);
                if (refObjectId != null) {
                    // Determine if we should nest or reference by ID
                    boolean shouldNest = shouldNestObject(refObjectId);

                    if (shouldNest) {
                        // Export nested object
                        writer.writeCharacters("    ");
                        exportNestedObject(writer, fieldValue, refObjectId, field.getName());
                        writer.writeCharacters("\n");
                    } else {
                        // Export as reference (ID only)
                        writer.writeCharacters("    ");
                        writer.writeStartElement(sanitizeName(field.getName()));
                        writer.writeAttribute("ref", String.valueOf(refObjectId));
                        writer.writeEndElement();
                        writer.writeCharacters("\n");
                    }
                }
            }
        }
    }

    /**
     * Export a field value (for unreached objects).
     */
    private void exportFieldValue(XMLStreamWriter writer, DOField field, Object value) throws XMLStreamException {
        if (value == null) {
            return;
        }

        writer.writeCharacters("    ");
        writer.writeStartElement(sanitizeName(field.getName()));

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
     * Export a collection field.
     */
    private void exportCollection(XMLStreamWriter writer, DOField field, DODatabaseObject obj, Object actualObj)
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

        // Export each item in the collection
        for (Long itemId : containedIds) {
            Object item = container.getByID(itemId);
            if (item != null) {
                ObjectResolverUtil.activateObject(container, item, itemId);

                boolean shouldNest = shouldNestObject(itemId);

                writer.writeCharacters("    ");
                if (shouldNest) {
                    exportNestedObject(writer, item, itemId, field.getName());
                } else {
                    writer.writeStartElement(sanitizeName(field.getName()));
                    writer.writeAttribute("ref", String.valueOf(itemId));
                    writer.writeEndElement();
                }
                writer.writeCharacters("\n");
            }
        }
    }

    /**
     * Export an object as a nested element.
     */
    private void exportNestedObject(XMLStreamWriter writer, Object obj, Long objectId, String elementName)
            throws XMLStreamException {

        writer.writeStartElement(sanitizeName(elementName));
        writer.writeAttribute("id", String.valueOf(objectId));

        // Mark as exported
        exportedObjectIds.add(objectId);

        // Export fields of the nested object
        com.db4o.ext.ExtObjectContainer container = engine.getDatabase().getContainer();
        ObjectResolverUtil.activateObject(container, obj, objectId);

        // Get the class and export its fields
        // For now, we'll use a simplified approach
        writer.writeCharacters("<!-- Nested object: " + obj.getClass().getName() + " -->");

        writer.writeEndElement();
    }

    /**
     * Determine if an object should be nested or referenced by ID.
     * Objects referenced by multiple parents should use ID/IDREF.
     * Objects referenced by only one parent can be nested.
     */
    private boolean shouldNestObject(Long objectId) {
        // Count how many times this object is referenced
        int referenceCount = countReferencesToObject(objectId);
        return referenceCount <= 1;
    }

    /**
     * Count how many objects reference the given object ID.
     */
    private int countReferencesToObject(Long targetObjectId) {
        int count = 0;

        // Scan all resolved objects to count references
        for (DODatabaseClass dbClass : engine.getDatabase().getClasses()) {
            DODatabaseObject[] objects = dbClass.getResolvedObjects();
            if (objects == null) {
                continue;
            }

            for (DODatabaseObject obj : objects) {
                // Check direct references
                DOObjectReference[] refs = obj.getReferences();
                if (refs != null) {
                    for (DOObjectReference ref : refs) {
                        if (ref.getTargetObjectId().equals(targetObjectId)) {
                            count++;
                        }
                    }
                }

                // Check collection references
                DOCollectionReference[] collections = obj.getCollections();
                if (collections != null) {
                    for (DOCollectionReference collection : collections) {
                        Long[] containedIds = collection.getContainedObjectIds();
                        if (containedIds != null) {
                            for (Long id : containedIds) {
                                if (id.equals(targetObjectId)) {
                                    count++;
                                }
                            }
                        }
                    }
                }
            }
        }

        return count;
    }

    /**
     * Find a collection reference for a specific field in an object.
     */
    private DOCollectionReference findCollectionReference(DODatabaseObject obj, DOField field) {
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
    private DOClass findClassByName(String absoluteName) {
        for (DODatabaseClass dbClass : engine.getDatabase().getClasses()) {
            if (dbClass.getAbsoluteName().equals(absoluteName)) {
                return dbClass;
            }
        }
        return null;
    }

    /**
     * Sanitize a name for XML.
     */
    private String sanitizeName(String name) {
        if (name == null) {
            return "unnamed";
        }
        return name.replaceAll("[^a-zA-Z0-9_.-]", "_");
    }
}
