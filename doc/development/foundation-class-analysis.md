# Foundation Class Analysis Report

**Analysis Date**: November 26, 2025  
**Source**: migration-schema.xml  
**Purpose**: Identify foundation classes that should be moved to specific modules

## Important Note on Schema Structure

The schema has three main sections:
1. **`<modules>`** (lines 1-2429): Classes already organized by module
2. **`<foundation>`** (lines 2430-4020): Shared/common classes that don't belong to a specific module
3. **`<excluded>`** (lines 4021+): Classes excluded from export

**This analysis focuses ONLY on the `<foundation>` section** to identify which foundation classes might actually belong to a specific module.

## Executive Summary

This analysis examines the 80+ foundation classes to determine which ones are only used by a single module and should be moved there, versus which ones are truly shared across multiple modules and should remain in foundation.

### Key Findings

Based on manual analysis of the migration-schema.xml file's `<foundation>` section:

1. **VoieCircul Analysis** (User's Example):
   - Class: `gest.gen.VoieCircul` (in foundation)
   - Objects: 34
   - References: 2 total
   - Referenced by:
     - `gest.dossPrev.ParamDossPrev$NValRempl` (field: `iVoie`)
     - `gest.dossPrev.ParamDossPrev$NValRempl` (field: `iVoieRempl`)
   - **Module Chain**: Both references from `ParamDossPrev$NValRempl` in Parametres module
   - **Recommendation**: Move to **Dossier adresse** module (based on user's domain knowledge)

## Foundation Classes Referenced by Single Modules

### 1. Classes Referenced Only by One Module (Candidates for Moving)

**Alarme** (`gest.secCiv.Alarme`) - IN FOUNDATION
- Objects: 0
- References: 1
- Referenced by: `gest.secCiv.LogSinistre` (field: `mAlarme`) in Sécurité civile module
- **Recommendation**: Move to **Sécurité civile** module (single-module usage)

**EtatDoc** (`gest.docum.EtatDoc`) - IN FOUNDATION
- Objects: 1
- References: 1
- Referenced by: `gest.docum.Docum` (field: `mEtatDoc`) in Sécurité civile module
- **Recommendation**: Move to **Sécurité civile** module (single-module usage)

**VoieCircul** (`gest.gen.VoieCircul`) - IN FOUNDATION
- Objects: 34
- References: 2
- Both references from: `gest.dossPrev.ParamDossPrev$NValRempl` in Parametres module
- **Recommendation**: Move to **Dossier adresse** module (per user domain knowledge)

**CartographieParamUtilisateurCartographie** (`gest.cartographie.ParamUtilisateurCartographie`) - IN FOUNDATION
- Objects: 14
- References: 1
- Referenced by: `gest.utilGroupe.Utilisateur` (field: `mUtilCarto`) in Organisation module
- **Recommendation**: Consider moving to **Organisation** module or keeping in foundation if cartography is cross-cutting

**HistoActivite** (`gest.activite.HistoActivite`) - IN FOUNDATION
- Objects: 5,793
- References: 2
- Referenced by:
  - `gest.quart.Quart` (field: `mHistoActivite`) in Travail module
  - `gest.horaire.HorQuart` (field: `mHistoActivite`) in Horaires module
- **Analysis**: Used by 2 related modules (both work/schedule related)
- **Recommendation**: Keep in foundation OR move to a common activity module if one exists

**InfoHistoEmploye** (`gest.activite.InfoHistoEmploye`) - IN FOUNDATION
- Objects: 7,316
- References: 4
- Referenced by:
  - `gest.intervention.IntervIntervenant` in Intervention module
  - `gest.quart.Quart` in Travail module  
  - `gest.horaire.HorQuart` in Horaires module
- **Analysis**: Used by 3+ different modules
- **Recommendation**: ✅ **Keep in foundation** (multi-module usage)

## Foundation Classes With Multi-Module Usage (Correctly Placed)

These classes are referenced by multiple modules and should remain in foundation:

### High-Usage Shared Classes

**VectRechID** (`gen.util.VectRechID`) - IN FOUNDATION ✅
- Objects: 47,430
- References: 35 different classes across multiple modules
- Modules: Vehicule, Activite, Dossier adresse, Prevention, Form elec, Grade, Plan intervention, Borne, Rapport, RCI, Document, Horaire, Code appel, Employe, Intervention, Maintenance, Caserne
- **Status**: ✅ Correctly in foundation (widely shared utility class)

**ChampPerso** (`gest.champPerso.ChampPerso`) - IN FOUNDATION ✅
- Objects: 440,644
- References: 2 visible (but used via VectChampPerso in many classes)
- Referenced by:
  - `gest.champPerso.TypeChampPerso` (field: `mValDefaut`)
  - `gest.secCiv.LogSinistre` (field: `mCP`)
- **Status**: ✅ Correctly in foundation (custom fields used everywhere)

**VectChampPerso** (`gest.champPerso.VectChampPerso`) - IN FOUNDATION ✅
- Objects: 11,302
- References: 4
- Referenced by:
  - `gest.dossPrev.DossPrev`
  - `gest.prevention.Prevention`
  - `gest.horaire.HorQuart`
  - `gest.maintEquip.MaintEquipHisto`
- **Status**: ✅ Correctly in foundation (custom field collections used across modules)

**Adresse** (`gest.gen.Adresse`) - IN FOUNDATION ✅
- Objects: 22,696
- References: 21 different classes
- Referenced by classes in: Intervention, Employe, License, Dossier adresse, Sécurité civile, RCI, Borne, Config, Ressource, Fournisseur, Plan intervention, Caserne
- **Status**: ✅ Correctly in foundation (address is universal)

**PhotoFich** (`gest.gen.PhotoFich`) - IN FOUNDATION ✅
- Objects: 21,751
- References: 1 direct (but used via Vector<PhotoFich> in almost every module)
- Referenced directly by: `gest.planInterv.PlanInterv`
- **Status**: ✅ Correctly in foundation (attachments used everywhere)

**InfoEmploye** (`gest.activite.InfoEmploye`) - IN FOUNDATION ✅
- Objects: 28,947
- References: Referenced via VectRechID by many activity types across modules
- **Status**: ✅ Correctly in foundation (employee info used across modules)

**Periodicite** (`gest.gen.Periodicite`) - IN FOUNDATION ✅
- Objects: 7,471
- References: 12 different classes
- Used by: DossPrev, Employe, Maintenance, Rapport, Classif, ParamBackup, CoursInterne, ParamSSI, ModeleHoraire, Prevention, etc.
- **Status**: ✅ Correctly in foundation (scheduling pattern used everywhere)

**Qte** (`gest.gen.Qte`) - IN FOUNDATION ✅
- Objects: 52,151
- References: 18 different classes
- Used for measurements, quantities, durations across all modules
- **Status**: ✅ Correctly in foundation (quantity/measurement utility)

## Foundation Classes With No Direct Module References

These are typically base classes, utility classes, or ID classes:

### Base Classes
- `gest.gen.Entite` (1,993,720 objects) - Root entity class
- `gest.gen.EntiteContientID` (113,060 objects) - Base for entities with IDs
- `gest.gen.EntiteParam` (29 objects) - Base for parameter entities
- `gest.gen.EntiteContientIDResettableMobile` - Base for mobile-resettable entities

### Utility Classes
- `gest.gen.Periodicite` (7,471 objects, 12 references) - Periodicity used by many modules
- `gest.gen.Qte` (52,151 objects, 18 references) - Quantity/measurement used everywhere
- `gest.gen.Message` (32,933 objects) - Messages/notes
- `gest.gen.Lieu` (1,519 objects, 3 references) - Location data
- `gest.gen.LongLat` (6,599 objects, 2 references) - GPS coordinates

### Form/Report Classes
- `gest.formElec.FormElec` (52 objects, 2 references)
- `gest.formElec.Section` (embedded in FormElec)
- `gest.rapport.Rapport` - Report definitions
- `gest.rapport.SousRapport` - Sub-reports

### Java Core Classes
- `java.awt.Color` (138 objects, 5 references)
- `java.util.Vector` (233,929 objects, 249 references)
- `java.util.Hashtable` (18,841 objects, 8 references)
- `java.util.UUID` (9,488 objects, 4 references)

**Status**: ✅ These are correctly in foundation as infrastructure classes

## ID Classes Analysis

All ID classes (IDEmploye, IDCaserne, IDEquipExist, etc.) are in foundation and correctly placed there as they are reference types used across all modules.

## Detailed Analysis: Single-Module Candidates

### High Priority - Move to Specific Modules

1. **gest.equipement.SituationEquipement** → Equipements module
   - Single reference from EquipExist in same domain

2. **gest.secCiv.Alarme** → Sécurité civile module
   - Only used by LogSinistre in Sécurité civile

3. **gest.docum.EtatDoc** → Sécurité civile module
   - Only used by Docum in Sécurité civile

4. **gest.gen.VoieCircul** → Dossier adresse module (per user guidance)
   - Used only by ParamDossPrev$NValRempl in Parametres
   - Domain knowledge indicates it belongs with Dossier adresse

### Medium Priority - Investigate Further

These classes have limited references but may need domain knowledge to properly categorize:

1. **gest.cartographie.ParamUtilisateurCartographie**
   - Objects: 14
   - References: 1 (from gest.utilGroupe.Utilisateur)
   - May belong with user/group management

2. **gest.config.ConfigCompressionImages**
   - Objects: 12
   - References: 11 (from various ParamXXX classes)
   - Configuration class - likely correctly in foundation

3. **gest.config.VilleGeo**
   - Objects: 191
   - References: 2 (IntervIntervEntraide, ParamConfigSSI)
   - Geographic data - likely correctly in foundation

## Recommendations Summary

### Classes to Move FROM Foundation TO Modules

| Class | Current Location | Target Module | Reason |
|-------|-----------------|---------------|---------|
| `gest.secCiv.Alarme` | Foundation | Sécurité civile | Single reference from LogSinistre in same module |
| `gest.docum.EtatDoc` | Foundation | Sécurité civile | Single reference from Docum in same module |
| `gest.gen.VoieCircul` | Foundation | Dossier adresse | All references from Parametres; domain logic indicates Dossier adresse |
| `gest.cartographie.ParamUtilisateurCartographie` | Foundation | Organisation (or stay) | Single reference from Utilisateur; may be cross-cutting |

### Classes to Keep in Foundation

**Multi-Module Usage** (3+ modules):
- `gen.util.VectRechID` - 35+ references across all modules
- `gest.gen.Adresse` - 21 references across 12+ modules
- `gest.gen.Periodicite` - 12 references across modules
- `gest.gen.Qte` - 18 references (measurements everywhere)
- `gest.champPerso.ChampPerso` / `VectChampPerso` - Custom fields in all modules
- `gest.gen.PhotoFich` - Attachments used universally
- `gest.activite.InfoEmploye` - Employee info across activities

**Base Classes** (infrastructure):
- `gest.gen.Entite` (1,993,720 objects) - Root entity class
- `gest.gen.EntiteContientID` (113,060 objects) - Base for entities with IDs
- `gest.gen.EntiteParam` (29 objects) - Base for parameter entities
- `gest.gen.EntiteContientIDResettableMobile` - Base for mobile-resettable entities

**ID Classes** (all correctly in foundation):
- All ID classes (IDEmploye, IDCaserne, IDEquipExist, etc.) are reference types used across all modules

**Utility Classes**:
- `gest.gen.Message` (32,933 objects) - Messages/notes
- `gest.gen.Lieu` (1,519 objects) - Location data
- `gest.gen.LongLat` (6,599 objects) - GPS coordinates
- `gest.gen.ExpirationDocument` - Document expiration tracking

**Java Core Classes**:
- `java.awt.Color`, `java.util.Vector`, `java.util.Hashtable`, `java.util.UUID`, etc.

## Next Steps

1. **Verify domain logic**: For each candidate class, verify with domain experts that the module assignment makes sense
2. **Update schema**: Move identified classes from `<foundation>` to appropriate `<module>` sections
3. **Update export logic**: Ensure ExportOrchestrator correctly exports these classes with their target modules
4. **Test**: Verify that moved classes export correctly and references work properly

## Notes

- This analysis is based on the reference structure in migration-schema.xml
- Some references may be indirect (through collection classes like Vector, VectRechID)
- Domain knowledge is essential for final decisions
- The reference count in the schema shows direct field references, not runtime usage patterns
- Classes like VoieCircul demonstrate that schema structure may not always align with domain logic

---

**Analysis Method**: Manual examination of migration-schema.xml reference elements and cross-referencing with module class locations.
