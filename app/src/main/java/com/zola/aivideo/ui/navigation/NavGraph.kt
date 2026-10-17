package com.zola.aivideo.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zola.aivideo.ui.glossary.GlossaryScreen
import com.zola.aivideo.ui.glossary.GlossaryViewModel
import com.zola.aivideo.ui.home.HomeScreen
import com.zola.aivideo.ui.importer.ImportResultScreen
import com.zola.aivideo.ui.importer.ImportViewModel
import com.zola.aivideo.ui.models.ModelManagementScreen
import com.zola.aivideo.ui.models.ModelManagementViewModel
import com.zola.aivideo.ui.settings.LlmSettingsScreen
import com.zola.aivideo.ui.settings.LlmSettingsViewModel
import com.zola.aivideo.ui.subtitles.SubtitleEditScreen
import com.zola.aivideo.ui.subtitles.SubtitleEditViewModel
import com.zola.aivideo.ui.transcribe.TranscribeScreen
import com.zola.aivideo.ui.transcribe.TranscribeViewModel
import com.zola.aivideo.ui.translate.SubtitleImportScreen
import com.zola.aivideo.ui.translate.TranslateConfigScreen
import com.zola.aivideo.ui.translate.TranslateConfigViewModel
import com.zola.aivideo.ui.translate.TranslateProgressScreen
import com.zola.aivideo.ui.translate.TranslateProgressViewModel
import com.zola.aivideo.ui.dub.DubConfigScreen
import com.zola.aivideo.ui.dub.DubConfigViewModel
import com.zola.aivideo.ui.dub.DubPreviewScreen
import com.zola.aivideo.ui.dub.DubPreviewViewModel
import com.zola.aivideo.ui.dub.DubProgressScreen
import com.zola.aivideo.ui.dub.DubProgressViewModel
import com.zola.aivideo.ui.dub.TtsModelScreen
import com.zola.aivideo.ui.dub.TtsModelViewModel
import com.zola.aivideo.ui.dub.TtsSettingsScreen
import com.zola.aivideo.ui.dub.TtsSettingsViewModel
import com.zola.aivideo.ui.synthesize.SynthesizeConfigScreen
import com.zola.aivideo.ui.synthesize.SynthesizeConfigViewModel
import com.zola.aivideo.ui.settings.SettingsScreen
import com.zola.aivideo.ui.settings.AboutScreen
import com.zola.aivideo.ui.settings.LicenseTextScreen
import com.zola.aivideo.ui.stock.StockSettingsScreen
import com.zola.aivideo.ui.stock.StockSettingsViewModel
import com.zola.aivideo.ui.stock.StockSearchScreen
import com.zola.aivideo.ui.stock.StockSearchViewModel
import com.zola.aivideo.ui.stock.StockReplaceScreen
import com.zola.aivideo.ui.stock.StockReplaceViewModel
import com.zola.aivideo.ui.ytdlp.YtDlpScreen
import com.zola.aivideo.ui.ytdlp.YtDlpViewModel
import com.zola.aivideo.data.ytdlp.YtDlpEnvironment
import androidx.core.content.FileProvider
import com.zola.aivideo.ui.tasks.CreateTaskDialog
import com.zola.aivideo.ui.tasks.TaskCreateViewModel
import com.zola.aivideo.ui.tasks.TaskDetailScreen
import com.zola.aivideo.ui.tasks.TaskDetailViewModel
import com.zola.aivideo.ui.tasks.TaskListScreen
import com.zola.aivideo.ui.tasks.TaskListViewModel
import com.zola.aivideo.ui.translate.TranslateResultScreen
import com.zola.aivideo.ui.translate.TranslateResultViewModel
import com.zola.aivideo.ui.translate.TranslationFlowHolder
import com.zola.aivideo.data.task.TaskStage
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * 翻译流程内存传递器的 EntryPoint：供无需 ViewModel 的 composable 获取 @Singleton。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TranslationFlowHolderEntryPoint {
    fun holder(): TranslationFlowHolder
}



/**
 * yt-dlp 运行环境的 EntryPoint：供 YtDlpScreen 获取 @Singleton。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface YtDlpEnvironmentEntryPoint {
    fun environment(): YtDlpEnvironment
}

/** 路由定义：集中管理，避免各处硬编码字符串。 */
object Routes {
    const val HOME = "home"
    const val IMPORT_RESULT = "import_result"

    /** 语音模型管理（M2）。 */
    const val MODEL_MANAGEMENT = "model_management"

    /** 语音转写（M2）：携带 M1 导入的本地视频文件绝对路径。 */
    const val TRANSCRIBE = "transcribe"

    /** 字幕预览与编辑（M2）：分段经 TranscriptionResultHolder 内存传递。 */
    const val SUBTITLE_EDIT = "subtitle_edit"

    /** LLM 设置（M3）：BYOK 配置。 */
    const val LLM_SETTINGS = "llm_settings"

    /** 术语表管理（M3）。 */
    const val GLOSSARY = "glossary"

    /** 翻译配置（M3）：分段经 TranslationFlowHolder 内存传递。 */
    const val TRANSLATE_CONFIG = "translate_config"

    /** 流式翻译进度（M3）。 */
    const val TRANSLATE_PROGRESS = "translate_progress"

    /** 翻译结果预览（M3）。 */
    const val TRANSLATE_RESULT = "translate_result"

    /** 纯字幕独立导入（M3）：srt/vtt/ass。 */
    const val SUBTITLE_IMPORT = "subtitle_import"

    /** TTS 模型管理（M4）：Kokoro 模型下载。 */
    const val TTS_MODELS = "tts_models"

    /** 云 TTS 设置（M4）：OpenAI 兼容 BYOK 配置。 */
    const val TTS_SETTINGS = "tts_settings"

    /** 配音配置（M4）：分段经 DubFlowHolder 内存传递。 */
    const val DUB_CONFIG = "dub_config"

    /** 配音合成进度（M4）：前台协程逐段合成，可取消。 */
    const val DUB_PROGRESS = "dub_progress"

    /** 配音试听（M4）：MediaPlayer 播放对齐后的配音轨。 */
    const val DUB_PREVIEW = "dub_preview"

    /** 任务中心（M5）。 */
    const val TASK_LIST = "task_list"

    /** 任务详情（M5）：携带任务 id。 */
    const val TASK_DETAIL = "task_detail"

    /** 合成配置（M5）：携带任务 id。 */
    const val SYNTHESIZE_CONFIG = "synthesize_config"

    /** 设置页（M6）：全应用设置统一入口。 */
    const val SETTINGS = "settings"

    /** 关于与开源许可（M6）：GPL 声明 + 第三方组件清单。 */
    const val ABOUT = "about"

    /** 许可证全文查看（M6）：携带 assets 路径与标题。 */
    const val LICENSE_TEXT = "license_text"

    /** 素材库设置（M6）：Pexels/Pixabay Key（BYOK）。 */
    const val STOCK_SETTINGS = "stock_settings"

    /** 素材搜索（M6）：携带任务 id。 */
    const val STOCK_SEARCH = "stock_search"

    /** 素材替换（M6）：携带任务 id 与已下载文件名。 */
    const val STOCK_REPLACE = "stock_replace"

    /** 链接下载（M6）：yt-dlp 批量下载。 */
    const val YTDLP = "ytdlp_download"

    /** 素材搜索路由：携带任务 id。 */
    fun stockSearch(taskId: Long): String = "$STOCK_SEARCH?taskId=$taskId"

    /** 素材替换路由：携带任务 id 与文件名。 */
    fun stockReplace(taskId: Long, fileName: String): String =
        "$STOCK_REPLACE?taskId=$taskId&fileName=${URLEncoder.encode(fileName, Charsets.UTF_8.name())}"

    /** 许可证全文路由：携带 assets 路径与标题。 */
    fun licenseText(assetPath: String, title: String): String =
        "$LICENSE_TEXT?path=${URLEncoder.encode(assetPath, Charsets.UTF_8.name())}" +
            "&title=${URLEncoder.encode(title, Charsets.UTF_8.name())}"

    /** 导入结果页路由：携带系统文件选择器返回的 Uri 字符串。 */
    fun importResult(uriString: String): String =
        "$IMPORT_RESULT?uri=${URLEncoder.encode(uriString, Charsets.UTF_8.name())}"

    /** 转写页路由：携带本地视频文件绝对路径。 */
    fun transcribe(filePath: String): String =
        "$TRANSCRIBE?filePath=${URLEncoder.encode(filePath, Charsets.UTF_8.name())}"

    /** 任务详情路由：携带任务 id。 */
    fun taskDetail(taskId: Long): String = "$TASK_DETAIL/$taskId"

    /** 合成配置路由：携带任务 id。 */
    fun synthesizeConfig(taskId: Long): String = "$SYNTHESIZE_CONFIG/$taskId"

    /**
     * 带可选 taskId 的路由（M5 任务模式）：
     * 有 taskId 时配置页从任务目录读写，确认后回任务详情。
     */
    fun withTaskId(base: String, taskId: Long?): String =
        if (taskId != null && taskId > 0L) "$base?taskId=$taskId" else base
}

/**
 * 影译导航图。
 *
 * M1 路由：HOME（导入入口）/ IMPORT_RESULT（元信息页）。
 * M2 新增：MODEL_MANAGEMENT（模型管理）/ TRANSCRIBE（转写）/ SUBTITLE_EDIT（字幕编辑）。
 * M3 新增：LLM_SETTINGS / GLOSSARY / TRANSLATE_CONFIG / TRANSLATE_PROGRESS /
 *   TRANSLATE_RESULT / SUBTITLE_IMPORT。
 * M4 新增：TTS_MODELS / TTS_SETTINGS / DUB_CONFIG / DUB_PROGRESS / DUB_PREVIEW。
 * M5 新增：TASK_LIST / TASK_DETAIL / SYNTHESIZE_CONFIG。
 * M6 新增：SETTINGS / ABOUT / LICENSE_TEXT / STOCK_SETTINGS / STOCK_SEARCH /
 *   STOCK_REPLACE / YTDLP。
 */
@Composable
fun AiVideoNavGraph(
    startDestination: String = Routes.HOME,
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.HOME) {
            HomeScreen(
                onVideoPicked = { uri ->
                    navController.navigate(Routes.importResult(uri.toString()))
                },
                onOpenModels = { navController.navigate(Routes.MODEL_MANAGEMENT) },
                onOpenLlmSettings = { navController.navigate(Routes.LLM_SETTINGS) },
                onImportSubtitle = { navController.navigate(Routes.SUBTITLE_IMPORT) },
                onOpenTtsModels = { navController.navigate(Routes.TTS_MODELS) },
                onOpenTtsSettings = { navController.navigate(Routes.TTS_SETTINGS) },
                onOpenTasks = { navController.navigate(Routes.TASK_LIST) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = "${Routes.IMPORT_RESULT}?uri={uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType }),
        ) { backStackEntry ->
            val encoded = backStackEntry.arguments?.getString("uri").orEmpty()
            val uriString = URLDecoder.decode(encoded, Charsets.UTF_8.name())
            val viewModel: ImportViewModel = hiltViewModel()
            // M5：任务创建（行前检查 + 建任务）
            val taskCreateViewModel: TaskCreateViewModel = hiltViewModel()
            val pending = taskCreateViewModel.pendingCreate
            if (pending != null && pending.issues.isEmpty()) {
                // 无问题：直接建任务跳详情页（副作用放 LaunchedEffect，避免组合中导航）
                LaunchedEffect(pending) {
                    taskCreateViewModel.confirmCreate { id ->
                        navController.navigate(Routes.taskDetail(id))
                    }
                }
            }
            if (pending != null && pending.issues.isNotEmpty()) {
                CreateTaskDialog(
                    pending = pending,
                    hasBlocker = taskCreateViewModel.hasBlocker(),
                    onDismiss = taskCreateViewModel::dismiss,
                    onConfirm = {
                        taskCreateViewModel.confirmCreate { id ->
                            navController.navigate(Routes.taskDetail(id))
                        }
                    },
                )
            }
            ImportResultScreen(
                uriString = uriString,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onTranscribe = { filePath ->
                    navController.navigate(Routes.transcribe(filePath))
                },
                onCreateTask = { name, filePath ->
                    taskCreateViewModel.requestCreate(filePath, name)
                },
            )
        }
        composable(Routes.MODEL_MANAGEMENT) {
            val viewModel: ModelManagementViewModel = hiltViewModel()
            ModelManagementScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "${Routes.TRANSCRIBE}?filePath={filePath}",
            arguments = listOf(navArgument("filePath") { type = NavType.StringType }),
        ) { backStackEntry ->
            val encoded = backStackEntry.arguments?.getString("filePath").orEmpty()
            val filePath = URLDecoder.decode(encoded, Charsets.UTF_8.name())
            val viewModel: TranscribeViewModel = hiltViewModel()
            TranscribeScreen(
                videoPath = filePath,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNeedModel = { navController.navigate(Routes.MODEL_MANAGEMENT) },
                onDone = {
                    navController.navigate(Routes.SUBTITLE_EDIT) {
                        // 转写页出栈：字幕页返回时回到元信息页，避免重复转写
                        popUpTo(Routes.TRANSCRIBE) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = "${Routes.SUBTITLE_EDIT}?taskId={taskId}",
            arguments = listOf(
                navArgument("taskId") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
        ) {
            val viewModel: SubtitleEditViewModel = hiltViewModel()
            SubtitleEditScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNextStep = { navController.navigate(Routes.TRANSLATE_CONFIG) },
            )
        }
        // ---------- M3 ----------
        composable(Routes.LLM_SETTINGS) {
            val viewModel: LlmSettingsViewModel = hiltViewModel()
            LlmSettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.GLOSSARY) {
            val viewModel: GlossaryViewModel = hiltViewModel()
            GlossaryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SUBTITLE_IMPORT) {
            // TranslationFlowHolder 是 @Singleton：经 EntryPoint 从 ApplicationComponent 获取
            val appContext = LocalContext.current.applicationContext
            val holder = remember {
                EntryPointAccessors.fromApplication(
                    appContext,
                    TranslationFlowHolderEntryPoint::class.java,
                ).holder()
            }
            SubtitleImportScreen(
                holder = holder,
                onBack = { navController.popBackStack() },
                onParsed = { navController.navigate(Routes.TRANSLATE_CONFIG) },
            )
        }
        composable(
            route = "${Routes.TRANSLATE_CONFIG}?taskId={taskId}",
            arguments = listOf(
                navArgument("taskId") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
        ) {
            val viewModel: TranslateConfigViewModel = hiltViewModel()
            TranslateConfigScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenLlmSettings = { navController.navigate(Routes.LLM_SETTINGS) },
                onOpenGlossary = { navController.navigate(Routes.GLOSSARY) },
                onStartTranslate = {
                    navController.navigate(Routes.TRANSLATE_PROGRESS) {
                        // 配置页出栈：进度页返回时回到字幕编辑/导入页
                        popUpTo(Routes.TRANSLATE_CONFIG) { inclusive = true }
                    }
                },
                // M5 任务模式：确认后直接回任务详情页
                onTaskModeDone = { navController.popBackStack() },
            )
        }
        composable(Routes.TRANSLATE_PROGRESS) {
            val viewModel: TranslateProgressViewModel = hiltViewModel()
            TranslateProgressScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
            // 翻译完成 → 结果页（副作用放 LaunchedEffect，避免组合中导航）
            val done = viewModel.doneResult
            LaunchedEffect(done) {
                if (done != null) {
                    navController.navigate(Routes.TRANSLATE_RESULT) {
                        popUpTo(Routes.TRANSLATE_PROGRESS) { inclusive = true }
                    }
                }
            }
        }
        composable(Routes.TRANSLATE_RESULT) {
            val viewModel: TranslateResultViewModel = hiltViewModel()
            TranslateResultScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onStartDub = { navController.navigate(Routes.DUB_CONFIG) },
            )
        }
        // ---------- M4：TTS / 配音 ----------
        composable(Routes.TTS_MODELS) {
            val viewModel: TtsModelViewModel = hiltViewModel()
            TtsModelScreen(
                onBack = { navController.popBackStack() },
                viewModel = viewModel,
            )
        }
        composable(Routes.TTS_SETTINGS) {
            val viewModel: TtsSettingsViewModel = hiltViewModel()
            TtsSettingsScreen(
                onBack = { navController.popBackStack() },
                viewModel = viewModel,
            )
        }
        composable(
            route = "${Routes.DUB_CONFIG}?taskId={taskId}",
            arguments = listOf(
                navArgument("taskId") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
        ) {
            val viewModel: DubConfigViewModel = hiltViewModel()
            DubConfigScreen(
                onBack = { navController.popBackStack() },
                onStart = {
                    navController.navigate(Routes.DUB_PROGRESS) {
                        // 配置页出栈：进度页返回时回到翻译结果页
                        popUpTo(Routes.DUB_CONFIG) { inclusive = true }
                    }
                },
                onManageTtsModel = { navController.navigate(Routes.TTS_MODELS) },
                onTtsSettings = { navController.navigate(Routes.TTS_SETTINGS) },
                // M5 任务模式：确认后直接回任务详情页
                onTaskModeDone = { navController.popBackStack() },
                viewModel = viewModel,
            )
        }
        composable(Routes.DUB_PROGRESS) {
            val viewModel: DubProgressViewModel = hiltViewModel()
            DubProgressScreen(
                onBack = { navController.popBackStack() },
                onPreview = {
                    navController.navigate(Routes.DUB_PREVIEW) {
                        popUpTo(Routes.DUB_PROGRESS) { inclusive = true }
                    }
                },
                viewModel = viewModel,
            )
        }
        composable(Routes.DUB_PREVIEW) {
            val viewModel: DubPreviewViewModel = hiltViewModel()
            DubPreviewScreen(
                onBack = { navController.popBackStack() },
                viewModel = viewModel,
            )
        }
        // ---------- M5：任务中心 ----------
        composable(Routes.TASK_LIST) {
            val viewModel: TaskListViewModel = hiltViewModel()
            TaskListScreen(
                onBack = { navController.popBackStack() },
                onOpenTask = { id -> navController.navigate(Routes.taskDetail(id)) },
                viewModel = viewModel,
            )
        }
        composable(
            route = "${Routes.TASK_DETAIL}/{taskId}",
            arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
        ) {
            val viewModel: TaskDetailViewModel = hiltViewModel()
            TaskDetailScreen(
                onBack = { navController.popBackStack() },
                onContinueStage = { taskId, stage ->
                    when (stage) {
                        // 转写无配置页，NEEDS_INPUT 直接重跑 Worker
                        TaskStage.TRANSCRIBE -> viewModel.retryStage(stage)
                        TaskStage.TRANSLATE ->
                            navController.navigate(Routes.withTaskId(Routes.TRANSLATE_CONFIG, taskId))
                        TaskStage.DUB ->
                            navController.navigate(Routes.withTaskId(Routes.DUB_CONFIG, taskId))
                        TaskStage.SYNTHESIZE ->
                            navController.navigate(Routes.synthesizeConfig(taskId))
                    }
                },
                onProofread = { taskId ->
                    navController.navigate(Routes.withTaskId(Routes.SUBTITLE_EDIT, taskId))
                },
                onOpenStock = { taskId ->
                    navController.navigate(Routes.stockSearch(taskId))
                },
                viewModel = viewModel,
            )
        }
        composable(
            route = "${Routes.SYNTHESIZE_CONFIG}/{taskId}",
            arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
        ) {
            val viewModel: SynthesizeConfigViewModel = hiltViewModel()
            SynthesizeConfigScreen(
                onBack = { navController.popBackStack() },
                onConfirmDone = { navController.popBackStack() },
                viewModel = viewModel,
            )
        }
        // ---------- M6：设置 / 关于 / 素材 ----------
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenModels = { navController.navigate(Routes.MODEL_MANAGEMENT) },
                onOpenTtsModels = { navController.navigate(Routes.TTS_MODELS) },
                onOpenLlmSettings = { navController.navigate(Routes.LLM_SETTINGS) },
                onOpenTtsSettings = { navController.navigate(Routes.TTS_SETTINGS) },
                onOpenGlossary = { navController.navigate(Routes.GLOSSARY) },
                onOpenStockSettings = { navController.navigate(Routes.STOCK_SETTINGS) },
                onOpenYtDlp = { navController.navigate(Routes.YTDLP) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
            )
        }
        composable(Routes.ABOUT) {
            AboutScreen(
                onBack = { navController.popBackStack() },
                onViewLicenseText = { assetPath, title ->
                    navController.navigate(Routes.licenseText(assetPath, title))
                },
            )
        }
        composable(
            route = "${Routes.LICENSE_TEXT}?path={path}&title={title}",
            arguments = listOf(
                navArgument("path") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val path = URLDecoder.decode(
                backStackEntry.arguments?.getString("path").orEmpty(),
                Charsets.UTF_8.name(),
            )
            val title = URLDecoder.decode(
                backStackEntry.arguments?.getString("title").orEmpty(),
                Charsets.UTF_8.name(),
            )
            LicenseTextScreen(
                assetPath = path,
                title = title,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.STOCK_SETTINGS) {
            val viewModel: StockSettingsViewModel = hiltViewModel()
            StockSettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.YTDLP) {
            val appContext = LocalContext.current.applicationContext
            val viewModel: YtDlpViewModel = hiltViewModel()
            // YtDlpEnvironment 经 Hilt 取（@Singleton）；界面需要它做 Wi-Fi 判断
            val envEntryPoint = remember {
                EntryPointAccessors.fromApplication(
                    appContext,
                    YtDlpEnvironmentEntryPoint::class.java,
                )
            }
            YtDlpScreen(
                viewModel = viewModel,
                environment = envEntryPoint.environment(),
                onBack = { navController.popBackStack() },
                // 下载完成 → 经 FileProvider 进导入页（与系统选择器同链路）
                onImportFile = { file ->
                    val uri = FileProvider.getUriForFile(
                        appContext,
                        "${appContext.packageName}.fileprovider",
                        file,
                    )
                    navController.navigate(Routes.importResult(uri.toString()))
                },
            )
        }
        composable(
            route = "${Routes.STOCK_SEARCH}?taskId={taskId}",
            arguments = listOf(
                navArgument("taskId") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId")?.toLongOrNull() ?: 0L
            val viewModel: StockSearchViewModel = hiltViewModel()
            StockSearchScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onReplaceClip = { fileName ->
                    navController.navigate(Routes.stockReplace(taskId, fileName))
                },
            )
        }
        composable(
            route = "${Routes.STOCK_REPLACE}?taskId={taskId}&fileName={fileName}",
            arguments = listOf(
                navArgument("taskId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("fileName") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId")?.toLongOrNull() ?: 0L
            val viewModel: StockReplaceViewModel = hiltViewModel()
            StockReplaceScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                // 替换成功后回到任务详情（素材搜索页出栈）
                onDone = {
                    if (taskId > 0L) {
                        navController.popBackStack(Routes.taskDetail(taskId), false)
                    } else {
                        navController.popBackStack()
                    }
                },
            )
        }
    }
}
