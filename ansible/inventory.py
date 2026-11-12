#!/usr/bin/env python3
"""Ansible inventory: the one MCP server (host "mcp", group mcp_server) from .env or the environment:

    MCP_SERVER_IP=1.2.3.4
    MCP_SERVER_USER=mcp      (optional: the user Ansible logs in as; "mcp" when empty)
"""
import json
import os
import re
import sys

ENV_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env")
HOST = "mcp"


def dotenv_value(name):
    """Read name from .env the way group_vars/all.yml does: optional 'export', quotes stripped, the
    last line wins, and an empty value falls back to the environment variable."""
    value = ""
    try:
        with open(ENV_FILE) as f:
            for line in f:
                m = re.match(r"^[ \t]*(?:export[ \t]+)?(\w+)[ \t]*=[ \t]*(.*?)[ \t\r]*$", line.rstrip("\n"))
                if m and m.group(1) == name:
                    value = re.sub(r"""^(['"])(.*)\1$""", r"\2", m.group(2))
    except FileNotFoundError:
        pass
    return value or os.environ.get(name, "")


def server():
    ip = dotenv_value("MCP_SERVER_IP").strip()
    if not ip:
        sys.exit(f"inventory.py: set MCP_SERVER_IP in {ENV_FILE}, e.g. MCP_SERVER_IP=1.2.3.4")
    return {"ansible_host": ip, "ansible_user": dotenv_value("MCP_SERVER_USER").strip() or "mcp"}


def main():
    hostvars = {HOST: server()}
    if len(sys.argv) > 2 and sys.argv[1] == "--host":
        print(json.dumps(hostvars.get(sys.argv[2], {})))
    else:
        print(json.dumps({"mcp_server": {"hosts": [HOST]}, "_meta": {"hostvars": hostvars}}))


if __name__ == "__main__":
    main()
