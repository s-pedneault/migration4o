#!/usr/bin/env python3
"""
Transform class elements in migration-schema.xml:
1. Rename "name" attribute to "sourceName"
2. Rename "simpleName" attribute to "destinationName"
3. Add migrate="true" attribute
"""

import re
import sys

def transform_class(match):
    """Transform a class element by renaming attributes and adding migrate."""
    indent = match.group(1)
    full_attrs = match.group(2)
    closing = match.group(3)
    
    # Extract all attributes
    attrs_dict = {}
    attr_pattern = r'(\w+)="([^"]*)"'
    
    for attr_match in re.finditer(attr_pattern, full_attrs):
        attr_name = attr_match.group(1)
        attr_value = attr_match.group(2)
        attrs_dict[attr_name] = attr_value
    
    # Build new attributes
    new_attrs = []
    
    # Add sourceName (from name)
    if 'name' in attrs_dict:
        new_attrs.append(f'sourceName="{attrs_dict["name"]}"')
    
    # Add destinationName (from simpleName)
    if 'simpleName' in attrs_dict:
        new_attrs.append(f'destinationName="{attrs_dict["simpleName"]}"')
    
    # Add migrate="true"
    new_attrs.append('migrate="true"')
    
    # Add other attributes (except name and simpleName)
    for attr_name in ['title', 'parentClass', 'objects', 'references']:
        if attr_name in attrs_dict:
            new_attrs.append(f'{attr_name}="{attrs_dict[attr_name]}"')
    
    # Reconstruct the class tag
    attrs_str = ' '.join(new_attrs)
    result = f'{indent}<class {attrs_str}{closing}'
    
    return result

def process_file(input_path, output_path):
    """Process the XML file and transform all class elements."""
    with open(input_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Pattern to match class opening tags
    # Captures: (indent) <class (attributes) (> or />)
    pattern = r'(\s*)<class\s+([^>]+?)(/?(?:\s*)>)'
    
    # Count matches
    matches = list(re.finditer(pattern, content))
    print(f"Found {len(matches)} class elements to transform")
    
    # Transform all matching class elements
    transformed_content = re.sub(pattern, transform_class, content)
    
    # Write the result
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write(transformed_content)
    
    print(f"Transformation complete. Output written to {output_path}")
    return len(matches)

if __name__ == '__main__':
    input_file = 'schema/migration-schema.xml'
    output_file = 'schema/migration-schema.xml'
    
    if len(sys.argv) > 1:
        input_file = sys.argv[1]
    if len(sys.argv) > 2:
        output_file = sys.argv[2]
    
    try:
        count = process_file(input_file, output_file)
        print(f"Successfully transformed {count} class elements")
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)
