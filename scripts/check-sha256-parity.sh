#!/usr/bin/env bash
# SHA-256 Hash Code Comparison Script for Linux / Bash
# Validates Invariant 5 (P1) - Byte-for-Byte Asset Parity

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
SRC_DIR="$PROJECT_ROOT/web-ui"
DEST_DIR="$PROJECT_ROOT/web-ui/public"

mapfile -t CORE_FILES < <(cd "$SRC_DIR" && find . -type f \( -name "*.html" -o -name "*.js" -o -name "*.css" -o -name "*.input.css" \) \
  ! -path "./public*" ! -path "./node_modules*" ! -path "./tests*" ! -name "server.js" | sed 's#^./##')

echo "================================================================================"
echo "  INVARIANT 5 (P1): SHA-256 ASSET PARITY VERIFICATION (LINUX / BASH)"
echo "================================================================================"

ALL_PASS=true

for rel in "${CORE_FILES[@]}"; do
    f1="$SRC_DIR/$rel"
    f2="$DEST_DIR/$rel"
    
    if [ ! -f "$f1" ]; then
        echo "[-] $rel : SOURCE MISSING"
        ALL_PASS=false
        continue
    fi
    if [ ! -f "$f2" ]; then
        echo "[-] $rel : DESTINATION MISSING"
        ALL_PASS=false
        continue
    fi
    
    h1=$(sha256sum "$f1" | awk '{print $1}')
    h2=$(sha256sum "$f2" | awk '{print $1}')
    
    if [ "$h1" = "$h2" ]; then
        echo "[+] $rel : MATCH (PASS)"
        echo "    SHA-256: $h1"
    else
        echo "[-] $rel : MISMATCH (FAIL)"
        echo "    Source: $h1"
        echo "    Public: $h2"
        ALL_PASS=false
    fi
done

echo "--------------------------------------------------------------------------------"
if [ "$ALL_PASS" = true ]; then
    echo "VERIFICATION RESULT: ALL CORE FILES MATCH SHA-256 (100% PARITY - GATE-01 PASS)"
    exit 0
else
    echo "VERIFICATION RESULT: PARITY MISMATCH DETECTED (GATE-01 FAIL)"
    exit 1
fi
