package online.draran.billing.core.model

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** App-wide settings stored on the device. */
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    /** Android 12+: take colours from the wallpaper instead of the brand palette. */
    val dynamicColor: Boolean = false,
)
