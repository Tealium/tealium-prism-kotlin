package com.tealium.prism.core.internal.utils

import java.security.MessageDigest
import java.util.Locale


interface HashingProvider {
    fun hash(algorithm: String, input: String): String
}

object DefaultHashingProvider : HashingProvider {

    override fun hash(algorithm: String, input: String): String {
        val bytes = MessageDigest.getInstance(algorithm)
            .digest(input.toByteArray())
        return printHexBinary(bytes).uppercase(Locale.ROOT)
    }

    private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

    private fun printHexBinary(data: ByteArray): String {
        val r = StringBuilder(data.size * 2)
        data.forEach { b ->
            val i = b.toInt()
            r.append(HEX_CHARS[i shr 4 and 0xF])
            r.append(HEX_CHARS[i and 0xF])
        }
        return r.toString()
    }
}

fun String.sha256() : String {
    return DefaultHashingProvider.hash("SHA-256", this)
}