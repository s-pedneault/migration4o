---
name: export-field-loop
description: Write, extend, or debug export logic — iterating over schema fields, writing XML output, routing by field type, applying skip conditions, formatting values. Use this skill when adding a new export feature, modifying how a class or field is exported, or understanding how the export engine works.
---

# Export Field Loop — Architecture & Patterns

## Engine layers (outermost → innermost)

```
MigrationExportService
  └─ ExportModuleLoop          iterates modules → classes → FormatHandlers
       └─ ObjectExportLoop     iterates objectIds for one class/format pair
            └─ ObjectExporter  activates + routes one DB4O object
                 └─ FieldExporter  writes all fields of one object
```

**Key context object**: `ExportCurrentState ctx` flows through all layers and carries:
- `ctx.request.referenceSchema` — the `DOSchema` reference schema
- `ctx.request.database` — the `DODatabase` (multi-delegate DB4O container)
- `ctx.delegate` — current `DODatabaseDelegate` (switch this when changing DB source)
- `ctx.schemaClass` — current `DOSchemaClass` being exported
- `ctx.exportConfig` — current `ClassExportConfig` (user's per-class export config)
- `ctx.basePath` — root output directory
- `ctx.statistics` — export statistics accumulator

---

## Accessing schema properties — CRITICAL

All data properties are behind `.attributes`:

| Object | Access pattern |
|---|---|
| Field source (DB) name | `field.attributes.source` |
| Field XML tag | `field.attributes.destinationName` |
| Field type | `field.attributes.type` |
| Field is exported | `field.attributes.isExported` |
| Field is collection | `field.attributes.isCollection` |
| Field embed contents | `field.attributes.embedContents` |
| Field children type | `field.attributes.childrenType` |
| Field skip conditions | `field.attributes.skipWhen` |
| Field value map | `field.attributes.valueMap` |
| Class FQN | `schemaClass.attributes.source` |
| Class XML tag | `schemaClass.attributes.destinationName` |
| Class is exported | `schemaClass.attributes.migrate` |
| Class parent FQN | `schemaClass.attributes.parentClassName` |

Behavioral flags / type tests are methods **on the object itself**:
- `field.isVirtualField()`, `field.isMethodCallField()`, `field.isSharedField()`
- `schemaClass.isPrimitive()`, `schemaClass.isCollection()`, `schemaClass.isCollectionOrMap()`
- `schemaClass.isIDEntite()`, `schemaClass.isEntite()`, `schemaClass.isParam()`
- `schemaClass.isDescendantOf(fqn)`

---

## Finding a schema field for a DB4O StoredField

**Always use the ancestor-traversing lookup** — schema fields may be declared on a parent class, not the concrete class:

```java
DOSchemaField schemaField = DatabaseUtil.findSchemaFieldByNameIncludingAncestors(
        parentClass, storedField.getName(), schema);

if (schemaField == null) continue; // unmapped — skip silently
if (!schemaField.attributes.isExported) continue; // excluded from export
```

Do **not** use `schemaClass.findFieldBySourceName()` for live DB objects — it only searches the concrete class, missing inherited fields.

---

## Per-field routing in the export engine

```java
if (field.isVirtualField()) {
    // Run a DB4O query; see schema-field-virtual skill
    String targetFieldName = field.getVirtualFieldName(); // strips @
    // criterias: field.attributes.criterias
    // operator:  field.attributes.criteriasOperator

} else if (field.isMethodCallField()) {
    // Invoke via reflection
    String methodName = field.getMethodCallName(); // strips ()

} else if (field.attributes.isCollection) {
    // Recurse into collection items
    DOSchemaClass childClass = field.childrenSchemaClass;
    if (childClass == null && field.attributes.childrenType != null) {
        childClass = schema.findClassByName(field.attributes.childrenType);
    }
    // field.attributes.embedContents → inline vs mID-only

} else {
    // Scalar: read StoredField value from DB4O
    StoredField sf = storedClass.storedField(field.attributes.source, null);
    Object rawValue = sf != null ? sf.get(obj) : null;
}
```

---

## Skip → Format → Map → Write pipeline

Apply in this exact order before writing any field value:

```java
// 1. Skip evaluation
if (ValueUtil.shouldSkipField(rawValue, schemaField, schema)) {
    continue;
}

// 2. Format (TRIM, UPPERCASE, LOWERCASE, or custom formatter)
FormatterContext fmtCtx = new FormatterContext(ctx.basePath, ctx.schemaClass, schemaField, obj);
String stringValue = ValueUtil.formatFieldValue(ctx.delegate, fmtCtx, rawValue, schemaField);

// 3. Value mapping
stringValue = FieldValueMapper.applyMapping(stringValue, schemaField);

// 4. Write
writer.elementWithContent(schemaField.attributes.destinationName, null, stringValue, true);
```

---

## Resolving the reference schema

```java
DOSchema schema = DOSchemaService.getInstance().getReferenceSchema();
// OR from ExportCurrentState:
DOSchema schema = ctx.request.referenceSchema;
// OR directly on any schema object:
schema.findClassByName("gest.intervention.Intervention");
```

`SchemaUtil.findClassByName()` no longer exists — use `schema.findClassByName()` directly.

---

## FormatHandler — adding a new export format

Extend `FormatHandler` and override:
- `extension()` — output file extension, e.g. `".xml"`
- `displayName()` — label, e.g. `"XML"`
- `createWriter(Path)` — instantiate your `StructuredWriter`
- `open(ExportCurrentState)` — write file header
- `close(ExportCurrentState)` — write file footer
- `folderName()` — optional, output sub-directory name

The engine calls `open → ObjectExportLoop.run → close` for every class.

---

## Related specialized skills

Load these for deeper details on the specific sub-topic:

| Topic | Skill |
|---|---|
| `field.attributes.*` properties | `schema-field-identity`, `schema-field-behavioral-flags` |
| Virtual field criteria queries | `schema-field-virtual` |
| Collection children resolution | `schema-field-collection` |
| Skip condition keywords | `schema-field-skip` |
| Format transformation pipeline | `schema-field-format` |
| Value map translation | `schema-field-value-mapping` |
| IDEntite pointsTo resolution | `schema-field-reference` |
| Shared field definitions | `schema-field-shared` |
| Class type classification | `schema-class-inheritance`, `schema-class-behavioral-flags` |
| Class navigation (ancestors, subclasses) | `schema-class-navigation` |
| XML file header metadata | `schema-class-export-metadata` |

## Key files
- `src/main/java/migration4o/migration/FieldExporter.java`
- `src/main/java/migration4o/migration/ObjectExporter.java`
- `src/main/java/migration4o/migration/tasks/ExportModuleLoop.java`
- `src/main/java/migration4o/migration/tasks/ObjectExportLoop.java`
- `src/main/java/migration4o/migration/format/FormatHandler.java`
- `src/main/java/migration4o/migration/format/ExportCurrentState.java`
- `src/main/java/migration4o/util/DatabaseUtil.java`
- `src/main/java/migration4o/util/ValueUtil.java`
- `src/main/java/migration4o/migration/recipes/FieldValueMapper.java`
