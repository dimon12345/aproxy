package com.aproxy.app.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProxyUtilsTest {

    @Test
    fun parsesHostAndPort() {
        assertEquals(HostPort("example.com", 8080), parseHostPort("example.com:8080", 80))
        assertEquals(HostPort("example.com", 80), parseHostPort("example.com", 80))
        assertEquals(HostPort("127.0.0.1", 443), parseHostPort("127.0.0.1:443", 80))
    }

    @Test
    fun parsesIpv6Authority() {
        assertEquals(HostPort("::1", 8080), parseHostPort("[::1]:8080", 80))
        assertEquals(HostPort("2001:db8::1", 443), parseHostPort("[2001:db8::1]", 443))
    }

    @Test
    fun rejectsMalformedAuthorities() {
        assertNull(parseHostPort("", 80))
        assertNull(parseHostPort("host:", 80))
        assertNull(parseHostPort("host:notaport", 80))
        assertNull(parseHostPort("host:70000", 80))
        assertNull(parseHostPort("[::1", 80))
    }
}
