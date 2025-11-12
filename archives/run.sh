#!/bin/bash

# Migration4O Launcher Script
# This script handles the complex JVM arguments needed for DB4O to work with Java 9+

# Base directory (where the script is located)
BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Check if compiled classes exist
CLASSES_DIR=""
if [ -d "$BASE_DIR/target/classes" ]; then
    CLASSES_DIR="$BASE_DIR/target/classes"
elif [ -d "$BASE_DIR/classes" ]; then
    CLASSES_DIR="$BASE_DIR/classes"
else
    echo "Error: No compiled classes found. Please run build.sh first."
    echo ""
    echo "To build:"
    echo "  ./build.sh"
    exit 1
fi

# Check if lib directory exists
if [ ! -d "$BASE_DIR/lib" ]; then
    echo "Error: lib directory not found. Please ensure DB4O libraries are in the lib/ directory."
    exit 1
fi

# Allocate 4GB of memory to the Java process
JAVA_MEM_OPTS=("-Xms512m" "-Xmx4g")

# Java module opens needed for DB4O to work with Java 9+
JVM_ARGS=(
    "--add-opens" "java.base/java.util=ALL-UNNAMED"
    "--add-opens" "java.base/java.lang=ALL-UNNAMED" 
    "--add-opens" "java.base/java.lang.reflect=ALL-UNNAMED"
    "--add-opens" "java.base/java.io=ALL-UNNAMED"
    "--add-opens" "java.base/java.nio=ALL-UNNAMED"
    "--add-opens" "java.base/java.net=ALL-UNNAMED"
    "--add-opens" "java.base/java.text=ALL-UNNAMED"
    "--add-opens" "java.base/java.time=ALL-UNNAMED"
    "--add-opens" "java.base/java.util.concurrent=ALL-UNNAMED"
    "--add-opens" "java.base/java.security=ALL-UNNAMED"
    "--add-opens" "java.desktop/java.awt=ALL-UNNAMED"
    "--add-opens" "java.desktop/java.awt.color=ALL-UNNAMED"
    "--add-opens" "java.desktop/java.awt.font=ALL-UNNAMED"
    "--add-opens" "java.desktop/java.awt.geom=ALL-UNNAMED"
    "--add-opens" "java.desktop/java.awt.image=ALL-UNNAMED"
    "--add-opens" "java.desktop/javax.swing=ALL-UNNAMED"
    "--add-opens" "java.sql/java.sql=ALL-UNNAMED"
)

# Show usage if help is requested
if [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
    echo "=== Migration4O - DB4O Database Migration Assistant ==="
    echo ""
    echo "Usage: $0 [options]"
    echo ""
    echo "This tool provides a streamlined, step-by-step interface for:"
    echo "  1. Database file selection and configuration"
    echo "  2. Database analysis and schema discovery"  
    echo "  3. Database migration with progress tracking"
    echo ""
    echo "Options:"
    echo "  -h, --help              Show this help message"
    echo "  --batch                 Run in batch mode (non-interactive)"
    echo "  --input <file>          Specify input DB4O file"
    echo "  --output <dir>          Specify output directory" 
    echo "  --format <format>       Output format (xml, json, csv)"
    echo ""
    echo "The assistant will guide you through the entire migration process."
    exit 0
fi

echo "=== Migration4O - DB4O Database Migration Assistant ==="
echo "Java version: $(java -version 2>&1 | head -n 1)"
echo "Working directory: $BASE_DIR"
echo "Classes directory: $CLASSES_DIR"
echo ""

echo "🚀 Launching Migration Assistant..."
# Execute Migration Assistant
exec java "${JAVA_MEM_OPTS[@]}" "${JVM_ARGS[@]}" -cp "$CLASSES_DIR:$BASE_DIR/lib/*" ui.assistant.AssistantLauncher "$@"
