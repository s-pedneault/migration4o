#!/bin/bash

# Run Migration4o UI

# Set working directory to project root
cd "$(dirname "$0")"

echo "=== Migration4O UI ==="
echo ""

# Check if classes exist
if [ ! -d "classes" ]; then
    echo "Error: Project not built. Please run ./build.sh first."
    exit 1
fi

# Run the UI application
java -cp "classes:lib/*" migration4o.ui.Migration4oUI

echo ""
echo "UI closed."
