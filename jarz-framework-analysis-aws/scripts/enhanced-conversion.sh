#!/bin/bash
# Enhanced JARZ Conversion with Framework-Aware Clustering
# Implements improved dependency analysis and block optimization

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
JARZ_CLI="$PROJECT_ROOT/../../jarz-tools/target/jarz-cli.jar"

# Enhanced conversion function
enhanced_convert() {
    local input_dir="$1"
    local output_dir="$2"
    local framework_name="$3"
    
    echo "🔄 Enhanced JARZ conversion for $framework_name..."
    
    # Build comprehensive classpath
    local classpath=""
    while IFS= read -r -d '' jar; do
        if [ -z "$classpath" ]; then
            classpath="$jar"
        else
            classpath="$classpath:$jar"
        fi
    done < <(find "$input_dir" -name "*.jar" -type f -size +0c -print0)
    
    # Enhanced JAVA_OPTS for optimal compression
    export JAVA_OPTS="-Djarz.analysis.classpath=$classpath -Djarz.clustering.strategy=framework -Djarz.compression.level=6 -Djarz.block.optimization=true"
    
    mkdir -p "$output_dir"
    
    local converted=0
    local total=0
    local total_original=0
    local total_compressed=0
    
    echo "  🔍 Processing $(find "$input_dir" -name "*.jar" -type f -size +0c | wc -l) JAR files..."
    
    # Convert with enhanced settings
    while IFS= read -r -d '' jar_file; do
        total=$((total + 1))
        
        local jar_name=$(basename "$jar_file")
        local jarz_file="$output_dir/${jar_name%.jar}.jarz"
        
        # Skip if already converted
        if [ -f "$jarz_file" ]; then
            echo "    ⏭️  Skipping: $jar_name (already exists)"
            continue
        fi
        
        echo "    🔧 Converting: $jar_name"
        
        if java $JAVA_OPTS -jar "$JARZ_CLI" --convert "$jar_file" "$jarz_file" 2>/dev/null; then
            converted=$((converted + 1))
            
            local original_size=$(stat -c%s "$jar_file")
            local jarz_size=$(stat -c%s "$jarz_file")
            local reduction=$(echo "scale=1; (($original_size - $jarz_size) * 100.0) / $original_size" | bc -l)
            
            total_original=$((total_original + original_size))
            total_compressed=$((total_compressed + jarz_size))
            
            echo "      ✅ ${reduction}% reduction ($(numfmt --to=iec $original_size) → $(numfmt --to=iec $jarz_size))"
        else
            echo "      ❌ Failed: $jar_name"
        fi
    done < <(find "$input_dir" -name "*.jar" -type f -size +0c -print0)
    
    # Summary
    if [ $total_original -gt 0 ]; then
        local overall_reduction=$(echo "scale=1; (($total_original - $total_compressed) * 100.0) / $total_original" | bc -l)
        echo "  📊 $framework_name Summary:"
        echo "    Converted: $converted/$total JARs"
        echo "    Original:  $(numfmt --to=iec $total_original)"
        echo "    JARZ:      $(numfmt --to=iec $total_compressed)"
        echo "    Reduction: ${overall_reduction}%"
    fi
}

# Framework-specific optimization
optimize_for_framework() {
    local framework="$1"
    local data_dir="$2"
    
    echo "🎯 Applying $framework-specific optimizations..."
    
    case "$framework" in
        "flink")
            # Flink-specific optimizations
            export JAVA_OPTS="$JAVA_OPTS -Djarz.flink.streaming.optimization=true -Djarz.flink.table.clustering=true"
            ;;
        "spark")
            # Spark-specific optimizations
            export JAVA_OPTS="$JAVA_OPTS -Djarz.spark.sql.optimization=true -Djarz.spark.catalyst.clustering=true"
            ;;
    esac
    
    # Apply optimizations to existing JARZ files
    local optimized_count=0
    while IFS= read -r -d '' jarz_file; do
        local temp_file="${jarz_file}.tmp"
        
        if java $JAVA_OPTS -jar "$JARZ_CLI" --optimize "$jarz_file" "$temp_file" 2>/dev/null; then
            mv "$temp_file" "$jarz_file"
            optimized_count=$((optimized_count + 1))
        else
            rm -f "$temp_file"
        fi
    done < <(find "$data_dir/jarz-converted" -name "*.jarz" -type f -print0 2>/dev/null)
    
    echo "  ✅ Optimized $optimized_count JARZ files for $framework"
}

# Main enhanced conversion function
main() {
    local framework="$1"
    local data_dir="$2"
    
    if [ -z "$framework" ] || [ -z "$data_dir" ]; then
        echo "Usage: $0 <framework> <data_dir>"
        echo "  framework: flink|spark"
        echo "  data_dir: path to framework data directory"
        exit 1
    fi
    
    if [ ! -d "$data_dir/original-jars" ]; then
        echo "❌ Original JARs directory not found: $data_dir/original-jars"
        exit 1
    fi
    
    # Check JARZ CLI
    if [ ! -f "$JARZ_CLI" ]; then
        echo "❌ JARZ CLI not found. Building..."
        cd "$PROJECT_ROOT/../.."
        mvn clean install -DskipTests -q
        echo "✅ JARZ CLI built"
    fi
    
    # Run enhanced conversion
    enhanced_convert "$data_dir/original-jars" "$data_dir/jarz-enhanced" "$framework"
    
    # Apply framework-specific optimizations
    optimize_for_framework "$framework" "$data_dir"
    
    echo "🎯 Enhanced conversion completed for $framework!"
}

# Execute if run directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi
