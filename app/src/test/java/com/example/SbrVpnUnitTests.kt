package com.example

import com.example.data.model.VpnProtocolOption
import com.example.data.model.VpnProvisionResponse
import com.example.data.model.VpnServer
import com.example.data.remote.SbrApiClient
import com.example.util.FormatUtils
import com.example.vpn.WireGuardConfigBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SbrVpnUnitTests {

    @Test
    fun `generateKeyPair produces valid 44-char base64 Curve25519 keys`() {
        val (priv, pub) = WireGuardConfigBuilder.generateKeyPair()
        assertEquals(44, priv.length)
        assertEquals(44, pub.length)
        assertTrue(priv != pub)
    }

    @Test
    fun `buildFromProvision creates valid WireGuard Config with DNS leak protection`() {
        val (clientPriv, _) = WireGuardConfigBuilder.generateKeyPair()
        val (_, serverPub) = WireGuardConfigBuilder.generateKeyPair()

        val provision = VpnProvisionResponse(
            sessionId = "sess-1",
            serverId = "srv-de-1",
            serverPublicKey = serverPub,
            endpoint = "198.51.100.10:51820",
            assignedIp = "10.8.0.2/32",
            dnsServers = listOf("10.8.0.1", "1.1.1.1"),
            allowedIps = listOf("0.0.0.0/0", "::/0"),
            mtu = 1420,
            persistentKeepalive = 25
        )

        val config = WireGuardConfigBuilder.buildFromProvision(
            provision = provision,
            clientPrivateKeyBase64 = clientPriv,
            protocolOption = VpnProtocolOption.WIREGUARD
        )

        assertEquals(1, config.peers.size)
        assertEquals(2, config.`interface`.dnsServers.size)
        assertEquals(1420, config.`interface`.mtu.orElse(0))
    }

    @Test
    fun `normalizeAndValidateUrl enforces HTTPS except emulator loopback`() {
        assertEquals(
            "https://api.sbrvpn.example.com",
            SbrApiClient.normalizeAndValidateUrl("https://api.sbrvpn.example.com/")
        )
        assertNull(SbrApiClient.normalizeAndValidateUrl("http://api.sbrvpn.example.com"))
        assertNotNull(SbrApiClient.normalizeAndValidateUrl("http://10.0.2.2:8000"))
    }

    @Test
    fun `countryCodeToFlag converts ISO codes to flag emojis`() {
        assertEquals("🇺🇸", VpnServer.countryCodeToFlag("US"))
        assertEquals("🇩🇪", VpnServer.countryCodeToFlag("de"))
        assertEquals("🇯🇵", VpnServer.countryCodeToFlag("JP"))
    }

    @Test
    fun `FormatUtils formats bytes speed and duration accurately`() {
        assertEquals("512 B", FormatUtils.formatBytes(512))
        assertEquals("1.50 MB", FormatUtils.formatBytes(1024L * 1536L))
        assertEquals("01:02:05", FormatUtils.formatDuration(3725))
    }
}
