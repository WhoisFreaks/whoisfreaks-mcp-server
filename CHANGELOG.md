# Changelog

## [2.0.0] - 2026-11-13

### Added
- Hosted, multi-user HTTP mode (`--http` or `MCP_TRANSPORT=http`): a Streamable HTTP endpoint at the root and at `/mcp`, and a `/health` check, served by the Java server itself
- In HTTP mode every request carries the caller's own WhoisFreaks API key (`Authorization: Bearer`, `X-API-Key` or `?apiKey=`); the server stores none
- `ansible/` folder: sets up one machine (nginx, HTTPS, rate limiting) and deploys to it with automatic rollback

### Changed
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
