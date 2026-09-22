package ir.omid.vpnman.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// NOTE: this project referenced res/font/vazirmatn_{regular,medium,bold}.ttf, but those
// .ttf files were never actually included in the repo/zip, so R.font.vazirmatn_* did not
// exist and the app could not compile. Falling back to the system default font family
// (Roboto, which still renders Persian via the OS's Noto Naskh Arabic fallback) keeps
// the build working right now.
//
// To get the real Vazirmatn look back:
//   1. Download the three weights from https://fonts.google.com/specimen/Vazirmatn
//      (Regular, Medium, Bold) as .ttf files.
//   2. Put them in app/src/main/res/font/ named exactly
//      vazirmatn_regular.ttf, vazirmatn_medium.ttf, vazirmatn_bold.ttf
//      (lowercase, no spaces/hyphens — Android resource names require that).
//   3. Replace the `Vazir` definition below with:
//        private val Vazir = FontFamily(
//            Font(R.font.vazirmatn_regular, FontWeight.Normal),
//            Font(R.font.vazirmatn_medium, FontWeight.Medium),
//            Font(R.font.vazirmatn_bold, FontWeight.Bold)
//        )
//      (and re-add `import ir.omid.vpnman.R`).
private val Vazir = FontFamily.Default

// Brightened for readability: background/surfaces lifted a few steps so cards and dividers
// separate clearly from the page, and onSurfaceVariant (used for nearly all secondary/label
// text throughout the app) raised well above the old low-contrast gray-blue.
private val Colors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF8CEBCC),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF07110E),
    secondary = androidx.compose.ui.graphics.Color(0xFFA3B7FF),
    background = androidx.compose.ui.graphics.Color(0xFF10151F),
    surface = androidx.compose.ui.graphics.Color(0xFF1A2333),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF232E42),
    onBackground = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    onSurface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFCBD4E3),
    error = androidx.compose.ui.graphics.Color(0xFFFF8B92)
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Vazir, fontWeight = FontWeight.Bold, fontSize = 30.sp),
    headlineSmall = TextStyle(fontFamily = Vazir, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = Vazir, fontWeight = FontWeight.Bold, fontSize = 19.sp),
    titleMedium = TextStyle(fontFamily = Vazir, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = Vazir, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = Vazir, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    labelLarge = TextStyle(fontFamily = Vazir, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Vazir, fontWeight = FontWeight.Medium, fontSize = 12.sp)
)

@Composable
fun VpnManTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = AppTypography, content = content)
}
