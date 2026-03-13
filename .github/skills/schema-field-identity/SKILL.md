---
name: schema-field-identity
description: Read identity and metadata properties on a DOSchemaField (source name, destination name, type name, title, description, parent class). Use this skill when working with the basic configuration properties of a schema field.
---

# DOSchemaField — Identity & Metadata

All identity properties are **public fields** on `DOSchemaField`. There are no getter methods for them.

## Field map

| What you want | How to get it |
|---|---|
| Raw DB field name | `field.source` — includes `@` prefix for virtual fields |
| XML element tag name | `field.destinationName` |
| Declared Java type | `field.type` — e.g. `"long"`, `"java.util.Vector"`, `"gest.gen.IDVehicule"` |
| Human-readable label | `field.title` |
| Extended semantic description | `field.description` |
| Owning class | `field.parentClass` — set by `DOSchemaClass.setFields()` |

## Examples

```java
// Write XML tag
writer.writeField(field.destinationName, value);

// Read from DB4O StoredField — use source only for non-virtual fields
if (!field.isVirtualField()) {
    StoredField sf = storedClass.storedField(field.source, null);
}

// Strip @ from virtual field source
String dbFieldName = field.getVirtualFieldName(); // removes @ prefix

// Error reporting with class context
String location = field.parentClass.getSourceName() + "." + field.source;
```

## Notes
- `field.source` for virtual fields starts with `@`; always call `field.getVirtualFieldName()` before DB access.
- `field.parentClass` may be null for shared field definitions in `DOSchema.sharedFields` until they are instantiated into a class via `copy()`.
- `field.type` is the raw declared type string — resolve it with `TypeUtil` for classification.

## Key files
- `src/main/java/migration4o/models/schema/DOSchemaField.java`
- `src/main/java/migration4o/util/TypeUtil.java`
