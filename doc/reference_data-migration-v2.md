# Migration4O XML Export Reference (v2 - Object-Specific Elements)

This document describes the XML export format for Migration4O database content, designed for schema compliance, data integrity, and optimal readability.

## Overview

The export generates one XML file per module containing strongly-typed data using object-specific elements with precise XSD validation.

This approach provides **complete schema validation** with precise field-level type checking, while maintaining readability and schema compliance. The XSD defines all object types and their structures, eliminating the need for separate type mappings.

## XML Structure

### Root Structure

```xml
<?xml version="1.0" encoding="UTF-8"?>
<migration xmlns="http://migration4o/schema"
           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           xsi:schemaLocation="http://migration4o/schema migration-schema.xsd">

    <!-- Strongly-typed data modules -->
    <modules>
        <module name="Dossier adresse">
            <objects>
                <!-- Strongly-typed objects using object-specific elements -->
                <PersonneRess id="12345">
                    <!-- ... -->
                </PersonneRess>
            </objects>
        </module>
    </modules>
</migration>
```

**Note:** The XSD schema fully defines all object types and their structures, eliminating the need for a separate type definitions section.

## Naming Conventions

The v2 format uses consistent naming conventions to distinguish between different XML element types:

**PascalCase (First letter uppercase):**
- Object type names: `<PersonneRess>`, `<DossierAdresse>`, `<VilleGeo>`
- Reference elements: `<SpecialiteRef id="10"/>`, `<VilleGeoRef id="123"/>`
- Embedded object elements: `<Adresse>`, `<Periodicite>`

**camelCase (First letter lowercase):**
- Field names: `<nom>`, `<dateCreation>`, `<personneRessource>`
- Collection wrapper elements: `<vectSpecialite>`, `<vectVilleEntraide>`
- Special ID fields: `<id>`, `<idssi>`, `<idDossPrev>`

**Reference Elements:**
Reference elements use the pattern `<{ObjectType}Ref id="123"/>`:
- Element name: PascalCase object type + "Ref" suffix
- `id` attribute: Contains the referenced object's ID
- Self-closing: `<VilleGeoRef id="26502107"/>`
- Makes the reference relationship explicit and matches the actual object's `id` attribute

This convention makes it immediately clear:
- PascalCase = Types (objects and their references)
- camelCase = Data (fields and collection containers)

### Data Modules

Each `<module>` contains objects organized by their logical grouping:

```xml
<modules>
    <module name="Dossier adresse">
        <objects>
            <PersonneRess id="12345">
                <!-- Strongly-typed fields as child elements -->
            </PersonneRess>
        </objects>
    </module>
</modules>
```

## Object Structure

### Simple Object with Basic Fields

```xml
<PersonneRess id="12345">
    <nom>Jean Dupont</nom>
    <couriel>jean.dupont@example.com</couriel>
    <cell>514-555-1234</cell>
    <personneRessource>true</personneRessource>
    <contactIntervention>true</contactIntervention>
    <handicape>false</handicape>
    <statut>1</statut>
    <idssi>1</idssi>
</PersonneRess>
```

**Field Name Cleaning:**
- Field names follow the same cleaning rules as v1 (mXxx → xxx, ID handling, camelCase)
- Each field becomes a child element with the cleaned name
- Field values are element text content with appropriate XSD type validation

### Object with References

References to other objects contain the object ID as text content:

```xml
<PersonneRess id="12345">
    <nom>Jean Dupont</nom>
    <couriel>jean.dupont@example.com</couriel>
    
    <!-- Reference fields contain the target object ID -->
    <idDossPrev>54321</idDossPrev>
    <idLangue>1</idLangue>
</PersonneRess>
```

**Reference Field Attributes:**
In the XSD, reference fields are defined with annotations or specific types that indicate they reference another object type.

### Object with Embedded Objects

When an object is referenced by only one parent, it's embedded directly as a nested element:

```xml
<DossierAdresse id="54321">
    <matricule>2024-001</matricule>
    
    <!-- Embedded object as nested element -->
    <adresse>
        <Adresse id="99001">
            <noCivique>123</noCivique>
            <rue>Rue Principale</rue>
            <ville>Montreal</ville>
            <codePostal>H3H 1A1</codePostal>
        </Adresse>
    </adresse>
</DossierAdresse>
```

## Collections

Collections are handled as wrapper elements containing either values or nested objects:

### Collection of References

```xml
<ParamConfigSSI id="100">
    <prefixe>SSI01</prefixe>
    
    <!-- Collection of references to Specialite objects -->
    <vectSpecialite>
        <SpecialiteRef id="10"/>
        <SpecialiteRef id="15"/>
        <SpecialiteRef id="22"/>
    </vectSpecialite>
</ParamConfigSSI>
```

**Collection of References Structure:**
- Collection field becomes a wrapper element in camelCase (e.g., `<vectSpecialite>`)
- Each reference is a self-closing child element: `<{ElementType}Ref id="123"/>`
- The element name uses PascalCase with `Ref` suffix (e.g., `SpecialiteRef`)
- The `id` attribute contains the referenced object's ID, making the relationship explicit

### Collection of Embedded Objects

```xml
<ParamConfigSSI id="100">
    <!-- Collection of embedded VilleGeo objects -->
    <vectVilleDesservie>
        <VilleGeo id="45001">
            <nom>Montreal</nom>
            <codeGeo>45001</codeGeo>
            <province>QC</province>
        </VilleGeo>
        <VilleGeo id="45002">
            <nom>Laval</nom>
            <codeGeo>45002</codeGeo>
            <province>QC</province>
        </VilleGeo>
    </vectVilleDesservie>
</ParamConfigSSI>
```

**Collection of Embedded Objects Structure:**
- Collection field becomes a wrapper element (e.g., `<vectVilleDesservie>`)
- Each embedded object is a full object element with all its fields
- Objects in collections have the same structure as top-level objects

### Collection of Primitives

```xml
<ParamConfigGeneral id="101">
    <nbrEnregMaxRequete>1000</nbrEnregMaxRequete>
    
    <!-- Collection of integers -->
    <vectIDSousSSI>
        <sousSSI>100</sousSSI>
        <sousSSI>101</sousSSI>
        <sousSSI>105</sousSSI>
        <sousSSI>110</sousSSI>
    </vectIDSousSSI>
</ParamConfigGeneral>
```

**Collection of Primitives Structure:**
- Collection field becomes a wrapper element (e.g., `<vectIDSousSSI>`)
- Each value is a child element with a singular form of the collection name or a generic name
- For primitive collections, use a descriptive singular element name

## Complex Example: Intervention Data

Here's an example showing complex intervention data with multiple collections and nested objects:

```xml
<Intervention id="67890">
    <id>67890</id>
    <numActivite>2024-INC-001</numActivite>
    <dateActivite>2024-11-15T14:30:00</dateActivite>
    <dateCreation>2024-11-15T14:32:15</dateCreation>
    <lieu>123 Rue de l'Incendie</lieu>
    <lieuCodePost>H3H 1A1</lieuCodePost>
    <statut>2</statut>
    <coutTotal>15750.50</coutTotal>
    <associeDossierPrev>true</associeDossierPrev>
    <genererDSI2003>true</genererDSI2003>
    
    <!-- Embedded complex object -->
    <carteAppel>
        <CarteAppel id="67901">
            <heureAppel>2024-11-15T14:30:00</heureAppel>
            <nomAppelant>Marie Tremblay</nomAppelant>
            <telephoneAppelant>514-555-9876</telephoneAppelant>
            <descriptionInitiale>Incendie dans un garage résidentiel</descriptionInitiale>
        </CarteAppel>
    </carteAppel>
    
    <lieuInterv>
        <Lieu id="67902">
            <noCivique>123</noCivique>
            <rue>Rue de l'Incendie</rue>
            <ville>Montreal</ville>
            <codePostal>H3H 1A1</codePostal>
            <coordGPS>45.5017,-73.5673</coordGPS>
        </Lieu>
    </lieuInterv>
    
    <!-- Reference to object in another module -->
    <typeEvenement>C110</typeEvenement>
    
    <!-- Collection of embedded objects -->
    <vectIntervVehicule>
        <IntervVehicule id="67910">
            <numeroVehicule>PUMP-01</numeroVehicule>
            <heureDepart>2024-11-15T14:35:00</heureDepart>
            <heureArrivee>2024-11-15T14:42:00</heureArrivee>
            <heureRetour>2024-11-15T16:15:00</heureRetour>
            <kmDepart>85420</kmDepart>
            <kmRetour>85438</kmRetour>
        </IntervVehicule>
        <IntervVehicule id="67911">
            <numeroVehicule>LADDER-01</numeroVehicule>
            <heureDepart>2024-11-15T14:37:00</heureDepart>
            <heureArrivee>2024-11-15T14:45:00</heureArrivee>
            <heureRetour>2024-11-15T16:20:00</heureRetour>
            <kmDepart>42380</kmDepart>
            <kmRetour>42395</kmRetour>
        </IntervVehicule>
    </vectIntervVehicule>
    
    <!-- Collection of references to objects in other modules -->
    <vectHistoActivite>
        <HistoActiviteRef id="78001"/>
        <HistoActiviteRef id="78002"/>
        <HistoActiviteRef id="78003"/>
    </vectHistoActivite>
    
    <!-- Collection with renfort cards (additional interventions) -->
    <vectCarteAppelRenfort>
        <CarteAppelRef id="67920"/>
        <CarteAppelRef id="67921"/>
    </vectCarteAppelRenfort>
</Intervention>
```

## Unreached Objects

Objects not reachable from module roots are exported separately in their own module:

```xml
<module name="Unreached">
    <objects>
        <!-- Orphaned objects grouped by type -->
        <VilleGeo id="90001">
            <nom>Ville Abandonnée</nom>
            <codeGeo>99999</codeGeo>
            <province>QC</province>
        </VilleGeo>
        <VilleGeo id="90002">
            <nom>Ancienne Municipalité</nom>
            <codeGeo>99998</codeGeo>
            <province>QC</province>
        </VilleGeo>
        <PhotoFich id="90100">
            <nom>obsolete_image.png</nom>
            <cheminFichier>/temp/obsolete_image.png</cheminFichier>
            <taille>12800</taille>
            <dateModification>2020-03-15T09:20:00</dateModification>
        </PhotoFich>
    </objects>
</module>
```

## Field Name Processing

Field names are automatically converted from Java conventions to XML conventions using the `toXmlFieldName()` method:

- **"m" prefix removal**: Field names following the pattern `mXxx` (where X is uppercase) have their "m" prefix removed
  - `mNom` → `nom`
  - `mDateCreation` → `dateCreation`
  - `mVectSpecialite` → `vectSpecialite`
- **Special ID field handling**: ID fields that START with ID get special lowercase treatment:
  - `mID` → `id` (fully lowercase)
  - `mIDSSI` → `idssi` (fully lowercase)
  - `IDDossPrev` → `idDossPrev` (lowercase 'id' prefix only)
  - `VectIDSousSSI` → `vectIDSousSSI` (ID in middle stays uppercase)
- **camelCase conversion**: First letter is converted to lowercase for all fields
  - `Nom` → `nom`
  - `DateCreation` → `dateCreation`
  - `VectSpecialite` → `vectSpecialite` (collection wrapper)
- **Non-"m" fields**: Fields not following the mXxx pattern are still processed for camelCase
  - `Name` → `name`
  - `CreationDate` → `creationDate`

**Note:** Object type names (like `PersonneRess`, `VilleGeo`) remain in PascalCase and are not processed by `toXmlFieldName()`. This creates a clear visual distinction between object types (PascalCase) and field names (camelCase).

## Data Validation Strategy

The v2 export format uses a multi-layered validation approach to ensure data quality and integrity.

### XSD Validation (Structural)

The XSD schema validates:
- ✅ **Element names**: Only valid object types allowed (e.g., `<PersonneRess>`, not `<Foo>`)
- ✅ **Field names**: Only fields defined in schema for each object type
- ✅ **Field data types**: String, int, boolean, dateTime, decimal validation
- ✅ **Structure**: Required vs optional elements, correct nesting
- ✅ **Collection wrappers**: Proper collection element structure

### Database Loading Validation (Semantic)

During export, the database loading code performs deeper validation:
- ✅ **Reference integrity**: All object references point to existing objects
- ✅ **Reference types**: Referenced objects match expected types from schema
- ✅ **Collection state**: Collections are properly initialized (not null)
- ✅ **Field accessibility**: Object activation and field resolution work correctly

### What XSD Cannot Validate

While comprehensive, XSD has inherent limitations:

**Reference Integrity**: XSD validates that `<idDossPrev>99999</idDossPrev>` is a number, but cannot verify object 99999 exists
- **Handled by**: Database loading validates all references
- **Invalid references**: Exported as-is and logged in anomaly files

**Reference Target Types**: XSD cannot verify that ID 54321 references a DossierAdresse vs other type
- **Handled by**: Schema-based type checking during database loading
- **Type mismatches**: Reported in anomaly files

**Field Value Constraints**: XSD validates type but not value ranges (e.g., valid status codes)
- **Solution**: Business rule validation post-import if needed

**Business Rules**: Complex constraints like date ranges, conditional requirements
- **Solution**: Application-level validation after import

### Anomaly Detection and Reporting

Invalid references and data quality issues are detected during export and reported separately rather than failing the export.

**Anomaly Types Detected**:
1. **Invalid References**: References to non-existent objects
2. **Type Mismatches**: References to wrong object types
3. **Null Collections**: Collection fields that are null vs empty

**Anomaly File Location**: `output/migration/anomalies/{ModuleName}_anomalies.txt`

**Example: `Dossier_adresse_anomalies.txt`**
```
=== Data Anomalies Report ===
Module: Dossier adresse
Export Date: 2024-11-26 14:30:00
Total Objects Processed: 15,234
Anomalies Found: 3

[INVALID_REFERENCE]
Object: gest.dossPrev.PersonneRess#12345
Field: mIDDossPrev
Referenced ID: 99999 (DossPrev)
Issue: Referenced object does not exist in database
Action: Field exported as <idDossPrev>99999</idDossPrev> with validation warning

[TYPE_MISMATCH]
Object: gest.dossPrev.PersonneRess#12350
Field: mIDLangue
Referenced ID: 456
Expected Type: gest.config.Langue
Actual Type: gest.config.VilleGeo
Issue: Referenced object is wrong type
Action: Field exported as <idLangue>456</idLangue> with validation warning

[NULL_COLLECTION]
Object: gest.config.ParamConfigSSI#100
Field: mVectSpecialite
Issue: Collection field is null instead of empty Vector
Action: Field exported as empty <vectSpecialite></vectSpecialite>

=== Summary ===
INVALID_REFERENCE: 1
TYPE_MISMATCH: 1
NULL_COLLECTION: 1
```

**Export Behavior with Anomalies**:
1. **Continue Export**: Anomalies don't stop the export process
2. **Export As-Is**: Invalid references exported with their ID values
3. **Detailed Logging**: All anomalies logged to module-specific anomaly files
4. **Summary Report**: Anomaly counts included in export summary

This approach ensures migration can complete while highlighting data quality issues that need manual review and correction.

## XSD Schema Structure

The XSD provides comprehensive validation with object-specific complex types:

### Root Structure

```xsd
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
           targetNamespace="http://migration4o/schema"
           xmlns:m4o="http://migration4o/schema"
           elementFormDefault="qualified">

    <!-- Root element -->
    <xs:element name="migration">
        <xs:complexType>
            <xs:sequence>
                <xs:element name="modules" type="m4o:ModulesType"/>
            </xs:sequence>
        </xs:complexType>
    </xs:element>
    
    <!-- Object-specific complex types for each class -->
    
</xs:schema>
```

### Object-Specific Complex Types

Each object type gets its own complex type definition with exact field specifications:

```xsd
<!-- PersonneRess object type -->
<xs:element name="PersonneRess">
    <xs:complexType>
        <xs:sequence>
            <xs:element name="nom" type="xs:string" minOccurs="0"/>
            <xs:element name="couriel" type="xs:string" minOccurs="0"/>
            <xs:element name="cell" type="xs:string" minOccurs="0"/>
            <xs:element name="personneRessource" type="xs:boolean" minOccurs="0"/>
            <xs:element name="contactIntervention" type="xs:boolean" minOccurs="0"/>
            <xs:element name="handicape" type="xs:boolean" minOccurs="0"/>
            <xs:element name="statut" type="xs:int" minOccurs="0"/>
            <xs:element name="idssi" type="xs:int" minOccurs="0"/>
            <xs:element name="idDossPrev" type="xs:long" minOccurs="0"/> <!-- Reference field -->
            <xs:element name="idLangue" type="xs:long" minOccurs="0"/> <!-- Reference field -->
        </xs:sequence>
        <xs:attribute name="id" type="xs:long" use="required"/>
    </xs:complexType>
</xs:element>
```

### Collection Types

Collections are defined with wrapper elements containing specific child elements:

```xsd
<!-- Collection of references -->
<xs:element name="vectSpecialite">
    <xs:complexType>
        <xs:sequence>
            <xs:element name="specialiteRef" type="xs:long" minOccurs="0" maxOccurs="unbounded"/>
        </xs:sequence>
    </xs:complexType>
</xs:element>

<!-- Collection of embedded objects -->
<xs:element name="vectVilleDesservie">
    <xs:complexType>
        <xs:sequence>
            <xs:element ref="m4o:VilleGeo" minOccurs="0" maxOccurs="unbounded"/>
        </xs:sequence>
    </xs:complexType>
</xs:element>

<!-- Collection of primitives -->
<xs:element name="vectIDSousSSI">
    <xs:complexType>
        <xs:sequence>
            <xs:element name="sousSSI" type="xs:int" minOccurs="0" maxOccurs="unbounded"/>
        </xs:sequence>
    </xs:complexType>
</xs:element>
```

## Schema Benefits

### 1. Complete Validation
- **Structure validation**: XML must follow the exact hierarchy with correct element names
- **Type safety**: All fields validated against their declared XSD types (string, int, boolean, dateTime, etc.)
- **Field-level validation**: Each object type has exact field definitions
- **Required fields**: XSD can specify which fields are required vs optional
- **Reference integrity**: Reference fields validated as numeric IDs

### 2. Tool Support
- **XML Editors**: Provide autocomplete for valid element names specific to each object type
- **Code Generation**: Can generate strongly-typed classes directly from XSD
- **Runtime Validation**: Comprehensive validation before processing
- **Documentation**: Auto-generate precise API documentation with field-level details

### 3. Key Patterns Summary

1. **Object elements**: Each object type uses its own element name (e.g., `<PersonneRess>`, `<Intervention>`)
2. **Field elements**: Each field is a child element with cleaned name
3. **Simple values**: Element text content (e.g., `<nom>Jean Dupont</nom>`)
4. **References**: Element text contains target object ID (e.g., `<idDossPrev>54321</idDossPrev>`)
5. **Collections of primitives**: Wrapper element with repeated child elements (e.g., `<vectIDSousSSI><sousSSI>100</sousSSI>...</vectIDSousSSI>`)
6. **Collections of references**: Wrapper element with `{type}Ref` child elements containing IDs
7. **Collections of embedded objects**: Wrapper element with full object elements
8. **Embedded objects**: Field element contains nested object element

This structure ensures complete data fidelity with maximum XSD validation capability, providing strong guarantees about data structure and types.

## Reference Validation and Anomaly Detection

During export, the database loading code validates all object references. Invalid references are detected and reported as anomalies rather than causing export failures.

### Anomaly Types

**Invalid References**: References to objects that don't exist in the database
```
Object: PersonneRess#12345
Field: idDossPrev
Issue: References non-existent object ID 99999
```

**Type Mismatches**: References pointing to objects of unexpected types
```
Object: PersonneRess#12345
Field: idLangue (expected: Langue)
Issue: Object 456 is VilleGeo, not Langue
```

**Null Collections**: Collection fields that are null when they should be empty vectors
```
Object: ParamConfigSSI#100
Field: vectSpecialite
Issue: Collection is null instead of empty Vector
```

### Anomaly Reporting

All detected anomalies are exported to:
```
output/migration/anomalies/{ModuleName}_anomalies.txt
```

**Example: `output/migration/anomalies/Dossier_adresse_anomalies.txt`**
```
=== Data Anomalies Report ===
Module: Dossier adresse
Export Date: 2024-11-26 14:30:00
Total Objects Processed: 15,234
Anomalies Found: 3

[INVALID_REFERENCE]
Object: gest.dossPrev.PersonneRess#12345
Field: mIDDossPrev
Referenced ID: 99999 (DossPrev)
Issue: Referenced object does not exist in database
Action: Field exported as <idDossPrev>99999</idDossPrev> with validation warning

[INVALID_REFERENCE]
Object: gest.dossPrev.PersonneRess#12350
Field: mIDLangue
Referenced ID: 456 (Langue)
Issue: Referenced object does not exist in database
Action: Field exported as <idLangue>456</idLangue> with validation warning

[NULL_COLLECTION]
Object: gest.config.ParamConfigSSI#100
Field: mVectSpecialite
Issue: Collection field is null instead of empty Vector
Action: Field exported as empty <vectSpecialite></vectSpecialite>

=== Summary ===
INVALID_REFERENCE: 2
NULL_COLLECTION: 1
```

### Export Behavior with Anomalies

1. **Continue Export**: Anomalies don't stop the export process
2. **Export As-Is**: Invalid references are exported with their ID values
3. **Log Details**: All anomalies logged to anomaly files
4. **Summary Report**: Count of anomalies per module in export summary

This allows the migration to complete while highlighting data quality issues that need manual review.

## Performance Considerations

- Use streaming XML writing (StAX) to handle very large databases
- One XML file per module for optimal file size management
- Progress reporting through `DOEngineMonitoring`
- Memory-efficient processing of large object graphs
- Reference validation performed during database loading phase
- Anomaly detection has minimal performance impact
- XSD validation can be performed during or after export

## Migration from v1 Format

The main differences from the generic field-based format (v1):

| Aspect | v1 (Generic Fields) | v2 (Object-Specific Elements) |
|--------|---------------------|-------------------------------|
| Root structure | `<types>` + `<modules>` | `<modules>` only |
| Object elements | `<object type="PersonneRess">` | `<PersonneRess>` |
| Field representation | `<field name="nom" type="string">value</field>` | `<nom>value</nom>` |
| Type mapping | Required in `<types>` section | Implicit in element names + XSD |
| XSD validation | Generic field validation only | Precise field-level validation |
| Schema complexity | Simpler | More comprehensive |
| Validation strength | Weak (structure only) | Strong (structure + types + fields) |
| Readability | Moderate | High |
| XSD file size | Small | Larger (one definition per object type) |

Both formats are valid approaches - v2 provides stronger validation at the cost of a more complex XSD.
