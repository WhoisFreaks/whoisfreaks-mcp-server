# Changelog

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
