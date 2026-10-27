package io.alema.visualcontext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkTest {
    @Test fun acceptsDeviceToken() = assertEquals("vc_abcdef0123456789abcd", DeepLink.parse(" vc_abcdef0123456789abcd "))
    @Test fun rejectsOtherSecrets() { assertNull(DeepLink.parse("b44u_abcdef0123456789")); assertNull(DeepLink.parse("AIzaSyAbcdef0123456789")) }
    @Test fun rejectsJunk() { assertNull(DeepLink.parse("vc_<script>")); assertNull(DeepLink.parse("")); assertNull(DeepLink.parse(null)) }
}
