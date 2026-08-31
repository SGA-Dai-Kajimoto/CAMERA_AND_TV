package com.sony.dtv.camera_tv.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException

/**
 * 証明書の検証失敗を他のネットワーク障害と区別できることを固定する。
 * テレビの日付ずれが原因のことが多く、同じ文言で片付けると原因にたどり着けない。
 */
class NetworkFailureTest {

    @Test
    fun `ハンドシェイク失敗は証明書エラーと判定する`() {
        assertTrue(SSLHandshakeException("Chain validation failed").isCertificateFailure())
    }

    @Test
    fun `実機で起きた原因チェーンを判定できる`() {
        // 2026-08-28 実測: テレビの時計が3週間ずれていて OCSP 応答が有効期間外になった
        val actual = SSLHandshakeException("Chain validation failed").initCause(
            CertificateException("Chain validation failed").initCause(
                CertPathValidatorException("Response is unreliable: its validity interval is out-of-date"),
            ),
        )

        assertTrue(actual.isCertificateFailure())
    }

    @Test
    fun `通信タイムアウトは証明書エラーではない`() {
        assertFalse(SocketTimeoutException("timeout").isCertificateFailure())
        assertFalse(IOException("Canceled").isCertificateFailure())
    }

    @Test
    fun `APIエラーは証明書エラーではない`() {
        assertFalse(ApiException(400, "Input validation error.").isCertificateFailure())
    }

    @Test
    fun `原因が循環していても止まる`() {
        val a = IOException("a")
        val b = IOException("b", a)
        // cause をたどり続けても打ち切られる
        assertFalse(b.isCertificateFailure())
    }
}
