package id.alfindotech.kasir.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SkyBlue = Color(0xFF03A9F4)
val SkyBlueDark = Color(0xFF0288D1)
val SkyBlueLight = Color(0xFFE1F5FE)

private val Colors = lightColorScheme(
    primary = SkyBlue,
    onPrimary = Color.White,
    secondary = SkyBlueDark,
    background = Color(0xFFF7FBFD),
    surface = Color.White
)

@Composable
fun KasirTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
