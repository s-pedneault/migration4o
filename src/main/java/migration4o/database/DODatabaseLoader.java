package migration4o.database;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;

import migration4o.database.processors.DOClassConverter;
import migration4o.database.processors.DOFieldConverter;
import migration4o.database.processors.DOStoredFieldDeduplicationProcessor;
import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaField;
import migration4o.util.CollectionTypeUtil;

/**
 * Loads a DODatabase from a DB4O container, populating classes, fields, and linking to corresponding schema objects when available.
 */
public class DODatabaseLoader {

    /**
     * Populates a delegate's classes and attributes from its DB4O container,
     * linking to the provided schema.
     *
     * @param delegate the delegate wrapping the DB4O container
     * @param database the parent DODatabase (shared across delegates)
     * @param schema   the reference schema to link corresponding objects (may be null)
     */
    public void load(DODatabaseDelegate delegate, DODatabase database, DOSchema schema) {
        if (delegate == null) {
            return;
        }

        loadAttributes(delegate);
        loadClasses(delegate, database, schema);
    }

    private void loadAttributes(DODatabaseDelegate delegate) {
        delegate.attributes.version = delegate.version();
        delegate.attributes.classCount = delegate.storedClasses().length;

        var systemInfo = delegate.systemInfo();
        delegate.attributes.totalSize = systemInfo.totalSize();
        delegate.attributes.freespaceSize = systemInfo.freespaceSize();
        delegate.attributes.freespaceEntryCount = systemInfo.freespaceEntryCount();

        delegate.attributes.creationTime = delegate.creationTime();
        delegate.attributes.signature = delegate.signature();
    }

    private void loadClasses(DODatabaseDelegate delegate, DODatabase database, DOSchema schema) {
        StoredClass[] storedClasses = getStoredClassesSafely(delegate);
        Map<String, StoredClass> storedClassMap = DOClassConverter.createStoredClassMap(storedClasses);

        DODatabaseClass[] dbClasses = new DODatabaseClass[storedClasses.length];
        for (int i = 0; i < storedClasses.length; i++) {
            dbClasses[i] = loadClass(delegate, database, storedClasses[i], storedClassMap, schema);
        }
        delegate.classes = dbClasses;
    }

    private static StoredClass[] getStoredClassesSafely(DODatabaseDelegate delegate) {
        try {
            return delegate.storedClasses();
        } catch (Exception e) {
            System.out.println("Warning: Could not enumerate stored classes: " + e.getMessage());
            return new StoredClass[0];
        }
    }

    private DODatabaseClass loadClass(DODatabaseDelegate delegate, DODatabase database, StoredClass storedClass, Map<String, StoredClass> storedClassMap, DOSchema schema) {
        DODatabaseClass dbClass = new DODatabaseClass(database);
        dbClass.delegate = delegate;

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

        // Link to schema first (must happen before field loading so fields can match)
        if (schema != null) {
            dbClass.schemaClass = schema.findClassByName(storedClass.getName());
        }

        // Fields
        DODatabaseField[] dbFields = loadFields(database, dbClass, storedClass, storedClassMap);
        dbClass.setFields(dbFields);

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
