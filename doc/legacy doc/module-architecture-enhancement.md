# Module Architecture Enhancement - Developer Guide

## Overview

The module system has been significantly enhanced to support sophisticated export configurations with per-class settings and field-based filtering criteria. This allows precise control over what gets exported and how.

## Key Improvements

### 1. **Per-Class Export Configuration**

Previously, modules only stored class names as strings:
```java
List<String> classNames = Arrays.asList("gest.config.ParamConfig", "gest.dossPrev.DossierPrev");
```

Now, each class in a module has its own configuration:
```java
List<ClassExportConfig> configs = Arrays.asList(
    new ClassExportConfig("gest.dossPrev.DossierPrev", "DossierPrevOld", criteria),
    new ClassExportConfig("gest.dossPrev.DossierPrev", "DossierPrevNew", otherCriteria)
);
```

### 2. **Custom Destination File Names**

You can specify a custom XML file name for each class:
- **Old behavior**: Class `gest.dossPrev.DossierPrev` → `DossierPrev.xml`
- **New behavior**: Same class can export to `DossierPrevOld.xml`, `DossierPrevNew.xml`, etc.

### 3. **Field-Based Export Criteria**

Filter which objects get exported based on field values:
```java
// Export only objects where mIDDossPrevOld == -1
ExportCriteria criteria = new ExportCriteria("mIDDossPrevOld", Operator.EQUALS, "-1");
```

Supported operators:
- `==` (EQUALS)
- `!=` (NOT_EQUALS)
- `>` (GREATER_THAN)
- `<` (LESS_THAN)
- `>=` (GREATER_OR_EQUAL)
- `<=` (LESS_OR_EQUAL)
- `is null` (IS_NULL)
- `is not null` (IS_NOT_NULL)

### 4. **Multiple Instances of Same Class**

Add the same class multiple times with different configurations:
```java
// Export old records to one file
ClassExportConfig oldRecords = new ClassExportConfig(
    "gest.dossPrev.DossierPrev",
    "DossierPrevOld",
    Arrays.asList(new ExportCriteria("mIDDossPrevOld", Operator.EQUALS, "-1"))
);

// Export new records to another file
ClassExportConfig newRecords = new ClassExportConfig(
    "gest.dossPrev.DossierPrev",
    "DossierPrevNew",
    Arrays.asList(new ExportCriteria("mIDDossPrevOld", Operator.NOT_EQUALS, "-1"))
);

module.getClassConfigs().addAll(Arrays.asList(oldRecords, newRecords));
```

## New Model Classes

### ExportCriteria

Represents a single filter condition:

```java
public class ExportCriteria {
    private final String fieldName;       // e.g., "mIDDossPrevOld"
    private final Operator operator;      // e.g., EQUALS, GREATER_THAN
    private final String value;           // e.g., "-1"
    
    public boolean matches(Object fieldValue) {
        // Evaluates if the field value matches this criteria
    }
}
```

### ClassExportConfig

Configuration for exporting a specific class:

```java
public class ClassExportConfig {
    private final String className;              // Source class name
    private final String destinationFileName;    // Custom XML file name (optional)
    private final List<ExportCriteria> criteria; // Filter conditions
    
    public boolean matchesAllCriteria(Object object) {
        // Returns true if object matches ALL criteria (AND logic)
    }
}
```

### Updated MigrationModule

Now works with `ClassExportConfig` instead of plain strings:

```java
public class MigrationModule {
    private final List<ClassExportConfig> classConfigs;  // Instead of List<String>
    
    // Backward compatibility
    public List<String> getClassNames() {
        return classConfigs.stream()
            .map(ClassExportConfig::getClassName)
            .collect(Collectors.toList());
    }
    
    public List<ClassExportConfig> getClassConfigs() {
        return classConfigs;
    }
}
```

## XML Format

### Old Format (Still Supported)

```xml
<module name="Dossiers" id="Dossiers">
    <classRef sourceName="gest.dossPrev.DossierPrev"/>
    <classRef sourceName="gest.dossPrev.DossierAdresse"/>
</module>
```

### New Extended Format

```xml
<module name="Dossiers" id="Dossiers">
    <!-- Simple class reference without criteria -->
    <classRef sourceName="gest.config.ParamConfig"/>
    
    <!-- Class with custom destination file name -->
    <classRef sourceName="gest.dossPrev.DossierPrev" destinationFile="DossierPrevOld">
        <criteria field="mIDDossPrevOld" operator="==" value="-1"/>
    </classRef>
    
    <!-- Same class again with different settings -->
    <classRef sourceName="gest.dossPrev.DossierPrev" destinationFile="DossierPrevNew">
        <criteria field="mIDDossPrevOld" operator="!=" value="-1"/>
        <criteria field="mStatut" operator=">" value="0"/>
    </classRef>
    
    <!-- Null check -->
    <classRef sourceName="gest.dossPrev.DossierAdresse">
        <criteria field="mIDDossierPrev" operator="is not null"/>
    </classRef>
</module>
```

## Export Behavior

### Criteria Evaluation

All criteria must match (AND logic) for an object to be exported:

```java
if (config.hasCriteria()) {
    Object obj = container.ext().getByID(objectId);
    if (!config.matchesAllCriteria(obj)) {
        continue; // Skip this object
    }
}
```

The `matchesAllCriteria` method:
1. Uses reflection to access field values
2. Traverses class hierarchy to find private fields from parent classes
3. Converts values for comparison (numeric or string)
4. Returns `false` if any criterion doesn't match

### Combined with Object Limit

Criteria filtering works together with the existing object limit feature:

```java
int exportedCount = 0;
for (long objectId : objectIds) {
    // First check object limit
    if (maxObjectsPerClass != null && exportedCount >= maxObjectsPerClass) {
        break;
    }
    
    // Then check criteria
    if (config != null && config.hasCriteria()) {
        if (!config.matchesAllCriteria(obj)) {
            continue; // Doesn't count toward limit
        }
    }
    
    exportObject(obj);
    exportedCount++;
}
```

## File Output Structure

With the new system, your output can look like:

```
output/54060/
├── Data/
│   └── Dossiers/
│       ├── DossierPrevOld.xml      (only objects with mIDDossPrevOld == -1)
│       ├── DossierPrevNew.xml      (only objects with mIDDossPrevOld != -1)
│       └── ParamConfig.xml         (all objects)
└── Definitions/
    └── Dossiers/
        ├── DossierPrevOld.xsd
        ├── DossierPrevNew.xsd
        └── ParamConfig.xsd
```

## Backward Compatibility

### Reading Old Files

The `DOModuleStructureReader` automatically handles both formats:
- Old format (`<classRef sourceName="..."/>`) → Creates `ClassExportConfig` with no criteria
- New format → Parses all attributes and child elements

### Writing Files

The `DOModuleStructureWriter` always uses the new format but:
- Omits `destinationFile` attribute if it's the same as class name
- Uses self-closing tags when there are no criteria
- Uses child elements only when criteria exist

### Existing Code

UI code that builds modules from tree nodes uses the backward-compatible constructor:
```java
new MigrationModule(name, id, classNames, childModules, true);
```

The `true` flag indicates it's receiving strings and should convert them to `ClassExportConfig` objects.

## Usage Examples

### Example 1: Split Export by Status Field

```java
// Export active records
ExportCriteria activeOnly = new ExportCriteria("mStatut", Operator.EQUALS, "1");
ClassExportConfig activeConfig = new ClassExportConfig(
    "gest.intervention.Intervention", 
    "InterventionActive", 
    Arrays.asList(activeOnly)
);

// Export archived records
ExportCriteria archivedOnly = new ExportCriteria("mStatut", Operator.EQUALS, "0");
ClassExportConfig archivedConfig = new ClassExportConfig(
    "gest.intervention.Intervention",
    "InterventionArchived",
    Arrays.asList(archivedOnly)
);
```

### Example 2: Filter by Numeric Range

```java
// Export recent items (ID > 1000)
ExportCriteria recentOnly = new ExportCriteria("mID", Operator.GREATER_THAN, "1000");
ClassExportConfig config = new ClassExportConfig(
    "gest.formation.Formation",
    "FormationRecent",
    Arrays.asList(recentOnly)
);
```

### Example 3: Multiple Criteria (AND Logic)

```java
// Export only valid, active, recent records
List<ExportCriteria> criteria = Arrays.asList(
    new ExportCriteria("mStatut", Operator.EQUALS, "1"),           // Active
    new ExportCriteria("mID", Operator.GREATER_THAN, "1000"),      // Recent
    new ExportCriteria("mValidated", Operator.NOT_EQUALS, "0")     // Validated
);

ClassExportConfig config = new ClassExportConfig(
    "gest.dossPrev.DossierPrev",
    "DossierPrevValid",
    criteria
);
```

### Example 4: Null Checks

```java
// Export only records with a parent reference
ExportCriteria hasParent = new ExportCriteria("mIDParent", Operator.IS_NOT_NULL, null);
ClassExportConfig config = new ClassExportConfig(
    "gest.dossPrev.DossierAdresse",
    "DossierAdresseLinked",
    Arrays.asList(hasParent)
);
```

## Implementation Details

### Reflection for Field Access

The system uses reflection to access field values during criteria evaluation:

```java
private java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
    Class<?> current = clazz;
    while (current != null) {
        try {
            return current.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            current = current.getSuperclass();  // Check parent class
        }
    }
    return null;
}
```

This ensures private fields from parent classes can be accessed for filtering.

### Type-Safe Comparisons

Numeric comparisons are performed with proper type conversion:

```java
if (fieldValue instanceof Number && value != null) {
    double fieldNum = ((Number) fieldValue).doubleValue();
    double criteriaNum = Double.parseDouble(value);
    
    switch (operator) {
        case GREATER_THAN: return fieldNum > criteriaNum;
        case LESS_THAN: return fieldNum < criteriaNum;
        // ...
    }
}
```

String comparisons fall back when numeric conversion fails.

## Future Enhancements (Not Yet Implemented)

Potential additions to consider:

1. **OR Logic Support**: Currently all criteria use AND. Could add `CriteriaGroup` with OR support.

2. **UI for Editing Criteria**: Currently requires manual XML editing. Need dialog to add/edit criteria.

3. **Validation**: Check if field names exist before export to catch typos early.

4. **Criteria Templates**: Save commonly-used criteria sets for reuse.

5. **String Pattern Matching**: Support wildcards or regex in string comparisons.

## Migration Path

To migrate existing modules to the new system:

1. **No changes required** - Old format still works
2. To add criteria, manually edit `migration-format.xml` or wait for UI enhancement
3. Files will be re-saved in new format preserving all information

## Summary

This enhancement provides powerful filtering capabilities while maintaining full backward compatibility. You can now:

- ✅ Export same class multiple times with different filters
- ✅ Use custom file names for each export configuration
- ✅ Filter by field values using standard comparison operators
- ✅ Combine multiple criteria with AND logic
- ✅ Check for null/not-null values
- ✅ Mix old simple configs with new filtered configs in same module

The system is production-ready and all changes compile successfully.
