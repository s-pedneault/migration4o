package migration4o.demo;

import com.db4o.Db4o;
import com.db4o.ObjectContainer;
import com.db4o.config.Configuration;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;
import com.db4o.reflect.ReflectClass;
import com.db4o.reflect.ReflectField;
import com.db4o.reflect.generic.GenericClass;
import com.db4o.reflect.generic.GenericField;
import com.db4o.reflect.generic.GenericObject;
import com.db4o.reflect.generic.GenericReflector;
import com.db4o.reflect.jdk.JdkReflector;

import migration4o.models.schema.DOSchemaConstants;

import java.io.File;

/**
 * Phase 1 validation spike for the demo database generator.
 *
 * Proves that DB4O 7.4 supports:
 *   1. Registering synthetic GenericClass definitions at runtime (no compiled .class files needed)
 *   2. Creating GenericObject instances from those classes
 *   3. Setting field values and persisting objects with store() + commit()
 *   4. Reading the resulting database back using the standard JdkReflector configuration
 *      (same path taken when opening customer databases)
 *
 * Usage: run via run-demo-spike.sh
 * Output: local/demo-spike.dat
 * Expected: PASSED printed at the end
 */
public class DemoGeneratorSpike {

    // Fake class name -- not a real compiled class, purely synthetic
    private static final String CLASS_NAME = "gest.test.Person";
    private static final String OUTPUT_PATH = "local/demo-spike.dat";

    public static void main(String[] args) {
        System.out.println();
        System.out.println("=== Demo Generator Spike — Phase 1 Validation ===");
        System.out.println();

        // Clean up any previous spike file
        File outFile = new File(OUTPUT_PATH);
        if (outFile.exists()) {
            boolean deleted = outFile.delete();
            System.out.println("[write] Deleted previous spike file: " + deleted);
        }

        boolean writeOk = writeDatabase();
        if (!writeOk) {
            System.out.println();
            System.out.println("RESULT: FAILED during write phase.");
            System.exit(1);
        }

        boolean readOk = readBackDatabase();
        System.out.println();
        if (readOk) {
            System.out.println("RESULT: PASSED — GenericObject round-trip works. Demo generator is feasible.");
        } else {
            System.out.println("RESULT: FAILED — See errors above. Consider Byte Buddy fallback.");
            System.exit(1);
        }
    }

    // ── Write phase ──────────────────────────────────────────────────────────

    /**
     * DB4O deep-clones the reflector during openFile(), so any GenericClass
     * instances created beforehand become orphans — forGenericObject() sees
     * a different instance for the same name and throws IllegalStateException.
     *
     * The correct sequence is:
     *   1. Open the container (creates an empty .dat file)
     *   2. Obtain the container's own GenericReflector via ext().reflector()
     *   3. Create GenericClass/GenericField using THAT reflector
     *   4. Register the class with the container's reflector
     *   5. Create GenericObject instances from the registered class
     *   6. store() + commit()
     */
    private static boolean writeDatabase() {
        System.out.println("--- Write Phase ---");
        ObjectContainer container = null;
        try {
            // 1. Open a new empty database file with standard configuration.
            Configuration config = Db4o.newConfiguration();
            config.activationDepth(0);
            config.updateDepth(10);
            config.callConstructors(true);
            config.exceptionsOnNotStorable(false);

            container = Db4o.openFile(config, OUTPUT_PATH);
            System.out.println("[write] Opened new container: " + OUTPUT_PATH);

            // 2. Get the container's own reflector — this is the one store() uses internally.
            GenericReflector reflector = container.ext().reflector();
            System.out.println("[write] Got container reflector: " + reflector.getClass().getName());

            // 3. Resolve primitive/standard types from the container's reflector.
            ReflectClass intType = reflector.forClass(int.class);
            ReflectClass stringType = reflector.forClass(String.class);
            System.out.println("[write] int type:    " + (intType != null ? intType.getName() : "NULL"));
            System.out.println("[write] String type: " + (stringType != null ? stringType.getName() : "NULL"));

            // 4. Create a synthetic class using the container's reflector.
            //    Constructor: GenericClass(reflector, delegateClass, name, superClass)
            //      - delegateClass: null (no compiled .class file on classpath)
            //      - superClass:    null (no parent for this test)
            GenericClass personClass = new GenericClass(reflector, null, CLASS_NAME, null);

            // 5. Define fields using the container's type system.
            //    Constructor: GenericField(name, fieldType, isPrimitive)
            GenericField idField = new GenericField(DOSchemaConstants.OBJECT_BUSINESS_ID_FIELD_NAME, intType, true);
            GenericField nomField = new GenericField("mNom", stringType, false);
            personClass.initFields(new GenericField[] { idField, nomField });

            // 6. Register the class with the container's reflector.
            //    After this, forName(CLASS_NAME) will return this exact instance.
            reflector.register(personClass);
            System.out.println("[write] Registered class: " + personClass.getName());

            // 7. Create and store objects.
            storeObject(container, personClass, idField, nomField, 1, "Fortier");
            storeObject(container, personClass, idField, nomField, 2, "Tremblay");

            container.commit();
            System.out.println("[write] Committed 2 objects.");

            return true;

        } catch (Exception ex) {
            System.err.println("[write] EXCEPTION: " + ex.getMessage());
            ex.printStackTrace(System.err);
            return false;
        } finally {
            if (container != null) {
                container.close();
                System.out.println("[write] Container closed.");
            }
        }
    }

    private static void storeObject(ObjectContainer container, GenericClass personClass, GenericField idField, GenericField nomField, int id, String nom) {
        GenericObject obj = (GenericObject) personClass.newInstance();
        idField.set(obj, id);
        nomField.set(obj, nom);
        container.store(obj);
        System.out.println("[write] Stored: mID=" + id + ", mNom=" + nom);
    }

    // ── Read phase ───────────────────────────────────────────────────────────

    /**
     * Re-opens the file using the same plain JdkReflector config used for customer
     * databases (no GenericReflector passed in). If DB4O correctly falls back to
     * GenericObject for unknown classes, our synthetic objects will be readable.
     */
    private static boolean readBackDatabase() {
        System.out.println();
        System.out.println("--- Read-Back Phase ---");
        try {
            // Use the same minimal config as DODatabaseConfiguration (JdkReflector only).
            Configuration config = Db4o.newConfiguration();
            config.activationDepth(0);
            config.callConstructors(true);
            config.reflectWith(new JdkReflector(Thread.currentThread().getContextClassLoader()));

            ObjectContainer container = Db4o.openFile(config, OUTPUT_PATH);
            ExtObjectContainer ext = container.ext();
            System.out.println("[read] Opened container.");

            try {
                // Find our synthetic class by iterating all stored classes.
                StoredClass storedPerson = findStoredClass(ext, CLASS_NAME);
                if (storedPerson == null) {
                    System.err.println("[read] FAIL: class '" + CLASS_NAME + "' not found in stored classes.");
                    return false;
                }
                System.out.println("[read] Found stored class: " + storedPerson.getName());

                // Verify instance count.
                int count = storedPerson.instanceCount();
                System.out.println("[read] Instance count: " + count);
                if (count != 2) {
                    System.err.println("[read] FAIL: expected 2 instances, got " + count);
                    return false;
                }

                // List stored fields.
                StoredField[] storedFields = storedPerson.getStoredFields();
                System.out.println("[read] Stored fields (" + storedFields.length + "):");
                for (StoredField sf : storedFields) {
                    System.out.println("[read]   " + sf.getName() + " (" + sf.getStoredType() + ")");
                }

                if (storedFields.length == 0) {
                    System.err.println("[read] FAIL: no stored fields found — field metadata was not persisted.");
                    return false;
                }

                // Read all objects and verify field values.
                long[] objectIds = storedPerson.getIDs();
                System.out.println("[read] Object IDs (" + objectIds.length + "):");
                boolean allValuesPresent = true;

                StoredField idStoredField = storedPerson.storedField(DOSchemaConstants.OBJECT_BUSINESS_ID_FIELD_NAME, null);
                StoredField nomStoredField = storedPerson.storedField("mNom", null);

                if (idStoredField == null || nomStoredField == null) {
                    System.err.println("[read] FAIL: could not locate stored fields mID / mNom.");
                    return false;
                }

                for (long oid : objectIds) {
                    Object obj = ext.getByID(oid);
                    if (obj == null) {
                        System.err.println("[read] FAIL: getByID(" + oid + ") returned null.");
                        allValuesPresent = false;
                        continue;
                    }
                    container.activate(obj, 1);

                    Object mID = idStoredField.get(obj);
                    Object mNom = nomStoredField.get(obj);
                    System.out.println("[read]   oid=" + oid + " → mID=" + mID + ", mNom=" + mNom);

                    if (mNom == null) {
                        System.err.println("[read] FAIL: mNom is null for oid=" + oid);
                        allValuesPresent = false;
                    }
                }

                return allValuesPresent;

            } finally {
                container.close();
                System.out.println("[read] Container closed.");
            }

        } catch (Exception ex) {
            System.err.println("[read] EXCEPTION: " + ex.getMessage());
            ex.printStackTrace(System.err);
            return false;
        }
    }

    private static StoredClass findStoredClass(ExtObjectContainer ext, String className) {
        for (StoredClass sc : ext.storedClasses()) {
            if (className.equals(sc.getName())) {
                return sc;
            }
        }
        return null;
    }
}
