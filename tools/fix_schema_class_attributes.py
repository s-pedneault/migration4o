#!/usr/bin/env python3
"""
Update all DOSchemaClass field accesses to go through .attributes.
Fields moved: source, destinationName, parentClassName, migrate,
              schemaNotes, title, description, summary, pointsTo
"""
import re
import os

MOVED_FIELDS = [
    'source', 'destinationName', 'parentClassName', 'migrate',
    'schemaNotes', 'title', 'description', 'summary', 'pointsTo'
]

# Variable names known to be DOSchemaClass instances (not DOSchemaField)
SCHEMA_CLASS_VARS = [
    'schemaClass', 'newClass', 'dbSchemaClass', 'loopClass', 'typeClass',
    'idClass', 'childClass', 'baseClass', 'idEntiteClass', 'pointsToClass',
    'referenceClass', 'containingClass', 'targetClass', 'targetSchemaClass',
    'currentSchemaClass', 'liveClass', 'exportedParent', 'parent',
    'currentClass', 'cls', 'sc', 'c', 'desc',
    # Additional variables found during build
    'c1', 'c2', 'candidate', 'childrenClass', 'childrenTarget',
    'current', 'dbClass', 'existing', 'fieldClass', 'fieldTypeClass',
    'itemClass', 'leafClass', 'nodeClass', 'oldClass', 'parentClass',
    'potentialChild', 'referencedClass', 'referenceMatch', 'refSource',
    'refTarget', 'resolved', 'sourceClass', 'target', 'typeTarget',
    'childTypeClass', 'concreteClass', 'nextClass', 'refClass',
]

vars_pat = '|'.join(re.escape(v) for v in sorted(SCHEMA_CLASS_VARS, key=len, reverse=True))
fields_pat = '|'.join(re.escape(f) for f in MOVED_FIELDS)
pattern = re.compile(r'\b(' + vars_pat + r')\.(' + fields_pat + r')\b')


def replacement(m):
    return m.group(1) + '.attributes.' + m.group(2)


root = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'src', 'main', 'java', 'migration4o')
total = 0
for dirpath, dirnames, filenames in os.walk(root):
    for fname in filenames:
        if not fname.endswith('.java'):
            continue
        fpath = os.path.join(dirpath, fname)
        with open(fpath, 'r', encoding='utf-8') as f:
            content = f.read()
        new_content = pattern.sub(replacement, content)
        if new_content != content:
            with open(fpath, 'w', encoding='utf-8') as f:
                f.write(new_content)
            print('Updated: ' + fpath)
            total += 1

print('Total files updated: ' + str(total))
