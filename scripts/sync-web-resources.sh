#!/usr/bin/env bash
# Safe Web Resource Synchronization Script for Linux / Bash
# Invariant 5 (P1) - Asset Parity
# Synchronizes HTML, JS, CSS and CSS source inputs from web-ui/ to web-ui/public/
# Excludes public/, node_modules/, tests/, and removes server.js from destination.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
SRC_DIR="$PROJECT_ROOT/web-ui"
DEST_DIR="$PROJECT_ROOT/web-ui/public"

echo "Synchronizing static assets from $SRC_DIR to $DEST_DIR..."

mkdir -p "$DEST_DIR"

# Copy all .html, .js, .css files preserving structure, excluding public, node_modules, tests, and server.js
(cd "$SRC_DIR" && find . -type f \( -name "*.html" -o -name "*.js" -o -name "*.css" -o -name "*.input.css" \) \
  ! -path "./public*" \
  ! -path "./node_modules*" \
  ! -path "./tests*" \
  ! -name "server.js" \
  -exec cp --parents "{}" "$DEST_DIR/" \;)

# Remove server.js from public if present
if [ -f "$DEST_DIR/server.js" ]; then
  rm -f "$DEST_DIR/server.js"
  echo "Removed server.js from $DEST_DIR"
fi

echo "Synchronization complete."
