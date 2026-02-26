# Database Structure Documentation

This document describes the complete in-memory object structure that results from loading a db4o database using our Migration4O system.

## Overview

The Migration4O system loads a db4o database and creates a structured in-memory representation that separates **schema definition** from **database content**, then links them together to create a unified object model for export and analysis.

## Core Architecture Components

### 1. DOEngine - The Main Container
The `DOEngine` is the central container that holds everything:
- **DOSchema**: Defines the expected structure and export modules
- **DODatabase**: Contains the actual database classes and objects
- **DOObjectReachabilityTracker**: Tracks which objects are reachable from root classes
- **DOEngineMonitoring**: Provides statistics and monitoring

### 2. DOSchema - The Export Structure Definition
The schema is loaded from `schema/migration-schema.xml` and defines:

#### Schema Modules (DOSchemaModule)
These define the **export modules** - each becomes a separate output file:
- `Paramètres` → `Parametres.xml`
- `Organisation` → `Organisation.xml` 
- `Dossier adresse` → `Dossier_adresse.xml`
- `Prévention` → `Prevention.xml`
- `Intervention` → `Intervention.xml`
- `RCCI` → `RCCI.xml`
- `Travail` → `Travail.xml`
- `Bornes` → `Bornes.xml`
- `Plan d'intervention` → `Plan_d_intervention.xml`
- `Formation` → `Formation.xml`
- `Équipements` → `Equipements.xml`
- `Sécurité civile` → `Securite_civile.xml`
- `Horaires` → `Horaires.xml`
- `Rapports` → `Rapports.xml`

#### Schema Classes (DOSchemaClass)
Each module contains schema classes that define:
- **Export name**: The name to use in the export file
- **Database class link**: Reference to the actual database class
- **Field definitions**: Expected fields and their types

**Key Methods:**
- `getExportName()`: Returns the name for export (used as XML element name)
- `getDatabaseClass()`: Returns the linked database class
- `getShortName()`: Returns the class name

### 3. DODatabase - The Actual Data Container
Contains the real database content discovered from the db4o file:

#### Database Classes (DODatabaseClass)
Represent the actual Java classes found in the database:
- **Class metadata**: Full name, short name, inheritance relationships
- **Object storage**: All objects of this class type
- **Field definitions**: Actual fields found in the database
- **Schema linking**: Reference back to corresponding schema class (if any)

**Key Methods:**
- `getShortName()`: Returns class name (e.g., "PersonneRess", "VilleGeo")
- `getName()`: Returns full class name (e.g., "gest.dossPrev.PersonneRess")
- `getObjects()`: Returns all resolved objects of this class
- `getReferenceCount()`: Number of times this class is referenced

#### Database Objects (DODatabaseObject)
Represent individual object instances:
- **Object ID**: Unique identifier in the database
- **Field values**: All primitive and reference field values
- **References**: Links to other objects (both direct and through collections)
- **Reachability**: Whether object is reachable from module roots

## Data Location and Export Logic

### Where Data Actually Resides
Data is exported to modules based on **schema class definitions**, NOT based on class names:

1. **Schema Module Assignment**: Each database class is exported to the module where it's defined in the schema
2. **Export Name Resolution**: Uses `DOSchemaClass.getExportName()` or falls back to `getShortName()`
3. **Single Export Rule**: Each database class is exported only once, even if referenced from multiple modules

### Schema-to-Database Linking Process
During initialization:
1. Load schema from XML file
2. Scan database for actual classes
3. **Link schema classes to database classes** by matching names
4. Database classes get reference to their schema class
5. Schema classes get reference to their database class

### Collection Reference Resolution
When collections reference other objects:

#### For Schema Classes (Enhanced Fields)
- `field.getContentTypeClass()` returns `DOSchemaClass`
- Get database class via `schemaClass.getDatabaseClass()`
- Determine export location from the **database class's schema assignment**

#### For Database Classes (Direct References)
- `field.getContentTypeClass()` returns `DODatabaseClass`
- Export location determined by finding which module contains this class

#### Export Module Resolution Algorithm
```java
private String getExportNameForClass(DODatabaseClass dbClass) {
    // 1. Find the schema class that links to this database class
    for (DOSchemaClass schemaClass : engine.getSchema().getClasses()) {
        if (schemaClass.getDatabaseClass() == dbClass) {
            // 2. Use the export name from schema class
            String exportName = schemaClass.getExportName();
            if (exportName != null && !exportName.isEmpty()) {
                return exportName;  // e.g., "PersonneRess"
            }
            return schemaClass.getShortName();
        }
    }
    // 3. Fallback to database class name if no schema class found
    return dbClass.getShortName();
}
```

## Object Relationships and Navigation

### Direct Field References
- Object A has field pointing to Object B
- Creates navigable link A → B
- B's location determined by B's schema module assignment

### Collection Field References
- Object A has Vector/List field containing IDs or objects
- Each collection item creates A → Item relationship
- Collection metadata includes:
  - `referencedClass`: The actual class of items in the collection
  - `exportModule`: Where those items are actually exported
  - `type`: "id-collection" vs "object-collection"

### ID-Type Object Handling
Special handling for classes starting with "ID" (e.g., `IDPersonneRess`):
1. **Target Resolution**: `IDPersonneRess` → find `PersonneRess` class in database
2. **Export Location**: Use target class's export location, not ID class location
3. **Synthetic References**: ID objects get direct reference to their target via `mID` field

## In-Memory Data Structure

### Complete Object Graph
After loading, the system contains:

```
DOEngine
├── DOSchema
│   ├── DOSchemaModule[] (14 modules - the export destinations)
│   │   ├── "Paramètres" → contains classes exported to Parametres.xml
│   │   ├── "Organisation" → contains classes exported to Organisation.xml
│   │   ├── "Dossier adresse" → contains classes exported to Dossier_adresse.xml
│   │   └── ... (11 more modules)
│   └── DOSchemaClass[] (independent classes used across modules)
├── DODatabase
│   ├── DODatabaseClass[] (all classes found in database)
│   │   ├── PersonneRess (linked to schema class in "Prevention" module)
│   │   ├── VilleGeo (linked to schema class in "Parametres" module)
│   │   ├── CodeRef (linked to schema class in "Organisation" module)
│   │   └── ... (hundreds of classes)
│   └── ExtObjectContainer (db4o database connection)
└── DOObjectReachabilityTracker
    └── Tracks which specific object IDs are reachable from module root classes
```

### Field Content Type Resolution
For collection fields, content type resolution follows this hierarchy:
1. **Schema Enhanced**: `field.getContentTypeClass()` → `DOSchemaClass` → `getDatabaseClass()` → `DODatabaseClass`
2. **Database Direct**: `field.getContentTypeClass()` → `DODatabaseClass`
3. **Export Location**: Database class → find linked schema class → determine module

## Key Insights for Reference Resolution

### Correct Reference Information
- `referencedClass`: The **database class name** of collection items (e.g., "PersonneRess")
- `exportModule`: The **module name or export name** where that class is actually exported (e.g., "Prevention", not "PersonneRess")

### Why Previous Assumptions Were Wrong
1. **Class name ≠ Module name**: A class named "PersonneRess" is NOT exported to a "PersonneRess.xml" module
2. **Schema defines location**: Export location is determined by which schema module contains the class definition
3. **One class, one location**: Each database class is exported exactly once to its assigned module

### Validation Requirements
For collection references to be valid:
1. The referenced class must exist in the database
2. The referenced class must be assigned to a schema module  
3. The export module name must match an actual output file
4. The collection should only reference classes that are actually exported

## Export Process Flow

1. **For each schema module** (in order):
   - Create output file (e.g., `Parametres.xml`)
   - **For each schema class** in the module:
     - Get linked database class
     - Export all objects of that database class
     - Include collection references with correct metadata

2. **Collection metadata generation**:
   - Determine collection content type (database class)
   - Find which module exports that class
   - Generate `referencedClass` and `exportModule` attributes
   - Ensure references point to actual export destinations

This structure ensures that all collection references point to valid, existing data locations in the exported files.