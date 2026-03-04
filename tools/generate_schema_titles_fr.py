#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
import shutil
import unicodedata
from datetime import datetime
from pathlib import Path
from typing import Dict, List

TAG_RE = re.compile(r'^(?P<indent>\s*)<(?P<tag>class|field)\b(?P<attrs>[^>]*)>(?P<rest>.*)$')
ATTR_RE = re.compile(r'(\w+)="([^"]*)"')
TOKEN_RE = re.compile(r'[A-Z]+(?=[A-Z][a-zàâäéèêëîïôöùûüç]|\d|$)|[A-Z]?[a-zàâäéèêëîïôöùûüç]+|\d+')

EXACT_TITLE_BY_DEST: Dict[str, str] = {
    "listePieceJointe": "Liste de pièces jointes",
    "dateActivite": "Date de l’activité",
    "dateCompletee": "Date complétée",
    "dateIdent": "Date d’identification",
    "dateDebut": "Date de début",
    "dateFin": "Date de fin",
    "id": "ID",
    "idSSI": "ID SSI",
    "idDBConso": "ID DB Conso",
}

ACRONYMS = {
    "id": "ID",
    "db": "DB",
    "ssi": "SSI",
    "dsi": "DSI",
    "msp": "MSP",
    "gps": "GPS",
    "xml": "XML",
    "json": "JSON",
    "csv": "CSV",
    "pdf": "PDF",
    "api": "API",
    "sql": "SQL",
    "http": "HTTP",
    "https": "HTTPS",
    "url": "URL",
    "uuid": "UUID",
}

WORD_MAP = {
    "activite": "activité",
    "activites": "activités",
    "adresse": "adresse",
    "adresses": "adresses",
    "ajout": "ajout",
    "anomalie": "anomalie",
    "avant": "avant",
    "apres": "après",
    "borne": "borne",
    "bornes": "bornes",
    "classe": "classe",
    "code": "code",
    "codes": "codes",
    "commence": "début",
    "collection": "collection",
    "collections": "collections",
    "completee": "complétée",
    "config": "config",
    "conso": "conso",
    "couleur": "couleur",
    "creation": "création",
    "courriel": "courriel",
    "date": "date",
    "debit": "débit",
    "debut": "début",
    "debute": "début",
    "detail": "détail",
    "details": "détails",
    "dossier": "dossier",
    "dossiers": "dossiers",
    "duree": "durée",
    "eau": "eau",
    "echeance": "échéance",
    "edition": "édition",
    "eleve": "élève",
    "eleves": "élèves",
    "employe": "employé",
    "employes": "employés",
    "energie": "énergie",
    "entite": "entité",
    "entites": "entités",
    "equipement": "équipement",
    "equipements": "équipements",
    "etat": "état",
    "etats": "états",
    "fichier": "fichier",
    "fichiers": "fichiers",
    "fin": "fin",
    "formation": "formation",
    "formations": "formations",
    "groupe": "groupe",
    "groupes": "groupes",
    "hiver": "hiver",
    "ident": "identification",
    "incident": "incident",
    "intervention": "intervention",
    "interventions": "interventions",
    "joint": "joint",
    "jointe": "jointe",
    "joints": "joints",
    "jointes": "jointes",
    "libere": "libéré",
    "liberes": "libérés",
    "liste": "liste",
    "longueur": "longueur",
    "maintenance": "maintenance",
    "materiel": "matériel",
    "matricule": "matricule",
    "methode": "méthode",
    "metier": "métier",
    "modele": "modèle",
    "modeles": "modèles",
    "module": "module",
    "modules": "modules",
    "montant": "montant",
    "nom": "nom",
    "note": "note",
    "niveau": "niveau",
    "numero": "numéro",
    "objet": "objet",
    "objets": "objets",
    "param": "paramètre",
    "parametre": "paramètre",
    "parametres": "paramètres",
    "partielle": "partielle",
    "paye": "payé",
    "periodicite": "périodicité",
    "periodicites": "périodicités",
    "piece": "pièce",
    "pieces": "pièces",
    "preference": "préférence",
    "preferences": "préférences",
    "preseance": "préséance",
    "prefixe": "préfixe",
    "presence": "présence",
    "proprietaire": "propriétaire",
    "proprietaires": "propriétaires",
    "protection": "protection",
    "quantite": "quantité",
    "rapport": "rapport",
    "rapports": "rapports",
    "raccord": "raccord",
    "reference": "référence",
    "references": "références",
    "reseau": "réseau",
    "role": "rôle",
    "seche": "sèche",
    "securite": "sécurité",
    "section": "section",
    "sections": "sections",
    "service": "service",
    "ssi": "SSI",
    "statique": "statique",
    "supplementaire": "supplémentaire",
    "table": "table",
    "tache": "tâche",
    "taches": "tâches",
    "telephone": "téléphone",
    "temoin": "témoin",
    "temps": "temps",
    "termine": "fin",
    "terminee": "fin",
    "titre": "titre",
    "type": "type",
    "types": "types",
    "usage": "usage",
    "usages": "usages",
    "utilisateur": "utilisateur",
    "utilisateurs": "utilisateurs",
    "valeur": "valeur",
    "valeurs": "valeurs",
    "vehicule": "véhicule",
    "vehicules": "véhicules",
    "ville": "ville",
}

LOWER_WORDS = {
    "de", "du", "des", "la", "le", "les", "et", "a", "au", "aux", "en", "sur", "pour", "par", "avec", "sans", "dans", "ou", "si", "non", "sous"
}


def xml_escape(value: str) -> str:
    return (
        value.replace("&", "&amp;")
        .replace('"', "&quot;")
        .replace("'", "&apos;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    )


def parse_attrs(attrs: str) -> Dict[str, str]:
    return {m.group(1): m.group(2) for m in ATTR_RE.finditer(attrs)}


def normalize_key(value: str) -> str:
    folded = unicodedata.normalize("NFD", value)
    stripped = "".join(ch for ch in folded if unicodedata.category(ch) != "Mn")
    return stripped.lower()


def split_tokens(destination_name: str) -> List[str]:
    normalized = destination_name.replace("_", " ").replace("-", " ").replace(".", " ")
    tokens: List[str] = []

    for part in normalized.split():
        if not part:
            continue
        found = TOKEN_RE.findall(part)
        if found:
            tokens.extend(found)
        else:
            tokens.append(part)

    return [token for token in tokens if token]


def word_for_token(token: str) -> str:
    low = normalize_key(token)
    if low in ACRONYMS:
        return ACRONYMS[low]
    if low in WORD_MAP:
        return WORD_MAP[low]
    if token.isupper() and len(token) <= 5:
        return token
    return low


def capitalize_sentence(words: List[str]) -> str:
    if not words:
        return ""
    out: List[str] = []
    for i, word in enumerate(words):
        if i == 0:
            if word in ACRONYMS.values() or word.isupper():
                out.append(word)
            else:
                out.append(word[:1].upper() + word[1:])
        else:
            if word in ACRONYMS.values() or word.isupper():
                out.append(word)
            elif word in LOWER_WORDS:
                out.append(word)
            else:
                out.append(word)
    text = " ".join(out)
    text = re.sub(r"\bde ([aeiouyàâäéèêëîïôöùûüh])", r"d’\1", text, flags=re.IGNORECASE)
    text = re.sub(r"\bde l ([aeiouyàâäéèêëîïôöùûüh])", r"de l’\1", text, flags=re.IGNORECASE)
    return text


def title_from_tokens(tokens: List[str]) -> str:
    if not tokens:
        return ""

    words = [word_for_token(t) for t in tokens]
    low = [w.lower() for w in words]

    if low[0] == "id":
        rest = words[1:]
        if not rest:
            return "ID"
        if len(rest) == 1 and (rest[0].isupper() or rest[0] in ACRONYMS.values()):
            return f"ID {rest[0]}"
        return capitalize_sentence(["ID", "de", *[r.lower() if r not in ACRONYMS.values() else r for r in rest]])

    if low[0] == "liste" and len(words) > 1:
        rest = [w.lower() if w not in ACRONYMS.values() else w for w in words[1:]]
        return capitalize_sentence(["liste", "de", *rest])

    if low[0] == "date" and len(words) > 1:
        rest = [w.lower() if w not in ACRONYMS.values() else w for w in words[1:]]
        if rest and rest[0][:1] in "aeiouyàâäéèêëîïôöùûüh":
            return capitalize_sentence(["date", "de", "l", *rest])
        return capitalize_sentence(["date", "de", *rest])

    if low[0] in {"nom", "type", "niveau", "numero", "quantite"} and len(words) > 1:
        head = words[0].lower()
        rest = [w.lower() if w not in ACRONYMS.values() else w for w in words[1:]]
        return capitalize_sentence([head, "de", *rest])

    return capitalize_sentence(words)


def refine_title(title: str) -> str:
    refined = title

    replacements = [
        (r"\bTemps préséance\b", "Temps de préséance"),
        (r"\bNiveau préséance\b", "Niveau de préséance"),
        (r"\bType activité\b", "Type d’activité"),
        (r"\bNom usage\b", "Nom d’usage"),
        (r"\bNom affichage\b", "Nom d’affichage"),
        (r"\bNom complet\b", "Nom complet"),
        (r"\baprès fin\b", "après la fin"),
        (r"\bde ID\b", "d’ID"),
        (r"\bListe d’ID Type\b", "Liste d’ID de type"),
        (r"\bTemps payé max\b", "Temps payé maximal"),
        (r"\bTemps payé min\b", "Temps payé minimal"),
        (r"\bDate de partielle début\b", "Date partielle de début"),
        (r"\bDate de partielle fin\b", "Date partielle de fin"),
        (r"\bMontant supplémentaire\b", "Montant supplémentaire"),
        (r"\bListe de début intervalle date\b", "Liste des intervalles de début"),
        (r"\bListe de début plage horaire\b", "Liste des plages horaires de début"),
        (r"\bListe de ID\b", "Liste d’ID"),
    ]

    for pattern, replacement in replacements:
        refined = re.sub(pattern, replacement, refined, flags=re.IGNORECASE)

    refined = re.sub(r"\s+", " ", refined).strip()

    # Capitalize first letter while preserving acronyms
    if refined:
        refined = refined[0].upper() + refined[1:]

    # Normalize common apostrophe spacing variants
    refined = refined.replace("d'", "d’").replace("l'", "l’")

    return refined


def generate_title(destination_name: str) -> str:
    if destination_name in EXACT_TITLE_BY_DEST:
        return EXACT_TITLE_BY_DEST[destination_name]

    tokens = split_tokens(destination_name)
    title = title_from_tokens(tokens)
    if not title:
        return destination_name

    post_fixes = {
        "Liste de pièce jointe": "Liste de pièces jointes",
        "Date de complétée": "Date complétée",
        "Date d’ident": "Date d’identification",
        "ID de ssi": "ID SSI",
        "ID de db conso": "ID DB Conso",
    }
    title = post_fixes.get(title, title)
    return refine_title(title)


def add_title_to_attrs(attrs: str, title: str) -> str:
    trailing_slash = bool(re.search(r'/\s*$', attrs))
    core = re.sub(r'/\s*$', '', attrs).rstrip() if trailing_slash else attrs.rstrip()
    separator = "" if core.endswith(" ") else " "
    updated = f'{core}{separator}title="{xml_escape(title)}"'
    if trailing_slash:
        updated += " /"
    return updated


def process_schema(schema_path: Path, dry_run: bool = False, rewrite_existing: bool = False) -> Dict[str, int]:
    text = schema_path.read_text(encoding="utf-8")
    lines = text.splitlines()

    destination_to_title: Dict[str, str] = dict(EXACT_TITLE_BY_DEST)

    for line in lines:
        match = TAG_RE.match(line)
        if not match:
            continue
        attrs = parse_attrs(match.group("attrs"))
        destination_name = attrs.get("destinationName")
        title = attrs.get("title")
        if (not rewrite_existing) and destination_name and title and destination_name not in destination_to_title:
            destination_to_title[destination_name] = title

    updated_lines: List[str] = []
    updated_count = 0
    reused_count = 0
    generated_count = 0

    for line in lines:
        match = TAG_RE.match(line)
        if not match:
            updated_lines.append(line)
            continue

        tag = match.group("tag")
        attrs_raw = match.group("attrs")
        rest = match.group("rest")
        indent = match.group("indent")

        attrs = parse_attrs(attrs_raw)
        destination_name = attrs.get("destinationName")

        if not destination_name:
            updated_lines.append(line)
            continue

        has_existing_title = "title" in attrs
        if has_existing_title and not rewrite_existing:
            updated_lines.append(line)
            continue

        if has_existing_title and rewrite_existing:
            title = generate_title(destination_name)
            destination_to_title[destination_name] = title
            generated_count += 1
        elif destination_name in destination_to_title:
            title = destination_to_title[destination_name]
            reused_count += 1
        else:
            title = generate_title(destination_name)
            destination_to_title[destination_name] = title
            generated_count += 1

        if has_existing_title:
            new_attrs_raw = re.sub(r'\btitle="[^"]*"', f'title="{xml_escape(title)}"', attrs_raw)
        else:
            new_attrs_raw = add_title_to_attrs(attrs_raw, title)

        updated_line = f"{indent}<{tag}{new_attrs_raw}>{rest}"
        updated_lines.append(updated_line)
        updated_count += 1

    if not dry_run:
        ending = "\n" if text.endswith("\n") else ""
        schema_path.write_text("\n".join(updated_lines) + ending, encoding="utf-8")

    remaining_missing = 0
    for line in updated_lines:
        m = TAG_RE.match(line)
        if not m:
            continue
        attrs = parse_attrs(m.group("attrs"))
        if "destinationName" in attrs and "title" not in attrs:
            remaining_missing += 1

    return {
        "updated": updated_count,
        "reused": reused_count,
        "generated": generated_count,
        "remaining_missing": remaining_missing,
        "known_destination_names": len(destination_to_title),
    }


def maybe_backup(path: Path) -> Path:
    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    backup_path = path.with_suffix(path.suffix + f".bak-{timestamp}")
    shutil.copy2(path, backup_path)
    return backup_path


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Génère les title FR manquants dans le schéma XML à partir de destinationName."
    )
    parser.add_argument(
        "--file",
        default="schema/reference-schema.xml",
        help="Chemin du fichier XML (défaut: schema/reference-schema.xml)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Calcule les changements sans écrire le fichier.",
    )
    parser.add_argument(
        "--backup",
        action="store_true",
        help="Crée une copie de sauvegarde avant modification.",
    )
    parser.add_argument(
        "--rewrite-existing",
        action="store_true",
        help="Régénère aussi les title déjà présents (utile pour corriger un ancien run).",
    )

    args = parser.parse_args()
    schema_path = Path(args.file)

    if not schema_path.exists():
        raise SystemExit(f"Fichier introuvable: {schema_path}")

    if args.backup and not args.dry_run:
        backup_path = maybe_backup(schema_path)
        print(f"Backup créé: {backup_path}")

    stats = process_schema(
        schema_path,
        dry_run=args.dry_run,
        rewrite_existing=args.rewrite_existing,
    )

    print(f"Mises à jour: {stats['updated']}")
    print(f"Titres réutilisés: {stats['reused']}")
    print(f"Titres générés: {stats['generated']}")
    print(f"destinationName connus: {stats['known_destination_names']}")
    print(f"Restants sans title: {stats['remaining_missing']}")


if __name__ == "__main__":
    main()
