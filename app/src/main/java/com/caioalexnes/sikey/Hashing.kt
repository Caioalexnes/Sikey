package com.caioalexnes.sikey

import java.io.InputStream
import java.security.MessageDigest

/**
 * Os três algoritmos que aparecem quando se fala de assinatura de APK.
 *
 * MD5 e SHA-1 estão aqui porque ferramentas antigas ainda os imprimem, não
 * porque prestam: os dois têm colisões práticas e ninguém deve decidir se
 * confia num APK olhando para eles. Quem verifica de verdade usa o SHA-256.
 */
enum class HashAlg(val label: String) {
    SHA256("SHA-256"),
    SHA1("SHA-1"),
    MD5("MD5"),
    ;

    /** Nome do algoritmo no JCE. Coincide com o rótulo nos três casos. */
    val jceName: String get() = label
}

/** Hashes de um mesmo conteúdo, sempre em hexadecimal minúsculo e sem separador. */
typealias Hashes = Map<HashAlg, String>

object Hashing {
    private val HEX = "0123456789abcdef".toCharArray()

    fun hex(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            out[i * 2] = HEX[v ushr 4]
            out[i * 2 + 1] = HEX[v and 0x0F]
        }
        return String(out)
    }

    /**
     * "AB:CD:EF…", que é como keytool, o Play Console e o assetlinks.json
     * mostram impressão digital de certificado.
     */
    fun colonHex(plainHex: String): String =
        plainHex.uppercase().chunked(2).joinToString(":")

    /**
     * Reduz qualquer jeito de escrever um hash à forma canônica usada na
     * comparação: sem dois-pontos, espaço, hífen ou "0x", tudo minúsculo.
     */
    fun normalize(input: String): String =
        input.trim()
            .removePrefix("0x")
            .removePrefix("0X")
            .filter { it.isLetterOrDigit() }
            .lowercase()

    fun of(bytes: ByteArray): Hashes =
        HashAlg.entries.associateWith { alg ->
            hex(MessageDigest.getInstance(alg.jceName).digest(bytes))
        }

    /**
     * Os três hashes numa leitura só. Um APK tem centenas de megabytes no pior
     * caso, e ler o arquivo três vezes é o triplo de espera por nada.
     *
     * Fecha o stream ao terminar.
     */
    fun ofStream(input: InputStream): Hashes {
        val digests = HashAlg.entries.associateWith { MessageDigest.getInstance(it.jceName) }
        input.use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digests.values.forEach { it.update(buffer, 0, read) }
            }
        }
        return digests.mapValues { (_, digest) -> hex(digest.digest()) }
    }
}
