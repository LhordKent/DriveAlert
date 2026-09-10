package com.lhordkent.drivealert.data.connection

import java.security.SecureRandom

object ConnectionCode {
    private const val PREFIX = "DA"
    private const val BODY_LENGTH = 26
    private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    private val validPattern = Regex("^DA[$ALPHABET]{$BODY_LENGTH}$")
    private val secureRandom = SecureRandom()

    fun generate(): String = buildString(PREFIX.length + BODY_LENGTH) {
        append(PREFIX)
        repeat(BODY_LENGTH) { append(ALPHABET[secureRandom.nextInt(ALPHABET.length)]) }
    }

    fun normalize(value: String): String = value
        .uppercase()
        .filterNot(Char::isWhitespace)
        .replace("-", "")

    fun isValid(value: String): Boolean = validPattern.matches(normalize(value))

    fun format(value: String): String {
        val normalized = normalize(value)
        if (!isValid(normalized)) return value
        val body = normalized.removePrefix(PREFIX)
        return "$PREFIX-${body.substring(0, 5)}-${body.substring(5, 10)}-${body.substring(10, 15)}-" +
            "${body.substring(15, 20)}-${body.substring(20)}"
    }
}
