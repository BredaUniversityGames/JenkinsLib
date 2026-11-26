#!/usr/bin/env bash
# One-time setup of the local test Jenkins controller in WSL.
# Safe to re-run: it only downloads what's missing, and re-resolves plugins.
#
# Everything goes into $JENKINSLIB_LOCAL_HOME (default ~/jenkinslib-local);
# delete that folder to start over. No sudo needed.
set -euo pipefail

JENKINS_VERSION="${JENKINS_VERSION:-2.580.3}"   # LTS
PIMT_VERSION="2.16.0"                           # Jenkins plugin installation manager
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../../.." && pwd)"
STATE="${JENKINSLIB_LOCAL_HOME:-$HOME/jenkinslib-local}"

mkdir -p "$STATE/home/plugins"
cd "$STATE"

# ── JDK 21 (Temurin) ──
if [ ! -x jdk/bin/java ]; then
    echo "Downloading Temurin JDK 21..."
    curl -fSL --progress-bar -o jdk.tar.gz \
        "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
    rm -rf jdk && mkdir jdk
    tar -xzf jdk.tar.gz -C jdk --strip-components=1
    rm jdk.tar.gz
fi
JAVA="$STATE/jdk/bin/java"
"$JAVA" -version 2>&1 | head -1

# ── Jenkins ──
WAR="jenkins-$JENKINS_VERSION.war"
if [ ! -f "$WAR" ]; then
    echo "Downloading Jenkins $JENKINS_VERSION..."
    BASE="https://get.jenkins.io/war-stable/$JENKINS_VERSION"
    curl -fSL --progress-bar -o "$WAR.tmp" "$BASE/jenkins.war"
    EXPECTED="$(curl -fsSL "$BASE/jenkins.war.sha256" | cut -d' ' -f1)"
    echo "$EXPECTED  $WAR.tmp" | sha256sum -c --quiet
    mv "$WAR.tmp" "$WAR"
fi
ln -sfn "$WAR" jenkins.war

# ── Plugins ──
PIMT="jenkins-plugin-manager-$PIMT_VERSION.jar"
if [ ! -f "$PIMT" ]; then
    echo "Downloading plugin installation manager $PIMT_VERSION..."
    curl -fSL --progress-bar -o "$PIMT" \
        "https://github.com/jenkinsci/plugin-installation-manager-tool/releases/download/$PIMT_VERSION/$PIMT"
fi
echo "Installing plugins from plugins.txt..."
"$JAVA" -jar "$PIMT" --war jenkins.war \
    --plugin-download-directory home/plugins \
    --plugin-file "$HERE/plugins.txt"

# ── Admin password (local test server only) ──
if [ ! -f admin-password ]; then
    (umask 077; head -c 24 /dev/urandom | base64 | tr -d '/+=' > admin-password)
fi

# ── Let git read the Windows checkout (owned by another user as seen from WSL) ──
if ! git config --global --get-all safe.directory 2>/dev/null | grep -qxF "$REPO"; then
    git config --global --add safe.directory "$REPO"
fi

echo
echo "Setup done. Start the controller with:"
echo "  $HERE/start-controller.sh"
