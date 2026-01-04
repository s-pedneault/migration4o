#!/usr/bin/env python3
"""
Process field elements in migration-schema.xml:
1. If type is a collection (Vector, List, etc.), add collection="true" and embedValues="true" before "migrate"
2. If sourceName starts with "mID", add embedValue="true" before "migrate"
"""

import re
import sys

def is_collection_type(type_value):
    """Check if the type is a collection type."""
    collection_types = [
        'java.util.Vector',
        'java.util.List',
        'java.util.ArrayList',
        'java.util.LinkedList',
        'java.util.Set',
        'java.util.HashSet',
        'java.util.TreeSet',
        'java.util.Collection',
        'gen.util.VectRechID',
    ]
    return any(col_type in type_value for col_type in collection_types)

def transform_field(match):
    """Transform a field element by adding collection/embedValue attributes."""
    full_match = match.group(0)
    indent = match.group(1)
    source_name = match.group(2)
    dest_name = match.group(3)
    before_type = match.group(4)  # Attributes between destinationName and type
    type_value = match.group(5)
    remaining_attrs = match.group(6)  # Everything after type until the end tag
    
    # Determine what attributes to add
    attrs_to_add = []
    
    # Check if it's a collection type
    if is_collection_type(type_value):
        attrs_to_add.append('collection="true"')
        attrs_to_add.append('embedValues="true"')
    
    # Check if sourceName starts with "mID"
    if source_name.startswith('mID'):
        attrs_to_add.append('embedValue="true"')
    
    # If no attributes to add, return original
    if not attrs_to_add:
        return full_match
    
    # Build the new attributes string
    new_attrs = ' ' + ' '.join(attrs_to_add) + ' '
    
    # Insert the new attributes before "migrate"
    # The pattern will be: ...type="..." ... migrate="true"...
    # We need to insert before migrate="true"
    if 'migrate="true"' in remaining_attrs:
        new_remaining = remaining_attrs.replace('migrate="true"', new_attrs + 'migrate="true"', 1)
    else:
        # Shouldn't happen, but handle it anyway
        new_remaining = new_attrs + remaining_attrs
    
    # Reconstruct the field element
    result = f'{indent}<field sourceName="{source_name}" destinationName="{dest_name}"{before_type} type="{type_value}"{new_remaining}'
    
    return result

def process_file(input_path, output_path):
    """Process the XML file and add collection/embedValue attributes."""
    with open(input_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Pattern to match field elements with sourceName and destinationName
    # Captures: (indent) <field sourceName="..." destinationName="..." (anything) type="..." (rest)
    pattern = r'(\s*)<field sourceName="([^"]+)" destinationName="([^"]+)"([^>]*?) type="([^"]+)"([^>]*>)'
    
    # Count matches
    matches = list(re.finditer(pattern, content))
    print(f"Found {len(matches)} field elements to process")
    
    # Count how many will be modified
    collection_count = 0
    embed_value_count = 0
    
    for match in matches:
        source_name = match.group(2)
        type_value = match.group(5)
        
        if is_collection_type(type_value):
            collection_count += 1
        if source_name.startswith('mID'):
            embed_value_count += 1
    
    print(f"  - {collection_count} collection fields")
    print(f"  - {embed_value_count} fields with sourceName starting with 'mID'")
    
    # Transform all matching fields
    transformed_content = re.sub(pattern, transform_field, content)
    
    # Write the result
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write(transformed_content)
    
    print(f"Transformation complete. Output written to {output_path}")

if __name__ == '__main__':
    input_file = 'schema/migration-schema.xml'
    output_file = 'schema/migration-schema.xml'
    
    if len(sys.argv) > 1:
        input_file = sys.argv[1]
    if len(sys.argv) > 2:
        output_file = sys.argv[2]
    
    try:
        process_file(input_file, output_file)
        print("Successfully added collection and embedValue attributes")
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)
