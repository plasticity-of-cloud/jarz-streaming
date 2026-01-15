#!/bin/bash
set -e

# JARZ Version Bump Script
# Usage: ./scripts/bump-version.sh [next-version]
# Example: ./scripts/bump-version.sh 1.1-SNAPSHOT

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

# Get current version
CURRENT_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
echo "Current version: $CURRENT_VERSION"

# Determine next version
if [ $# -eq 1 ]; then
    NEXT_VERSION="$1"
else
    # Auto-increment minor version for SNAPSHOT
    if [[ "$CURRENT_VERSION" == *"-SNAPSHOT" ]]; then
        BASE_VERSION="${CURRENT_VERSION%-SNAPSHOT}"
    else
        BASE_VERSION="$CURRENT_VERSION"
    fi
    
    # Extract major.minor and increment minor
    MAJOR=$(echo "$BASE_VERSION" | cut -d. -f1)
    MINOR=$(echo "$BASE_VERSION" | cut -d. -f2)
    NEXT_MINOR=$((MINOR + 1))
    NEXT_VERSION="${MAJOR}.${NEXT_MINOR}-SNAPSHOT"
fi

echo "Next version: $NEXT_VERSION"

# Confirm with user
read -p "Proceed with version bump? (y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Aborted."
    exit 1
fi

# Update version
echo "Updating version..."
mvn versions:set -DnewVersion="$NEXT_VERSION"
mvn versions:commit

# Commit changes
echo "Committing changes..."
git add pom.xml */pom.xml
git commit -m "Bump version to $NEXT_VERSION after release"

echo "✅ Version bumped to $NEXT_VERSION"
echo "Run 'git push' to push changes to remote"
