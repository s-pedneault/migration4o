# Application Initialization

## Overview

The application now uses a centralized `ApplicationService` to initialize core services at startup. This ensures that the schema and module structure are always loaded before any UI components are created.

## ApplicationService

**Location:** `migration4o.application.ApplicationService`

**Purpose:** Initialize all core application services at startup

### Initialized Services

1. **DOSchemaService** - Loads reference schema from `schema/reference-schema.xml`
2. **DOModuleService** - Loads module structure from `schema/migration-format.xml`

### NOT Initialized

- **DODatabaseService** - Requires user to select a database file at runtime

## Startup Flow

```
main()
  ├─> ApplicationService.initialize()
  │     ├─> DOSchemaService.loadReferenceSchema()
  │     └─> DOModuleService.loadModuleStructure()
  │
  └─> Create and show MainWindow
        └─> UI components can now safely access schema and modules
```

## Benefits

1. **Guaranteed Initialization**: UI components don't need to check if services are loaded
2. **Fail Fast**: Application exits with error if initialization fails
3. **Centralized Configuration**: All default paths defined in one place
4. **Cleaner UI Code**: No need to handle loading states in UI components

## Entry Point

**File:** `migration4o.ui.Migration4oUI`

The main method:
1. Parses command-line arguments
2. Initializes ApplicationService (exits on failure)
3. Sets up Swing look and feel
4. Creates MainWindow
5. Handles auto-open database if specified

## Error Handling

If service initialization fails:
- Error is logged to console
- User sees error dialog with details
- Application exits with code 1

## Implementation Notes

- ApplicationService uses singleton pattern
- Initialization is idempotent (safe to call multiple times)
- Services themselves remain responsible for reload operations
- Existing reload methods in UI panels still work for manual refresh

## Usage Example

```java
// At application startup (automatic)
ApplicationService.getInstance().initialize();

// Later, UI components can safely use services
DOSchema schema = DOSchemaService.getInstance().getReferenceSchema();
List<MigrationModule> modules = DOModuleService.getInstance().getModules();

// No need to check isSchemaLoaded() or hasModules() at startup
// They are guaranteed to be loaded
```

## Future Enhancements

Potential additions to ApplicationService:
- Configuration management
- Plugin system initialization
- Cache initialization
- Background task scheduler setup
