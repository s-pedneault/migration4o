#!/usr/bin/env python3
"""
Analyze migration-schema.xml to find classes that should be embedded
within field definitions rather than being defined separately.
"""

import xml.etree.ElementTree as ET
from collections import defaultdict
import re

def analyze_schema(xml_file):
    tree = ET.parse(xml_file)
    root = tree.getroot()
    
    # Track all class definitions and their locations
    class_definitions = {}  # class_name -> (location, references_count, parent_element)
    class_references = defaultdict(list)  # class_name -> [(referencing_class, field_name)]
    embedded_classes = set()  # Classes already embedded in fields
    
    # Find foundation section
    foundation = root.find('foundation')
    
    # Process all classes in foundation
    if foundation is not None:
        for cls in foundation.findall('class'):
            class_name = cls.get('name')
            refs_elem = cls.findall('reference')
            class_definitions[class_name] = ('foundation', len(refs_elem), cls)
    
    # Process all modules and their classes
    modules = root.find('modules')
    if modules is not None:
        for module in modules.findall('module'):
            module_name = module.get('name')
            for cls in module.findall('class'):
                class_name = cls.get('name')
                refs_elem = cls.findall('reference')
                class_definitions[class_name] = (f'module:{module_name}', len(refs_elem), cls)
                
                # Check for embedded classes in fields
                for field in cls.findall('.//field'):
                    for embedded_cls in field.findall('class'):
                        embedded_class_name = embedded_cls.get('name')
                        embedded_classes.add(embedded_class_name)
                        refs_elem = embedded_cls.findall('reference')
                        class_definitions[embedded_class_name] = (f'embedded in {class_name}.{field.get("name")}', len(refs_elem), embedded_cls)
    
    # Now scan for all field type references
    for module in modules.findall('module') if modules else []:
        for cls in module.findall('class'):
            class_name = cls.get('name')
            for field in cls.findall('.//field'):
                field_type = field.get('type')
                field_name = field.get('name')
                
                # Check if type is a custom class (not java.* or primitive)
                if field_type and not field_type.startswith('java.') and not field_type.startswith('gen.util.') and field_type not in ['int', 'double', 'boolean', 'long', 'byte[]']:
                    class_references[field_type].append((class_name, field_name))
                
                # Check children attribute for collection types
                children = field.get('children')
                if children and not children.startswith('java.') and children not in ['int', 'double', 'boolean', 'long', 'byte']:
                    class_references[children].append((class_name, field_name + '[collection]'))
    
    # Also scan foundation classes
    if foundation:
        for cls in foundation.findall('class'):
            class_name = cls.get('name')
            for field in cls.findall('.//field'):
                field_type = field.get('type')
                field_name = field.get('name')
                
                if field_type and not field_type.startswith('java.') and not field_type.startswith('gen.util.') and field_type not in ['int', 'double', 'boolean', 'long', 'byte[]']:
                    class_references[field_type].append((class_name, field_name))
                
                children = field.get('children')
                if children and not children.startswith('java.') and children not in ['int', 'double', 'boolean', 'long', 'byte']:
                    class_references[children].append((class_name, field_name + '[collection]'))
    
    # Find classes that are only used once and are in foundation
    candidates = []
    
    for class_name, refs in class_references.items():
        if class_name in class_definitions:
            location, ref_count, cls_elem = class_definitions[class_name]
            
            # Only consider foundation classes that are referenced exactly once
            if location == 'foundation' and len(refs) == 1 and class_name not in embedded_classes:
                parent_class = refs[0][0]
                field_name = refs[0][1]
                
                # Get class details
                objects = cls_elem.get('objects', '0')
                simple_name = cls_elem.get('simpleName', class_name.split('.')[-1])
                parent_class_attr = cls_elem.get('parentClass', '')
                
                # Get reference count from <reference> elements
                reference_count = ref_count
                
                candidates.append({
                    'class_name': class_name,
                    'simple_name': simple_name,
                    'parent_class': parent_class_attr,
                    'objects': objects,
                    'references': reference_count,
                    'used_by_class': parent_class,
                    'used_by_field': field_name,
                    'location': location
                })
    
    return candidates

def main():
    xml_file = 'schema/migration-schema.xml'
    
    print("Analyzing migration-schema.xml for classes that should be embedded...\n")
    print("="*80)
    
    candidates = analyze_schema(xml_file)
    
    if not candidates:
        print("No candidates found for embedding.")
        return
    
    # Sort by class name for consistency
    candidates.sort(key=lambda x: x['class_name'])
    
    print(f"\nFound {len(candidates)} classes in foundation that are used by exactly ONE field:\n")
    
    for i, candidate in enumerate(candidates, 1):
        print(f"{i}. {candidate['class_name']}")
        print(f"   Simple Name: {candidate['simple_name']}")
        print(f"   Parent Class: {candidate['parent_class'] or '(none)'}")
        print(f"   Objects: {candidate['objects']}")
        print(f"   References: {candidate['references']}")
        print(f"   Currently in: {candidate['location']}")
        print(f"   Used by: {candidate['used_by_class']}")
        print(f"   Field: {candidate['used_by_field']}")
        print(f"   → SHOULD BE EMBEDDED in {candidate['used_by_class']}.{candidate['used_by_field']}")
        print()
    
    print("="*80)
    print(f"\nSummary: {len(candidates)} classes should be moved from foundation")
    print("         and embedded within their single-use field definitions.")

if __name__ == '__main__':
    main()
