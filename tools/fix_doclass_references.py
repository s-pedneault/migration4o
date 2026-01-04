#!/usr/bin/env python3
"""
Fix DOClass references to use DODatabaseClass throughout the codebase.
Most uses are for database operations, so DODatabaseClass is the correct type.
"""

import os
import re

def fix_file(filepath):
    """Fix DOClass references in a file."""
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original = content
    
    # Remove DOClass import if present
    content = re.sub(r'import migration4o\.models\.DOClass;\n', '', content)
    
    # Replace DOClass with DODatabaseClass in type declarations
    content = re.sub(r'\bDOClass\b', 'DODatabaseClass', content)
    
    # Add DODatabaseClass import if needed and not already present
    if 'DODatabaseClass' in content and 'import migration4o.models.database.DODatabaseClass;' not in content:
        # Find the package statement and add import after it
        package_match = re.search(r'(package [^;]+;\n)', content)
        if package_match:
            # Check if there are already imports
            import_match = re.search(r'\nimport ', content)
            if import_match:
                # Add to existing imports
                first_import_pos = import_match.start()
                content = content[:first_import_pos+1] + 'import migration4o.models.database.DODatabaseClass;\n' + content[first_import_pos+1:]
            else:
                # No imports, add after package
                content = package_match.group(0) + '\nimport migration4o.models.database.DODatabaseClass;\n\n' + content[len(package_match.group(0)):]
    
    # Write back if changed
    if content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    return False

def main():
    base_dir = 'src/main/java/migration4o'
    
    # Files to process
    files_to_fix = [
        'util/ObjectResolverUtil.java',
        'util/DatabaseUtil.java',
        'engine/resolvers/DOObjectResolver.java',
        'engine/resolvers/DOGenericObjectResolver.java',
        'engine/resolvers/DOReferenceResolver.java',
        'engine/resolvers/DOFieldResolver.java',
        'engine/migration/formats/xml/XMLDataExporter.java',
        'engine/migration/formats/xml/XMLFormatHandler.java',
        'engine/migration/formats/xml/XMLSchemaGenerator.java',
        'engine/migration/formats/excel/ExcelExportEngine.java',
        'engine/report/DOEnginePrintout.java',
        'engine/report/DOStructureReportGenerator.java',
    ]
    
    fixed_count = 0
    for file_path in files_to_fix:
        full_path = os.path.join(base_dir, file_path)
        if os.path.exists(full_path):
            if fix_file(full_path):
                print(f"Fixed: {file_path}")
                fixed_count += 1
        else:
            print(f"Not found: {file_path}")
    
    print(f"\nFixed {fixed_count} files")

if __name__ == '__main__':
    main()
