package migration4o.migration.xsd;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.schema.DOSchemaService;

/**
 * Shared context for XSD generation, providing schema navigation utilities.
 * <p>
 * The XSD is generated from the full reference schema — no observation-based
 * registration is needed. This class provides helper methods used by
 * {@link XSDSchemaWriter}, {@link XSDClassWriter}, and {@link XSDFieldWriter}.
 */
class XSDContext {

    // ── Reference schema accessor ──────────────────────────────────────────

    /** Returns the reference schema (cached singleton). */
    DOSchema getReferenceSchema() {
        return DOSchemaService.getInstance().getReferenceSchema();
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
            String parentName = current.attributes.parentClassName;
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
                    if (f.attributes.isExported) {
                        result.put(f.attributes.destinationName, f);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Returns only the fields declared directly on this class that are not
     * already defined in any ancestor class. Used for xs:extension where
     * inherited fields come from the parent type — re-emitting them would
     * violate the XSD Unique Particle Attribution (UPA) constraint.
     */
    Map<String, DOSchemaField> getOwnExportedFields(DOSchemaClass schemaClass) {
        // Collect all ancestor field destinationNames so we can exclude them
        Set<String> ancestorFieldNames = new HashSet<>();
        DOSchema schema = getReferenceSchema();
        String parentName = schemaClass.attributes.parentClassName;
        while (parentName != null && !parentName.isEmpty()) {
            DOSchemaClass parent = schema.findClassByName(parentName);
            if (parent == null)
                break;
            if (parent.fields != null) {
                for (DOSchemaField f : parent.fields) {
                    if (f.attributes.isExported) {
                        ancestorFieldNames.add(f.attributes.destinationName);
                    }
                }
            }
            parentName = parent.attributes.parentClassName;
        }

        Map<String, DOSchemaField> result = new LinkedHashMap<>();
        if (schemaClass.fields != null) {
            for (DOSchemaField f : schemaClass.fields) {
                if (f.attributes.isExported && !ancestorFieldNames.contains(f.attributes.destinationName)) {
                    result.put(f.attributes.destinationName, f);
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
        String targetName = schemaClass.attributes.source;
        for (DOSchemaClass c : schema.getClasses()) {
            if (targetName.equals(c.attributes.parentClassName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns all exported descendant classes (direct and transitive) of the
     * given class. Uses BFS over parentClassName to find all descendants.
     */
    List<DOSchemaClass> getAllExportedDescendants(DOSchemaClass schemaClass) {
        DOSchema schema = getReferenceSchema();
        List<DOSchemaClass> descendants = new ArrayList<>();
        List<String> queue = new ArrayList<>();
        queue.add(schemaClass.attributes.source);

        int idx = 0;
        while (idx < queue.size()) {
            String parentName = queue.get(idx++);
            for (DOSchemaClass c : schema.getClasses()) {
                if (parentName.equals(c.attributes.parentClassName)) {
                    if (c.attributes.migrate) {
                        descendants.add(c);
                    }
                    queue.add(c.attributes.source);
                }
            }
        }
        return descendants;
    }

    /**
     * Checks whether a schema class has an exported parent class that uses
     * xs:extension. Returns the parent class if it's exported, null otherwise.
     */
    DOSchemaClass getExportedParent(DOSchemaClass schemaClass) {
        if (schemaClass.attributes.parentClassName == null || schemaClass.attributes.parentClassName.isEmpty()) {
            return null;
        }
        DOSchema schema = getReferenceSchema();
        DOSchemaClass parent = schema.findClassByName(schemaClass.attributes.parentClassName);
        if (parent != null && parent.attributes.migrate) {
            return parent;
        }
        return null;
    }
}
