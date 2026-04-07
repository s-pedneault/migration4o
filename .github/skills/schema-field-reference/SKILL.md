---
name: schema-field-reference
description: Read the explicit pointsTo hint on a DOSchemaField to determine the target entity class for an IDEntite reference field. Use this skill when resolving foreign-key reference targets during export.
---

# DOSchemaField — Reference Configuration

`field.pointsTo` is an explicit override for IDEntite fields where the target class cannot be inferred from the field type name by convention.

## Field map

| What you want | How to get it |
|---|---|
| Explicit target class name | `field.attributes.pointsTo` — fully-qualified FQN, or null |

## Resolving the target entity class

```java
// 1. Prefer explicit override
String targetFQN = field.attributes.pointsTo;

// 2. Fall back to type-name convention when not set
if (targetFQN == null) {
    targetFQN = TypeUtil.resolveIDEntiteTarget(field.attributes.type, schema);
}

// 3. Resolve to schema class
DOSchemaClass targetClass = field.schema.findClassByName(targetFQN);
if (targetClass != null && field.attributes.embedContents) {
    // inline all fields of the target entity
}
```

## Notes
- `field.attributes.pointsTo` is set only when the target cannot be inferred from `field.attributes.type` by naming convention.
- Distinct from `DOSchemaClass.attributes.pointsTo` which serves the same role at the class level.
- When `field.attributes.embedContents == false`, only the numeric mID is written to XML and `pointsTo` is irrelevant for export.

## Key files
- `src/main/java/migration4o/models/schema/DOSchemaField.java`
- `src/main/java/migration4o/util/TypeUtil.java`
- `src/main/java/migration4o/util/SchemaUtil.java`
