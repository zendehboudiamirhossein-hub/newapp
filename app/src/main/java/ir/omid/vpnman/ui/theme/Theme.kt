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

private val Colors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF7DE3C3),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF07110E),
    secondary = androidx.compose.ui.graphics.Color(0xFF8FA7FF),
    background = androidx.compose.ui.graphics.Color(0xFF080B14),
    surface = androidx.compose.ui.graphics.Color(0xFF101725),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF151E2F),
    onBackground = androidx.compose.ui.graphics.Color(0xFFF3F7FB),
    onSurface = androidx.compose.ui.graphics.Color(0xFFF3F7FB),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFA9B4C4),
    error = androidx.compose.ui.graphics.Color(0xFFFF7E86)
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
