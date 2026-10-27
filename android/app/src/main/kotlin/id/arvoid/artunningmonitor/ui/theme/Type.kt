package id.arvoid.artunningmonitor.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Level type NomadKit: Headline / Body / Label (lihat spesimen di
 * docs/design/nomadkit.md). DESIGN.md tidak menyebut family font ->
 * pakai default platform (SansSerif).
 */
private val Base = Typography()

val NomadTypography = Typography(
    headlineMedium = Base.headlineMedium.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.25).sp,
    ),
    titleMedium = Base.titleMedium.copy(
        fontWeight = FontWeight.SemiBold,
    ),
    bodyMedium = Base.bodyMedium.copy(
        lineHeight = 20.sp,
    ),
    labelMedium = Base.labelMedium.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
    ),
)
