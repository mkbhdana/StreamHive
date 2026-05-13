package com.mkbhdana.streamhive.settings

import com.mkbhdana.streamhive.data.db.MediaFileEntity

data class SourcePriorityOption(
    val id: String,
    val label: String
)

data class SourcePriorityConfig(
    val resolutionOrder: List<String> = emptyList(),
    val videoFormatOrder: List<String> = emptyList(),
    val decoderOrder: List<String> = emptyList(),
    val containerOrder: List<String> = emptyList()
) {
    val hasAnyPriority: Boolean
        get() = resolutionOrder.isNotEmpty() ||
            videoFormatOrder.isNotEmpty() ||
            decoderOrder.isNotEmpty() ||
            containerOrder.isNotEmpty()
}

data class SourceTraits(
    val resolution: String = SourcePriorityIds.UNKNOWN,
    val videoFormat: String = SourcePriorityIds.UNKNOWN,
    val decoder: String = SourcePriorityIds.UNKNOWN,
    val container: String = SourcePriorityIds.UNKNOWN
)

data class AppliedSourcePriority(
    val categoryLabel: String,
    val valueLabel: String
)

data class SourcePriorityResult(
    val files: List<MediaFileEntity>,
    val totalFiles: Int,
    val isConfigured: Boolean,
    val applied: List<AppliedSourcePriority> = emptyList()
) {
    val isFiltered: Boolean
        get() = files.size < totalFiles
}

object SourcePriorityIds {
    const val UNKNOWN = "unknown"

    const val RESOLUTION_2160P = "2160p"
    const val RESOLUTION_1080P = "1080p"
    const val RESOLUTION_720P = "720p"
    const val RESOLUTION_480P = "480p"
    const val RESOLUTION_360P = "360p"
    const val RESOLUTION_240P = "240p"

    const val FORMAT_DOLBY_VISION = "dolby_vision"
    const val FORMAT_HDR = "hdr"
    const val FORMAT_HDR10 = "hdr10"
    const val FORMAT_HDR10_PLUS = "hdr10_plus"
    const val FORMAT_HLG = "hlg"
    const val FORMAT_SDR = "sdr"

    const val DECODER_HEVC = "hevc"
    const val DECODER_H265 = "h265"
    const val DECODER_AVC = "avc"
    const val DECODER_H264 = "h264"
    const val DECODER_AV1 = "av1"
    const val DECODER_VP9 = "vp9"
    const val DECODER_MPEG2 = "mpeg2"
    const val DECODER_XVID = "xvid"
    const val DECODER_VC1 = "vc1"
}

object SourcePriorityOptions {
    val resolutions = listOf(
        SourcePriorityOption(SourcePriorityIds.RESOLUTION_2160P, "2160p"),
        SourcePriorityOption(SourcePriorityIds.RESOLUTION_1080P, "1080p"),
        SourcePriorityOption(SourcePriorityIds.RESOLUTION_720P, "720p"),
        SourcePriorityOption(SourcePriorityIds.RESOLUTION_480P, "480p"),
        SourcePriorityOption(SourcePriorityIds.RESOLUTION_360P, "360p"),
        SourcePriorityOption(SourcePriorityIds.RESOLUTION_240P, "240p"),
        SourcePriorityOption(SourcePriorityIds.UNKNOWN, "Unknown")
    )

    val videoFormats = listOf(
        SourcePriorityOption(SourcePriorityIds.FORMAT_DOLBY_VISION, "Dolby Vision"),
        SourcePriorityOption(SourcePriorityIds.FORMAT_HDR, "HDR"),
        SourcePriorityOption(SourcePriorityIds.FORMAT_HDR10, "HDR10"),
        SourcePriorityOption(SourcePriorityIds.FORMAT_HDR10_PLUS, "HDR10+"),
        SourcePriorityOption(SourcePriorityIds.FORMAT_HLG, "HLG"),
        SourcePriorityOption(SourcePriorityIds.FORMAT_SDR, "SDR"),
        SourcePriorityOption(SourcePriorityIds.UNKNOWN, "Unknown")
    )

    val decoders = listOf(
        SourcePriorityOption(SourcePriorityIds.DECODER_HEVC, "HEVC"),
        SourcePriorityOption(SourcePriorityIds.DECODER_H265, "H265"),
        SourcePriorityOption(SourcePriorityIds.DECODER_AVC, "AVC"),
        SourcePriorityOption(SourcePriorityIds.DECODER_H264, "H264"),
        SourcePriorityOption(SourcePriorityIds.DECODER_AV1, "AV1"),
        SourcePriorityOption(SourcePriorityIds.DECODER_VP9, "VP9"),
        SourcePriorityOption(SourcePriorityIds.DECODER_MPEG2, "MPEG2"),
        SourcePriorityOption(SourcePriorityIds.DECODER_XVID, "XVID"),
        SourcePriorityOption(SourcePriorityIds.DECODER_VC1, "VC1"),
        SourcePriorityOption(SourcePriorityIds.UNKNOWN, "Unknown")
    )

    val containers = listOf(
        SourcePriorityOption("mkv", "MKV"),
        SourcePriorityOption("mp4", "MP4"),
        SourcePriorityOption("3gp", "3GP"),
        SourcePriorityOption("webm", "WEBM"),
        SourcePriorityOption("avi", "AVI"),
        SourcePriorityOption("mov", "MOV"),
        SourcePriorityOption("m4v", "M4V"),
        SourcePriorityOption("ts", "TS"),
        SourcePriorityOption("m2ts", "M2TS"),
        SourcePriorityOption("mpg", "MPG"),
        SourcePriorityOption("mpeg", "MPEG"),
        SourcePriorityOption("vob", "VOB"),
        SourcePriorityOption(SourcePriorityIds.UNKNOWN, "Unknown")
    )

    fun labelFor(options: List<SourcePriorityOption>, id: String): String {
        return options.firstOrNull { it.id == id }?.label ?: id
    }

    fun sanitizeOrder(order: List<String>, options: List<SourcePriorityOption>): List<String> {
        val allowed = options.map { it.id }.toSet()
        return order
            .map { normalizeId(it) }
            .filter { it in allowed }
            .distinct()
    }

    private fun normalizeId(value: String): String {
        return value.trim()
            .lowercase()
            .replace("+", "_plus")
            .replace(Regex("""[\s.-]+"""), "_")
    }
}

object SourcePriorityFilter {
    fun filter(
        files: List<MediaFileEntity>,
        config: SourcePriorityConfig
    ): SourcePriorityResult {
        if (!config.hasAnyPriority || files.size <= 1) {
            return SourcePriorityResult(
                files = files,
                totalFiles = files.size,
                isConfigured = config.hasAnyPriority
            )
        }

        var current = files
        val applied = mutableListOf<AppliedSourcePriority>()

        current = applyCategory(
            files = current,
            order = config.resolutionOrder,
            options = SourcePriorityOptions.resolutions,
            categoryLabel = "Resolution",
            valueSelector = { parse(it).resolution },
            applied = applied
        )
        current = applyCategory(
            files = current,
            order = config.videoFormatOrder,
            options = SourcePriorityOptions.videoFormats,
            categoryLabel = "Video format",
            valueSelector = { parse(it).videoFormat },
            applied = applied
        )
        current = applyCategory(
            files = current,
            order = config.decoderOrder,
            options = SourcePriorityOptions.decoders,
            categoryLabel = "Decoder",
            valueSelector = { parse(it).decoder },
            applied = applied
        )
        current = applyCategory(
            files = current,
            order = config.containerOrder,
            options = SourcePriorityOptions.containers,
            categoryLabel = "Container",
            valueSelector = { parse(it).container },
            applied = applied
        )

        return SourcePriorityResult(
            files = current,
            totalFiles = files.size,
            isConfigured = true,
            applied = applied
        )
    }

    fun parse(file: MediaFileEntity): SourceTraits {
        return SourceTraits(
            resolution = parseResolution(file),
            videoFormat = parseVideoFormat(file.name),
            decoder = parseDecoder(file.name),
            container = parseContainer(file)
        )
    }

    private fun applyCategory(
        files: List<MediaFileEntity>,
        order: List<String>,
        options: List<SourcePriorityOption>,
        categoryLabel: String,
        valueSelector: (MediaFileEntity) -> String,
        applied: MutableList<AppliedSourcePriority>
    ): List<MediaFileEntity> {
        val sanitizedOrder = SourcePriorityOptions.sanitizeOrder(order, options)
        if (sanitizedOrder.isEmpty() || files.size <= 1) return files

        val grouped = files.groupBy(valueSelector)
        val winningValue = sanitizedOrder.firstOrNull { grouped[it]?.isNotEmpty() == true }
            ?: return files

        applied.add(
            AppliedSourcePriority(
                categoryLabel = categoryLabel,
                valueLabel = SourcePriorityOptions.labelFor(options, winningValue)
            )
        )
        return grouped[winningValue].orEmpty()
    }

    private fun parseResolution(file: MediaFileEntity): String {
        val name = file.name.lowercase()
        Regex("""(?<!\d)(2160|1080|720|480|360|240)\s*p(?!\w)""")
            .find(name)
            ?.let { return "${it.groupValues[1]}p" }

        if (Regex("""(?:^|[\s._\-\[\]()])(?:4k|uhd)(?:$|[\s._\-\[\]()])""").containsMatchIn(name)) {
            return SourcePriorityIds.RESOLUTION_2160P
        }

        val height = file.videoHeight ?: return SourcePriorityIds.UNKNOWN
        return when {
            height >= 2000 -> SourcePriorityIds.RESOLUTION_2160P
            height >= 1000 -> SourcePriorityIds.RESOLUTION_1080P
            height >= 700 -> SourcePriorityIds.RESOLUTION_720P
            height >= 470 -> SourcePriorityIds.RESOLUTION_480P
            height >= 350 -> SourcePriorityIds.RESOLUTION_360P
            height >= 230 -> SourcePriorityIds.RESOLUTION_240P
            else -> SourcePriorityIds.UNKNOWN
        }
    }

    private fun parseVideoFormat(name: String): String {
        val normalized = searchable(name)
        return when {
            Regex("""\b(dolby vision|dovi|dv)\b""").containsMatchIn(normalized) ->
                SourcePriorityIds.FORMAT_DOLBY_VISION
            Regex("""\bhdr\s*10\s*(\+|plus)(?=\s|$)""").containsMatchIn(normalized) ||
                Regex("""\bhdr10(\+|plus)(?=\s|$)""").containsMatchIn(normalized) ->
                SourcePriorityIds.FORMAT_HDR10_PLUS
            Regex("""\bhdr\s*10\b""").containsMatchIn(normalized) ||
                Regex("""\bhdr10\b""").containsMatchIn(normalized) ->
                SourcePriorityIds.FORMAT_HDR10
            Regex("""\bhlg\b""").containsMatchIn(normalized) ->
                SourcePriorityIds.FORMAT_HLG
            Regex("""\bhdr\b""").containsMatchIn(normalized) ->
                SourcePriorityIds.FORMAT_HDR
            Regex("""\bsdr\b""").containsMatchIn(normalized) ->
                SourcePriorityIds.FORMAT_SDR
            else -> SourcePriorityIds.UNKNOWN
        }
    }

    private fun parseDecoder(name: String): String {
        val normalized = searchable(name)
        return when {
            Regex("""\bhevc\b""").containsMatchIn(normalized) -> SourcePriorityIds.DECODER_HEVC
            Regex("""\b(h\s*265|x265|h265)\b""").containsMatchIn(normalized) -> SourcePriorityIds.DECODER_H265
            Regex("""\bavc\b""").containsMatchIn(normalized) -> SourcePriorityIds.DECODER_AVC
            Regex("""\b(h\s*264|x264|h264)\b""").containsMatchIn(normalized) -> SourcePriorityIds.DECODER_H264
            Regex("""\bav1\b""").containsMatchIn(normalized) -> SourcePriorityIds.DECODER_AV1
            Regex("""\bvp9\b""").containsMatchIn(normalized) -> SourcePriorityIds.DECODER_VP9
            Regex("""\bmpeg\s*2\b""").containsMatchIn(normalized) ||
                Regex("""\bmpeg2\b""").containsMatchIn(normalized) -> SourcePriorityIds.DECODER_MPEG2
            Regex("""\bxvid\b""").containsMatchIn(normalized) -> SourcePriorityIds.DECODER_XVID
            Regex("""\bvc\s*1\b""").containsMatchIn(normalized) ||
                Regex("""\bvc1\b""").containsMatchIn(normalized) -> SourcePriorityIds.DECODER_VC1
            else -> SourcePriorityIds.UNKNOWN
        }
    }

    private fun parseContainer(file: MediaFileEntity): String {
        val extension = file.fileExtension
            ?: file.name.substringAfterLast('.', missingDelimiterValue = "")
        val normalized = extension.trim().lowercase()
        return SourcePriorityOptions.containers.firstOrNull { it.id == normalized }?.id
            ?: SourcePriorityIds.UNKNOWN
    }

    private fun searchable(name: String): String {
        return name.lowercase()
            .replace(Regex("""[._\-\[\]()]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
