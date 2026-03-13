---
name: schema-class-export-metadata
description: Produce a StructuredWriterMetadata instance for a DOSchemaClass within a module, used to write the XML file header. Use this skill when opening an XML output file for a class during export.
---

# DOSchemaClass — Export Metadata

`getMetadata(moduleName)` is a method on `DOSchemaClass` that returns a `StructuredWriterMetadata` object written into the XML file header.

## Method map

| What you want | How to get it |
|---|---|
| Metadata for XML file header | `schemaClass.getMetadata(moduleName)` |

## What the returned object contains

```
metadata.generator = "Migration4o"
metadata.provider  = "Gestion Technologies"
metadata.module    = moduleName  (empty string if null)
metadata.type      = schemaClass.destinationName  (falls back to getSourceName() if null)
metadata.objects   = String.valueOf(schemaClass.objectIds.length)  ("0" if objectIds is null)
```

## Example

```java
// In the export engine when opening an XML file for a class
String moduleName = doSchemaModule.name; // e.g. "Intervention"
StructuredWriterMetadata meta = schemaClass.getMetadata(moduleName);
writer.writeMetadata(meta);

// Accessing fields on the result
meta.type;     // XML type name, e.g. "Intervention"
meta.objects;  // total object count as string, e.g. "142"
meta.module;   // owning module name
```

## Notes
- `objectIds` is populated during the database scan phase before export begins. If missing, `objects` is `"0"`.
- `destinationName` is preferred over `getSourceName()` for the `type` field.
- The module name comes from `DOSchemaModule.name`, resolved via `DOModuleService`.

## Key files
- `src/main/java/migration4o/models/schema/DOSchemaClass.java`
- `src/main/java/migration4o/util/tools/structuredwriter/StructuredWriterMetadata.java`
- `src/main/java/migration4o/engine/export/XMLExportEngine.java`
