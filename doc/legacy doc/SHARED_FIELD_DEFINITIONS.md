# Shared Field Definitions in Reference Schema

## Overview

The reference schema now supports **shared field definitions**, allowing you to define a field once and reuse it across multiple classes. This ensures consistency and makes it easier to maintain fields that appear in many places with identical configurations.

## How It Works

### 1. Defining Shared Fields

Shared fields are defined in a top-level `<fields>` element in the reference schema XML. The `source` attribute acts as the unique identifier for the shared field definition:

```xml
<?xml version='1.0' encoding='UTF-8'?>
<classes>
    <fields>
        <!-- Define shared field definitions here (source is the key) -->
        <field source="mID" 
               destinationName="id" 
               isExported="true" 
               skipWhen="DEFAULT" 
               type="int" 
               title="Unique Identifier"
               description="Primary key identifier for the entity" />
        
        <field source="mNom"
               destinationName="nom"
               isExported="true"
               skipWhen="DEFAULT"
               type="string"
               title="Name"
               description="Display name of the entity" />
               
        <field source="mDateCreation"
               destinationName="dateCreation"
               isExported="true"
               skipWhen="DEFAULT"
               type="date"
               title="Creation Date"
               description="Date when the record was created" />
    </fields>
    
    <!-- Class definitions follow -->
    <class source="gest.employe.Employe" destinationName="Employe" isExported="true">
        <!-- Reference shared fields: source is class-specific, definition points to shared config -->
        <field source="mID" definition="mID" />
        <field source="mNom" definition="mNom" />
        <field source="mDateCreation" definition="mDateCreation" />
        
        <!-- Class-specific fields defined normally -->
        <field source="mPrenom" destinationName="prenom" isExported="true" type="string" />
    </class>
    
    <class source="gest.departement.Departement" destinationName="Departement" isExported="true">
        <!-- Reuse the same shared field definitions -->
        <field source="mID" definition="mID" />
        <field source="mNom" definition="mNom" />
        <field source="mDateCreation" definition="mDateCreation" />
        
        <field source="mCode" destinationName="code" isExported="true" type="string" />
    </class>
    
    <!-- Example: Different source name, same shared configuration -->
    <class source="gest.projet.Projet" destinationName="Projet" isExported="true">
        <!-- Class uses "mIDProjet" but inherits configuration from "mID" definition -->
        <field source="mIDProjet" definition="mID" />
        <field source="mNomProjet" definition="mNom" />
    </class>
</classes>
```

### 2. Using Shared Fields in Classes

To use a shared field in a class, specify both the class-specific `source` name and the `definition` to use:

```xml
<field source="mID" definition="mID" />
```

This allows flexibility - the actual field name in each class can vary (e.g., `mID`, `mIDEntite`, `mIdentifiant`) while sharing the same configuration. At load time, the schema reader:
1. Resolves the shared field definition using the `definition` attribute
2. Clones its configuration (type, export settings, etc.)
3. Applies the class-specific `source` name from the field element

### 3. Schema Loading Process

When the schema is loaded:

1. The `<fields>` element is parsed first, creating a map of shared field definitions
2. Each class is parsed, and field references are resolved
3. When a `<field definition="..." />` is encountered, the shared definition is cloned
4. The cloned field maintains a reference to its definition ID via `field.definitionId`

## UI Features

### Visual Highlighting

In the Schema Editor panel:
- **Shared fields are highlighted** with a light blue background in the fields table
- **Tooltip** shows "Shared field definition: [id]" when hovering over a shared field
- This makes it easy to identify which fields are shared at a glance

### Editing Shared Fields

When you edit a shared field using the Field Editor Dialog:
- A **warning banner** appears at the top in light blue
- The banner displays: "**Shared Field Definition:** [id]"
- It warns: "Changes made here will affect all classes using this shared field."
- This ensures you're aware when your changes will propagate to all usages

## Benefits

### 1. Consistency
All instances of a shared field (e.g., `mID`) will have identical configuration:
- Same type
- Same export settings
- Same skip conditions
- Same value mappings
- Same documentation (title/description)

### 2. Maintainability
When you need to change a field's configuration:
- Edit the shared definition once in the `<fields>` section
- The change automatically applies to all classes using that field
- No need to update each class individually

### 3. Reduced Errors
- Eliminates copy-paste errors
- Prevents inconsistent field configurations
- Ensures field behavior is uniform across the schema

### 4. Documentation
- Field title and description are defined once
- All usages inherit the same documentation
- Makes the schema self-documenting

## Common Use Cases
!-- In <fields> section: -->
<field source="mID" destinationName="id" type="int" 
       title="Entity ID" description="Unique identifier" />

<!-- In classes: -->
<field source="mID" definition="mID" />              <!-- Employe -->
<field source="mID" definition="mID" />              <!-- Departement -->
<field source="mIDProjet" definition="mID" />        <!-- Projet - different source name -->
<field source="mIdentifiant" definition="mID" />     <!-- Other - different source name -->
```

### Common Metadata Fields
```xml
<!-- In <fields> section: -->
<field source="mDateCreation" destinationName="dateCreation" type="date"
       title="Creation Date" />
       
<field source="mDateModification" destinationName="dateModification" type="date"
       title="Last Modified Date" />
       
<field source="mUtilisateurCreation" destinationName="createdBy" type="string"
       title="Created By" />

<!-- In classes: -->
<field source="mDateCreation" definition="mDateCreation" />
<field source="mDateModification" definition="mDateModification" />
<field source="mUtilisateurCreation" definition="mUtilisateurCreation" />
```

### Standard Status/State Fields
```xml
<!-- In <fields> section: -->
<field source="mActif" destinationName="actif" type="boolean"
       title="Active Status" description="Indicates if the record is active" />

<!-- In classes: -->
<field sourceActs as the unique key for the shared definitionActif" />
<field source="mEstActif" definition="mActif" />     <!-- Different source name -->
```

### Common Descriptive Fields
```xml
<!-- In <fields> section: -->
<field source="mNom" destinationName="nom" type="string"
       title="Name" description="Display name" />
       
<field source="mDescription" destinationName="description" type="string"
       title="Description" description="Detailed description" />

<!-- In classes: -->
<field source="mNom" definition="mNom" />
<field source="mNomProjet" definition="mNom" />      <!-- Different source name --
        using the `definition` attribute
- Each usage gets a **copy** of the shared definition's configuration
- The class-specific `source` name is preserved from the field element
- This allows field names to vary (e.g., `mID` vs `mIDProjet`) while sharing configurastinationName="description" type="string"
       title="Description" description="Detailed description" />
```

## Implementation Notes

### XML Schema Structure
The shared field definition must include all the attributes you want to standardize:
- `source` - The database field name
- `destinationName` - The export field name
- `type` - The data type
- `isExported` - Export flag
- `skipWhen` - Skip conditions (optional)
- `collection` - Collection flag (optional)
- `embedContents` - Esource names in shared definitions**
   - Use the most common field name as the `source` in the shared definition
   - e.g.,hildrenType` - For collections (optional)
- `title` - Display title (optional)
- `description` - Documentation (optional)
- `pointsTo` - Referenced class (optional)

### Field Resolution
- Field references are resolved at schema load time
- Each usage gets a **copy** of the shared definition
- Changes to the copy do NOT affect the shared definition (intentional design)
- To update all usages, edit the shared definition and reload the schema

### Backwards Compatibility
- Schemas without `<fields>` element continue to work normally
- You can mix shared and regular field definitions
- Existing schemas don't need to be modified

## Best Practices

1. **Define shared fields for common patterns**
   - ID fields across entities
   - Standard metadata (creation date, modified date, etc.)
   - Common status/state fields

2. **Use descriptive IDs**
   - `mID` not `field1`
   - `mDateCreation` not `dc`
   - Makes the schema self-documenting

3. **Document shared fields well**
   - Always include `title` and `description`
   - These become the standard documentation for all usages

4. **Group related fields**
   - Keep shared fields organized in the `<fields>` section
   - Consider adding XML comments to group related fields

5. **Test changes carefully**
   - Remember that editing a shared field affects ALL classes using it
   - Always reload the schema after modifying shared definitions
   - Test export behavior after changes

## Example: Migrating to Shared Fields

### Before (repetitive)
```xml
<class source="gest.employe.Employe" ...>
    <field source="mID" destinationName="id" type="int" isExported="true" skipWhen="DEFAULT" />
    <field source="mNom" destinationName="nom" type="string" isExported="true" skipWhen="DEFAULT" />
</class>

<class source="gest.departement.Departement" ...>

<class source="gest.projet.Projet" ...>
    <field source="mIDProjet" destinationName="id" type="int" isExported="true" skipWhen="DEFAULT" />
    <field source="mNomProjet" destinationName="nom" type="string" isExported="true" skipWhen="DEFAULT" />
</class>
```

### After (using shared fields)
```xml
<fields>
    <!-- Define once with source as the key -->
    <field source="mID" destinationName="id" type="int" isExported="true" skipWhen="DEFAULT" />
    <field source="mNom" destinationName="nom" type="string" isExported="true" skipWhen="DEFAULT" />
</fields>

<class source="gest.employe.Employe" ...>
    <!-- Use class-specific source, reference shared config -->
    <field source="mID" definition="mID" />
    <field source="mNom" definition="mNom" />
</class>

<class source="gest.departement.Departement" ...>
    <!-- Same source names, same definitions -->
    <field source="mID" definition="mID" />
    <field source="mNom" definition="mNom" />
</class>

<class source="gest.projet.Projet" ...>
    <!-- Different source names, but same shared configuration -->
    <field source="mIDProjet" definition="mID" />
    <field source="mNomProjet"
<class source="gest.departement.Departement" ...>
    <field definition="mID" />
    <field definition="mNom" />
</class>
```

## Technical Details

### Schema Model Changes
- `DOSchema` now has a `Map<String, DOSchemaField> sharedFields`
- `DOSchemaField` has a `String definitionId` to track shared field references
- `DOSchemaField.isSharedField()` checks if the field is a shared reference
- `DOSchemaField.copy()` creates deep copies when resolving references

### Reader Changes
- `DOReferenceSchemaReader.parseSharedFields()` parses the `<fields>` element
- `DOReferenceSchemaReader.parseFieldOrReference()` resolves field references
- Shared fields are loaded before classes are parsed

### Writer Changes
- `DOReferenceSchemaWriter.writeSharedField()` writes shared field definitions
- Shared fields are written in a `<fields>` block before classes
- Field references are written as `<field definition="..." />`

### UI Changes
- `SharedFieldRenderer` highlights shared fields in light blue
- `FieldEditorDialog` shows a warning banner when editing shared fields
- Tooltips indicate which fields are shared
