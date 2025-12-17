package dataobjects.impl.database;

import dataobjects.api.models.database.DODatabase;
import dataobjects.api.models.database.DODatabaseClass;
import dataobjects.api.database.DODatabaseEncoding;
import dataobjects.api.database.DODatabaseReader;
import dataobjects.api.models.database.DODatabaseObject;
import dataobjects.api.models.schema.DOSchema;
import dataobjects.api.resolution.DOObjectResolver;
import dataobjects.api.resolution.DOInheritanceResolver;
import dataobjects.api.resolution.DOModuleReachabilityResolver;
import dataobjects.impl.models.database.DODatabaseImpl;
import dataobjects.impl.models.database.DODatabaseClassImpl;
import dataobjects.impl.resolution.DOObjectResolverImpl;
import dataobjects.impl.resolution.DOInheritanceResolverImpl;
import dataobjects.impl.resolution.DOModuleReachabilityResolverImpl;
import dataobjects.util.DatabaseUtil;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Implementation for reading database information and statistics.
 * Provides both basic database reading and full object resolution.
 */
public class DODatabaseReaderImpl implements DODatabaseReader {

    // private final DOObjectResolver objectResolver;
    private final DOInheritanceResolver inheritanceResolver;
    // private final DOModuleReachabilityResolver reachabilityResolver;

    public DODatabaseReaderImpl() {
        // this.objectResolver = new DOObjectResolverImpl();
        this.inheritanceResolver = new DOInheritanceResolverImpl();
        // this.reachabilityResolver = new DOModuleReachabilityResolverImpl();
    }

    @Override
    public DODatabase readDatabaseMeta(ExtObjectContainer container, DODatabaseEncoding encoding,
            String databaseSize, DOSchema schema) {
        try {
            StoredClass[] storedClasses = DatabaseUtil.getStoredClassesSafely(container);
            int totalClasses = storedClasses.length;
            int totalObjects = DatabaseUtil.countTotalObjects(storedClasses);

            DODatabaseClass[] databaseClasses = createDatabaseClasses(storedClasses, schema);
            System.out.println("Created " + databaseClasses.length + " database classes");

            return new DODatabaseImpl(container, encoding, totalClasses, totalObjects, databaseSize, databaseClasses);

        } catch (Exception e) {
            System.out.println("Error reading database information: " + e.getMessage());
            e.printStackTrace();
            return DatabaseUtil.createEmptyDatabase(container, encoding, databaseSize);
        }
    }

    @Override
    public DODatabase readDatabase(ExtObjectContainer container, DODatabaseEncoding encoding,
            String databaseSize, DOSchema schema) {

        // NOTE: Full object resolution now happens in DOEngineImpl after database
        // initialization
        // This method is kept for backward compatibility but just returns basic
        // database info
        System.out.println("Note: Full object resolution now happens in DOEngineImpl");

        DODatabase basicDatabase = readDatabaseMeta(container, encoding, databaseSize, schema);

        // Resolve inheritance relationships (still needed here)
        inheritanceResolver.resolveInheritance(basicDatabase, schema);

        return basicDatabase;
    }

    private DODatabaseClass[] createDatabaseClasses(StoredClass[] storedClasses, DOSchema schema) {
        List<DODatabaseClass> classList = new ArrayList<>();

        for (StoredClass storedClass : storedClasses) {
            try {
                DODatabaseClass dbClass = DatabaseUtil.createDatabaseClass(storedClass, schema);
                classList.add(dbClass);
            } catch (Exception e) {
                System.out.println("Warning: Could not create database class for: " + storedClass.getName() + " - "
                        + e.getMessage());
            }
        }

        return classList.toArray(new DODatabaseClass[classList.size()]);
    }

    // private DODatabaseClass[] enhanceClassesWithResolvedObjects(DODatabaseClass[]
    // basicClasses,
    // DODatabaseObject[] resolvedObjects) {
    // // Group resolved objects by class name for efficient lookup
    // Map<String, List<DODatabaseObject>> classToObjects = new HashMap<>();

    // for (DODatabaseObject obj : resolvedObjects) {
    // String className = DatabaseUtil.getClassNameFromObject(obj);
    // if (className != null) {
    // classToObjects.computeIfAbsent(className, k -> new ArrayList<>()).add(obj);
    // }
    // }

    // List<DODatabaseClass> result = new ArrayList<>();

    // for (DODatabaseClass basicClass : basicClasses) {
    // if (basicClass instanceof DODatabaseClassImpl) {
    // DODatabaseClassImpl impl = (DODatabaseClassImpl) basicClass;

    // // Find matching resolved objects using utility method
    // List<DODatabaseObject> classObjects =
    // DatabaseUtil.findResolvedObjectsForClass(basicClass,
    // classToObjects);

    // if (!classObjects.isEmpty()) {
    // impl.setResolvedObjects(classObjects.toArray(new DODatabaseObject[0]));
    // } else {
    // impl.setResolvedObjects(new DODatabaseObject[0]);
    // if (basicClass.getTotalObjectCount() > 0) {
    // System.out.println(
    // "DEBUG: No resolved objects found for database class: " +
    // basicClass.getAbsoluteName()
    // + " (has " + basicClass.getTotalObjectCount() + " objects)");
    // }
    // }

    // result.add(impl);
    // } else {
    // result.add(basicClass);
    // }
    // }

    // return result.toArray(new DODatabaseClass[0]);
    // }

}
