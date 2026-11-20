# SBR VPN REST API Documentation (v1)

Base URL: `https://api.yourdomain.com` (HTTPS required in production)

## Authentication
All protected endpoints require `Authorization: Bearer <access_token>`.

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/auth/register` | Public | Register user (`email`, `password`, `display_name`) |
| POST | `/api/v1/auth/login` | Public | Authenticate user and receive JWT |
| POST | `/api/v1/auth/guest` | Public | Create/resume device-scoped guest session |
| POST | `/api/v1/auth/logout` | Bearer | End client session |
| POST | `/api/v1/auth/password-reset/request` | Public | Request time-limited password recovery token |
| POST | `/api/v1/auth/password-reset/confirm` | Public | Confirm reset with token and new password |
| GET | `/api/v1/auth/me` | Bearer | Fetch user profile and active plan tier |

## VPN Registry & Tunnel Provisioning
| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/servers` | Public | List enabled, online deployed WireGuard nodes |
| GET | `/api/v1/network/ip` | Public | Return observed client public IP address |
| POST | `/api/v1/vpn/connect` | Bearer | Validate device public key, provision peer on VPS, return endpoint & tunnel IP |
| POST | `/api/v1/vpn/disconnect` | Bearer | Remove peer from VPS node and record aggregate session Rx/Tx bytes |

## Subscriptions & Support
| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/subscriptions/plans` | Public | Retrieve Free and Premium plan specs |
| POST | `/api/v1/subscriptions/verify-play` | Bearer | Verify Google Play purchase token and activate Premium |
| POST | `/api/v1/support/tickets` | Bearer | Submit support & diagnostics ticket |

## Admin RBAC Endpoints (`role == "admin"`)
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/v1/admin/overview` | Fleet summary (online nodes, active sessions, total users, bandwidth) |
| GET | `/api/v1/admin/servers` | List all configured VPN servers with CPU, RAM, uptime, bandwidth |
| POST | `/api/v1/admin/servers` | Register a new deployed WireGuard VPS node |
| PATCH | `/api/v1/admin/servers/{id}` | Enable/disable server or update tier/location |
| POST | `/api/v1/admin/servers/{id}/poll-health` | Poll live telemetry from node agent |
| DELETE | `/api/v1/admin/servers/{id}` | Remove server from registry |
| GET | `/api/v1/admin/sessions` | Inspect active WireGuard sessions |
| GET | `/api/v1/admin/users` | List users and subscription tiers |
| PATCH | `/api/v1/admin/users/{id}` | Update user plan, device limit, or role |
| GET | `/api/v1/admin/errors` | Inspect system error logs |
