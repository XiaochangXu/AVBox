package com.github.tvbox.osc.player.controller

enum class ProgressPhase {
    IDLE,
    LOADING,
    ACTIVE,
}

object ProgressUiGate {

    fun accept(phase: ProgressPhase, contentUrl: String?, currentUrl: String?): Boolean {
        if (phase != ProgressPhase.ACTIVE) return false
        if (contentUrl.isNullOrEmpty()) return false
        return contentUrl == currentUrl
    }
}

data class ProgressSnapshot(
    val durationMs: Int,
    val positionMs: Int,
    val bufferedPercent: Int,
)
