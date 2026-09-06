#!/usr/bin/env bash
# Convenience wrapper delegating to MobileLinux/gradlew
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$DIR/MobileLinux/gradlew" -p "$DIR/MobileLinux" "$@"
