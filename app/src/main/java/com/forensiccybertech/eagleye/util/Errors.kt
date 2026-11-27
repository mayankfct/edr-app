package com.forensiccybertech.eagleye.util

import retrofit2.HttpException
import java.io.IOException

fun errorMessage(e: Throwable): String = when {
    e is IOException -> "Network error. Check your connection and pull to refresh."
    e is HttpException && e.code() == 403 -> "You don't have permission to view this data."
    e is HttpException && e.code() in 500..599 -> "Server error (${e.code()}). Try again shortly."
    else -> "Couldn't load data."
}
