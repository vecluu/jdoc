package com.example.sample.kt

object KotlinUtils {
    @JvmStatic
    fun joinNames(names: List<String>, separator: String): String = names.joinToString(separator)

    @JvmStatic
    fun twice(value: Int): Int = value * 2
}
