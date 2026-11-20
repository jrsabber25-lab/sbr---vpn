import json
import httpx
from .security import sign_node_agent_payload


async def add_wireguard_peer(
    agent_url: str,
    client_public_key: str,
    allowed_ip: str,
) -> None:
    payload = json.dumps(
        {"client_public_key": client_public_key, "allowed_ip": allowed_ip}
    ).encode("utf-8")
    sig = sign_node_agent_payload(payload)
    async with httpx.AsyncClient(timeout=8.0) as client:
        resp = await client.post(
            f"{agent_url.rstrip('/')}/peers/add",
            content=payload,
            headers={
                "Content-Type": "application/json",
                "X-SBR-Signature": sig,
            },
        )
        resp.raise_for_status()


async def remove_wireguard_peer(agent_url: str, client_public_key: str) -> None:
    payload = json.dumps({"client_public_key": client_public_key}).encode("utf-8")
    sig = sign_node_agent_payload(payload)
    async with httpx.AsyncClient(timeout=8.0) as client:
        resp = await client.post(
            f"{agent_url.rstrip('/')}/peers/remove",
            content=payload,
            headers={
                "Content-Type": "application/json",
                "X-SBR-Signature": sig,
            },
        )
        resp.raise_for_status()


async def fetch_node_health(agent_url: str) -> dict:
    payload = b"{}"
    sig = sign_node_agent_payload(payload)
    async with httpx.AsyncClient(timeout=5.0) as client:
        resp = await client.get(
            f"{agent_url.rstrip('/')}/health",
            headers={"X-SBR-Signature": sig},
        )
        resp.raise_for_status()
        return resp.json()
