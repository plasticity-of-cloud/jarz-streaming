#!/bin/bash
set -e

# JARZ Repository Sync Script (Rsync Strategy)
# Usage: ./scripts/sync-to-public.sh <tag-name> [public-repo-path]
# Example: ./scripts/sync-to-public.sh v2.1.3-pre-ga
# Example: ./scripts/sync-to-public.sh v2.1.3-pre-ga /path/to/custom/public/repo

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
DEFAULT_PUBLIC_REPO="/home/ubuntu/projects/pl-cloud/jarz-streaming"
TEMP_SYNC_DIR="/tmp/jarz-sync-$$"

cd "$PROJECT_ROOT"

# Get arguments
if [ $# -lt 1 ] || [ $# -gt 2 ]; then
    echo "Usage: $0 <tag-name> [public-repo-path]"
    echo "Example: $0 v2.1.3-pre-ga"
    echo "Example: $0 v2.1.3-pre-ga /path/to/custom/public/repo"
    exit 1
fi

TAG_NAME="$1"
PUBLIC_REPO="${2:-$DEFAULT_PUBLIC_REPO}"

echo "Tag: $TAG_NAME"
echo "Public repo: $PUBLIC_REPO"

# Verify tag exists
if ! git rev-parse "$TAG_NAME" >/dev/null 2>&1; then
    echo "Error: Tag '$TAG_NAME' does not exist"
    exit 1
fi

echo "Syncing tag '$TAG_NAME' to public repository..."

# Create temporary directory
mkdir -p "$TEMP_SYNC_DIR"
trap "rm -rf '$TEMP_SYNC_DIR'" EXIT

# Extract tag to temporary directory
echo "Extracting tag to temporary directory..."
git archive --format=tar "$TAG_NAME" | tar -x -C "$TEMP_SYNC_DIR"

# Verify public repo exists
if [ ! -d "$PUBLIC_REPO" ]; then
    echo "Error: Public repository not found at $PUBLIC_REPO"
    exit 1
fi

# Sync with rsync (delete files not in source, exclude .kiro)
echo "Syncing to public repository with rsync..."
rsync -av --delete --exclude='.kiro*' --exclude='.git*' "$TEMP_SYNC_DIR/" "$PUBLIC_REPO/"

# Commit changes in public repo
cd "$PUBLIC_REPO"
echo "Committing changes in public repository..."

# Check if there are changes
if git diff --quiet && git diff --cached --quiet; then
    echo "No changes to commit"
else
    git add .
    git commit -m "Sync from private repo $TAG_NAME

- Complete synchronization using rsync strategy
- Ensures exact match with private repository tag
- Removes files not present in private repo"
    
    echo "Pushing changes to public repository..."
    git push
    
    echo "✅ Successfully synced $TAG_NAME to public repository"
fi

echo "Sync completed!"
