-- SBR VPN Production PostgreSQL Schema

CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) UNIQUE,
    password_hash VARCHAR(255),
    display_name VARCHAR(120) NOT NULL DEFAULT 'Guest User',
    role VARCHAR(20) NOT NULL DEFAULT 'user', -- 'user' | 'admin'
    is_guest BOOLEAN NOT NULL DEFAULT FALSE,
    device_id VARCHAR(120) UNIQUE,
    subscription_tier VARCHAR(20) NOT NULL DEFAULT 'free', -- 'free' | 'premium'
    subscription_expires_at TIMESTAMPTZ,
    max_devices INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);

CREATE TABLE vpn_servers (
    id VARCHAR(36) PRIMARY KEY,
    country_code CHAR(2) NOT NULL,
    country_name VARCHAR(80) NOT NULL,
    city VARCHAR(80) NOT NULL,
    hostname VARCHAR(255) NOT NULL UNIQUE,
    wg_port INTEGER NOT NULL DEFAULT 51820,
    health_port INTEGER NOT NULL DEFAULT 443,
    agent_url VARCHAR(255) NOT NULL,
    public_key VARCHAR(64) NOT NULL,
    subnet_cidr VARCHAR(32) NOT NULL DEFAULT '10.8.0.0/24',
    dns_servers VARCHAR(120) NOT NULL DEFAULT '10.8.0.1,1.1.1.1',
    tier VARCHAR(20) NOT NULL DEFAULT 'free', -- 'free' | 'premium'
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_online BOOLEAN NOT NULL DEFAULT FALSE,
    load_percent INTEGER NOT NULL DEFAULT 0,
    cpu_percent INTEGER NOT NULL DEFAULT 0,
    ram_percent INTEGER NOT NULL DEFAULT 0,
    uptime_seconds BIGINT NOT NULL DEFAULT 0,
    total_bandwidth_bytes BIGINT NOT NULL DEFAULT 0,
    last_checked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_vpn_servers_active ON vpn_servers(is_enabled, is_online);

CREATE TABLE vpn_sessions (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    server_id VARCHAR(36) NOT NULL REFERENCES vpn_servers(id) ON DELETE CASCADE,
    client_public_key VARCHAR(64) NOT NULL,
    assigned_ip VARCHAR(48) NOT NULL,
    protocol VARCHAR(32) NOT NULL DEFAULT 'wireguard',
    rx_bytes BIGINT NOT NULL DEFAULT 0,
    tx_bytes BIGINT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMPTZ
);

CREATE INDEX idx_vpn_sessions_user_active ON vpn_sessions(user_id, is_active);
CREATE INDEX idx_vpn_sessions_server_active ON vpn_sessions(server_id, is_active);

CREATE TABLE password_reset_tokens (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE support_tickets (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    subject VARCHAR(200) NOT NULL,
    category VARCHAR(40) NOT NULL,
    message TEXT NOT NULL,
    app_version VARCHAR(32) NOT NULL,
    android_version VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'open',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE system_errors (
    id VARCHAR(36) PRIMARY KEY,
    component VARCHAR(60) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'error',
    message TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
