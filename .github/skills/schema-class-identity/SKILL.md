---
name: schema-class-identity
description: Read or set identity and metadata properties on a DOSchemaClass (source name, package, destination name, parent class name, title, description, summary, schema notes, pointsTo). Use this skill when working with class configuration properties.
---

# DOSchemaClass — Identity & Metadata

All identity properties live in `schemaClass.attributes` (`DOSchemaClassAttributes`). Two getters on `DOSchemaClass` derive values from `attributes.source`.

## Field / method map

| What you want | How to get it |
|---|---|
| Simple class name (e.g. `Vehicule`) | `schemaClass.getSourceName()` → `ClassUtil.getSimpleName(attributes.source)` |
| Package only (e.g. `gest.trans`) | `schemaClass.getSourcePackage()` → `ClassUtil.getPackageName(attributes.source)` |
| Fully-qualified DB name | `schemaClass.attributes.source` |
| XML element tag name | `schemaClass.attributes.destinationName` |
| Declared parent class name | `schemaClass.attributes.parentClassName` (null/empty for root classes) |
| Human-readable label | `schemaClass.attributes.title` |
| Extended semantic description | `schemaClass.attributes.description` |
| Brief overview line | `schemaClass.attributes.summary` |
| Internal maintainer notes | `schemaClass.attributes.schemaNotes` (not exported to XML) |
| IDEntite target class name | `schemaClass.attributes.pointsTo` (only set on IDEntite classes) |
| Resolved IDEntite target class | `schemaClass.getPointsToClass()` |
| Bypass export limits flag | `schemaClass.attributes.alwaysExportAll` |

## Examples

```java
// Label shown in UI — prefer title, fall back to simple name
String label = schemaClass.attributes.title != null
        ? schemaClass.attributes.title : schemaClass.getSourceName();

// XML output tag
String tag = schemaClass.attributes.destinationName;

// Group classes by package
String pkg = schemaClass.getSourcePackage(); // e.g. "gest.trans"

// Check whether this is an IDEntite reference-holder
if (schemaClass.attributes.pointsTo != null) {
    // this class carries mID referencing pointsTo
    DOSchemaClass target = schemaClass.getPointsToClass(); // fast convenience method
}
```

## Key files
- `src/main/java/migration4o/models/schema/DOSchemaClass.java`
- `src/main/java/migration4o/models/schema/DOSchemaClassAttributes.java`
- `src/main/java/migration4o/util/ClassUtil.java`
