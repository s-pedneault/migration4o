---
name: schema-field-behavioral-flags
description: Check the behavioral flags on a DOSchemaField — whether it is exported, a collection, virtual, shared, or whether its referenced contents should be embedded inline. Use this skill when routing export logic based on field type.
---

# DOSchemaField — Behavioral Flags

All data properties live in `field.attributes` (`DOSchemaFieldAttributes`). The boolean flags are fields on `attributes`; virtual/shared detection are methods on `DOSchemaField` itself.

## Field / method map

| What you want | How to get it |
|---|---|
| Is field included in export? | `field.attributes.isExported` (boolean) |
| Is field a collection? | `field.attributes.isCollection` (boolean) |
| Is field virtual (criteria-query)? | `field.isVirtualField()` → `attributes.source.startsWith("@")` |
| Is field a method-call field? | `field.isMethodCallField()` → `attributes.source.endsWith("()")` |
| Is field a shared-definition reference? | `field.isSharedField()` → `attributes.definitionId != null && !blank` |
| Inline referenced object fields? | `field.attributes.embedContents` (boolean) |

## Routing logic in export engine

```java
if (!field.attributes.isExported) {
    continue; // silently skip
}

if (field.isVirtualField()) {
    // query DB using field.attributes.criterias / field.attributes.criteriasOperator
} else if (field.isMethodCallField()) {
    // invoke field.getMethodCallName() on the object via reflection
} else if (field.attributes.isCollection) {
    // extract collection, iterate using field.childrenSchemaClass
} else {
    // read scalar StoredField value from field.attributes.source
}
```

## embedContents — inline vs scalar reference

```java
// Applies to both IDEntite fields and collection items
if (field.attributes.embedContents) {
    // write all child/referenced fields inline as nested XML
} else {
    // write only the numeric mID as a scalar value
}
```

## Resolving a shared field before use

```java
if (field.isSharedField()) {
    DOSchemaField definition = field.schema.sharedFields.get(field.attributes.definitionId);
    if (definition != null) {
        // use 'definition' for type, skipWhen, format, valueMap, etc.
    }
}
```

## Key files
- `src/main/java/migration4o/models/schema/DOSchemaField.java`
- `src/main/java/migration4o/models/schema/DOSchemaFieldAttributes.java`
