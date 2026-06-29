package com.example.safeshe2

import java.util.Random

object Utils {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private const val CODE_LENGTH = 6
    private val random = Random()

    fun generateAngelCode(): String {
        val code = StringBuilder()
        repeat(CODE_LENGTH) {
            val index = random.nextInt(ALPHABET.length)
            code.append(ALPHABET[index])
        }
        return code.toString()
    }
} 