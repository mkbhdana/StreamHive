package com.mkbhdana.streamhive.data.db

import kotlinx.serialization.Serializable

/**
 * Serializable settings that are saved per-file in playback history.
 * These represent settings the user changed during playback
 * and should be restored when resuming the same file.
 */
@Serializable
data class PerFilePlayerSettings(
    val audioTrackLanguage: String? = null,
    val audioTrackLabel: String? = null,
    val subtitleTrackLanguage: String? = null,
    val subtitleTrackLabel: String? = null,
    val subtitleDelay: Long? = null,
    val subtitleFontSize: Int? = null,
    val subtitleColor: Long? = null,
    val subtitleBgOpacity: Float? = null,
    val subtitlePosition: Int? = null,
    val subtitleEdgeType: String? = null,
    val subtitleEdgeSize: Int? = null,
    val subtitleOutlineColor: Long? = null,
    val overrideAssSubtitleStyles: Boolean? = null,
    val subtitleScale: Float? = null,
    val subtitleFont: String? = null,
    val subtitleBold: Boolean? = null,
    val subtitleItalic: Boolean? = null,
    val subtitleAlignment: String? = null
)
