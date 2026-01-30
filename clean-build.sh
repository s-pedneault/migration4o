#!/bin/bash

# Safe clean build script that ensures no stale classes remain

echo "=== Clean Build Script ==="
echo ""

# Check if app is running
if pgrep -f "migration4o.ui.Migration4oUI" > /dev/null; then
    echo "ERROR: Application is currently running!"
    echo "Please close all instances of Migration4O before building."
    echo ""
    echo "Running processes:"
    pgrep -f -l "migration4o.ui.Migration4oUI"
    exit 1
fi

# Step 1: Delete ALL .class files recursively (catch IDE-compiled files)
echo "Step 1: Removing all .class files..."
find . -name "*.class" -type f -delete
echo "  ✓ Deleted all .class files"
echo ""

# Step 2: Maven clean
echo "Step 2: Running Maven clean..."
mvn clean -q
if [ $? -ne 0 ]; then
    echo "  ✗ Maven clean failed"
    exit 1
fi
echo "  ✓ Maven clean complete"
echo ""

# Step 3: Maven compile
echo "Step 3: Compiling..."
mvn compile -q
if [ $? -ne 0 ]; then
    echo "  ✗ Compilation failed"
    exit 1
fi
echo "  ✓ Compilation successful"
echo ""

# Step 4: Verify classes directory
if [ ! -d "classes" ]; then
    echo "  ✗ ERROR: classes directory not created"
    exit 1
fi

CLASS_COUNT=$(find classes -name "*.class" | wc -l | tr -d ' ')
echo "  ✓ Created $CLASS_COUNT class files in classes/"
echo ""

echo "=== Build Complete ==="
echo "You can now run: ./run-ui.sh"
