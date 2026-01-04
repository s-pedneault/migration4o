#!/usr/bin/env python3
"""
Remove objects and references attributes from class elements in database-schema.xml
"""

import re
import sys

def remove_attributes(match):
    """Remove objects and references attributes from class tag."""
    indent = match.group(1)
    attrs = match.group(2)
    closing = match.group(3)
    
    # Remove objects="..." and references="..." attributes
    attrs = re.sub(r'\s+objects="[^"]*"', '', attrs)
    attrs = re.sub(r'\s+references="[^"]*"', '', attrs)
    
    return f'{indent}<class {attrs}{closing}'

def process_file(input_path, output_path):
    """Process the XML file and remove objects/references attributes."""
    
    with open(input_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Pattern to match class tags
    pattern = r'(\s*)<class\s+([^>]+?)(/?(?:\s*)>)'
    
    # Count matches
    matches = list(re.finditer(pattern, content))
    print(f"Found {len(matches)} class elements to process")
    
    # Count how many have objects or references attributes
    objects_count = len(re.findall(r'<class[^>]+objects="[^"]*"', content))
    references_count = len(re.findall(r'<class[^>]+references="[^"]*"', content))
    
    print(f"  - {objects_count} with objects attribute")
    print(f"  - {references_count} with references attribute")
    
    # Transform all class elements
    transformed_content = re.sub(pattern, remove_attributes, content)
    
    # Write the result
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write(transformed_content)
    
    print(f"Transformation complete. Output written to {output_path}")

if __name__ == '__main__':
    input_file = 'schema/database-schema.xml'
    output_file = 'schema/database-schema.xml'
    
    if len(sys.argv) > 1:
        input_file = sys.argv[1]
    if len(sys.argv) > 2:
        output_file = sys.argv[2]
    
    try:
        process_file(input_file, output_file)
        print("Successfully removed objects and references attributes")
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(1)
