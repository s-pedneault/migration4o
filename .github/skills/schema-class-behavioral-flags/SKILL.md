---
name: schema-class-behavioral-flags
description: Check whether a DOSchemaClass is included in the export (migrate flag) or is a Java primitive type. Use this skill when deciding whether to skip or traverse a class during export.
---

# DOSchemaClass — Behavioral Flags

## Field / method map

| What you want | How to get it |
|---|---|
| Is the class included in the export? | `schemaClass.migrate` (boolean field) |
| Is the class a Java primitive/wrapper type? | `schemaClass.isPrimitive()` → `TypeUtil.isPrimitiveType(source)` |

## Examples

```java
// Skip classes excluded from export
if (!schemaClass.migrate) {
    continue;
}

// Avoid traversing primitive types as persistent DB4O objects
if (schemaClass.isPrimitive()) {
    return; // e.g. java.lang.String, long, Integer, Date…
}

// Typical guard at export entry point
if (schemaClass.migrate && !schemaClass.isPrimitive()) {
    // proceed with object traversal
}
```

## Notes
- `migrate` is set from the `migrate` XML attribute in `schema/reference-schema.xml`.
- `TypeUtil.isPrimitiveType` checks a fixed list covering Java primitives and standard wrappers (`String`, `Integer`, `Long`, `Boolean`, `Date`, `Double`, `Float`, `Short`, `Byte`, `Character`, `BigDecimal`, `BigInteger`).

## Key files
- `src/main/java/migration4o/models/schema/DOSchemaClass.java`
- `src/main/java/migration4o/util/TypeUtil.java`
