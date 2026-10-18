package io.github.rahulraj94391.keeper.domain.model

/** Whether this device can do strong (Class 3) biometric authentication right now. */
enum class BiometricAvailability {
    Available,

    /** No strong biometric sensor. */
    NoHardware,

    /** A sensor exists but no fingerprint or face is enrolled. */
    NotEnrolled,

    /** Temporarily unavailable, or blocked by a security update or policy. */
    Unavailable,
}
