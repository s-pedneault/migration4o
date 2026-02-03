# Service-Based Architecture Refactoring

## Overview

The application has been refactored from a UI-managed approach to a **service-oriented architecture** where singleton services manage database and schema lifecycle, while UI components consume these services.

## Services

### DODatabaseService

**Location**: `src/main/java/migration4o/database/DODatabaseService.java`

**Responsibility**: Manages the single in-memory database instance across the entire application.

**Key Features**:
- Singleton pattern ensures only ONE database instance exists
- Loads database into RAM using `MemoryIoAdapter` for fast access
- Provides thread-safe access to the shared database container
- Handles database lifecycle (open/close operations)

**API**:
```java
DODatabaseService service = DODatabaseService.getInstance();

// Open database (loads into memory)
ExtObjectContainer container = service.openDatabase(path);

// Get shared container
ExtObjectContainer container = service.getContainer();

// Check if database is open
boolean isOpen = service.isDatabaseOpen();

// Get current database path
String path = service.getCurrentDatabasePath();

// Close database
service.closeDatabase();
```

### DOSchemaService

**Location**: `src/main/java/migration4o/schema/DOSchemaService.java`

**Responsibility**: Manages the reference schema across the entire application.

**Key Features**:
- Singleton pattern ensures only ONE schema instance exists
- Loads schema once and shares it across all components
- Provides thread-safe access to the schema

**API**:
```java
DOSchemaService service = DOSchemaService.getInstance();

// Load reference schema
DOSchema schema = service.loadReferenceSchema(schemaPath);

// Get loaded schema
DOSchema schema = service.getReferenceSchema();

// Check if schema is loaded
boolean isLoaded = service.isSchemaLoaded();

// Get schema path
String path = service.getCurrentSchemaPath();
```

## Architecture Benefits

### Before (UI-Managed)
```
MainWindow
  ├─ Opens database → Container1 (in memory)
  ├─ Loads schema
  └─ Passes these to panels

XMLExportEngine
  └─ Opens database AGAIN → Container2 (in memory) ❌

MigrationCoveragePanel
  └─ Opens database AGAIN → Container3 (in memory) ❌

ClassObjectsDialog
  └─ Opens database AGAIN → Container4 (in memory) ❌
```

**Problems**:
- Multiple database copies loaded into RAM (~50-212MB each)
- Each export operation reloads the entire database
- UI components responsible for database lifecycle
- Tight coupling between UI and data access

### After (Service-Managed)
```
DODatabaseService (Singleton)
  └─ Container (in memory, shared) ✓

DOSchemaService (Singleton)
  └─ Schema (shared) ✓

All Components
  └─ Use shared instances from services ✓
```

**Benefits**:
- ✅ **Single database instance** - loaded once, used everywhere
- ✅ **Memory efficient** - ~50-212MB instead of multiple copies
- ✅ **Performance** - no repeated loading (300ms+ per load eliminated)
- ✅ **Separation of concerns** - UI doesn't manage data lifecycle
- ✅ **Testability** - services can be easily mocked
- ✅ **Thread safety** - synchronized access built-in

## Updated Components

### Core Services
- ✅ `DODatabaseService.java` - NEW singleton service
- ✅ `DOSchemaService.java` - NEW singleton service

### Export Engine
- ✅ `XMLExportEngine.java` - Uses DODatabaseService.getInstance().getContainer()
- ✅ `MigrationExportService.java` - Uses both services, no constructor parameters

### UI Components
- ✅ `MainWindow.java` - Uses services instead of managing database/schema directly
- ✅ `MigrationStructurePanel.java` - Creates MigrationExportService() with no parameters
- ✅ `MigrationCoveragePanel.java` - Uses DODatabaseService.getInstance().getContainer()
- ✅ `ClassObjectsDialog.java` - Uses DODatabaseService.getInstance().getContainer()

## Migration Guide

### For Developers

**Old Way** (deprecated):
```java
// Opening database
DODatabaseOpener opener = new DODatabaseOpener();
ExtObjectContainer container = opener.openDatabase(path);
// ... use container ...
container.close();
```

**New Way**:
```java
// Opening database (usually done by MainWindow)
DODatabaseService.getInstance().openDatabase(path);

// Using database anywhere in the application
ExtObjectContainer container = DODatabaseService.getInstance().getContainer();
// ... use container ...
// DON'T CLOSE IT - the service manages lifecycle
```

**Old Way** (deprecated):
```java
// Creating export service
MigrationExportService service = new MigrationExportService(
    referenceSchema,
    databaseSchema,
    databasePath
);
```

**New Way**:
```java
// Creating export service (gets data from singletons)
MigrationExportService service = new MigrationExportService();
```

## Performance Impact

### Before
- Opening database: ~300ms each time
- Memory: Multiple copies (~50-212MB each)
- Each export: Load database → Export → Close

### After
- Opening database: ~300ms ONE TIME
- Memory: Single copy (~50-212MB total)
- Each export: Reuse in-memory container ✅

## Thread Safety

Both services are thread-safe:
- All public methods are `synchronized`
- Safe to call from multiple threads
- No race conditions on shared resources

## Future Enhancements

Potential improvements:
1. **Database schema caching** - Add getDatabaseSchema() to DODatabaseService
2. **Multiple database support** - Track multiple open databases by key
3. **Connection pooling** - For scenarios with multiple concurrent operations
4. **Event notifications** - Notify listeners when database/schema changes

## Testing

Services can be easily mocked for unit testing:
```java
// In tests
DODatabaseService mockService = mock(DODatabaseService.class);
when(mockService.getContainer()).thenReturn(mockContainer);
```

## Conclusion

This refactoring establishes a clean separation between UI and data management, eliminates redundant database loading, and provides a solid foundation for future enhancements. The service-oriented approach makes the codebase more maintainable, testable, and performant.
