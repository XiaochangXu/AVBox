package com.github.tvbox.osc.player.state

class VideoSizeGate {

    companion object {
        const val UNKNOWN = "0 X 0"

        fun format(width: Int, height: Int): String = "$width X $height"

        fun qualityTag(width: Int, height: Int): String {
            val short = minOf(width, height)
            return when {
                short >= 2160 -> "2160P"
                short >= 1440 -> "1440P"
                short >= 1080 -> "1080P"
                short >= 720 -> "720P"
                short >= 480 -> "480P"
                short >= 360 -> "360P"
                short >= 240 -> "240P"
                else -> "144P"
            }
        }
    }

    private var awaitingNewSession = false
    private var kernelContentReplaced = false
    private var priorWidth = 0
    private var priorHeight = 0

    fun onNewSession(kernelWidth: Int, kernelHeight: Int): String {
        awaitingNewSession = true
        kernelContentReplaced = false
        priorWidth = kernelWidth
        priorHeight = kernelHeight
        return UNKNOWN
    }

    fun onKernelContentReplaced() {
        kernelContentReplaced = true
    }

    fun textFor(kernelWidth: Int, kernelHeight: Int): String {
        if (isPreviousSessionValue(kernelWidth, kernelHeight)) return UNKNOWN
        awaitingNewSession = false
        return if (kernelWidth > 0 && kernelHeight > 0) format(kernelWidth, kernelHeight) else UNKNOWN
    }

    fun qualityFor(kernelWidth: Int, kernelHeight: Int): String {
        if (isPreviousSessionValue(kernelWidth, kernelHeight)) return ""
        return if (kernelWidth > 0 && kernelHeight > 0) {
            qualityTag(kernelWidth, kernelHeight)
        } else {
            ""
        }
    }

    private fun isPreviousSessionValue(width: Int, height: Int): Boolean =
        awaitingNewSession && !kernelContentReplaced && width == priorWidth && height == priorHeight
}
