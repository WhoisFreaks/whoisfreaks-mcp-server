# WhoisFreaks MCP server deployment

Sets up one machine as the hosted MCP server (`https://<MCP_HOSTNAME>`, also at `/mcp`) and deploys new versions to it. With `MCP_OAUTH_HOSTNAME` set, the machine also runs the server in OAuth mode for the WhoisFreaks plugin in ChatGPT and Codex (`https://<MCP_OAUTH_HOSTNAME>`, section 9).

| Playbook | Does |
|---|---|
| `setup.yml` | turns a new Rocky Linux 9 machine into the server and installs the current version |
| `deploy.yml` | deploys a new version; puts the previous one back if the new one doesn't start |
| `status.yml` | shows whether the server is up, which commit runs and whether git works; changes nothing |

Run everything from this folder, on a machine whose SSH key logs in to the server:

```bash
cd whoisfreaks-mcp-server/ansible
```

The machine runs the server's JAR as the systemd service `whoisfreaks-mcp` on port 3100, behind nginx with a Let's Encrypt certificate for `MCP_HOSTNAME`. The server keeps no state and no API key of its own: every user sends their own WhoisFreaks key with each request. In OAuth mode the same JAR also runs as `whoisfreaks-mcp-oauth` on port 3101, behind its own site and certificate.

## 1. Before the first run (once per machine you run Ansible from)

1. Install Ansible (ansible-core 2.15 or newer; on a Mac `brew install ansible`) and the collection the playbooks use:
   ```bash
   ansible-galaxy collection install -r requirements.yml
   ```
2. Create `.env` and fill it in (section 2):
   ```bash
   cp .env.example .env
   ```

## 2. Settings (`.env`)

Every secret, and everything that names our server (its IP and hostname), lives only in `.env`. Git ignores it; `.env.example` lists every setting with a comment. One `NAME=value` per line, no comments after values, quotes optional. An environment variable with the same name is used when `.env` leaves it empty. A playbook stops before doing anything if a setting it needs is missing, and names it.

| Name | What | Needed |
|---|---|---|
| `MCP_SERVER_IP` | The machine's public IPv4 address | always |
| `MCP_HOSTNAME` | The address users connect to, e.g. `mcp.example.com`. It must point at `MCP_SERVER_IP` | always |
| `MCP_SERVER_USER` | The user Ansible logs in as; `setup.yml` creates it. Empty: `mcp` | optional |
| `CF_DNS_API_TOKEN` | Secret. Cloudflare token with "Zone: DNS - Edit" and "Zone: Zone - Read". With it, `setup.yml` creates the DNS record. Empty: `setup.yml` doesn't touch DNS, and the record must exist before you run it | optional |
| `CF_ZONE` | The Cloudflare zone of `MCP_HOSTNAME` (and of `MCP_OAUTH_HOSTNAME`, which must be in the same zone): its name (`example.com`) or its Zone ID. Empty: the hostname's last two labels (`mcp.example.com` → `example.com`) | optional |
| `GIT_USERNAME`, `GIT_TOKEN` | Secret. The git user and access token the server pulls with. Leave both empty while the repository is public | optional |
| `NGINX_DENIED_IPS` | IPs nginx refuses, comma-separated | optional |
| `MCP_OAUTH_HOSTNAME` | OAuth mode's address, e.g. `mcp-oauth.example.com`, pointing at `MCP_SERVER_IP` like `MCP_HOSTNAME`; also its OAuth resource (`https://<MCP_OAUTH_HOSTNAME>`). Empty: no OAuth mode (section 9) | optional |
| `OAUTH_ISSUER` | The authorization server that signs users in, e.g. `https://auth.example.com` | with `MCP_OAUTH_HOSTNAME` |
| `BILLING_URL` | Billing's backend, where OAuth mode trades access tokens for API keys, e.g. `https://billing-backend.example.com` | with `MCP_OAUTH_HOSTNAME` |
| `BILLING_SERVER_TOKEN` | Secret. Shared with billing for that trade (billing's `oauth.mcp-server-token`) | with `MCP_OAUTH_HOSTNAME` |

`inventory.py` builds the inventory (one host, `mcp`) from `MCP_SERVER_IP` and `MCP_SERVER_USER`. On the server, git credentials (when set) sit in a temporary file only the login user can read, removed after each git command. No playbook prints a secret.

Everything else (branch, paths, Java heap, rate limit) is in `group_vars/all.yml`.

## 3. Set up the machine

1. Buy a Rocky Linux 9 machine; when creating it, give root your SSH public key (`~/.ssh/id_rsa.pub`).
2. Put its IP in `MCP_SERVER_IP` and the hostname in `MCP_HOSTNAME`.
3. Point `MCP_HOSTNAME` at the IP with an A record, or put `CF_DNS_API_TOKEN` in `.env` and `setup.yml` creates the record in Cloudflare (DNS only, not proxied).
4. Push the version you want to `master`: the server builds from the git repository.
5. Run:
   ```bash
   ansible-playbook setup.yml
   ```
6. Check with `ansible-playbook status.yml`.

A failed run can simply be run again: finished steps are left as they are.

**Logins.** The first run logs in as root, because a new machine has no other user. Every later run of any playbook logs in as `MCP_SERVER_USER`. When you run `setup.yml` again, its first step still tries root, shows `UNREACHABLE` for it and moves on: that line is expected and means root is blocked.

What it does, in order (tags in brackets, for `--tags` / `--skip-tags`):

| Step | Does |
|---|---|
| Checks (always) | Settings present, an SSH public key on this machine |
| `bootstrap` | Logs in as root once: creates the login user, `MCP_SERVER_USER` (wheel, passwordless sudo, your SSH key). Then logs in as that user and blocks SSH logins as root (`PermitRootLogin no`, checked with `sshd -T`). Root is blocked only after the new login and its sudo work, so a failed run can't lock you out |
| `system` | Installs EPEL, nginx (nginx.org), certbot, Java 17, git and the usual tools. Lets nginx reach the server under SELinux. Firewall: HTTP, HTTPS and SSH open; port 3100 stays closed, so visitors reach the server only through nginx |
| `app` | Maven 3.9.6 from archive.apache.org (checksum verified). The `whoisfreaks-mcp` user, `/opt/whoisfreaks-mcp` and the `whoisfreaks-mcp` service (256 MB to 1 GB heap, `app_java_opts`), enabled at boot. In OAuth mode also the `whoisfreaks-mcp-oauth` service and its settings file (section 9) |
| `code` | Clones the repository into `~/code/whoisfreaks-mcp-server` |
| `dns` | With `CF_DNS_API_TOKEN`: creates the DNS record (and `MCP_OAUTH_HOSTNAME`'s) and waits until it resolves. Without: checks that the hostnames resolve |
| `nginx` | nginx with a site per hostname (section 6), a Let's Encrypt certificate for each, automatic renewal |
| `deploy` | Builds and installs the server and starts it (section 4, steps 2–5) |

`setup.yml --check` shows the system and nginx changes it would make; it doesn't build or install.

## 4. Deploy a new version

`deploy.yml` deploys what is on `master` of the git repository, so push there first. Then:

```bash
ansible-playbook deploy.yml
```

1. Checks the settings, that the machine is set up and that it can read the repository. It stops here, changing nothing, if one of those fails.
2. Pulls `master` (fast-forward only) and builds the JAR with Maven. The running version keeps serving meanwhile, and stays running if any of this fails.
3. Keeps the running JAR as `/opt/whoisfreaks-mcp/app.previous.jar`, if it answers `/health`.
4. Installs the new JAR and restarts the service (both services in OAuth mode: they run the same JAR). **The server is down for the few seconds the restart takes**; there is no second machine to carry the traffic.
5. Waits up to 2 minutes for `/health` to answer 200, on each hostname. If one doesn't, puts the previous JAR back, restarts both and says which hostname is still down, if any.
6. Checks `https://<MCP_HOSTNAME>/health` (and `https://<MCP_OAUTH_HOSTNAME>/health`) from this machine, the way users reach it.

It ends with `Deployed. https://<MCP_HOSTNAME>/health answers 200.`

## 5. If something fails

| What failed | State | What to do |
|---|---|---|
| A setting is missing, the machine isn't set up, or git refuses it | Nothing changed | The message names it; fix it and run again |
| The machine's branch has diverged from the git server | Nothing built; the running version still runs | Fix the checkout on the server (`git status` in `~/code/whoisfreaks-mcp-server`), run it again |
| The build | Nothing installed; the running version still runs | The message shows Maven's error; fix it, push, run `deploy.yml` again |
| The build makes no JAR, or more than one | The same | Check the build output and `build_jar_pattern` in `group_vars/all.yml` |
| The new version doesn't answer `/health` | The previous version is back and the server is up | Read `journalctl -u whoisfreaks-mcp` (and `-u whoisfreaks-mcp-oauth`) on the server for why, fix it, run `deploy.yml` again |
| The previous version is back, but OAuth mode's hostname still doesn't answer | The API-key server is up; OAuth mode is down | Usually its settings, not the JAR: `journalctl -u whoisfreaks-mcp-oauth` names the missing or wrong one. Fix it in `.env`, run `setup.yml --tags app` |
| The new version doesn't answer and there was none before (a first install) | The server is not running | Read `journalctl -u whoisfreaks-mcp`, fix it, run again |
| The new version runs but `https://<MCP_HOSTNAME>/health` doesn't answer from here | The new version runs on the machine | Check DNS for `MCP_HOSTNAME`, the certificate and the firewall |

To go back to the previous version by hand, on the server:

```bash
sudo cp /opt/whoisfreaks-mcp/app.previous.jar /opt/whoisfreaks-mcp/app.jar && sudo systemctl restart whoisfreaks-mcp
```

The next `deploy.yml` installs `master` again, so revert the commit there too.

## 6. nginx on the machine

`/etc/nginx/conf.d/whoisfreaks-mcp.conf` serves `MCP_HOSTNAME` from the server on port 3100. In OAuth mode, `/etc/nginx/conf.d/whoisfreaks-mcp-oauth.conf` serves `MCP_OAUTH_HOSTNAME` from port 3101 the same way, with the same logging and rate limit.

- **API keys stay out of the logs.** A client that can't send headers puts its key in the URL (`?apiKey=…`). The access log (`/var/log/nginx/mcp-access.log`) records the request without its query string, and the site's error log (`mcp-error.log`) records only critical problems, because nginx's error lines quote the full request.
- **Rate limit.** 10 requests a second per visitor IP, with a burst of 40, then 429 (`nginx_rate_limit`, `nginx_rate_burst` in `group_vars/all.yml`). The server itself has no limit, and connecting or listing tools needs no key.
- **Behind Cloudflare's proxy, if you turn it on.** `setup.yml` creates a DNS-only record. If you later proxy `MCP_HOSTNAME` through Cloudflare, nginx already takes the visitor's IP from `CF-Connecting-IP` for Cloudflare's ranges (read from its API when `setup.yml` ran), so the log and the rate limit stay per visitor.

## 7. The `/health` check

`/health` on the server answers 200 `{"status":"ok"}` once it runs. It needs no API key and calls nothing else: the server has no data sources of its own, and whether the WhoisFreaks API answers is not this machine's health. Deploys and `status.yml` use it.

## 8. Still open (see also section 9)

- `setup.yml` ran end to end against a Rocky Linux 9 container (root login, the login user, root blocked, packages, Maven, the service, a real build and start), and its nginx config was tested with the real server behind it. Three steps could not run there and get their first test on the real machine: the firewall (firewalld doesn't run in a container), the DNS record and the Let's Encrypt certificate.
- One machine means a restart is a few seconds of downtime, and the machine is a single point of failure. The Whois API's `ansible/` folder (rolling deploys behind a Cloudflare load balancer) is the model if this ever needs a second machine.
- No monitoring agents or cron jobs are installed.

## 9. OAuth mode

ChatGPT and Codex connect to MCP servers only with OAuth, so the WhoisFreaks plugin for them needs the server in OAuth mode (the main README, "OAuth Mode"). It runs on the same machine from the same JAR, as a second service on its own hostname, so the API-key server at `MCP_HOSTNAME` never answers with a 401.

**Turn it on.** Set `MCP_OAUTH_HOSTNAME`, `OAUTH_ISSUER`, `BILLING_URL` and `BILLING_SERVER_TOKEN` in `.env` (section 2), then run `ansible-playbook setup.yml`. Besides what it did before, it:

- writes the settings to `/etc/whoisfreaks-mcp/oauth.env`, which only root can read (it holds `BILLING_SERVER_TOKEN`; Ansible never prints it), with `MCP_RESOURCE=https://<MCP_OAUTH_HOSTNAME>`
- installs `whoisfreaks-mcp-oauth` (port 3101, `--oauth`), which systemd starts with that file
- creates the DNS record for `MCP_OAUTH_HOSTNAME` (with `CF_DNS_API_TOKEN`), and an nginx site and Let's Encrypt certificate for it

From then on, `deploy.yml` restarts, checks and rolls back both services together, and `status.yml` shows both.

**Change a setting** (a new `BILLING_SERVER_TOKEN`, say): change it in `.env` and run `ansible-playbook setup.yml --tags app`. The settings file is rewritten and `whoisfreaks-mcp-oauth` restarts; the API-key server isn't touched.

**Turn it off.** Emptying `MCP_OAUTH_HOSTNAME` makes the playbooks leave OAuth mode alone; they don't remove it. On the server: `sudo systemctl disable --now whoisfreaks-mcp-oauth`, then remove `/etc/systemd/system/whoisfreaks-mcp-oauth.service`, `/etc/whoisfreaks-mcp/oauth.env` and `/etc/nginx/conf.d/whoisfreaks-mcp-oauth.conf`, and reload nginx. Remove its DNS record yourself.

## Files

| Path | What |
|---|---|
| `setup.yml`, `deploy.yml`, `status.yml` | the playbooks |
| `inventory.py` | the server, from `MCP_SERVER_IP` in `.env` |
| `group_vars/all.yml` | every other setting, read from `.env` where it names our server |
| `tasks/deploy_node.yml` | builds, installs, restarts and rolls back |
| `tasks/git.yml` | check, pull and clone |
| `tasks/setup_*.yml` | the parts of `setup.yml` |
| `templates/`, `files/` | the config files `setup.yml` writes on the server (`whoisfreaks-mcp-oauth.service.j2` and `oauth.env.j2` for OAuth mode) |
| `.env.example` | the names `.env` needs |
