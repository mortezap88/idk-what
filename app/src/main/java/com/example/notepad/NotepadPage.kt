package com.example.notepad

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.notepad.ui.screens.MainNotepadScreen
import com.example.notepad.ui.theme.DarkNotepadColors
import com.example.notepad.ui.theme.LightNotepadColors
import com.example.notepad.ui.theme.LocalNotepadColors
import com.example.notepad.ui.viewmodel.NotepadViewModel
import com.example.ui.theme.LocalIsDarkTheme

/**
 * Entry point for the Notes page of the app shell.
 */
@Composable
fun NotepadPage(modifier: Modifier = Modifier) {
    val isDark = LocalIsDarkTheme.current
    val viewModel: NotepadViewModel = viewModel()
    LaunchedEffect(Unit) {
        viewModel.geminiPreferences.refreshFromSharedSettings()
    }
    // The notepad keeps its own visual identity but follows the app's light/dark setting.
    val colors = if (isDark) DarkNotepadColors else LightNotepadColors
    CompositionLocalProvider(LocalNotepadColors provides colors) {
        MainNotepadScreen(viewModel = viewModel, modifier = modifier)
    }
}
