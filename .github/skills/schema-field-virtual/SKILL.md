---
name: schema-field-virtual
description: Work with virtual fields on a DOSchemaField — access criteria queries, the criteria operator, and the real field name with the @ prefix stripped. Use this skill when querying the database for objects that match a virtual field's conditions.
---

# DOSchemaField — Virtual Field Configuration

A virtual field has `field.source` starting with `@`. Instead of reading a `StoredField`, the export engine runs a criteria query against the database.

## Method / field map

| What you want | How to get it |
|---|---|
| Is this field virtual? | `field.isVirtualField()` → `source.startsWith("@")` |
| DB field name (sans @) | `field.getVirtualFieldName()` → `source.substring(1)` |
| List of query conditions | `field.attributes.criterias` (`List<DOFieldCriteria>`, may be null) |
| Logic joining conditions | `field.attributes.criteriasOperator` — `"AND"` (default) or `"OR"` |

## DOFieldCriteria fields

```java
public class DOFieldCriteria {
    public String match;    // field reference in parent object, e.g. "this.mID"
    public String with;     // field name in target class, e.g. "mIDIntervention"
    public String operator; // "equals" (default), "notEquals", "greaterThan",
                            // "lessThan", "greaterOrEqual", "lessOrEqual"
}
```

## Example: evaluating a virtual field

```java
if (field.isVirtualField()) {
    String targetFieldName = field.getVirtualFieldName(); // e.g. "Notes"

    // Build a DB4O query for each criterion
    Query query = container.query();
    query.constrain(resolveClass(targetFieldName, schema));

    boolean useAnd = !"OR".equalsIgnoreCase(field.attributes.criteriasOperator);

    if (field.attributes.criterias != null) {
        for (DOFieldCriteria c : field.attributes.criterias) {
            Object matchValue = resolveMatchValue(c.match, parentObject);
            Constraint constraint = query.descend(c.with).constrain(matchValue);
            // apply operator — default is equals (no modifier needed)
            if ("notEquals".equals(c.operator)) constraint.not();
            // combine with AND/OR
            if (useAnd) constraint.and(previousConstraint);
            else        constraint.or(previousConstraint);
        }
    }

    List<?> results = query.execute();
    // write results as collection children
}
```

## Notes
- `field.attributes.criterias` may be null for virtual fields that have no conditions defined yet.
- Always check `field.isVirtualField()` **before** accessing `criterias` — non-virtual fields do not use this path.

## Key files
- `src/main/java/migration4o/models/schema/DOSchemaField.java`
- `src/main/java/migration4o/models/schema/DOFieldCriteria.java`
