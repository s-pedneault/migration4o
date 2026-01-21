package migration4o.engine.export;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
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

public class XMLExportEngine {
    private DOSchema schema; // Reference schema for class definitions
    private DOSchema databaseSchema; // Database schema with actual object IDs
    private String databasePath;
    private Set<Long> exportedObjectIds; // Track exported objects to avoid duplicates
    private FileWriter writer;
    private XSDBuilder xsdBuilder; // In-memory XSD builder to record structure as XML is generated

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

        // Reset XSD builder
        xsdBuilder = new XSDBuilder();

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
                // Use objectIds (not uniqueObjectIds) to get ALL objects for this class,
                // including those that might be in subclasses
                long[] objectIds = dbSchemaClass.objectIds;
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
        exportModule(classNames, moduleName, outputPath, null);
    }

    /**
     * Exports all objects in a module (all classes in the module) to XML file, and
     * generates XSD in sync.
     */
    public void exportModule(List<String> classNames, String moduleName, String outputPath, String xsdOutputPath)
            throws Exception {
        // Reset exported object IDs for this export
        exportedObjectIds.clear();

        // Reset XSD builder
        xsdBuilder = new XSDBuilder();

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

            // XSD: record root structure
            xsdBuilder.startExportRoot();

            // Export all objects from each class in the module
            for (String className : classNames) {
                // Get object IDs from the database schema (which has actual object IDs)
                DOSchemaClass dbSchemaClass = findClassByName(databaseSchema, className);
                if (dbSchemaClass != null) {
                    // XSD: record top-level object type
                    xsdBuilder.addTopLevelObject(dbSchemaClass.destinationName, dbSchemaClass);
                    // Use objectIds (not uniqueObjectIds) to get ALL objects for this class
                    long[] objectIds = dbSchemaClass.objectIds;
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

            // Write XSD if requested
            if (xsdOutputPath != null) {
                xsdBuilder.writeXSD(xsdOutputPath);
            }

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

            // Write object opening tag using destination class name as element name
            DOSchemaClass schemaClass = findClassByName(className);
            String elementName = schemaClass != null ? schemaClass.destinationName : getSimpleClassName(className);
            writeIndent(indentLevel);
            writer.write("<" + elementName + ">\n");

            // XSD: record this class structure
            if (schemaClass != null) {
                xsdBuilder.addClass(schemaClass);
            }

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
            writer.write("</" + elementName + ">\n");
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
                    String sourceFieldName = field.getName();

                    // Get destination field name from schema
                    DOSchemaField schemaField = findSchemaField(parentClassName, sourceFieldName);
                    String fieldName = schemaField != null ? schemaField.destinationName : sourceFieldName;

                    // XSD: record field type
                    if (schemaField != null) {
                        xsdBuilder.addField(parentClassName, schemaField);
                    }

                    if (fieldValue == null) {
                        writeIndent(indentLevel);
                        writer.write("<" + fieldName + "/>\n");
                        continue;
                    }

                    // Handle collections
                    if (fieldValue instanceof Collection) {
                        Collection<?> collection = (Collection<?>) fieldValue;
                        if (collection.isEmpty()) {
                            writeIndent(indentLevel);
                            writer.write("<" + fieldName + "/>\n");
                        } else {
                            writeIndent(indentLevel);
                            writer.write("<" + fieldName + ">\n");
                            for (Object item : collection) {
                                if (item != null) {
                                    exportFieldValue(container, item, fieldName, parentClassName, indentLevel + 1);
                                }
                            }
                            writeIndent(indentLevel);
                            writer.write("</" + fieldName + ">\n");
                        }
                    } else if (fieldValue.getClass().isArray()) {
                        int length = java.lang.reflect.Array.getLength(fieldValue);
                        if (length == 0) {
                            writeIndent(indentLevel);
                            writer.write("<" + fieldName + "/>\n");
                        } else {
                            writeIndent(indentLevel);
                            writer.write("<" + fieldName + ">\n");
                            for (int i = 0; i < length; i++) {
                                Object item = java.lang.reflect.Array.get(fieldValue, i);
                                if (item != null) {
                                    exportFieldValue(container, item, fieldName, parentClassName, indentLevel + 1);
                                }
                            }
                            writeIndent(indentLevel);
                            writer.write("</" + fieldName + ">\n");
                        }
                    } else {
                        long refId = container.ext().getID(fieldValue);
                        if (refId > 0) {
                            // This is a persistent object reference
                            writeIndent(indentLevel);
                            writer.write("<" + fieldName + ">\n");
                            exportFieldValue(container, fieldValue, fieldName, parentClassName, indentLevel + 1);
                            writeIndent(indentLevel);
                            writer.write("</" + fieldName + ">\n");
                        } else {
                            // Primitive or non-persistent value - write inline
                            writeIndent(indentLevel);
                            writer.write("<" + fieldName + ">");
                            writer.write(xmlEscape(fieldValue.toString()));
                            writer.write("</" + fieldName + ">\n");
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
     * Helper to build XSD structure in-memory as XML is generated.
     */
    private static class XSDBuilder {
        private final Set<String> processedClasses = new LinkedHashSet<>();
        private final Map<String, DOSchemaClass> classMap = new LinkedHashMap<>();
        private final Map<String, Map<String, DOSchemaField>> fieldsByClass = new LinkedHashMap<>();
        private final Set<String> topLevelObjects = new LinkedHashSet<>();

        public void startExportRoot() {
            // No-op for now, but could record root structure if needed
        }

        public void addTopLevelObject(String destName, DOSchemaClass schemaClass) {
            topLevelObjects.add(destName);
            if (schemaClass != null) {
                classMap.put(schemaClass.source, schemaClass);
            }
        }

        public void addClass(DOSchemaClass schemaClass) {
            if (schemaClass == null)
                return;
            String absName = schemaClass.source;
            if (!classMap.containsKey(absName)) {
                classMap.put(absName, schemaClass);
            }
        }

        public void addField(String parentClassName, DOSchemaField field) {
            if (field == null)
                return;
            fieldsByClass.computeIfAbsent(parentClassName, k -> new LinkedHashMap<>())
                    .put(field.destinationName, field);
        }

        public void writeXSD(String xsdPath) throws IOException {
            try (FileWriter xsdWriter = new FileWriter(xsdPath)) {
                xsdWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                xsdWriter.write("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n\n");

                // Root export element
                xsdWriter.write("  <xs:element name=\"export\">\n");
                xsdWriter.write("    <xs:complexType>\n");
                xsdWriter.write("      <xs:sequence>\n");
                xsdWriter.write("        <xs:element name=\"metadata\" type=\"MetadataType\"/>\n");
                xsdWriter.write("        <xs:element name=\"objects\">\n");
                xsdWriter.write("          <xs:complexType>\n");
                xsdWriter.write("            <xs:sequence>\n");
                for (String obj : topLevelObjects) {
                    xsdWriter.write("              <xs:element ref=\"" + obj
                            + "\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>\n");
                }
                xsdWriter.write("            </xs:sequence>\n");
                xsdWriter.write("          </xs:complexType>\n");
                xsdWriter.write("        </xs:element>\n");
                xsdWriter.write("      </xs:sequence>\n");
                xsdWriter.write("    </xs:complexType>\n");
                xsdWriter.write("  </xs:element>\n\n");

                // Metadata type
                xsdWriter.write("  <xs:complexType name=\"MetadataType\">\n");
                xsdWriter.write("    <xs:sequence>\n");
                xsdWriter.write("      <xs:element name=\"moduleName\" type=\"xs:string\"/>\n");
                xsdWriter.write("      <xs:element name=\"classCount\" type=\"xs:int\"/>\n");
                xsdWriter.write("      <xs:element name=\"exportDate\" type=\"xs:string\"/>\n");
                xsdWriter.write("    </xs:sequence>\n");
                xsdWriter.write("  </xs:complexType>\n\n");

                // Write all class definitions
                for (DOSchemaClass schemaClass : classMap.values()) {
                    writeClassTypeDefinition(xsdWriter, schemaClass);
                }

                xsdWriter.write("</xs:schema>\n");
            }
        }

        private void writeClassTypeDefinition(FileWriter xsdWriter, DOSchemaClass schemaClass) throws IOException {
            String className = schemaClass.source;
            String destClassName = schemaClass.destinationName;

            // Write element definition
            xsdWriter.write("  <!-- Class: " + className + " (exported as " + destClassName + ") -->\n");
            xsdWriter.write("  <xs:element name=\"" + destClassName + "\">\n");
            xsdWriter.write("    <xs:complexType>\n");
            xsdWriter.write("      <xs:sequence>\n");

            Map<String, DOSchemaField> fields = fieldsByClass.getOrDefault(className, new LinkedHashMap<>());
            for (DOSchemaField field : fields.values()) {
                String fieldName = field.destinationName;
                String fieldType = field.type;
                boolean isCollection = field.isCollection;
                if (fieldType == null || fieldType.isEmpty())
                    continue;
                String maxOccurs = isCollection ? "unbounded" : "1";
                if (isCollection) {
                    String childrenType = field.childrenType;
                    if (childrenType == null || childrenType.isEmpty()) {
                        childrenType = fieldType;
                    }
                    if (isPrimitiveType(childrenType)) {
                        String xsdType = getXSDType(childrenType);
                        xsdWriter.write("        <xs:element name=\"" + fieldName + "\" type=\"" + xsdType
                                + "\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>\n");
                    } else {
                        String refClassName = getSimpleClassName(childrenType) + "Type";
                        xsdWriter.write("        <xs:element name=\"" + fieldName + "\" type=\"" + refClassName
                                + "\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>\n");
                    }
                } else if (isPrimitiveType(fieldType)) {
                    String xsdType = getXSDType(fieldType);
                    xsdWriter.write("        <xs:element name=\"" + fieldName + "\" type=\"" + xsdType
                            + "\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
                } else {
                    String refClassName = getSimpleClassName(fieldType) + "Type";
                    xsdWriter.write("        <xs:element name=\"" + fieldName + "\" type=\"" + refClassName
                            + "\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
                }
            }

            xsdWriter.write("      </xs:sequence>\n");
            xsdWriter.write("    </xs:complexType>\n");
            xsdWriter.write("  </xs:element>\n\n");

            // Write type definition for object reference
            xsdWriter.write("  <xs:complexType name=\"" + getSimpleClassName(className) + "Type\">\n");
            xsdWriter.write("    <xs:sequence>\n");
            for (DOSchemaField field : fields.values()) {
                String fieldName = field.destinationName;
                String fieldType = field.type;
                boolean isCollection = field.isCollection;
                if (fieldType == null || fieldType.isEmpty())
                    continue;
                String maxOccurs = isCollection ? "unbounded" : "1";
                if (isCollection) {
                    String childrenType = field.childrenType;
                    if (childrenType == null || childrenType.isEmpty()) {
                        childrenType = fieldType;
                    }
                    if (isPrimitiveType(childrenType)) {
                        String xsdType = getXSDType(childrenType);
                        xsdWriter.write("      <xs:element name=\"" + fieldName + "\" type=\"" + xsdType
                                + "\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>\n");
                    } else {
                        String refClassName = getSimpleClassName(childrenType) + "Type";
                        xsdWriter.write("      <xs:element name=\"" + fieldName + "\" type=\"" + refClassName
                                + "\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>\n");
                    }
                } else if (isPrimitiveType(fieldType)) {
                    String xsdType = getXSDType(fieldType);
                    xsdWriter.write("      <xs:element name=\"" + fieldName + "\" type=\"" + xsdType
                            + "\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
                } else {
                    String refClassName = getSimpleClassName(fieldType) + "Type";
                    xsdWriter.write("      <xs:element name=\"" + fieldName + "\" type=\"" + refClassName
                            + "\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
                }
            }
            xsdWriter.write("    </xs:sequence>\n");
            xsdWriter.write("  </xs:complexType>\n\n");
        }

        private boolean isPrimitiveType(String typeName) {
            return typeName.equals("java.lang.String") ||
                    typeName.equals("java.lang.Integer") ||
                    typeName.equals("int") ||
                    typeName.equals("java.lang.Long") ||
                    typeName.equals("long") ||
                    typeName.equals("java.lang.Boolean") ||
                    typeName.equals("boolean") ||
                    typeName.equals("java.lang.Double") ||
                    typeName.equals("double") ||
                    typeName.equals("java.lang.Float") ||
                    typeName.equals("float") ||
                    typeName.equals("java.util.Date");
        }

        private String getXSDType(String javaType) {
            if (javaType.equals("java.lang.String"))
                return "xs:string";
            if (javaType.equals("java.lang.Integer") || javaType.equals("int"))
                return "xs:int";
            if (javaType.equals("java.lang.Long") || javaType.equals("long"))
                return "xs:long";
            if (javaType.equals("java.lang.Boolean") || javaType.equals("boolean"))
                return "xs:boolean";
            if (javaType.equals("java.lang.Double") || javaType.equals("double"))
                return "xs:double";
            if (javaType.equals("java.lang.Float") || javaType.equals("float"))
                return "xs:float";
            if (javaType.equals("java.util.Date"))
                return "xs:dateTime";
            return "xs:string";
        }

        private String getSimpleClassName(String fullClassName) {
            if (fullClassName == null) {
                return "Unknown";
            }
            int lastDot = fullClassName.lastIndexOf('.');
            return lastDot >= 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
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
            boolean embedContents = (schemaField != null && schemaField.embedContents);

            // Get target type from field's pointsTo or extract from field name
            String expectedType = null;
            if (schemaField != null && schemaField.pointsTo != null) {
                expectedType = schemaField.pointsTo;
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
                    String fullClassName = schemaClass.source;

                    // Only search in classes that match the expected type
                    if (expectedType != null && !fullClassName.equals(expectedType)) {
                        continue; // Skip classes that don't match the expected type
                    }

                    // Use objectIds (not uniqueObjectIds) to search ALL objects
                    long[] objectIds = schemaClass.objectIds;
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
            if (schemaClass.source.equals(className)) {
                return schemaClass;
            }
        }
        return null;
    }

    private DOSchemaField findSchemaField(String className, String fieldName) {
        DOSchemaClass schemaClass = findClassByName(className);
        if (schemaClass == null || schemaClass.fields == null) {
            return null;
        }
        for (DOSchemaField field : schemaClass.fields) {
            if (field.source != null && field.source.equals(fieldName)) {
                return field;
            }
        }
        return null;
    }

    private boolean isDescendantOf(DOSchemaClass schemaClass, String ancestorClassName) {
        if (schemaClass == null || ancestorClassName == null) {
            return false;
        }

        String currentClassName = schemaClass.source;
        if (currentClassName.equals(ancestorClassName)) {
            return true;
        }

        String parentClassName = schemaClass.parentClassName;
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
     * Gets the simple class name from a fully qualified class name.
     * Used for element names in the XML output.
     */
    private String getSimpleClassName(String fullClassName) {
        if (fullClassName == null) {
            return "Unknown";
        }
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot >= 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
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
            xsdWriter.write("        <xs:element name=\"objects\">\n");
            xsdWriter.write("          <xs:complexType>\n");
            xsdWriter.write("            <xs:sequence>\n");
            xsdWriter.write("              <xs:element ref=\"" + schemaClass.destinationName
                    + "\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>\n");
            xsdWriter.write("            </xs:sequence>\n");
            xsdWriter.write("          </xs:complexType>\n");
            xsdWriter.write("        </xs:element>\n");
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

            // Generate class type definitions
            Set<String> processedClasses = new HashSet<>();
            writeClassTypeDefinition(xsdWriter, schemaClass, processedClasses);

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
        String className = schemaClass.source;

        // Avoid infinite recursion
        if (processedClasses.contains(className)) {
            return;
        }
        processedClasses.add(className);

        String destClassName = schemaClass.destinationName;

        // Write element definition
        xsdWriter.write("  <!-- Class: " + className + " (exported as " + destClassName + ") -->\n");
        xsdWriter.write("  <xs:element name=\"" + destClassName + "\">\n");
        xsdWriter.write("    <xs:complexType>\n");
        xsdWriter.write("      <xs:sequence>\n");

        // Write field elements
        for (DOSchemaField field : schemaClass.fields) {
            String fieldName = field.destinationName;
            String fieldType = field.type;
            boolean isCollection = field.isCollection;

            if (fieldType == null || fieldType.isEmpty()) {
                continue;
            }

            String maxOccurs = isCollection ? "unbounded" : "1";

            if (isCollection) {
                String childrenType = field.childrenType;
                if (childrenType == null || childrenType.isEmpty()) {
                    childrenType = fieldType;
                }
                if (isPrimitiveType(childrenType)) {
                    String xsdType = getXSDType(childrenType);
                    xsdWriter.write("        <xs:element name=\"" + fieldName + "\" type=\"" + xsdType
                            + "\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>\n");
                } else {
                    DOSchemaClass refClass = findClassByName(childrenType);
                    String refClassName = refClass != null ? refClass.destinationName
                            : getSimpleClassName(childrenType);
                    xsdWriter.write("        <xs:element name=\"" + fieldName + "\" type=\"" + refClassName
                            + "Type\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>\n");
                }
            } else if (isPrimitiveType(fieldType)) {
                String xsdType = getXSDType(fieldType);
                xsdWriter.write("        <xs:element name=\"" + fieldName + "\" type=\"" + xsdType
                        + "\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
            } else {
                DOSchemaClass refClass = findClassByName(fieldType);
                String refClassName = refClass != null ? refClass.destinationName : getSimpleClassName(fieldType);
                xsdWriter.write("        <xs:element name=\"" + fieldName + "\" type=\"" + refClassName
                        + "Type\" minOccurs=\"0\" maxOccurs=\"1\"/>\n");
            }
        }

        xsdWriter.write("      </xs:sequence>\n");
        xsdWriter.write("    </xs:complexType>\n");
        xsdWriter.write("  </xs:element>\n\n");

        // Recursively process referenced classes
        for (DOSchemaField field : schemaClass.fields) {
            if (field.type != null && !isPrimitiveType(field.type)) {
                DOSchemaClass referencedClass = findClassByName(field.type);
                if (referencedClass != null) {
                    writeClassTypeDefinition(xsdWriter, referencedClass, processedClasses);
                }
            }
        }
    }

    /**
     * Maps Java types to XSD types.
     */
    private String getXSDType(String javaType) {
        if (javaType.equals("java.lang.String"))
            return "xs:string";
        if (javaType.equals("java.lang.Integer") || javaType.equals("int"))
            return "xs:int";
        if (javaType.equals("java.lang.Long") || javaType.equals("long"))
            return "xs:long";
        if (javaType.equals("java.lang.Boolean") || javaType.equals("boolean"))
            return "xs:boolean";
        if (javaType.equals("java.lang.Double") || javaType.equals("double"))
            return "xs:double";
        if (javaType.equals("java.lang.Float") || javaType.equals("float"))
            return "xs:float";
        if (javaType.equals("java.util.Date"))
            return "xs:dateTime";
        return "xs:string"; // Default to string for unknown types
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
