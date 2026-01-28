# DODatabaseReaderV2 Architecture

## Overview

The `DODatabaseReaderV2` is a new database reader that directly creates `DOSchema*` classes from a DB4O database, replacing the previous two-step process with a single, more efficient operation.

## Previous Architecture (DODatabaseReader + DODatabaseSchemaInferrer)

The original design involved two steps:

```
DB4O Database (StoredClass)
        ↓
  DODatabaseReader
        ↓
  DODatabase* classes (DODatabaseClass, DODatabaseField)
        ↓
  DODatabaseSchemaInferrer
        ↓
  DOSchema* classes (DOSchemaClass, DOSchemaField)
        ↓
  UI (works exclusively with DOSchema*)
```

### Why This Was Designed

The original two-step architecture was designed to:

1. **Avoid rewriting database reading logic** - By reusing `DODatabaseReader` to read from DB4O
2. **Separate concerns** - Database representation (`DODatabase*`) vs. schema representation (`DOSchema*`)
3. **Leverage existing code** - The `DODatabaseReader` already handled DB4O's complexities

### The Problem

Since the UI now works **exclusively with `DOSchema*` classes**, the intermediary `DODatabase*` classes are unnecessary overhead:

- Extra memory allocation for `DODatabase*` objects that are immediately discarded
- Two conversion steps instead of one
- More complex code flow to understand and maintain

## New Architecture (DODatabaseReaderV2)

The new design eliminates the intermediary step:

```
DB4O Database (StoredClass)
        ↓
  DODatabaseReaderV2
        ↓
  DOSchema* classes (DOSchemaClass, DOSchemaField)
        ↓
  UI (works exclusively with DOSchema*)
```

### Key Features

1. **Direct Conversion** - Converts `StoredClass` directly to `DOSchemaClass`
2. **No Intermediaries** - No `DODatabase*` objects are created
3. **Same Functionality** - Performs all the same tasks as the old two-step process:
   - Field deduplication (prefers array versions)
   - Type normalization (e.g., `java.lang.String` → `"string"`)
   - Collection detection
   - Children type inference for collections
   - Inheritance hierarchy handling
   - Object ID deduplication across inheritance chains

## Implementation Details

### Core Methods

#### `readDatabaseAsSchema(ExtObjectContainer container)`

Main entry point that reads a database and returns a `DOSchema`:

```java
DODatabaseReaderV2 reader = new DODatabaseReaderV2();
DOSchema schema = reader.readDatabaseAsSchema(container);
```

#### `convertStoredClassToSchemaClass(StoredClass, Map, ExtObjectContainer)`

Converts a DB4O `StoredClass` directly to a `DOSchemaClass`:

- Extracts class metadata (name, parent, object count)
- Converts fields to schema fields
- Captures object IDs
- Marks all classes as `migrate=true`

#### `convertStoredFieldsToSchemaFields(StoredClass, Map, ExtObjectContainer, String)`

Converts DB4O stored fields to schema fields:

- **Deduplicates fields** with the same name (prefers array version)
- Detects collections
- Infers children types for collections
- Sets appropriate defaults for schema fields

#### `deduplicateObjectIdsInInheritanceHierarchies(DOSchema)`

Removes duplicate object IDs from parent classes:

- DB4O stores each object at every level of its inheritance chain
- This method keeps object IDs only in the most derived (leaf) class
- Prevents double-counting of objects in reports

### Type Normalization

The reader normalizes Java types to match schema conventions:

```java
java.lang.String  → "string"
java.util.Date    → "date"
java.lang.Object  → "object"
java.lang.Integer → "int"
java.lang.Long    → "long"
java.lang.Boolean → "boolean"
java.lang.Double  → "double"
java.lang.Float   → "float"
```

### Collection Handling

The reader detects and handles collections:

1. **Detection** - Uses `CollectionTypeUtil.isCollectionType()` to identify collections
2. **Children Type Inference**:
   - Arrays: Extracts component type (e.g., `String[]` → `"string"`)
   - Generics: Parses type parameters (e.g., `List<String>` → `"string"`)
   - Maps: Returns value type (e.g., `Map<String,Integer>` → `"int"`)
   - Non-generic: Defaults to `"java.lang.Object"`

### Field Deduplication

When multiple stored fields have the same name (rare but possible in DB4O):

- Keeps **array version** if one exists
- Otherwise, keeps the **first occurrence**
- Prevents duplicate field names in the schema

## Usage Examples

### Basic Usage

```java
// Open database
DODatabaseOpener opener = new DODatabaseOpener();
ExtObjectContainer container = opener.openDatabase(databasePath);

// Read database as schema directly
DODatabaseReaderV2 reader = new DODatabaseReaderV2();
DOSchema schema = reader.readDatabaseAsSchema(container);

// Use schema in UI
schemaEditorPanel.loadSchema(schema);
```

### Replacing Old Code

**Before (two-step process):**

```java
// Step 1: Read database
DODatabaseReader dbReader = new DODatabaseReader();
DODatabase database = dbReader.readDatabaseMeta(container, encoding, size, schema);

// Step 2: Infer schema
DODatabaseSchemaInferrer inferrer = new DODatabaseSchemaInferrer();
DOSchema inferredSchema = inferrer.inferSchemaFromDatabase(database);
```

**After (single-step process):**

```java
// Single step: Read database as schema
DODatabaseReaderV2 reader = new DODatabaseReaderV2();
DOSchema schema = reader.readDatabaseAsSchema(container);
```

## Benefits

### Performance

- **Reduced memory usage** - No intermediary `DODatabase*` objects
- **Faster execution** - Single conversion pass instead of two
- **Better garbage collection** - Fewer temporary objects

### Maintainability

- **Simpler code flow** - One class instead of two
- **Easier to understand** - Direct conversion path
- **Less duplication** - Single implementation of conversion logic

### Correctness

- **Same algorithms** - Uses the same logic as the old two-step process
- **Proven deduplication** - Object ID deduplication across inheritance
- **Consistent normalization** - Same type normalization rules

## Migration Guide

### When to Use DODatabaseReaderV2

Use `DODatabaseReaderV2` when:

- ✅ You need a `DOSchema` directly from a database
- ✅ Your UI works exclusively with `DOSchema*` classes
- ✅ You don't need `DODatabase*` intermediary objects
- ✅ You want optimal performance

### When to Use DODatabaseReader (Legacy)

Use `DODatabaseReader` when:

- ⚠️ You need `DODatabase*` objects for other purposes
- ⚠️ You have legacy code that depends on `DODatabase*`
- ⚠️ You need the `DOEngine` integration (which uses `DODatabase`)

### Compatibility

`DODatabaseReaderV2` is **fully compatible** with existing `DOSchema*` model classes:

- Uses the same `DOSchemaClass`, `DOSchemaField`, `DOSchemaModule`
- Produces schemas with identical structure
- Works with all existing schema utilities and resolvers

## Testing

### Unit Tests

Test the following scenarios:

1. **Basic conversion** - Simple classes convert correctly
2. **Inheritance** - Parent/child relationships are preserved
3. **Collections** - Arrays and generic collections are detected
4. **Object ID deduplication** - IDs are removed from parent classes
5. **Field deduplication** - Duplicate field names are handled
6. **Type normalization** - Java types are normalized to schema types

### Integration Tests

Test with:

1. **Real databases** - Test against actual DB4O files
2. **Complex hierarchies** - Deep inheritance chains
3. **Large databases** - Performance with many classes/objects
4. **Edge cases** - Empty databases, single class, etc.

## Future Enhancements

Potential improvements:

1. **Parallel processing** - Process classes in parallel for large databases
2. **Streaming** - Process classes one at a time to reduce memory
3. **Caching** - Cache type mappings and field conversions
4. **Configuration** - Allow custom type normalization rules
5. **Progress reporting** - Report progress for large databases

## Related Files

- **Implementation**: [DODatabaseReaderV2.java](../src/main/java/migration4o/database/DODatabaseReaderV2.java)
- **Legacy reader**: [DODatabaseReader.java](../src/main/java/migration4o/database/DODatabaseReader.java)
- **Legacy inferrer**: [DODatabaseSchemaInferrer.java](../src/main/java/migration4o/schema/DODatabaseSchemaInferrer.java)
- **Schema models**: [models/schema/](../src/main/java/migration4o/models/schema/)
- **Collection utilities**: [CollectionTypeUtil.java](../src/main/java/migration4o/util/CollectionTypeUtil.java)

## Summary

`DODatabaseReaderV2` simplifies the database reading architecture by eliminating unnecessary intermediary objects and providing a direct path from DB4O `StoredClass` to `DOSchema*` classes. This is ideal for UIs and tools that work exclusively with schema representations and don't need the database-specific `DODatabase*` classes.
