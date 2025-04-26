package com.reeman.elevator.utils

object IdGenerateUtil {

    fun generateTimeBasedHexId(): String {
        val toString = System.currentTimeMillis().toString(16)
        val timestampPart = toString.takeLast(8)
        return timestampPart.uppercase()
    }
}