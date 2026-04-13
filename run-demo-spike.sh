#!/bin/bash

# Demo Generator Spike — Phase 1 Validation
#
# Proves that DB4O 7.4 GenericReflector/GenericClass/GenericObject APIs
# support creating and storing synthetic objects (no compiled .class files needed).
#
# Usage: ./run-demo-spike.sh
# Output: local/demo-spike.dat

cd "$(dirname "$0")"

if [ ! -d "classes" ]; then
    echo "Error: Project not built. Run 'mvn clean compile' first."
    exit 1
fi

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
     -cp "classes:lib/*" migration4o.demo.DemoGeneratorSpike
