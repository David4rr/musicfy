package com.example.musicfy.playback.custom

data class CustomStreamResult(
    val streamUrl: String,
    val decryptionKey: String? = null,
    val keyId: String? = null,
    val isDash: Boolean = false,
    val source: String? = null,
    /** an HLS playlist rather than a file (source modules) */
    val isHls: Boolean = false,
    /** Widevine-protected: trades the device CDM's challenge for a license (source modules) */
    val widevineLicense: com.example.musicfy.module.LicenseServer? = null,
)
