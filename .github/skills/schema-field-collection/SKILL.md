---
name: schema-field-collection
description: Access the collection element type and resolved child schema class on a DOSchemaField. Use this skill when exporting or traversing collection fields that hold multiple child objects.
---

# DOSchemaField — Collection Configuration

Collection fields are identified by `field.attributes.isCollection == true`. Only these fields carry meaningful `childrenType` and `childrenSchemaClass` values.

## Field map

| What you want | How to get it |
|---|---|
| Declared element type name | `field.attributes.childrenType` — e.g. `"gest.intervention.Note"` |
| Resolved element schema class | `field.childrenSchemaClass` — set at schema-load time, may be null |

## Example: routing a collection field

```java
if (field.attributes.isCollection) {
    DOSchemaClass childClass = field.childrenSchemaClass;

    if (childClass == null && field.attributes.childrenType != null) {
        // Manual fallback resolution
        childClass = field.schema.findClassByName(field.attributes.childrenType);
    }

    if (childClass != null) {
        // Recurse into child class export
        for (Object childObject : collectionItems) {
            exportObject(childObject, childClass, writer, schema);
        }
    }
}
```

## embedContents applies to collections too

```java
if (field.attributes.isCollection) {
    if (field.attributes.embedContents) {
        // inline each child's fields
    } else {
        // emit only the mID of each child as a scalar reference
    }
}
```

## Notes
- `field.childrenSchemaClass` is resolved by `DOReferenceSchemaReader` during schema load. It may be null if the element type is not in the reference schema (e.g. primitive collections).
- Never assume `childrenSchemaClass` is non-null — always null-check or fall back to `field.attributes.childrenType` resolution via `schema.findClassByName()`.

## Key files
- `src/main/java/migration4o/models/schema/DOSchemaField.java`
- `src/main/java/migration4o/models/schema/DOSchemaFieldAttributes.java`
- `src/main/java/migration4o/schema/DOReferenceSchemaReader.java`
