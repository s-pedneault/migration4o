package migration4o.migration.recipes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.StoredClass;
import com.db4o.ext.StoredField;
import com.db4o.reflect.generic.GenericObject;

import migration4o.models.schema.DOFieldCriteria;
import migration4o.models.schema.DOSchemaField;
import migration4o.util.DatabaseUtil;

/**
 * In-memory query engine for virtual schema fields.
 * <p>
 * Virtual fields (source starts with {@code @}) are defined in the reference
 * schema but have no physical counterpart in the DB4O database. Their values
 * are computed at export time by querying all objects of a target class and
 * filtering by criteria (match/with/operator). Results are cached per target
 * class so the DB4O scan happens only once per session.
 */
public class VirtualFieldQueryEngine {

    /**
     * Per-class cache of preloaded objects — loaded once per target class,
     * reused across all exports.
     */
    private final Map<String, List<GenericObject>> preloadedObjectsByClass = new HashMap<>();

    /**
     * Executes a criteria-based query for a virtual field.
     *
     * @param container DB4O container
     * @param obj Current object being exported (supplies the "this.*" match
     * values)
     * @param schemaField Virtual field definition with criterias
     * @return matching objects (never null, may be empty)
     */
    public Collection<?> execute(ExtObjectContainer container, GenericObject obj, DOSchemaField schemaField) {
        List<Object> results = new ArrayList<>();

        String targetClassName = schemaField.type;
        if (targetClassName == null || targetClassName.isEmpty()) {
            return results;
        }

        // Preload all objects of this class if not already cached
        if (!preloadedObjectsByClass.containsKey(targetClassName)) {
            preloadedObjectsByClass.put(targetClassName, preloadAll(container, targetClassName));
        }

        List<GenericObject> targetObjects = preloadedObjectsByClass.get(targetClassName);

        // Determine the logical operator for combining criteria (default: AND)
        boolean useAndLogic = !"OR".equalsIgnoreCase(schemaField.criteriasOperator);

        // Extract match values from current object for all criteria
        List<CriterionMatch> criteriaData = extractCriteriaValues(container, obj, schemaField, useAndLogic);
        if (criteriaData == null) {
            return results; // null means AND-logic short-circuit (null match
                            // value)
        }
        if (criteriaData.isEmpty()) {
            return results;
        }

        // Search through preloaded objects in memory
        for (GenericObject targetObj : targetObjects) {
            try {
                if (matches(container, targetObj, criteriaData, useAndLogic)) {
                    results.add(targetObj);
                }
            } catch (Exception e) {
                // Skip objects that cause errors
            }
        }

        return results;
    }

    // ── Private helpers ─────────────────────────────────────────────────

    private List<GenericObject> preloadAll(ExtObjectContainer container, String className) {
        List<GenericObject> allObjects = new ArrayList<>();
        StoredClass storedClass = container.ext().storedClass(className);
        if (storedClass == null) {
            return allObjects;
        }
        long[] objectIds = storedClass.getIDs();
        for (long objectId : objectIds) {
            try {
                Object loadedObj = container.ext().getByID(objectId);
                if (loadedObj instanceof GenericObject) {
                    // Activate to depth 2 so nested fields (e.g.
                    // mIDIntervention.mID) are accessible
                    container.activate(loadedObj, 2);
                    allObjects.add((GenericObject) loadedObj);
                }
            } catch (Exception e) {
                // Skip objects that fail to load
            }
        }
        return allObjects;
    }

    /**
     * Extracts match values from the current object for each criterion.
     *
     * @return list of criterion matches, or {@code null} if AND-logic
     * encountered a null match value
     */
    private List<CriterionMatch> extractCriteriaValues(ExtObjectContainer container, GenericObject obj, DOSchemaField schemaField, boolean useAndLogic) {
        List<CriterionMatch> data = new ArrayList<>();
        StoredClass storedClass = container.ext().storedClass(obj);
        if (storedClass == null) {
            return data;
        }

        for (DOFieldCriteria criterion : schemaField.criterias) {
            try {
                String matchFieldName = criterion.match;
                if (matchFieldName.startsWith("this.")) {
                    matchFieldName = matchFieldName.substring(5);
                }

                StoredField matchField = storedClass.storedField(matchFieldName, null);
                if (matchField == null) {
                    continue;
                }

                Object matchValue = matchField.get(obj);
                if (matchValue == null && useAndLogic) {
                    return null; // AND logic with null value → no matches
                                 // possible
                }
                if (matchValue == null) {
                    continue; // OR logic — skip this criterion
                }

                data.add(new CriterionMatch(criterion, matchValue));
            } catch (Exception e) {
                // Skip criteria that fail
            }
        }
        return data;
    }

    private boolean matches(ExtObjectContainer container, GenericObject targetObj, List<CriterionMatch> criteriaData, boolean useAndLogic) {
        boolean matchesAll = true;
        boolean matchesAny = false;

        for (CriterionMatch data : criteriaData) {
            Object withValue = DatabaseUtil.getFieldValueByPath(container, targetObj, data.criterion.with);
            if (withValue == null) {
                matchesAll = false;
                if (useAndLogic)
                    break;
                continue;
            }

            boolean hit = compare(data.matchValue, withValue, data.criterion.operator);
            if (hit) {
                matchesAny = true;
            } else {
                matchesAll = false;
                if (useAndLogic)
                    break;
            }
        }

        return useAndLogic ? matchesAll : matchesAny;
    }

    // ── Value comparison ────────────────────────────────────────────────

    private static boolean compare(Object matchValue, Object withValue, String operator) {
        if (matchValue == null || withValue == null) {
            return "equals".equals(operator) ? (matchValue == withValue) : (matchValue != withValue);
        }

        return switch (operator.toLowerCase()) {
        case "equals" -> matchValue.equals(withValue);
        case "notequals" -> !matchValue.equals(withValue);
        case "greaterthan" -> compareNumeric(matchValue, withValue) > 0;
        case "lessthan" -> compareNumeric(matchValue, withValue) < 0;
        case "greaterorequal" -> compareNumeric(matchValue, withValue) >= 0;
        case "lessorequal" -> compareNumeric(matchValue, withValue) <= 0;
        default -> matchValue.equals(withValue);
        };
    }

    @SuppressWarnings("unchecked")
    private static int compareNumeric(Object val1, Object val2) {
        try {
            if (val1 instanceof Number && val2 instanceof Number) {
                return Double.compare(((Number) val1).doubleValue(), ((Number) val2).doubleValue());
            }
            if (val1 instanceof Comparable && val2 instanceof Comparable && val1.getClass().equals(val2.getClass())) {
                return ((Comparable<Object>) val1).compareTo(val2);
            }
            return Double.compare(Double.parseDouble(val1.toString()), Double.parseDouble(val2.toString()));
        } catch (Exception e) {
            return val1.toString().compareTo(val2.toString());
        }
    }

    // ── Inner record ────────────────────────────────────────────────────

    private static class CriterionMatch {
        final DOFieldCriteria criterion;
        final Object matchValue;

        CriterionMatch(DOFieldCriteria criterion, Object matchValue) {
            this.criterion = criterion;
            this.matchValue = matchValue;
        }
    }
}
