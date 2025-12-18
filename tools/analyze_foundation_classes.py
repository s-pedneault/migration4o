#!/usr/bin/env python3
"""
Analyze migration-schema.xml to identify foundation classes that should be moved to specific modules.

This script:
1. Parses the migration-schema.xml file
2. Identifies all foundation classes
3. Analyzes reference chains to determine module ownership
4. Generates a detailed report of recommendations
"""

import xml.etree.ElementTree as ET
from collections import defaultdict
from typing import Dict, List, Set, Tuple

def parse_schema(schema_path: str) -> Tuple[Dict, Dict, Dict]:
    """
    Parse the schema XML and extract:
    - module_classes: {class_name: module_name}
    - foundation_classes: {class_name: class_info}
    - references: {class_name: [(referring_class, field)]}
    """
    tree = ET.parse(schema_path)
    root = tree.getroot()
    
    module_classes = {}
    foundation_classes = {}
    references = defaultdict(list)
    
    # Parse module classes
    modules = root.find('modules')
    if modules:
        for module in modules.findall('module'):
            module_name = module.get('name')
            for cls in module.findall('class'):
                class_name = cls.get('name')
                module_classes[class_name] = module_name
                
                # Extract references for this class
                for ref in cls.findall('reference'):
                    ref_class = ref.get('class')
                    ref_field = ref.get('field')
                    references[class_name].append((ref_class, ref_field))
    
    # Parse foundation classes
    foundation = root.find('foundation')
    if foundation:
        for cls in foundation.findall('class'):
            class_name = cls.get('name')
            objects = cls.get('objects', '0')
            ref_count = cls.get('references', '0')
            
            foundation_classes[class_name] = {
                'name': class_name,
                'simpleName': cls.get('simpleName'),
                'parentClass': cls.get('parentClass', ''),
                'objects': int(objects) if objects else 0,
                'reference_count': int(ref_count) if ref_count else 0
            }
            
            # Extract references for this class
            for ref in cls.findall('reference'):
                ref_class = ref.get('class')
                ref_field = ref.get('field')
                references[class_name].append((ref_class, ref_field))
    
    return module_classes, foundation_classes, dict(references)


def find_module_for_class(class_name: str, module_classes: Dict[str, str]) -> str:
    """Find which module a class belongs to."""
    return module_classes.get(class_name, None)


def analyze_foundation_class(
    foundation_class: str,
    references: Dict[str, List[Tuple[str, str]]],
    module_classes: Dict[str, str]
) -> Dict[str, int]:
    """
    Analyze which modules reference this foundation class.
    Returns: {module_name: reference_count}
    """
    module_refs = defaultdict(int)
    
    # Get all classes that reference this foundation class
    referring_classes = references.get(foundation_class, [])
    
    for referring_class, field in referring_classes:
        # Find which module the referring class belongs to
        module = find_module_for_class(referring_class, module_classes)
        
        if module:
            module_refs[module] += 1
        else:
            # Check if it's another foundation class - trace further
            if referring_class in references:
                # Recursively check references to the referring class
                indirect_refs = analyze_foundation_class(referring_class, references, module_classes)
                for mod, count in indirect_refs.items():
                    module_refs[mod] += count
    
    return dict(module_refs)


def generate_report(
    foundation_classes: Dict,
    module_classes: Dict,
    references: Dict
) -> str:
    """Generate a detailed markdown report."""
    
    report_lines = [
        "# Foundation Class Analysis Report",
        "",
        "This report analyzes foundation classes in migration-schema.xml to identify classes that should be moved to specific modules.",
        "",
        "## Summary",
        ""
    ]
    
    # Categorize foundation classes
    single_module = []
    multi_module = []
    no_references = []
    
    for class_name, class_info in foundation_classes.items():
        module_refs = analyze_foundation_class(class_name, references, module_classes)
        
        if not module_refs:
            no_references.append((class_name, class_info))
        elif len(module_refs) == 1:
            module = list(module_refs.keys())[0]
            count = list(module_refs.values())[0]
            single_module.append((class_name, class_info, module, count))
        else:
            multi_module.append((class_name, class_info, module_refs))
    
    report_lines.append(f"- **Total foundation classes**: {len(foundation_classes)}")
    report_lines.append(f"- **Single-module usage** (candidates for moving): {len(single_module)}")
    report_lines.append(f"- **Multi-module usage** (should stay in foundation): {len(multi_module)}")
    report_lines.append(f"- **No references found**: {len(no_references)}")
    report_lines.append("")
    
    # Section 1: Single-module classes (candidates for moving)
    report_lines.extend([
        "## 1. Foundation Classes That Should Move to Specific Modules",
        "",
        "These classes are only referenced by classes in a single module and should be moved there.",
        ""
    ])
    
    if single_module:
        # Sort by module, then by reference count
        single_module.sort(key=lambda x: (x[2], -x[3]))
        
        current_module = None
        for class_name, class_info, module, ref_count in single_module:
            if module != current_module:
                current_module = module
                report_lines.append(f"### Module: `{module}`")
                report_lines.append("")
            
            # Get detailed references
            refs = references.get(class_name, [])
            ref_details = []
            for ref_class, ref_field in refs:
                ref_module = find_module_for_class(ref_class, module_classes)
                ref_details.append(f"  - `{ref_class}` (field: `{ref_field}`) in module `{ref_module or 'Foundation'}`")
            
            report_lines.append(f"**{class_info['simpleName']}** (`{class_name}`)")
            report_lines.append(f"- Objects: {class_info['objects']}")
            report_lines.append(f"- References: {ref_count} from `{module}` module")
            if ref_details:
                report_lines.append("- Referenced by:")
                report_lines.extend(ref_details)
            report_lines.append("")
    else:
        report_lines.append("*No single-module foundation classes found.*")
        report_lines.append("")
    
    # Section 2: Multi-module classes (correctly placed)
    report_lines.extend([
        "## 2. Foundation Classes Correctly Placed (Multi-Module Usage)",
        "",
        "These classes are referenced by multiple modules and should remain in foundation.",
        ""
    ])
    
    if multi_module:
        # Sort by total reference count
        multi_module.sort(key=lambda x: -sum(x[2].values()))
        
        for class_name, class_info, module_refs in multi_module[:20]:  # Top 20
            total_refs = sum(module_refs.values())
            module_list = ", ".join([f"`{m}` ({c})" for m, c in sorted(module_refs.items(), key=lambda x: -x[1])])
            
            report_lines.append(f"**{class_info['simpleName']}** (`{class_name}`)")
            report_lines.append(f"- Objects: {class_info['objects']}")
            report_lines.append(f"- Total references: {total_refs} from {len(module_refs)} modules")
            report_lines.append(f"- Modules: {module_list}")
            report_lines.append("")
        
        if len(multi_module) > 20:
            report_lines.append(f"*... and {len(multi_module) - 20} more multi-module classes*")
            report_lines.append("")
    else:
        report_lines.append("*No multi-module foundation classes found.*")
        report_lines.append("")
    
    # Section 3: No references
    report_lines.extend([
        "## 3. Foundation Classes With No Direct Module References",
        "",
        "These classes have no references from module classes. They may be:",
        "- Base classes used by other foundation classes",
        "- Utility classes",
        "- Classes that should be investigated",
        ""
    ])
    
    if no_references:
        for class_name, class_info in no_references[:30]:  # Top 30
            report_lines.append(f"**{class_info['simpleName']}** (`{class_name}`)")
            report_lines.append(f"- Objects: {class_info['objects']}")
            report_lines.append(f"- Parent: `{class_info['parentClass'] or 'None'}`")
            
            # Check if it has references within foundation
            refs = references.get(class_name, [])
            if refs:
                foundation_refs = [r for r in refs if r[0] not in module_classes]
                if foundation_refs:
                    report_lines.append(f"- Referenced by {len(foundation_refs)} other foundation classes")
            
            report_lines.append("")
        
        if len(no_references) > 30:
            report_lines.append(f"*... and {len(no_references) - 30} more classes with no references*")
            report_lines.append("")
    else:
        report_lines.append("*All foundation classes have module references.*")
        report_lines.append("")
    
    # Section 4: Special case - VoieCircul
    report_lines.extend([
        "## 4. Special Case Analysis: VoieCircul",
        "",
        "User example: `gest.gen.VoieCircul` should be in Dossier adresse module.",
        ""
    ])
    
    voie_circul = "gest.gen.VoieCircul"
    if voie_circul in foundation_classes:
        module_refs = analyze_foundation_class(voie_circul, references, module_classes)
        refs = references.get(voie_circul, [])
        
        report_lines.append(f"**Analysis of {voie_circul}:**")
        report_lines.append(f"- Objects: {foundation_classes[voie_circul]['objects']}")
        report_lines.append(f"- Module references: {module_refs}")
        report_lines.append("- Direct references:")
        for ref_class, ref_field in refs:
            ref_module = find_module_for_class(ref_class, module_classes)
            report_lines.append(f"  - `{ref_class}` (field: `{ref_field}`) in module `{ref_module or 'Foundation'}`")
        report_lines.append("")
        
        if len(module_refs) == 1:
            target_module = list(module_refs.keys())[0]
            report_lines.append(f"**Recommendation**: Move `VoieCircul` to `{target_module}` module (single-module usage)")
        elif module_refs:
            report_lines.append(f"**Recommendation**: Keep `VoieCircul` in foundation (used by {len(module_refs)} modules)")
        else:
            report_lines.append("**Recommendation**: Investigate - no module references found")
        report_lines.append("")
    else:
        report_lines.append(f"*{voie_circul} not found in foundation classes.*")
        report_lines.append("")
    
    return "\n".join(report_lines)


def main():
    schema_path = "/Users/sylvain/Development/migration4o/schema/migration-schema.xml"
    output_path = "/Users/sylvain/Development/migration4o/doc/development/foundation-class-analysis.md"
    
    print("Parsing migration-schema.xml...")
    module_classes, foundation_classes, references = parse_schema(schema_path)
    
    print(f"Found {len(module_classes)} module classes")
    print(f"Found {len(foundation_classes)} foundation classes")
    print(f"Found {sum(len(v) for v in references.values())} total references")
    
    print("\nGenerating report...")
    report = generate_report(foundation_classes, module_classes, references)
    
    print(f"\nWriting report to {output_path}...")
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write(report)
    
    print("Done!")
    print(f"\nReport summary:")
    print(f"- Total foundation classes: {len(foundation_classes)}")
    
    # Quick stats
    single_module_count = 0
    for class_name in foundation_classes:
        module_refs = analyze_foundation_class(class_name, references, module_classes)
        if len(module_refs) == 1:
            single_module_count += 1
    
    print(f"- Single-module candidates: {single_module_count}")
    print(f"- Multi-module classes: {len(foundation_classes) - single_module_count}")


if __name__ == "__main__":
    main()
