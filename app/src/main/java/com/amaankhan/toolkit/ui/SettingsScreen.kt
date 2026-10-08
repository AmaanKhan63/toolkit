package com.amaankhan.toolkit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.amaankhan.toolkit.R
import com.amaankhan.toolkit.ToolkitViewModel
import com.amaankhan.toolkit.data.*

@Composable fun SettingsScreen(model: ToolkitViewModel, preferences: Preferences, onBack: () -> Unit) {
    var page by rememberSaveable { mutableStateOf("Settings") }
    var clearHistory by remember { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf(preferences.displayName) }
    var workspace by rememberSaveable { mutableStateOf(preferences.workspace) }
    val back = { if (page == "Settings") onBack() else page = "Settings" }
    BackHandler(onBack = back)
    Scaffold(topBar = { ScreenHeader(page, back) }, bottomBar = {
        if (page == "User profile") ActionBar { PrimaryAction("Save profile") { model.setProfile(name, workspace); page = "Settings" } }
    }) { padding ->
        key(page) {
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (page) {
                "Settings" -> {
                    item { SettingsRow("User profile", preferences.displayName.ifBlank { "Your local workspace" }, Glyph.USER) { page = "User profile" } }
                    item { SettingsRow("Themes", "${preferences.palette.label} · ${preferences.theme.label}", Glyph.PALETTE) { page = "Themes" } }
                    item { SectionTitle("Data & storage") }
                    item { InfoCard("Stored on your device", "Your profile, themes and saved tools stay in Toolkit’s private storage. Finished files go to the folder you choose. No account is needed.") }
                    item { OutlinedButton(onClick = model::clearRecents, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Clear recent tools") } }
                    item { OutlinedButton(onClick = { clearHistory = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Clear file history") } }
                    item { SettingsRow("Open-source licenses", "Fonts, icons & libraries", Glyph.FILE) { page = "Licenses" } }
                    item { EmptyNote("Toolkit · On-device document tools") }
                }
                "Themes" -> {
                    item { Text("A space that feels like yours", style = MaterialTheme.typography.titleMedium) }
                    item { EmptyNote("Choose a palette. Your appearance setting applies to every palette.") }
                    com.amaankhan.toolkit.data.Palette.entries.forEach { palette -> item {
                        val colors = toolkitColors(palette, isDarkTheme(preferences.theme))
                        Surface(onClick = { model.setPalette(palette) }, shape = RoundedCornerShape(20.dp), border = BorderStroke(if (preferences.palette == palette) 2.dp else 1.dp, if (preferences.palette == palette) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { Text(palette.label, style = MaterialTheme.typography.titleMedium); Text(palette.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    RadioButton(selected = preferences.palette == palette, onClick = null)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(colors.background, colors.surface, colors.primaryContainer, colors.primary).forEach { color -> Box(Modifier.size(28.dp).background(color, CircleShape)) }
                                }
                            }
                        }
                    } }
                    item { SectionTitle("Appearance") }
                    item { ChoiceField("Appearance", preferences.theme.label, ThemeChoice.entries.map { it.label }) { value -> model.setTheme(ThemeChoice.entries.first { it.label == value }) } }
                }
                "User profile" -> {
                    item { InfoCard("Your local profile", "A name and workspace label for this device. This does not create an online account.") }
                    item { ToolkitTextField(name, { name = it.take(60) }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                    item { ToolkitTextField(workspace, { workspace = it.take(60) }, label = { Text("Workspace label") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                }
                "Licenses" -> item {
                    val context = LocalContext.current
                    val notices = remember {
                        context.resources.openRawResource(R.raw.third_party_notices).bufferedReader().use { it.readText() } + "\n\n" +
                            context.assets.open("office-notices.txt").bufferedReader().use { it.readText() } + "\n\n" +
                            context.assets.open("download-reader-notices.txt").bufferedReader().use { it.readText() }
                    }
                    Text(notices, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        }
    }
    if (clearHistory) AlertDialog(onDismissRequest = { clearHistory = false }, title = { Text("Clear file history?") }, text = { Text("This removes the list inside Toolkit. Your saved files remain in their folders.") }, confirmButton = { TextButton(onClick = { model.clearExportHistory(); clearHistory = false }) { Text("Clear history") } }, dismissButton = { TextButton(onClick = { clearHistory = false }) { Text("Cancel") } })
}
@Composable private fun SettingsRow(title: String, subtitle: String, glyph: Glyph, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GlyphIcon(glyph, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            GlyphIcon(Glyph.CHEVRON)
        }
    }
}
