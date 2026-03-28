# WhoisFreaks MCP Server

A pure-Java **Model Context Protocol (MCP)** server that exposes the full **WhoisFreaks API** suite as AI-callable tools. Works with Claude Desktop, Cursor, Windsurf, VS Code, Continue, Zed, and any other MCP-compatible AI client.

Run locally via stdio (your AI client launches it as a subprocess) or deploy on a VM using the bundled **mcp-proxy HTTP gateway** — exposing it as an HTTP/SSE endpoint on port 3100 so any client can connect remotely without needing Java installed.

---

## Table of Contents

1. [Tools Reference (14 tools)](#tools-reference)
2. [Prerequisites](#prerequisites)
3. [Build](#build)
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

## Prerequisites

| Requirement | Minimum Version | Notes |
|-------------|-----------------|-------|
| Java (JDK) | 17 | Check with `java -version` |
| Maven | 3.8 | Check with `mvn -version` |
| WhoisFreaks API Key | — | Get yours at [whoisfreaks.com/billing](https://whoisfreaks.com/billing) |

> **For VM deployment only:** Docker and Docker Compose are required on the server. No Java or Maven needed on the VM — the Docker image bundles everything.

---

## Build

```bash
git clone https://github.com/whoisfreaks/whoisfreaks-mcp-server.git
cd whoisfreaks-mcp-server
mvn clean package -q
```

This produces a single self-contained fat JAR — no other files needed to run it:

```
target/whoisfreaks-mcp-server-1.0.0.jar
```

> **Tip:** Note the **absolute path** to this JAR. You will need it in every platform configuration below.
> Example: `/Users/yourname/whoisfreaks-mcp-server/target/whoisfreaks-mcp-server-1.0.0.jar`

---

## Platform Integration

### 1. Claude Desktop

The most popular MCP client. Claude Desktop launches the MCP server automatically on startup.

**Config file location:**

| OS | Path |
|----|------|
| macOS | `~/Library/Application Support/Claude/claude_desktop_config.json` |
| Windows | `%APPDATA%\Claude\claude_desktop_config.json` |
| Linux | `~/.config/Claude/claude_desktop_config.json` |

**Edit the config file** (create it if it doesn't exist):

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
1. Paste the config above with your real JAR path and API key
2. Save the file
3. Quit Claude Desktop completely (`Cmd+Q` on macOS)
4. Reopen Claude Desktop
5. Click the **hammer icon** (Tools) in the chat input — you should see 14 WhoisFreaks tools listed

**Verify it works:** Type `Who owns google.com?` and Claude will automatically call `liveWhoisLookup`.

> **Using the VM gateway instead?** Replace the `command`/`args`/`env` block with just `"url": "http://your-vm-ip:3100/sse"` — see [Connect Clients to the Gateway](#connect-clients-to-the-gateway).

---

### 2. Cursor IDE

Cursor has native MCP support. Add it through Settings or directly via the config file.

**Via Settings UI:**
1. Open Cursor → `Settings` → `Cursor Settings` → `MCP`
2. Click **Add new MCP server**
3. Fill in:
   - **Name:** `whoisfreaks`
   - **Type:** `command`
   - **Command:** `java`
   - **Args:** `-jar /absolute/path/to/whoisfreaks-mcp-server-1.0.0.jar`
   - **Env:** `WHOISFREAKS_API_KEY=your-api-key-here`
4. Click **Save**

**Via config file** (`~/.cursor/mcp.json`):

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
1. Save the config file
2. Restart Cursor
3. Open the **AI Panel** (`Cmd+L`) → you should see a tools indicator showing WhoisFreaks tools
4. Ask in the chat: `Check the SSL certificate for github.com`

> **Using the VM gateway instead?** Add `"url": "http://your-vm-ip:3100/sse"` and `"transport": "sse"` in place of `command`/`args`/`env`.

---

### 3. Windsurf IDE

Windsurf (by Codeium) supports MCP via its Cascade AI sidebar.

**Config file location:**

| OS | Path |
|----|------|
| macOS | `~/.codeium/windsurf/mcp_config.json` |
| Windows | `%USERPROFILE%\.codeium\windsurf\mcp_config.json` |
| Linux | `~/.codeium/windsurf/mcp_config.json` |

**Edit the config file:**

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
1. Save the config
2. Restart Windsurf
3. Open Cascade sidebar → look for the MCP tools indicator (plug icon)
4. Ask: `Find all subdomains of tesla.com`

> **Note:** Windsurf requires Cascade to be in **Write** mode for tool use. Toggle it from the Cascade header if tools are not being called.

---

### 4. VS Code + GitHub Copilot

VS Code supports MCP tools through the GitHub Copilot extension (Chat Participants / Tools feature).

**Config file** (`.vscode/mcp.json` in your workspace, or `~/.vscode/mcp.json` globally):

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
2. Save the config file above
3. Open Copilot Chat (`Ctrl+Shift+I` / `Cmd+Shift+I`)
4. Switch the model to **Agent mode** using the dropdown
5. Click the **Tools** button — WhoisFreaks tools should appear in the list
6. Ask: `What are the MX records for github.com?`

> **Requirement:** VS Code 1.99+ and GitHub Copilot Chat extension are required for MCP tool support.

---

### 5. Continue.dev

Continue is an open-source AI coding assistant with MCP support for VS Code and JetBrains IDEs.

**Config file** (`~/.continue/config.json`):

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
2. Edit `~/.continue/config.json` with the snippet above
3. Reload the window (`Cmd+Shift+P` → `Reload Window`)
4. Open Continue sidebar — tools from WhoisFreaks will be available in chat
5. Ask: `Look up IP geolocation for 1.1.1.1`

---

### 6. Zed Editor

Zed has a built-in AI assistant with MCP support via its `assistant` configuration.

**Config file** (`~/.config/zed/settings.json`):

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
2. Add the `mcp_servers` block to your existing settings JSON
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

| Problem | Cause | Fix |
|---------|-------|-----|
| `WHOISFREAKS_API_KEY is not set` | Missing env var | Export `WHOISFREAKS_API_KEY` before running, or add it to the `env` block in your client config |
| `Unauthorized (HTTP 401/403)` | Invalid or inactive API key | Verify the key at whoisfreaks.com/billing |
| `No data found (HTTP 404)` | Domain/IP/ASN not in database | Double-check the input value is correct |
| `Rate limit reached (HTTP 429)` | Too many requests | Slow down or upgrade your plan |
| `Credit limit exceeded (HTTP 413)` | Credits exhausted | Add credits at whoisfreaks.com/billing |
| `Timeout (HTTP 408)` | Upstream WHOIS server is slow | Retry — some TLDs have slow WHOIS servers |
| Server starts but no tools visible in client | Wrong JAR path in config | Use the full absolute path to the JAR, not a relative path |
| `UnsupportedClassVersionError` | Java version too old | Upgrade to Java 17+ (`java -version` to check) |
| Tools listed but calls fail silently | Config not saved / client not restarted | Save config → fully quit and reopen the client |
| Cursor shows tools but doesn't call them | Agent mode not enabled | Make sure Cursor is using **Agent** mode, not Chat mode |
| Gateway returns `connection refused` on port 3100 | Container not running or port not open | Run `docker ps` to check the container, and open port 3100 in your VM firewall |
| SSE connection drops after a few seconds | Nginx proxy timeout | Add `proxy_read_timeout 3600s;` to your Nginx location block |

---

## Links

- **WhoisFreaks API Documentation:** https://whoisfreaks.com/documentation
- **API Key & Billing:** https://whoisfreaks.com/billing
- **MCP Protocol Specification:** https://modelcontextprotocol.io
- **MCP Inspector (test tool):** `npx @modelcontextprotocol/inspector`
- **Issues & Support:** Open an issue on GitHub
