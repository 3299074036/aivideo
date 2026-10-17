package com.zola.aivideo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zola.aivideo.ui.navigation.AiVideoNavGraph
import com.zola.aivideo.ui.navigation.Routes
import com.zola.aivideo.ui.theme.AiVideoTheme
import dagger.hilt.android.AndroidEntryPoint

/** 主 Activity：承载全 App 的 Compose 导航。 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 冷启动时若携带系统分享的视频文件，后续里程碑接入导入流程（M1 仅首页）
        val sharedVideo = intent.extractSharedVideo()
        setContent {
            AiVideoTheme {
                AiVideoNavGraph(
                    startDestination = Routes.HOME,
                )
            }
        }
    }

    /** 从 ACTION_SEND 分享意图中提取视频 Uri（M1 占位，后续里程碑使用）。 */
    private fun Intent.extractSharedVideo(): String? {
        if (action != Intent.ACTION_SEND) return null
        if (type?.startsWith("video/") != true) return null
        val uri: android.net.Uri? = if (android.os.Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM)
        }
        return uri?.toString()
    }
}
