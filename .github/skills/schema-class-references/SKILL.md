---
name: schema-class-references
description: Access the schema back-references on a DOSchemaClass — the list of other classes and fields that point to this class. Use this skill when doing reverse-navigation, coverage reporting, or reachability analysis.
---

# DOSchemaClass — Schema References

Schema references are reverse-navigation links. Each `DOSchemaReference` names a **class + field** elsewhere in the schema that targets this class.

## Field map

| What you want | How to get it |
|---|---|
| All back-references to this class | `schemaClass.schemaReferences` (`DOSchemaReference[]`, may be null) |

## DOSchemaReference fields

```java
public class DOSchemaReference {
    public String className;  // FQN of the class that holds the referring field
    public String fieldName;  // field on that class that points here
}
```

## Examples

```java
// Log all back-references
if (schemaClass.schemaReferences != null) {
    for (DOSchemaReference ref : schemaClass.schemaReferences) {
        System.out.println(ref.className + "." + ref.fieldName + " → " + schemaClass.source);
    }
}

// Count references for coverage reporting
int refCount = schemaClass.schemaReferences != null
        ? schemaClass.schemaReferences.length : 0;

// Resolve the referring class and field
for (DOSchemaReference ref : schemaClass.schemaReferences) {
    DOSchemaClass referrer = SchemaUtil.findClassByName(ref.className, schema);
    if (referrer != null) {
        DOSchemaField field = referrer.findField(ref.fieldName);
        // field is the one that points to schemaClass
    }
}
```

## Notes
- Populated by `DOReferenceSchemaReader` during schema load from `schema/reference-schema.xml`.
- Drives reverse-navigation in the coverage panel and reachability analysis.
- Always null-check before iterating — not all classes have back-references.

## Key files
- `src/main/java/migration4o/models/schema/DOSchemaClass.java`
- `src/main/java/migration4o/models/schema/DOSchemaReference.java`
- `src/main/java/migration4o/schema/DOReferenceSchemaReader.java`
