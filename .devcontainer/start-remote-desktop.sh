#!/usr/bin/env bash
set -euo pipefail
# Start a virtual X display for visible Playwright Chromium.
if ! pgrep -f "Xvfb :1" >/dev/null; then
  Xvfb :1 -screen 0 1440x900x24 -ac +extension RANDR >/tmp/scanbug-xvfb.log 2>&1 &
fi
export DISPLAY=:1
for i in {1..30}; do
  if xdpyinfo -display "$DISPLAY" >/dev/null 2>&1; then break; fi
  sleep 1
done
if ! pgrep -x fluxbox >/dev/null; then fluxbox >/tmp/scanbug-fluxbox.log 2>&1 & fi
if ! pgrep -f "x11vnc.*5900" >/dev/null; then
  x11vnc -display "$DISPLAY" -forever -shared -nopw -localhost -rfbport 5900 >/tmp/scanbug-x11vnc.log 2>&1 &
fi
if ! pgrep -f "websockify.*6080" >/dev/null; then
  websockify --web=/usr/share/novnc 6080 localhost:5900 >/tmp/scanbug-websockify.log 2>&1 &
fi
echo "ScanBug remote desktop ready on port 6080 (noVNC)."
