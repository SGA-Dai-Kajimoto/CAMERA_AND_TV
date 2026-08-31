package com.sony.dtv.camera_tv.data.remote.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [Pkce] の単体テスト。
 * verifier / challenge の対応が崩れるとサーバー側で 401 になり認証が通らなくなる。
 */
class PkceTest {

    /** RFC 7636 の base64url（パディング無し）で許される文字だけか。 */
    private val allowed = Regex("^[A-Za-z0-9\\-._~]+$")

    @Test
    fun `verifier は RFC 7636 の長さと文字種に収まる`() {
        val verifier = Pkce.generate().verifier

        assertTrue(verifier.length in 43..128)
        assertTrue(verifier, allowed.matches(verifier))
    }

    @Test
    fun `challenge も base64url でパディングを含まない`() {
        val challenge = Pkce.generate().challenge

        assertTrue(challenge, allowed.matches(challenge))
        assertEquals(43, challenge.length) // SHA-256 の 32 バイトを base64url した長さ
    }

    @Test
    fun `challenge は verifier から一意に決まる`() {
        val pair = Pkce.generate()

        assertEquals(pair.challenge, Pkce.challengeOf(pair.verifier))
    }

    @Test
    fun `生成のたびに異なる verifier になる`() {
        assertNotEquals(Pkce.generate().verifier, Pkce.generate().verifier)
    }

    @Test
    fun `RFC 7636 の例と一致する`() {
        // RFC 7636 Appendix B の値
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"

        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.challengeOf(verifier))
    }
}
