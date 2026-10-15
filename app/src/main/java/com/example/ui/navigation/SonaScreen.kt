package com.example.ui.navigation

sealed class SonaScreen {
    object Chat : SonaScreen()
    object Settings : SonaScreen()
    object Permissions : SonaScreen()
    object Diagnostics : SonaScreen()
    object Auth : SonaScreen()
}
