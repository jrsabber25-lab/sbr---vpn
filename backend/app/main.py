from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
import ipaddress
import secrets
import uuid
from fastapi import Depends, FastAPI, HTTPException, Request, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, EmailStr, Field
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from .config import settings
from .models import (
    AsyncSessionLocal,
    Base,
    PasswordResetToken,
    SupportTicket,
    SystemErrorLog,
    User,
    VpnServer,
    VpnSession,
    engine,
    get_db,
)
from .node_client import add_wireguard_peer, fetch_node_health, remove_wireguard_peer
from .security import (
    create_access_token,
    get_current_user,
    hash_password,
    hash_token,
    is_valid_wireguard_key,
    require_admin,
    verify_password,
)


@asynccontextmanager
async def lifespan(_app: FastAPI):
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    if settings.bootstrap_admin_password:
        async with AsyncSessionLocal() as db:
            existing = await db.execute(
                select(User).where(User.email == settings.bootstrap_admin_email)
            )
            if existing.scalar_one_or_none() is None:
                db.add(
                    User(
                        id=str(uuid.uuid4()),
                        email=settings.bootstrap_admin_email,
                        password_hash=hash_password(settings.bootstrap_admin_password),
                        display_name="SBR Administrator",
                        role="admin",
                        is_guest=False,
                        subscription_tier="premium",
                        max_devices=10,
                    )
                )
                await db.commit()
    yield


app = FastAPI(title="SBR VPN Control Plane API", version="1.0.0", lifespan=lifespan)

origins = [o.strip() for o in settings.cors_origins.split(",") if o.strip()]
app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


async def log_system_error(db: AsyncSession, component: str, message: str, severity: str = "error"):
    db.add(
        SystemErrorLog(
            id=str(uuid.uuid4()),
            component=component,
            severity=severity,
            message=message[:2000],
        )
    )
    await db.commit()


# --- Schemas ---
class AuthRequest(BaseModel):
    email: EmailStr
    password: str = Field(min_length=6, max_length=128)
    display_name: str | None = None


class GuestAuthRequest(BaseModel):
    device_id: str = Field(min_length=8, max_length=120)


class PasswordResetReq(BaseModel):
    email: EmailStr


class PasswordResetConfirmReq(BaseModel):
    email: EmailStr
    token: str
    new_password: str = Field(min_length=8, max_length=128)


class VpnConnectReq(BaseModel):
    server_id: str
    client_public_key: str
    protocol: str = "wireguard"


class VpnDisconnectReq(BaseModel):
    session_id: str | None = None
    rx_bytes: int = 0
    tx_bytes: int = 0


class PlayVerifyReq(BaseModel):
    product_id: str
    purchase_token: str


class SupportTicketReq(BaseModel):
    subject: str = Field(min_length=3, max_length=200)
    category: str = Field(min_length=2, max_length=40)
    message: str = Field(min_length=10, max_length=4000)
    app_version: str
    android_version: str


class AdminServerCreateReq(BaseModel):
    country_code: str = Field(min_length=2, max_length=2)
    country_name: str
    city: str
    hostname: str
    wg_port: int = 51820
    health_port: int = 443
    agent_url: str
    public_key: str
    subnet_cidr: str = "10.8.0.0/24"
    dns_servers: str = "10.8.0.1,1.1.1.1"
    tier: str = "free"


class AdminServerPatchReq(BaseModel):
    is_enabled: bool | None = None
    tier: str | None = None
    city: str | None = None
    country_name: str | None = None


class AdminUserPatchReq(BaseModel):
    subscription_tier: str | None = None
    max_devices: int | None = None
    role: str | None = None


def serialize_user(u: User) -> dict:
    return {
        "id": u.id,
        "email": u.email,
        "display_name": u.display_name,
        "is_guest": u.is_guest,
        "subscription_tier": u.subscription_tier,
        "subscription_expires_at": u.subscription_expires_at.isoformat() if u.subscription_expires_at else None,
        "max_devices": u.max_devices,
        "role": u.role,
    }


# --- Auth Endpoints ---
@app.post("/api/v1/auth/register")
async def register(body: AuthRequest, db: AsyncSession = Depends(get_db)):
    existing = await db.execute(select(User).where(User.email == body.email.lower()))
    if existing.scalar_one_or_none():
        raise HTTPException(status_code=409, detail="Email already registered")
    user = User(
        id=str(uuid.uuid4()),
        email=body.email.lower(),
        password_hash=hash_password(body.password),
        display_name=body.display_name or "SBR Member",
        role="user",
        is_guest=False,
        subscription_tier="free",
        max_devices=1,
    )
    db.add(user)
    await db.commit()
    token = create_access_token(user.id, user.role)
    return {"access_token": token, "token_type": "bearer", "user": serialize_user(user)}


@app.post("/api/v1/auth/login")
async def login(body: AuthRequest, db: AsyncSession = Depends(get_db)):
    res = await db.execute(select(User).where(User.email == body.email.lower()))
    user = res.scalar_one_or_none()
    if not user or not user.password_hash or not verify_password(body.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Invalid email or password")
    token = create_access_token(user.id, user.role)
    return {"access_token": token, "token_type": "bearer", "user": serialize_user(user)}


@app.post("/api/v1/auth/guest")
async def guest_auth(body: GuestAuthRequest, db: AsyncSession = Depends(get_db)):
    res = await db.execute(select(User).where(User.device_id == body.device_id))
    user = res.scalar_one_or_none()
    if user is None:
        user = User(
            id=str(uuid.uuid4()),
            email=None,
            password_hash=None,
            display_name="Guest User",
            role="user",
            is_guest=True,
            device_id=body.device_id,
            subscription_tier="free",
            max_devices=1,
        )
        db.add(user)
        await db.commit()
    token = create_access_token(user.id, user.role)
    return {"access_token": token, "token_type": "bearer", "user": serialize_user(user)}


@app.post("/api/v1/auth/logout")
async def logout(_user: User = Depends(get_current_user)):
    return {"message": "Session terminated. Client token cleared."}


@app.post("/api/v1/auth/password-reset/request")
async def request_password_reset(body: PasswordResetReq, db: AsyncSession = Depends(get_db)):
    res = await db.execute(select(User).where(User.email == body.email.lower()))
    user = res.scalar_one_or_none()
    if user:
        raw_token = secrets.token_urlsafe(24)
        db.add(
            PasswordResetToken(
                id=str(uuid.uuid4()),
                user_id=user.id,
                token_hash=hash_token(raw_token),
                expires_at=datetime.now(timezone.utc) + timedelta(minutes=20),
                used=False,
            )
        )
        await db.commit()
        # Production: dispatch raw_token via transactional SMTP / SES / Postmark
    return {"message": "If an account exists for this email, a recovery token has been dispatched."}


@app.post("/api/v1/auth/password-reset/confirm")
async def confirm_password_reset(body: PasswordResetConfirmReq, db: AsyncSession = Depends(get_db)):
    res = await db.execute(select(User).where(User.email == body.email.lower()))
    user = res.scalar_one_or_none()
    if not user:
        raise HTTPException(status_code=400, detail="Invalid recovery request")
    tok_res = await db.execute(
        select(PasswordResetToken)
        .where(
            PasswordResetToken.user_id == user.id,
            PasswordResetToken.token_hash == hash_token(body.token),
            PasswordResetToken.used.is_(False),
        )
    )
    record = tok_res.scalar_one_or_none()
    if not record or record.expires_at < datetime.now(timezone.utc):
        raise HTTPException(status_code=400, detail="Recovery token expired or invalid")
    user.password_hash = hash_password(body.new_password)
    record.used = True
    await db.commit()
    return {"message": "Password updated successfully."}


@app.get("/api/v1/auth/me")
async def get_me(user: User = Depends(get_current_user)):
    return serialize_user(user)


# --- Public Server Registry & Health ---
@app.get("/api/v1/servers")
async def list_servers(db: AsyncSession = Depends(get_db)):
    res = await db.execute(
        select(VpnServer).where(VpnServer.is_enabled.is_(True), VpnServer.is_online.is_(True))
    )
    servers = res.scalars().all()
    return [
        {
            "id": s.id,
            "country_code": s.country_code,
            "country_name": s.country_name,
            "city": s.city,
            "hostname": s.hostname,
            "port": s.wg_port,
            "health_port": s.health_port,
            "is_online": s.is_online,
            "load_percent": s.load_percent,
            "tier": s.tier,
        }
        for s in servers
    ]


@app.get("/api/v1/network/ip")
async def client_ip(request: Request):
    forwarded = request.headers.get("x-forwarded-for")
    ip = forwarded.split(",")[0].strip() if forwarded else (request.client.host if request.client else "unknown")
    return {"ip": ip}


# --- WireGuard Tunnel Provisioning ---
@app.post("/api/v1/vpn/connect")
async def vpn_connect(
    body: VpnConnectReq,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    if not is_valid_wireguard_key(body.client_public_key):
        raise HTTPException(status_code=400, detail="Invalid Curve25519 WireGuard public key")

    srv_res = await db.execute(select(VpnServer).where(VpnServer.id == body.server_id))
    server = srv_res.scalar_one_or_none()
    if not server or not server.is_enabled or not server.is_online:
        raise HTTPException(status_code=404, detail="Selected VPN server is unavailable")

    if server.tier == "premium" and user.subscription_tier != "premium":
        raise HTTPException(status_code=403, detail="Premium subscription required for this location")

    active_count_res = await db.execute(
        select(func.count(VpnSession.id)).where(
            VpnSession.user_id == user.id,
            VpnSession.is_active.is_(True),
        )
    )
    active_count = active_count_res.scalar_one()
    if active_count >= user.max_devices:
        raise HTTPException(status_code=429, detail=f"Device connection limit reached ({user.max_devices})")

    # Allocate next available tunnel IP in the server's subnet
    used_ips_res = await db.execute(
        select(VpnSession.assigned_ip).where(
            VpnSession.server_id == server.id,
            VpnSession.is_active.is_(True),
        )
    )
    used_ips = {ip.split("/")[0] for ip in used_ips_res.scalars().all()}
    network = ipaddress.ip_network(server.subnet_cidr, strict=False)
    assigned_ip = None
    for host in list(network.hosts())[1:]:
        if str(host) not in used_ips:
            assigned_ip = f"{host}/32"
            break
    if not assigned_ip:
        raise HTTPException(status_code=503, detail="Server subnet pool exhausted")

    try:
        await add_wireguard_peer(server.agent_url, body.client_public_key, assigned_ip)
    except Exception as exc:
        await log_system_error(db, "vpn_provision", f"Node {server.hostname} error: {exc}")
        raise HTTPException(status_code=502, detail="Failed to provision peer on VPN node") from exc

    session = VpnSession(
        id=str(uuid.uuid4()),
        user_id=user.id,
        server_id=server.id,
        client_public_key=body.client_public_key,
        assigned_ip=assigned_ip,
        protocol=body.protocol,
        is_active=True,
    )
    db.add(session)
    await db.commit()

    return {
        "session_id": session.id,
        "server_id": server.id,
        "server_public_key": server.public_key,
        "endpoint": f"{server.hostname}:{server.wg_port}",
        "assigned_ip": assigned_ip,
        "dns_servers": [d.strip() for d in server.dns_servers.split(",") if d.strip()],
        "allowed_ips": ["0.0.0.0/0", "::/0"],
        "mtu": 1420,
        "persistent_keepalive": 25,
    }


@app.post("/api/v1/vpn/disconnect")
async def vpn_disconnect(
    body: VpnDisconnectReq,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    query = select(VpnSession).where(VpnSession.user_id == user.id, VpnSession.is_active.is_(True))
    if body.session_id:
        query = query.where(VpnSession.id == body.session_id)
    res = await db.execute(query)
    sessions = res.scalars().all()

    for sess in sessions:
        srv_res = await db.execute(select(VpnServer).where(VpnServer.id == sess.server_id))
        server = srv_res.scalar_one_or_none()
        if server:
            try:
                await remove_wireguard_peer(server.agent_url, sess.client_public_key)
            except Exception as exc:
                await log_system_error(db, "vpn_disconnect", f"Peer removal warning: {exc}", "warning")
        sess.rx_bytes = body.rx_bytes
        sess.tx_bytes = body.tx_bytes
        sess.is_active = False
        sess.ended_at = datetime.now(timezone.utc)
    await db.commit()
    return {"message": "Disconnected"}


# --- Subscriptions & Support ---
@app.get("/api/v1/subscriptions/plans")
async def subscription_plans():
    return [
        {
            "id": "free",
            "name": "Free Plan",
            "play_product_id": "",
            "price_display": "$0 / month",
            "billing_period": "Free",
            "max_devices": 1,
            "fair_usage_gb": 10,
            "features": [
                "Access to Free-tier deployed WireGuard regions",
                "10 GB monthly fair-usage allowance",
                "1 active device tunnel",
                "DNS leak protection & Kill Switch included",
            ],
        },
        {
            "id": "premium_monthly",
            "name": "SBR Premium",
            "play_product_id": "sbr_vpn_premium_monthly",
            "price_display": "$4.99 / month",
            "billing_period": "Monthly",
            "max_devices": 5,
            "fair_usage_gb": 500,
            "features": [
                "Access to all deployed global WireGuard nodes",
                "500 GB monthly fair-usage allowance",
                "Up to 5 simultaneous devices",
                "Priority server routing & bandwidth QoS",
            ],
        },
    ]


@app.post("/api/v1/subscriptions/verify-play")
async def verify_play_purchase(
    body: PlayVerifyReq,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    # Production note: Verify body.purchase_token against Google Play Developer API
    # androidpublisher.purchases.subscriptionsv2.get using your Service Account credentials.
    if len(body.purchase_token) < 12:
        raise HTTPException(status_code=400, detail="Invalid Google Play purchase token")
    user.subscription_tier = "premium"
    user.max_devices = 5
    user.subscription_expires_at = datetime.now(timezone.utc) + timedelta(days=30)
    await db.commit()
    return serialize_user(user)


@app.post("/api/v1/support/tickets")
async def create_support_ticket(
    body: SupportTicketReq,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    ticket = SupportTicket(
        id=str(uuid.uuid4()),
        user_id=user.id,
        subject=body.subject,
        category=body.category,
        message=body.message,
        app_version=body.app_version,
        android_version=body.android_version,
    )
    db.add(ticket)
    await db.commit()
    return {
        "ticket_id": ticket.id,
        "status": ticket.status,
        "created_at": ticket.created_at.isoformat(),
    }


# --- Admin RBAC Dashboard APIs ---
@app.get("/api/v1/admin/overview")
async def admin_overview(
    _admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    servers = (await db.execute(select(VpnServer))).scalars().all()
    users_count = (await db.execute(select(func.count(User.id)))).scalar_one()
    active_sessions = (
        await db.execute(select(func.count(VpnSession.id)).where(VpnSession.is_active.is_(True)))
    ).scalar_one()
    total_bw = sum(s.total_bandwidth_bytes for s in servers)
    return {
        "total_servers": len(servers),
        "online_servers": sum(1 for s in servers if s.is_online and s.is_enabled),
        "total_users": users_count,
        "active_sessions": active_sessions,
        "total_bandwidth_bytes": total_bw,
    }


@app.get("/api/v1/admin/servers")
async def admin_list_servers(
    _admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    servers = (await db.execute(select(VpnServer))).scalars().all()
    return [
        {
            "id": s.id,
            "country_code": s.country_code,
            "country_name": s.country_name,
            "city": s.city,
            "hostname": s.hostname,
            "wg_port": s.wg_port,
            "health_port": s.health_port,
            "agent_url": s.agent_url,
            "public_key": s.public_key,
            "tier": s.tier,
            "is_enabled": s.is_enabled,
            "is_online": s.is_online,
            "load_percent": s.load_percent,
            "cpu_percent": s.cpu_percent,
            "ram_percent": s.ram_percent,
            "uptime_seconds": s.uptime_seconds,
            "total_bandwidth_bytes": s.total_bandwidth_bytes,
        }
        for s in servers
    ]


@app.post("/api/v1/admin/servers", status_code=status.HTTP_201_CREATED)
async def admin_add_server(
    body: AdminServerCreateReq,
    _admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    if not is_valid_wireguard_key(body.public_key):
        raise HTTPException(status_code=400, detail="Invalid WireGuard server public key")

    # Probe node agent immediately to verify node is actually reachable
    is_online = False
    cpu = 0
    ram = 0
    uptime = 0
    try:
        health = await fetch_node_health(body.agent_url)
        is_online = True
        cpu = int(health.get("cpu_percent", 0))
        ram = int(health.get("ram_percent", 0))
        uptime = int(health.get("uptime_seconds", 0))
    except Exception as exc:
        await log_system_error(db, "admin_add_server", f"Agent unreachable for {body.hostname}: {exc}", "warning")

    server = VpnServer(
        id=str(uuid.uuid4()),
        country_code=body.country_code.upper(),
        country_name=body.country_name,
        city=body.city,
        hostname=body.hostname,
        wg_port=body.wg_port,
        health_port=body.health_port,
        agent_url=body.agent_url,
        public_key=body.public_key,
        subnet_cidr=body.subnet_cidr,
        dns_servers=body.dns_servers,
        tier=body.tier.lower(),
        is_enabled=True,
        is_online=is_online,
        cpu_percent=cpu,
        ram_percent=ram,
        uptime_seconds=uptime,
        last_checked_at=datetime.now(timezone.utc),
    )
    db.add(server)
    await db.commit()
    return {"id": server.id, "is_online": is_online}


@app.patch("/api/v1/admin/servers/{server_id}")
async def admin_patch_server(
    server_id: str,
    body: AdminServerPatchReq,
    _admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    server = (await db.execute(select(VpnServer).where(VpnServer.id == server_id))).scalar_one_or_none()
    if not server:
        raise HTTPException(status_code=404, detail="Server not found")
    if body.is_enabled is not None:
        server.is_enabled = body.is_enabled
    if body.tier is not None:
        server.tier = body.tier.lower()
    if body.city is not None:
        server.city = body.city
    if body.country_name is not None:
        server.country_name = body.country_name
    await db.commit()
    return {"status": "updated"}


@app.post("/api/v1/admin/servers/{server_id}/poll-health")
async def admin_poll_server_health(
    server_id: str,
    _admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    server = (await db.execute(select(VpnServer).where(VpnServer.id == server_id))).scalar_one_or_none()
    if not server:
        raise HTTPException(status_code=404, detail="Server not found")
    try:
        health = await fetch_node_health(server.agent_url)
        server.is_online = True
        server.cpu_percent = int(health.get("cpu_percent", 0))
        server.ram_percent = int(health.get("ram_percent", 0))
        server.uptime_seconds = int(health.get("uptime_seconds", 0))
        server.total_bandwidth_bytes = int(health.get("total_bandwidth_bytes", 0))
        server.load_percent = min(100, max(server.cpu_percent, int(health.get("active_peers", 0))))
        server.last_checked_at = datetime.now(timezone.utc)
    except Exception as exc:
        server.is_online = False
        await log_system_error(db, "health_poll", f"Server {server.hostname} unreachable: {exc}")
    await db.commit()
    return {"is_online": server.is_online, "cpu_percent": server.cpu_percent, "ram_percent": server.ram_percent}


@app.delete("/api/v1/admin/servers/{server_id}")
async def admin_delete_server(
    server_id: str,
    _admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    server = (await db.execute(select(VpnServer).where(VpnServer.id == server_id))).scalar_one_or_none()
    if not server:
        raise HTTPException(status_code=404, detail="Server not found")
    await db.delete(server)
    await db.commit()
    return {"status": "deleted"}


@app.get("/api/v1/admin/sessions")
async def admin_active_sessions(
    _admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    res = await db.execute(select(VpnSession).where(VpnSession.is_active.is_(True)))
    sessions = res.scalars().all()
    return [
        {
            "id": s.id,
            "user_id": s.user_id,
            "server_id": s.server_id,
            "assigned_ip": s.assigned_ip,
            "protocol": s.protocol,
            "started_at": s.started_at.isoformat(),
        }
        for s in sessions
    ]


@app.get("/api/v1/admin/users")
async def admin_list_users(
    _admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    users = (await db.execute(select(User))).scalars().all()
    return [serialize_user(u) for u in users]


@app.patch("/api/v1/admin/users/{user_id}")
async def admin_patch_user(
    user_id: str,
    body: AdminUserPatchReq,
    _admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    user = (await db.execute(select(User).where(User.id == user_id))).scalar_one_or_none()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    if body.subscription_tier is not None:
        user.subscription_tier = body.subscription_tier.lower()
    if body.max_devices is not None:
        user.max_devices = body.max_devices
    if body.role is not None:
        user.role = body.role.lower()
    await db.commit()
    return serialize_user(user)


@app.get("/api/v1/admin/errors")
async def admin_system_errors(
    _admin: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    logs = (
        await db.execute(select(SystemErrorLog).order_by(SystemErrorLog.created_at.desc()).limit(100))
    ).scalars().all()
    return [
        {
            "id": l.id,
            "component": l.component,
            "severity": l.severity,
            "message": l.message,
            "created_at": l.created_at.isoformat(),
        }
        for l in logs
    ]
