#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_java="/home/alanno/.local/share/rotp-toolchains/jdk-25.0.4.1+1"
if [[ -d "$project_java" ]]; then
    export JAVA_HOME="$project_java"
fi
exec "$project_dir/fabric/gradlew" -p "$project_dir/fabric" runClient "$@"
