package com.shilapi.xcertplay.network

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * A manual access point hands the iPhone an IPv4 lease, so that is the address it can actually
 * reach, and it is the same one put into the 0x4301 session parameters. The scoped link-local IPv6
 * stays as the fallback for an interface that has nothing else, and it keeps the interface index
 * because a link-local address is unusable without its scope.
 */
internal fun wirelessHostAddress(addresses: List<InetAddress>, interfaceIndex: Int): InetAddress? {
    addresses.firstOrNull {
        it is Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress &&
            !it.isAnyLocalAddress && !it.isMulticastAddress
    }?.let { return it }
    if (interfaceIndex <= 0) return null
    val linkLocal = addresses.filterIsInstance<Inet6Address>().firstOrNull { it.isLinkLocalAddress }
        ?: return null
    return Inet6Address.getByAddress(null, linkLocal.address, interfaceIndex)
}
