#!/usr/bin/env bash
# deploy.sh — One-shot deploy of whoisfreaks-mcp-server to a remote Ubuntu/Debian VM
#
# Usage:
#   chmod +x deploy/deploy.sh
#   ./deploy/deploy.sh <user@host> <api-key>
#
# Examples:
#   ./deploy/deploy.sh ubuntu@192.168.1.10 my-api-key-here
#   ./deploy/deploy.sh ec2-user@54.23.11.5 my-api-key-here

set -euo pipefail

# ---- Arguments ----------------------------------------------------------------
TARGET="${1:?Usage: $0 <user@host> <api-key>}"
API_KEY="${2:?API key is required. Get yours at https://whoisfreaks.com/billing}"

JAR="target/whoisfreaks-mcp-server-1.0.0.jar"
REMOTE_DIR="/opt/whoisfreaks-mcp"
SERVICE_FILE="deploy/whoisfreaks-mcp.service"
ENV_FILE="/etc/whoisfreaks-mcp/env"

# ---- Build --------------------------------------------------------------------
echo "==> [1/4] Building fat JAR..."
mvn clean package -q -DskipTests

if [[ ! -f "$JAR" ]]; then
  echo "ERROR: JAR not found at $JAR. Build may have failed."
  exit 1
fi

echo "    Built: $JAR ($(du -h "$JAR" | cut -f1))"

# ---- Prepare remote environment -----------------------------------------------
echo "==> [2/4] Preparing remote VM at $TARGET ..."
ssh "$TARGET" bash <<REMOTE
  set -e

  # Install Java 17 if not present
  if ! java -version 2>&1 | grep -q '17\|21'; then
    echo "    Installing Java 17..."
    sudo apt-get update -qq
    sudo apt-get install -y -qq openjdk-17-jre-headless
  fi

  # Create deploy directory and system user
  sudo mkdir -p $REMOTE_DIR
  if ! id -u whoisfreaks &>/dev/null; then
    sudo useradd --system --no-create-home --shell /sbin/nologin whoisfreaks
  fi
  sudo chown whoisfreaks:whoisfreaks $REMOTE_DIR
REMOTE

# ---- Copy files ---------------------------------------------------------------
echo "==> [3/4] Copying JAR and service file..."
scp -q "$JAR" "$TARGET:$REMOTE_DIR/whoisfreaks-mcp-server-1.0.0.jar"
scp -q "$SERVICE_FILE" "$TARGET:/tmp/whoisfreaks-mcp.service"

# ---- Configure and start service ----------------------------------------------
echo "==> [4/4] Installing and starting systemd service..."
ssh "$TARGET" bash <<REMOTE
  set -e

  # Write the environment file (holds the API key securely)
  sudo mkdir -p /etc/whoisfreaks-mcp
  sudo tee $ENV_FILE > /dev/null <<ENV
WHOISFREAKS_API_KEY=$API_KEY
ENV
  sudo chmod 600 $ENV_FILE
  sudo chown root:root $ENV_FILE

  # Install and start the systemd service
  sudo mv /tmp/whoisfreaks-mcp.service /etc/systemd/system/
  sudo systemctl daemon-reload
  sudo systemctl enable whoisfreaks-mcp
  sudo systemctl restart whoisfreaks-mcp

  # Short wait and status check
  sleep 2
  sudo systemctl status whoisfreaks-mcp --no-pager -l
REMOTE

echo ""
echo "==> Done! WhoisFreaks MCP Server is running on $TARGET"
echo ""
echo "    Useful commands:"
echo "    View logs:    ssh $TARGET 'journalctl -u whoisfreaks-mcp -f'"
echo "    Stop server:  ssh $TARGET 'sudo systemctl stop whoisfreaks-mcp'"
echo "    Restart:      ssh $TARGET 'sudo systemctl restart whoisfreaks-mcp'"
echo "    Update key:   ssh $TARGET 'sudo nano /etc/whoisfreaks-mcp/env && sudo systemctl restart whoisfreaks-mcp'"
