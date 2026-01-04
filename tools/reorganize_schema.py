#!/usr/bin/env python3
"""
Reorganize schema structure:
1. Create database-schema.xml with ALL class definitions in a "classes" node, sorted alphabetically
2. Create migration-format.xml with modules structure but WITHOUT class/field definitions, only references
"""

import re
import sys
from xml.dom import minidom
import xml.etree.ElementTree as ET

def extract_classes_from_content(content):
    """Extract all class definitions (including nested ones) from the XML content."""
    classes = []
    
    # Pattern to match complete class elements (including nested content)
    # We'll use a more sophisticated approach: find class tags and their content
    
    lines = content.split('\n')
    i = 0
    while i < len(lines):
        line = lines[i]
        
        # Check if this line starts a class definition
        if '<class ' in line:
            # Find the class name
            class_match = re.search(r'sourceName="([^"]+)"', line)
            if class_match:
                class_name = class_match.group(1)
                
                # Collect all lines for this class
                class_lines = [line]
                indent_level = len(line) - len(line.lstrip())
                i += 1
                
                # If it's a self-closing tag
                if '/>' in line:
                    classes.append({
                        'name': class_name,
                        'content': '\n'.join(class_lines)
                    })
                    continue
                
                # Otherwise, collect until we find the closing tag
                open_tags = 1
                while i < len(lines) and open_tags > 0:
                    current_line = lines[i]
                    
                    # Count opening and closing class tags
                    if '<class ' in current_line:
                        open_tags += 1
                    if '</class>' in current_line:
                        open_tags -= 1
                    
                    class_lines.append(current_line)
                    i += 1
                
                classes.append({
                    'name': class_name,
                    'content': '\n'.join(class_lines)
                })
                continue
        
        i += 1
    
    return classes

def create_database_schema(classes, output_path):
    """Create database-schema.xml with all classes sorted alphabetically."""
    
    # Sort classes by name
    sorted_classes = sorted(classes, key=lambda x: x['name'])
    
    # Build the XML content
    lines = ['<?xml version="1.0" encoding="UTF-8"?>']
    lines.append('<classes>')
    
    for cls in sorted_classes:
        # Add the class content with proper indentation
        class_lines = cls['content'].split('\n')
        for line in class_lines:
            if line.strip():
                # Adjust indentation (add 4 spaces)
                lines.append('    ' + line.lstrip())
    
    lines.append('</classes>')
    
    # Write to file
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines))
    
    print(f"Created {output_path} with {len(sorted_classes)} class definitions")

def create_migration_format(content, output_path):
    """Create migration-format.xml with module structure but only class references."""
    
    lines = content.split('\n')
    output_lines = []
    
    i = 0
    while i < len(lines):
        line = lines[i]
        
        # Keep the XML declaration, database, modules, and module tags
        if any(tag in line for tag in ['<?xml', '<database', '</database', '<modules', '</modules', 
                                        '<module ', '</module', '<foundation', '</foundation']):
            output_lines.append(line)
            i += 1
            continue
        
        # When we encounter a class definition, replace it with just a reference
        if '<class ' in line:
            class_match = re.search(r'sourceName="([^"]+)"', line)
            if class_match:
                class_name = class_match.group(1)
                indent = len(line) - len(line.lstrip())
                
                # Add a class reference instead
                output_lines.append(' ' * indent + f'<classRef sourceName="{class_name}"/>')
                
                # Skip all lines until the closing </class> tag
                if not '/>' in line:
                    open_tags = 1
                    i += 1
                    while i < len(lines) and open_tags > 0:
                        current_line = lines[i]
                        if '<class ' in current_line and not '<!-- ' in current_line:
                            open_tags += 1
                        if '</class>' in current_line:
                            open_tags -= 1
                        i += 1
                    continue
            i += 1
            continue
        
        i += 1
    
    # Write to file
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(output_lines))
    
    print(f"Created {output_path} with module structure and class references")

def process_schema(input_path):
    """Main processing function."""
    
    # Read the input file
    with open(input_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    print(f"Processing {input_path}...")
    
    # Extract all class definitions
    classes = extract_classes_from_content(content)
    print(f"Extracted {len(classes)} class definitions")
    
    # Create database-schema.xml
    create_database_schema(classes, 'schema/database-schema.xml')
    
    # Create migration-format.xml
    create_migration_format(content, 'schema/migration-format.xml')
    
    print("Schema reorganization complete!")

if __name__ == '__main__':
    input_file = 'schema/migration-schema.xml'
    
    if len(sys.argv) > 1:
        input_file = sys.argv[1]
    
    try:
        process_schema(input_file)
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(1)
