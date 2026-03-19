package migration4o.migration;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.db4o.ext.ExtObjectContainer;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.util.ClassUtil;
import migration4o.util.DatabaseUtil;
import migration4o.util.ObjectResolverUtil;
import migration4o.util.ReferenceUtil;
import migration4o.util.TypeUtil;

/**
 * Generates a human-readable summary string for a DB4O object using the {@code summary} template defined in its {@link DOSchemaClass}.
 *
 * <p>
 * Template syntax: literal text plus {@code [fieldRef]} tokens. A {@code fieldRef} is a dot-separated path of <em>destination names</em> as defined in the reference schema. The path may span an embedded (non-IDEntite, non-collection) object one level deep.
 *
 * <p>
 * Examples:
 * 
 * <pre>
 *   Dossier [adresse.numeroCivique] [adresse.rue], [adresse.ville]
 *   [prenom] [nom] (dossier [numeroDossier])
 * </pre>
 *
 * <p>
 * Field resolution algorithm for each token {@code seg1.seg2...}:
 * <ol>
 * <li>Look up {@code seg1} in the current class by {@code destinationName} → get its {@code source} (real DB4O field name).</li>
 * <li>Fetch the value from the DB4O object using {@link StoredClass}/{@link StoredField}.</li>
 * <li>If there are more segments, resolve the schema class for {@code field.attributes.type}, then repeat from step 1 with the next object.</li>
 * <li>Convert the final value to a string; on any failure return {@code ""}.</li>
 * </ol>
 */
public class SummaryGenerator {

    private static final Pattern FIELD_REF_PATTERN = Pattern.compile("\\[([^\\]]+)\\]");

    /**
     * Export language code ({@code "fr"} or {@code "en"}). Set once before the export starts by the format handler; read by {@link #formatValue} for locale-aware date rendering. Defaults to French.
     */
    private static volatile String exportLanguage = "fr";

    /**
     * Sets the export language used for locale-aware date formatting in summaries.
     *
     * @param language ISO language code: {@code "fr"} for French (default), {@code "en"} for English
     */
    public static void setExportLanguage(String language) {
        exportLanguage = (language != null && !language.isBlank()) ? language : "fr";
    }

    /** Prevents instantiation — all methods are static. */
    private SummaryGenerator() {
    }

    /**
     * Generates a summary string for {@code obj} using the summary template on its schema class.
     *
     * <p>
     * This overload does not support traversing IDEntite references in field paths. Use {@link #generate(ExtObjectContainer, Object, DOSchemaClass, DOSchema, DOSchema)} when IDEntite traversal is needed.
     *
     * @param container open DB4O container
     * @param obj the root object to read field values from
     * @param schemaClass the schema class whose {@code summary} template is used
     * @param referenceSchema full reference schema for embedded-class lookups
     * @return the generated summary, or {@code null} if the class has no summary template, or {@code ""} if the template is defined but all tokens resolved to empty
     */
    public static String generate(ExtObjectContainer container, Object obj, DOSchemaClass schemaClass, DOSchema referenceSchema) {
        return generate(container, obj, schemaClass, referenceSchema, null);
    }

    /**
     * Generates a summary string for {@code obj} using the summary template on its schema class, with support for traversing IDEntite references.
     *
     * <p>
     * When {@code databaseSchema} is provided, field paths that pass through an IDEntite reference (e.g. {@code idDossierAdresse.adresse.rue}) are resolved by following the IDEntite to its target entity in the database.
     *
     * @param container open DB4O container
     * @param obj the root object to read field values from
     * @param schemaClass the schema class whose {@code summary} template is used
     * @param referenceSchema full reference schema for embedded-class lookups
     * @param databaseSchema database schema for IDEntite resolution (may be {@code null} to disable IDEntite traversal)
     * @return the generated summary, or {@code null} if the class has no summary template, or {@code ""} if the template is defined but all tokens resolved to empty
     */
    public static String generate(ExtObjectContainer container, Object obj, DOSchemaClass schemaClass, DOSchema referenceSchema, DOSchema databaseSchema) {
        if (schemaClass == null || schemaClass.attributes.summary == null || schemaClass.attributes.summary.isEmpty()) {
            return null;
        }
        if (obj == null) {
            return null;
        }

        Matcher matcher = FIELD_REF_PATTERN.matcher(schemaClass.attributes.summary);
        StringBuilder result = new StringBuilder();
        int last = 0;

        while (matcher.find()) {
            // Append the literal text before this token
            result.append(schemaClass.attributes.summary, last, matcher.start());
            // Resolve and append the field reference
            String token = matcher.group(1); // e.g. "adresse.rue"
            result.append(resolveToken(container, obj, token, schemaClass, referenceSchema, databaseSchema));
            last = matcher.end();
        }

        // Append any trailing literal text
        result.append(schemaClass.attributes.summary, last, schemaClass.attributes.summary.length());
        return result.toString();
    }

    // ── Resolution helpers
    // ────────────────────────────────────────────────────

    /**
     * Resolves a single {@code [token]} (already stripped of brackets). Returns {@code ""} whenever the path cannot be traversed.
     *
     * <p>
     * Each segment in the dot-path is a <em>destination name</em> as defined in the reference schema. It is translated to the DB4O source name via {@link DOSchemaClass#findField(String)}, and the actual field value is then read from the live object using {@link DatabaseUtil#getStoredFieldValue}.
     *
     * <p>
     * When {@code databaseSchema} is non-null, IDEntite segments are traversed by resolving the reference to the target entity before continuing with the remaining path segments.
     */
    /**
     * Well-known virtual field name that triggers recursive summary generation for the current entity. Must match {@code FieldSelectorPanel.SUMMARY_FIELD_NAME}.
     */
    private static final String SUMMARY_TOKEN = "sommaire";

    private static String resolveToken(ExtObjectContainer container, Object rootObj, String token, DOSchemaClass rootClass, DOSchema schema, DOSchema databaseSchema) {
        String[] parts = token.split("\\.", -1);
        Object currentObj = rootObj;
        DOSchemaClass currentClass = rootClass;

        for (int i = 0; i < parts.length; i++) {
            if (currentObj == null || currentClass == null) {
                return "";
            }

            // Virtual "sommaire" token: recursively generate this entity's
            // summary
            if (SUMMARY_TOKEN.equals(parts[i])) {
                String sub = generate(container, currentObj, currentClass, schema, databaseSchema);
                return sub != null ? sub : "";
            }

            // Find field by destinationName, walking the full inheritance chain
            DOSchemaField field = DatabaseUtil.findSchemaFieldByDestinationNameIncludingAncestors(currentClass, parts[i], schema);
            if (field == null || field.attributes.source == null || field.attributes.source.isEmpty()) {
                return "";
            }

            // Fetch the value via the shared DB4O utility (source = real field
            // name)
            currentObj = DatabaseUtil.getStoredFieldValue(container, currentObj, field.attributes.source);

            // If there are more path segments, advance the schema class
            if (i < parts.length - 1) {
                String type = field.attributes.type;
                if (type == null || type.isEmpty() || TypeUtil.isPrimitiveType(type)) {
                    return ""; // Can't go deeper into a primitive
                }
                DOSchemaClass nextClass = schema.findClassByName(type);

                // IDEntite traversal: follow the reference to the target entity
                if (nextClass != null && nextClass.isIDEntite() && databaseSchema != null && currentObj != null) {
                    String expectedType = nextClass.attributes.pointsTo;
                    if (expectedType == null || expectedType.isEmpty()) {
                        expectedType = (field.attributes.pointsTo != null && !field.attributes.pointsTo.isEmpty()) ? field.attributes.pointsTo : ReferenceUtil.extractExpectedTypeFromFieldName(null, nextClass.attributes.source);
                    }
                    Long targetObjectId = ReferenceUtil.resolveIDEntiteReference(container, currentObj, expectedType, databaseSchema);
                    if (targetObjectId == null) {
                        return "";
                    }
                    Object targetObj = container.ext().getByID(targetObjectId);
                    if (targetObj == null) {
                        return "";
                    }
                    ObjectResolverUtil.activateObjectShallow(container, targetObj, targetObjectId);
                    String targetClassName = ClassUtil.getClassName(targetObj);
                    nextClass = schema.findClassByName(targetClassName);
                    currentObj = targetObj;
                }

                currentClass = nextClass;
            }
        }

        return formatValue(currentObj);
    }

    /**
     * Maps language codes to {@link Locale} instances for date formatting.
     */
    private static Locale resolveLocale() {
        return "en".equals(exportLanguage) ? Locale.ENGLISH : Locale.FRENCH;
    }

    /**
     * Formats the final resolved value as a string. Dates are rendered in a human-friendly locale format (e.g. "18 juil. 2019, 17:13" for French, "Jul 18, 2019, 5:13 PM" for English).
     */
    private static String formatValue(Object value) {
        if (value == null)
            return "";
        if (value instanceof Date) {
            DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, resolveLocale());
            return df.format((Date) value);
        }
        return value.toString();
    }

    /**
     * Result container returned by {@link #resolveIDEntiteResult}.
     */
    public static final class IDEntiteResult {
        /** Human-readable label for the referenced entity; may be null. */
        public final String label;
        /**
         * DB4O native object ID of the resolved target entity; used to build cross-reference indices and deep-links.
         */
        public final Long targetObjectId;
        /**
         * Application-level mID extracted from the IDEntite object. Used as fallback display text when no human-readable label is available, so the viewer never exports the raw mID as a plain unlinked primitive.
         */
        public final Long mId;

        public IDEntiteResult(String label, Long targetObjectId, Long mId) {
            this.label = label;
            this.targetObjectId = targetObjectId;
            this.mId = mId;
        }
    }

    /**
     * Resolves an IDEntite reference and returns both the human-readable label and the target entity's native DB4O object ID in a single pass (with caching).
     *
     * @return an {@link IDEntiteResult} with the resolved label (possibly null) and the target's DB4O object ID (possibly null if unresolvable)
     */
    public static IDEntiteResult resolveIDEntiteResult(ExtObjectContainer container, Object idEntiteObj, DOSchemaClass idEntiteClass, DOSchema referenceSchema, DOSchema databaseSchema, Map<String, Long> targetCache, Map<Long, String> summaryCache) {
        if (container == null || idEntiteObj == null || idEntiteClass == null) {
            return new IDEntiteResult(null, null, null);
        }
        try {
            String expectedType = idEntiteClass.attributes.pointsTo;
            if (expectedType == null || expectedType.isEmpty()) {
                expectedType = ReferenceUtil.extractExpectedTypeFromFieldName(null, idEntiteClass.attributes.source);
            }

            long idEntiteObjId = container.ext().getID(idEntiteObj);
            ObjectResolverUtil.activateObjectShallow(container, idEntiteObj, idEntiteObjId);
            Long mID = ReferenceUtil.extractMIDField(container, idEntiteObj);
            if (mID == null) {
                return new IDEntiteResult(null, null, null);
            }
            String cacheKey = mID + ":" + (expectedType != null ? expectedType : "");

            Long targetObjectId;
            if (targetCache != null && targetCache.containsKey(cacheKey)) {
                targetObjectId = targetCache.get(cacheKey);
            } else {
                targetObjectId = ReferenceUtil.findObjectByMID(container, mID, expectedType, databaseSchema);
                if (targetCache != null) {
                    targetCache.put(cacheKey, targetObjectId);
                }
            }
            if (targetObjectId == null) {
                return new IDEntiteResult(null, null, mID);
            }

            if (summaryCache != null && summaryCache.containsKey(targetObjectId)) {
                return new IDEntiteResult(summaryCache.get(targetObjectId), targetObjectId, mID);
            }

            Object targetObj = container.ext().getByID(targetObjectId);
            if (targetObj == null) {
                return new IDEntiteResult(null, targetObjectId, mID);
            }
            ObjectResolverUtil.activateObjectShallow(container, targetObj, targetObjectId);

            String targetClassName = ClassUtil.getClassName(targetObj);
            DOSchemaClass targetSchemaClass = referenceSchema.findClassByName(targetClassName);
            String label = null;
            if (targetSchemaClass != null && targetSchemaClass.attributes.summary != null && !targetSchemaClass.attributes.summary.isEmpty()) {
                label = generate(container, targetObj, targetSchemaClass, referenceSchema, databaseSchema);
            }
            if ((label == null || label.isBlank()) && targetObj != null) {
                label = generateFallbackLabel(container, targetObj);
            }
            if (summaryCache != null) {
                summaryCache.put(targetObjectId, label);
            }
            return new IDEntiteResult(label, targetObjectId, mID);
        } catch (Exception e) {
            return new IDEntiteResult(null, null, null);
        }
    }

    // ── Fallback label generation
    // ─────────────────────────────────────────────

    /** Common DB4O field names tried in order for fallback label generation. */
    private static final String[][] FALLBACK_FIELD_GROUPS = { { "mNom", "mPrenom" }, // person-like:
                                                                                     // "Dupont
                                                                                     // Jean"
            { "mTitre" }, // titled entities
            { "mLibelle" }, // lookup/param tables
            { "mCode" }, // code-based entities
            { "mDescription" }, // descriptive entities
            { "mNumero" }, // numbered entities
            { "mNomFichier" }, // file/document entities
    };

    /**
     * Builds a human-readable label by reading common naming fields directly from the DB4O object. Returns {@code null} if no usable value is found.
     */
    private static String generateFallbackLabel(ExtObjectContainer container, Object obj) {
        if (obj == null) {
            return null;
        }
        // Try each field group; return the first that yields a non-blank result
        for (String[] group : FALLBACK_FIELD_GROUPS) {
            StringBuilder sb = new StringBuilder();
            for (String fieldName : group) {
                Object val = DatabaseUtil.getStoredFieldValue(container, obj, fieldName);
                if (val != null) {
                    String s = val.toString().trim();
                    if (!s.isEmpty()) {
                        if (sb.length() > 0)
                            sb.append(' ');
                        sb.append(s);
                    }
                }
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        }
        return null;
    }
}
