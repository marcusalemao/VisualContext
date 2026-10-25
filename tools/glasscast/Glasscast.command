#!/bin/bash
# Fallback if the apps are missing: opens the panel and closes this Terminal window.
bash "$(dirname "$0")/glasscast-open.sh" painel
osascript -e 'tell application "Terminal" to close (every window whose name contains "Glasscast.command")' >/dev/null 2>&1 &
exit 0
