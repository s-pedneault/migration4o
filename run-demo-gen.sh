#!/bin/bash

# Demo Database Generator
#
# Usage:
#   ./run-demo-gen.sh [options]
#
# Options:
#   --output <path>              Output database file (default: local/55555/demo.dat)
#   --scale  <small|medium|large> Objects per class (default: medium)
#   --seed   <number>            Random seed for deterministic output (default: 42)
#   --verify                     Verify generated database after creation
#   --help                       Show help
#
# Examples:
#   ./run-demo-gen.sh                                    # Generate medium demo DB
#   ./run-demo-gen.sh --scale small --verify             # Small DB with verification
#   ./run-demo-gen.sh --scale large --output local/demo-large.dat
#   ./run-demo-gen.sh --seed 123 --scale medium          # Deterministic with custom seed

cd "$(dirname "$0")"

# Check if classes exist
if [ ! -d "classes" ]; then
    echo "Error: Project not built. Run 'mvn clean compile' first."
    exit 1
fi

# Run the demo generator with DB4O-required module access flags
java --add-opens java.base/java.util=ALL-UNNAMED \
     --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
     --add-opens java.base/java.io=ALL-UNNAMED \
     --add-opens java.base/java.nio=ALL-UNNAMED \
     --add-opens java.base/java.net=ALL-UNNAMED \
     --add-opens java.base/java.text=ALL-UNNAMED \
     --add-opens java.base/java.time=ALL-UNNAMED \
     --add-opens java.base/java.util.concurrent=ALL-UNNAMED \
     --add-opens java.base/java.security=ALL-UNNAMED \
     --add-opens java.desktop/java.awt=ALL-UNNAMED \
     --add-opens java.desktop/java.awt.color=ALL-UNNAMED \
     --add-opens java.desktop/java.awt.font=ALL-UNNAMED \
     --add-opens java.desktop/java.awt.geom=ALL-UNNAMED \
     --add-opens java.desktop/java.awt.image=ALL-UNNAMED \
     --add-opens java.desktop/javax.swing=ALL-UNNAMED \
     --add-opens java.sql/java.sql=ALL-UNNAMED \
    -Xms256m \
    -Xmx2g \
    -Djdk.xml.maxGeneralEntitySizeLimit=0 \
    -cp "classes:lib/*" migration4o.demo.DemoGeneratorCLI "$@"
