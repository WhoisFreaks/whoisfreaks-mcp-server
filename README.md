# WhoisFreaks MCP Server

A **Model Context Protocol (MCP)** server that exposes the full **WhoisFreaks API** suite as AI-callable tools. Works with Claude Desktop, Cursor, Windsurf, VS Code, Continue, Zed, and any other MCP-compatible AI client.

**Two ways to run it — pick what fits your setup:**

| | Method | Requires | Best for |
|--|--------|----------|----------|
| ⭐ **Recommended** | Docker (pre-built image) | Docker only | Everyone — no Java or Maven needed |
| | Build from source | Java 17 + Maven | Contributors / custom builds |

Once running, connect it to any MCP client via **stdio** (local) or **HTTP/SSE on port 3100** (remote VM).

---

## Table of Contents

1. [Tools Reference (14 tools)](#tools-reference)
2. [Quick Start](#quick-start)
   - [⭐ Recommended — Docker](#-recommended--docker)
   - [Build from Source](#build-from-source)
3. [Prerequisites](#prerequisites)
4. [Platform Integration](#platform-integration)
   - [1. Claude Desktop](#1-claude-desktop)
   - [2. Cursor IDE](#2-cursor-ide)
   - [3. Windsurf IDE](#3-windsurf-ide)
   - [4. VS Code + GitHub Copilot](#4-vs-code--github-copilot)
   - [5. Continue.dev](#5-continuedev)
   - [6. Zed Editor](#6-zed-editor)
5. [Example Prompts](#example-prompts)
6. [Troubleshooting](#troubleshooting)

---

## Tools Reference

### WHOIS Tools
| Tool | Description |
|------|-------------|
| `liveWhoisLookup` | Real-time WHOIS data for any domain — registrar, registrant, dates, nameservers |
| `whoisHistory` | Complete ownership history timeline for a domain |
| `reverseWhoisLookup` | Find all domains registered by an email address, keyword, owner name, or company |

### IP & ASN WHOIS Tools
| Tool | Description |
|------|-------------|
| `ipWhoisLookup` | WHOIS registration data for any IPv4 or IPv6 address |
| `asnWhoisLookup` | WHOIS data for an Autonomous System Number (e.g. AS15169) |

### DNS Tools
| Tool | Description |
|------|-------------|
| `dnsLookup` | Live DNS records — A, AAAA, MX, NS, CNAME, SOA, TXT, SPF, or all |
| `dnsHistory` | Historical DNS records with full change timeline |
| `reverseDnsLookup` | Find all domains pointing to a given IP or nameserver |

### IP Intelligence Tools
| Tool | Description |
|------|-------------|
| `ipGeolocation` | Country, city, region, ISP, and coordinates for any IP address |
| `ipSecurity` | VPN, proxy, Tor exit node, bot, and threat intelligence for any IP |

### Domain Tools
| Tool | Description |
|------|-------------|
| `domainAvailability` | Check if a domain is available to register, with optional suggestions |
| `subdomainLookup` | Enumerate all known subdomains for a domain, with status and date filters |
| `domainDiscovery` | Find domains by keyword, including typosquatting and similar variants |

### SSL Tools
| Tool | Description |
|------|-------------|
| `sslLookup` | SSL/TLS certificate details — issuer, expiry, SANs, chain, and raw output |

---

## Quick Start

### ⭐ Recommended — Docker

No Java or Maven required. Pull the pre-built image directly from Docker Hub and run.

**Get your free API key first:** [whoisfreaks.com/signup](https://whoisfreaks.com/signup)

---

#### Option 1 — Direct `docker run` (simplest)

```bash
docker run -d \
  --name whoisfreaks-mcp \
  --restart unless-stopped \
  -p 3100:3100 \
  -e WHOISFREAKS_API_KEY=your-api-key-here \
  whoisfreaks/mcp-server:latest
```

The MCP gateway is now live at **`http://localhost:3100/sse`**.

---

#### Option 2 — Docker Compose (recommended for production / VM)

Create a `docker-compose.yml`:

```yaml
services:
  whoisfreaks-mcp:
    image: whoisfreaks/mcp-server:latest
    container_name: whoisfreaks-mcp
    restart: unless-stopped
    ports:
      - "3100:3100"
    environment:
      WHOISFREAKS_API_KEY: your-api-key-here
```

Start it:

```bash
docker compose up -d
```

Or use a `.env` file instead of hardcoding the key (recommended):

```bash
# Create .env file (never commit this to git)
echo "WHOISFREAKS_API_KEY=your-api-key-here" > .env
docker compose up -d
```

---

#### Verify Docker is working

```bash
# Container should show as "Up"
docker ps

# SSE endpoint should respond
curl http://localhost:3100/health
# → {"status":"ok"}

# List all 14 registered tools
curl http://localhost:3100/tools/list
```

---

#### Use with Claude Desktop (Docker stdio mode)

Claude Desktop requires stdio transport. Override the Docker entrypoint to bypass `mcp-proxy` and run the JAR directly:

```json
{
  "mcpServers": {
    "whoisfreaks": {
      "command": "docker",
      "args": [
        "run", "-i", "--rm",
        "-e", "WHOISFREAKS_API_KEY=your-api-key-here",
        "--entrypoint", "java",
        "whoisfreaks/mcp-server:latest",
        "-jar", "app.jar"
      ]
    }
  }
}
```

> No port mapping needed here — Claude Desktop talks to the container directly via stdio.

---

### Build from Source

Only needed if you want to modify the code or build a custom version.

**Prerequisites:**

| Requirement | Minimum Version | Notes |
|-------------|-----------------|-------|
| Java (JDK) | 17 | Check with `java -version` |
| Maven | 3.8 | Check with `mvn -version` |
| WhoisFreaks API Key | — | Get yours at [whoisfreaks.com/signup](https://whoisfreaks.com/signup) |

**Clone and build:**

```bash
git clone https://github.com/whoisfreaks/whoisfreaks-mcp-server.git
cd whoisfreaks-mcp-server
mvn clean package -q
```

This produces a single self-contained fat JAR:

```
target/whoisfreaks-mcp-server-1.0.0.jar
```

> **Tip:** Note the **absolute path** to this JAR — you will need it in the platform configs below.
> Example: `/Users/yourname/whoisfreaks-mcp-server/target/whoisfreaks-mcp-server-1.0.0.jar`

---

## Prerequisites

| Requirement | Notes |
|-------------|-------|
| Docker | Required for the recommended Docker path |
| Java 17 + Maven 3.8 | Required only if building from source |
| WhoisFreaks API Key | Free at [whoisfreaks.com/signup](https://whoisfreaks.com/signup) |

---

## Platform Integration

### 1. Claude Desktop

The most popular MCP client. Claude Desktop launches the MCP server as a subprocess on startup and communicates via **stdio** — it does not support SSE/HTTP URLs.

**Config file location:**

| OS | Path |
|----|------|
| macOS | `~/Library/Application Support/Claude/claude_desktop_config.json` |
| Windows | `%APPDATA%\Claude\claude_desktop_config.json` |
| Linux | `~/.config/Claude/claude_desktop_config.json` |

---

**⭐ Option A — Docker (recommended, no Java needed)**

```json
{
  "mcpServers": {
    "whoisfreaks": {
      "command": "docker",
      "args": [
        "run", "-i", "--rm",
        "-e", "WHOISFREAKS_API_KEY=your-api-key-here",
        "--entrypoint", "java",
        "whoisfreaks/mcp-server:latest",
        "-jar", "app.jar"
      ]
    }
  }
}
```

> Docker must be running before you open Claude Desktop. The image is pulled automatically on first use.

---

**Option B — JAR directly (requires Java 17)**

```json
{
  "mcpServers": {
    "whoisfreaks": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/whoisfreaks-mcp-server-1.0.0.jar"],
      "env": {
        "WHOISFREAKS_API_KEY": "your-api-key-here"
      }
    }
  }
}
```

---

**Steps (both options):**
1. Paste the config above into the config file (create it if it doesn't exist)
2. Save the file
3. Quit Claude Desktop completely (`Cmd+Q` on macOS / taskbar exit on Windows)
4. Reopen Claude Desktop
5. Click the **hammer icon** (Tools) in the chat input — you should see 14 WhoisFreaks tools listed

**Verify it works:** Type `Who owns google.com?` and Claude will automatically call `liveWhoisLookup`.

---

### 2. Cursor IDE

Cursor supports both stdio and SSE transports. Config file: `~/.cursor/mcp.json`

**⭐ Option A — Docker via SSE (recommended)**

```json
{
  "mcpServers": {
    "whoisfreaks": {
      "url": "http://localhost:3100/sse",
      "transport": "sse"
    }
  }
}
```

> Start the Docker container first: `docker run -d -p 3100:3100 -e WHOISFREAKS_API_KEY=your-key whoisfreaks/mcp-server:latest`

---

**Option B — Docker via stdio (no port needed)**

```json
{
  "mcpServers": {
    "whoisfreaks": {
      "command": "docker",
      "args": [
        "run", "-i", "--rm",
        "-e", "WHOISFREAKS_API_KEY=your-api-key-here",
        "--entrypoint", "java",
        "whoisfreaks/mcp-server:latest",
        "-jar", "app.jar"
      ]
    }
  }
}
```

---

**Option C — JAR directly (requires Java 17)**

```json
{
  "mcpServers": {
    "whoisfreaks": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/whoisfreaks-mcp-server-1.0.0.jar"],
      "env": {
        "WHOISFREAKS_API_KEY": "your-api-key-here"
      }
    }
  }
}
```

**Steps:**
1. Save `~/.cursor/mcp.json` with your chosen option
2. Restart Cursor
3. Open the **AI Panel** (`Cmd+L`) → you should see a tools indicator showing WhoisFreaks tools
4. Ask: `Check the SSL certificate for github.com`

---

### 3. Windsurf IDE

Windsurf (by Codeium) supports MCP via its Cascade AI sidebar.

**Config file location:**

| OS | Path |
|----|------|
| macOS | `~/.codeium/windsurf/mcp_config.json` |
| Windows | `%USERPROFILE%\.codeium\windsurf\mcp_config.json` |
| Linux | `~/.codeium/windsurf/mcp_config.json` |

**⭐ Option A — Docker via stdio (recommended)**

```json
{
  "mcpServers": {
    "whoisfreaks": {
      "command": "docker",
      "args": [
        "run", "-i", "--rm",
        "-e", "WHOISFREAKS_API_KEY=your-api-key-here",
        "--entrypoint", "java",
        "whoisfreaks/mcp-server:latest",
        "-jar", "app.jar"
      ]
    }
  }
}
```

**Option B — JAR directly (requires Java 17)**

```json
{
  "mcpServers": {
    "whoisfreaks": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/whoisfreaks-mcp-server-1.0.0.jar"],
      "env": {
        "WHOISFREAKS_API_KEY": "your-api-key-here"
      }
    }
  }
}
```

**Steps:**
1. Save the config with your chosen option
2. Restart Windsurf
3. Open Cascade sidebar → look for the MCP tools indicator (plug icon)
4. Ask: `Find all subdomains of tesla.com`

> **Note:** Windsurf requires Cascade to be in **Write** mode for tool use. Toggle it from the Cascade header if tools are not being called.

---

### 4. VS Code + GitHub Copilot

VS Code supports MCP tools through the GitHub Copilot extension (Chat Participants / Tools feature).

**Config file** (`.vscode/mcp.json` in your workspace, or `~/.vscode/mcp.json` globally):

**⭐ Option A — Docker via SSE (recommended, container must be running)**

```json
{
  "servers": {
    "whoisfreaks": {
      "type": "sse",
      "url": "http://localhost:3100/sse"
    }
  }
}
```

> Start the container first: `docker run -d -p 3100:3100 -e WHOISFREAKS_API_KEY=your-key whoisfreaks/mcp-server:latest`

**Option B — Docker via stdio**

```json
{
  "servers": {
    "whoisfreaks": {
      "type": "stdio",
      "command": "docker",
      "args": [
        "run", "-i", "--rm",
        "-e", "WHOISFREAKS_API_KEY=your-api-key-here",
        "--entrypoint", "java",
        "whoisfreaks/mcp-server:latest",
        "-jar", "app.jar"
      ]
    }
  }
}
```

**Option C — JAR directly (requires Java 17)**

```json
{
  "servers": {
    "whoisfreaks": {
      "type": "stdio",
      "command": "java",
      "args": ["-jar", "/absolute/path/to/whoisfreaks-mcp-server-1.0.0.jar"],
      "env": {
        "WHOISFREAKS_API_KEY": "your-api-key-here"
      }
    }
  }
}
```

**Steps:**
1. Make sure you have the **GitHub Copilot Chat** extension installed (v1.99+)
2. Save the config file above with your chosen option
3. Open Copilot Chat (`Ctrl+Shift+I` / `Cmd+Shift+I`)
4. Switch the model to **Agent mode** using the dropdown
5. Click the **Tools** button — WhoisFreaks tools should appear in the list
6. Ask: `What are the MX records for github.com?`

> **Requirement:** VS Code 1.99+ and GitHub Copilot Chat extension are required for MCP tool support.

---

### 5. Continue.dev

Continue is an open-source AI coding assistant with MCP support for VS Code and JetBrains IDEs.

**Config file** (`~/.continue/config.json`):

**⭐ Option A — Docker via SSE (recommended, container must be running)**

```json
{
  "mcpServers": [
    {
      "name": "whoisfreaks",
      "url": "http://localhost:3100/sse",
      "transport": "sse"
    }
  ]
}
```

> Start the container first: `docker run -d -p 3100:3100 -e WHOISFREAKS_API_KEY=your-key whoisfreaks/mcp-server:latest`

**Option B — Docker via stdio**

```json
{
  "mcpServers": [
    {
      "name": "whoisfreaks",
      "command": "docker",
      "args": [
        "run", "-i", "--rm",
        "-e", "WHOISFREAKS_API_KEY=your-api-key-here",
        "--entrypoint", "java",
        "whoisfreaks/mcp-server:latest",
        "-jar", "app.jar"
      ]
    }
  ]
}
```

**Option C — JAR directly (requires Java 17)**

```json
{
  "mcpServers": [
    {
      "name": "whoisfreaks",
      "command": "java",
      "args": ["-jar", "/absolute/path/to/whoisfreaks-mcp-server-1.0.0.jar"],
      "env": {
        "WHOISFREAKS_API_KEY": "your-api-key-here"
      }
    }
  ]
}
```

**Steps:**
1. Install the **Continue** extension from the VS Code Marketplace or JetBrains Plugin Marketplace
2. Edit `~/.continue/config.json` with your chosen option
3. Reload the window (`Cmd+Shift+P` → `Reload Window`)
4. Open Continue sidebar — tools from WhoisFreaks will be available in chat
5. Ask: `Look up IP geolocation for 1.1.1.1`

---

### 6. Zed Editor

Zed has a built-in AI assistant with MCP support via its `assistant` configuration.

**Config file** (`~/.config/zed/settings.json`):

**⭐ Option A — Docker via stdio (recommended)**

```json
{
  "assistant": {
    "mcp_servers": {
      "whoisfreaks": {
        "command": "docker",
        "args": [
          "run", "-i", "--rm",
          "-e", "WHOISFREAKS_API_KEY=your-api-key-here",
          "--entrypoint", "java",
          "whoisfreaks/mcp-server:latest",
          "-jar", "app.jar"
        ]
      }
    }
  }
}
```

**Option B — JAR directly (requires Java 17)**

```json
{
  "assistant": {
    "mcp_servers": {
      "whoisfreaks": {
        "command": "java",
        "args": ["-jar", "/absolute/path/to/whoisfreaks-mcp-server-1.0.0.jar"],
        "env": {
          "WHOISFREAKS_API_KEY": "your-api-key-here"
        }
      }
    }
  }
}
```

**Steps:**
1. Open Zed → `Zed` menu → `Settings` (or `Cmd+,`)
2. Add the `mcp_servers` block to your existing settings JSON using your chosen option
3. Save and restart Zed
4. Open the AI panel (`Cmd+?`) → tools will be listed under the tools indicator
5. Ask: `Is the domain myapp.io available?`

---

## Example Prompts

Once configured in any client above, try these prompts:

```
# WHOIS
Who owns the domain apple.com? Give me full WHOIS details.
Show me the complete ownership history for whoisfreaks.com.
Find all domains registered by admin@google.com.

# IP & ASN WHOIS
What organization owns the IP address 8.8.8.8?
Look up ASN information for AS15169.

# DNS
What are the MX and NS records for github.com?
Has facebook.com changed its IP addresses in the last 2 years? (DNS history)
Which domains are pointing to the IP 104.21.0.0? (reverse DNS)

# IP Intelligence
Where is 1.1.1.1 located? What ISP runs it?
Is 185.220.101.45 a Tor exit node or a VPN?

# Domain Tools
Is mycompany.io available to register? Suggest 5 alternatives.
List all known subdomains of tesla.com.
Find domains similar to 'google' — possible typosquatting targets.

# SSL
Check the SSL certificate for github.com — who issued it and when does it expire?
```

---

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `WHOISFREAKS_API_KEY` | **Yes** | Your API key from [whoisfreaks.com/billing](https://whoisfreaks.com/billing). All 14 tools call `api.whoisfreaks.com` directly using this key — no other variables are needed. |

---

## Troubleshooting

### API Errors

| Problem | Cause | Fix |
|---------|-------|-----|
| `WHOISFREAKS_API_KEY is not set` | Missing env var | Add `-e WHOISFREAKS_API_KEY=your-key` to the Docker command, or add it to the `env` block in your client config |
| `Unauthorized (HTTP 401/403)` | Invalid or inactive API key | Verify the key at whoisfreaks.com/billing |
| `No data found (HTTP 404)` | Domain/IP/ASN not in database | Double-check the input value is correct |
| `Rate limit reached (HTTP 429)` | Too many requests | Slow down or upgrade your plan |
| `Credit limit exceeded (HTTP 413)` | Credits exhausted | Add credits at whoisfreaks.com/billing |
| `Timeout (HTTP 408)` | Upstream WHOIS server is slow | Retry — some TLDs have slow WHOIS servers |

### Docker Errors

| Problem | Cause | Fix |
|---------|-------|-----|
| `Cannot connect to Docker daemon` | Docker Desktop not running | Open Docker Desktop and wait for the engine to start |
| Container exits immediately | Missing API key or wrong entrypoint | Check logs: `docker logs whoisfreaks-mcp` |
| `port 3100 already in use` | Another process on port 3100 | Stop it: `docker rm -f whoisfreaks-mcp` then retry |
| `no such image` | Image not pulled yet | Run `docker pull whoisfreaks/mcp-server:latest` first |
| Claude Desktop: tools not appearing with Docker config | Docker not in PATH seen by the app | Use the full Docker path: `/usr/local/bin/docker` as the `command` |
| `lstat deploy: no such file or directory` (CI/CD) | Relative path issue in GitHub Actions | Use `${{ github.workspace }}/deploy/Dockerfile` for absolute paths |
| Gateway `connection refused` on port 3100 | Container not running or port not mapped | Run `docker ps` — ensure `-p 3100:3100` is in the run command |
| SSE connection drops after a few seconds | Nginx proxy timeout | Add `proxy_read_timeout 3600s;` to your Nginx location block |

### Client Errors

| Problem | Cause | Fix |
|---------|-------|-----|
| Tools not visible in client | Config not saved or client not restarted | Save config → fully quit and reopen the client |
| `UnsupportedClassVersionError` (JAR mode) | Java version too old | Upgrade to Java 17+ (`java -version` to check) |
| Wrong JAR path | Relative path in config | Use the full absolute path to the JAR |
| Cursor shows tools but doesn't call them | Agent mode not enabled | Switch Cursor to **Agent** mode, not Chat mode |
| Claude Desktop skips whoisfreaks entry | SSE URL used instead of stdio | Claude Desktop requires `command`/`args` — not `url`. Use the Docker stdio config shown above |
| Windsurf tools not called | Wrong Cascade mode | Switch Cascade to **Write** mode from the Cascade header |

---

## Links

- **WhoisFreaks API Documentation:** https://whoisfreaks.com/documentation
- **API Key & Billing:** https://whoisfreaks.com/billing
- **MCP Protocol Specification:** https://modelcontextprotocol.io
- **MCP Inspector (test tool):** `npx @modelcontextprotocol/inspector`
- **Issues & Support:** Open an issue on GitHub
