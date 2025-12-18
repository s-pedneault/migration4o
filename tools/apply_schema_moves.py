#!/usr/bin/env python3
"""
Apply schema embedding moves by:
1. Finding each class definition in foundation
2. Removing it from foundation  
3. Embedding it inline in the target field
"""

import xml.etree.ElementTree as ET
import sys

# Read the schema file
tree = ET.parse('schema/migration-schema.xml')
root = tree.getroot()

# Find foundation section
foundation = root.find('foundation')
if foundation is None:
    print("ERROR: foundation section not found")
    sys.exit(1)

# Define the moves to make (className → targetClass.targetField)
moves = [
    ("gest.DSI2003.IDDsi2003G6", "gest.intervention.IntervCauseIncendie", "mAmpleurIncendie"),
    ("gest.DSI2003.IDDsi2003G8", "gest.intervention.IntervCauseIncendie", "mDommage"),
    ("gest.actionCondition.IDGroupeActionCondition", "gest.activite.ActionCondition", "mIDGroupeAction"),
    ("gest.activite.ActionCondition", "gest.activite.TypeActivite", "mVectActionCondition"),
    ("gest.activite.IDActe", "gest.activite.InfoEmploye", "mVectIDActe"),
    ("gest.activite.IDHistoActivite", "gest.activite.InfoHistoEmploye", "mIDHistoActivite"),
    ("gest.activite.IDInfoEmploye", "gest.feuilleTemps.TempsPaye", "mIDInfoEmploye"),
    ("gest.activite.InfoEmploye", "gest.activite.InfoHistoEmploye", "mVectInfoEmploye"),
    ("gest.borne.IDNivPriorite", "gest.borne.AnomBorne", "mIDNivPriorite"),
    ("gest.canutec.IDPageJaune", "gest.dossPrev.ProdDang", "mIDPageJaune"),
    ("gest.centreDesRapports.IDRapportCentral", "gest.centreDesRapports.RapportCentral", "mIDRapportCentral"),
    ("gest.champPerso.ChampPersoChoix", "gest.champPerso.TypeChampPerso", "mVectChoix"),
    ("gest.classif.IDClassif", "gest.dossPrev.DossPrev", "mIDClassif"),
    ("gest.consolide.ParamSSI$NModuleDetail", "gest.consolide.ParamSSI", "mVectNModuleDetail"),
    ("gest.docum.IDDossierDocum", "gest.docum.Docum", "mIDDossierParent"),
    ("gest.dossPrev.IDPersonneRess", "gest.planInterv.PlanContact", "mIDPersonneRess"),
    ("gest.equipe.IDEquipeOuIDEmploye", "gest.equipe.Equipe", "mVectIDEquipeOuIDEmpl"),
    ("gest.formElec.ParamFigureSaisie", "gest.formElec.Section", "mVectParam"),
    ("gest.formElec.Section", "gest.formElec.FormElec", "mVectSection"),
    ("gest.gen.InfosAuthAurora", "gest.feuilleTemps.ParamFeuilleTemps", "mInfosAuthAurora"),
    ("gest.gen.IntervalleHeure", "gest.plageHoraire.PlageHoraire", "mVectIntervalleHeure"),
    ("gest.gen.Message", "gest.processus.Etape", "mNote"),
    ("gest.gen.ParamPropCouleur$NEntreeDeLegende", "gest.gen.ParamPropCouleur", "mLegende"),
    ("gest.graph.IDGraphLib", "gest.graph.TypeGraph", "mVectIDGraphLib"),
    ("gest.graph.IDTypeGraph", "gest.graph.Graph", "mIDTypeGraph"),
    ("gest.horaire.IDHoraire", "gest.horaire.HorQuart", "mIDHoraire"),
    ("gest.horaire.IDModeleHorQuart", "gest.horaire.HorQuart", "mIDModeleHorQuart"),
    ("gest.intervention.IDFournSAAQ", "gest.intervention.IntervVehAccident", "mIDFournSAAQ"),
    ("gest.listePerso.FormatDonnee", "gest.listePerso.ListePerso$NCol", "iFormatDonnee"),
    ("gest.listePerso.ListePerso$NCol", "gest.centreDesRapports.RapportCentral", "mVectNCol"),
    ("gest.listePerso.ListePerso$NGroupe", "gest.centreDesRapports.RapportCentral", "mVectNGroupe"),
    ("gest.listePerso.ListePerso$NTri", "gest.centreDesRapports.RapportCentral", "mVectNTri"),
    ("gest.maintEquip.IDEtatEquipement", "gest.maintenance.TypeMaint", "mNouvEtatParDefaut"),
    ("gest.maintEquip.IDMaintEquip", "gest.maintEquip.MaintEquipHisto", "mIDMaintEquip"),
    ("gest.maintenance.IDMaintenance", "gest.maintEquip.MaintEquip", "mIDMaintenance"),
    ("gest.parement.IDParement", "gest.dossPrev.DossPrev", "mIDParementExt"),
    ("gest.plancher.IDPlancher", "gest.dossPrev.DossPrev", "mIDPlancher"),
    ("gest.rapport.SousRapport", "gest.rapport.Rapport", "mVectSousRapport"),
    ("gest.rapport.SousRapportPageSupp", "gest.rapport.Rapport", "mVectPageSupp"),
    ("gest.schema.ForceFrappe", "gest.schema.ParamSchema", "mVectForceFrappe"),
    ("gest.secCiv.IDSinistre", "gest.secCiv.LogSinistre", "mIDSinistre"),
    ("gest.typeAssistanceParticuliere.AssistanceParticuliere", "gest.dossPrev.PersonneRess", "mAssistanceParticuliere"),
    ("gest.typeAssistanceParticuliere.IDTypeAssistanceParticuliere", "gest.typeAssistanceParticuliere.AssistanceParticuliere", "mIDTypeAssistanceParticuliere"),
    ("gest.typeBatiment.IDTypeBatiment", "gest.dossPrev.DossPrev", "mIDTypeBatiment"),
    ("gest.typeChauffage.IDTypeChauffage", "gest.dossPrev.Chauffage", "mIDTypeChauffage"),
    ("gest.typeCheminee.IDTypeCheminee", "gest.dossPrev.Chauffage", "mIDTypeCheminee"),
    ("gest.typeToit.IDTypeToit", "gest.dossPrev.DossPrev", "mIDTypeToit"),
]

successful_moves = []
failed_moves = []

for class_name, target_class, target_field in moves:
    # Find the class in foundation
    class_elem = None
    for cls in foundation.findall('class'):
        if cls.get('name') == class_name:
            class_elem = cls
            break
    
    if class_elem is None:
        print(f"SKIP: {class_name} not found in foundation (may already be embedded)")
        continue
    
    # Find target class in modules
    modules = root.find('modules')
    target_class_elem = None
    for cls in modules.iter('class'):
        if cls.get('name') == target_class:
            target_class_elem = cls
            break
    
    if target_class_elem is None:
        # Maybe it's in foundation now (if it was moved there)
        for cls in foundation.findall('class'):
            if cls.get('name') == target_class:
                target_class_elem = cls
                break
    
    if target_class_elem is None:
        print(f"FAIL: Target class {target_class} not found for {class_name}")
        failed_moves.append((class_name, target_class, target_field))
        continue
    
    # Find target field
    target_field_elem = None
    for field in target_class_elem.findall('field'):
        if field.get('name') == target_field:
            target_field_elem = field
            break
    
    if target_field_elem is None:
        print(f"FAIL: Target field {target_field} not found in {target_class} for {class_name}")
        failed_moves.append((class_name, target_class, target_field))
        continue
    
    # Create a copy of the class element to embed
    # We need to convert it to be a child of the field
    # First, preserve the class attributes and children
    embedded_class = ET.Element('class')
    for attr_key, attr_value in class_elem.attrib.items():
        embedded_class.set(attr_key, attr_value)
    
    # Copy all child elements (fields, references, etc.)
    for child in class_elem:
        embedded_class.append(child)
    
    # Clear any existing children of the field and add the embedded class
    # (but keep any text/tail)
    for child in list(target_field_elem):
        target_field_elem.remove(child)
    target_field_elem.append(embedded_class)
    
    # Remove the class from foundation
    foundation.remove(class_elem)
    
    print(f"SUCCESS: Embedded {class_name} in {target_class}.{target_field}")
    successful_moves.append((class_name, target_class, target_field))

# Write the modified XML back
tree.write('schema/migration-schema.xml', encoding='UTF-8', xml_declaration=True)

print("\n" + "="*80)
print(f"Successfully embedded: {len(successful_moves)} classes")
print(f"Failed: {len(failed_moves)} classes")
print(f"Skipped (already embedded): {len(moves) - len(successful_moves) - len(failed_moves)}")

if failed_moves:
    print("\nFailed moves:")
    for class_name, target_class, target_field in failed_moves:
        print(f"  - {class_name} → {target_class}.{target_field}")
