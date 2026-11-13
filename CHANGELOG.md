# Changelog

## [Unreleased]

### Added
- `server.json` for the Official MCP Registry (`io.github.WhoisFreaks/whoisfreaks-mcp-server`), listing the hosted server and the Docker image
- The release workflow publishes every release to the registry, and can be run by hand to publish the current version without a release
- The Docker image carries the `io.modelcontextprotocol.server.name` label the registry checks
- Signing in with OAuth, next to API keys, at the same address, for the WhoisFreaks plugin in ChatGPT and Codex, which can only connect with OAuth. On when `OAUTH_ISSUER` is set (with `MCP_RESOURCE`, `BILLING_URL` and `BILLING_SERVER_TOKEN`). A bearer value that is a JWT is an access token from the WhoisFreaks authorization server: it is checked and traded for the account's API key at billing (cached for up to a minute), and an unusable one gets 401 with a challenge pointing to `/.well-known/oauth-protected-resource`. API keys, and requests without credentials, are handled as before: no 401
- `deploy/docker-compose.yml` passes the OAuth variables through, and tests (`mvn test`)
- `ansible/` writes the OAuth settings to a file only root can read when `OAUTH_ISSUER` is set in its `.env`, and removes it when it isn't; the service reads it at start

## [2.1.0] - 2026-11-13

Brought in line with the WhoisFreaks OpenAPI spec (version 2026-11-13.3).

### Added
- 8 tools, for 22 in all: `bulkWhoisLookup`, `bulkDnsLookup`, `bulkDomainAvailability`, `bulkIpGeolocation` and `bulkIpSecurity` (up to 100 items per request), `subdomainTyposquatting`, `domainReputation`, and `apiKeyUsage`
- `dnsLookup` takes `ipAddress` for reverse (PTR) lookups, `cnameDepth` and `relativeRecords`, and a comma-separated list of record types
- `reverseDnsLookup` takes `sort` and `sortBy`, and accepts CIDR ranges for A and AAAA
- Results marked partial (HTTP 206) or cached (HTTP 210) by the API say so
- A rate-limit error (HTTP 429) says how many seconds to wait, from the API's `X-RateLimit-Remaining-Time` header

### Changed
- WHOIS, history and reverse WHOIS use `/v2.0/whois/live`, `/v2.0/whois/history` and `/v2.0/whois/reverse`; IP reputation uses `/v2.0/security`; domain availability uses `/v2.0/domain/availability`
- `domainDiscovery` uses the typosquatting search `/v3.0/domain/typos`: it finds registered typo variants of a brand `keyword`, or domains matching a `pattern`, and pages with `pageToken`/`fetchSize` (the old `page` and `labelLength` arguments are gone). To find domains whose name contains a word, use `reverseWhoisLookup` with `keyword`
- Error messages follow the spec and include the API's own reason: credit or plan limits are HTTP 401, an unsupported TLD is HTTP 403, and HTTP 413 means a bulk list is too long
- Corrected tool descriptions: `reverseWhoisLookup` defaults to full records (`mode=default`) and `exact=false`; `reverseDnsLookup` defaults to `exact=false`; suggestion counts are 5 to 100; `sslLookup` describes the fields the API returns. Paged and costly tools state their credit cost
- Bulk lookups and typosquatting searches wait up to 240 seconds for the API; the Ansible nginx template allows 300 seconds (`proxy_read_timeout`)

### Not included
- The API's key rotation endpoint: it would replace the key the MCP client uses and hand the new key to the AI. Rotate keys in the billing dashboard

## [2.0.0] - 2026-11-13

### Added
- Hosted, multi-user HTTP mode (`--http` or `MCP_TRANSPORT=http`): a Streamable HTTP endpoint at the root and at `/mcp`, and a `/health` check, served by the Java server itself
- In HTTP mode every request carries the caller's own WhoisFreaks API key (`Authorization: Bearer`, `X-API-Key` or `?apiKey=`); the server stores none
- `ansible/` folder: sets up one machine (nginx, HTTPS, rate limiting) and deploys to it with automatic rollback

### Changed
- Every request to the WhoisFreaks API carries `app=mcp-server`
- A 401 from the API now includes the API's own reason (invalid key, IP address not whitelisted)
- The Docker image runs HTTP mode by default and no longer includes Node or `mcp-proxy`
- stdio mode is unchanged: it still reads `WHOISFREAKS_API_KEY`

### Removed
- The `/sse` endpoint: connect to `/mcp` instead

## [1.0.3] - 2026-11-11

### Changed
- Rewrote all 14 tool descriptions: each says when to use it versus similar tools, what it returns, and no longer repeats the parameter list from the input schema
- Every tool description ends with a shared note on API key use, credits and error messages
- Tools now carry MCP titles and annotations (read-only, non-destructive, idempotent, calls an external API)

## [1.0.2] - 2026-11-11

### Fixed
- Fixed Docker build: Dockerfile no longer hard-codes the jar version
- docker-compose.yml now uses the `latest` image instead of pinning 1.0.0
- README jar paths updated to the current version
- Server now reports its real version (was 2.0.0); it is read from the jar manifest so it always matches pom.xml
- glama.json version updated to 1.0.2

### Changed
- Server now starts without `WHOISFREAKS_API_KEY`, so MCP clients and registries such as Glama can list its 14 tools; each tool call returns a clear "WHOISFREAKS_API_KEY is not set" error until the key is set (previously the server exited at startup)

## [1.0.1] - 2026-09-23

### Fixed
- Added MIT license for GitHub and Glama registry visibility
- Updated MCP server metadata
- Improved documentation

### Changed
- Enhanced README with troubleshooting section

### Added
- Full support for Docker Compose configurations
