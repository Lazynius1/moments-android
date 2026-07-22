package com.moments.android.models

import java.util.Date

/** Sesión de login activa (definida en iOS en LoginActivityView.swift). */
data class LoginSession(
    val id: String,
    val device: String,
    val location: String,
    val ipAddress: String,
    val timestamp: Date,
    val isActive: Boolean,
    val deviceIdentifier: String? = null,
    val isSuspicious: Boolean = false,
    val isNewDevice: Boolean = false,
    val suspiciousReason: String? = null,
)
