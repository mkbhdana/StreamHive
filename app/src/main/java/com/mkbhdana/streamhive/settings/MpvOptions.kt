package com.mkbhdana.streamhive.settings

/**
 * Option lists for the MPV engine settings, shared by the phone and TV settings
 * screens so the two surfaces always offer the same choices.
 */
object MpvOptions {

    /**
     * mpv's built-in profiles (see mpv's etc/builtin.conf). "default" is applied by
     * mpv implicitly, so selecting it means "don't set the profile option at all".
     */
    val profiles = listOf(
        "fast" to "Fast",
        "default" to "Default",
        "high-quality" to "High Quality",
        "gpu-hq" to "GPU HQ",
        "low-latency" to "Low Latency",
        "sw-fast" to "SW Fast"
    )

    val debanding = listOf(
        "none" to "None",
        "cpu" to "CPU",
        "gpu" to "GPU"
    )

    fun profileLabel(value: String): String =
        profiles.firstOrNull { it.first == value }?.second ?: value

    fun debandingLabel(value: String): String =
        debanding.firstOrNull { it.first == value }?.second ?: value
}
