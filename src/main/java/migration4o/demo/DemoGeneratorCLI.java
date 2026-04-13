package migration4o.demo;

import com.db4o.Db4o;
import com.db4o.ObjectContainer;
import com.db4o.config.Configuration;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.reflect.jdk.JdkReflector;

import migration4o.models.schema.DOSchema;
import migration4o.schema.DOSchemaService;

import java.io.File;

/**
 * CLI entry point for generating a demo DB4O database.
 *
 * Usage:
 *   java migration4o.demo.DemoGeneratorCLI [options]
 *
 * Options:
 *   --output <path>         Output database file (default: local/55555/demo.dat)
 *   --scale  <small|medium|large>  Number of objects per class (default: medium)
 *   --seed   <number>       Random seed for deterministic output (default: 42)
 *   --verify                Reopen and verify the generated database
 */
public class DemoGeneratorCLI {

    private static final String DEFAULT_OUTPUT = "local/55555/demo.dat";
    private static final long DEFAULT_SEED = 42;
    private static final DataGenerator.Scale DEFAULT_SCALE = DataGenerator.Scale.MEDIUM;

    public static void main(String[] args) {
        // Parse arguments
        String outputPath = DEFAULT_OUTPUT;
        long seed = DEFAULT_SEED;
        DataGenerator.Scale scale = DEFAULT_SCALE;
        boolean verify = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
            case "--output":
                if (i + 1 < args.length)
                    outputPath = args[++i];
                break;
            case "--scale":
                if (i + 1 < args.length) {
                    String s = args[++i].toUpperCase();
                    try {
                        scale = DataGenerator.Scale.valueOf(s);
                    } catch (IllegalArgumentException e) {
                        System.err.println("Invalid scale: " + s + ". Use small, medium, or large.");
                        System.exit(1);
                    }
                }
                break;
            case "--seed":
                if (i + 1 < args.length) {
                    try {
                        seed = Long.parseLong(args[++i]);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid seed: " + args[i]);
                        System.exit(1);
                    }
                }
                break;
            case "--verify":
                verify = true;
                break;
            case "--help":
                printUsage();
                System.exit(0);
                break;
            default:
                System.err.println("Unknown option: " + args[i]);
                printUsage();
                System.exit(1);
            }
        }

        System.out.println();
        System.out.println("=== Demo Database Generator ===");
        System.out.println();
        System.out.println("  Output: " + outputPath);
        System.out.println("  Scale:  " + scale + " (" + scale.objectsPerClass + " objects/class)");
        System.out.println("  Seed:   " + seed);
        System.out.println();

        try {
            generate(outputPath, scale, seed);

            if (verify) {
                System.out.println();
                verifyDatabase(outputPath);
            }
        } catch (Exception e) {
            System.err.println("FAILED: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void generate(String outputPath, DataGenerator.Scale scale, long seed) throws Exception {
        long startTime = System.currentTimeMillis();

        // Clean up any previous file
        File outFile = new File(outputPath);
        if (outFile.exists()) {
            if (!outFile.delete()) {
                throw new RuntimeException("Cannot delete existing file: " + outputPath);
            }
            System.out.println("[gen] Deleted previous file.");
        }

        // Ensure parent directory exists
        File parentDir = outFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        // 1. Load reference schema
        System.out.println("[gen] Loading reference schema...");
        DOSchema schema = DOSchemaService.getInstance().loadReferenceSchema();
        System.out.println("[gen] Schema loaded: " + schema.getClasses().length + " classes.");

        // 2. Open empty DB4O container
        System.out.println("[gen] Opening new DB4O container...");
        Configuration config = Db4o.newConfiguration();
        config.activationDepth(0);
        config.updateDepth(10);
        config.callConstructors(true);
        config.exceptionsOnNotStorable(false);

        ObjectContainer container = Db4o.openFile(config, outputPath);

        try {
            // 3. Register all schema classes
            System.out.println("[gen] Registering schema classes...");
            SchemaClassRegistrar registrar = new SchemaClassRegistrar(container, schema);
            int classCount = registrar.registerAll();

            // 4. Generate objects
            System.out.println("[gen] Generating objects (scale=" + scale + ", seed=" + seed + ")...");
            DataGenerator dataGen = new DataGenerator(seed, scale);
            DemoObjectFactory factory = new DemoObjectFactory(container, schema, registrar, dataGen);
            int objectCount = factory.generateAll();

            long elapsed = System.currentTimeMillis() - startTime;
            long fileSize = new File(outputPath).length();

            System.out.println();
            System.out.println("=== Generation Complete ===");
            System.out.println("  Classes:  " + classCount);
            System.out.println("  Objects:  " + objectCount);
            System.out.println("  File:     " + outputPath + " (" + formatFileSize(fileSize) + ")");
            System.out.println("  Time:     " + elapsed + " ms");
            System.out.println();

        } finally {
            container.close();
            System.out.println("[gen] Container closed.");
        }
    }

    // ── Verification ─────────────────────────────────────────────────────────

    private static void verifyDatabase(String outputPath) {
        System.out.println("=== Verification ===");
        System.out.println("[verify] Reopening database with standard JdkReflector config...");

        Configuration config = Db4o.newConfiguration();
        config.activationDepth(0);
        config.callConstructors(true);
        config.reflectWith(new JdkReflector(Thread.currentThread().getContextClassLoader()));

        ObjectContainer container = Db4o.openFile(config, outputPath);
        ExtObjectContainer ext = container.ext();

        try {
            StoredClass[] storedClasses = ext.storedClasses();
            int totalInstances = 0;
            int classesWithObjects = 0;

            // Filter to only gest.* classes (skip java.* and db4o internals)
            for (StoredClass sc : storedClasses) {
                String name = sc.getName();
                if (!name.startsWith("gest.") && !name.startsWith("gen."))
                    continue;

                int count = sc.instanceCount();
                if (count > 0)
                    classesWithObjects++;
                totalInstances += count;
            }

            System.out.println("[verify] Stored classes (gest.*/gen.*): " + countDomainClasses(storedClasses));
            System.out.println("[verify] Classes with objects: " + classesWithObjects);
            System.out.println("[verify] Total instances: " + totalInstances);

            // Spot-check: list top 10 classes by instance count
            System.out.println("[verify] Top classes by instance count:");
            java.util.List<StoredClass> sorted = new java.util.ArrayList<>();
            for (StoredClass sc : storedClasses) {
                if (sc.getName().startsWith("gest.") || sc.getName().startsWith("gen.")) {
                    sorted.add(sc);
                }
            }
            sorted.sort((a, b) -> b.instanceCount() - a.instanceCount());
            for (int i = 0; i < Math.min(10, sorted.size()); i++) {
                StoredClass sc = sorted.get(i);
                System.out.println("[verify]   " + sc.getName() + " → " + sc.instanceCount() + " objects");
            }

            System.out.println();
            if (totalInstances > 0) {
                System.out.println("VERIFICATION: PASSED");
            } else {
                System.out.println("VERIFICATION: FAILED — no objects found");
            }

        } finally {
            container.close();
        }
    }

    private static int countDomainClasses(StoredClass[] classes) {
        int count = 0;
        for (StoredClass sc : classes) {
            String name = sc.getName();
            if (name.startsWith("gest.") || name.startsWith("gen."))
                count++;
        }
        return count;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String formatFileSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private static void printUsage() {
        System.out.println("Usage: DemoGeneratorCLI [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --output <path>              Output database file (default: local/55555/demo.dat)");
        System.out.println("  --scale  <small|medium|large> Objects per class (default: medium)");
        System.out.println("  --seed   <number>            Random seed (default: 42)");
        System.out.println("  --verify                     Verify generated database after creation");
        System.out.println("  --help                       Show this help");
    }
}
