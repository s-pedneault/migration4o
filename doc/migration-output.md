# DB4O database migration engine

Now that we have a fully-functional DB4O database reader and resolver, and test migration process to Excel, we need to build a complete migration engine.

We will be building an XML export engine (in package `dataobjects/impl/migration/xml/`), which receives a fully-initialized `DOEngine`. 

## Output Structure

The XML engine must export several files:

### 1. XML Schema (`premligne-schema.xml`)
An XSD (XML Schema Definition) that describes the structure of all exported data files.
- Namespace: `migration4o`
- Defines complex types for each `DOClass` in the schema
- Defines simple types for primitive fields and enumerations
- Handles inheritance relationships
- Supports both nested objects and ID/IDREF references

### 2. XML Data Files (`premligne-data/`)
A folder containing XML files with the complete database export:
- **One XML file per `DOSchemaModule`** (e.g., `[module-name].xml`)
- **One additional file for unreached objects** (`unreached.xml`)
- All files conform to the generated XSD schema
- Uses streaming XML writing for performance with large databases

#### Object Reference Strategy
- **Nested objects**: Used when an object is referenced by only one parent (not shared)
- **ID/IDREF references**: Used when an object is referenced by multiple parents
- Each object exported in a separate tree includes:
  - `type`: The fully-qualified class name
  - `id`: The unique object identifier
  - All field values

#### Unreached Objects
Objects not reachable from the root object(s) are exported to `unreached.xml` for investigation, as all objects in the database are expected to be reachable for full migration.

### 3. Migration Report (`premligne-report.xml`)
A comprehensive XML report documenting the migration:
- Complete database structure (modules, classes, fields)
- Statistics: number of objects migrated per class
- Reachability information:
  - Count of reached objects per class
  - Count of unreached objects per class
  - Total object count per class
- Module organization
- Migration metadata (date, source database, engine version)
- Warnings/issues encountered during migration

## Example Output

If we import `54060/premligne.dat`, we should end up with:
```
54060/migration/
├── premligne-schema.xml           # XSD schema definition
├── premligne-data/                # Data export folder
│   ├── [module1].xml              # One file per module
│   ├── [module2].xml
│   ├── ...
│   └── unreached.xml              # Unreached objects (for investigation)
└── premligne-report.xml           # Migration statistics and report
```

## Performance Considerations

- Use streaming XML writing (StAX) to handle very large databases
- Single XML file per module (no chunking)
- Progress reporting through `DOEngineMonitoring`
- Memory-efficient object traversal

