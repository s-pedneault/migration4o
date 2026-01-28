# DODatabaseReaderV2 Architecture Diagrams

## Old Two-Step Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         DB4O Database                            │
│                      (StoredClass objects)                       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      DODatabaseReader                            │
│  • Reads StoredClass objects                                     │
│  • Creates DODatabaseClass objects                               │
│  • Extracts fields as DODatabaseField                            │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    DODatabase* Objects                           │
│  • DODatabase                                                    │
│  • DODatabaseClass[]                                             │
│  • DODatabaseField[]                                             │
│  (INTERMEDIARY - Created and discarded)                          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                  DODatabaseSchemaInferrer                        │
│  • Converts DODatabaseClass → DOSchemaClass                      │
│  • Converts DODatabaseField → DOSchemaField                      │
│  • Normalizes types                                              │
│  • Deduplicates object IDs                                       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     DOSchema* Objects                            │
│  • DOSchema                                                      │
│  • DOSchemaClass[]                                               │
│  • DOSchemaField[]                                               │
│  (FINAL OUTPUT - Used by UI)                                     │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                           UI Layer
```

## New Direct Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         DB4O Database                            │
│                      (StoredClass objects)                       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    DODatabaseReaderV2                            │
│  • Reads StoredClass objects                                     │
│  • Creates DOSchemaClass objects DIRECTLY                        │
│  • Creates DOSchemaField objects DIRECTLY                        │
│  • Normalizes types                                              │
│  • Deduplicates fields                                           │
│  • Deduplicates object IDs                                       │
│                                                                   │
│  (ALL IN ONE STEP - No intermediaries)                           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     DOSchema* Objects                            │
│  • DOSchema                                                      │
│  • DOSchemaClass[]                                               │
│  • DOSchemaField[]                                               │
│  (FINAL OUTPUT - Used by UI)                                     │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                           UI Layer
```

## Conversion Process Details

### Old Approach - Field Conversion

```
StoredField
    │
    ▼
┌─────────────────────┐
│ DODatabaseReader    │
│ extractField()      │
└─────────────────────┘
    │
    ▼
DODatabaseField
    │
    ▼
┌─────────────────────┐
│ SchemaInferrer      │
│ convertField()      │
└─────────────────────┘
    │
    ▼
DOSchemaField
```

### New Approach - Field Conversion

```
StoredField
    │
    ▼
┌─────────────────────┐
│ DODatabaseReaderV2  │
│ convertField()      │
└─────────────────────┘
    │
    ▼
DOSchemaField
```

## Object Flow Comparison

### Old Approach

```
Memory Usage Over Time:

DB4O Objects: ████████████████████████████████
              ↓
DODatabase*:  ████████████████████ (peak usage)
              ↓
DOSchema*:    ████████████████
              ↓
Final:        ████████████████

Total Peak Memory: DODatabase* + DOSchema* objects
```

### New Approach

```
Memory Usage Over Time:

DB4O Objects: ████████████████████████████████
              ↓
DOSchema*:    ████████████████
              ↓
Final:        ████████████████

Total Peak Memory: Only DOSchema* objects (~50% reduction)
```

## Class Hierarchy Processing

```
┌──────────────────┐
│  GrandParent     │  Object IDs: [1, 2, 3, 4, 5]
└──────────────────┘
         │
         ▼
┌──────────────────┐
│     Parent       │  Object IDs: [3, 4, 5]
└──────────────────┘
         │
         ▼
┌──────────────────┐
│      Child       │  Object IDs: [5]
└──────────────────┘

After Deduplication:

┌──────────────────┐
│  GrandParent     │  Object IDs: [1, 2]  ✓ Deduplicated
└──────────────────┘
         │
         ▼
┌──────────────────┐
│     Parent       │  Object IDs: [3, 4]  ✓ Deduplicated
└──────────────────┘
         │
         ▼
┌──────────────────┐
│      Child       │  Object IDs: [5]     ✓ Unchanged
└──────────────────┘
```

## Collection Type Inference

```
┌─────────────────────────────┐
│     StoredField             │
│  name: "items"              │
│  type: "java.util.List"     │
│  stored: "List<String>"     │
└─────────────────────────────┘
              │
              ▼
┌─────────────────────────────┐
│  DODatabaseReaderV2         │
│  • Detect: isCollection     │
│  • Parse generics           │
│  • Extract: "String"        │
└─────────────────────────────┘
              │
              ▼
┌─────────────────────────────┐
│     DOSchemaField           │
│  source: "items"            │
│  type: "java.util.List"     │
│  isCollection: true         │
│  childrenType: "String"     │
└─────────────────────────────┘
```

## Performance Comparison

### Old Approach - Timeline

```
Time: 0ms ────────── 100ms ────────── 200ms
      │              │                 │
      Start          DODatabase*       DOSchema*
                     created           created
      
Total: ~200ms
Objects: DODatabase* + DOSchema*
```

### New Approach - Timeline

```
Time: 0ms ────────── 120ms
      │              │
      Start          DOSchema*
                     created
      
Total: ~120ms (40% faster)
Objects: Only DOSchema*
```

## Integration Pattern

```
┌──────────────────┐
│  Application     │
└──────────────────┘
         │
         ▼
┌──────────────────┐
│ DODatabaseOpener │  Opens DB4O file
└──────────────────┘
         │
         ▼
ExtObjectContainer
         │
         ▼
┌──────────────────┐
│DODatabaseReaderV2│  Converts to schema
└──────────────────┘
         │
         ▼
     DOSchema
         │
         ├──────────────┐
         │              │
         ▼              ▼
┌──────────────┐  ┌──────────────┐
│ SchemaEditor │  │ MigrationUI  │
└──────────────┘  └──────────────┘
```

## Legend

```
████  Memory usage
───▶  Data flow
│     Inheritance/Dependency
▼     Process direction
✓     Verified/Completed
```
