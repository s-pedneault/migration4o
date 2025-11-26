#!/bin/bash

# Migration4O Build Script
echo "=== Migration4O Build Script ==="

# Create directories if they don't exist
mkdir -p classes target

# Clean previous build
echo "Cleaning previous build..."
rm -rf classes/* target/*

# Configure Java - use system Java 24 for compilation but target Java 21 bytecode
echo "Using system Java for compilation (targeting Java 21 bytecode)"

# Show Java version being used
echo "Java version:"
java -version

echo ""
echo "Building with javac..."

# Compile with system Java but targeting Java 21 bytecode
javac --release 21 -cp ".:lib/*" -d classes $(find src/main/java -name "*.java" -not -path "*/deprecated/*")

if [ $? -eq 0 ]; then
    echo "✓ Compilation successful"
    echo ""
    echo "Build complete! You can now run:"
    echo "  ./run.sh"
else
    echo "✗ Compilation failed"
    echo ""
    echo "Please ensure:"
    echo "  - Java 21+ is installed and in your PATH"
    echo "  - All required JAR files are in the lib/ directory"
    exit 1
fi
