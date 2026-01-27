#!/bin/bash
# Master EMR JARZ Analysis Script
# Orchestrates complete EMR analysis workflow

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "🚀 JARZ Framework Analysis AWS"
echo "=============================="
echo "Enterprise EMR Container Optimization"
echo ""

# Display menu
show_menu() {
    echo "Available analyses:"
    echo "  1) Flink EMR analysis"
    echo "  2) Spark EMR analysis" 
    echo "  3) Combined EMR analysis"
    echo "  4) Enhanced conversion (Flink)"
    echo "  5) Enhanced conversion (Spark)"
    echo "  6) Full analysis pipeline"
    echo "  7) View existing results"
    echo "  q) Quit"
    echo ""
}

# View results function
view_results() {
    echo "📊 Available Results:"
    echo ""
    
    if [ -d "$PROJECT_ROOT/results" ] && [ "$(ls -A "$PROJECT_ROOT/results" 2>/dev/null)" ]; then
        ls -la "$PROJECT_ROOT/results"/*.md 2>/dev/null | while read -r line; do
            echo "  $line"
        done
    else
        echo "  No results found. Run analysis first."
    fi
    echo ""
}

# Main execution
main() {
    while true; do
        show_menu
        read -p "Select option [1-7,q]: " choice
        echo ""
        
        case $choice in
            1)
                echo "🔄 Running Flink EMR analysis..."
                "$SCRIPT_DIR/analyze-flink-emr.sh"
                ;;
            2)
                echo "🔄 Running Spark EMR analysis..."
                "$SCRIPT_DIR/analyze-spark-emr.sh"
                ;;
            3)
                echo "🔄 Running combined EMR analysis..."
                "$SCRIPT_DIR/analyze-emr-combined.sh"
                ;;
            4)
                echo "🔄 Running enhanced Flink conversion..."
                "$SCRIPT_DIR/enhanced-conversion.sh" flink "$PROJECT_ROOT/data/flink-emr"
                ;;
            5)
                echo "🔄 Running enhanced Spark conversion..."
                "$SCRIPT_DIR/enhanced-conversion.sh" spark "$PROJECT_ROOT/data/spark-emr"
                ;;
            6)
                echo "🔄 Running full analysis pipeline..."
                "$SCRIPT_DIR/analyze-flink-emr.sh"
                "$SCRIPT_DIR/analyze-spark-emr.sh"
                "$SCRIPT_DIR/enhanced-conversion.sh" flink "$PROJECT_ROOT/data/flink-emr"
                "$SCRIPT_DIR/enhanced-conversion.sh" spark "$PROJECT_ROOT/data/spark-emr"
                "$SCRIPT_DIR/analyze-emr-combined.sh"
                ;;
            7)
                view_results
                ;;
            q|Q)
                echo "👋 Goodbye!"
                exit 0
                ;;
            *)
                echo "❌ Invalid option. Please try again."
                ;;
        esac
        
        echo ""
        read -p "Press Enter to continue..."
        echo ""
    done
}

# Execute
main "$@"
