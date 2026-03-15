package migration4o.migration;

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
import migration4o.util.SchemaUtil;
import migration4o.util.TypeUtil;

/**
 * Generates a human-readable summary string for a DB4O object using the
 * {@code summary} template defined in its {@link DOSchemaClass}.
 *
 * <p>
 * Template syntax: literal text plus {@code [fieldRef]} tokens. A
 * {@code fieldRef} is a dot-separated path of <em>destination names</em> as
 * defined in the reference schema. The path may span an embedded (non-IDEntite,
 * non-collection) object one level deep.
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
 * <li>Look up {@code seg1} in the current class by {@code destinationName} →
 * get its {@code source} (real DB4O field name).</li>
 * <li>Fetch the value from the DB4O object using
 * {@link StoredClass}/{@link StoredField}.</li>
 * <li>If there are more segments, resolve the schema class for
 * {@code field.type}, then repeat from step 1 with the next object.</li>
 * <li>Convert the final value to a string; on any failure return
 * {@code ""}.</li>
 * </ol>
 */
public class SummaryGenerator {

    private static final Pattern FIELD_REF_PATTERN = Pattern.compile("\\[([^\\]]+)\\]");

    /** Prevents instantiation — all methods are static. */
    private SummaryGenerator() {
    }

    /**
     * Generates a summary string for {@code obj} using the summary template on
     * its schema class.
     *
     * @param container open DB4O container
     * @param obj the root object to read field values from
     * @param schemaClass the schema class whose {@code summary} template is
     * used
     * @param referenceSchema full reference schema for embedded-class lookups
     * @return the generated summary, or {@code null} if the class has no
     * summary template, or {@code ""} if the template is defined but all tokens
     * resolved to empty
     */
    public static String generate(ExtObjectContainer container, Object obj, DOSchemaClass schemaClass, DOSchema referenceSchema) {
        if (schemaClass == null || schemaClass.summary == null || schemaClass.summary.isEmpty()) {
            return null;
        }
        if (obj == null) {
            return null;
        }

        Matcher matcher = FIELD_REF_PATTERN.matcher(schemaClass.summary);
        StringBuilder result = new StringBuilder();
        int last = 0;

        while (matcher.find()) {
            // Append the literal text before this token
            result.append(schemaClass.summary, last, matcher.start());
            // Resolve and append the field reference
            String token = matcher.group(1); // e.g. "adresse.rue"
            result.append(resolveToken(container, obj, token, schemaClass, referenceSchema));
            last = matcher.end();
        }

        // Append any trailing literal text
        result.append(schemaClass.summary, last, schemaClass.summary.length());
        return result.toString();
    }

    // ── Resolution helpers
    // ────────────────────────────────────────────────────

    /**
     * Resolves a single {@code [token]} (already stripped of brackets). Returns
     * {@code ""} whenever the path cannot be traversed.
     *
     * <p>
     * Each segment in the dot-path is a <em>destination name</em> as defined in
     * the reference schema. It is translated to the DB4O source name via
     * {@link DOSchemaClass#findField(String)}, and the actual field value is
     * then read from the live object using
     * {@link DatabaseUtil#getStoredFieldValue}.
     */
    private static String resolveToken(ExtObjectContainer container, Object rootObj, String token, DOSchemaClass rootClass, DOSchema schema) {
        String[] parts = token.split("\\.", -1);
        Object currentObj = rootObj;
        DOSchemaClass currentClass = rootClass;

        for (int i = 0; i < parts.length; i++) {
            if (currentObj == null || currentClass == null) {
                return "";
            }

            // Find field by destinationName, walking the full inheritance chain
            DOSchemaField field = DatabaseUtil.findSchemaFieldByDestinationNameIncludingAncestors(currentClass, parts[i], schema);
            if (field == null || field.source == null || field.source.isEmpty()) {
                return "";
            }

            // Fetch the value via the shared DB4O utility (source = real field
            // name)
            currentObj = DatabaseUtil.getStoredFieldValue(container, currentObj, field.source);

            // If there are more path segments, advance the schema class
            if (i < parts.length - 1) {
                String type = field.type;
                if (type == null || type.isEmpty() || TypeUtil.isPrimitiveType(type)) {
                    return ""; // Can't go deeper into a primitive
                }
                currentClass = SchemaUtil.findClassByName(type, schema);
            }
        }

        return currentObj != null ? currentObj.toString() : "";
    }

    /**
     * Resolves a human-readable label for an IDEntite reference by finding the
     * target entity and generating its summary.
     *
     * <p>
     * Used by JS-format exports to annotate ID numbers with a readable label so
     * HTML viewers can display meaningful text instead of raw identifiers.
     *
     * @param container open DB4O container
     * @param idEntiteObj the IDEntite wrapper object
     * @param idEntiteClass schema class of the IDEntite (may carry
     * {@code pointsTo})
     * @param referenceSchema full reference schema (for summary template
     * lookup)
     * @param databaseSchema full database schema (for object-ID iteration)
     * @return the summary of the referenced entity, or {@code null} if the
     * entity cannot be resolved or has no summary template
     */
    /**
     * Resolves a human-readable label for an IDEntite reference (no caching).
     * Delegates to the caching overload with {@code null} cache maps.
     */
    public static String resolveIDEntiteLabel(ExtObjectContainer container, Object idEntiteObj, DOSchemaClass idEntiteClass, DOSchema referenceSchema, DOSchema databaseSchema) {
        return resolveIDEntiteLabel(container, idEntiteObj, idEntiteClass, referenceSchema, databaseSchema, null, null);
    }

    /**
     * Resolves a human-readable label for an IDEntite reference with optional
     * caching.
     *
     * <p>
     * Two cache levels are supported:
     * <ol>
     * <li>{@code targetCache} — maps {@code "<mID>:<expectedType>"} to the
     * resolved target entity's DB4O object ID, skipping the O(n) mID scan for
     * subsequent references to the same entity.</li>
     * <li>{@code summaryCache} — maps the target entity's DB4O object ID to its
     * generated summary string, skipping re-generation when the same entity is
     * referenced from multiple records.</li>
     * </ol>
     * Pass {@code null} for either map to disable that cache level.
     *
     * @param targetCache mID+type key → targetObjectId (may be {@code null})
     * @param summaryCache targetObjectId → label string (may be {@code null})
     */
    public static String resolveIDEntiteLabel(ExtObjectContainer container, Object idEntiteObj, DOSchemaClass idEntiteClass, DOSchema referenceSchema, DOSchema databaseSchema, Map<String, Long> targetCache, Map<Long, String> summaryCache) {
        if (container == null || idEntiteObj == null || idEntiteClass == null) {
            return null;
        }
        try {
            // Determine the expected target type
            String expectedType = idEntiteClass.pointsTo;
            if (expectedType == null || expectedType.isEmpty()) {
                expectedType = ReferenceUtil.extractExpectedTypeFromFieldName(null, idEntiteClass.source);
            }

            // ── Level 1: skip the O(n) mID scan if we already resolved this
            // mID ──
            // Activate the IDEntite wrapper to read its mID
            long idEntiteObjId = container.ext().getID(idEntiteObj);
            ObjectResolverUtil.activateObjectShallow(container, idEntiteObj, idEntiteObjId);
            Long mID = ReferenceUtil.extractMIDField(container, idEntiteObj);
            if (mID == null) {
                return null;
            }
            String cacheKey = mID + ":" + (expectedType != null ? expectedType : "");

            Long targetObjectId;
            if (targetCache != null && targetCache.containsKey(cacheKey)) {
                targetObjectId = targetCache.get(cacheKey);
            } else {
                targetObjectId = ReferenceUtil.findObjectByMID(container, mID, expectedType, databaseSchema);
                if (targetCache != null) {
                    targetCache.put(cacheKey, targetObjectId); // cache null too
                                                               // (unresolvable)
                }
            }
            if (targetObjectId == null) {
                return null;
            }

            // ── Level 2: skip summary generation if we already labelled this
            // target ──
            if (summaryCache != null && summaryCache.containsKey(targetObjectId)) {
                return summaryCache.get(targetObjectId);
            }

            // Activate and retrieve the target object
            Object targetObj = container.ext().getByID(targetObjectId);
            if (targetObj == null) {
                return null;
            }
            ObjectResolverUtil.activateObjectShallow(container, targetObj, targetObjectId);

            // Find the target schema class and generate its summary
            String targetClassName = ClassUtil.getClassName(targetObj);
            DOSchemaClass targetSchemaClass = SchemaUtil.findClassByName(targetClassName, referenceSchema);
            String label = null;
            if (targetSchemaClass != null && targetSchemaClass.summary != null && !targetSchemaClass.summary.isEmpty()) {
                label = generate(container, targetObj, targetSchemaClass, referenceSchema);
            }
            // Fallback: when no summary template exists, build a label from
            // common naming fields on the target entity (nom, prenom, titre,
            // code, libelle, description, numero).
            if ((label == null || label.isBlank()) && targetObj != null) {
                label = generateFallbackLabel(container, targetObj);
            }
            if (summaryCache != null) {
                summaryCache.put(targetObjectId, label);
            }
            return label;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Result container returned by {@link #resolveIDEntiteResult}.
     */
    public static final class IDEntiteResult {
        /** Human-readable label for the referenced entity; may be null. */
        public final String label;
        /**
         * DB4O native object ID of the resolved target entity; used to build
         * cross-reference indices.
         */
        public final Long targetObjectId;

        public IDEntiteResult(String label, Long targetObjectId) {
            this.label = label;
            this.targetObjectId = targetObjectId;
        }
    }

    /**
     * Resolves an IDEntite reference and returns both the human-readable label
     * and the target entity's native DB4O object ID in a single pass (with
     * caching).
     *
     * @return an {@link IDEntiteResult} with the resolved label (possibly null)
     * and the target's DB4O object ID (possibly null if unresolvable)
     */
    public static IDEntiteResult resolveIDEntiteResult(ExtObjectContainer container, Object idEntiteObj,
            DOSchemaClass idEntiteClass, DOSchema referenceSchema, DOSchema databaseSchema,
            Map<String, Long> targetCache, Map<Long, String> summaryCache) {
        if (container == null || idEntiteObj == null || idEntiteClass == null) {
            return new IDEntiteResult(null, null);
        }
        try {
            String expectedType = idEntiteClass.pointsTo;
            if (expectedType == null || expectedType.isEmpty()) {
                expectedType = ReferenceUtil.extractExpectedTypeFromFieldName(null, idEntiteClass.source);
            }

            long idEntiteObjId = container.ext().getID(idEntiteObj);
            ObjectResolverUtil.activateObjectShallow(container, idEntiteObj, idEntiteObjId);
            Long mID = ReferenceUtil.extractMIDField(container, idEntiteObj);
            if (mID == null) {
                return new IDEntiteResult(null, null);
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
                return new IDEntiteResult(null, null);
            }

            if (summaryCache != null && summaryCache.containsKey(targetObjectId)) {
                return new IDEntiteResult(summaryCache.get(targetObjectId), targetObjectId);
            }

            Object targetObj = container.ext().getByID(targetObjectId);
            if (targetObj == null) {
                return new IDEntiteResult(null, targetObjectId);
            }
            ObjectResolverUtil.activateObjectShallow(container, targetObj, targetObjectId);

            String targetClassName = ClassUtil.getClassName(targetObj);
            DOSchemaClass targetSchemaClass = SchemaUtil.findClassByName(targetClassName, referenceSchema);
            String label = null;
            if (targetSchemaClass != null && targetSchemaClass.summary != null && !targetSchemaClass.summary.isEmpty()) {
                label = generate(container, targetObj, targetSchemaClass, referenceSchema);
            }
            if ((label == null || label.isBlank()) && targetObj != null) {
                label = generateFallbackLabel(container, targetObj);
            }
            if (summaryCache != null) {
                summaryCache.put(targetObjectId, label);
            }
            return new IDEntiteResult(label, targetObjectId);
        } catch (Exception e) {
            return new IDEntiteResult(null, null);
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
     * Builds a human-readable label by reading common naming fields directly
     * from the DB4O object. Returns {@code null} if no usable value is found.
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
