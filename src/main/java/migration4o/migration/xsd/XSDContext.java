package migration4o.migration.xsd;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.schema.DOSchemaService;

/**
 * Mutable shared state accumulated during export and consumed by XSD writers.
 * <p>
 * During the export phase, {@link XSDBuilder} populates this context via the
 * {@code register*()} methods. During the write phase, {@link XSDSchemaWriter}
 * and {@link XSDFieldWriter} read and extend the maps as they discover
 * additional referenced types.
 */
class XSDContext {

    /** Classes registered for XSD generation, keyed by source name. */
    final Map<String, DOSchemaClass> classMap = new LinkedHashMap<>();

    /** Exported fields per class (source name → destination name → field). */
    final Map<String, Map<String, DOSchemaField>> fieldsByClass = new LinkedHashMap<>();

    /** Destination names of classes that appear as top-level XML elements. */
    final Set<String> topLevelObjects = new LinkedHashSet<>();

    /**
     * Destination names of types referenced as field types (discovered during
     * writing).
     */
    final Set<String> referencedTypes = new LinkedHashSet<>();

    // ── Reference schema accessor ──────────────────────────────────────────

    /** Returns the reference schema (cached singleton). */
    DOSchema getReferenceSchema() {
        return DOSchemaService.getInstance().getReferenceSchema();
    }

    // ── Registration methods (called during export phase) ──────────────────

    void registerClass(DOSchemaClass schemaClass) {
        if (schemaClass == null)
            return;
        String absName = schemaClass.source;
        if (!classMap.containsKey(absName)) {
            DOSchemaClass refClass = getReferenceSchema().findClassByName(absName);
            if (refClass != null) {
                classMap.put(absName, refClass);
            }
        }
    }

    void registerTopLevelObject(String destName, DOSchemaClass schemaClass) {
        if (schemaClass != null) {
            DOSchemaClass refClass = getReferenceSchema().findClassByName(schemaClass.source);
            if (refClass != null) {
                topLevelObjects.add(refClass.destinationName);
                classMap.put(refClass.source, refClass);
            }
        }
    }

    void registerField(DOSchemaClass parentClass, DOSchemaField field) {
        if (field == null || parentClass == null)
            return;

        DOSchemaClass refClass = getReferenceSchema().findClassByName(parentClass.source);
        if (refClass == null)
            return;

        // Look up the field in reference schema to get correct export
        // properties
        DOSchemaField refField = null;
        if (refClass.fields != null) {
            for (DOSchemaField f : refClass.fields) {
                if (f.source.equals(field.source)) {
                    refField = f;
                    break;
                }
            }
        }

        if (refField != null && refField.isExported) {
            fieldsByClass.computeIfAbsent(refClass.source, k -> new LinkedHashMap<>()).put(refField.destinationName, refField);
        }
    }

    // ── Schema navigation utilities (used by writers) ──────────────────────

    /**
     * Returns all exported fields for a class including fields inherited from
     * ancestor classes. Ancestors are processed root-first so a child class
     * field overrides an ancestor field with the same destinationName.
     */
    Map<String, DOSchemaField> getAllExportedFieldsIncludingAncestors(DOSchemaClass schemaClass) {
        DOSchema schema = getReferenceSchema();
        // Build ancestry chain from root down to this class
        List<DOSchemaClass> chain = new ArrayList<>();
        DOSchemaClass current = schemaClass;
        while (current != null) {
            chain.add(0, current); // prepend so root is first
            String parentName = current.parentClassName;
            if (parentName == null || parentName.isEmpty())
                break;
            DOSchemaClass parent = schema.findClassByName(parentName);
            if (parent == null)
                break;
            current = parent;
        }
        // Merge fields root-first; child fields override ancestor fields with
        // same name
        Map<String, DOSchemaField> result = new LinkedHashMap<>();
        for (DOSchemaClass cls : chain) {
            if (cls.fields != null) {
                for (DOSchemaField f : cls.fields) {
                    if (f.isExported) {
                        result.put(f.destinationName, f);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Checks whether a schema class has any subclass in the reference schema.
     */
    boolean hasAnySubclass(DOSchemaClass schemaClass) {
        if (schemaClass == null)
            return false;
        DOSchema schema = getReferenceSchema();
        String targetName = schemaClass.source;
        for (DOSchemaClass c : schema.getClasses()) {
            if (targetName.equals(c.parentClassName)) {
                return true;
            }
        }
        return false;
    }
}
