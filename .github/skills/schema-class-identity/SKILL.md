---
name: schema-class-identity
description: Read or set identity and metadata properties on a DOSchemaClass (source name, package, destination name, parent class name, title, description, summary, schema notes, pointsTo). Use this skill when working with class configuration properties.
---

# DOSchemaClass — Identity & Metadata

All identity properties are **public fields** on `DOSchemaClass` except the two utility-backed getters.

## Field / method map

| What you want | How to get it |
|---|---|
| Simple class name (e.g. `Vehicule`) | `schemaClass.getSourceName()` → `ClassUtil.getSimpleName(source)` |
| Package only (e.g. `gest.trans`) | `schemaClass.getSourcePackage()` → `ClassUtil.getPackageName(source)` |
| Fully-qualified DB name | `schemaClass.source` |
| XML element tag name | `schemaClass.destinationName` |
| Declared parent class name | `schemaClass.parentClassName` (null/empty for root classes) |
| Human-readable label | `schemaClass.title` |
| Extended semantic description | `schemaClass.description` |
| Brief overview line | `schemaClass.summary` |
| Internal maintainer notes | `schemaClass.schemaNotes` (not exported to XML) |
| IDEntite target class name | `schemaClass.pointsTo` (only set on IDEntite classes) |

## Examples

```java
// Label shown in UI — prefer title, fall back to simple name
String label = schemaClass.title != null ? schemaClass.title : schemaClass.getSourceName();

// XML output tag
String tag = schemaClass.destinationName;

// Group classes by package
String pkg = schemaClass.getSourcePackage(); // e.g. "gest.trans"

// Check whether this is an IDEntite reference-holder
if (schemaClass.pointsTo != null) {
    // this class carries mID referencing pointsTo
}
```

## Key files
- `src/main/java/migration4o/models/schema/DOSchemaClass.java`
- `src/main/java/migration4o/util/ClassUtil.java`
