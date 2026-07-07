package com.kypeli.flightsoverhead.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.kypeli.flightsoverhead.R

val provider =
    GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs,
    )

val HankenGroteskFont = GoogleFont("Hanken Grotesk")
val InterFont = GoogleFont("Inter")
val JetBrainsMonoFont = GoogleFont("JetBrains Mono")

val HankenGroteskFontFamily =
    FontFamily(
        Font(googleFont = HankenGroteskFont, fontProvider = provider),
        Font(googleFont = HankenGroteskFont, fontProvider = provider, weight = FontWeight.Bold),
        Font(googleFont = HankenGroteskFont, fontProvider = provider, weight = FontWeight.SemiBold),
    )

val InterFontFamily =
    FontFamily(
        Font(googleFont = InterFont, fontProvider = provider),
        Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.Normal),
        Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.SemiBold),
    )

val JetBrainsMonoFontFamily =
    FontFamily(
        Font(googleFont = JetBrainsMonoFont, fontProvider = provider),
        Font(googleFont = JetBrainsMonoFont, fontProvider = provider, weight = FontWeight.Medium),
        Font(googleFont = JetBrainsMonoFont, fontProvider = provider, weight = FontWeight.SemiBold),
    )

// Set of Material typography styles to start with
val Typography =
    Typography(
        headlineLarge =
            TextStyle(
                fontFamily = HankenGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                letterSpacing = (-0.02).sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = HankenGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                letterSpacing = (-0.02).sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = HankenGroteskFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                color = Color.Black,
            ),
        labelMedium =
            TextStyle(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
    )

val DataMonoStyle =
    TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        color = OnSurface,
        fontSize = 14.sp,
    )
