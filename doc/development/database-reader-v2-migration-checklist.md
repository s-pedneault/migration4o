# Migration Checklist: From DODatabaseReader to DODatabaseReaderV2

## Overview

This checklist helps you migrate from the old two-step approach (DODatabaseReader + DODatabaseSchemaInferrer) to the new direct approach (DODatabaseReaderV2).

## Pre-Migration Checklist

- [ ] **Understand current usage** - Identify all places using DODatabaseReader
- [ ] **Understand schema usage** - Verify UI components use DOSchema* classes
- [ ] **Review dependencies** - Check if any code requires DODatabase* objects
- [ ] **Backup code** - Commit current state to version control
- [ ] **Read documentation** - Review architecture document

## Code Locations to Update

### 1. Find All Usages

Search for these patterns in your codebase:

```bash
# Find DODatabaseReader usage
grep -r "DODatabaseReader" src/

# Find DODatabaseSchemaInferrer usage
grep -r "DODatabaseSchemaInferrer" src/

# Find inferSchemaFromDatabase calls
grep -r "inferSchemaFromDatabase" src/
```

### 2. Common Patterns to Replace

#### Pattern 1: Basic Schema Inference

**OLD:**
```java
DODatabaseReader dbReader = new DODatabaseReader();
DODatabase database = dbReader.readDatabaseMeta(container, encoding, size, schema);

DODatabaseSchemaInferrer inferrer = new DODatabaseSchemaInferrer();
DOSchema inferredSchema = inferrer.inferSchemaFromDatabase(database);
```

**NEW:**
```java
DODatabaseReaderV2 reader = new DODatabaseReaderV2();
DOSchema inferredSchema = reader.readDatabaseAsSchema(container);
```

**Changes:**
- [ ] Remove DODatabaseReader instantiation
- [ ] Remove readDatabaseMeta call
- [ ] Remove DODatabase variable
- [ ] Remove DODatabaseSchemaInferrer instantiation
- [ ] Replace with single DODatabaseReaderV2 call

#### Pattern 2: In DODatabaseBuilder

**Location:** [DODatabaseBuilder.java](../src/main/java/migration4o/database/DODatabaseBuilder.java)

**OLD:**
```java
public DODatabase buildDatabase(String filePath, DOSchema schema) {
    // ... open database ...
    DODatabase database = databaseReader.readDatabaseMeta(container, encoding, databaseSize, schema);
    inheritanceResolver.resolveInheritance(database, schema);
    return database;
}
```

**NEW (if you want schema instead):**
```java
public DOSchema buildDatabaseAsSchema(String filePath) {
    ExtObjectContainer container = databaseOpener.openDatabase(filePath);
    DODatabaseReaderV2 reader = new DODatabaseReaderV2();
    return reader.readDatabaseAsSchema(container);
}
```

**Changes:**
- [ ] Add new method that returns DOSchema instead of DODatabase
- [ ] Keep old method for backward compatibility (if needed)
- [ ] Update callers to use new method

#### Pattern 3: UI Component Loading

**Example Location:** Schema editor panels

**OLD:**
```java
private void loadDatabaseAsSchema(String dbPath) {
    DODatabaseBuilder builder = new DODatabaseBuilder();
    DODatabase database = builder.buildDatabase(dbPath, null);
    
    DODatabaseSchemaInferrer inferrer = new DODatabaseSchemaInferrer();
    DOSchema schema = inferrer.inferSchemaFromDatabase(database);
    
    displaySchema(schema);
}
```

**NEW:**
```java
private void loadDatabaseAsSchema(String dbPath) {
    DODatabaseOpener opener = new DODatabaseOpener();
    ExtObjectContainer container = opener.openDatabase(dbPath);
    
    DODatabaseReaderV2 reader = new DODatabaseReaderV2();
    DOSchema schema = reader.readDatabaseAsSchema(container);
    
    displaySchema(schema);
    container.close();
}
```

**Changes:**
- [ ] Remove DODatabaseBuilder if only used for schema inference
- [ ] Remove DODatabaseSchemaInferrer
- [ ] Add DODatabaseReaderV2
- [ ] Ensure container is closed

## Step-by-Step Migration

### Step 1: Add Import

```java
import migration4o.database.DODatabaseReaderV2;
```

### Step 2: Replace Reader Instantiation

**Before:**
```java
DODatabaseReader dbReader = new DODatabaseReader();
DODatabaseSchemaInferrer inferrer = new DODatabaseSchemaInferrer();
```

**After:**
```java
DODatabaseReaderV2 reader = new DODatabaseReaderV2();
```

### Step 3: Replace Conversion Logic

**Before:**
```java
DODatabase database = dbReader.readDatabaseMeta(container, encoding, size, schema);
DOSchema schema = inferrer.inferSchemaFromDatabase(database);
```

**After:**
```java
DOSchema schema = reader.readDatabaseAsSchema(container);
```

### Step 4: Remove Unused Variables

Remove:
- `DODatabase database` variable
- `DODatabaseReader dbReader` variable
- `DODatabaseSchemaInferrer inferrer` variable
- Any `encoding`, `size`, `schema` parameters if only used for old reader

### Step 5: Update Imports

Remove unused imports:
```java
// Remove these if no longer used:
import migration4o.database.DODatabaseReader;
import migration4o.schema.DODatabaseSchemaInferrer;
import migration4o.models.database.DODatabase;
import migration4o.models.database.DODatabaseClass;
import migration4o.models.database.DODatabaseField;
```

## Testing After Migration

### Unit Tests

- [ ] Test basic schema reading
- [ ] Test with empty database
- [ ] Test with complex inheritance hierarchies
- [ ] Test collection type detection
- [ ] Test object ID deduplication

### Integration Tests

- [ ] Test UI loads schema correctly
- [ ] Test schema editing works
- [ ] Test migration process works
- [ ] Test with real database files
- [ ] Compare results with old approach (temporarily)

### Validation Checklist

- [ ] No compilation errors
- [ ] No runtime errors
- [ ] UI displays schema correctly
- [ ] All classes are loaded
- [ ] Object counts are accurate
- [ ] Field types are correct
- [ ] Collections are detected
- [ ] Inheritance is preserved

## Files to Update (Examples)

### High Priority

- [ ] [DODatabaseBuilder.java](../src/main/java/migration4o/database/DODatabaseBuilder.java)
- [ ] Schema editor panels in UI
- [ ] Migration workflow components
- [ ] Database inspection tools

### Medium Priority

- [ ] Report generators
- [ ] Schema comparison tools
- [ ] Database utilities

### Low Priority

- [ ] Examples and documentation
- [ ] Test utilities
- [ ] Debug tools

## Rollback Plan

If migration causes issues:

1. **Revert changes** - Use version control to revert
2. **Document issues** - Note what went wrong
3. **Keep both approaches** - Use V2 for new code, keep old for legacy
4. **Gradual migration** - Migrate one component at a time

## Compatibility Notes

### Safe to Migrate

✅ Code that:
- Only needs DOSchema* objects
- Doesn't use DODatabase* for other purposes
- Works with UI components
- Performs schema comparison
- Generates reports from schema

### Requires DODatabase*

⚠️ Keep old approach if code:
- Uses DOEngine (which requires DODatabase)
- Accesses DODatabase-specific methods
- Performs database-level operations
- Has tight coupling to DODatabase* classes

## Performance Validation

After migration, verify:

- [ ] **Memory usage** - Should be ~50% lower
- [ ] **Load time** - Should be ~30-40% faster
- [ ] **Object count** - Should match old approach
- [ ] **Schema accuracy** - Should be identical

## Common Issues and Solutions

### Issue 1: Missing Container Reference

**Symptom:** Code expects `DODatabase.getContainer()`

**Solution:** Keep container reference:
```java
ExtObjectContainer container = opener.openDatabase(path);
DOSchema schema = reader.readDatabaseAsSchema(container);
// Use container directly instead of database.getContainer()
```

### Issue 2: Need Encoding Information

**Symptom:** Code needs `DODatabase.getEncoding()`

**Solution:** Get encoding from opener:
```java
DODatabaseOpener opener = new DODatabaseOpener();
ExtObjectContainer container = opener.openDatabase(path);
DODatabaseEncoding encoding = opener.getSuccessfulEncoding();
```

### Issue 3: Need Database Statistics

**Symptom:** Code needs `database.getTotalClasses()`, `database.getTotalObjects()`

**Solution:** Calculate from schema:
```java
int totalClasses = schema.getClasses().length;
int totalObjects = 0;
for (DOSchemaClass cls : schema.getClasses()) {
    totalObjects += (cls.objectIds != null ? cls.objectIds.length : 0);
}
```

## Documentation Updates

After migration, update:

- [ ] Architecture diagrams
- [ ] API documentation
- [ ] User guides
- [ ] Code comments
- [ ] README files

## Sign-off Checklist

- [ ] All code migrated successfully
- [ ] All tests passing
- [ ] Performance validated
- [ ] Documentation updated
- [ ] Code reviewed
- [ ] Deployed and tested in production

## Resources

- [Architecture Documentation](database-reader-v2-architecture.md)
- [Quick Reference](database-reader-v2-quick-reference.md)
- [Usage Example](../src/main/java/migration4o/examples/DODatabaseReaderV2Example.java)
- [Summary](database-reader-v2-summary.md)

## Questions?

Contact the development team or refer to documentation for help.
