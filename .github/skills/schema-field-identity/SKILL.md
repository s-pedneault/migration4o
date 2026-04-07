---
name: schema-field-identity
description: Read identity and metadata properties on a DOSchemaField (source name, destination name, type name, title, description, parent class). Use this skill when working with the basic configuration properties of a schema field.
---

# DOSchemaField — Identity & Metadata

All identity properties live in `field.attributes` (`DOSchemaFieldAttributes`). The only direct field on `DOSchemaField` itself is `field.parentClass` and `field.childrenSchemaClass`.

## Field map

| What you want | How to get it |
|---|---|
| Raw DB field name | `field.attributes.source` — includes `@` prefix for virtual fields |
| XML element tag name | `field.attributes.destinationName` |
| Declared Java type | `field.attributes.type` — e.g. `"long"`, `"java.util.Vector"`, `"gest.gen.IDVehicule"` |
| Human-readable label | `field.attributes.title` |
| Extended semantic description | `field.attributes.description` |
| Semantic group for layout | `field.attributes.group` — e.g. `"identity"`, `"dates"`, `"status"`, `"text"` |
| Owning class | `field.parentClass` — set by `DOSchemaClass.setFields()` |

## Examples

```java
// Write XML tag
writer.writeField(field.attributes.destinationName, value);

// Read from DB4O StoredField — use source only for non-virtual fields
if (!field.isVirtualField()) {
    StoredField sf = storedClass.storedField(field.attributes.source, null);
}

// Strip @ from virtual field source
String dbFieldName = field.getVirtualFieldName(); // removes @ prefix

// Error reporting with class context
String location = field.parentClass.getSourceName() + "." + field.attributes.source;
```

## Notes
- `field.attributes.source` for virtual fields starts with `@`; always call `field.getVirtualFieldName()` before DB access.
- `field.attributes.source` for method-call fields ends with `()`; call `field.getMethodCallName()` to strip the suffix.
- `field.parentClass` may be null for shared field definitions in `DOSchema.sharedFields` until they are instantiated into a class via `copy()`.
- `field.attributes.type` is the raw declared type string — resolve it with `TypeUtil` for classification.

## Key files
- `src/main/java/migration4o/models/schema/DOSchemaField.java`
- `src/main/java/migration4o/models/schema/DOSchemaFieldAttributes.java`
- `src/main/java/migration4o/util/TypeUtil.java`
