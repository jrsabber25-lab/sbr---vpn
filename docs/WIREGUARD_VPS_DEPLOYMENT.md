# SBR VPN — WireGuard VPS & Backend Deployment Guide

## 1. Prerequisites
- Central Control Plane VPS (Ubuntu 24.04 LTS) with PostgreSQL 16 + HTTPS domain (`api.yourdomain.com`).
- One or more regional VPN Exit VPS nodes (Ubuntu 24.04 LTS) in your desired regions (`US`, `GB`, `DE`, `NL`, `FR`, `SG`, `JP`, `CA`, `IN`).

## 2. Regional WireGuard Exit Node Setup (Repeat per VPS)

### A. Install WireGuard, Unbound DNS (Leak Protection), and Firewall
```bash
sudo apt update && sudo apt install -y wireguard unbound nftables python3-venv python3-pip
```

### B. Generate Server Curve25519 Keys
```bash
umask 077
wg genkey | tee /etc/wireguard/server_private.key | wg pubkey > /etc/wireguard/server_public.key
```
*(Keep `server_private.key` strictly on the VPS — only copy `server_public.key` into the Admin Dashboard when registering the node.)*

### C. Enable IPv4 & IPv6 Kernel Forwarding
Create `/etc/sysctl.d/99-sbrvpn.conf`:
```ini
net.ipv4.ip_forward = 1
net.ipv6.conf.all.forwarding = 1
```
Apply immediately: `sudo sysctl --system`

### D. Configure `/etc/wireguard/wg0.conf`
Replace `eth0` with your VPS public NIC (`ip route show default`):
```ini
[Interface]
Address = 10.8.0.1/24, fd00:8::1/64
ListenPort = 51820
PrivateKey = <CONTENTS_OF_SERVER_PRIVATE_KEY>
PostUp = nft add table inet sbrnat; nft 'add chain inet sbrnat postrouting { type nat hook postrouting priority 100 ; }'; nft add rule inet sbrnat postrouting oifname "eth0" masquerade
PostDown = nft delete table inet sbrnat
```
Enable & start WireGuard:
```bash
sudo systemctl enable --now wg-quick@wg0
```

### E. Configure Local DNS Resolver (`/etc/unbound/unbound.conf.d/sbrvpn.conf`)
Binding Unbound to `10.8.0.1` prevents third-party DNS leaks:
```yaml
server:
  interface: 10.8.0.1
  access-control: 10.8.0.0/24 allow
  hide-identity: yes
  hide-version: yes
  qname-minimisation: yes
```
Restart Unbound: `sudo systemctl restart unbound`

### F. Run the SBR VPN Node Agent Over HTTPS (Caddy Reverse Proxy)
```bash
sudo mkdir -p /opt/sbr-node-agent
sudo cp backend/node_agent/agent.py /opt/sbr-node-agent/
python3 -m venv /opt/sbr-node-agent/venv
/opt/sbr-node-agent/venv/bin/pip install fastapi uvicorn psutil pydantic
```
Configure systemd service `/etc/systemd/system/sbr-node-agent.service` with `Environment="SBR_NODE_AGENT_SECRET=..."` and bind to `127.0.0.1:8443`, then expose via Caddy with TLS (`https://node-de1.yourdomain.com`).

## 3. Deploy the Central FastAPI Backend
1. Copy `backend/` to your control plane server.
2. Copy `backend/.env.example` to `backend/.env` and generate strong secrets (`openssl rand -hex 32`).
3. Run migrations / startup:
   ```bash
   uvicorn app.main:app --host 127.0.0.1 --port 8000
   ```
4. Terminate TLS using Caddy/Nginx at `https://api.yourdomain.com`.
5. Open `admin-dashboard/index.html`, log in as the bootstrap admin, and add each deployed WireGuard VPS node!
