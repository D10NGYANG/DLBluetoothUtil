package com.d10ng.bluetooth

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform