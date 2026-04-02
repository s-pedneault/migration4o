#!/usr/bin/env python3
import re, json, sys

filepath = '/Volumes/Business/GestionTechnologies/Migration4O/output/46058/max50/html/2-Territoire/DossierAdresse/DossierAdresse.html'
with open(filepath, 'r') as f:
    html = f.read()

# The data is stored per-entity in script tags. Find them.
# Look for the actual entity JSON data blocks
# The entities are loaded from external XML, but sometimes inlined.
# Let's search for the data format used by the viewer

# Search for idFichier in the actual data portion (after SCHEMA_FIELDS)
last_script = html.rfind('<script>')
if last_script >= 0:
    data_section = html[last_script:last_script+5000]
    print("Last script section (first 2000 chars):")
    print(data_section[:2000])
    print("---")

# Also look for the entity data format
for pattern in ['parsexml', 'loadXML', 'XMLHttpRequest', 'fetch(', 'entities =', 'entityData']:
    idx = html.find(pattern)
    if idx >= 0:
        print(f"Found '{pattern}' at position {idx}")
        print(html[max(0,idx-50):idx+200])
        print("---")
