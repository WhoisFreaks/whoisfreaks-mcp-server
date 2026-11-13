# WhoisFreaks MCP Server

A **Model Context Protocol (MCP)** server that exposes the full **WhoisFreaks API** suite as AI-callable tools. Works with Claude Desktop, Cursor, Windsurf, VS Code, Continue, Zed, and any other MCP-compatible AI client.

**Three ways to use it — pick what fits your setup:**

| | Method | Requires | Best for |
|--|--------|----------|----------|
| ⭐ **Easiest** | [Hosted server](#hosted-server-no-install) | Just your API key | Everyone — nothing to install |
| | Docker (pre-built image) | Docker | Running it on your own machine or VM |
| | Build from source | Java 17 + Maven | Contributors / custom builds |

Connect it to any MCP client via **Streamable HTTP** (`/mcp` endpoint, hosted or self-hosted) or **stdio** (local).

Every user brings their **own WhoisFreaks API key** — the HTTP server stores no key of its own and uses the key sent with each request.

---

## Table of Contents

1. [Tools Reference (22 tools)](#tools-reference)
2. [Hosted Server (no install)](#hosted-server-no-install)
   - [OAuth Mode (ChatGPT and Codex)](#oauth-mode-chatgpt-and-codex)
3. [Quick Start](#quick-start)
   - [⭐ Recommended — Docker](#-recommended--docker)
   - [Build from Source](#build-from-source)
4. [Prerequisites](#prerequisites)
5. [Platform Integration](#platform-integration)
   - [1. Claude Desktop](#1-claude-desktop)
   - [2. Cursor IDE](#2-cursor-ide)
   - [3. Windsurf IDE](#3-windsurf-ide)
   - [4. VS Code + GitHub Copilot](#4-vs-code--github-copilot)
   - [5. Continue.dev](#5-continuedev)
   - [6. Zed Editor](#6-zed-editor)
6. [Example Prompts](#example-prompts)
7. [Troubleshooting](#troubleshooting)

---

## Tools Reference

### WHOIS Tools
| Tool | Description |
|------|-------------|
| `liveWhoisLookup` | Real-time WHOIS data for any domain — registrar, registrant, dates, nameservers |
| `bulkWhoisLookup` | Real-time WHOIS data for up to 100 domains in one request |
| `whoisHistory` | Complete ownership history timeline for a domain |
| `reverseWhoisLookup` | Find all domains registered by an email address, owner name or company, or whose name contains a keyword |

### IP & ASN WHOIS Tools
| Tool | Description |
|------|-------------|
| `ipWhoisLookup` | WHOIS registration data for any IPv4 or IPv6 address |
| `asnWhoisLookup` | WHOIS data for an Autonomous System Number (e.g. AS15169) |

### DNS Tools
| Tool | Description |
|------|-------------|
| `dnsLookup` | Live DNS records — A, AAAA, MX, NS, CNAME, SOA, TXT, SPF, or all — and PTR records for an IP |
| `bulkDnsLookup` | Live DNS records for up to 100 domains and 100 IPs in one request |
| `dnsHistory` | Historical DNS records with full change timeline |
| `reverseDnsLookup` | Find all domains pointing to a given IP or range, mail server or nameserver |

### IP Intelligence Tools
| Tool | Description |
|------|-------------|
| `ipGeolocation` | Country, city, region, ISP, and coordinates for any IP address |
| `bulkIpGeolocation` | Geolocation for up to 100 IP addresses in one request |
| `ipSecurity` | IP reputation: VPN, proxy, Tor exit node, relay, bot, spam and known-attacker signals |
| `bulkIpSecurity` | IP reputation for up to 100 IP addresses in one request |

### Domain Tools
| Tool | Description |
|------|-------------|
| `domainAvailability` | Check if a domain is available to register, with optional suggestions |
| `bulkDomainAvailability` | Check up to 100 domains, or one name across up to 100 extensions (TLDs) |
| `subdomainLookup` | Enumerate all known subdomains for a domain, with status and date filters |
| `domainDiscovery` | Find registered domains that are typo variants of a brand (typosquatting), by keyword or wildcard pattern |
| `subdomainTyposquatting` | Find subdomains on any domain that contain a typo variant of a brand, such as login.paypa1.example.com |
| `domainReputation` | Risk verdict, trust score, DGA analysis, threat-feed matches and email security for a domain |

### SSL Tools
| Tool | Description |
|------|-------------|
| `sslLookup` | SSL/TLS certificate details — issuer, expiry, SANs, chain, and raw output |


### Account Tools
| Tool | Description |
|------|-------------|
| `apiKeyUsage` | Credits and subscription requests used by your API key (costs nothing) |

The API's key rotation endpoint has no tool on purpose: it would replace the key the MCP client uses and hand the new one to the AI. Rotate keys in the [billing dashboard](https://billing.whoisfreaks.com/).

The tools call the endpoints of the WhoisFreaks OpenAPI spec and cost the same credits as calling the [API](https://whoisfreaks.com/documentation) directly; the [credit usage page](https://whoisfreaks.com/documentation/credit-usage) lists them. Bulk tools cost credits for each item.

---

## Hosted Server (no install)

Point any MCP client that supports remote servers at:

```
https://mcp.whoisfreaks.com
```

(`https://mcp.whoisfreaks.com/mcp` is the same endpoint, for clients or configs that expect that path)

and send your WhoisFreaks API key ([get one free](https://billing.whoisfreaks.com/signup)) in **one** of these ways:

| Method | Example | Use with |
|--------|---------|----------|
| `Authorization` header | `Authorization: Bearer YOUR_API_KEY` | Claude Code, Cursor, VS Code, Windsurf, Continue |
| `X-API-Key` header | `X-API-Key: YOUR_API_KEY` | Any client that supports custom headers |
| Query parameter | `https://mcp.whoisfreaks.com/?apiKey=YOUR_API_KEY` | Clients without custom headers (claude.ai / Claude Desktop custom connectors) |

> Prefer a header when your client supports one — URLs with keys can end up in logs and history.

**Claude Code**

```bash
claude mcp add --transport http whoisfreaks https://mcp.whoisfreaks.com \
  --header "Authorization: Bearer YOUR_API_KEY"
```

**claude.ai / Claude Desktop** — Settings → Connectors → **Add custom connector**, URL:

```
https://mcp.whoisfreaks.com/?apiKey=YOUR_API_KEY
```

**Cursor** (`~/.cursor/mcp.json`), **Windsurf**, and other JSON-configured clients:

```json
{
  "mcpServers": {
    "whoisfreaks": {
      "url": "https://mcp.whoisfreaks.com",
      "headers": { "Authorization": "Bearer YOUR_API_KEY" }
    }
  }
}
```

**VS Code** (`.vscode/mcp.json`):

```json
{
  "servers": {
    "whoisfreaks": {
      "type": "http",
      "url": "https://mcp.whoisfreaks.com",
      "headers": { "Authorization": "Bearer YOUR_API_KEY" }
    }
  }
}
```

Without a key the server still connects and lists its tools, but every tool call returns an error explaining how to add one. API usage is billed to the key you send.

---

## OAuth Mode (ChatGPT and Codex)

ChatGPT and Codex connect to MCP servers only with OAuth, so the WhoisFreaks plugin for them uses a second instance of this server in OAuth mode, on its own host name (`https://mcp-oauth.whoisfreaks.com`). The API-key endpoint above keeps working exactly as before and never answers with a 401.

Start it with `--oauth` (or `MCP_AUTH=oauth`; either one implies HTTP mode) and these variables:

| Variable | Example | Description |
|----------|---------|-------------|
| `OAUTH_ISSUER` | `https://auth.whoisfreaks.com` | The authorization server, which signs users in with their WhoisFreaks account |
| `MCP_RESOURCE` | `https://mcp-oauth.whoisfreaks.com` | This server's resource identifier: access tokens must name it as their audience |
| `BILLING_URL` | `https://billing-backend.whoisfreaks.com` | Where the server trades access tokens for API keys |
| `BILLING_SERVER_TOKEN` | — | The shared secret for that trade (billing's `oauth.mcp-server-token`) |
| `OAUTH_JWKS_URI` | `OAUTH_ISSUER/oauth2/jwks` (default) | The authorization server's signing keys |

How it handles a request:

1. **No access token, or an unusable one:** `401` with `WWW-Authenticate: Bearer resource_metadata="…", scope="lookups"`. Apps read `GET /.well-known/oauth-protected-resource` (plus `MCP_RESOURCE`'s path, if it has one) and sign the user in at the issuer. A token without the `lookups` scope gets `403`.
2. **A valid token** (RS256, signed with one of the issuer's keys, this resource as audience, not expired) is traded for the account's API key at billing. The answer is cached for up to a minute, never past the token's expiry. Tokens never contain the key, and the key is never logged.
3. **If billing no longer accepts the token** (the user disconnected, changed their password or signed out of all devices), the answer is `401` again, so the app asks them to connect. **If the account can't run lookups** (inactive, or no active key), tools return an error explaining why instead.

API keys sent as headers or `?apiKey=` are ignored in this mode. A key restricted to listed IPs only works here if this server's address is on its list.

```bash
docker run -d -p 3101:3100 \
  -e MCP_AUTH=oauth \
  -e OAUTH_ISSUER=https://auth.whoisfreaks.com \
  -e MCP_RESOURCE=https://mcp-oauth.whoisfreaks.com \
  -e BILLING_URL=https://billing-backend.whoisfreaks.com \
  -e BILLING_SERVER_TOKEN=... \
  whoisfreaks/mcp-server:latest
```

`deploy/docker-compose.yml` has the same as its `whoisfreaks-mcp-oauth` service. `ansible/` doesn't deploy OAuth mode yet.

---

## Quick Start

### ⭐ Recommended — Docker

No Java or Maven required. Pull the pre-built image directly from Docker Hub and run.

The container runs the same multi-user HTTP server as the hosted one: it needs **no API key of its own** — clients send theirs with each request (see [Hosted Server](#hosted-server-no-install) for the ways to send it). Get a free key at [billing.whoisfreaks.com/signup](https://billing.whoisfreaks.com/signup).

---

#### Option 1 — Direct `docker run` (simplest)

```bash
docker run -d \
  --name whoisfreaks-mcp \
  --restart unless-stopped \
  -p 3100:3100 \
  whoisfreaks/mcp-server:latest
```

The MCP server is now live at **`http://localhost:3100/mcp`**.

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
```

Start it:

```bash
docker compose up -d
```

---

#### Serving it publicly (HTTPS)

Put a reverse proxy with TLS in front of port 3100. Caddy example (`Caddyfile`), which obtains a certificate automatically:

```
mcp.example.com {
    reverse_proxy localhost:3100
}
```

Nginx example:

```nginx
location / {
    proxy_pass http://127.0.0.1:3100;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_buffering off;          # stream responses as they are produced
    proxy_read_timeout 300s;      # some WHOIS lookups are slow
}
```

The server is stateless (no MCP sessions), so you can run several containers behind a load balancer without sticky sessions.

To set up a machine and deploy to it without Docker (nginx, HTTPS, rate limiting, rollback), see [`ansible/`](ansible/README.md).

---

#### Verify Docker is working

```bash
# Container should show as "Up"
docker ps

# Health check
curl http://localhost:3100/health
# → {"status":"ok"}

# List all 22 registered tools (any key works for listing; tool calls need a valid one)
curl -s http://localhost:3100/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "Authorization: Bearer your-api-key-here" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

---

#### Use with Claude Desktop (Docker stdio mode)

To run the server locally over stdio instead of HTTP, override the Docker entrypoint and pass your key as an environment variable:

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
| WhoisFreaks API Key | — | Get yours at [billing.whoisfreaks.com/signup](https://billing.whoisfreaks.com/signup) |

**Clone and build:**

```bash
git clone https://github.com/whoisfreaks/whoisfreaks-mcp-server.git
cd whoisfreaks-mcp-server
mvn clean package -q
```

This produces a single self-contained fat JAR:

```
target/whoisfreaks-mcp-server-2.1.0.jar
```

> **Tip:** Note the **absolute path** to this JAR — you will need it in the platform configs below.
> Example: `/Users/yourname/whoisfreaks-mcp-server/target/whoisfreaks-mcp-server-2.1.0.jar`

---

## Prerequisites

| Requirement | Notes |
|-------------|-------|
| Docker | Required for the recommended Docker path |
| Java 17 + Maven 3.8 | Required only if building from source |
| WhoisFreaks API Key | Free at [billing.whoisfreaks.com/signup](https://billing.whoisfreaks.com/signup) |

---

## Platform Integration

### 1. Claude Desktop

The simplest setup is the [hosted server](#hosted-server-no-install) as a custom connector (Settings → Connectors → **Add custom connector**). To run it locally instead, Claude Desktop launches the MCP server as a subprocess via **stdio** using `claude_desktop_config.json`:

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
      "args": ["-jar", "/absolute/path/to/whoisfreaks-mcp-server-2.1.0.jar"],
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
5. Click the **hammer icon** (Tools) in the chat input — you should see 22 WhoisFreaks tools listed

**Verify it works:** Type `Who owns google.com?` and Claude will automatically call `liveWhoisLookup`.

---

### 2. Cursor IDE

Cursor supports both remote (HTTP) and stdio servers. Config file: `~/.cursor/mcp.json`

**⭐ Option A — HTTP (hosted, or your own container)**

```json
{
  "mcpServers": {
    "whoisfreaks": {
      "url": "https://mcp.whoisfreaks.com",
      "headers": { "Authorization": "Bearer your-api-key-here" }
    }
  }
}
```

> For your own container use `http://localhost:3100/mcp` (start it with `docker run -d -p 3100:3100 whoisfreaks/mcp-server:latest`).

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
      "args": ["-jar", "/absolute/path/to/whoisfreaks-mcp-server-2.1.0.jar"],
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
      "args": ["-jar", "/absolute/path/to/whoisfreaks-mcp-server-2.1.0.jar"],
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

**⭐ Option A — HTTP (hosted, or your own container)**

```json
{
  "servers": {
    "whoisfreaks": {
      "type": "http",
      "url": "https://mcp.whoisfreaks.com",
      "headers": { "Authorization": "Bearer your-api-key-here" }
    }
  }
}
```

> For your own container use `http://localhost:3100/mcp` (start it with `docker run -d -p 3100:3100 whoisfreaks/mcp-server:latest`).

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
      "args": ["-jar", "/absolute/path/to/whoisfreaks-mcp-server-2.1.0.jar"],
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

**⭐ Option A — HTTP (hosted, or your own container)**

```json
{
  "mcpServers": [
    {
      "name": "whoisfreaks",
      "url": "https://mcp.whoisfreaks.com/?apiKey=your-api-key-here",
      "transport": "streamable-http"
    }
  ]
}
```

> For your own container use `http://localhost:3100/mcp?apiKey=...` (start it with `docker run -d -p 3100:3100 whoisfreaks/mcp-server:latest`).

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
      "args": ["-jar", "/absolute/path/to/whoisfreaks-mcp-server-2.1.0.jar"],
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
        "args": ["-jar", "/absolute/path/to/whoisfreaks-mcp-server-2.1.0.jar"],
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
Which of mycompany .com, .io, .ai and .dev are still available? (bulk availability)
List all known subdomains of tesla.com.
Find typosquats of 'paypal' registered recently. (domain typosquatting)
Are there phishing subdomains that imitate 'paypal' on other domains? (subdomain typosquatting)
Is suspicious-login-portal.com safe? Give me a verdict. (domain reputation)

# Bulk
Get the WHOIS records of these 20 domains and list their registrars and expiry dates.
Which of these IPs from my logs are VPNs, proxies or Tor exit nodes?

# Account
How many credits do I have left on my WhoisFreaks key?

# SSL
Check the SSL certificate for github.com — who issued it and when does it expire?
```

---

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `WHOISFREAKS_API_KEY` | stdio mode only | Your API key from [whoisfreaks.com/billing](https://whoisfreaks.com/billing). Without it the server still starts and lists its tools, but every tool call returns a `WHOISFREAKS_API_KEY is not set` error. Not used in HTTP mode, where each request carries the caller's own key. |
| `MCP_TRANSPORT` | No | `http` to run the hosted HTTP server (same as the `--http` flag). The Docker image runs HTTP mode by default. |
| `MCP_AUTH` | No | `oauth` to run [OAuth mode](#oauth-mode-chatgpt-and-codex) (same as the `--oauth` flag), which then needs `OAUTH_ISSUER`, `MCP_RESOURCE`, `BILLING_URL` and `BILLING_SERVER_TOKEN`. |
| `PORT` | No | HTTP port. Default: `3100`. |

---

## Troubleshooting

### API Errors

| Problem | Cause | Fix |
|---------|-------|-----|
| `WHOISFREAKS_API_KEY is not set` (stdio) | Missing env var | Add `-e WHOISFREAKS_API_KEY=your-key` to the Docker command, or add it to the `env` block in your client config |
| `No WhoisFreaks API key was sent` (HTTP) | Client sent no key | Add `Authorization: Bearer your-key`, an `X-API-Key` header, or `?apiKey=your-key` to the URL, then reconnect |
| `Unauthorized (HTTP 401)` | Invalid or inactive API key, or the plan, credits or surcharge limit are used up | The message includes the API's own reason. Check the key and usage at whoisfreaks.com/billing, or ask the assistant to run `apiKeyUsage` |
| `Not supported (HTTP 403)` | WhoisFreaks does not support the domain's extension (TLD), or it is inactive | Try another domain |
| `No data found (HTTP 404)` | Domain/IP/ASN not in database | Double-check the input value is correct |
| `Rate limit reached (HTTP 429)` | Too many requests per minute | Wait for the number of seconds in the message, or upgrade your plan |
| `Too large (HTTP 413)` | A bulk list has more than 100 items | Split the list; the bulk tools also refuse more than 100 items before calling the API |
| `Timeout (HTTP 408)` | Upstream WHOIS server is slow | Retry — some TLDs have slow WHOIS servers |
| `did not answer within ... seconds` | The API took too long (60 s for single lookups, 240 s for bulk lookups and typosquatting searches) | Try again later |

### Docker Errors

| Problem | Cause | Fix |
|---------|-------|-----|
| `Cannot connect to Docker daemon` | Docker Desktop not running | Open Docker Desktop and wait for the engine to start |
| Container exits immediately | Port in use or wrong entrypoint | Check logs: `docker logs whoisfreaks-mcp` |
| `port 3100 already in use` | Another process on port 3100 | Stop it: `docker rm -f whoisfreaks-mcp` then retry |
| `no such image` | Image not pulled yet | Run `docker pull whoisfreaks/mcp-server:latest` first |
| Claude Desktop: tools not appearing with Docker config | Docker not in PATH seen by the app | Use the full Docker path: `/usr/local/bin/docker` as the `command` |
| `lstat deploy: no such file or directory` (CI/CD) | Relative path issue in GitHub Actions | Use `${{ github.workspace }}/deploy/Dockerfile` for absolute paths |
| Gateway `connection refused` on port 3100 | Container not running or port not mapped | Run `docker ps` — ensure `-p 3100:3100` is in the run command |
| `504` / timeouts behind Nginx | Nginx proxy timeout shorter than a slow lookup | Add `proxy_read_timeout 300s;` and `proxy_buffering off;` to your Nginx location block |

### Client Errors

| Problem | Cause | Fix |
|---------|-------|-----|
| Tools not visible in client | Config not saved or client not restarted | Save config → fully quit and reopen the client |
| `UnsupportedClassVersionError` (JAR mode) | Java version too old | Upgrade to Java 17+ (`java -version` to check) |
| Wrong JAR path | Relative path in config | Use the full absolute path to the JAR |
| Cursor shows tools but doesn't call them | Agent mode not enabled | Switch Cursor to **Agent** mode, not Chat mode |
| Claude Desktop skips whoisfreaks entry | `url` used in `claude_desktop_config.json` | That file needs `command`/`args` (stdio). For a URL, add a custom connector in Settings → Connectors instead |
| Client can't connect to `/sse` | Pre-2.0 SSE endpoint was removed | Use the `/mcp` URL (Streamable HTTP) |
| Windsurf tools not called | Wrong Cascade mode | Switch Cascade to **Write** mode from the Cascade header |

---

## Links

- **WhoisFreaks API Documentation:** https://whoisfreaks.com/documentation
- **API Key & Billing:** https://whoisfreaks.com/billing
- **MCP Protocol Specification:** https://modelcontextprotocol.io
- **MCP Inspector (test tool):** `npx @modelcontextprotocol/inspector`
- **Issues & Support:** Open an issue on GitHub
