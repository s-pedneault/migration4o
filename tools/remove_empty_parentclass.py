#!/usr/bin/env python3
"""
Remove empty parentClass attributes from class elements in database-schema.xml
"""

import re
import sys

def process_file(input_path, output_path):
    """Process the XML file and remove empty parentClass attributes."""
    
    with open(input_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Count empty parentClass attributes before removal
    empty_count = len(re.findall(r'\s+parentClass=""', content))
    print(f"Found {empty_count} empty parentClass attributes to remove")
    
    # Remove parentClass="" attributes
    transformed_content = re.sub(r'\s+parentClass=""', '', content)
    
    # Count remaining parentClass attributes (non-empty)
    remaining_count = len(re.findall(r'\s+parentClass="[^"]+"', transformed_content))
    print(f"Keeping {remaining_count} non-empty parentClass attributes")
    
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
        print("Successfully removed empty parentClass attributes")
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(1)
