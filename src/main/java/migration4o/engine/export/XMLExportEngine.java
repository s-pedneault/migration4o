package migration4o.engine.export;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;
import com.db4o.foundation.io.File4;
import migration4o.database.DODatabaseOpener;
import migration4o.util.ObjectResolverUtil;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import com.db4o.reflect.generic.GenericObject;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * XML Export Engine that exports objects using the same reachability logic
 * as the Reach Analysis feature. Follows object references through fields,
 * handles IDEntite relationships, and exports the entire object graph to XML.
 */
public class XMLExportEngine {
    private DOSchema schema; // Reference schema for class definitions
    private DOSchema databaseSchema; // Database schema with actual object IDs
    private String databasePath;
    private Set<Long> exportedObjectIds; // Track exported objects to avoid duplicates
    private FileWriter writer;

    public XMLExportEngine(DOSchema schema, DOSchema databaseSchema, String databasePath) {
        this.schema = schema;
        this.databaseSchema = databaseSchema;
        this.databasePath = databasePath;
        this.exportedObjectIds = new HashSet<>();
    }

    /**
     * Exports objects of a specific class to XML file
     */
    public void exportClass(String className, String outputPath) throws Exception {
        DOSchemaClass schemaClass = findClassByName(className);
        if (schemaClass == null) {
            throw new IllegalArgumentException("Class not found: " + className);
        }

        // Reset exported object IDs for this export
        exportedObjectIds.clear();

        ExtObjectContainer container = null;
        try {
            // Open database
            DODatabaseOpener opener = new DODatabaseOpener();
            container = opener.openDatabase(databasePath);

            // Create output file
            writer = new FileWriter(outputPath);
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write("<export>\n");
            writer.write("  <metadata>\n");
            writer.write("    <exportClass>" + xmlEscape(className) + "</exportClass>\n");
            writer.write("    <exportDate>" + new Date() + "</exportDate>\n");
            writer.write("  </metadata>\n");
            writer.write("  <objects>\n");

            // Get object IDs from the database schema (which has actual object IDs)
            DOSchemaClass dbSchemaClass = findClassByName(databaseSchema, className);
            if (dbSchemaClass != null) {
                long[] objectIds = dbSchemaClass.getUniqueObjectIds();
                System.out.println("DEBUG: Found " + (objectIds != null ? objectIds.length : 0)
                        + " objects for class " + className);
                if (objectIds != null) {
                    for (long objectId : objectIds) {
                        exportObjectRecursively(container, objectId, 2);
                    }
                }
            } else {
                System.err.println("Warning: Class not found in database schema: " + className);
            }

            writer.write("  </objects>\n");
            writer.write("</export>\n");
            writer.close();

            System.out.println("Exported " + exportedObjectIds.size() + " objects to " + outputPath);
            // Generate XSD schema file
            String xsdPath = outputPath.replace(".xml", ".xsd");
            generateXSD(className, xsdPath);
            System.out.println("Generated XSD schema: " + xsdPath);
        } finally {
            if (container != null) {
                container.close();
            }
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    /**
     * Exports all objects in a module (all classes in the module) to XML file
     */
    public void exportModule(List<String> classNames, String moduleName, String outputPath) throws Exception {
        // Reset exported object IDs for this export
        exportedObjectIds.clear();

        ExtObjectContainer container = null;
        try {
            // Open database
            DODatabaseOpener opener = new DODatabaseOpener();
            container = opener.openDatabase(databasePath);

            // Create output file
            writer = new FileWriter(outputPath);
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write("<export>\n");
            writer.write("  <metadata>\n");
            writer.write("    <moduleName>" + xmlEscape(moduleName) + "</moduleName>\n");
            writer.write("    <classCount>" + classNames.size() + "</classCount>\n");
            writer.write("    <exportDate>" + new Date() + "</exportDate>\n");
            writer.write("  </metadata>\n");
            writer.write("  <objects>\n");

            // Export all objects from each class in the module
            for (String className : classNames) {
                // Get object IDs from the database schema (which has actual object IDs)
                DOSchemaClass dbSchemaClass = findClassByName(databaseSchema, className);
                if (dbSchemaClass != null) {
                    long[] objectIds = dbSchemaClass.getUniqueObjectIds();
                    if (objectIds != null) {
                        for (long objectId : objectIds) {
                            exportObjectRecursively(container, objectId, 2);
                        }
                    }
                } else {
                    System.err.println("Warning: Class not found in database schema: " + className);
                }
            }

            writer.write("  </objects>\n");
            writer.write("</export>\n");
            writer.close();

            System.out.println("Exported " + exportedObjectIds.size() + " objects to " + outputPath);
        } finally {
            if (container != null) {
                container.close();
            }
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    /**
     * Recursively exports an object and all its referenced objects.
     * Uses the same reachability logic as the Reach Analysis feature.
     */
    private void exportObjectRecursively(ExtObjectContainer container, long objectId, int indentLevel)
            throws IOException {
        // Avoid exporting the same object twice
        if (!exportedObjectIds.add(objectId)) {
            return;
        }

        try {
            // Get and activate the object
            Object obj = container.ext().getByID(objectId);
            if (obj == null) {
                return;
            }

            String className = getClassName(obj);
            ObjectResolverUtil.activateObject(container, obj, objectId);

            // Write object opening tag
            writeIndent(indentLevel);
            writer.write("<object id=\"" + objectId + "\" class=\"" + xmlEscape(className) + "\">\n");

            // If it's a GenericObject, export all its fields
            if (obj instanceof GenericObject) {
                GenericObject genericObj = (GenericObject) obj;
                StoredClass storedClass = container.ext().storedClass(genericObj);
                if (storedClass != null) {
                    exportAllFields(container, genericObj, className, indentLevel + 1);
                }
            }

            // Write object closing tag
            writeIndent(indentLevel);
            writer.write("</object>\n");
        } catch (Exception e) {
            System.err.println("Error exporting object " + objectId + ": " + e.getMessage());
        }
    }

    /**
     * Exports all fields of a GenericObject, following references recursively.
     */
    private void exportAllFields(ExtObjectContainer container, GenericObject obj, String parentClassName,
            int indentLevel) throws IOException {
        try {
            StoredClass storedClass = container.ext().storedClass(obj);
            if (storedClass == null) {
                return;
            }

            StoredField[] fields = storedClass.getStoredFields();
            for (StoredField field : fields) {
                try {
                    Object fieldValue = field.get(obj);
                    String fieldName = field.getName();

                    writeIndent(indentLevel);
                    writer.write("<field name=\"" + xmlEscape(fieldName) + "\"");

                    if (fieldValue == null) {
                        writer.write("/>");
                        continue;
                    }

                    // Handle collections
                    if (fieldValue instanceof Collection) {
                        writer.write(">");
                        Collection<?> collection = (Collection<?>) fieldValue;
                        for (Object item : collection) {
                            if (item != null) {
                                exportFieldValue(container, item, fieldName, parentClassName, indentLevel + 1);
                            }
                        }
                        writeIndent(indentLevel);
                        writer.write("</field>\n");
                    }
                    // Handle arrays
                    else if (fieldValue.getClass().isArray()) {
                        writer.write(">");
                        int length = java.lang.reflect.Array.getLength(fieldValue);
                        for (int i = 0; i < length; i++) {
                            Object item = java.lang.reflect.Array.get(fieldValue, i);
                            if (item != null) {
                                exportFieldValue(container, item, fieldName, parentClassName, indentLevel + 1);
                            }
                        }
                        writeIndent(indentLevel);
                        writer.write("</field>\n");
                    }
                    // Handle single values
                    else {
                        long refId = container.ext().getID(fieldValue);
                        if (refId > 0) {
                            // This is a persistent object reference
                            writer.write(">");
                            exportFieldValue(container, fieldValue, fieldName, parentClassName, indentLevel + 1);
                            writeIndent(indentLevel);
                            writer.write("</field>\n");
                        } else {
                            // Primitive or non-persistent value
                            writer.write(">");
                            writer.write(xmlEscape(fieldValue.toString()));
                            writer.write("</field>\n");
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error exporting field " + field.getName() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Error accessing fields: " + e.getMessage());
        }
    }

    /**
     * Exports a field value (handles references and IDEntite relationships)
     */
    private void exportFieldValue(ExtObjectContainer container, Object item, String fieldName,
            String parentClassName, int indentLevel) throws IOException {
        long childId = container.ext().getID(item);
        if (childId <= 0) {
            return; // Not a persistent object
        }

        String className = getClassName(item);
        if (className == null) {
            return;
        }

        // Check if this is an IDEntite descendant
        DOSchemaClass itemClass = findClassByName(className);
        if (itemClass != null && isDescendantOf(itemClass, "gest.gen.IDEntite")) {
            // This is an IDEntite - check if we should embed its contents
            DOSchemaField schemaField = findSchemaField(parentClassName, fieldName);
            boolean embedContents = (schemaField != null && schemaField.isEmbedContents());

            // Get target type from field's pointsTo or extract from field name
            String expectedType = null;
            if (schemaField != null && schemaField.getPointsTo() != null) {
                expectedType = schemaField.getPointsTo();
            } else {
                // Fallback to name extraction
                expectedType = extractExpectedTypeFromFieldName(fieldName, className);
            }

            // Handle the special mID relationship with type filtering
            handleIDEntiteExport(container, item, childId, expectedType, embedContents, indentLevel);
        } else {
            // Regular object - export it recursively
            exportObjectRecursively(container, childId, indentLevel);
        }
    }

    /**
     * Handles IDEntite relationships based on embedContents flag:
     * - If embedContents=true: resolves to target object and exports it fully
     * - If embedContents=false: exports only the IDEntite reference (with mID
     * field)
     */
    private void handleIDEntiteExport(ExtObjectContainer container, Object idEntiteObj,
            long idEntiteId, String expectedType, boolean embedContents, int indentLevel) throws IOException {

        try {
            // Activate and extract the mID field
            ObjectResolverUtil.activateObject(container, idEntiteObj, idEntiteId);
            Long mID = extractMIDField(container, idEntiteObj);

            if (mID == null || mID == -1) {
                // No valid mID - this IDEntite doesn't point to anything, skip it
                return;
            }

            if (!embedContents) {
                // Export only the IDEntite reference (not the full target object)
                // This exports the IDEntite fields including mID, allowing the consumer
                // to know this field refers to a separately exported object with the same mID
                exportObjectRecursively(container, idEntiteId, indentLevel);
                return;
            }

            // embedContents=true: resolve IDEntite to its target object and export it fully
            // Find the target EntiteContientID object with matching mID
            for (DOSchemaClass schemaClass : databaseSchema.getClasses()) {
                if (isDescendantOf(schemaClass, "gest.gen.EntiteContientID")) {
                    // Check if this class matches the expected type (if specified)
                    String fullClassName = schemaClass.getAbsoluteName();

                    // Only search in classes that match the expected type
                    if (expectedType != null && !fullClassName.equals(expectedType)) {
                        continue; // Skip classes that don't match the expected type
                    }

                    long[] objectIds = schemaClass.getUniqueObjectIds();
                    if (objectIds != null) {
                        for (long objectId : objectIds) {
                            try {
                                Object obj = container.ext().getByID(objectId);
                                if (obj != null) {
                                    ObjectResolverUtil.activateObject(container, obj, objectId);
                                    Long objMID = extractMIDField(container, obj);

                                    // If mIDs match, export this target object
                                    if (mID.equals(objMID)) {
                                        exportObjectRecursively(container, objectId, indentLevel);
                                        // Only export the first matching object
                                        return;
                                    }
                                }
                            } catch (Exception e) {
                                // Skip objects that can't be processed
                            }
                        }
                    }
                }
            }

            // If we didn't find a matching target, it's likely because the target class
            // is not in the database (e.g., gest.langue.Langue not found in this dataset)
            // This is normal and not an error condition - just don't export anything
        } catch (Exception e) {
            System.err.println("Error handling IDEntite relationship for object " + idEntiteId + ": " + e.getMessage());
        }
    }

    /**
     * Extracts the mID field value from a GenericObject.
     */
    private Long extractMIDField(ExtObjectContainer container, Object obj) {
        if (!(obj instanceof GenericObject)) {
            return null;
        }

        GenericObject genericObj = (GenericObject) obj;
        StoredClass storedClass = container.ext().storedClass(genericObj);
        if (storedClass == null) {
            return null;
        }

        StoredField[] fields = storedClass.getStoredFields();
        for (StoredField field : fields) {
            if ("mID".equals(field.getName())) {
                try {
                    Object value = field.get(genericObj);
                    if (value instanceof Long) {
                        return (Long) value;
                    } else if (value instanceof Integer) {
                        return ((Integer) value).longValue();
                    }
                } catch (Exception e) {
                    // Field access failed
                }
            }
        }

        return null;
    }

    /**
     * Extracts expected EntiteContientID type from field name.
     */
    private String extractExpectedTypeFromFieldName(String fieldName, String idClassName) {
        if (fieldName.startsWith("mID")) {
            return fieldName.substring(3); // Remove "mID" prefix
        }
        String simpleClassName = idClassName.substring(idClassName.lastIndexOf('.') + 1);
        if (simpleClassName.startsWith("ID")) {
            return simpleClassName.substring(2); // Remove "ID" prefix
        }
        return null;
    }

    private String getClassName(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof GenericObject) {
            GenericObject genericObj = (GenericObject) obj;
            try {
                if (genericObj.getGenericClass() != null) {
                    return genericObj.getGenericClass().getName();
                }
            } catch (Exception e) {
                // Fall back to regular class name
            }
        }
        return obj.getClass().getName();
    }

    private DOSchemaClass findClassByName(String className) {
        return findClassByName(schema, className);
    }

    private DOSchemaClass findClassByName(DOSchema targetSchema, String className) {
        if (targetSchema == null || targetSchema.getClasses() == null) {
            return null;
        }
        for (DOSchemaClass schemaClass : targetSchema.getClasses()) {
            if (schemaClass.getAbsoluteName().equals(className)) {
                return schemaClass;
            }
        }
        return null;
    }

    private DOSchemaField findSchemaField(String className, String fieldName) {
        DOSchemaClass schemaClass = findClassByName(className);
        if (schemaClass == null || schemaClass.getFields() == null) {
            return null;
        }
        for (DOSchemaField field : schemaClass.getFields()) {
            if (field.getSource() != null && field.getSource().equals(fieldName)) {
                return field;
            }
        }
        return null;
    }

    private boolean isDescendantOf(DOSchemaClass schemaClass, String ancestorClassName) {
        if (schemaClass == null || ancestorClassName == null) {
            return false;
        }

        String currentClassName = schemaClass.getAbsoluteName();
        if (currentClassName.equals(ancestorClassName)) {
            return true;
        }

        String parentClassName = schemaClass.getParentClass();
        if (parentClassName == null || parentClassName.isEmpty()) {
            return false;
        }

        if (parentClassName.equals(ancestorClassName)) {
            return true;
        }

        DOSchemaClass parentClass = findClassByName(parentClassName);
        if (parentClass != null) {
            return isDescendantOf(parentClass, ancestorClassName);
        }

        return false;
    }

    private void writeIndent(int level) throws IOException {
        for (int i = 0; i < level; i++) {
            writer.write("  ");
        }
    }

    private String xmlEscape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Generates an XSD schema file for the exported XML.
     * The schema defines the structure and data types based on the class
     * definition.
     */
    private void generateXSD(String className, String xsdPath) throws IOException {
        DOSchemaClass schemaClass = findClassByName(className);
        if (schemaClass == null) {
            return;
        }

        FileWriter xsdWriter = new FileWriter(xsdPath);
        try {
            xsdWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xsdWriter.write("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n\n");

            // Root export element
            xsdWriter.write("  <xs:element name=\"export\">\n");
            xsdWriter.write("    <xs:complexType>\n");
            xsdWriter.write("      <xs:sequence>\n");
            xsdWriter.write("        <xs:element name=\"metadata\" type=\"MetadataType\"/>\n");
            xsdWriter.write("        <xs:element name=\"objects\" type=\"ObjectsType\"/>\n");
            xsdWriter.write("      </xs:sequence>\n");
            xsdWriter.write("    </xs:complexType>\n");
            xsdWriter.write("  </xs:element>\n\n");

            // Metadata type
            xsdWriter.write("  <xs:complexType name=\"MetadataType\">\n");
            xsdWriter.write("    <xs:sequence>\n");
            xsdWriter.write("      <xs:element name=\"exportClass\" type=\"xs:string\"/>\n");
            xsdWriter.write("      <xs:element name=\"exportDate\" type=\"xs:string\"/>\n");
            xsdWriter.write("    </xs:sequence>\n");
            xsdWriter.write("  </xs:complexType>\n\n");

            // Objects container type
            xsdWriter.write("  <xs:complexType name=\"ObjectsType\">\n");
            xsdWriter.write("    <xs:sequence>\n");
            xsdWriter.write(
                    "      <xs:element name=\"object\" type=\"ObjectType\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>\n");
            xsdWriter.write("    </xs:sequence>\n");
            xsdWriter.write("  </xs:complexType>\n\n");

            // Generic object type
            xsdWriter.write("  <xs:complexType name=\"ObjectType\">\n");
            xsdWriter.write("    <xs:sequence>\n");
            xsdWriter.write("      <xs:element name=\"field\" minOccurs=\"0\" maxOccurs=\"unbounded\">\n");
            xsdWriter.write("        <xs:complexType mixed=\"true\">\n");
            xsdWriter.write("          <xs:sequence>\n");
            xsdWriter.write(
                    "            <xs:element name=\"object\" type=\"ObjectType\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>\n");
            xsdWriter.write("          </xs:sequence>\n");
            xsdWriter.write("          <xs:attribute name=\"name\" type=\"xs:string\" use=\"required\"/>\n");
            xsdWriter.write("        </xs:complexType>\n");
            xsdWriter.write("      </xs:element>\n");
            xsdWriter.write("    </xs:sequence>\n");
            xsdWriter.write("    <xs:attribute name=\"class\" type=\"xs:string\" use=\"required\"/>\n");
            xsdWriter.write("    <xs:attribute name=\"id\" type=\"xs:long\" use=\"required\"/>\n");
            xsdWriter.write("  </xs:complexType>\n\n");

            // Add specific class types with field definitions
            writeClassTypeDefinition(xsdWriter, schemaClass, new HashSet<>());

            xsdWriter.write("</xs:schema>\n");
        } finally {
            xsdWriter.close();
        }
    }

    /**
     * Writes class-specific type definitions to the XSD, including field types.
     * This provides detailed validation and documentation for each class structure.
     */
    private void writeClassTypeDefinition(FileWriter xsdWriter, DOSchemaClass schemaClass, Set<String> processedClasses)
            throws IOException {
        String className = schemaClass.getAbsoluteName();

        // Avoid infinite recursion
        if (processedClasses.contains(className)) {
            return;
        }
        processedClasses.add(className);

        xsdWriter.write("  <!-- Class: " + className + " -->\n");
        xsdWriter.write("  <xs:complexType name=\"" + getTypeNameFromClass(className) + "\">\n");
        xsdWriter.write("    <xs:annotation>\n");
        xsdWriter.write("      <xs:documentation>\n");

        // Add field documentation
        for (DOSchemaField field : schemaClass.getFields()) {
            String fieldType = field.getType() != null ? field.getType() : "unknown";
            String fieldDesc = field.getDescription() != null ? field.getDescription() : "";
            xsdWriter.write("        Field '" + field.getSource() + "': " + fieldType);
            if (field.isCollection()) {
                xsdWriter.write(" (collection)");
            }
            if (!fieldDesc.isEmpty()) {
                xsdWriter.write(" - " + fieldDesc);
            }
            xsdWriter.write("\n");
        }

        xsdWriter.write("      </xs:documentation>\n");
        xsdWriter.write("    </xs:annotation>\n");
        xsdWriter.write("  </xs:complexType>\n\n");

        // Recursively process referenced classes
        for (DOSchemaField field : schemaClass.getFields()) {
            if (field.getType() != null && !isPrimitiveType(field.getType())) {
                DOSchemaClass referencedClass = findClassByName(field.getType());
                if (referencedClass != null) {
                    writeClassTypeDefinition(xsdWriter, referencedClass, processedClasses);
                }
            }
        }
    }

    /**
     * Converts a fully qualified class name to an XSD type name.
     */
    private String getTypeNameFromClass(String className) {
        return className.replace(".", "_") + "Type";
    }

    /**
     * Checks if a type name represents a primitive type.
     */
    private boolean isPrimitiveType(String typeName) {
        return typeName.equals("java.lang.String") ||
                typeName.equals("java.lang.Integer") ||
                typeName.equals("java.lang.Long") ||
                typeName.equals("java.lang.Boolean") ||
                typeName.equals("java.lang.Double") ||
                typeName.equals("java.lang.Float") ||
                typeName.equals("java.util.Date") ||
                typeName.startsWith("java.lang.") ||
                typeName.startsWith("java.util.");
    }
}
