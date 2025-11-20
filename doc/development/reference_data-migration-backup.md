# DB4O database migration engine

Now that we have a fully-functional DB4O database reader and resolver, and test migration process to Excel, we need to build a complete migration engine.

We will be building an XML export engine (in package `dataobjects/impl/migration/xml/`), which receives a fully-initialized `DOEngine`. 

## Output Structure

The XML engine must export several files:

### 1. XML Schema (`premligne-schema.xml`)
An XSD (XML Schema Definition) that describes the structure of all exported data files.
- Namespace: `migration4o`
- Defines complex types for each `DOClass` in the schema
- Defines simple types for primitive fields and enumerations
- Handles inheritance relationships
- Supports both nested objects and ID/IDREF references

### 2. XML Data Files (`premligne-data/`)
A folder containing XML files with the complete database export:
- **One XML file per `DOSchemaModule`** (e.g., `[module-name].xml`)
- **One additional file for unreached objects** (`unreached.xml`)
- All files conform to the generated XSD schema
- Uses streaming XML writing for performance with large databases

#### Object Reference Strategy
- **Nested objects**: Used when an object is referenced by only one parent (not shared)
- **ID/IDREF references**: Used when an object is referenced by multiple parents
- Each object exported in a separate tree includes:
  - `type`: The fully-qualified class name
  - `id`: The unique object identifier
  - All field values

#### Unreached Objects
Objects not reachable from the root object(s) are exported to `unreached.xml` for investigation, as all objects in the database are expected to be reachable for full migration.

### 3. Migration Report (`premligne-report.xml`)
A comprehensive XML report documenting the migration:
- Complete database structure (modules, classes, fields)
- Statistics: number of objects migrated per class
- Reachability information:
  - Count of reached objects per class
  - Count of unreached objects per class
  - Total object count per class
- Module organization
- Migration metadata (date, source database, engine version)
- Warnings/issues encountered during migration

## Example Output

If we import `54060/premligne.dat`, we should end up with:
```
54060/migration/
├── premligne-schema.xml           # XSD schema definition
├── premligne-data/                # Data export folder
│   ├── [module1].xml              # One file per module
│   ├── [module2].xml
│   ├── ...
│   └── unreached.xml              # Unreached objects (for investigation)
└── premligne-report.xml           # Migration statistics and report
```

## Sample XML Data Output Structure

This section presents examples of the XML data output format, demonstrating all the different object relationship patterns found in the database. Examples use actual class and field names from the migration schema.

### Basic Object with Primitive Fields

```xml
<?xml version="1.0" encoding="UTF-8"?>
<module xmlns="migration4o" name="Dossier_adresse">
    <PersonneRess class="gest.dossPrev.PersonneRess" id="12345">
        <iD>12345</iD>
        <nom>Jean Dupont</nom>
        <couriel>jean.dupont@example.com</couriel>
        <cell>514-555-1234</cell>
        <personneRessource>true</personneRessource>
        <contactIntervention>true</contactIntervention>
        <handicape>false</handicape>
        <statut>1</statut>
        <idssi>1</idssi>
    </PersonneRess>
</module>
```

### Embedded Objects (Single Reference - Not Shared)

When an object is referenced by only one parent, it's embedded directly:

```xml
<ParamConfigSSI class="gest.config.ParamConfigSSI" id="100">
    <prefixe>SSI01</prefixe>
    <nbrChiffreSequence>4</nbrChiffreSequence>
    <identVilleDansNumActiv>true</identVilleDansNumActiv>
    <idssi>1</idssi>
    
    <!-- Embedded object - only referenced here -->
    <paramServeurCartographie class="gest.cartographie.ParamServeurCartographie" id="200">
        <actif>true</actif>
        <adresseServeur>192.168.1.100</adresseServeur>
        <portDirect>8080</portDirect>
        <nomServeur>CartographyServer</nomServeur>
        <nomProjet>FireDepartment</nomProjet>
        <memoireMax>512</memoireMax>
        <formatterMatricule>true</formatterMatricule>
        <nomAttributMatricule>MATRICULE</nomAttributMatricule>
        <nomCoucheUnitesEvaluation>UNITS_EVAL</nomCoucheUnitesEvaluation>
    </paramServeurCartographie>
    
    <!-- Another embedded object -->
    <paramFormWeb class="gest.prevention.ParamFormWebParSSI" id="201">
        <clientID>CLIENT_001</clientID>
        <codeUsager>USER123</codeUsager>
        <password>encrypted_password_hash</password>
    </paramFormWeb>
</ParamConfigSSI>
```

### ID Object References

When referencing through ID-type objects, use IDREF to point to the target object:

```xml
<PersonneRess class="gest.dossPrev.PersonneRess" id="12345">
    <nom>Jean Dupont</nom>
    <couriel>jean.dupont@example.com</couriel>
    
    <!-- ID object reference - points to actual target object -->
    <idDossPrev definition="DossPrev" module="Dossier_adresse" type="reference">54321</idDossPrev>
    <idLangue definition="Langue" module="Parametres" type="reference">1</idLangue>
</PersonneRess>

<!-- Target objects are exported in their respective modules -->
<!-- In Dossier_adresse.xml: -->
<DossierAdresse class="gest.dossPrev.DossPrev" id="54321">
    <matricule>2024-001</matricule>
    <adresse class="gest.gen.Adresse" id="99001">
        <noCivique>123</noCivique>
        <rue>Rue Principale</rue>
        <ville>Montreal</ville>
        <codePostal>H3H 1A1</codePostal>
    </adresse>
</DossierAdresse>
```

### Collections of ID References

Collections containing ID objects that reference other entities:

```xml
<ParamConfigSSI class="gest.config.ParamConfigSSI" id="100">
    <prefixe>SSI01</prefixe>
    
    <!-- Collection of ID references to Specialite objects -->
    <vectSpecialite definition="Specialite" module="Parametres" type="references">
        <id>10</id>
        <id>15</id>
        <id>22</id>
    </vectSpecialite>
</ParamConfigSSI>

<!-- Referenced objects in Parametres.xml: -->
<Specialite class="gest.specialite.Specialite" id="10">
    <nom>Incendie</nom>
    <code>INC</code>
</Specialite>
<Specialite class="gest.specialite.Specialite" id="15">
    <nom>Sauvetage</nom>
    <code>SAU</code>
</Specialite>
```

### Collections of Direct Object References

Collections containing direct references to objects (not through ID objects). The format depends on whether objects are shared:

```xml
<ParamConfigGeneral class="gest.config.ParamConfigGeneral" id="101">
    <nbrEnregMaxRequete>1000</nbrEnregMaxRequete>
    <posteMobile>false</posteMobile>
    
    <!-- Collection of embedded objects (VilleGeo objects used only here) -->
    <vectVilleDesservie definition="VilleGeo" module="Parametres" type="objects">
        <VilleGeo class="gest.config.VilleGeo" id="45001">
            <nom>Montreal</nom>
            <codeGeo>66023</codeGeo>
            <province>QC</province>
            <codePostalDebut>H1A</codePostalDebut>
            <codePostalFin>H9Z</codePostalFin>
        </VilleGeo>
        <VilleGeo class="gest.config.VilleGeo" id="45002">
            <nom>Laval</nom>
            <codeGeo>65005</codeGeo>
            <province>QC</province>
            <codePostalDebut>H7A</codePostalDebut>
            <codePostalFin>H7W</codePostalFin>
        </VilleGeo>
        <VilleGeo class="gest.config.VilleGeo" id="45003">
            <nom>Longueuil</nom>
            <codeGeo>58227</codeGeo>
            <province>QC</province>
            <codePostalDebut>J3Y</codePostalDebut>
            <codePostalFin>J4Z</codePostalFin>
        </VilleGeo>
    </vectVilleDesservie>
    
    <!-- Collection of embedded objects (PhotoFich objects only used here) -->
    <vectPhotoFich definition="PhotoFich" module="Parametres" type="objects">
        <PhotoFich class="gest.gen.PhotoFich" id="80001">
            <nom>logo.png</nom>
            <cheminFichier>/images/logo.png</cheminFichier>
            <taille>25600</taille>
            <dateModification>2024-11-15T10:30:00</dateModification>
        </PhotoFich>
        <PhotoFich class="gest.gen.PhotoFich" id="80002">
            <nom>banner.jpg</nom>
            <cheminFichier>/images/banner.jpg</cheminFichier>
            <taille>156800</taille>
            <dateModification>2024-11-10T14:20:00</dateModification>
        </PhotoFich>
    </vectPhotoFich>
</ParamConfigGeneral>

<!-- Alternative: If VilleGeo objects are shared across multiple parents, use references -->
<ParamConfigSSI class="gest.config.ParamConfigSSI" id="102">
    <prefixe>SSI02</prefixe>
    
    <!-- Collection of references to shared VilleGeo objects -->
    <vectVilleDesservie definition="VilleGeo" module="Parametres" type="references">
        <id>45001</id>  <!-- References to VilleGeo objects defined elsewhere -->
        <id>45002</id>
        <id>45003</id>
    </vectVilleDesservie>
</ParamConfigSSI>
```

### Collections of Primitive Values

Collections containing simple primitive types:

```xml
<ParamConfigGeneral class="gest.config.ParamConfigGeneral" id="101">
    <nbrEnregMaxRequete>1000</nbrEnregMaxRequete>
    
    <!-- Collection of integers -->
    <vectIDSousSSI definition="int" module="" type="primitives">
        <value>100</value>
        <value>101</value>
        <value>105</value>
        <value>110</value>
    </vectIDSousSSI>
</ParamConfigGeneral>
```

### Complex Intervention Example

A complex object showing multiple relationship types:

```xml
<Intervention class="gest.intervention.Intervention" id="67890">
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
    
    <!-- Embedded complex objects -->
    <carteAppel class="gest.intervention.CarteAppel" id="67901">
        <heureAppel>2024-11-15T14:30:00</heureAppel>
        <nomAppelant>Marie Tremblay</nomAppelant>
        <telephoneAppelant>514-555-9876</telephoneAppelant>
        <descriptionInitiale>Incendie dans un garage résidentiel</descriptionInitiale>
    </carteAppel>
    
    <lieuInterv class="gest.gen.Lieu" id="67902">
        <noCivique>123</noCivique>
        <rue>Rue de l'Incendie</rue>
        <ville>Montreal</ville>
        <codePostal>H3H 1A1</codePostal>
        <coordGPS>45.5017,-73.5673</coordGPS>
    </lieuInterv>
    
    <!-- ID references to other modules -->
    <typeEvenement definition="Dsi2003C1" module="Parametres" type="reference">C110</typeEvenement>
    
    <!-- Collections of embedded objects -->
    <vectIntervVehicule definition="IntervVehicule" module="Intervention" type="objects">
        <IntervVehicule class="gest.intervention.IntervVehicule" id="67910">
            <numeroVehicule>PUMP-01</numeroVehicule>
            <heureDepart>2024-11-15T14:35:00</heureDepart>
            <heureArrivee>2024-11-15T14:42:00</heureArrivee>
            <heureRetour>2024-11-15T16:15:00</heureRetour>
            <kmDepart>85420</kmDepart>
            <kmRetour>85438</kmRetour>
        </IntervVehicule>
        <IntervVehicule class="gest.intervention.IntervVehicule" id="67911">
            <numeroVehicule>LADDER-01</numeroVehicule>
            <heureDepart>2024-11-15T14:37:00</heureDepart>
            <heureArrivee>2024-11-15T14:45:00</heureArrivee>
            <heureRetour>2024-11-15T16:20:00</heureRetour>
            <kmDepart>42380</kmDepart>
            <kmRetour>42395</kmRetour>
        </IntervVehicule>
    </vectIntervVehicule>
    
    <!-- Collection of references to objects in other modules -->
    <vectHistoActivite definition="HistoActivite" module="Organisation" type="references">
        <id>78001</id>
        <id>78002</id>
        <id>78003</id>
    </vectHistoActivite>
    
    <!-- Collection with renfort cards (additional interventions) -->
    <vectCarteAppelRenfort definition="CarteAppel" module="Intervention" type="references">
        <id>67920</id>
        <id>67921</id>
    </vectCarteAppelRenfort>
</Intervention>
```

### Unreached Objects File

Objects not reachable from module roots are exported separately:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<unreachedObjects xmlns="migration4o">
    <!-- Orphaned objects grouped by class -->
    <objectGroup class="gest.config.VilleGeo" count="3">
        <VilleGeo class="gest.config.VilleGeo" id="90001">
            <nom>Ville Abandonnée</nom>
            <codeGeo>99999</codeGeo>
            <province>QC</province>
        </VilleGeo>
        <VilleGeo class="gest.config.VilleGeo" id="90002">
            <nom>Ancienne Municipalité</nom>
            <codeGeo>99998</codeGeo>
            <province>QC</province>
        </VilleGeo>
        <VilleGeo class="gest.config.VilleGeo" id="90003">
            <nom>Test City</nom>
            <codeGeo>99997</codeGeo>
            <province>QC</province>
        </VilleGeo>
    </objectGroup>
    
    <objectGroup class="gest.gen.PhotoFich" count="1">
        <PhotoFich class="gest.gen.PhotoFich" id="90100">
            <nom>obsolete_image.png</nom>
            <cheminFichier>/temp/obsolete_image.png</cheminFichier>
            <taille>12800</taille>
            <dateModification>2020-03-15T09:20:00</dateModification>
        </PhotoFich>
    </objectGroup>
</unreachedObjects>
```

### Key Patterns Summary

1. **Primitive fields**: Direct values (`<nom>Jean Dupont</nom>`)

2. **Embedded objects**: Full object nested when not shared elsewhere

3. **ID references**: `<idDossPrev definition="DossPrev" module="Dossier_adresse" type="reference">54321</idDossPrev>`

4. **Collection attributes**:
   - `definition`: Type of objects in the collection
   - `module`: Where the referenced objects are exported
   - `type`: `references`, `objects`, or `primitives`

5. **Collection contents**:
   - **Reference collections**: `<id>objectId</id>` references (both ID-type objects and direct object references)
   - **Object collections**: Embedded objects (full object XML) when objects are only used in this collection
   - **Primitive collections**: `<value>primitiveValue</value>`

6. **Object attributes**:
   - `class`: Full Java class name
   - `id`: Unique database object identifier

The key decision for collections is: **What do they actually contain?**
- **`type="objects"`** → Contains embedded objects (full XML) that are only used here
- **`type="references"`** → Contains `<id>` pointing to objects defined elsewhere (both ID-type and direct references)
- **`type="primitives"`** → Contains `<value>` with primitive data types

**XML Element Names for Clarity**:
- `<id>` = Reference to object defined elsewhere when `type="references"`
- `<value>` = Primitive value when `type="primitives"`
- Full object XML = Embedded object when `type="objects"` (not shared)

This structure ensures complete data fidelity while maintaining clear relationships between objects across modules.

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

## Performance Considerations

- Use streaming XML writing (StAX) to handle very large databases
- Single XML file per module (no chunking)
- Progress reporting through `DOEngineMonitoring`
- Memory-efficient object traversal

