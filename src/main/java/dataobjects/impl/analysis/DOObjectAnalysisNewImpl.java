package dataobjects.impl.analysis;

import dataobjects.api.engine.DOEngine;
import dataobjects.api.analysis.DOObjectAnalysis;
import dataobjects.api.models.database.DODatabase;
import dataobjects.api.models.database.DODatabaseClass;
import dataobjects.api.models.database.DODatabaseObject;
import dataobjects.api.database.DODatabaseReader;
import dataobjects.impl.database.DODatabaseReaderImpl;
import java.io.*;
import java.util.*;

/**
 * Simplified analysis class that works with fully-resolved objects from the
 * database.
 * All the complex resolution logic has been moved to the database loading
 * process.
 * 
 * This is the new, clean implementation that replaces the complex analysis code
 * with simple reporting on pre-resolved data.
 */
public class DOObjectAnalysisNewImpl implements DOObjectAnalysis {

    public void analyze(DOEngine engine) {
        // System.out.println("Starting analysis with fully-resolved objects...");

        // Use the enhanced database reader to get fully resolved objects
        DODatabaseReader reader = new DODatabaseReaderImpl();
        DODatabase fullyResolvedDatabase;

        try {
            fullyResolvedDatabase = reader.readDatabaseWithFullResolution(
                    engine.getDatabase().getContainer(),
                    engine.getDatabase().getEncoding(),
                    engine.getDatabase().getDatabaseSize(),
                    engine.getSchema());
            // System.out.println("Successfully loaded database with full resolution");
        } catch (Exception e) {
            System.err.println(
                    "Error loading fully resolved database, falling back to basic analysis: " + e.getMessage());
            fullyResolvedDatabase = engine.getDatabase();
        }

        // Generate analysis reports using resolved data
        outputDatabaseAllObjectIDs(fullyResolvedDatabase);
        outputDatabaseInheritedObjectIDs(fullyResolvedDatabase);
        outputDatabaseOrphanedObjectIDs(fullyResolvedDatabase);

        System.out.println("Analysis completed successfully!");
    }

    /**
     * Output all objects by class - now using resolved objects.
     */
    private void outputDatabaseAllObjectIDs(DODatabase database) {
        try {
            File outputDir = new File("output");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            File outputFile = new File("output/Analysis - All objects.csv");
            PrintWriter writer = new PrintWriter(new FileWriter(outputFile));

            // Get all classes from the database
            DODatabaseClass[] classes = database.getClasses();
            Map<String, List<Long>> classObjectIds = new TreeMap<>();

            for (DODatabaseClass dbClass : classes) {
                String className = dbClass.getShortName();
                List<Long> objectIds = new ArrayList<>();

                // Get resolved objects for this class
                DODatabaseObject[] resolvedObjects = dbClass.getResolvedObjects();
                for (DODatabaseObject obj : resolvedObjects) {
                    objectIds.add(obj.getObjectId());
                }

                Collections.sort(objectIds);
                classObjectIds.put(className, objectIds);
            }

            // Write CSV data
            for (Map.Entry<String, List<Long>> entry : classObjectIds.entrySet()) {
                String className = entry.getKey();
                List<Long> objectIds = entry.getValue();

                writer.print(className);
                for (Long objectId : objectIds) {
                    writer.print("," + objectId);
                }
                writer.println();
            }

            writer.close();
            System.out.println("Successfully generated output/Analysis - All objects.csv with " +
                    classObjectIds.size() + " classes");

        } catch (Exception e) {
            System.err.println("Error generating all objects analysis: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Output inherited objects - now using resolved inheritance information.
     */
    private void outputDatabaseInheritedObjectIDs(DODatabase database) {
        try {
            File outputFile = new File("output/Analysis - Inherited objects.csv");
            PrintWriter writer = new PrintWriter(new FileWriter(outputFile));

            DODatabaseClass[] classes = database.getClasses();

            for (DODatabaseClass dbClass : classes) {
                String className = dbClass.getShortName();
                writer.print(className);

                // Add inheritance chain (already resolved!)
                if (!dbClass.getInheritanceChain().isEmpty()) {
                    for (DODatabaseClass ancestor : dbClass.getInheritanceChain()) {
                        writer.print("," + ancestor.getAbsoluteName());
                    }
                }

                // Add object IDs
                DODatabaseObject[] resolvedObjects = dbClass.getResolvedObjects();
                for (DODatabaseObject obj : resolvedObjects) {
                    writer.print("," + obj.getObjectId());
                }

                writer.println();
            }

            writer.close();
            System.out.println("Successfully generated output/Analysis - Inherited objects.csv");

        } catch (Exception e) {
            System.err.println("Error generating inherited objects analysis: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Output orphaned objects - now using resolved reachability information.
     */
    private void outputDatabaseOrphanedObjectIDs(DODatabase database) {
        try {
            File outputFile = new File("output/Analysis - Objects not found through modules.csv");
            PrintWriter writer = new PrintWriter(new FileWriter(outputFile));

            DODatabaseClass[] classes = database.getClasses();
            int totalOrphaned = 0;

            for (DODatabaseClass dbClass : classes) {
                String className = dbClass.getShortName();

                // Get orphaned objects (already resolved!)
                DODatabaseObject[] orphanedObjects = dbClass.getOrphanedObjects();

                if (orphanedObjects.length > 0) {
                    writer.print(className);

                    List<Long> orphanedIds = new ArrayList<>();
                    for (DODatabaseObject obj : orphanedObjects) {
                        orphanedIds.add(obj.getObjectId());
                    }

                    Collections.sort(orphanedIds);
                    for (Long objectId : orphanedIds) {
                        writer.print("," + objectId);
                    }

                    writer.println();
                    totalOrphaned += orphanedIds.size();
                }
            }

            writer.close();
            System.out.println("Successfully generated output/Analysis - Objects not found through modules.csv with " +
                    totalOrphaned + " orphaned objects");

        } catch (Exception e) {
            System.err.println("Error generating orphaned objects analysis: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
