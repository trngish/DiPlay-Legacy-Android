package com.shilapi.xcertplay.network

import java.net.Inet6Address
import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class WirelessHostAddressTest {
    /** The iPhone joins a manual AP with an IPv4 lease, so the IPv4 address is the reachable one. */
    @Test fun manualApPrefersIpv4OverLinkLocalIpv6() {
        val ipv4 = ip("192.168.43.1")
        assertEquals(ipv4, wirelessHostAddress(listOf(ipv4, ip("fe80::1234")), 7))
        assertEquals(ipv4, wirelessHostAddress(listOf(ip("fe80::1234"), ipv4), 7))
    }

    @Test fun keepsLinkLocalIpv6ScopedWhenItIsTheOnlyAddress() {
        val wrongScope = Inet6Address.getByAddress(null, ip("fe80::1234").address, 3)
        assertEquals(8, (wirelessHostAddress(listOf(wrongScope), 8) as Inet6Address).scopeId)
    }

    @Test fun ignoresAddressesThatCannotServeAnAccessPoint() {
        val ipv4 = ip("192.168.43.1")
        assertEquals(ipv4, wirelessHostAddress(listOf(ip("::1"), ip("2001:db8::1"), ipv4), 7))
        assertEquals(ipv4, wirelessHostAddress(listOf(ip("fe80::1234"), ipv4), 0))
        assertNull(wirelessHostAddress(listOf(ip("0.0.0.0"), ip("127.0.0.1"), ip("224.0.0.251")), 7))
    }

    private fun ip(value: String) = InetAddress.getByName(value)
}
