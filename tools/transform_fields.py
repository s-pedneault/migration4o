#!/usr/bin/env python3
"""
Transform field elements in migration-schema.xml according to the specified rules:
1. Change "name" attribute to "sourceName"
2. Add "destinationName" attribute (remove leading 'm', handle 'ID' prefix, lowercase first letter)
3. Add "migrate" attribute set to true
4. Add "skipIfEmpty" attribute set to true
5. Replace "java.lang.String" type with "String"
"""

import re
import sys

def transform_destination_name(source_name):
    """
    Transform sourceName to destinationName:
    - Remove leading 'm' if present
    - Change 'ID' prefix to 'id'
    - Lowercase the first letter
    """
    name = source_name
    
    # Remove leading 'm' if it's lowercase
    if name.startswith('m') and len(name) > 1 and name[1].isupper():
        name = name[1:]
    
    # Handle ID prefix (e.g., "IDSomething" -> "idSomething")
    if name.startswith('ID') and len(name) > 2:
        name = 'id' + name[2:]
    
    # Lowercase first letter
    if name:
        name = name[0].lower() + name[1:]
    
    return name

def transform_field(match):
    """Transform a field element according to the rules."""
    indent = match.group(1)
    name_value = match.group(2)
    type_value = match.group(3)
    rest = match.group(4)  # children attribute or self-closing
    
    # Transform type: java.lang.String -> String
    if type_value == 'java.lang.String':
        type_value = 'String'
    
    # Generate destinationName
    dest_name = transform_destination_name(name_value)
    
    # Build the transformed field
    transformed = f'{indent}<field sourceName="{name_value}" destinationName="{dest_name}" migrate="true" skipIfEmpty="true" type="{type_value}"{rest}'
    
    return transformed

def process_file(input_path, output_path):
    """Process the XML file and transform all field elements."""
    with open(input_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Pattern to match field elements with name attribute
    # Captures: (indent) <field name="value" type="value" (children="..." or /)>
    pattern = r'(\s*)<field name="([^"]+)" type="([^"]+)"((?:\s+children="[^"]+")?(?:\s*/)?>)'
    
    # Count matches before transformation
    matches = re.findall(pattern, content)
    print(f"Found {len(matches)} field elements to transform")
    
    # Transform all matching fields
    transformed_content = re.sub(pattern, transform_field, content)
    
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
        print(f"Successfully transformed {count} field elements")
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)
