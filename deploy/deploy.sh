#!/usr/bin/env bash
# deploy.sh — One-shot deploy of whoisfreaks-mcp-server to a remote Ubuntu/Debian VM
#
# Usage:
#   chmod +x deploy/deploy.sh
#   ./deploy/deploy.sh <user@host> <api-key> [ip-whois-url] [asn-whois-url]
#
# Example:
#   ./deploy/deploy.sh ubuntu@192.168.1.10 my-secret-key http://localhost:8080 http://localhost:8081

set -euo pipefail

# ---- Arguments ----
TARGET="${1:?Usage: $0 <user@host> <api-key> [ip-whois-url] [asn-whois-url]}"
API_KEY="${2:?API key is required}"
IP_WHOIS_URL="${3:-http://localhost:8080}"
ASN_WHOIS_URL="${4:-http://localhost:8081}"

JAR="target/whoisfreaks-mcp-server-1.0.0.jar"
REMOTE_DIR="/opt/whoisfreaks-mcp"
SERVICE_FILE="deploy/whoisfreaks-mcp.service"

echo "==> Building fat JAR..."
mvn clean package -q -DskipTests

if [[ ! -f "$JAR" ]]; then
  echo "ERROR: JAR not found at $JAR. Build failed."
  exit 1
fi

echo "==> Deploying to $TARGET ..."

# Create remote directory and system user
ssh "$TARGET" bash <<REMOTE
  set -e
  sudo mkdir -p $REMOTE_DIR
  id -u whoisfreaks &>/dev/null || sudo useradd --system --no-create-home --shell /sbin/nologin whoisfreaks
  sudo chown whoisfreaks:whoisfreaks $REMOTE_DIR
REMOTE

# Copy JAR and systemd unit
scp "$JAR" "$TARGET:$REMOTE_DIR/whoisfreaks-mcp-server-1.0.0.jar"
scp "$SERVICE_FILE" "$TARGET:/tmp/whoisfreaks-mcp.service"

# Configure environment file and install service
ssh "$TARGET" bash <<REMOTE
  set -e

  # Environment file (holds secrets — chmod 600)
  sudo mkdir -p /etc/whoisfreaks-mcp
  sudo tee /etc/whoisfreaks-mcp/env > /dev/null <<ENV
WHOISFREAKS_API_KEY=$API_KEY
WHOISFREAKS_IP_WHOIS_URL=$IP_WHOIS_URL
WHOISFREAKS_ASN_WHOIS_URL=$ASN_WHOIS_URL
ENV
  sudo chmod 600 /etc/whoisfreaks-mcp/env
  sudo chown root:root /etc/whoisfreaks-mcp/env

  # Install and enable the systemd service
  sudo mv /tmp/whoisfreaks-mcp.service /etc/systemd/system/
  sudo systemctl daemon-reload
  sudo systemctl enable whoisfreaks-mcp
  sudo systemctl restart whoisfreaks-mcp
  sudo systemctl status whoisfreaks-mcp --no-pager
REMOTE

echo ""
echo "==> Done. Server is running on $TARGET."
echo "    Logs: ssh $TARGET 'journalctl -u whoisfreaks-mcp -f'"
