#!/usr/bin/env bash
# Starts the local test Jenkins controller in WSL (foreground; Ctrl+C stops it).
# Open http://localhost:8095 in Windows; log in as "admin" with the password
# in ~/jenkinslib-local/admin-password.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../../.." && pwd)"
STATE="${JENKINSLIB_LOCAL_HOME:-$HOME/jenkinslib-local}"

if [ ! -f "$STATE/jenkins.war" ] || [ ! -f "$STATE/admin-password" ]; then
    echo "Not set up yet. Run: $HERE/setup-controller.sh" >&2
    exit 1
fi

# Per-machine settings that don't belong in the repo, e.g.
#   UE5_ENGINE_ROOT='D:\Epic Games'
if [ -f "$STATE/local.env" ]; then
    set -a
    # shellcheck disable=SC1091
    . "$STATE/local.env"
    set +a
fi

export JENKINS_HOME="$STATE/home"
export JENKINSLIB_PORT="${JENKINSLIB_PORT:-8095}"
export JENKINSLIB_REPO="file://$REPO"
export JENKINSLIB_LOCAL_DIR="$HERE"
export CASC_JENKINS_CONFIG="$HERE/casc.yaml"
ADMIN_PASSWORD="$(cat "$STATE/admin-password")"
export ADMIN_PASSWORD

echo "Jenkins: http://localhost:$JENKINSLIB_PORT  (user: admin, password: $STATE/admin-password)"
echo "Library and jobs read from: $JENKINSLIB_REPO (ref jenkins-local)"

# ALLOW_LOCAL_CHECKOUT: the library and jobs come from a local repository,
# which the Git plugin blocks by default. Only acceptable on a test server.
# preferIPv4Stack: otherwise Java listens on [::ffff:127.0.0.1], which WSL
# doesn't forward to localhost on Windows.
exec "$STATE/jdk/bin/java" \
    -Djava.awt.headless=true \
    -Djava.net.preferIPv4Stack=true \
    -Djenkins.install.runSetupWizard=false \
    -Dhudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT=true \
    -jar "$STATE/jenkins.war" \
    --httpPort="$JENKINSLIB_PORT" \
    --httpListenAddress=127.0.0.1
