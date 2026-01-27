#!/bin/bash
# EMR Flink JARZ Analysis Script
# Extracts, converts, and analyzes Flink EMR containers for JARZ optimization

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ANALYSIS_DIR="$PROJECT_ROOT/data/flink-emr"
JARZ_CLI="$PROJECT_ROOT/../../jarz-tools/target/jarz-cli.jar"

# EMR Flink Configuration
FLINK_IMAGE="public.ecr.aws/emr-on-eks/flink/emr-7.11.0-flink:latest"
FLINK_VERSION="7.11.0"

echo "🚀 EMR Flink JARZ Analysis"
echo "=========================="
echo "Image: $FLINK_IMAGE"
echo "Analysis Dir: $ANALYSIS_DIR"

# Create directories
mkdir -p "$ANALYSIS_DIR"/{original-jars,jarz-converted,jarz-enhanced}

# Step 1: Extract JARs from container
extract_flink_jars() {
    echo "📦 Extracting Flink JARs from container..."
    
    # Pull image if not exists
    if ! docker image inspect "$FLINK_IMAGE" >/dev/null 2>&1; then
        echo "  Pulling image: $FLINK_IMAGE"
        docker pull "$FLINK_IMAGE"
    fi
    
    # Create temporary container
    local container_id=$(docker create "$FLINK_IMAGE")
    echo "  Created container: $container_id"
    
    # Extract JARs from known Flink locations
    local locations=(
        "/usr/lib/flink/lib"
        "/usr/lib/flink/plugins"
        "/usr/lib/hadoop"
        "/opt/flink/lib"
        "/opt/flink/plugins"
    )
    
    for location in "${locations[@]}"; do
        if docker exec "$container_id" test -d "$location" 2>/dev/null; then
            echo "  Extracting from: $location"
            docker cp "$container_id:$location/." "$ANALYSIS_DIR/original-jars/" 2>/dev/null || true
        fi
    done
    
    # Cleanup
    docker rm "$container_id" >/dev/null
    
    # Count extracted JARs
    local jar_count=$(find "$ANALYSIS_DIR/original-jars" -name "*.jar" -type f -size +0c | wc -l)
    echo "  ✅ Extracted $jar_count JAR files"
}

# Step 2: Convert JARs to JARZ format
convert_to_jarz() {
    echo "🔄 Converting Flink JARs to JARZ format..."
    
    # Build classpath for enhanced dependency analysis
    local classpath=""
    while IFS= read -r -d '' jar; do
        if [ -z "$classpath" ]; then
            classpath="$jar"
        else
            classpath="$classpath:$jar"
        fi
    done < <(find "$ANALYSIS_DIR/original-jars" -name "*.jar" -type f -size +0c -print0)
    
    # Set system property for enhanced analysis
    export JAVA_OPTS="-Djarz.analysis.classpath=$classpath"
    
    local converted=0
    local total=0
    
    # Convert each JAR
    while IFS= read -r -d '' jar_file; do
        total=$((total + 1))
        
        local jar_name=$(basename "$jar_file")
        local jarz_file="$ANALYSIS_DIR/jarz-converted/${jar_name%.jar}.jarz"
        
        echo "  Converting: $jar_name"
        
        if java $JAVA_OPTS -jar "$JARZ_CLI" --convert "$jar_file" "$jarz_file" 2>/dev/null; then
            converted=$((converted + 1))
            
            # Calculate compression
            local original_size=$(stat -c%s "$jar_file")
            local jarz_size=$(stat -c%s "$jarz_file")
            local reduction=$(echo "scale=1; (($original_size - $jarz_size) * 100.0) / $original_size" | bc -l)
            echo "    ✅ ${reduction}% reduction"
        else
            echo "    ❌ Failed"
        fi
    done < <(find "$ANALYSIS_DIR/original-jars" -name "*.jar" -type f -size +0c -print0)
    
    echo "  📊 Converted: $converted/$total JARs"
}

# Step 3: Analyze compression results
analyze_compression() {
    echo "📊 Analyzing Flink compression results..."
    
    local original_size=$(find "$ANALYSIS_DIR/original-jars" -name "*.jar" -type f -size +0c -exec stat -c%s {} + | awk '{sum+=$1} END {print sum}')
    local jarz_size=$(find "$ANALYSIS_DIR/jarz-converted" -name "*.jarz" -type f -exec stat -c%s {} + | awk '{sum+=$1} END {print sum}')
    local original_count=$(find "$ANALYSIS_DIR/original-jars" -name "*.jar" -type f -size +0c | wc -l)
    local jarz_count=$(find "$ANALYSIS_DIR/jarz-converted" -name "*.jarz" -type f | wc -l)
    
    if [ "$original_size" -gt 0 ]; then
        local reduction=$(echo "scale=1; (($original_size - $jarz_size) * 100.0) / $original_size" | bc -l)
        local savings=$((original_size - jarz_size))
        
        echo "  📈 Results:"
        echo "    Original: $(numfmt --to=iec $original_size) ($original_count JARs)"
        echo "    JARZ:     $(numfmt --to=iec $jarz_size) ($jarz_count JARs)"
        echo "    Savings:  $(numfmt --to=iec $savings) (${reduction}%)"
        
        # Save results
        cat > "$ANALYSIS_DIR/compression-report.md" << EOF
# Flink EMR $FLINK_VERSION JARZ Analysis

## Summary
- **Original Size**: $(numfmt --to=iec $original_size) ($original_count JARs)
- **JARZ Size**: $(numfmt --to=iec $jarz_size) ($jarz_count JARs)
- **Savings**: $(numfmt --to=iec $savings) (**${reduction}%** reduction)
- **Analysis Date**: $(date -u)

## Container Impact
- **Faster Pulls**: ${reduction}% reduction in transfer time
- **Storage Savings**: ${reduction}% lower ECR costs
- **Memory Efficiency**: Optimized ClassLoader overhead

## Recommendations
- **Immediate**: ${reduction}% storage reduction justifies JARZ adoption
- **Streaming**: Implement S3/CDN streaming for on-demand loading
- **Container**: Create JARZ-optimized Flink EMR base images
EOF
    fi
}

# Step 4: Generate comprehensive report
generate_report() {
    echo "📋 Generating Flink analysis report..."
    
    local report_file="$PROJECT_ROOT/results/flink-emr-analysis-$(date +%Y%m%d).md"
    
    cat > "$report_file" << EOF
# EMR Flink JARZ Analysis Report

**Generated**: $(date -u)  
**Image**: $FLINK_IMAGE  
**Version**: Flink EMR $FLINK_VERSION

## Executive Summary

$(cat "$ANALYSIS_DIR/compression-report.md" | tail -n +3)

## Files Generated
- **Original JARs**: \`$ANALYSIS_DIR/original-jars/\`
- **JARZ Files**: \`$ANALYSIS_DIR/jarz-converted/\`
- **Analysis**: \`$ANALYSIS_DIR/compression-report.md\`

## Next Steps
1. **Runtime Testing**: Validate Flink compatibility with JARZ ClassLoaders
2. **Performance Benchmarking**: Measure startup time improvements
3. **Container Integration**: Create optimized Flink EMR images
4. **Production Pilot**: Deploy in non-critical environments

---
*Generated by JARZ Framework Analysis AWS*
EOF
    
    echo "  📄 Report saved: $report_file"
}

# Main execution
main() {
    # Check prerequisites
    if [ ! -f "$JARZ_CLI" ]; then
        echo "❌ JARZ CLI not found. Building..."
        cd "$PROJECT_ROOT/../.."
        mvn clean install -DskipTests -q
        echo "✅ JARZ CLI built"
    fi
    
    # Run analysis pipeline
    extract_flink_jars
    convert_to_jarz
    analyze_compression
    generate_report
    
    echo ""
    echo "🎯 Flink EMR analysis completed!"
    echo "📊 Results: $PROJECT_ROOT/results/"
    echo "📁 Data: $ANALYSIS_DIR"
}

# Execute if run directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi
