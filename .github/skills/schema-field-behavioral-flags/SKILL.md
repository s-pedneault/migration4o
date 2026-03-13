---
name: schema-field-behavioral-flags
description: Check the behavioral flags on a DOSchemaField — whether it is exported, a collection, virtual, shared, or whether its referenced contents should be embedded inline. Use this skill when routing export logic based on field type.
---

# DOSchemaField — Behavioral Flags

Three are direct boolean fields; two are methods with logic.

## Field / method map

| What you want | How to get it |
|---|---|
| Is field included in export? | `field.isExported` (boolean field) |
| Is field a collection? | `field.isCollection` (boolean field) |
| Is field virtual (criteria-query)? | `field.isVirtualField()` → `source.startsWith("@")` |
| Is field a shared-definition reference? | `field.isSharedField()` → `definitionId != null && !blank` |
| Inline referenced object fields? | `field.embedContents` (boolean field) |

## Routing logic in export engine

```java
if (!field.isExported) {
    continue; // silently skip
}

if (field.isVirtualField()) {
    // query DB using field.criterias / field.criteriasOperator
} else if (field.isCollection) {
    // extract collection, iterate using field.childrenSchemaClass
} else {
    // read scalar StoredField value from field.source
}
```

## embedContents — inline vs scalar reference

```java
// Applies to both IDEntite fields and collection items
if (field.embedContents) {
    // write all child/referenced fields inline as nested XML
} else {
    // write only the numeric mID as a scalar value
}
```

## Resolving a shared field before use

```java
if (field.isSharedField()) {
    DOSchemaField definition = schema.sharedFields.get(field.definitionId);
    if (definition != null) {
        // use 'definition' for type, skipWhen, format, valueMap, etc.
    }
}
```

## Key files
- `src/main/java/migration4o/models/schema/DOSchemaField.java`
