# Quick Start: Using Export Criteria

## Manual XML Editing (Until UI is Built)

Since the UI for editing criteria hasn't been implemented yet, you can manually edit `schema/migration-format.xml` to use the new features.

## Example: Split DossierPrev by Old/New Status

Let's say you want to export `DossierPrev` records to two different files based on whether they have an old dossier reference:

### Before (Simple Export)
```xml
<module name="Dossiers" id="Dossiers">
    <classRef sourceName="gest.dossPrev.DossierPrev"/>
</module>
```

This exports all `DossierPrev` objects to a single `DossierPrev.xml` file.

### After (Split Export with Criteria)
```xml
<module name="Dossiers" id="Dossiers">
    <!-- Export old dossiers (those with mIDDossPrevOld == -1) -->
    <classRef sourceName="gest.dossPrev.DossierPrev" destinationFile="DossierPrevOld">
        <criteria field="mIDDossPrevOld" operator="==" value="-1"/>
    </classRef>
    
    <!-- Export new dossiers (those with mIDDossPrevOld != -1) -->
    <classRef sourceName="gest.dossPrev.DossierPrev" destinationFile="DossierPrevNew">
        <criteria field="mIDDossPrevOld" operator="!=" value="-1"/>
    </classRef>
</module>
```

Now the export will create two files:
- `DossierPrevOld.xml` - Contains only objects where `mIDDossPrevOld == -1`
- `DossierPrevNew.xml` - Contains only objects where `mIDDossPrevOld != -1`

## Available Operators

| Operator | Symbol | Example |
|----------|--------|---------|
| Equals | `==` | `<criteria field="mStatut" operator="==" value="1"/>` |
| Not Equals | `!=` | `<criteria field="mStatut" operator="!=" value="0"/>` |
| Greater Than | `>` | `<criteria field="mID" operator=">" value="1000"/>` |
| Less Than | `<` | `<criteria field="mID" operator="<" value="1000"/>` |
| Greater or Equal | `>=` | `<criteria field="mScore" operator=">=" value="50"/>` |
| Less or Equal | `<=` | `<criteria field="mScore" operator="<=" value="50"/>` |
| Is Null | `is null` | `<criteria field="mParent" operator="is null"/>` |
| Is Not Null | `is not null` | `<criteria field="mParent" operator="is not null"/>` |

**Note**: For `is null` and `is not null`, you don't need the `value` attribute.

## Multiple Criteria (AND Logic)

You can add multiple criteria - all must match for an object to be exported:

```xml
<classRef sourceName="gest.intervention.Intervention" destinationFile="InterventionValidRecent">
    <criteria field="mStatut" operator="==" value="1"/>      <!-- Must be active -->
    <criteria field="mID" operator=">" value="1000"/>         <!-- Must be recent -->
    <criteria field="mValidated" operator="!=" value="0"/>    <!-- Must be validated -->
</classRef>
```

This exports only `Intervention` objects that are:
- Active (mStatut == 1) **AND**
- Recent (mID > 1000) **AND**
- Validated (mValidated != 0)

## Complete Working Example

Here's a realistic example of a module configuration:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<database>
    <modules>
        <module name="Dossiers Prevention" id="DossiersPrev">
            <!-- Export all parameters without filtering -->
            <classRef sourceName="gest.dossPrev.ParamDossPrev"/>
            
            <!-- Split dossiers by old/new status -->
            <classRef sourceName="gest.dossPrev.DossierPrev" destinationFile="DossierPrevOld">
                <criteria field="mIDDossPrevOld" operator="==" value="-1"/>
            </classRef>
            
            <classRef sourceName="gest.dossPrev.DossierPrev" destinationFile="DossierPrevNew">
                <criteria field="mIDDossPrevOld" operator="!=" value="-1"/>
            </classRef>
            
            <!-- Export only addresses that are linked to a dossier -->
            <classRef sourceName="gest.dossPrev.DossierAdresse" destinationFile="DossierAdresse">
                <criteria field="mIDDossierPrev" operator="is not null"/>
            </classRef>
        </module>
        
        <module name="Interventions" id="Interventions">
            <!-- Export active interventions -->
            <classRef sourceName="gest.intervention.Intervention" destinationFile="InterventionActive">
                <criteria field="mStatut" operator="==" value="1"/>
            </classRef>
            
            <!-- Export archived interventions -->
            <classRef sourceName="gest.intervention.Intervention" destinationFile="InterventionArchived">
                <criteria field="mStatut" operator="==" value="0"/>
            </classRef>
        </module>
    </modules>
</database>
```

## Testing Your Configuration

After editing `migration-format.xml`:

1. **Reload** the migration structure in the UI (it should automatically detect the changes)
2. **Export** the module
3. **Check** the output directory to verify the files were created as expected
4. **Verify** the exported data matches your criteria

## Tips

### Finding Field Names

To find the correct field names to use in criteria:

1. Open the **Database Schema** tab
2. Find your class
3. Look at the field names (they usually start with `m`)
4. Use the exact field name (case-sensitive)

### Testing Criteria

Start with simple criteria first:
```xml
<criteria field="mID" operator=">" value="0"/>
```

Then add more complex filtering once you verify it works.

### Troubleshooting

**Problem**: No objects are exported
- **Solution**: Check that your criteria field names are correct and values are appropriate

**Problem**: All objects are exported (criteria ignored)
- **Solution**: Verify the field name exists in the class and is spelled correctly

**Problem**: Export fails with error
- **Solution**: Check XML syntax - make sure you have matching quotes and closing tags

## What's Next?

Future UI enhancements will add:
- Dialog to add/edit criteria without manual XML editing
- Field name picker with autocomplete
- Criteria preview showing how many objects match
- Validation of field names before export
