#!/usr/bin/env bash
set -euo pipefail

# Installed in /etc/bash_scripts; launch the project's current playable build.
playtest_launcher="/home/alanno/Documents/Soft/ROTP_port/play-draft.sh"
if [[ ! -x "$playtest_launcher" ]]; then
    printf 'Playtest launcher missing or not executable: %s\n' "$playtest_launcher" >&2
    exit 1
fi
exec "$playtest_launcher" "$@"
