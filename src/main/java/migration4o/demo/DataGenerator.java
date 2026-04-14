package migration4o.demo;

import migration4o.models.schema.DOSchemaConstants;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.schema.DOSchemaValueMap;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Random;

/**
 * Deterministic seeded data generator that produces believable field values. Uses field-name heuristics to choose contextually appropriate data (e.g., mNom → last name, mTelephone → phone number).
 */
public class DataGenerator {

    public enum Scale {
        SMALL(5), MEDIUM(25), LARGE(100);

        public final int objectsPerClass;

        Scale(int count) {
            this.objectsPerClass = count;
        }
    }

    /** Number of fire departments in a demo multi-SSI database. */
    static final int FIRE_DEPT_COUNT = 3;

    /**
     * Probability (0..100) that a record is not tied to any organization (mIDSSI = -1). Mirrors real databases where some records predate org assignment.
     */
    static final int NO_ORG_PERCENT = 20;

    /** DossierAdresse is a prime object referenced by many classes — always generate plenty. */
    private static final String DOSSIER_ADRESSE_CLASS = "gest.dossPrev.DossPrev";

    private final Random rng;
    private final Scale scale;
    private int nextId = 1;

    public DataGenerator(long seed, Scale scale) {
        this.rng = new Random(seed);
        this.scale = scale;
    }

    public Scale getScale() {
        return scale;
    }

    /**
     * Returns how many objects to create for the given class. Applies per-class overrides before falling back to scale defaults.
     */
    public int getObjectCount(String className, boolean isParam, boolean isStatic, boolean alwaysExportAll) {
        // DossierAdresse is a prime object — MANY classes point to it; always generate plenty
        if (DOSSIER_ADRESSE_CLASS.equals(className)) {
            return Math.max(scale.objectsPerClass * 2, 50);
        }
        // Exactly one org-config object per fire department triggers multi-org export
        if (DOSchemaConstants.ORGANIZATION_CLASS_NAME.equals(className)) {
            return FIRE_DEPT_COUNT;
        }
        if (isParam || isStatic) {
            return Math.min(scale.objectsPerClass, 8); // params/static: small fixed set
        }
        return scale.objectsPerClass;
    }

    /**
     * Allocates a unique sequential ID.
     */
    public int nextId() {
        return nextId++;
    }

    /**
     * Generates a value appropriate for the given schema field. Uses field name heuristics and type to produce believable data.
     */
    public Object generateValue(DOSchemaField field) {
        String type = field.attributes.type;
        if (type == null)
            type = "string";

        String fieldName = field.attributes.source != null ? field.attributes.source.toLowerCase() : "";

        // Check if field has a value map — pick a valid key from it
        if (field.attributes.valueMap != null) {
            return pickFromValueMap(field.attributes.valueMap, type);
        }

        // For string fields, pass the parent class context for more precise generation
        String lower = type.toLowerCase();
        if (lower.equals("string") || lower.equals("java.lang.string")) {
            String parentClassName = (field.parentClass != null) ? field.parentClass.attributes.source : null;
            return generateString(fieldName, parentClassName);
        }

        // For int fields whose sentinel is -1, return -1 most of the time.
        // In production data, MINUS_ONE is the "none" default for these ID/reference fields —
        // generating realistic values means defaulting to -1, not a random number.
        // Exception: mIDSSI identifies which organization an object belongs to and must be a
        // positive org ID (1..FIRE_DEPT_COUNT) — its dedicated handler in generateInt() takes care of it.
        if ((lower.equals("int") || lower.equals("java.lang.integer")) && field.attributes.skipWhen != null && field.attributes.skipWhen.contains("MINUS_ONE") && !fieldName.equals(DOSchemaConstants.ORGANIZATION_BUSINESS_ID_FIELD_NAME) && !fieldName.equals(DOSchemaConstants.OBJECT_BUSINESS_ID_FIELD_NAME) && rng.nextInt(100) < 80) {
            return -1;
        }

        return generatePrimitiveValue(type, fieldName);
    }

    /**
     * Generates a primitive value by type name and field name, without requiring a DOSchemaField. Used for collection elements and other contexts where no schema field is available.
     */
    public Object generatePrimitiveValue(String type, String fieldName) {
        if (type == null)
            type = "string";
        String lower = type.toLowerCase();
        switch (lower) {
        case "string":
        case "java.lang.string":
            return generateString(fieldName);
        case "int":
        case "java.lang.integer":
            return generateInt(fieldName);
        case "double":
        case "java.lang.double":
            return generateDouble(fieldName);
        case "boolean":
        case "java.lang.boolean":
            return generateBoolean(fieldName);
        case "long":
        case "java.lang.long":
            return generateLong(fieldName);
        case "date":
        case "java.util.date":
        case "java.sql.date":
            return generateDate(fieldName);
        case "float":
        case "java.lang.float":
            return (float) generateDouble(fieldName);
        case "byte":
        case "java.lang.byte":
            return (byte) rng.nextInt(128);
        case "object":
        case "java.lang.object":
            return null; // Unknown object type, leave null
        default:
            return null; // Complex/reference types handled by DemoObjectFactory
        }
    }

    /**
     * Should this field value occasionally be null/empty? Uses skipWhen hints from the schema.
     */
    public boolean shouldBeNull(DOSchemaField field) {
        // Fields with a valueMap have enumeration constraints in the XSD — never leave them empty.
        if (field.attributes.valueMap != null) {
            return false;
        }
        // ~15% chance of null for nullable fields
        if (field.attributes.skipWhen != null && field.attributes.skipWhen.contains("NULL")) {
            return rng.nextInt(100) < 15;
        }
        return false;
    }

    /**
     * How many elements to put in a collection field.
     */
    public int collectionSize() {
        switch (scale) {
        case SMALL:
            return rng.nextInt(3); // 0-2
        case MEDIUM:
            return rng.nextInt(5); // 0-4
        case LARGE:
            return 1 + rng.nextInt(8); // 1-8
        default:
            return 1;
        }
    }

    public Random getRng() {
        return rng;
    }

    // ── String generation by field name heuristic ────────────────────────────

    /**
     * Generates a string value with class-context awareness. Used when a DOSchemaField is available via generateValue().
     */
    private String generateString(String fieldName, String parentClassName) {
        // VilleGeo.mNom should be a municipality name, not a person's last name
        if ("gest.config.VilleGeo".equals(parentClassName) && "mnom".equals(fieldName)) {
            return QuebecDataLists.municipality(rng);
        }
        return generateString(fieldName);
    }

    private String generateString(String fieldName) {
        // Name fields
        if (fieldName.equals("mnom") || fieldName.endsWith("nom")) {
            return QuebecDataLists.lastName(rng);
        }
        if (fieldName.equals("mprenom") || fieldName.endsWith("prenom")) {
            return QuebecDataLists.firstName(rng);
        }
        if (fieldName.contains("nomcomplet") || fieldName.contains("nomemploye")) {
            return QuebecDataLists.fullName(rng);
        }

        // Contact info
        if (fieldName.contains("courriel") || fieldName.contains("email") || fieldName.contains("mail")) {
            return QuebecDataLists.email(rng);
        }
        if (fieldName.contains("telephone") || fieldName.contains("telcel") || fieldName.contains("telres") || fieldName.contains("fax") || fieldName.contains("telbur") || fieldName.contains("pagette")) {
            return QuebecDataLists.phoneNumber(rng);
        }

        // Address fields
        if (fieldName.contains("adresse") || fieldName.contains("rue")) {
            return QuebecDataLists.streetAddress(rng);
        }
        if (fieldName.contains("ville") || fieldName.contains("municipalite")) {
            return QuebecDataLists.municipality(rng);
        }
        if (fieldName.contains("codepostal") || fieldName.contains("codpos")) {
            return QuebecDataLists.postalCode(rng);
        }
        if (fieldName.contains("province")) {
            return "Québec";
        }
        if (fieldName.contains("pays")) {
            return "Canada";
        }

        // Fire department specific
        if (fieldName.contains("grade")) {
            return QuebecDataLists.pick(QuebecDataLists.FIRE_GRADES, rng);
        }
        if (fieldName.contains("certification") || fieldName.contains("formation")) {
            return QuebecDataLists.pick(QuebecDataLists.FIRE_CERTIFICATIONS, rng);
        }
        if (fieldName.contains("typevehicule") || fieldName.contains("typecamion")) {
            return QuebecDataLists.pick(QuebecDataLists.VEHICLE_TYPES, rng);
        }
        if (fieldName.contains("marque") || fieldName.contains("fabriquant")) {
            return QuebecDataLists.pick(QuebecDataLists.VEHICLE_MAKES, rng);
        }
        if (fieldName.contains("batiment") || fieldName.contains("typebat")) {
            return QuebecDataLists.pick(QuebecDataLists.BUILDING_TYPES, rng);
        }
        if (fieldName.contains("chauffage")) {
            return QuebecDataLists.pick(QuebecDataLists.HEATING_TYPES, rng);
        }

        // Description / notes
        if (fieldName.contains("description") || fieldName.contains("remarque") || fieldName.contains("commentaire") || fieldName.contains("note")) {
            return QuebecDataLists.pick(QuebecDataLists.SHORT_NOTES, rng);
        }

        // Codes / identifiers
        if (fieldName.contains("code")) {
            return "CODE-" + (100 + rng.nextInt(900));
        }
        if (fieldName.contains("numero") || fieldName.contains("nocivique")) {
            return String.valueOf(1 + rng.nextInt(9999));
        }
        if (fieldName.contains("prefixe")) {
            return String.valueOf((char) ('A' + rng.nextInt(26)));
        }

        // Generic short text
        if (fieldName.contains("titre") || fieldName.contains("libelle") || fieldName.contains("etiquette")) {
            return "Élément " + (1 + rng.nextInt(500));
        }

        // Default
        return "Demo-" + (1 + rng.nextInt(10000));
    }

    // ── Numeric generation ──────────────────────────────────────────────────

    private int generateInt(String fieldName) {
        if (fieldName.equalsIgnoreCase(DOSchemaConstants.ORGANIZATION_BUSINESS_ID_FIELD_NAME) || fieldName.equals("middbconso")) {
            return nextId(); // Unique sequential ID
        }
        // mIDSSI identifies which fire department an object belongs to
        if (fieldName.equals(DOSchemaConstants.ORGANIZATION_BUSINESS_ID_FIELD_NAME)) {
            return 1 + rng.nextInt(FIRE_DEPT_COUNT);
        }
        if (fieldName.contains("annee") || fieldName.contains("year")) {
            return 2005 + rng.nextInt(20);
        }
        if (fieldName.contains("mois") || fieldName.contains("month")) {
            return 1 + rng.nextInt(12);
        }
        if (fieldName.contains("jour") || fieldName.contains("day")) {
            return 1 + rng.nextInt(28);
        }
        if (fieldName.contains("etage") || fieldName.contains("nbre") || fieldName.contains("nombre")) {
            return rng.nextInt(20);
        }
        if (fieldName.contains("priorite") || fieldName.contains("niveau")) {
            return 1 + rng.nextInt(5);
        }
        if (fieldName.contains("couleur") || fieldName.contains("color")) {
            return rng.nextInt(0xFFFFFF);
        }
        if (fieldName.contains("contrainte")) {
            return rng.nextInt(3); // 0, 1, or 2
        }
        return rng.nextInt(100);
    }

    private double generateDouble(String fieldName) {
        if (fieldName.contains("latitude") || fieldName.contains("lat")) {
            return 45.0 + rng.nextDouble() * 5; // Quebec latitudes
        }
        if (fieldName.contains("longitude") || fieldName.contains("lon")) {
            return -(71.0 + rng.nextDouble() * 8); // Quebec longitudes (negative)
        }
        if (fieldName.contains("montant") || fieldName.contains("cout") || fieldName.contains("prix") || fieldName.contains("salaire") || fieldName.contains("taux")) {
            return Math.round(rng.nextDouble() * 10000.0) / 100.0; // monetary, 2 decimals
        }
        if (fieldName.contains("distance") || fieldName.contains("superficie")) {
            return Math.round(rng.nextDouble() * 1000.0) / 10.0;
        }
        return Math.round(rng.nextDouble() * 1000.0) / 100.0;
    }

    private long generateLong(String fieldName) {
        if (fieldName.contains("idjpa")) {
            return nextId();
        }
        return rng.nextLong() & 0x7FFFFFFFL; // positive long
    }

    private boolean generateBoolean(String fieldName) {
        // Most boolean flags are false by default
        if (fieldName.contains("inactif") || fieldName.contains("supprime") || fieldName.contains("archive") || fieldName.contains("ferme")) {
            return rng.nextInt(100) < 10; // 10% true
        }
        if (fieldName.contains("actif") || fieldName.contains("valide") || fieldName.contains("visible")) {
            return rng.nextInt(100) < 85; // 85% true
        }
        return rng.nextBoolean();
    }

    private Date generateDate(String fieldName) {
        Calendar cal = new GregorianCalendar();
        if (fieldName.contains("embauche") || fieldName.contains("debut") || fieldName.contains("entree")) {
            // Hire/start dates: 2005-2022
            cal.set(2005 + rng.nextInt(18), rng.nextInt(12), 1 + rng.nextInt(28));
        } else if (fieldName.contains("construction") || fieldName.contains("fabrication")) {
            // Construction years: 1960-2020
            cal.set(1960 + rng.nextInt(61), rng.nextInt(12), 1 + rng.nextInt(28));
        } else if (fieldName.contains("inspection") || fieldName.contains("visite") || fieldName.contains("intervention")) {
            // Recent activities: 2020-2024
            cal.set(2020 + rng.nextInt(5), rng.nextInt(12), 1 + rng.nextInt(28));
        } else if (fieldName.contains("dernmodif") || fieldName.contains("datecreation") || fieldName.contains("datemaj")) {
            // Modification dates: recent
            cal.set(2022 + rng.nextInt(3), rng.nextInt(12), 1 + rng.nextInt(28));
        } else {
            // Generic dates: 2015-2024
            cal.set(2015 + rng.nextInt(10), rng.nextInt(12), 1 + rng.nextInt(28));
        }
        cal.set(Calendar.HOUR_OF_DAY, rng.nextInt(24));
        cal.set(Calendar.MINUTE, rng.nextInt(60));
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    // ── Value map support ────────────────────────────────────────────────────

    private Object pickFromValueMap(DOSchemaValueMap valueMap, String type) {
        if (valueMap.isEmpty()) {
            return generateString(""); // fallback
        }
        // Pick a random entry's "from" value (the database value)
        java.util.List<java.util.Map.Entry<String, String>> entryList = new java.util.ArrayList<>(valueMap.entrySet());
        int idx = rng.nextInt(entryList.size());
        String fromValue = entryList.get(idx).getKey();

        // Cast to the field's type
        String lower = type.toLowerCase();
        try {
            if (lower.equals("int") || lower.equals("java.lang.integer")) {
                return Integer.parseInt(fromValue);
            }
            if (lower.equals("double") || lower.equals("java.lang.double")) {
                return Double.parseDouble(fromValue);
            }
            if (lower.equals("boolean") || lower.equals("java.lang.boolean")) {
                return Boolean.parseBoolean(fromValue);
            }
        } catch (NumberFormatException e) {
            // Fall through to return as string
        }
        return fromValue;
    }
}
