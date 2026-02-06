# Object Duplication Detection Rules

This document defines the rules implemented in `ObjectDuplicationDetector` for identifying and reporting duplicate object exports during the migration process.

## Overview

Object duplication tracking is a critical component of the export process. It ensures data integrity by detecting when the same object is exported multiple times, which can indicate schema configuration issues or unexpected reference patterns.

## Duplication Confirmation Rules

An object duplication is **CONFIRMED** when **ALL** of the following conditions are met:

### 1. Object's Class is Defined in Reference Schema
- The class of the exported object must be present in the reference schema (`reference-schema.xml`)
- Objects from classes not in the reference schema are not tracked for duplication
- This ensures we only track objects we explicitly configured for export

### 2. Same Object (Class + ID) Exported Twice
- The combination of class name and object ID must appear more than once in the export
- Each export is recorded as an `ObjectReference` entry
- The `ObjectDuplicationDetector` maintains a map: `objectId → List<ObjectReference>`
- Duplication is detected when `references.size() > 1`

### 3. Object is NOT a Descendant of IDEntite
- Objects that extend or implement `IDEntite` are **excluded** from duplication warnings
- `IDEntite` represents a soft reference pattern (ID-only reference, not embedded)
- Soft references are expected to appear multiple times and should not trigger warnings
- Examples of `IDEntite` descendants that are allowed to duplicate:
  - ID reference wrappers
  - Lookup keys
  - Foreign key representations

## Schema Configuration Issues

When duplication is detected, it often indicates a schema configuration problem that should be fixed.

### Required Schema Fix Conditions

The schema **MUST BE FIXED** when **ALL** of the following conditions are met:

#### 1. Field Type is Defined in Reference Schema
- The field's data type must be a class present in `reference-schema.xml`
- Simple types (String, int, Date, etc.) are not subject to this rule
- Only complex object types require `embedContents` configuration

#### 2. Field's embedContents is Set to FALSE
- The field has `embedContents="false"` (or no attribute, which defaults to false)
- This means the field is configured to export objects as ID references
- ID references can lead to duplication when the same object is referenced from multiple places

#### 3. Referenced Object Has Single Reference
- When analyzing duplicate warnings, if an object has exactly one reference source
- And that reference comes from a field with `embedContents="false"`
- This indicates the object should have been embedded instead of exported separately

### Recommended Fix

**Change `embedContents="false"` to `embedContents="true"`** on the field definition in `reference-schema.xml`

This will cause the object to be embedded within its parent rather than exported as a separate entity and referenced by ID.

## Implementation in ObjectDuplicationDetector

### Key Methods

#### `recordObjectReference()`
Records each export of an object with full context:
- `objectId` - The DB4O object ID
- `className` - The object's class name
- `parentObjectId` - The ID of the parent object (null for module exports)
- `sourceContainingClass` - The class that contains the field referencing this object
- `sourceFieldName` - The field name that references this object

#### `generateDuplicateWarnings()`
Analyzes all recorded references and generates warnings for:
- Objects exported more than once (excluding IDEntite descendants)
- Two warning types:
  - `DUPLICATE_EMBEDDED_REFERENCE` - Same object referenced from multiple fields
  - `MISSING_EMBED_CONTENTS` - Object exported as both embedded AND standalone

### Integration Points

The detector is used by:
- **ObjectIdTracker recipe** - Records each reference during export
- **XMLExportEngine** - Generates warnings after export completion
- **ExportStatistics** - Holds the detector instance and collects warnings

## Example Scenarios

### Scenario 1: Missing embedContents="true"

```xml
<!-- INCORRECT CONFIGURATION -->
<field source="responsable" exportAs="responsable" export="true" embedContents="false"/>
```

**Problem**: If `responsable` objects are only referenced once but exported as ID references, they will appear as duplicate exports (once as ID reference target, once as module export).

**Solution**:
```xml
<!-- CORRECT CONFIGURATION -->
<field source="responsable" exportAs="responsable" export="true" embedContents="true"/>
```

### Scenario 2: Legitimate Duplication (Shared Reference)

```xml
<!-- CORRECT CONFIGURATION -->
<field source="departement" exportAs="departement" export="true" embedContents="false"/>
```

**Case**: Multiple employees reference the same department. This is legitimate - the department should be exported once and referenced by ID from multiple employees.

**Result**: Duplication warning is expected but not a schema error. Department should be exported once in its own module.

### Scenario 3: IDEntite Soft References

```xml
<field source="creePar" exportAs="creePar" export="true"/>
```

**Case**: `creePar` is an `IDEntite` (contains only mID field). Many objects may reference the same user ID.

**Result**: No duplication warning generated because IDEntite descendants are excluded from tracking.

## Best Practices

1. **Use `embedContents="true"` for**:
   - Child objects that belong exclusively to their parent
   - Objects that are only referenced once
   - Complex nested structures

2. **Use `embedContents="false"` for**:
   - Shared reference data (departments, categories, users)
   - Objects exported in their own module
   - Objects referenced from multiple parents

3. **Use IDEntite pattern for**:
   - Soft references (ID-only lookups)
   - Cross-module references
   - References to objects that may not be exported

## Validation Process

1. Export completes normally
2. `ObjectDuplicationDetector.generateDuplicateWarnings()` is called
3. Warnings are added to `ExportStatistics.schemaWarnings`
4. Warnings are displayed in `ExportResultDialog`
5. Developer reviews warnings and updates `reference-schema.xml` as needed
6. Re-export to verify warnings are resolved

## Related Files

- `migration4o/migration/monitoring/ObjectDuplicationDetector.java` - Core implementation
- `migration4o/migration/monitoring/ObjectReference.java` - Reference data structure
- `migration4o/migration/monitoring/ExportWarning.java` - Warning analysis and formatting
- `migration4o/migration/recipes/ObjectIdTracker.java` - Records references during export
- `schema/reference-schema.xml` - Schema configuration file to be fixed
