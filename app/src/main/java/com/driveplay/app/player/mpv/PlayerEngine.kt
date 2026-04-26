package com.driveplay.app.player.mpv

/**
 * Player engine selection. The user can switch between ExoPlayer (Media3),
 * MPV, or External (system "Open with" intent) at runtime.
 */
enum class PlayerEngine {
    EXO_PLAYER,
    MPV,
    EXTERNAL
}
