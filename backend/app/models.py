from datetime import datetime, timezone
from sqlalchemy import Boolean, DateTime, ForeignKey, Integer, String, Text
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship
from .config import settings


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


engine = create_async_engine(settings.database_url, echo=False, future=True)
AsyncSessionLocal = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)


class Base(DeclarativeBase):
    pass


class User(Base):
    __tablename__ = "users"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    email: Mapped[str | None] = mapped_column(String(255), unique=True, nullable=True, index=True)
    password_hash: Mapped[str | None] = mapped_column(String(255), nullable=True)
    display_name: Mapped[str] = mapped_column(String(120), default="Guest User")
    role: Mapped[str] = mapped_column(String(20), default="user")  # user | admin
    is_guest: Mapped[bool] = mapped_column(Boolean, default=False)
    device_id: Mapped[str | None] = mapped_column(String(120), unique=True, nullable=True)
    subscription_tier: Mapped[str] = mapped_column(String(20), default="free")  # free | premium
    subscription_expires_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    max_devices: Mapped[int] = mapped_column(Integer, default=1)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)

    sessions: Mapped[list["VpnSession"]] = relationship(back_populates="user")


class VpnServer(Base):
    __tablename__ = "vpn_servers"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    country_code: Mapped[str] = mapped_column(String(4), index=True)
    country_name: Mapped[str] = mapped_column(String(80))
    city: Mapped[str] = mapped_column(String(80))
    hostname: Mapped[str] = mapped_column(String(255), unique=True)
    wg_port: Mapped[int] = mapped_column(Integer, default=51820)
    health_port: Mapped[int] = mapped_column(Integer, default=443)
    agent_url: Mapped[str] = mapped_column(String(255))
    public_key: Mapped[str] = mapped_column(String(64))
    subnet_cidr: Mapped[str] = mapped_column(String(32), default="10.8.0.0/24")
    dns_servers: Mapped[str] = mapped_column(String(120), default="10.8.0.1,1.1.1.1")
    tier: Mapped[str] = mapped_column(String(20), default="free")  # free | premium
    is_enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    is_online: Mapped[bool] = mapped_column(Boolean, default=False)
    load_percent: Mapped[int] = mapped_column(Integer, default=0)
    cpu_percent: Mapped[int] = mapped_column(Integer, default=0)
    ram_percent: Mapped[int] = mapped_column(Integer, default=0)
    uptime_seconds: Mapped[int] = mapped_column(Integer, default=0)
    total_bandwidth_bytes: Mapped[int] = mapped_column(Integer, default=0)
    last_checked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class VpnSession(Base):
    __tablename__ = "vpn_sessions"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id"), index=True)
    server_id: Mapped[str] = mapped_column(ForeignKey("vpn_servers.id"), index=True)
    client_public_key: Mapped[str] = mapped_column(String(64), index=True)
    assigned_ip: Mapped[str] = mapped_column(String(48))
    protocol: Mapped[str] = mapped_column(String(32), default="wireguard")
    rx_bytes: Mapped[int] = mapped_column(Integer, default=0)
    tx_bytes: Mapped[int] = mapped_column(Integer, default=0)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, index=True)
    started_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    ended_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    user: Mapped["User"] = relationship(back_populates="sessions")


class PasswordResetToken(Base):
    __tablename__ = "password_reset_tokens"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id"), index=True)
    token_hash: Mapped[str] = mapped_column(String(255))
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    used: Mapped[bool] = mapped_column(Boolean, default=False)


class SupportTicket(Base):
    __tablename__ = "support_tickets"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    user_id: Mapped[str | None] = mapped_column(ForeignKey("users.id"), nullable=True)
    subject: Mapped[str] = mapped_column(String(200))
    category: Mapped[str] = mapped_column(String(40))
    message: Mapped[str] = mapped_column(Text)
    app_version: Mapped[str] = mapped_column(String(32))
    android_version: Mapped[str] = mapped_column(String(32))
    status: Mapped[str] = mapped_column(String(24), default="open")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class SystemErrorLog(Base):
    __tablename__ = "system_errors"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    component: Mapped[str] = mapped_column(String(60))
    severity: Mapped[str] = mapped_column(String(20), default="error")
    message: Mapped[str] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


async def get_db():
    async with AsyncSessionLocal() as session:
        yield session
