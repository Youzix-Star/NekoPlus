package love.miao.yun.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ── 语义化颜色 ──
object MiaoColors {
    // 主色
    val Blue = Color(0xFF2196F3)
    val BlueDark = Color(0xFF90CAF9)

    // 卡片/背景
    val CardBgLight = Color.White
    val CardBgDark = Color(0xFF1E1E1E)

    // 主文字
    val TextPrimaryLight = Color(0xFF212121)
    val TextPrimaryDark = Color(0xFFE0E0E0)

    // 副文字
    val TextSecondaryLight = Color(0xFF9E9E9E)
    val TextSecondaryDark = Color(0xFF888888)

    // 三级文字
    val TextTertiaryLight = Color(0xFF616161)
    val TextTertiaryDark = Color(0xFFAAAAAA)

    // 分隔线/边框
    val BorderLight = Color(0xFFE0E0E0)
    val BorderDark = Color(0xFF333333)

    // 页面背景
    val BgLight = Color(0xFFF5F5F5)
    val BgDark = Color(0xFF121212)

    // TopAppBar
    val TopBarLight = Color.White
    val TopBarDark = Color(0xFF1A1A1A)

    // Switch
    val SwitchOffLight = Color(0xFFE0E0E0)
    val SwitchOffDark = Color(0xFF444444)

    // 功能色
    val Green = Color(0xFF43A047)
    val Orange = Color(0xFFFFA000)
    val Red = Color(0xFFE53935)
    val Purple = Color(0xFF8E24AA)
    val Teal = Color(0xFF00897B)
    val Pink = Color(0xFFE91E63)

    @Composable
    fun cardBg() = if (isSystemInDarkTheme()) CardBgDark else CardBgLight

    @Composable
    fun textPrimary() = if (isSystemInDarkTheme()) TextPrimaryDark else TextPrimaryLight

    @Composable
    fun textSecondary() = if (isSystemInDarkTheme()) TextSecondaryDark else TextSecondaryLight

    @Composable
    fun textTertiary() = if (isSystemInDarkTheme()) TextTertiaryDark else TextTertiaryLight

    @Composable
    fun border() = if (isSystemInDarkTheme()) BorderDark else BorderLight

    @Composable
    fun bg() = if (isSystemInDarkTheme()) BgDark else BgLight

    @Composable
    fun topBar() = if (isSystemInDarkTheme()) TopBarDark else TopBarLight

    @Composable
    fun switchOff() = if (isSystemInDarkTheme()) SwitchOffDark else SwitchOffLight

    @Composable
    fun blue() = if (isSystemInDarkTheme()) BlueDark else Blue
}

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2196F3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    onPrimaryContainer = Color(0xFF0D47A1),
    secondary = Color(0xFF2196F3),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3F2FD),
    onSecondaryContainer = Color(0xFF0D47A1),
    tertiary = Color(0xFF616161),
    onTertiary = Color.White,
    surface = Color.White,
    onSurface = Color(0xFF212121),
    background = Color.White,
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF757575),
    outline = Color(0xFFE0E0E0),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0D47A1),
    primaryContainer = Color(0xFF1565C0),
    onPrimaryContainer = Color(0xFFBBDEFB),
    secondary = Color(0xFF90CAF9),
    onSecondary = Color(0xFF0D47A1),
    secondaryContainer = Color(0xFF1565C0),
    onSecondaryContainer = Color(0xFFBBDEFB),
    tertiary = Color(0xFFAAAAAA),
    onTertiary = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE0E0E0),
    background = Color(0xFF121212),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFF888888),
    outline = Color(0xFF333333),
)

@Composable
fun MiaoAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
