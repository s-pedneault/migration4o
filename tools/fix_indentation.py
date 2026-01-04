#!/usr/bin/env python3
"""
Fix indentation in database-schema.xml
"""

import sys

def fix_indentation(input_path, output_path):
    """Fix XML indentation."""
    
    with open(input_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    output_lines = []
    indent_level = 0
    
    for line in lines:
        stripped = line.strip()
        
        if not stripped:
            continue
        
        # Check if this is a closing tag
        if stripped.startswith('</'):
            indent_level -= 1
            output_lines.append('    ' * indent_level + stripped)
        # Check if this is a self-closing tag or single-line tag
        elif stripped.endswith('/>') or (stripped.startswith('<') and stripped.endswith('>') and '</' in stripped):
            output_lines.append('    ' * indent_level + stripped)
        # Opening tag
        else:
            output_lines.append('    ' * indent_level + stripped)
            # Increment indent if it's an opening tag (not self-closing)
            if stripped.startswith('<') and not stripped.endswith('/>'):
                # Check if it's not a self-closing tag disguised
                if not '/>' in stripped:
                    indent_level += 1
    
    # Write output
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(output_lines) + '\n')
    
    print(f"Fixed indentation in {output_path}")

if __name__ == '__main__':
    input_file = 'schema/database-schema.xml'
    output_file = 'schema/database-schema.xml'
    
    if len(sys.argv) > 1:
        input_file = sys.argv[1]
    if len(sys.argv) > 2:
        output_file = sys.argv[2]
    
    try:
        fix_indentation(input_file, output_file)
        print("Indentation fix complete!")
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(1)
