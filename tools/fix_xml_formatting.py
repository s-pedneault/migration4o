#!/usr/bin/env python3
"""
Fix XML formatting to have proper indentation for embedded classes.
"""

import xml.etree.ElementTree as ET
import xml.dom.minidom as minidom

def prettify_xml(elem, level=0):
    """Add proper indentation to XML elements."""
    indent = "\n" + "    " * level
    if len(elem):
        if not elem.text or not elem.text.strip():
            elem.text = indent + "    "
        if not elem.tail or not elem.tail.strip():
            elem.tail = indent
        for child in elem:
            prettify_xml(child, level + 1)
        if not child.tail or not child.tail.strip():
            child.tail = indent
    else:
        if level and (not elem.tail or not elem.tail.strip()):
            elem.tail = indent

# Parse the XML
tree = ET.parse('schema/migration-schema.xml')
root = tree.getroot()

# Apply pretty printing
prettify_xml(root)

# Write with proper XML declaration
with open('schema/migration-schema.xml', 'wb') as f:
    f.write(b'<?xml version="1.0" encoding="UTF-8"?>\n')
    tree.write(f, encoding='UTF-8', xml_declaration=False)

print("XML formatting fixed with proper indentation")
