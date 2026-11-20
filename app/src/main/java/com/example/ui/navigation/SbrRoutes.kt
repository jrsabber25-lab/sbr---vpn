package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface SbrRoute {
    @Serializable
    data object Splash : SbrRoute

    @Serializable
    data object Onboarding : SbrRoute

    @Serializable
    data object Home : SbrRoute

    @Serializable
    data object Servers : SbrRoute

    @Serializable
    data object Connection : SbrRoute

    @Serializable
    data object Stats : SbrRoute

    @Serializable
    data object Profile : SbrRoute

    @Serializable
    data object Subscription : SbrRoute

    @Serializable
    data object Settings : SbrRoute

    @Serializable
    data class Legal(val isPrivacyPolicy: Boolean = true) : SbrRoute

    @Serializable
    data object Support : SbrRoute
}
