package com.d10ng.bluetooth.demo

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform