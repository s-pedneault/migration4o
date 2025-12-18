# Migration Schema - Classes to Embed (Complete List)

## Summary
Found **58 classes** in the foundation layer that are used by exactly ONE field and should be embedded within that field definition.

---

## Complete List of Recommended Moves

Each entry provides:
- Class name and location
- Parent class
- Object count and reference count
- Target location (where to embed)
- Specific justification

---

---

### 1. com.db4o.StaticField
- **Current Location**: foundation
- **Parent Class**: (none)
- **Object Count**: 0
- **Reference Count**: 1
- **Target Field**: `com.db4o.StaticClass.fields`
- **Justification**: DB4O internal structure - StaticField objects only exist as children of StaticClass. This is a composition relationship that should be reflected through embedding.

---

### 2. gest.DSI2003.IDDsi2003C1
- **Current Location**: foundation
- **Parent Class**: gest.DSI2003.IDDSI2003
- **Object Count**: 1,516
- **Reference Count**: 1
- **Target Field**: `gest.intervention.Intervention.mTypeEvenement`
- **Justification**: DSI2003 event type classification used only in Intervention. Single-purpose ID type that doesn't need separate definition.

---

### 3. gest.DSI2003.IDDsi2003D8
- **Current Location**: foundation
- **Parent Class**: gest.DSI2003.IDDSI2003
- **Object Count**: 1,705
- **Reference Count**: 1
- **Target Field**: `gest.intervention.IntervChronologie.mRaisonDelai`
- **Justification**: DSI2003 delay reason code used only in intervention chronology. Embedding clarifies this is specific to timeline tracking.

---

### 4. gest.DSI2003.IDDsi2003E12
- **Current Location**: foundation
- **Parent Class**: gest.DSI2003.IDDSI2003
- **Object Count**: 1,522
- **Reference Count**: 1
- **Target Field**: `gest.intervention.IntervPerte.mIDAssurance`
- **Justification**: DSI2003 insurance classification for losses. Only used in loss reporting, should be embedded there.

---

### 5. gest.DSI2003.IDDsi2003E8
- **Current Location**: foundation
- **Parent Class**: gest.DSI2003.IDDSI2003
- **Object Count**: 6,599
- **Reference Count**: 1
- **Target Field**: `gest.dossPrev.DossPrev.mIDTypeConstruction`
- **Justification**: DSI2003 construction type for buildings. Single use in building records justifies embedding.

---

### 6. gest.DSI2003.IDDsi2003F1
- **Current Location**: foundation
- **Parent Class**: gest.DSI2003.IDDSI2003
- **Object Count**: 6,772
- **Reference Count**: 1
- **Target Field**: `gest.dossPrev.Protection.mIDAvertisseur`
- **Justification**: DSI2003 alarm/warning system type. Specific to protection systems, should be embedded.

---

### 7. gest.DSI2003.IDDsi2003F2
- **Current Location**: foundation
- **Parent Class**: gest.DSI2003.IDDSI2003
- **Object Count**: 1,516
- **Reference Count**: 1
- **Target Field**: `gest.intervention.IntervProtection.mIDAvertFonctionne`
- **Justification**: DSI2003 alarm functionality status during intervention. Single-use intervention field.

---

### 8. gest.DSI2003.IDDsi2003F3
- **Current Location**: foundation
- **Parent Class**: gest.DSI2003.IDDSI2003
- **Object Count**: 6,772
- **Reference Count**: 1
- **Target Field**: `gest.dossPrev.Protection.mIDSystAlarme`
- **Justification**: DSI2003 alarm system type in protection records. Only used in building protection data.

---

### 9. gest.DSI2003.IDDsi2003F4
- **Current Location**: foundation
- **Parent Class**: gest.DSI2003.IDDSI2003
- **Object Count**: 1,516
- **Reference Count**: 1
- **Target Field**: `gest.intervention.IntervProtection.mIDSystAlarmeFonctionne`
- **Justification**: DSI2003 alarm system functionality during intervention. Specific to intervention protection assessment.

---

### 10. gest.DSI2003.IDDsi2003F5
- **Current Location**: foundation
- **Parent Class**: gest.DSI2003.IDDSI2003
- **Object Count**: 6,599
- **Reference Count**: 1
- **Target Field**: `gest.dossPrev.Protection.mIDExtinction`
- **Justification**: DSI2003 fire suppression system type. Only referenced in building protection records.

---

### 11. gest.DSI2003.IDDsi2003F6
- **Current Location**: foundation
- **Parent Class**: gest.DSI2003.IDDSI2003
- **Object Count**: 1,516
- **Reference Count**: 1
- **Target Field**: `gest.intervention.IntervProtection.mIDExtinctionFonctionne`
- **Justification**: DSI2003 suppression system functionality. Single use in intervention protection evaluation.

---

### 12. gest.DSI2003.IDDsi2003G6
- **Current Location**: foundation
- **Parent Class**: gest.DSI2003.IDDSI2003
- **Object Count**: 1,725
- **Reference Count**: 1
- **Target Field**: `gest.intervention.IntervCauseIncendie.mAmpleurIncendie`
- **Justification**: DSI2003 fire extent classification. Only used in fire cause investigation.

---

### 13. gest.DSI2003.IDDsi2003G8
- **Current Location**: foundation
- **Parent Class**: gest.DSI2003.IDDSI2003
- **Object Count**: 1,725
- **Reference Count**: 1
- **Target Field**: `gest.intervention.IntervCauseIncendie.mDommage`
- **Justification**: DSI2003 damage classification. Specific to fire cause analysis, should be embedded.

---

### 14. gest.actionCondition.IDGroupeActionCondition
- **Current Location**: foundation
- **Parent Class**: gest.gen.IDEntite
- **Object Count**: 11
- **Reference Count**: 1
- **Target Field**: `gest.activite.ActionCondition.mIDGroupeAction`
- **Justification**: Action condition group identifier. Only used within ActionCondition class, composition relationship.

---

### 15. gest.activite.ActionCondition
- **Current Location**: foundation
- **Parent Class**: gest.gen.Entite
- **Object Count**: 0
- **Reference Count**: 0
- **Target Field**: `gest.activite.TypeActivite.mVectActionCondition[collection]`
- **Justification**: Action conditions are internal configuration of activity types. Not standalone entities.

---

### 16. gest.activite.IDActe
- **Current Location**: foundation
- **Parent Class**: gest.gen.IDEntite
- **Object Count**: 1,188
- **Reference Count**: 0
- **Target Field**: `gest.activite.InfoEmploye.mVectIDActe[collection]`
- **Justification**: Acts performed by employees. Collection element only used in InfoEmploye context.

---

### 17. gest.activite.IDHistoActivite
- **Current Location**: foundation
- **Parent Class**: gest.gen.IDEntite
- **Object Count**: 7,316
- **Reference Count**: 1
- **Target Field**: `gest.activite.InfoHistoEmploye.mIDHistoActivite`
- **Justification**: Activity history identifier specific to employee history records.

---

### 18. gest.activite.IDInfoEmploye
- **Current Location**: foundation
- **Parent Class**: gest.gen.IDEntite
- **Object Count**: 1
- **Reference Count**: 1
- **Target Field**: `gest.feuilleTemps.TempsPaye.mIDInfoEmploye`
- **Justification**: Employee info reference only used in payroll time tracking.

---

### 19. gest.activite.InfoEmploye
- **Current Location**: foundation
- **Parent Class**: gest.gen.EntiteContientID
- **Object Count**: 28,947
- **Reference Count**: 0
- **Target Field**: `gest.activite.InfoHistoEmploye.mVectInfoEmploye[collection]`
- **Justification**: Employee activity information is part of history records, not standalone.

---

### 20. gest.borne.IDNivPriorite
- **Current Location**: foundation
- **Parent Class**: gest.gen.IDEntite
- **Object Count**: 39
- **Reference Count**: 1
- **Target Field**: `gest.borne.AnomBorne.mIDNivPriorite`
- **Justification**: Priority level for hydrant anomalies. Only used in anomaly records.

---

### 21. gest.canutec.IDPageJaune
- **Current Location**: foundation
- **Parent Class**: gest.gen.IDEntite
- **Object Count**: 2,568
- **Reference Count**: 1
- **Target Field**: `gest.dossPrev.ProdDang.mIDPageJaune`
- **Justification**: CANUTEC yellow page reference for dangerous products. Single use in hazardous material records.

---

### 22. gest.centreDesRapports.IDRapportCentral
- **Current Location**: foundation
- **Parent Class**: gest.gen.IDEntite
- **Object Count**: 0
- **Reference Count**: 1
- **Target Field**: `gest.centreDesRapports.RapportCentral.mIDRapportCentral`
- **Justification**: Self-reference ID pattern. Should be embedded as part of the class definition.

---

### 23. gest.champPerso.ChampPersoChoix
- **Current Location**: foundation
- **Parent Class**: gest.gen.Entite
- **Object Count**: 0
- **Reference Count**: 0
- **Target Field**: `gest.champPerso.TypeChampPerso.mVectChoix[collection]`
- **Justification**: Custom field choice options. These are configuration elements of the field type, not standalone.

---

### 24. gest.classif.IDClassif
- **Current Location**: foundation
- **Parent Class**: gest.gen.IDEntite
- **Object Count**: 6,599
- **Reference Count**: 1
- **Target Field**: `gest.dossPrev.DossPrev.mIDClassif`
- **Justification**: Building classification ID only used in building address records.

---

### 25. gest.consolide.ParamSSI$NModuleDetail
- **Current Location**: foundation
- **Parent Class**: gest.gen.Entite
- **Object Count**: 0
- **Reference Count**: 0
- **Target Field**: `gest.consolide.ParamSSI.mVectNModuleDetail[collection]`
- **Justification**: SSI module detail configuration. Inner class structure that belongs within parent.

---

### 26. gest.docum.IDDossierDocum
- **Current Location**: foundation
- **Parent Class**: gest.gen.IDEntite
- **Object Count**: 1
- **Reference Count**: 1
- **Target Field**: `gest.docum.Docum.mIDDossierParent`
- **Justification**: Parent folder reference for documents. Only used within document hierarchy.

---

### 27. gest.dossPrev.IDPersonneRess
- **Current Location**: foundation
- **Parent Class**: gest.gen.IDEntite
- **Object Count**: 2
- **Reference Count**: 1
- **Target Field**: `gest.planInterv.PlanContact.mIDPersonneRess`
- **Justification**: Resource person reference only used in intervention plan contacts.

---

### 28. gest.equipe.IDEquipeOuIDEmploye
- **Current Location**: foundation
- **Parent Class**: gest.gen.Entite
- **Object Count**: 79
- **Reference Count**: 0
- **Target Field**: `gest.equipe.Equipe.mVectIDEquipeOuIDEmpl[collection]`
- **Justification**: Team member reference (team or employee). Union type specific to team composition.

---

### 29. gest.formElec.ParamFigureSaisie
- **Current Location**: foundation
- **Parent Class**: gest.gen.EntiteContientID
- **Object Count**: 120
- **Reference Count**: 0
- **Target Field**: `gest.formElec.Section.mVectParam[collection]`
- **Justification**: Electronic form input parameters. Part of form section structure, not standalone.

---

### 30. gest.formElec.Section
- **Current Location**: foundation
- **Parent Class**: gest.gen.Entite
- **Object Count**: 0
- **Reference Count**: 0
- **Target Field**: `gest.formElec.FormElec.mVectSection[collection]`
- **Justification**: Form sections are structural components of forms, should be embedded.

---

### 31. gest.gen.InfosAuthAurora
- **Current Location**: foundation
- **Parent Class**: gest.gen.EntiteParam
- **Object Count**: 0
- **Reference Count**: 1
- **Target Field**: `gest.feuilleTemps.ParamFeuilleTemps.mInfosAuthAurora`
- **Justification**: Aurora authentication configuration specific to timesheet parameters.

---

### 32. gest.gen.IntervalleHeure
- **Current Location**: foundation
- **Parent Class**: gest.gen.Entite
- **Object Count**: 0
- **Reference Count**: 0
- **Target Field**: `gest.plageHoraire.PlageHoraire.mVectIntervalleHeure[collection]`
- **Justification**: Time intervals are components of time ranges, not independent entities.

---

### 33. gest.gen.Message
- **Current Location**: foundation
- **Parent Class**: gest.gen.Entite
- **Object Count**: 32,933
- **Reference Count**: 1
- **Target Field**: `gest.processus.Etape.mNote`
- **Justification**: Messages used only as process step notes. High object count but single usage pattern.

---

### 34. gest.gen.ParamPropCouleur$NEntreeDeLegende
- **Current Location**: foundation
- **Parent Class**: gest.gen.Entite
- **Object Count**: 0
- **Reference Count**: 0
- **Target Field**: `gest.gen.ParamPropCouleur.mLegende[collection]`
- **Justification**: Legend entries for color properties. Inner class that belongs within parent.

---

### 35. gest.graph.IDGraphLib
- **Current Location**: foundation
- **Parent Class**: gest.gen.IDEntite
- **Object Count**: 9
- **Reference Count**: 0
- **Target Field**: `gest.graph.TypeGraph.mVectIDGraphLib[collection]`
- **Justification**: Graph library references specific to graph types.

---

### 36. gest.graph.IDTypeGraph
- **Current Location**: foundation
- **Parent Class**: gest.gen.IDEntite
- **Object Count**: 1,372
- **Reference Count**: 1
- **Target Field**: `gest.graph.Graph.mIDTypeGraph`
- **Justification**: Graph type identifier only used within graph definitions.

---

### 37. gest.horaire.IDHoraire
- **Current Location**: foundation
- **Parent Class**: gest.gen.IDEntite
- **Object Count**: 4
- **Reference Count**: 1
- **Target Field**: `gest.horaire.HorQuart.mIDHoraire`
- **Justification**: Schedule reference specific to shift schedules.

---

### 38. gest.horaire.IDModeleHorQuart
- **Current Location**: foundation
- **Parent Class**: gest.gen.IDEntite
- **Object Count**: 4
- **Reference Count**: 1
- **Target Field**: `gest.horaire.HorQuart.mIDModeleHorQuart`
- **Justification**: Schedule template reference only used in shift records.

---

### 39. gest.intervention.IDFournSAAQ
- **Current Location**: foundation
- **Parent Class**: gest.gen.IDEntite
- **Object Count**: 189
- **Reference Count**: 1
- **Target Field**: `gest.intervention.IntervVehAccident.mIDFournSAAQ`
- **Justification**: SAAQ provider reference for vehicle accidents. Single use case.

---

### 40. gest.listePerso.FormatDonnee
- **Current Location**: foundation
- **Parent Class**: (none)
- **Object Count**: 615
- **Reference Count**: 1
- **Target Field**: `gest.listePerso.ListePerso$NCol.iFormatDonnee`
- **Justification**: Data format specification for list columns. Part of column definition.

---

### 41. gest.listePerso.ListePerso$NCol
- **Current Location**: foundation
- **Parent Class**: gest.gen.Entite
- **Object Count**: 0
- **Reference Count**: 0
- **Target Field**: `gest.centreDesRapports.RapportCentral.mVectNCol[collection]`
- **Justification**: Column configuration for central reports. Inner class structure.

---

### 42. gest.listePerso.ListePerso$NGroupe
- **Current Location**: foundation
- **Parent Class**: gest.gen.Entite
- **Object Count**: 0
- **Reference Count**: 0
- **Target Field**: `gest.centreDesRapports.RapportCentral.mVectNGroupe[collection]`
- **Justification**: Grouping configuration for central reports. Inner class structure.

---

### 43. gest.listePerso.ListePerso$NTri
- **Current Location**: foundation
- **Parent Class**: gest.gen.Entite
- **Object Count**: 0
- **Reference Count**: 0
- **Target Field**: `gest.centreDesRapports.RapportCentral.mVectNTri[collection]`
- **Justification**: Sorting configuration for central reports. Inner class structure.

---

### 44. gest.maintEquip.IDEtatEquipement
- **Current Location**: foundation
- **Parent Class**: gest.gen.IDEntite
- **Object Count**: 2
- **Reference Count**: 1
- **Target Field**: `gest.maintenance.TypeMaint.mNouvEtatParDefaut`
- **Justification**: Default equipment state for maintenance types. Single use in type definition.

---

### 45. gest.maintEquip.IDMaintEquip
- **Current Location**: foundation
- **Parent Class**: gest.gen.IDEntite
- **Object Count**: 5
- **Reference Count**: 1
- **Target Field**: `gest.maintEquip.MaintEquipHisto.mIDMaintEquip`
- **Justification**: Equipment maintenance reference only used in maintenance history.

---

### 46. gest.maintenance.IDMaintenance
- **Current Location**: foundation
- **Parent Class**: gest.gen.IDEntite
- **Object Count**: 5
- **Reference Count**: 1
- **Target Field**: `gest.maintEquip.MaintEquip.mIDMaintenance`
- **Justification**: Maintenance record reference specific to equipment maintenance.

---

### 47. gest.parement.IDParement
- **Current Location**: foundation
- **Parent Class**: gest.gen.IDEntite
- **Object Count**: 6,599
- **Reference Count**: 1
- **Target Field**: `gest.dossPrev.DossPrev.mIDParementExt`
- **Justification**: Exterior siding type ID only used in building records.

---

### 48. gest.plancher.IDPlancher
- **Current Location**: foundation
- **Parent Class**: gest.gen.IDEntite
- **Object Count**: 6,599
- **Reference Count**: 1
- **Target Field**: `gest.dossPrev.DossPrev.mIDPlancher`
- **Justification**: Floor type ID only used in building records.

---

### 49. gest.rapport.SousRapport
- **Current Location**: foundation
- **Parent Class**: gest.rapport.Rapport
- **Object Count**: 0
- **Reference Count**: 0
- **Target Field**: `gest.rapport.Rapport.mVectSousRapport[collection]`
- **Justification**: Sub-reports are components of parent reports, hierarchical structure.

---

### 50. gest.rapport.SousRapportPageSupp
- **Current Location**: foundation
- **Parent Class**: gest.rapport.SousRapport
- **Object Count**: 0
- **Reference Count**: 0
- **Target Field**: `gest.rapport.Rapport.mVectPageSupp[collection]`
- **Justification**: Additional report pages are components of parent reports.

---

### 51. gest.schema.ForceFrappe
- **Current Location**: foundation
- **Parent Class**: gest.gen.Entite
- **Object Count**: 0
- **Reference Count**: 0
- **Target Field**: `gest.schema.ParamSchema.mVectForceFrappe[collection]`
- **Justification**: Strike force configuration elements. Part of schema parameters.

---

### 52. gest.secCiv.IDSinistre
- **Current Location**: foundation
- **Parent Class**: gest.gen.IDEntite
- **Object Count**: 4
- **Reference Count**: 1
- **Target Field**: `gest.secCiv.LogSinistre.mIDSinistre`
- **Justification**: Disaster reference only used in civil security logs.

---

### 53. gest.typeAssistanceParticuliere.AssistanceParticuliere
- **Current Location**: foundation
- **Parent Class**: gest.gen.Entite
- **Object Count**: 12,478
- **Reference Count**: 1
- **Target Field**: `gest.dossPrev.PersonneRess.mAssistanceParticuliere`
- **Justification**: Special assistance information specific to resource persons. High object count but single usage.

---

### 54. gest.typeAssistanceParticuliere.IDTypeAssistanceParticuliere
- **Current Location**: foundation
- **Parent Class**: gest.gen.IDEntite
- **Object Count**: 13,301
- **Reference Count**: 1
- **Target Field**: `gest.typeAssistanceParticuliere.AssistanceParticuliere.mIDTypeAssistanceParticuliere`
- **Justification**: Special assistance type reference. Only used within assistance records.

---

### 55. gest.typeBatiment.IDTypeBatiment
- **Current Location**: foundation
- **Parent Class**: gest.gen.IDEntite
- **Object Count**: 7,168
- **Reference Count**: 1
- **Target Field**: `gest.dossPrev.DossPrev.mIDTypeBatiment`
- **Justification**: Building type ID only used in building address records.

---

### 56. gest.typeChauffage.IDTypeChauffage
- **Current Location**: foundation
- **Parent Class**: gest.gen.IDEntite
- **Object Count**: 15,906
- **Reference Count**: 1
- **Target Field**: `gest.dossPrev.Chauffage.mIDTypeChauffage`
- **Justification**: Heating type ID specific to heating system records. High object count due to one per heating system.

---

### 57. gest.typeCheminee.IDTypeCheminee
- **Current Location**: foundation
- **Parent Class**: gest.gen.IDEntite
- **Object Count**: 15,906
- **Reference Count**: 1
- **Target Field**: `gest.dossPrev.Chauffage.mIDTypeCheminee`
- **Justification**: Chimney type ID specific to heating system records.

---

### 58. gest.typeToit.IDTypeToit
- **Current Location**: foundation
- **Parent Class**: gest.gen.IDEntite
- **Object Count**: 6,599
- **Reference Count**: 1
- **Target Field**: `gest.dossPrev.DossPrev.mIDTypeToit`
- **Justification**: Roof type ID only used in building address records.

---

## Implementation Priority

### High Priority (Most Obvious Single-Use Cases)
- All DSI2003 classification IDs (13 classes)
- Building/Prevention IDs (9 classes)
- Nested configuration classes (7 classes)
- **Total: 29 classes**

### Medium Priority (Clear Single-Use but Higher Object Counts)
- Activity & Employee Management (6 classes)
- Maintenance & Equipment (4 classes)
- All other ID classes
- **Total: 20 classes**

### Low Priority (Review Impact First)
- Classes with complex inheritance chains
- Classes with many objects (>10,000)
- **Total: 9 classes**

---

## Benefits of Embedding

1. **Improved Schema Clarity**: Relationships become explicit through nesting
2. **Reduced Foundation Clutter**: Foundation layer contains only truly shared classes
3. **Better Locality**: Related data structures are co-located
4. **Easier Maintenance**: Single-use classes are defined where they're used
5. **Clearer Data Model**: The schema more accurately reflects the actual data relationships

---

## Risks to Consider

1. **Large Object Counts**: Some classes have 10,000+ objects (e.g., IDTypeChauffage: 15,906)
2. **Complex Inheritance**: Some classes inherit from gest.gen.IDEntite or gest.gen.EntiteContientID
3. **Reference Attributes**: Some embedded classes have references="1" indicating they're referenced by other classes

**Recommendation**: Start with classes that have:
- Low object counts (< 1,000)
- Simple inheritance
- No or few reference attributes
- Clear single-purpose usage

Then progressively move to more complex cases after validating the approach.
