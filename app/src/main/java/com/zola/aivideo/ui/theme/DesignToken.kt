package com.zola.aivideo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 影译 Design Token：全 App 唯一的圆角 / 间距 / 字阶来源。
 *
 * 铁律：页面禁止各自定义一套圆角、间距、字号，一律引用此处。
 * 后续如需主题切换（深色纯黑等），通过 CompositionLocal 整体替换。
 */

/** 圆角 token。 */
data class AiVideoCorners(
    val xs: RoundedCornerShape = RoundedCornerShape(4.dp),
    val sm: RoundedCornerShape = RoundedCornerShape(8.dp),
    val md: RoundedCornerShape = RoundedCornerShape(12.dp),
    val lg: RoundedCornerShape = RoundedCornerShape(16.dp),
    val xl: RoundedCornerShape = RoundedCornerShape(24.dp),
    val full: RoundedCornerShape = RoundedCornerShape(50),
)

/** 间距 token。 */
data class AiVideoSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
)

/** 字阶语义别名：页面按语义取用，不直接写 sp。 */
data class AiVideoType(
    val display: TextStyle = AiVideoTypography.displayLarge,
    val headline: TextStyle = AiVideoTypography.headlineMedium,
    val title: TextStyle = AiVideoTypography.titleLarge,
    val titleSmall: TextStyle = AiVideoTypography.titleMedium,
    val body: TextStyle = AiVideoTypography.bodyLarge,
    val bodySmall: TextStyle = AiVideoTypography.bodyMedium,
    val caption: TextStyle = AiVideoTypography.bodySmall,
    val label: TextStyle = AiVideoTypography.labelLarge,
)

/** 聚合 token。 */
data class AiVideoTokens(
    val corners: AiVideoCorners = AiVideoCorners(),
    val spacing: AiVideoSpacing = AiVideoSpacing(),
    val type: AiVideoType = AiVideoType(),
)

val LocalAiVideoTokens = staticCompositionLocalOf { AiVideoTokens() }

/** 非 Compose 上下文（如 Preview 外部）的快捷访问。 */
val DefaultAiVideoTokens = AiVideoTokens()
