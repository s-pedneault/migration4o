# Migration4O XML Export Reference

This document describes the XML export format for Migration4O database content, designed for schema compliance, data integrity, and optimal readability.

## Overview

The export generates a single XML file per database with a robust two-part structure:

1. **Type Definitions Section**: Maps simple type names to full Java class names
2. **Data Modules Section**: Clean, optimized data using simple type references

This approach provides **complete schema validation** while minimizing redundancy in data files.

## XML Structure

### Root Structure

```xml
<?xml version="1.0" encoding="UTF-8"?>
<migration xmlns="http://migration4o/schema"
           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           xsi:schemaLocation="http://migration4o/schema migration-schema.xsd">

    <!-- Type definitions (auto-generated from migration-schema.xml) -->
    <types>
        <type name="PersonneRess" class="gest.dossPrev.PersonneRess" module="Dossier adresse"/>
        <type name="DossierAdresse" class="gest.dossPrev.DossPrev" module="Dossier adresse"/>
        <type name="ParamConfigSSI" class="gest.config.ParamConfigSSI" module="Paramètres"/>
        <!-- ... complete type mapping -->
    </types>

    <!-- Clean data modules -->
    <modules>
        <module name="Ressources">
            <objects>
                <!-- Clean objects using simple type references -->
            </objects>
        </module>
    </modules>
</migration>
```

### Type Definitions

The `<types>` section defines the mapping between simple type names and full Java classes:

```xml
<types>
    <type name="PersonneRess" class="gest.dossPrev.PersonneRess" module="Dossier adresse"/>
    <type name="DossierAdresse" class="gest.dossPrev.DossPrev" module="Dossier adresse"/>
    <type name="Employe" class="gest.employe.Employe" module="Organisation"/>
    <type name="ParamConfigSSI" class="gest.config.ParamConfigSSI" module="Paramètres"/>
    <type name="Adresse" class="gest.gen.Adresse" module="General"/>
    <type name="Specialite" class="gest.specialite.Specialite" module="Parametres"/>
</types>
```

**Attributes:**
- `name`: Simple type identifier (from schema simpleName)
- `class`: Full Java class name
- `module`: Logical module grouping

### Data Modules

Each `<module>` contains objects organized by their logical grouping:

```xml
<modules>
    <module name="Dossier adresse">
        <objects>
            <object type="PersonneRess" id="12345">
                <!-- Fields with cleaned names -->
            </object>
        </objects>
    </module>
</modules>
```

## Object Structure

### Simple Object with Basic Fields

```xml
<object type="PersonneRess" id="12345">
    <field name="nom" type="string">Jean Dupont</field>
    <field name="couriel" type="string">jean.dupont@example.com</field>
    <field name="cell" type="string">514-555-1234</field>
    <field name="personneRessource" type="boolean">true</field>
    <field name="contactIntervention" type="boolean">true</field>
    <field name="handicape" type="boolean">false</field>
    <field name="statut" type="int">1</field>
    <field name="idssi" type="int">1</field>
</object>
```

### Object with References

References to other objects use `targetType` and `targetModule` attributes:

```xml
<object type="PersonneRess" id="12345">
    <field name="nom" type="string">Jean Dupont</field>
    <field name="couriel" type="string">jean.dupont@example.com</field>
    
    <!-- ID object reference - points to actual target object -->
    <field name="idDossPrev" type="reference" targetType="DossierAdresse" targetModule="Dossier_adresse">54321</field>
    <field name="idLangue" type="reference" targetType="Langue" targetModule="Parametres">1</field>
</object>
```

### Object with Embedded Objects

When an object is referenced by only one parent, it's embedded directly:

```xml
<object type="DossierAdresse" id="54321">
    <field name="matricule" type="string">2024-001</field>
    
    <!-- Embedded object -->
    <field name="adresse" type="embedded">
        <object type="Adresse" id="99001">
            <field name="noCivique" type="int">123</field>
            <field name="rue" type="string">Rue Principale</field>
            <field name="ville" type="string">Montreal</field>
            <field name="codePostal" type="string">H3H 1A1</field>
        </object>
    </field>
</object>
```

## Collections

Collections are handled as fields with specific `elementType` attributes:

### Collection of References

```xml
<object type="ParamConfigSSI" id="100">
    <field name="prefixe" type="string">SSI01</field>
    
    <!-- Collection of ID references to Specialite objects -->
    <field name="vectSpecialite" type="collection" elementType="reference" elementClass="Specialite" elementModule="Parametres">
        <value>10</value>
        <value>15</value>
        <value>22</value>
    </field>
</object>
```

### Collection of Embedded Objects

```xml
<object type="ParamConfigSSI" id="100">
    <!-- Collection of embedded VilleGeo objects -->
    <field name="vectVilleDesservie" type="collection" elementType="embedded" elementClass="VilleGeo" elementModule="Parametres">
        <object type="VilleGeo" id="45001">
            <field name="nom" type="string">Montreal</field>
            <field name="codeGeo" type="string">45001</field>
            <field name="province" type="string">QC</field>
        </object>
        <object type="VilleGeo" id="45002">
            <field name="nom" type="string">Laval</field>
            <field name="codeGeo" type="string">45002</field>
            <field name="province" type="string">QC</field>
        </object>
    </field>
</object>
```

### Collection of Primitives

```xml
<object type="ParamConfigGeneral" id="101">
    <field name="nbrEnregMaxRequete" type="int">1000</field>
    
    <!-- Collection of integers -->
    <field name="vectIDSousSSI" type="collection" elementType="int">
        <value>100</value>
        <value>101</value>
        <value>105</value>
        <value>110</value>
    </field>
</object>
```

## Complex Example: Intervention Data

Here's an example showing complex intervention data with multiple collections and nested objects:

```xml
<object type="Intervention" id="67890">
    <field name="id" type="int">67890</field>
    <field name="numActivite" type="string">2024-INC-001</field>
    <field name="dateActivite" type="date">2024-11-15T14:30:00</field>
    <field name="dateCreation" type="date">2024-11-15T14:32:15</field>
    <field name="lieu" type="string">123 Rue de l'Incendie</field>
    <field name="lieuCodePost" type="string">H3H 1A1</field>
    <field name="statut" type="int">2</field>
    <field name="coutTotal" type="double">15750.50</field>
    <field name="associeDossierPrev" type="boolean">true</field>
    <field name="genererDSI2003" type="boolean">true</field>
    
    <!-- Embedded complex objects -->
    <field name="carteAppel" type="embedded">
        <object type="CarteAppel" id="67901">
            <field name="heureAppel" type="date">2024-11-15T14:30:00</field>
            <field name="nomAppelant" type="string">Marie Tremblay</field>
            <field name="telephoneAppelant" type="string">514-555-9876</field>
            <field name="descriptionInitiale" type="string">Incendie dans un garage résidentiel</field>
        </object>
    </field>
    
    <field name="lieuInterv" type="embedded">
        <object type="Lieu" id="67902">
            <field name="noCivique" type="int">123</field>
            <field name="rue" type="string">Rue de l'Incendie</field>
            <field name="ville" type="string">Montreal</field>
            <field name="codePostal" type="string">H3H 1A1</field>
            <field name="coordGPS" type="string">45.5017,-73.5673</field>
        </object>
    </field>
    
    <!-- ID references to other modules -->
    <field name="typeEvenement" type="reference" targetType="Dsi2003C1" targetModule="Parametres">C110</field>
    
    <!-- Collections of embedded objects -->
    <field name="vectIntervVehicule" type="collection" elementType="embedded" elementClass="IntervVehicule" elementModule="Intervention">
        <object type="IntervVehicule" id="67910">
            <field name="numeroVehicule" type="string">PUMP-01</field>
            <field name="heureDepart" type="date">2024-11-15T14:35:00</field>
            <field name="heureArrivee" type="date">2024-11-15T14:42:00</field>
            <field name="heureRetour" type="date">2024-11-15T16:15:00</field>
            <field name="kmDepart" type="int">85420</field>
            <field name="kmRetour" type="int">85438</field>
        </object>
        <object type="IntervVehicule" id="67911">
            <field name="numeroVehicule" type="string">LADDER-01</field>
            <field name="heureDepart" type="date">2024-11-15T14:37:00</field>
            <field name="heureArrivee" type="date">2024-11-15T14:45:00</field>
            <field name="heureRetour" type="date">2024-11-15T16:20:00</field>
            <field name="kmDepart" type="int">42380</field>
            <field name="kmRetour" type="int">42395</field>
        </object>
    </field>
    
    <!-- Collection of references to objects in other modules -->
    <field name="vectHistoActivite" type="collection" elementType="reference" elementClass="HistoActivite" elementModule="Organisation">
        <value>78001</value>
        <value>78002</value>
        <value>78003</value>
    </field>
    
    <!-- Collection with renfort cards (additional interventions) -->
    <field name="vectCarteAppelRenfort" type="collection" elementType="reference" elementClass="CarteAppel" elementModule="Intervention">
        <value>67920</value>
        <value>67921</value>
    </field>
</object>
```

## Unreached Objects

Objects not reachable from module roots are exported separately in their own module:

```xml
<module name="Unreached">
    <objects>
        <!-- Orphaned objects grouped by type -->
        <object type="VilleGeo" id="90001">
            <field name="nom" type="string">Ville Abandonnée</field>
            <field name="codeGeo" type="string">99999</field>
            <field name="province" type="string">QC</field>
        </object>
        <object type="VilleGeo" id="90002">
            <field name="nom" type="string">Ancienne Municipalité</field>
            <field name="codeGeo" type="string">99998</field>
            <field name="province" type="string">QC</field>
        </object>
        <object type="PhotoFich" id="90100">
            <field name="nom" type="string">obsolete_image.png</field>
            <field name="cheminFichier" type="string">/temp/obsolete_image.png</field>
            <field name="taille" type="int">12800</field>
            <field name="dateModification" type="date">2020-03-15T09:20:00</field>
        </object>
    </objects>
</module>
```

## Field Name Processing

The generic export engine automatically cleans field names to improve readability:

- **"m" prefix removal**: Field names following the pattern `mXxx` (where X is uppercase) have their "m" prefix removed
  - `mNom` → `nom`
  - `mDateCreation` → `dateCreation`
- **Special ID field handling**: ID fields that START with ID get special lowercase treatment:
  - `mID` → `id` (fully lowercase)
  - `mIDSSI` → `idssi` (fully lowercase)
  - `IDDossPrev` → `idDossPrev` (lowercase 'id' prefix only)
  - `VectIDSousSSI` → `vectIDSousSSI` (ID in middle stays uppercase)
- **camelCase conversion**: First letter is converted to lowercase for all cleaned fields
  - `Nom` → `nom`
  - `DateCreation` → `dateCreation`
  - `VectSpecialite` → `vectSpecialite`
- **Non-"m" fields**: Fields not following the mXxx pattern are still processed for camelCase
  - `Name` → `name`
  - `CreationDate` → `creationDate`

This processing is handled centrally in the `GenericExportEngineImpl.cleanFieldName()` method, so all export formats automatically benefit from cleaner field names without duplicating this logic.

## Schema Benefits

### 1. Complete Validation
- **Structure validation**: XML must follow the exact hierarchy
- **Type safety**: All object types must be defined in the `<types>` section
- **Reference integrity**: All `targetType` references must point to valid types
- **Data validation**: Field values must match their declared types

### 2. Tool Support
- **XML Editors**: Provide autocomplete for valid type names
- **Code Generation**: Can generate classes directly from schema
- **Runtime Validation**: Validate XML files before processing
- **Documentation**: Auto-generate API documentation

### 3. Key Patterns Summary

1. **Type definitions**: Map simple names to full class names once
2. **Simple fields**: `<field name="nom" type="string">Jean Dupont</field>`
3. **References**: `<field name="idDossPrev" type="reference" targetType="DossierAdresse" targetModule="Dossier_adresse">54321</field>`
4. **Collections**: Use `elementType` and `elementClass` attributes with `<value>` or nested `<object>` elements
5. **Embedded objects**: Full object nested when not shared elsewhere

This structure ensures complete data fidelity while maintaining clear relationships between objects across modules and providing robust schema validation.

## Performance Considerations

- Use streaming XML writing (StAX) to handle very large databases
- Single XML file with modular structure reduces file I/O overhead
- Type definitions loaded once, referenced many times
- Progress reporting through `DOEngineMonitoring`
- Memory-efficient processing of large object graphs