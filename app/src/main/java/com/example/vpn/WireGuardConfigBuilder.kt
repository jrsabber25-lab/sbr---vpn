package com.example.vpn

import com.example.data.model.VpnProtocolOption
import com.example.data.model.VpnProvisionResponse
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.crypto.KeyPair
import java.io.BufferedReader
import java.io.StringReader

object WireGuardConfigBuilder {

    /**
     * Generates a genuine Curve25519 WireGuard KeyPair on-device.
     * The private key never leaves the device.
     */
    fun generateKeyPair(): Pair<String, String> {
        val keyPair = KeyPair()
        return keyPair.privateKey.toBase64() to keyPair.publicKey.toBase64()
    }

    /**
     * Builds a real WireGuard Config from backend-provisioned session parameters,
     * applying DNS leak protection and full-tunnel IPv4/IPv6 routing.
     */
    fun buildFromProvision(
        provision: VpnProvisionResponse,
        clientPrivateKeyBase64: String,
        protocolOption: VpnProtocolOption
    ): Config {
        val mtuValue = when (protocolOption) {
            VpnProtocolOption.WIREGUARD -> provision.mtu.coerceIn(1280, 1420)
            VpnProtocolOption.WIREGUARD_LOW_MTU -> 1280
        }

        val dnsString = provision.dnsServers
            .ifEmpty { listOf("1.1.1.1", "1.0.0.1") }
            .joinToString(",")

        val allowedIpsString = provision.allowedIps
            .ifEmpty { listOf("0.0.0.0/0", "::/0") }
            .joinToString(",")

        val wgInterface = Interface.Builder()
            .parsePrivateKey(clientPrivateKeyBase64)
            .parseAddresses(provision.assignedIp)
            .parseDnsServers(dnsString)
            .parseMtu(mtuValue.toString())
            .build()

        val peer = Peer.Builder()
            .parsePublicKey(provision.serverPublicKey)
            .parseEndpoint(provision.endpoint)
            .parseAllowedIPs(allowedIpsString)
            .parsePersistentKeepalive(provision.persistentKeepalive.coerceIn(15, 60).toString())
            .build()

        return Config.Builder()
            .setInterface(wgInterface)
            .addPeer(peer)
            .build()
    }

    /**
     * Parses a standard WireGuard .conf file imported by the user.
     */
    fun parseRawConfig(configText: String): Config {
        return BufferedReader(StringReader(configText)).use { reader ->
            Config.parse(reader)
        }
    }
}
