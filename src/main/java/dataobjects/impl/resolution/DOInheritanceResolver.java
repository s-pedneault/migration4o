package dataobjects.impl.resolution;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dataobjects.impl.models.database.*;
import dataobjects.impl.models.schema.*;
import dataobjects.impl.resolution.DOInheritanceResolver;
import dataobjects.util.InheritanceUtil;

/**
 * Implementation of inheritance resolver that builds direct references between
 * classes
 * instead of creating separate inheritance info objects.
 */
public class DOInheritanceResolver {

    public void resolveInheritance(DODatabase database, DOSchema schema) {
        DODatabaseClass[] classes = database.getClasses();

        // Create efficient lookup maps to avoid repeated linear searches
        Map<String, DODatabaseClass> databaseClassMap = InheritanceUtil.createDatabaseClassMap(classes);
        Map<String, DOSchemaClass> schemaClassMap = InheritanceUtil.createSchemaClassMap(schema);

        // Phase 1: Establish parent-child relationships and inheritance chains
        establishParentChildRelationships(classes, databaseClassMap, schemaClassMap);

        // Phase 2: Build complete subclass collections for each class
        buildSubclassCollections(classes, schemaClassMap);
    }

    /**
     * Phase 1: Establish direct parent-child relationships and build inheritance
     * chains.
     */
    private void establishParentChildRelationships(DODatabaseClass[] classes,
            Map<String, DODatabaseClass> databaseClassMap, Map<String, DOSchemaClass> schemaClassMap) {

        for (DODatabaseClass dbClass : classes) {
            String className = dbClass.getAbsoluteName();

            // Build complete inheritance chain using utility method
            List<String> inheritanceChainNames = InheritanceUtil.buildInheritanceChain(
                    className, schemaClassMap, databaseClassMap);

            // Set immediate parent class if exists
            if (!inheritanceChainNames.isEmpty()) {
                String parentName = inheritanceChainNames.get(0);
                DODatabaseClass parentClass = InheritanceUtil.findDatabaseClass(databaseClassMap, parentName);
                if (parentClass != null) {
                    dbClass.setParentClass(parentClass);
                    parentClass.getDirectSubclasses().add(dbClass);
                }
            }

            // Build inheritance chain with actual DODatabaseClass objects
            dbClass.getInheritanceChain().clear(); // Ensure clean state
            for (String ancestorName : inheritanceChainNames) {
                DODatabaseClass ancestorClass = InheritanceUtil.findDatabaseClass(databaseClassMap, ancestorName);
                if (ancestorClass != null) {
                    dbClass.getInheritanceChain().add(ancestorClass);
                }
            }
        }
    }

    /**
     * Phase 2: Build complete subclass collections using efficient lookups.
     */
    private void buildSubclassCollections(DODatabaseClass[] classes, Map<String, DOSchemaClass> schemaClassMap) {
        for (DODatabaseClass dbClass : classes) {
            // Find all subclasses using utility method with hard references
            Set<DODatabaseClass> allSubclasses = InheritanceUtil.findAllSubclasses(dbClass, classes, schemaClassMap);

            // Clear and populate the all subclasses collection
            dbClass.getAllSubclasses().clear();
            dbClass.getAllSubclasses().addAll(allSubclasses);
        }
    }

    public String[] buildInheritanceChain(DOSchema schema, String className) {
        // Use the utility method for cleaner implementation
        Map<String, DOSchemaClass> schemaClassMap = InheritanceUtil.createSchemaClassMap(schema);
        List<String> chain = InheritanceUtil.buildInheritanceChain(className, schemaClassMap, new HashMap<>());
        return chain.toArray(new String[0]);
    }

}
