FROM mcr.microsoft.com/devcontainers/typescript-node:1-22-bookworm
USER root
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    xvfb fluxbox x11vnc novnc websockify dbus-x11 x11-utils \
    && rm -rf /var/lib/apt/lists/*
USER node
