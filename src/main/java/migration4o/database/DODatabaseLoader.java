package migration4o.database;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;
import com.db4o.ext.SystemInfo;

import migration4o.database.processors.DOClassConverter;
import migration4o.database.processors.DOFieldConverter;
import migration4o.database.processors.DOStoredFieldDeduplicationProcessor;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaField;
import migration4o.util.CollectionTypeUtil;
import migration4o.util.DatabaseUtil;

/**
 * Loads a DODatabase from a DB4O container, populating classes, fields, and linking to corresponding schema objects when available.
 */
public class DODatabaseLoader {

    /**
     * Loads a DODatabase from the given container, linking to the provided schema.
     *
     * @param container the DB4O container to read from
     * @param schema the reference schema to link corresponding objects (may be null)
     * @return a fully populated DODatabase
     */
    public DODatabase load(ExtObjectContainer container, DOSchema schema) {
        DODatabase database = new DODatabase();
        database.schema = schema;

        if (container == null) {
            return database;
        }

        loadAttributes(database, container);
        loadClasses(database, container, schema);

        return database;
    }

    private void loadAttributes(DODatabase database, ExtObjectContainer container) {
        database.attributes.version = container.version();
        database.attributes.classCount = container.storedClasses().length;

        SystemInfo systemInfo = container.ext().systemInfo();
        database.attributes.totalSize = systemInfo.totalSize();
        database.attributes.freespaceSize = systemInfo.freespaceSize();
        database.attributes.freespaceEntryCount = systemInfo.freespaceEntryCount();

        database.attributes.creationTime = container.identity().getCreationTime();
        database.attributes.signature = container.identity().getSignature();
    }

    private void loadClasses(DODatabase database, ExtObjectContainer container, DOSchema schema) {
        StoredClass[] storedClasses = DatabaseUtil.getStoredClassesSafely(container);
        Map<String, StoredClass> storedClassMap = DOClassConverter.createStoredClassMap(storedClasses);

        DODatabaseClass[] dbClasses = new DODatabaseClass[storedClasses.length];
        for (int i = 0; i < storedClasses.length; i++) {
            dbClasses[i] = loadClass(database, storedClasses[i], storedClassMap, schema);
        }
        database.classes = dbClasses;
    }

    private DODatabaseClass loadClass(DODatabase database, StoredClass storedClass, Map<String, StoredClass> storedClassMap, DOSchema schema) {
        DODatabaseClass dbClass = new DODatabaseClass(database);

        // Attributes from StoredClass
        dbClass.attributes.source = storedClass.getName();
        dbClass.attributes.instanceCount = storedClass.instanceCount();

        StoredClass parentStoredClass = storedClass.getParentStoredClass();
        if (parentStoredClass != null) {
            dbClass.attributes.parentClassName = parentStoredClass.getName();
        }

        // Object IDs
        long[] objectIds = storedClass.getIDs();
        dbClass.objects.objectIds = objectIds != null ? objectIds : new long[0];

        // Fields
        DODatabaseField[] dbFields = loadFields(database, dbClass, storedClass, storedClassMap);
        dbClass.setFields(dbFields);

        // Link to schema
        if (schema != null) {
            dbClass.schemaClass = schema.findClassByName(storedClass.getName());
        }

        return dbClass;
    }

    private DODatabaseField[] loadFields(DODatabase database, DODatabaseClass dbClass, StoredClass storedClass, Map<String, StoredClass> storedClassMap) {
        StoredField[] storedFields;
        try {
            storedFields = storedClass.getStoredFields();
        } catch (Exception e) {
            return new DODatabaseField[0];
        }

        Map<String, StoredField> deduped = DOStoredFieldDeduplicationProcessor.deduplicateByNamePreferArray(storedFields);

        List<DODatabaseField> fields = new ArrayList<>();
        for (StoredField sf : deduped.values()) {
            try {
                fields.add(loadField(database, dbClass, sf, storedClassMap));
            } catch (Exception e) {
                // Skip fields that fail to convert
            }
        }
        return fields.toArray(new DODatabaseField[0]);
    }

    private DODatabaseField loadField(DODatabase database, DODatabaseClass dbClass, StoredField storedField, Map<String, StoredClass> storedClassMap) {
        DODatabaseField field = new DODatabaseField(database, dbClass);

        field.attributes.source = storedField.getName();
        field.attributes.type = DOFieldConverter.determineFieldType(storedField.getStoredType().getName(), storedField.isArray());
        field.attributes.isArray = storedField.isArray();
        field.attributes.isCollection = CollectionTypeUtil.isCollectionType(storedField.getStoredType().getName());
        field.attributes.childrenType = DOFieldConverter.determineChildrenType(storedField.getStoredType().getName(), field.attributes.isCollection, storedClassMap);

        // Link to schema field
        if (dbClass.schemaClass != null && dbClass.schemaClass.fields != null) {
            for (DOSchemaField schemaField : dbClass.schemaClass.fields) {
                if (storedField.getName().equals(schemaField.attributes.source)) {
                    field.schemaField = schemaField;
                    break;
                }
            }
        }

        return field;
    }

}
