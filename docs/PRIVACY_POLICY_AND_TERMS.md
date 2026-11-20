# SBR VPN Privacy Policy & Terms Template

## Privacy Policy

SBR VPN is engineered to minimize data collection and protect user traffic with WireGuard encryption. We do **not** make blanket "zero-log" claims; instead, we disclose every operational record retained and its exact retention window.

### 1. Data We Never Collect or Sell
- Browsing history, visited URLs, or destination IP addresses.
- DNS query contents.
- Decrypted VPN tunnel packet payloads.
- We never sell or monetize user traffic or personal data.

### 2. Operational Data Retained & Retention Windows
| Data Category | Purpose | Retention Period |
|---|---|---|
| Account Email & Argon2/bcrypt Hash | Authentication & password recovery | Until account deletion |
| Device WireGuard Public Key & Tunnel IP (`10.8.0.x`) | Routing encrypted WireGuard packets | Active session + purged within 15 minutes of disconnect |
| Aggregate Session Byte Counters (`rx_bytes`, `tx_bytes`) | Fair-usage quota & capacity planning | 30 days (not linked to browsing destinations) |
| API / Node Error Logs | Reliability debugging (stripped of payload/IP) | 14 days |

### 3. On-Device Security
- Client Curve25519 private keys are generated on-device and encrypted using Android Keystore (`AES-256-GCM`).
- All control-plane API communication requires HTTPS.
