---
name: schema-field-shared
description: Work with shared field definitions on a DOSchemaField — detect a shared reference, resolve the definition from DOSchema.sharedFields, and instantiate it into a class context. Use this skill when a field delegates its configuration to a reusable shared definition.
---

# DOSchemaField — Shared Definition

A shared field has `field.definitionId` pointing into `DOSchema.sharedFields` (a `Map<String, DOSchemaField>`). The definition carries the reusable configuration; the referencing field carries only `source` and `destinationName` overrides.

## Field / method map

| What you want | How to get it |
|---|---|
| Is this a shared reference? | `field.isSharedField()` → `definitionId != null && !blank` |
| Shared definition key | `field.definitionId` |
| The definition itself | `schema.sharedFields.get(field.definitionId)` |

## Storage structure

```
DOSchema.sharedFields: Map<String, DOSchemaField>
  key   = field.source name (e.g. "mID")
  value = canonical DOSchemaField definition

DOSchemaClass.fields[i].definitionId = "mID"  → reference to the definition
```

## Resolving a shared field before use

```java
DOSchemaField effective = field;
if (field.isSharedField()) {
    DOSchemaField definition = schema.sharedFields.get(field.definitionId);
    if (definition != null) {
        effective = definition;
        // field.destinationName / field.source may still override for this instance
    }
}
// use 'effective' for type, skipWhen, format, valueMap, etc.
```

## Instantiating (deep copy) for a specific class

```java
DOSchemaField definition = schema.sharedFields.get(field.definitionId);
if (definition != null) {
    DOSchemaField instance = definition.copy();
    instance.source = field.source;     // carry original source name
    instance.definitionId = null;       // make standalone — break the shared link
    // parentClass is set later by schemaClass.setFields()
}
```

## Converting an existing field to a shared reference

```java
// SchemaUtil handles this during schema normalization
DOSchemaField refField = SchemaUtil.convertToCommonFieldIfExists(field, schema);
```

## Key files
- `src/main/java/migration4o/models/schema/DOSchemaField.java`
- `src/main/java/migration4o/models/schema/DOSchema.java`
- `src/main/java/migration4o/util/SchemaUtil.java`
