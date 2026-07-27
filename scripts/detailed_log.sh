#!/bin/bash
set -e

echo "=== Full Build Log ==="
if [ -f "build.log" ]; then
    cat build.log
else
    echo "Build log not found"
fi

echo -e "\n=== Gradle Wrapper ==="
cat gradle/wrapper/gradle-wrapper.properties

echo -e "\n=== Gradle Settings ==="
cat gradle.properties
git --no-pager diff HEAD~1 gradle.properties || true

echo -e "\n=== Build Script Changes ==="
git --no-pager diff HEAD~1 -- .github/workflows/universal-debug-build.yml

echo -e "\n=== Build Log Truncated ==="
if [ -d ".gradle" ]; then
    find .gradle -name "*.log" -exec echo "=== $(basename {}) ===" \; -exec cat {} \;
else
    echo "No .gradle directory"
fi
