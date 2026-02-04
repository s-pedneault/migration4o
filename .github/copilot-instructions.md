# Copilot Instructions for Migration4O

## Project Overview
**Migration4O** is a Java Swing desktop application for migrating DB4O object databases to XML format. It features interactive schema management, export configuration, and reachability analysis.

## Architecture

### Singleton Service Layer
The application uses **singleton services** that manage shared state and are initialized at startup:
- **ApplicationService**: Orchestrates initialization; loads schemas and modules before UI starts
- **DODatabaseService**: Manages the DB4O database container (opened once, reused throughout session)
- **DOSchemaService**: Manages reference and database schemas
- **DOModuleService**: Manages the 14 export modules from `migration-format.xml`

**Entry Point**: `Migration4oUI.main()` → `ApplicationService.initialize()` → UI launch

### Two-Schema System (CRITICAL)
- **Reference Schema** (`schema/reference-schema.xml`): Defines **what to export and how** (field mappings, transformations, export flags)
- **Database Schema**: Runtime-discovered actual DB4O structure (what exists in the database)
- **Rule**: Export operations use reference schema to drive behavior, database schema to validate structure

### Export Workflow
1. **UI Layer**: `MigrationServiceCallback` (SwingWorker adapter for async operations)
2. **Service Layer**: `MigrationExportService` (business logic coordinator)
3. **Engine Layer**: `XMLExportEngine` (actual XML generation with progress callbacks)

**Progress Tracking**: Uses `DOExportMonitor` callbacks to update `ExportProgressDialog`

### Module Organization
14 export modules defined in `schema/migration-format.xml`:
- **Parametres**, **Organisation**, **Prevention**, **Intervention**, **Evaluation**, **Competences**, **Referentiels**, **Planning**, **Projets**, **Ressources**, **Documents**, **Messagerie**, **Administration**, **Systeme**

Each module contains class configurations with export settings.

## Developer Workflows


### Building
- Kill the application if running, because Maven cannot overwrite files in use.
- **Build**: `mvn clean compile`
- Always build after making new changes, to ensure the code compiles.
- Never try to truncate the build output with additional options, read the full build logs for errors.
- Always fix errors before running.

### Running
```bash
./run-ui.sh [database_path] [--repeat-export]
```

**CRITICAL**: DB4O requires special JVM flags for reflection on Java 9+. These are in `run-ui.sh`:
```bash
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.util=ALL-UNNAMED
# ... (many more --add-opens flags)
```
**Never remove these flags** or DB4O reflection will fail.


### Export History
Saved to `local/.export-history.properties` for tracking previous exports.

## Project-Specific Conventions

### UI Patterns
- **Background Operations**: Use `SwingWorker` with progress dialogs
- **Modal Avoidance**: Prefer `JFrame` over modal `JDialog` to avoid blocking entire application
- **Service Access**: Always use singleton instances (e.g., `DODatabaseService.getInstance()`)

### Schema Editing
- Field changes in UI must be **applied back to the schema object** (not just displayed)
- Example: After `FieldEditorDialog`, copy all 12+ properties back to `DOSchemaField`

### Database Access
- **NEVER reopen** the database container mid-session; use the existing instance
- Container lifecycle: Opened at startup, closed at shutdown
- Memory optimization: Single in-memory container replaces old multi-copy pattern

### Reachability Analysis
- `reachedObjectIds` arrays track which objects are reachable from root objects
- **MUST reset** before each export to prevent accumulation: `resetReachedValues()`
- Called from `MigrationServiceCallback.exportModulesAsync()`

## Critical Files

### Service Layer
- [src/main/java/migration4o/application/ApplicationService.java](src/main/java/migration4o/application/ApplicationService.java) - Startup initialization
- [src/main/java/migration4o/database/DODatabaseService.java](src/main/java/migration4o/database/DODatabaseService.java) - Database container management
- [src/main/java/migration4o/schema/DOSchemaService.java](src/main/java/migration4o/schema/DOSchemaService.java) - Schema management
- [src/main/java/migration4o/migration/DOModuleService.java](src/main/java/migration4o/migration/DOModuleService.java) - Module configuration

### Export Engine
- [src/main/java/migration4o/engine/export/MigrationExportService.java](src/main/java/migration4o/engine/export/MigrationExportService.java) - Export coordinator
- [src/main/java/migration4o/engine/export/XMLExportEngine.java](src/main/java/migration4o/engine/export/XMLExportEngine.java) - XML generation
- [src/main/java/migration4o/ui/services/MigrationServiceCallback.java](src/main/java/migration4o/ui/services/MigrationServiceCallback.java) - UI adapter

### Key UI Components
- [src/main/java/migration4o/ui/main/MainWindow.java](src/main/java/migration4o/ui/main/MainWindow.java) - Application coordinator
- [src/main/java/migration4o/ui/panels/MigrationCoveragePanel.java](src/main/java/migration4o/ui/panels/MigrationCoveragePanel.java) - Progress tracking with filters
- [src/main/java/migration4o/ui/dialogs/ExportResultDialog.java](src/main/java/migration4o/ui/dialogs/ExportResultDialog.java) - Export results with field editor integration

### Schema Files
- [schema/reference-schema.xml](schema/reference-schema.xml) - Export definitions (editable)
- [schema/migration-format.xml](schema/migration-format.xml) - Module structure
- [schema/migration-schema.xsd](schema/migration-schema.xsd) - XML schema validator

## Dependencies
- **DB4O 7.4.106**: Legacy object database (requires JVM reflection flags)
- **Apache POI 5.2.4**: Excel operations
- **Log4j 2.20.0**: Logging framework
- **Java 8+**: Minimum version (tested with Java 11)
- **Maven 3.6+**: Build system

## Memory Configuration
Adjust in `run.sh` or `run-ui.sh`:
```bash
-Xms1g -Xmx8g  # 1GB initial, 8GB max heap
```
For large databases, increase `-Xmx` value.

## Common Pitfalls

1. **Forgetting to reset reached values** → accumulated counts across exports
2. **Using wrong schema** → reference schema defines exports, database schema reflects data
3. **Modal dialogs blocking UI** → use JFrame for non-modal dialogs
4. **Not applying UI changes back to model** → changes appear but aren't saved
5. **Reopening database container** → memory leak, use singleton instance
6. **Missing JVM flags** → DB4O reflection failures on Java 9+

## Testing Notes
- UI testing requires running full application (`./run-ui.sh`)
- Export testing requires actual DB4O database in `local/` directory
- Schema changes can be validated against `migration-schema.xsd`
