package com.amaankhan.toolkit.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.amaankhan.toolkit.R

// Phosphor vectors with decorative duotone fills removed, bundled offline. See third-party/Phosphor-LICENSE.txt.
enum class Glyph(val resource: Int) {
    GRID(R.drawable.ph_squares_four),
    IMAGE(R.drawable.ph_images),
    LAYERS(R.drawable.ph_stack),
    COMPRESS(R.drawable.ph_arrows_in),
    LOCK(R.drawable.ph_lock_key),
    DOWNLOAD(R.drawable.ph_download_simple),
    FOLDER(R.drawable.ph_folder_simple),
    FILE(R.drawable.ph_file_text),
    SEARCH(R.drawable.ph_magnifying_glass),
    SETTINGS(R.drawable.ph_gear_six),
    SUN(R.drawable.ph_sun),
    MOON(R.drawable.ph_moon),
    STAR(R.drawable.ph_bookmark_simple),
    UP(R.drawable.ph_arrow_up),
    DOWN(R.drawable.ph_caret_down),
    TRASH(R.drawable.ph_trash),
    PLUS(R.drawable.ph_plus),
    BACK(R.drawable.ph_arrow_left),
    CLOSE(R.drawable.ph_x),
    CHECK(R.drawable.ph_check),
    CHEVRON(R.drawable.ph_caret_right),
    FLOW(R.drawable.ph_flow_arrow),
    CONVERT(R.drawable.ph_arrows_left_right),
    CREATE(R.drawable.ph_file_plus),
    USER(R.drawable.ph_user_circle),
    PALETTE(R.drawable.ph_palette),
    EYE(R.drawable.ph_eye),
    ZOOM(R.drawable.ph_magnifying_glass_plus),
    ZOOM_OUT(R.drawable.ph_magnifying_glass_minus)
}
@Composable fun GlyphIcon(glyph: Glyph, description: String? = null, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Icon(painterResource(glyph.resource), description, modifier, tint)
}
