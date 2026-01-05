#!/bin/bash
# Build script for JARZ jlink Plugin

set -e

echo "Building JARZ jlink Plugin..."

# Build the plugin
mvn clean package -DskipTests

# Check if build was successful
if [ -f "target/jarz-jlink-plugin-1.0.0-SNAPSHOT.jar" ]; then
    echo "✅ Build successful!"
    echo "Plugin JAR: target/jarz-jlink-plugin-1.0.0-SNAPSHOT.jar"
    
    # Show usage example
    echo ""
    echo "Usage example:"
    echo "java -jar target/jarz-jlink-plugin-1.0.0-SNAPSHOT.jar \\"
    echo "  --module-path \$JAVA_HOME/jmods \\"
    echo "  --add-modules java.base,java.logging \\"
    echo "  --output custom-jre-jarz \\"
    echo "  --compress jarz \\"
    echo "  --jarz-level 5"
    
    # Test help message
    echo ""
    echo "Testing help message:"
    java -jar target/jarz-jlink-plugin-1.0.0-SNAPSHOT.jar --help
    
else
    echo "❌ Build failed!"
    exit 1
fi
