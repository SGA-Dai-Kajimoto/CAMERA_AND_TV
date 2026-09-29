package com.sony.dtv.camera_tv.data.remote

import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException

/** 原因チェーンをたどる上限。循環した cause で無限ループしないようにする。 */
private const val MAX_CAUSE_DEPTH = 10

/**
 * TLS 証明書の検証に失敗したか。
 *
 * テレビの日付がずれていると OCSP 応答が有効期間外と判定され、
 * 通信そのものは届いているのにハンドシェイクで落ちる。
 * 単なる通信エラーと同じ文言で片付けると原因にたどり着けないので区別する。
 */
fun Throwable.isCertificateFailure(): Boolean =
    generateSequence(this) { it.cause }
        .take(MAX_CAUSE_DEPTH)
        .any {
            it is SSLHandshakeException ||
                it is CertificateException ||
                it is CertPathValidatorException
        }
