"""
SBR VPN WireGuard VPS Node Agent
Runs on each Ubuntu/Debian VPS hosting WireGuard (`wg0`).
Accepts only HMAC-SHA256 authenticated requests from the central FastAPI control plane.
"""
import hashlib
import hmac
import os
import subprocess
import time
from fastapi import FastAPI, Header, HTTPException, Request
from pydantic import BaseModel
import psutil

AGENT_SECRET = os.environ.get("SBR_NODE_AGENT_SECRET", "")
WG_INTERFACE = os.environ.get("SBR_WG_INTERFACE", "wg0")
BOOT_TIME = psutil.boot_time()

app = FastAPI(title="SBR VPN Node Agent")


def verify_signature(body: bytes, signature: str | None) -> None:
    if not AGENT_SECRET:
        raise HTTPException(status_code=500, detail="Node agent secret not configured")
    if not signature:
        raise HTTPException(status_code=401, detail="Missing signature")
    expected = hmac.new(AGENT_SECRET.encode("utf-8"), body, hashlib.sha256).hexdigest()
    if not hmac.compare_digest(expected, signature):
        raise HTTPException(status_code=403, detail="Invalid HMAC signature")


class PeerAddReq(BaseModel):
    client_public_key: str
    allowed_ip: str


class PeerRemoveReq(BaseModel):
    client_public_key: str


@app.post("/peers/add")
async def add_peer(request: Request, x_sbr_signature: str | None = Header(default=None)):
    raw = await request.body()
    verify_signature(raw, x_sbr_signature)
    body = PeerAddReq.model_validate_json(raw)
    subprocess.run(
        ["wg", "set", WG_INTERFACE, "peer", body.client_public_key, "allowed-ips", body.allowed_ip],
        check=True,
    )
    return {"status": "peer_added"}


@app.post("/peers/remove")
async def remove_peer(request: Request, x_sbr_signature: str | None = Header(default=None)):
    raw = await request.body()
    verify_signature(raw, x_sbr_signature)
    body = PeerRemoveReq.model_validate_json(raw)
    subprocess.run(
        ["wg", "set", WG_INTERFACE, "peer", body.client_public_key, "remove"],
        check=True,
    )
    return {"status": "peer_removed"}


@app.get("/health")
async def health(x_sbr_signature: str | None = Header(default=None)):
    verify_signature(b"{}", x_sbr_signature)
    transfer = subprocess.check_output(["wg", "show", WG_INTERFACE, "transfer"], text=True)
    peers = [line for line in transfer.strip().splitlines() if line.strip()]
    total_bytes = 0
    for line in peers:
        parts = line.split("\t")
        if len(parts) == 3:
            total_bytes += int(parts[1]) + int(parts[2])

    return {
        "cpu_percent": int(psutil.cpu_percent(interval=0.2)),
        "ram_percent": int(psutil.virtual_memory().percent),
        "uptime_seconds": int(time.time() - BOOT_TIME),
        "active_peers": len(peers),
        "total_bandwidth_bytes": total_bytes,
    }
