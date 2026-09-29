package com.sony.dtv.camera_tv.data.remote.pairing

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** PKCE の verifier と、それを SHA-256 したチャレンジの組。 */
data class PkcePair(val verifier: String, val challenge: String)

/**
 * RFC 7636 の PKCE パラメータ生成。
 *
 * verifier は **この TV のメモリ上にしか置かない**。永続化するとサーバー側が
 * auth_code を持っていてもトークン化できないという前提が崩れるため。
 *
 * `android.util.Base64` ではなく `java.util.Base64` を使うのは、
 * JVM の単体テストでそのまま動かせるようにするため（minSdk 31 なので利用可能）。
 */
object Pkce {

    /** RFC 7636 が許す範囲（43〜128 文字）で十分な長さを取る。 */
    private const val VERIFIER_BYTES = 64

    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

    fun generate(random: SecureRandom = SecureRandom()): PkcePair {
        val bytes = ByteArray(VERIFIER_BYTES).also(random::nextBytes)
        val verifier = encoder.encodeToString(bytes)
        return PkcePair(verifier = verifier, challenge = challengeOf(verifier))
    }

    /** S256 方式のチャレンジ。BASE64URL(SHA256(ASCII(verifier)))。 */
    fun challengeOf(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return encoder.encodeToString(digest)
    }

    const val METHOD_S256 = "S256"
}
