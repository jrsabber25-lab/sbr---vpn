# SBR VPN — Testing & Release Build Guide

## 1. Local JVM & Unit Testing
Run unit tests without an emulator:
```bash
gradle :app:testDebugUnitTest
```

## 2. Real-Device VPN Verification Checklist
1. **Android Permissions**: Verify `VpnService.prepare()` prompt appears before first connect and denial is handled gracefully without falsely showing `Connected`.
2. **Public IP Verification**: Check `Public IP` on the Home screen before connecting, connect to a deployed WireGuard node, and verify `Public IP` updates to the exit VPS IP (also cross-check via `https://ipleak.net`).
3. **DNS Leak Test**: Verify DNS queries resolve via the configured tunnel DNS (`10.8.0.1` / `1.1.1.1`) and not the carrier/ISP DNS resolver.
4. **IPv4 & IPv6 Behavior**: Confirm `0.0.0.0/0` and `::/0` routes prevent IPv6 bypass leaks on dual-stack mobile carriers.
5. **Network Switching (Wi-Fi <-> Mobile Data)**: Toggle Wi-Fi off during an active session; verify `SbrVpnManager` transitions to `Reconnecting` and re-establishes the WireGuard handshake automatically.
6. **Kill Switch**: Enable Kill Switch in Settings and toggle Android **Always-On VPN + Block connections without VPN** in System VPN Settings; confirm traffic cannot leak outside the tunnel on interruption.
7. **Server Failure Handling**: Stop the VPS `wg0` interface or point to an unreachable backend; verify the UI reports `Unreachable` / `Error` and never simulates `Connected`.

## 3. Release APK / AAB Build Instructions
1. Set `DEFAULT_API_BASE_URL` in `app/build.gradle.kts` to your production HTTPS API domain (`https://api.yourdomain.com`).
2. Provide your release signing keystore via environment variables (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`).
3. Build release App Bundle (`.aab`) for Google Play:
   ```bash
   gradle :app:bundleRelease
   ```
4. Complete Google Play Console's **VpnService declaration** and **Data Safety** form matching `docs/PRIVACY_POLICY_AND_TERMS.md`.
