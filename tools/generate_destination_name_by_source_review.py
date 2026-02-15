#!/usr/bin/env python3
import csv
import re
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCHEMA_PATH = ROOT / "schema" / "reference-schema.xml"
OUTPUT_PATH = ROOT / "doc" / "development" / "destination-name-by-source-review.csv"

ABBREVIATIONS = {
    "addr": "adresse",
    "adr": "adresse",
    "arr": "arrondissement",
    "assoc": "association",
    "auto": "automatique",
    "cat": "categorie",
    "cfg": "configuration",
    "comp": "composant",
    "compt": "compteur",
    "conf": "configuration",
    "coord": "coordonnee",
    "cp": "codePostal",
    "deb": "debut",
    "dept": "departement",
    "desc": "description",
    "dest": "destination",
    "diff": "difference",
    "dispo": "disponible",
    "doc": "document",
    "dt": "date",
    "ech": "echeance",
    "enreg": "enregistrement",
    "ent": "entite",
    "env": "environnement",
    "err": "erreur",
    "evt": "evenement",
    "exp": "exportation",
    "ext": "externe",
    "fin": "fin",
    "freq": "frequence",
    "grp": "groupe",
    "hist": "historique",
    "idf": "identifiant",
    "img": "image",
    "info": "information",
    "interv": "intervention",
    "intit": "intitule",
    "lib": "libelle",
    "loc": "localisation",
    "maj": "miseAJour",
    "max": "maximum",
    "min": "minimum",
    "msg": "message",
    "nb": "nombre",
    "nbr": "nombre",
    "num": "numero",
    "orig": "origine",
    "param": "parametre",
    "pct": "pourcentage",
    "pers": "personne",
    "pref": "preference",
    "prep": "preparation",
    "prev": "prevision",
    "prio": "priorite",
    "proc": "processus",
    "prod": "produit",
    "proj": "projet",
    "prop": "propriete",
    "qte": "quantite",
    "qty": "quantite",
    "rech": "recherche",
    "req": "requete",
    "ref": "reference",
    "res": "resultat",
    "resp": "responsable",
    "seq": "sequence",
    "srv": "service",
    "stat": "statut",
    "temp": "temporaire",
    "tel": "telephone",
    "tmp": "temporaire",
    "tot": "total",
    "traj": "trajet",
    "usr": "utilisateur",
    "val": "valeur",
    "ver": "version",
}

TOKEN_RE = re.compile(r"[A-Z]?[a-z0-9]+|[A-Z]+(?![a-z])")


def split_camel(name: str):
    return TOKEN_RE.findall(name) or [name]


def suggest_name(destination_name: str) -> str:
    tokens = split_camel(destination_name)
    normalized = []
    for token in tokens:
        lower = token.lower()
        if lower in ABBREVIATIONS:
            normalized.append(ABBREVIATIONS[lower])
        elif lower in {"id", "ids"}:
            normalized.append(lower)
        else:
            normalized.append(lower)

    if not normalized:
        return destination_name

    first = normalized[0]
    tail = [part[:1].upper() + part[1:] for part in normalized[1:] if part]
    return first + "".join(tail)


def iter_fields_with_destination(root):
    for field in root.findall("./fields/field"):
        source = (field.get("source") or "").strip()
        destination = (field.get("destinationName") or "").strip()
        if source and destination:
            yield "[shared]", source, destination

    for cls in root.findall("./class"):
        class_source = (cls.get("source") or "").strip()
        for field in cls.findall("./field"):
            source = (field.get("source") or "").strip()
            destination = (field.get("destinationName") or "").strip()
            if source and destination:
                yield class_source, source, destination


def main():
    tree = ET.parse(SCHEMA_PATH)
    root = tree.getroot()

    by_source = defaultdict(Counter)
    by_source_classes = defaultdict(set)
    for class_source, source, destination in iter_fields_with_destination(root):
        by_source[source][destination] += 1
        by_source_classes[source].add(class_source)

    rows = []
    for source, counter in by_source.items():
        variants = sorted(counter.keys())
        primary_destination = counter.most_common(1)[0][0]
        current_destination = primary_destination if len(variants) == 1 else " | ".join(variants)
        suggestion = suggest_name(primary_destination)
        class_sources = sorted(by_source_classes.get(source, set()))
        class_source_value = " | ".join(class_sources)

        rows.append(
            {
                "class_source": class_source_value,
                "source_name": source,
                "current_destination_name": current_destination,
                "suggested_destination_name": suggestion,
                "destination_variants_count": str(len(variants)),
                "usage_count": str(sum(counter.values())),
                "status": "",  # approved | edited | rejected
                "notes": "",
            }
        )

    rows.sort(key=lambda r: (r["current_destination_name"].lower(), r["source_name"].lower()))

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT_PATH.open("w", newline="", encoding="utf-8") as f:
        fieldnames = [
            "class_source",
            "source_name",
            "current_destination_name",
            "suggested_destination_name",
            "destination_variants_count",
            "usage_count",
            "status",
            "notes",
        ]
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    print(f"Wrote {len(rows)} unique source rows to {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
