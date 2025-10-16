package com.phodal.ctxmesh

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform