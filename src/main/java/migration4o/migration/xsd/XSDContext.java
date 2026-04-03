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
 * The XSD is generated from the full reference schema — no observation-based registration is needed. This class provides helper methods used by {@link XSDSchemaWriter}, {@link XSDClassWriter}, and {@link XSDFieldWriter}.
 */
class XSDContext {

    // ── Reference schema accessor ──────────────────────────────────────────

    /** Returns the reference schema (cached singleton). */
    DOSchema getReferenceSchema() {
        return DOSchemaService.getInstance().getReferenceSchema();
    }

    // ── Schema navigation utilities (used by writers) ──────────────────────

    /**
     * Returns all exported fields for a class including fields inherited from ancestor classes. The class's own fields are loaded first; ancestor fields are only added when no field with the same source name has already been loaded, so a child's redefinition always wins without needing a post-processing override step.
     */
    Map<String, DOSchemaField> getAllExportedFieldsIncludingAncestors(DOSchemaClass schemaClass) {
        DOSchema schema = getReferenceSchema();
        // Collect the ancestry chain from this class up to the root
        List<DOSchemaClass> chain = new ArrayList<>();
        DOSchemaClass current = schemaClass;
        while (current != null) {
            chain.add(current); // child first
            String parentName = current.attributes.parentClassName;
            if (parentName == null || parentName.isEmpty())
                break;
            DOSchemaClass parent = schema.findClassByName(parentName);
            if (parent == null)
                break;
            current = parent;
        }
        // Load child fields first; putIfAbsent ensures ancestor fields never
        // override a field that the child class has already defined.
        Map<String, DOSchemaField> result = new LinkedHashMap<>();
        for (DOSchemaClass cls : chain) {
            if (cls.fields != null) {
                for (DOSchemaField f : cls.fields) {
                    if (f.attributes.isExported) {
                        result.putIfAbsent(f.attributes.source, f);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Returns only the fields declared directly on this class that are not already defined in any ancestor class. Used for xs:extension where inherited fields come from the parent type — re-emitting them would violate the XSD Unique Particle Attribution (UPA) constraint.
     */
    Map<String, DOSchemaField> getOwnExportedFields(DOSchemaClass schemaClass) {
        // Collect all ancestor field source names so we can exclude them
        Set<String> ancestorSourceNames = new HashSet<>();
        DOSchema schema = getReferenceSchema();
        String parentName = schemaClass.attributes.parentClassName;
        while (parentName != null && !parentName.isEmpty()) {
            DOSchemaClass parent = schema.findClassByName(parentName);
            if (parent == null)
                break;
            if (parent.fields != null) {
                for (DOSchemaField f : parent.fields) {
                    if (f.attributes.isExported) {
                        ancestorSourceNames.add(f.attributes.source);
                    }
                }
            }
            parentName = parent.attributes.parentClassName;
        }

        Map<String, DOSchemaField> result = new LinkedHashMap<>();
        if (schemaClass.fields != null) {
            for (DOSchemaField f : schemaClass.fields) {
                if (f.attributes.isExported && !ancestorSourceNames.contains(f.attributes.source)) {
                    result.put(f.attributes.source, f);
                }
            }
        }
        return result;
    }

    /**
     * Returns true if the class locally declares a field whose destinationName is already defined in an ancestor class AND the XSD type differs from the ancestor's XSD type. Re-using the same definition (same type via a shared field) is NOT an override and must not trigger a flat-layout fallback.
     * <p>
     * Only a genuine type change (e.g. {@code int} → {@code string}) requires falling back to flat layout, because xs:extension cannot re-declare an inherited field with a different type.
     */
    boolean hasOverrideFields(DOSchemaClass schemaClass) {
        // Collect ancestor fields: source name → XSD type string
        Map<String, String> ancestorXsdTypes = new java.util.HashMap<>();
        DOSchema schema = getReferenceSchema();
        String parentName = schemaClass.attributes.parentClassName;
        while (parentName != null && !parentName.isEmpty()) {
            DOSchemaClass parent = schema.findClassByName(parentName);
            if (parent == null)
                break;
            if (parent.fields != null) {
                for (DOSchemaField f : parent.fields) {
                    if (f.attributes.isExported && f.attributes.type != null && !f.attributes.type.isEmpty()) {
                        ancestorXsdTypes.putIfAbsent(f.attributes.source, XSDTypeMapper.getXSDType(f.attributes.type));
                    }
                }
            }
            parentName = parent.attributes.parentClassName;
        }
        if (schemaClass.fields != null) {
            for (DOSchemaField f : schemaClass.fields) {
                if (!f.attributes.isExported)
                    continue;
                String ancestorXsdType = ancestorXsdTypes.get(f.attributes.source);
                if (ancestorXsdType == null)
                    continue; // not an ancestor field — not an override
                if (f.attributes.type == null || f.attributes.type.isEmpty())
                    continue; // no type to compare — not a genuine override
                String ownXsdType = XSDTypeMapper.getXSDType(f.attributes.type);
                if (!ancestorXsdType.equals(ownXsdType)) {
                    return true; // genuine type change — requires flat layout
                }
            }
        }
        return false;
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
     * Returns all exported descendant classes (direct and transitive) of the given class. Uses BFS over parentClassName to find all descendants.
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
     * Checks whether a schema class has an exported parent class that uses xs:extension. Returns the parent class if it's exported, null otherwise.
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
