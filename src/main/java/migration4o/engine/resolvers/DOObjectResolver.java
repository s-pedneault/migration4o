package migration4o.engine.resolvers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;
import com.db4o.reflect.generic.GenericObject;

import migration4o.engine.DOEngine;
import migration4o.models.database.DODatabaseField;
import migration4o.models.database.DOCollectionReference;
import migration4o.models.database.DODatabase;
import migration4o.models.database.DODatabaseClass;
import migration4o.models.database.DODatabaseObject;
import migration4o.models.database.DOObjectReference;
import migration4o.models.database.DOReferenceType;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchema;
import migration4o.util.CollectionTypeUtil;
import migration4o.util.ObjectResolverUtil;

/**
 * Object resolver implementation following the database processing recipe.
 * Processes all leaf class objects recursively and tracks reachability.
 */
public class DOObjectResolver {

    private final DOGenericObjectResolver genericObjectResolver = new DOGenericObjectResolver();

    public DODatabaseObject[] resolveAllObjects(ExtObjectContainer container,
            DODatabase database,
            DOSchema schema,
            DOEngine engine) {
        System.out.println("\n=== Starting Object Resolution (Recipe Algorithm) ===");

        try {
            // Step 1: Get the reachability tracker from the engine
            DOObjectReachabilityTracker tracker = engine.getReachabilityTracker();

            // Create a map from db4o stored class names to DODatabaseClass objects
            Map<String, DODatabaseClass> classNameMap = new HashMap<>();
            for (DODatabaseClass dbClass : database.getClasses()) {
                classNameMap.put(dbClass.getAbsoluteName(), dbClass);
            }

            // Get all object IDs by stored class name from db4o
            Map<String, Set<Long>> allObjectIdsByClassName = ObjectResolverUtil.getAllClassObjectIds(container);

            // Convert to map keyed by DODatabaseClass
            Map<DODatabaseClass, Set<Long>> allObjectIdsByClass = new HashMap<>();
            for (Map.Entry<String, Set<Long>> entry : allObjectIdsByClassName.entrySet()) {
                String className = entry.getKey();
                DODatabaseClass dbClass = classNameMap.get(className);
                if (dbClass != null) {
                    allObjectIdsByClass.put(dbClass, entry.getValue());
                }
            }

            tracker.initializeFromDatabase(allObjectIdsByClass);

            System.out.println("Initialized tracker with " + tracker.getTotalObjectCount() + " total objects");

            // Step 2: Collect ALL unique object IDs from ALL classes with their most
            // specific type
            // This ensures we process objects stored in non-leaf classes as well
            Map<Long, DODatabaseClass> uniqueObjectIds = collectAllUniqueObjectIds(database, allObjectIdsByClass);
            System.out.println("Found " + uniqueObjectIds.size() + " unique object IDs across all database classes");

            // Step 3: Process each unique object ID exactly once using its most specific
            // class
            // GLOBAL list to collect ALL resolved objects (including referenced ones)
            List<DODatabaseObject> allResolvedObjects = new ArrayList<>();
            Set<Long> processedObjectIds = new HashSet<>();
            int processedCount = 0;

            for (Map.Entry<Long, DODatabaseClass> entry : uniqueObjectIds.entrySet()) {
                Long objectId = entry.getKey();
                DODatabaseClass mostSpecificClass = entry.getValue();

                if (processedObjectIds.contains(objectId)) {
                    continue; // Already processed
                }

                // Perform recursive OBJECT ID PROCESSING as per recipe
                // Pass uniqueObjectIds map for fast lookup of referenced objects
                DODatabaseObject resolvedObject = processObjectIdRecursive(
                        objectId,
                        mostSpecificClass,
                        container,
                        schema,
                        database,
                        tracker,
                        processedObjectIds,
                        allResolvedObjects,
                        uniqueObjectIds); // Pass the index for fast lookups

                if (resolvedObject != null) {
                    allResolvedObjects.add(resolvedObject);
                    processedCount++;

                    if (processedCount % 10000 == 0) {
                        System.out.println("  Progress: " + processedCount + " objects processed...");
                    }
                }
            }

            System.out.println("Processed " + processedCount + " unique root objects");
            System.out.println("Total resolved objects (including referenced): " + allResolvedObjects.size());
            System.out.println("DEBUG: processedObjectIds size: " + processedObjectIds.size());

            // Step 4: Mark reachability in resolved objects
            markReachabilityInObjects(allResolvedObjects, tracker);

            // Step 5: Populate database classes with their resolved objects
            populateDatabaseClassesWithResolvedObjects(database, allResolvedObjects);

            // Diagnostic: Analyze unreached objects - REMOVED (string-based)
            // printReachabilityDiagnostics(tracker, allObjectIdsByClassName);

            System.out.println("\n=== Object Resolution Complete ===");
            System.out.println("Total objects in database: " + tracker.getTotalObjectCount());
            System.out.println("Reached objects: " + tracker.getReachedObjectCount());
            System.out.println("Unreached objects: " + tracker.getUnreachedObjectCount());
            System.out.println("=====================================\n");

            return allResolvedObjects.toArray(new DODatabaseObject[0]);

        } catch (Exception e) {
            System.err.println("ERROR: Exception in resolveAllObjects: " + e.getMessage());
            e.printStackTrace();
            return new DODatabaseObject[0];
        }
    }

    /**
     * Collects all unique object IDs from all database classes.
     * Returns a map of objectId -> most specific class where it was found.
     * 
     * This implements the recipe's requirement to "process each unique object ID
     * using its ACTUAL runtime class (most specific type)".
     * 
     * The algorithm:
     * 1. Sort classes by specificity (leaf classes first, then by inheritance
     * depth)
     * 2. Collect object IDs, keeping only the first (most specific) class for each
     * ID
     * 3. This ensures objects stored in non-leaf classes are not missed
     */
    private Map<Long, DODatabaseClass> collectAllUniqueObjectIds(
            DODatabase database,
            Map<DODatabaseClass, Set<Long>> allObjectIdsByClass) {

        Map<Long, DODatabaseClass> uniqueIds = new HashMap<>();

        // Sort classes by specificity (most specific first)
        DODatabaseClass[] allClasses = database.getClasses();
        List<DODatabaseClass> sortedClasses = new ArrayList<>();
        for (DODatabaseClass dbClass : allClasses) {
            sortedClasses.add(dbClass);
        }
        sortedClasses.sort((a, b) -> {
            // Leaf classes first (no subclasses)
            boolean aIsLeaf = a.isLeafClass();
            boolean bIsLeaf = b.isLeafClass();
            if (aIsLeaf && !bIsLeaf)
                return -1;
            if (!aIsLeaf && bIsLeaf)
                return 1;

            // Then by inheritance depth (deeper = more specific)
            int depthA = a.getInheritanceChain() != null ? a.getInheritanceChain().size() : 0;
            int depthB = b.getInheritanceChain() != null ? b.getInheritanceChain().size() : 0;
            return Integer.compare(depthB, depthA);
        });

        // Collect object IDs, preferring most specific class
        System.out.println("Collecting unique object IDs by most specific class:");
        for (DODatabaseClass dbClass : sortedClasses) {
            // Use DODatabaseClass object as key instead of class name string
            Set<Long> classObjectIds = allObjectIdsByClass.get(dbClass);

            if (classObjectIds == null || classObjectIds.isEmpty()) {
                continue;
            }

            int newObjects = 0;
            for (Long objectId : classObjectIds) {
                // Only add if not already present (first occurrence = most specific)
                if (!uniqueIds.containsKey(objectId)) {
                    uniqueIds.put(objectId, dbClass);
                    newObjects++;
                }
            }

            if (newObjects > 0) {
                System.out.println("  " + dbClass.getAbsoluteName() + ": " + newObjects + " unique objects" +
                        (dbClass.isLeafClass() ? " (leaf)" : " (non-leaf)"));
            }
        }

        return uniqueIds;
    }

    /**
     * Finds an object in the objectIdToClassIndex, searching through the entire
     * inheritance hierarchy if necessary.
     * 
     * When a field is typed as IDRapport (which refers to Rapport objects), the
     * actual
     * object could be stored as a Rapport, SousRapport, RapportOfficier, or any
     * other
     * class in the Rapport inheritance chain.
     * 
     * @param objectId             The object ID to search for
     * @param declaredClass        The declared type from the field (may be null)
     * @param objectIdToClassIndex The pre-built index of all objects
     * @param database             The database for looking up inheritance
     *                             relationships
     * @return The most specific class for the object, or null if not found
     */
    private DODatabaseClass findObjectInInheritanceHierarchy(
            Long objectId,
            DODatabaseClass declaredClass,
            Map<Long, DODatabaseClass> objectIdToClassIndex,
            DODatabase database) {

        if (objectId == null) {
            return null;
        }

        // First, try direct lookup in the index
        DODatabaseClass foundClass = objectIdToClassIndex.get(objectId);
        if (foundClass != null) {
            return foundClass;
        }

        // If not found and we have a declared class, search through its inheritance
        // hierarchy
        // The object might be stored in a subclass or parent class of the declared type
        if (declaredClass != null) {
            // Collect all classes in the inheritance hierarchy (parents and children)
            Set<DODatabaseClass> hierarchyClasses = new HashSet<>();
            collectInheritanceHierarchy(declaredClass, database, hierarchyClasses);

            // Check if the object exists in any of these classes
            for (DODatabaseClass hierarchyClass : hierarchyClasses) {
                // Note: We already checked objectIdToClassIndex above, which contains ALL
                // objects
                // So if we didn't find it there, it truly doesn't exist
                // This loop is here for future extensibility
            }
        }

        // Object not found in the database
        return null;
    }

    /**
     * Collects all classes in the inheritance hierarchy (both ancestors and
     * descendants)
     * of the given class.
     */
    private void collectInheritanceHierarchy(
            DODatabaseClass startClass,
            DODatabase database,
            Set<DODatabaseClass> result) {

        if (startClass == null || result.contains(startClass)) {
            return;
        }

        result.add(startClass);

        // Add all parent classes (walk up the inheritance chain)
        DODatabaseClass parent = startClass;
        while (parent != null && parent.getSuperClassAbsoluteName() != null &&
                !parent.getSuperClassAbsoluteName().isEmpty()) {
            // Find the parent class in the database
            String parentName = parent.getSuperClassAbsoluteName();
            DODatabaseClass parentDbClass = findClassByName(parentName, database);
            if (parentDbClass != null && !result.contains(parentDbClass)) {
                result.add(parentDbClass);
                parent = parentDbClass;
            } else {
                break;
            }
        }

        // Add all subclasses (walk down the inheritance tree)
        for (DODatabaseClass dbClass : database.getClasses()) {
            if (isSubclassOf(dbClass, startClass) && !result.contains(dbClass)) {
                result.add(dbClass);
            }
        }
    }

    /**
     * Checks if testClass is a subclass of potentialParent
     */
    private boolean isSubclassOf(DODatabaseClass testClass, DODatabaseClass potentialParent) {
        if (testClass == null || potentialParent == null) {
            return false;
        }

        DODatabaseClass current = testClass;
        while (current != null && current.getSuperClassAbsoluteName() != null &&
                !current.getSuperClassAbsoluteName().isEmpty()) {
            if (current.getSuperClassAbsoluteName().equals(potentialParent.getAbsoluteName())) {
                return true;
            }
            // Move to the parent
            String parentName = current.getSuperClassAbsoluteName();
            current = findClassByName(parentName, null); // Will use cached lookup
            if (current == null) {
                break;
            }
        }
        return false;
    }

    /**
     * Finds a class by its absolute name in the database
     */
    private DODatabaseClass findClassByName(String className, DODatabase database) {
        if (className == null || database == null) {
            return null;
        }

        for (DODatabaseClass dbClass : database.getClasses()) {
            if (dbClass.getAbsoluteName().equals(className)) {
                return dbClass;
            }
        }
        return null;
    }

    /**
     * Performs OBJECT ID PROCESSING as described in the recipe.
     * This is the recursive core of the algorithm.
     * 
     * Recipe steps:
     * 1. If object is not activated: deep-activate the object
     * 2. Mark the object ID as reached for all classes in its inheritance chain
     * 3. For each field: do FIELD PROCESSING
     */
    private DODatabaseObject processObjectIdRecursive(
            Long objectId,
            DODatabaseClass mostSpecificClass,
            ExtObjectContainer container,
            DOSchema schema,
            DODatabase database,
            DOObjectReachabilityTracker tracker,
            Set<Long> processedObjectIds,
            List<DODatabaseObject> allResolvedObjects,
            Map<Long, DODatabaseClass> objectIdToClassIndex) {

        // Avoid infinite recursion on circular references
        if (processedObjectIds.contains(objectId)) {
            return null;
        }

        try {
            // CRITICAL FIX: Always prefer the index lookup over the declared class
            // The declared class might be a parent type (e.g., Rapport) while the actual
            // object is stored as a subclass (e.g., SousRapport or RapportOfficier)
            // The index contains the MOST SPECIFIC class for each object
            DODatabaseClass actualClass = objectIdToClassIndex.get(objectId);
            if (actualClass != null) {
                // Use the actual class from the index
                mostSpecificClass = actualClass;
            } else if (mostSpecificClass == null) {
                // Object truly doesn't exist in the database
                return null;
            }
            // If actualClass is null but mostSpecificClass is provided, we'll try with the
            // provided class
            // (this handles edge cases where objects might exist outside our index)

            // Step 1: Load and activate the object (deep activation)
            Object obj = container.getByID(objectId);
            if (obj == null) {
                return null;
            }

            ObjectResolverUtil.activateObject(container, obj, objectId);

            // Step 2: Mark as reached using the inheritance chain from mostSpecificClass
            markObjectAsReachedWithInheritanceChain(objectId, mostSpecificClass, tracker);
            processedObjectIds.add(objectId);

            // Step 3: Resolve the object to get its DODatabaseObject representation
            DODatabaseObject resolvedObject = resolveAndBuildObject(
                    obj,
                    objectId,
                    mostSpecificClass,
                    container,
                    schema,
                    database);

            if (resolvedObject == null) {
                return null;
            }

            // Step 4: Process each field (FIELD PROCESSING)
            processAllFieldsRecursive(
                    resolvedObject,
                    obj,
                    container,
                    schema,
                    database,
                    tracker,
                    processedObjectIds,
                    allResolvedObjects,
                    objectIdToClassIndex);

            return resolvedObject;

        } catch (Exception e) {
            System.err.println("ERROR: Failed to process object " + objectId + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Marks an object as reached for all classes in its inheritance chain.
     * Uses the DODatabaseClass hierarchy directly (no string-based lookups).
     * 
     * @param objectId          The object ID to mark as reached
     * @param mostSpecificClass The most specific database class for this object
     * @param tracker           The reachability tracker
     */
    private void markObjectAsReachedWithInheritanceChain(
            Long objectId,
            DODatabaseClass mostSpecificClass,
            DOObjectReachabilityTracker tracker) {

        // Build the inheritance chain from most specific to most general (Object)
        List<DODatabaseClass> classesInChain = new ArrayList<>();
        DODatabaseClass currentClass = mostSpecificClass;

        while (currentClass != null) {
            classesInChain.add(currentClass);
            currentClass = currentClass.getParentClass();
        }

        // Convert to array and mark as reached
        DODatabaseClass[] classes = classesInChain.toArray(new DODatabaseClass[0]);
        tracker.markObjectAsReached(objectId, classes);

        // DEBUG: Log DossPrev objects
        if (mostSpecificClass.getAbsoluteName().contains("DossPrev")
                && mostSpecificClass.getAbsoluteName().endsWith("DossPrev")) {
            // System.out.println("DEBUG: Marked DossPrev object " + objectId + " as reached
            // (class: "
            // + mostSpecificClass.getAbsoluteName() + ")");
        }
    }

    /**
     * Gets the inheritance chain class names for an object, from most specific to
     * least specific.
     * Each object is stored in an "exploded" state in db4o, meaning it exists in
     * multiple class tables.
     * 
     * @deprecated Use markObjectAsReachedWithInheritanceChain instead - this method
     *             uses string-based lookups
     */
    @Deprecated
    private String[] getInheritanceChainClassNames(Object obj, ExtObjectContainer container, DODatabase database) {
        if (obj == null) {
            return new String[0];
        }

        List<String> chain = new ArrayList<>();

        // Get the object's actual class name
        String className = obj.getClass().getName();
        chain.add(className);

        // Find the corresponding database class
        DODatabaseClass dbClass = findDatabaseClass(className, database);
        if (dbClass != null) {
            // Walk up the inheritance chain
            DODatabaseClass current = dbClass.getParentClass();
            while (current != null) {
                chain.add(current.getAbsoluteName());
                current = current.getParentClass();
            }
        }

        return chain.toArray(new String[0]);
    }

    /**
     * Finds a database class by name (handles both simple and fully qualified
     * names).
     */
    private DODatabaseClass findDatabaseClass(String className, DODatabase database) {
        for (DODatabaseClass dbClass : database.getClasses()) {
            if (dbClass.getAbsoluteName().equals(className) ||
                    dbClass.getShortName().equals(className)) {
                return dbClass;
            }
        }
        return null;
    }

    /**
     * Resolves and builds a DODatabaseObject from a loaded object.
     */
    private DODatabaseObject resolveAndBuildObject(
            Object obj,
            Long objectId,
            DODatabaseClass mostSpecificClass,
            ExtObjectContainer container,
            DOSchema schema,
            DODatabase database) {
        try {
            // Resolve GenericObjects to concrete representations
            Object resolvedObj = resolveGenericObjectIfNeeded(container, obj, objectId, schema, database);

            // Use the mostSpecificClass that was already determined for us
            DODatabaseClass dbClass = mostSpecificClass;

            if (dbClass == null) {
                return null;
            }

            // Use dbClass directly as the classDefinition to maintain object identity
            DODatabaseClass classDefinition = dbClass;

            // Build complete inheritance chain for this object
            DODatabaseClass[] allClasses = ObjectResolverUtil.buildInheritanceChain(classDefinition, dbClass, schema,
                    database);

            // Extract direct object references (pass database for ID-type handling)
            DOObjectReference[] directRefs = extractDirectReferences(container, resolvedObj, objectId, classDefinition,
                    database);

            // Extract collection references
            DOCollectionReference[] collectionRefs = extractCollectionReferences(container, resolvedObj, objectId,
                    classDefinition);

            // Create the resolved object with complete inheritance chain
            return new DODatabaseObject(
                    objectId,
                    classDefinition,
                    allClasses,
                    directRefs,
                    collectionRefs);

        } catch (Exception e) {
            System.err.println("ERROR: Failed to resolve object " + objectId + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Resolve GenericObjects to concrete collections when possible.
     * For GenericObject collections, converts to concrete collection types for
     * iteration.
     * Class identity is preserved in DODatabaseObject.mostSpecificClass before
     * conversion.
     */
    private Object resolveGenericObjectIfNeeded(ExtObjectContainer container, Object obj, Long objectId,
            DOSchema schema, DODatabase database) {
        if (!(obj instanceof GenericObject)) {
            return obj;
        }

        GenericObject genericObj = (GenericObject) obj;

        // Use the existing GenericObjectResolver to get class information
        DODatabaseClass resolvedClass = null;
        DOSchemaClass schemaClass = genericObjectResolver.resolveClass(genericObj, schema);
        if (schemaClass != null) {
            resolvedClass = schemaClass.getDatabaseClass();
        }
        if (resolvedClass == null) {
            resolvedClass = genericObjectResolver.resolveClass(genericObj, database);
        }

        // Convert any GenericObject collection to concrete collection for iteration
        // Class identity is already preserved in mostSpecificClass before this
        // conversion
        String genericClassName = ObjectResolverUtil.getGenericObjectClassName(genericObj);
        if (CollectionTypeUtil.isCollectionType(genericClassName)) {
            Object concreteCollection = convertGenericObjectToCollection(container, genericObj, objectId);
            if (concreteCollection != null) {
                return concreteCollection;
            }
        }

        // For non-collection GenericObjects, keep as is
        return obj;
    }

    /**
     * Convert GenericObject collections to concrete collection instances.
     * Extracts internal storage and creates appropriate collection type (ArrayList,
     * LinkedList, Vector).
     * This allows universal collection extraction to work with instanceof checks.
     */
    private Object convertGenericObjectToCollection(ExtObjectContainer container, GenericObject genericObj,
            Long objectId) {
        try {
            // Get the original class name from the GenericObject
            String className = ObjectResolverUtil.getGenericObjectClassName(genericObj);

            // Use db4o APIs to access the object's data
            StoredClass storedClass = container.ext().storedClass(genericObj);
            if (storedClass == null) {
                return null;
            }

            // Create appropriate collection based on original class name
            Collection<Object> resultCollection;
            if (className.contains("ArrayList")) {
                resultCollection = new ArrayList<>();
            } else if (className.contains("LinkedList")) {
                resultCollection = new LinkedList<>();
            } else {
                // Default to Vector for Vector and unknown types
                resultCollection = new Vector<>();
            }

            // Try to find collection element storage fields
            StoredField[] fields = storedClass.getStoredFields();
            for (StoredField field : fields) {
                Object fieldValue = field.get(genericObj);
                if (fieldValue instanceof Object[]) {
                    Object[] elements = (Object[]) fieldValue;
                    for (Object element : elements) {
                        if (element != null) {
                            resultCollection.add(element);
                        }
                    }
                    break;
                } else if (fieldValue instanceof Collection) {
                    @SuppressWarnings("unchecked")
                    Collection<Object> collection = (Collection<Object>) fieldValue;
                    resultCollection.addAll(collection);
                    break;
                }
            }

            return resultCollection;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Processes all fields of an object (FIELD PROCESSING from recipe).
     * For each field, recursively processes references and collections.
     */
    private void processAllFieldsRecursive(
            DODatabaseObject resolvedObject,
            Object obj,
            ExtObjectContainer container,
            DOSchema schema,
            DODatabase database,
            DOObjectReachabilityTracker tracker,
            Set<Long> processedObjectIds,
            List<DODatabaseObject> allResolvedObjects,
            Map<Long, DODatabaseClass> objectIdToClassIndex) {

        try {
            // Process direct references
            for (DOObjectReference ref : resolvedObject.getReferences()) {
                Long referencedId = ref.getTargetObjectId();
                if (referencedId != null && !processedObjectIds.contains(referencedId)) {
                    // Get the database class from the field type (may be null for synthetic refs)
                    DODatabaseClass fieldTypeClass = ref.getField() != null ? ref.getField().getTypeClass() : null;
                    DODatabaseClass referencedClass = (fieldTypeClass instanceof DODatabaseClass)
                            ? (DODatabaseClass) fieldTypeClass
                            : null;

                    // OPTIMIZED FIX: Always try to process the reference, even if referencedClass
                    // is null
                    // Use the pre-built index for O(1) lookup instead of expensive database scans
                    DODatabaseObject referencedObject = processObjectIdRecursive(
                            referencedId,
                            referencedClass, // May be null - will be looked up from index
                            container,
                            schema,
                            database,
                            tracker,
                            processedObjectIds,
                            allResolvedObjects,
                            objectIdToClassIndex);

                    // Add to global list if resolved successfully
                    if (referencedObject != null) {
                        allResolvedObjects.add(referencedObject);

                        // CRITICAL: If this is an ID-type field, follow the mID reference!
                        // As per database-processing.md: "Do OBJECT ID PROCESSING with the mID field
                        // value"
                        if (referencedClass != null && isIDTypeClass(referencedClass)) {
                            processIDTypeObject(referencedObject, referencedId, container, schema, database,
                                    tracker, processedObjectIds, allResolvedObjects, objectIdToClassIndex);
                        }
                    }
                }
            }

            // Process collection references (as per recipe FIELD PROCESSING)
            for (DOCollectionReference collRef : resolvedObject.getCollections()) {
                Long[] containedIds = collRef.getContainedObjectIds();
                if (containedIds != null) {
                    // Get the database class from the collection's content type (may be null)
                    DODatabaseClass contentTypeClass = collRef.getField().getContentTypeClass();
                    DODatabaseClass containedClass = (contentTypeClass instanceof DODatabaseClass)
                            ? (DODatabaseClass) contentTypeClass
                            : null;

                    for (Long containedId : containedIds) {
                        if (containedId != null && !processedObjectIds.contains(containedId)) {
                            // Mark as reached for all classes in its inheritance chain (if class known)
                            if (containedClass != null) {
                                markObjectAsReachedWithInheritanceChain(containedId, containedClass, tracker);
                            }

                            // OPTIMIZED FIX: Always try to process the contained object
                            // Use the pre-built index for O(1) lookup instead of expensive database scans
                            DODatabaseObject containedObject = processObjectIdRecursive(
                                    containedId,
                                    containedClass, // May be null - will be looked up from index
                                    container,
                                    schema,
                                    database,
                                    tracker,
                                    processedObjectIds,
                                    allResolvedObjects,
                                    objectIdToClassIndex);

                            // Add to global list if resolved successfully
                            if (containedObject != null) {
                                allResolvedObjects.add(containedObject);
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("ERROR: Failed to process fields for object " +
                    resolvedObject.getObjectId() + ": " + e.getMessage());
        }
    }

    /**
     * Marks reachability status in the resolved objects based on tracker results.
     */
    private void markReachabilityInObjects(
            List<DODatabaseObject> resolvedObjects,
            DOObjectReachabilityTracker tracker) {

        for (DODatabaseObject obj : resolvedObjects) {
            boolean isReachable = tracker.isObjectReached(obj.getObjectId());
            obj.setReachable(isReachable);
        }
    }

    /**
     * Populates each DODatabaseClass with its resolved objects.
     * This groups resolved objects by their most specific class.
     */
    private void populateDatabaseClassesWithResolvedObjects(
            DODatabase database,
            List<DODatabaseObject> allResolvedObjects) {

        // Group resolved objects by their most specific class (using the actual
        // DODatabaseClass object)
        Map<DODatabaseClass, List<DODatabaseObject>> objectsByClass = new HashMap<>();

        for (DODatabaseObject obj : allResolvedObjects) {
            DODatabaseClass mostSpecificClass = obj.getMostSpecificClass();
            // Most specific class should always be a DODatabaseClass for resolved objects
            if (mostSpecificClass instanceof DODatabaseClass) {
                DODatabaseClass dbClass = (DODatabaseClass) mostSpecificClass;
                List<DODatabaseObject> classObjects = objectsByClass.get(dbClass);
                if (classObjects == null) {
                    classObjects = new ArrayList<>();
                    objectsByClass.put(dbClass, classObjects);
                }
                classObjects.add(obj);
            }
        }

        // Set resolved objects on each database class
        for (DODatabaseClass dbClass : database.getClasses()) {
            List<DODatabaseObject> classObjects = objectsByClass.get(dbClass);

            if (classObjects != null && !classObjects.isEmpty()) {
                dbClass.setResolvedObjects(classObjects.toArray(new DODatabaseObject[0]));
            } else {
                dbClass.setResolvedObjects(new DODatabaseObject[0]);
            }
        }

        System.out.println("Populated " + objectsByClass.size() + " database classes with resolved objects");
    }

    /**
     * Extract direct object references from an object
     */
    private DOObjectReference[] extractDirectReferences(ExtObjectContainer container, Object obj,
            Long objectId, DODatabaseClass classDefinition, DODatabase database) {
        List<DOObjectReference> references = new ArrayList<>();

        try {
            // SPECIAL CASE: If this object itself is an ID-type, create a synthetic
            // reference to the target entity
            String shortClassName = classDefinition.getShortName();
            if (shortClassName != null && shortClassName.startsWith("ID") && shortClassName.length() > 2) {
                // This IS an ID-type object - find its mID field and create reference to target
                DODatabaseField[] fields = classDefinition.getFields();
                if (fields != null) {
                    for (DODatabaseField field : fields) {
                        // Match field names: mID, mId, or anything starting with "mID" (like
                        // mIDClassif, mIDUsagePrincipal)
                        String fieldName = field.getName();
                        if ("mID".equals(fieldName) || "mId".equals(fieldName) ||
                                (fieldName != null && fieldName.startsWith("mID"))) {
                            Object mIdValue = ObjectResolverUtil.getFieldValue(container, obj, field);
                            if (mIdValue != null) {
                                Long targetEntityId = null;
                                if (mIdValue instanceof Long) {
                                    targetEntityId = (Long) mIdValue;
                                } else if (mIdValue instanceof Integer) {
                                    targetEntityId = ((Integer) mIdValue).longValue();
                                }

                                if (targetEntityId != null && targetEntityId > 0) {
                                    // Create synthetic reference from ID object to target entity
                                    // Note: field is null because this is a synthetic reference that doesn't
                                    // correspond to an actual field - the ID object itself IS the reference
                                    DOObjectReference syntheticRef = new DOObjectReference(
                                            objectId, targetEntityId, null, DOReferenceType.DIRECT);
                                    references.add(syntheticRef);
                                }
                            }
                            break;
                        }
                    }
                }
                // Return early - ID objects only have this one synthetic reference
                return references.toArray(new DOObjectReference[0]);
            }

            // Normal processing for non-ID objects
            DODatabaseField[] fields = classDefinition.getFields();
            if (fields == null) {
                return new DOObjectReference[0];
            }

            for (DODatabaseField field : fields) {
                if (CollectionTypeUtil.isCollection(field)) {
                    continue; // Skip collections, they're handled separately
                }

                Object fieldValue = ObjectResolverUtil.getFieldValue(container, obj, field);
                if (fieldValue == null) {
                    continue;
                }

                Long refId = ObjectResolverUtil.getObjectId(container, fieldValue);
                if (refId != null) {
                    DOObjectReference ref = new DOObjectReference(
                            objectId, refId, field, DOReferenceType.DIRECT);
                    references.add(ref);
                    // No need to add synthetic reference here - the ID object itself
                    // will have a reference to its target entity when it gets resolved
                }
            }

        } catch (Exception e) {
            System.err.println(
                    "ERROR: Failed to extract direct references for object " + objectId + ": " + e.getMessage());
        }

        return references.toArray(new DOObjectReference[0]);
    }

    /**
     * Extract collection references from an object using unified collection
     * handling
     */
    private DOCollectionReference[] extractCollectionReferences(ExtObjectContainer container, Object obj,
            Long objectId, DODatabaseClass classDefinition) {
        List<DOCollectionReference> references = new ArrayList<>();

        try {
            // Universal collection detection - check if the object itself is a collection
            if (ObjectResolverUtil.isAnyCollectionType(obj)) {
                ObjectResolverUtil.CollectionExtractionResult result = ObjectResolverUtil
                        .extractUniversalCollectionContents(container, obj, objectId, null);
                if (result != null) {
                    // Create field for standalone collection objects
                    String collectionType = ObjectResolverUtil.getObjectClassName(container, obj);
                    DODatabaseField collectionField = new DODatabaseField("collection_elements", "Collection elements",
                            collectionType, null, false, true, result.contentType, null);

                    DOCollectionReference collectionRef = new DOCollectionReference(
                            objectId, collectionField,
                            result.containedIds, result.contentType, result.totalSize);
                    references.add(collectionRef);
                }
                return references.toArray(new DOCollectionReference[0]);
            }

            // Extract collection references from fields
            DODatabaseField[] fields = classDefinition.getFields();
            if (fields == null) {
                return new DOCollectionReference[0];
            }

            for (DODatabaseField field : fields) {
                if (!CollectionTypeUtil.isCollection(field)) {
                    continue; // Only process collection fields
                }

                Object fieldValue = ObjectResolverUtil.getFieldValue(container, obj, field);
                if (fieldValue == null) {
                    continue;
                }

                ObjectResolverUtil.CollectionExtractionResult result = ObjectResolverUtil
                        .extractUniversalCollectionContents(container, fieldValue, objectId, field);
                if (result != null) {
                    DOCollectionReference collectionRef = new DOCollectionReference(
                            objectId, field,
                            result.containedIds, result.contentType, result.totalSize);
                    references.add(collectionRef);
                }
            }

        } catch (Exception e) {
            System.err.println(
                    "ERROR: Failed to extract collection references for object " + objectId + ": " + e.getMessage());
        }

        return references.toArray(new DOCollectionReference[0]);
    }

    // Implement remaining interface methods with simplified logic
    public String findMostSpecificClass(DODatabase database, DOSchema schema, String[] classNames) {
        return ObjectResolverUtil.findMostSpecificClass(database, schema, classNames);
    }

    public DOObjectReference[] extractObjectReferences(ExtObjectContainer container, Long objectId,
            DODatabase database) {
        // This method is legacy - the new implementation uses resolveObject
        return new DOObjectReference[0];
    }

    public DOCollectionReference[] extractCollectionReferences(ExtObjectContainer container, Long objectId,
            DODatabase database, DOSchema schema) {
        // This method is legacy - the new implementation uses resolveObject
        return new DOCollectionReference[0];
    }

    /**
     * Print diagnostic information about reachability to understand what's
     * happening.
     */
    private void printReachabilityDiagnostics(DOObjectReachabilityTracker tracker,
            Map<String, Set<Long>> allObjectIdsByClass) {
        System.out.println("\n=== REACHABILITY DIAGNOSTICS ===");

        Map<DODatabaseClass, Set<Long>> reached = tracker.getReachedObjectsByClass();
        Map<DODatabaseClass, Set<Long>> unreached = tracker.getUnreachedObjectsByClass();

        // Count unique object IDs across all classes
        Set<Long> allUniqueIds = new HashSet<>();
        for (Set<Long> ids : allObjectIdsByClass.values()) {
            allUniqueIds.addAll(ids);
        }

        Set<Long> reachedUniqueIds = new HashSet<>();
        for (Set<Long> ids : reached.values()) {
            reachedUniqueIds.addAll(ids);
        }

        Set<Long> unreachedUniqueIds = new HashSet<>();
        for (Set<Long> ids : unreached.values()) {
            unreachedUniqueIds.addAll(ids);
        }

        System.out.println("Total unique object IDs in database: " + allUniqueIds.size());
        System.out.println("Unique reached object IDs: " + reachedUniqueIds.size());
        System.out.println("Unique unreached object IDs: " + unreachedUniqueIds.size());

        // Check if there's overlap (same object marked both reached and unreached)
        Set<Long> overlap = new HashSet<>(reachedUniqueIds);
        overlap.retainAll(unreachedUniqueIds);
        System.out.println("Objects marked as BOTH reached and unreached: " + overlap.size());

        if (overlap.size() > 0) {
            System.out.println("✓ This is EXPECTED: DB4O stores objects in multiple inheritance tables");
            System.out.println("  An object in a leaf class also appears in all its parent class tables");
        } else {
            System.out.println("⚠ WARNING: No overlap detected - unreached objects may be truly orphaned!");
        }

        // Show breakdown by class
        System.out.println("\nTop 10 classes with unreached objects:");
        unreached.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                .limit(10)
                .forEach(entry -> {
                    System.out.println(
                            "  " + entry.getKey().getAbsoluteName() + ": " + entry.getValue().size() + " unreached");
                });

        System.out.println("=================================\n");
    }

    /**
     * Checks if a class is an ID-type class (class name starts with "ID").
     */
    private boolean isIDTypeClass(DODatabaseClass dbClass) {
        if (dbClass == null) {
            return false;
        }
        String className = dbClass.getShortName();
        return className != null && className.startsWith("ID");
    }

    /**
     * Processes an ID-type object by following its mID field to the target entity.
     * As per database-processing.md: "Do OBJECT ID PROCESSING with the mID field
     * value"
     */
    private void processIDTypeObject(
            DODatabaseObject idObject,
            Long idObjectId,
            ExtObjectContainer container,
            DOSchema schema,
            DODatabase database,
            DOObjectReachabilityTracker tracker,
            Set<Long> processedObjectIds,
            List<DODatabaseObject> allResolvedObjects,
            Map<Long, DODatabaseClass> objectIdToClassIndex) {

        try {
            // Load the actual ID object from the database
            Object idObjInstance = container.getByID(idObjectId);
            if (idObjInstance == null) {
                return;
            }

            // Find the mID field (could be "mID", "mId", or similar)
            DODatabaseField mIdField = findMIdField(idObject.getMostSpecificClass());
            if (mIdField == null) {
                return;
            }

            // Extract the Long value from the mID field
            Object mIdValue = ObjectResolverUtil.getFieldValue(container, idObjInstance, mIdField);
            if (!(mIdValue instanceof Long)) {
                return;
            }

            Long targetEntityId = (Long) mIdValue;
            if (targetEntityId == null || processedObjectIds.contains(targetEntityId)) {
                return;
            }

            // Determine the target entity class based on the ID class name
            // E.g., IDEmploye -> Employe, IDEntite -> Entite
            DODatabaseClass targetClass = findTargetClassForIDType(idObject.getMostSpecificClass(), database);
            if (targetClass == null) {
                return;
            }

            // Do full OBJECT ID PROCESSING on the target entity
            DODatabaseObject targetEntity = processObjectIdRecursive(
                    targetEntityId,
                    targetClass,
                    container,
                    schema,
                    database,
                    tracker,
                    processedObjectIds,
                    allResolvedObjects,
                    objectIdToClassIndex);

            if (targetEntity != null) {
                allResolvedObjects.add(targetEntity);
            }

        } catch (Exception e) {
            // Silently handle - not all ID objects may have valid targets
        }
    }

    /**
     * Finds the mID field in an ID-type class.
     */
    private DODatabaseField findMIdField(DODatabaseClass idClass) {
        if (idClass == null) {
            return null;
        }

        DODatabaseField[] fields = idClass.getFields();
        if (fields == null) {
            return null;
        }

        // Look for common ID field names
        for (DODatabaseField field : fields) {
            String fieldName = field.getName();
            if ("mID".equals(fieldName) || "mId".equals(fieldName) || "id".equals(fieldName)) {
                return field;
            }
        }

        return null;
    }

    /**
     * Determines the target entity class for an ID-type class.
     * E.g., IDEmploye -> Employe, IDEntite -> Entite
     */
    private DODatabaseClass findTargetClassForIDType(DODatabaseClass idClass, DODatabase database) {
        if (idClass == null || database == null) {
            return null;
        }

        String idClassName = idClass.getShortName();
        if (idClassName == null || !idClassName.startsWith("ID")) {
            return null;
        }

        // Remove "ID" prefix to get the target class name
        String targetClassName = idClassName.substring(2);

        // Search for the target class in the database
        for (DODatabaseClass dbClass : database.getClasses()) {
            if (targetClassName.equals(dbClass.getShortName())) {
                return dbClass;
            }
        }

        return null;
    }
}
