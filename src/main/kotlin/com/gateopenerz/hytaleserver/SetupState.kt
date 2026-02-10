package com.gateopenerz.hytaleserver

internal data class SetupState(
    val channel: String,
    val version: String,
    val resolvedVersion: String?,
    val detectedVersion: String?,
    val serverJarSize: Long?,
    val serverJarLastModified: Long?,
    val serverJarSha256: String?,
    val assetsZipSize: Long?,
    val assetsZipLastModified: Long?,
    val assetsZipSha256: String?
)
