#!/usr/bin/env python3
import re, json, sys

html_path = '/Volumes/Business/GestionTechnologies/Migration4O/output/46058/max50/html/2-Territoire/DossierAdresse/DossierAdresse.html'
with open(html_path, 'r') as f:
    content = f.read()

m = re.search(r'window\.__m4o\s*=\s*(\{.*?\});\s*</script>', content, re.DOTALL)
if not m:
    print('No __m4o found')
    sys.exit(1)

data = json.loads(m.group(1))
export = data.get('export', data)

print('Export keys:', list(export.keys()))
objects = export.get('objects', [])
if isinstance(objects, list) and len(objects) > 0:
    wrapper = objects[0]
    for entity_name, entity_data in wrapper.items():
        items = entity_data if isinstance(entity_data, list) else [entity_data]
        item = items[0]
        if not isinstance(item, dict):
            continue
        pjs = item.get('listePieceJointe')
        if not pjs:
            continue
        if not isinstance(pjs, list):
            pjs = [pjs]
        # Show the raw structure of first piece jointe
        pj = pjs[0]
        print('PJ type:', type(pj).__name__)
        print('PJ keys:', list(pj.keys()) if isinstance(pj, dict) else 'N/A')
        # Unwrap PieceJointe wrapper
        inner = pj.get('PieceJointe', pj)
        if isinstance(inner, list):
            inner = inner[0]
        print('Inner type:', type(inner).__name__)
        print('Inner keys:', list(inner.keys()) if isinstance(inner, dict) else 'N/A')
        idf = inner.get('idFichier')
        print()
        print('idFichier raw type:', type(idf).__name__)
        print('idFichier raw:', json.dumps(idf, indent=2, ensure_ascii=False)[:2000])
        break
