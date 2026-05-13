package com.mkbhdana.streamhive.settings

import com.mkbhdana.streamhive.data.db.MediaFileEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SourcePriorityFilterTest {
    @Test
    fun `higher resolution wins when available`() {
        val files = listOf(
            file("uhd", "Movie.2160p.SDR.HEVC.mkv"),
            file("hd", "Movie.1080p.SDR.HEVC.mkv")
        )

        val result = SourcePriorityFilter.filter(
            files,
            SourcePriorityConfig(
                resolutionOrder = listOf("2160p", "1080p", "720p")
            )
        )

        assertEquals(listOf("uhd"), result.files.map { it.id })
    }

    @Test
    fun `lower resolution remains when it is the only configured match`() {
        val files = listOf(
            file("hd", "Movie.1080p.SDR.HEVC.mkv")
        )

        val result = SourcePriorityFilter.filter(
            files,
            SourcePriorityConfig(
                resolutionOrder = listOf("2160p", "1080p", "720p")
            )
        )

        assertEquals(listOf("hd"), result.files.map { it.id })
    }

    @Test
    fun `video format breaks ties after resolution`() {
        val files = listOf(
            file("dv", "Movie.2160p.DV.HEVC.mkv"),
            file("sdr", "Movie.2160p.SDR.HEVC.mkv"),
            file("hd", "Movie.1080p.DV.HEVC.mkv")
        )

        val result = SourcePriorityFilter.filter(
            files,
            SourcePriorityConfig(
                resolutionOrder = listOf("2160p", "1080p"),
                videoFormatOrder = listOf("dolby_vision", "sdr", "unknown")
            )
        )

        assertEquals(listOf("dv"), result.files.map { it.id })
    }

    @Test
    fun `no priority sequence lists every file`() {
        val files = listOf(
            file("uhd", "Movie.2160p.DV.HEVC.mkv"),
            file("hd", "Movie.1080p.SDR.H264.mp4")
        )

        val result = SourcePriorityFilter.filter(files, SourcePriorityConfig())

        assertEquals(listOf("uhd", "hd"), result.files.map { it.id })
    }

    @Test
    fun `unknown participates when it is in the sequence`() {
        val files = listOf(
            file("unknown", "Movie.Release.mkv"),
            file("hd", "Movie.1080p.SDR.H264.mp4")
        )

        val result = SourcePriorityFilter.filter(
            files,
            SourcePriorityConfig(
                resolutionOrder = listOf("unknown", "1080p")
            )
        )

        assertEquals(listOf("unknown"), result.files.map { it.id })
    }

    private fun file(
        id: String,
        name: String,
        height: Int? = null
    ) = MediaFileEntity(
        id = id,
        name = name,
        mimeType = "video/${name.substringAfterLast('.', "mkv")}",
        driveId = "drive",
        fileExtension = name.substringAfterLast('.', "").takeIf { it.isNotBlank() },
        videoHeight = height
    )
}
