package io.github.xhr666.opendictionary

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.ui.unit.sp
import io.github.xhr666.opendictionary.ui.*
import io.github.xhr666.opendictionary.data.UserDb
import io.github.xhr666.opendictionary.download.ApkDownloader
import io.github.xhr666.opendictionary.download.DownloadService
import io.github.xhr666.opendictionary.update.UpdateChecker
import io.github.xhr666.opendictionary.update.UpdateInfo

class MainActivity : ComponentActivity() {
    var pendingWord by androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingWord = intent.getStringExtra("word")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { io.github.xhr666.opendictionary.Diagnostics.install(this) }
        runCatching { com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext) }
        runCatching { androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false) }
        runCatching {
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
        Tts.init(this)
        io.github.xhr666.opendictionary.ui.UiState.load(this)
        Thread { io.github.xhr666.opendictionary.data.LexiconDb.get(this) }.start()
        setContent {
            val base = LocalDensity.current
            val scale = io.github.xhr666.opendictionary.ui.UiState.fontScale
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * scale)) { AppRoot() }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("od_prefs", Context.MODE_PRIVATE) }
    val db = remember { UserDb.get(context) }
    val onboardingKey = "onboarded_major_" + BuildConfig.VERSION_NAME.split(".").take(2).joinToString(".")
    var onboarded by remember { mutableStateOf(prefs.getBoolean(onboardingKey, false)) }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateSheetShown by remember { mutableStateOf(false) }
    val activity = context as? MainActivity
    val initialWord = remember { activity?.intent?.getStringExtra("word") }
    val saveRoute = rememberSaveable { mutableStateOf<String?>(null) }
    val stack = remember {
        val restored = saveRoute.value?.let { decodeRoute(it) }
        val first = when {
            restored != null -> restored
            !initialWord.isNullOrBlank() -> Route.Word(initialWord)
            else -> Route.Home
        }
        mutableStateListOf<Route>(Route.Home).apply { if (first != Route.Home) add(first) }
    }
    LaunchedEffect(stack.lastOrNull()) { stack.lastOrNull()?.let { saveRoute.value = encodeRoute(it) } }
    val pendingWord = activity?.pendingWord
    LaunchedEffect(pendingWord) {
        if (!pendingWord.isNullOrBlank() && stack.lastOrNull() != Route.Word(pendingWord)) { stack.add(Route.Word(pendingWord)); activity.pendingWord = null }
    }

    fun push(route: Route) {
        if (stack.lastOrNull() == route) return
        // 页内“返回”常调用 onNavigate(父路由)：如果父路由就在栈顶下一层，直接出栈而不是再压一份，避免返回栈重复膨胀
        if (stack.size >= 2 && stack[stack.size - 2] == route) { stack.removeAt(stack.lastIndex); return }
        stack.add(route)
    }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    fun checkUpdates(manual: Boolean) {
        val last = prefs.getLong("last_check", 0L)
        if (!manual && !prefs.getBoolean("auto_update", true)) return
        if (!manual && System.currentTimeMillis() - last < 24 * 60 * 60 * 1000L) return
        Thread {
            val found = runCatching {
                UpdateChecker(currentVersionCode = BuildConfig.VERSION_CODE, currentMinSdk = 33).check().getOrNull()
            }.getOrNull()
            prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()
            if (found != null) { update = found; updateSheetShown = true }
        }.start()
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        DownloadService.createChannel(context)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    if (!onboarded) {
        OnboardingScreen(
            onDone = {
                prefs.edit().putBoolean(onboardingKey, true).putBoolean("onboarded", true).apply()
                onboarded = true
                checkUpdates(false)
            },
            onDecline = { (context as? ComponentActivity)?.finish() }
        )
        return
    }

    var backProgress by remember { mutableFloatStateOf(0f) }
    BackHandler(enabled = stack.size > 1) { pop() }
    PredictiveBackHandler(enabled = stack.size > 1) { progress ->
        try {
            progress.collect { event -> backProgress = event.progress }
        } finally {
            backProgress = 0f
        }
        if (stack.size > 1) pop()
    }
    LaunchedEffect(Unit) { checkUpdates(false) }

    val route = stack.last()
    LaunchedEffect(Unit) { db.seedDemoIfNeeded() }
    LaunchedEffect(route) {
        when (route) {
            is Route.Word -> db.recordQuery(route.q)
            is Route.Search -> db.recordQuery(route.q)
            else -> {}
        }
    }
    val appBackdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize().graphicsLayer {
        translationX = -backProgress * size.width * 0.18f
        scaleX = 1f - backProgress * 0.04f
        scaleY = 1f - backProgress * 0.04f
    }) {
    Box(Modifier.fillMaxSize().layerBackdrop(appBackdrop), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
    Box(Modifier.fillMaxHeight().widthIn(max = 920.dp)) {
    when (route) {
        is Route.Home -> HomeScreen(onNavigate = { nav -> if (nav != Route.Home) push(nav) })
        is Route.Word -> RealWordScreen(query = route.q, onNavigate = { nav ->
            if (nav == Route.Home) { stack.clear(); stack.add(Route.Home) } else push(nav)
        })
        is Route.Search -> SearchScreen(query = route.q, onNavigate = { nav ->
            if (nav == Route.Home) { stack.clear(); stack.add(Route.Home) } else push(nav)
        })
        is Route.SearchInput -> SearchInputScreen(onNavigate = { nav ->
            if (nav == Route.Home) { stack.clear(); stack.add(Route.Home) } else push(nav)
        }, onBack = { stack.clear(); stack.add(Route.Home) })
        is Route.Settings -> SettingsScreen(onNavigate = { nav -> push(nav) }, onCheckUpdate = { checkUpdates(true) })
        is Route.Profile -> ProfileScreen(onNavigate = { nav -> if (nav == Route.Home) { stack.clear(); stack.add(Route.Home) } else push(nav) })
        is Route.Day -> DayDetailScreen(route.day, onNavigate = { nav -> if (nav == Route.Home) { stack.clear(); stack.add(Route.Home) } else push(nav) })
        is Route.Review -> ReviewScreen(onNavigate = { nav -> if (nav == Route.Home) { stack.clear(); stack.add(Route.Home) } else push(nav) })
        is Route.FoldReview -> FoldReviewScreen(onNavigate = { nav -> if (nav == Route.Home) { stack.clear(); stack.add(Route.Home) } else push(nav) })
        is Route.CardMode -> CardModeScreen(route.bookId, onNavigate = { nav -> if (nav == Route.Home) { stack.clear(); stack.add(Route.Home) } else push(nav) })
        is Route.Media -> MediaScreen(onNavigate = { nav -> if (nav == Route.Home) { stack.clear(); stack.add(Route.Home) } else push(nav) })
        is Route.Daily -> DailyScreen(onNavigate = { nav -> if (nav == Route.Home) { stack.clear(); stack.add(Route.Home) } else push(nav) })
        is Route.Notes -> NotesScreen(onNavigate = { nav -> if (nav == Route.Home) { stack.clear(); stack.add(Route.Home) } else push(nav) })
        is Route.Sources -> SourcesScreen(onNavigate = { nav -> if (nav == Route.Home) { stack.clear(); stack.add(Route.Home) } else push(nav) })
        is Route.Diagnostics -> DiagnosticsScreen(onNavigate = { nav -> if (nav == Route.Home) { stack.clear(); stack.add(Route.Home) } else push(nav) })
        is Route.CustomWords -> CustomWordsScreen(onNavigate = { nav -> if (nav == Route.Home) { stack.clear(); stack.add(Route.Home) } else push(nav) })
        is Route.SpeechSources -> SpeechSourcesScreen(onNavigate = { nav -> if (nav == Route.Home) { stack.clear(); stack.add(Route.Home) } else push(nav) })
        is Route.DocReader -> DocReaderScreen(route.docId, onNavigate = { nav -> if (nav == Route.Home) { stack.clear(); stack.add(Route.Home) } else push(nav) })
        is Route.TranslationPractice -> TranslationPracticeScreen(onNavigate = { nav -> if (nav == Route.Home) { stack.clear(); stack.add(Route.Home) } else push(nav) })
        is Route.ListeningPractice -> ListeningPracticeScreen(onNavigate = { nav -> if (nav == Route.Home) { stack.clear(); stack.add(Route.Home) } else push(nav) })
        is Route.PdfReader -> PdfReaderScreen(onNavigate = { nav -> if (nav == Route.Home) { stack.clear(); stack.add(Route.Home) } else push(nav) })
        is Route.EpubReader -> EpubReaderScreen(onNavigate = { nav -> if (nav == Route.Home) { stack.clear(); stack.add(Route.Home) } else push(nav) })
        is Route.MediaPlayer -> MediaPlayerScreen(route.mediaId, onNavigate = { nav -> if (nav == Route.Home) { stack.clear(); stack.add(Route.Home) } else push(nav) })
        else -> GenericRoute(route, onNavigate = { nav ->
            if (nav == Route.Home) { stack.clear(); stack.add(Route.Home) } else push(nav)
        }, onOpenUrl = ::openUrl)
    }
    }
    }
    val dockIndex = when (route) {
        is Route.Home -> 0
        is Route.Search -> if ((route as Route.Search).q.isBlank()) 1 else -1
        is Route.Word -> 1
        is Route.Learning -> 2
        is Route.Profile -> 3
        else -> -1
    }
    if (dockIndex >= 0) {
        Box(Modifier.align(androidx.compose.ui.Alignment.BottomCenter)) {
            FloatingGlassDock(current = dockIndex, backdrop = appBackdrop) { nav ->
                when (nav) {
                    is Route.Home, is Route.Profile, is Route.Learning -> { stack.clear(); stack.add(Route.Home); if (nav != Route.Home) stack.add(nav) }
                    is Route.Search -> { stack.clear(); stack.add(Route.Home); stack.add(nav) }
                    else -> {}
                }
            }
        }
    }
    }

    update?.let { info ->
        if (updateSheetShown) {
            DialogCard(visible = true, onDismiss = { updateSheetShown = false }) {
                Text("发现新版本 ${info.manifest.versionName}", style = OD.H2)
                Spacer(Modifier.height(8.dp))
                Text(info.manifest.notes.ifBlank { "预发布版本更新" }, style = OD.Cap.copy(lineHeight = 18.sp))
                Spacer(Modifier.height(8.dp))
                Text("版本号 ${info.manifest.versionCode} · 约 ${info.manifest.size / 1024 / 1024} MB · 下载后校验 sha256", style = OD.Micro)
                Spacer(Modifier.height(14.dp))
                ODButton("下载安装包", onClick = { downloadUpdate(context, info); updateSheetShown = false })
                Spacer(Modifier.height(8.dp))
                ODButton("稍后", onClick = { updateSheetShown = false }, primary = false)
            }
        }
    }
}

@Composable
private fun GenericRoute(route: Route, onNavigate: (Route) -> Unit, onOpenUrl: (String) -> Unit) {
    when (route) {
        is Route.Llm -> LlmRealScreen(onNavigate)
        is Route.Academic -> AcademicScreen(onNavigate)
        is Route.Fonts -> FontsScreen(onNavigate)
        is Route.Offline -> OfflineScreen(onNavigate)
        is Route.Privacy -> PrivacyScreen(onNavigate)
        is Route.Clipboard -> ClipboardScreen(onNavigate)
        is Route.Experiment -> ExperimentScreen(onNavigate)
        is Route.Help -> HelpScreen(onNavigate, onOpenUrl)
        is Route.Licenses -> LicensesScreen(onNavigate, onOpenUrl)
        is Route.Support -> SupportScreen(onNavigate, onOpenUrl)
        is Route.Learning -> LearningScreen(onNavigate)
        is Route.Writing -> CompositionScreen(onNavigate)
        is Route.Listening -> DictationScreen(onNavigate)
        is Route.Reading -> ReadingRssScreen(onNavigate)
        is Route.ExamReading -> ExamReadingScreen(onNavigate)
        is Route.About -> AboutScreen(onNavigate, onOpenUrl)
        is Route.Notifications -> NotificationsScreen(onNavigate)
        is Route.Accessibility -> AccessibilityScreen(onNavigate)
        is Route.Backup -> BackupScreen(onNavigate)
        is Route.DictSource -> DictSourceScreen(onNavigate)
        is Route.DictPopup -> DictPopupScreen(onNavigate)
        else -> {}
    }
}

private fun downloadUpdate(context: Context, info: UpdateInfo) {
    ApkDownloader.download(context, info) { _, _ -> }
}

private fun encodeRoute(route: Route): String = when (route) {
    is Route.Home -> "home"
    is Route.Word -> "word:" + route.q
    is Route.Search -> "search:" + route.q
    is Route.SearchInput -> "search_input"
    is Route.Settings -> "settings"
    is Route.Llm -> "llm"
    is Route.Academic -> "academic"
    is Route.Fonts -> "fonts"
    is Route.Offline -> "offline"
    is Route.Privacy -> "privacy"
    is Route.Clipboard -> "clipboard"
    is Route.Experiment -> "experiment"
    is Route.Help -> "help"
    is Route.Licenses -> "licenses"
    is Route.Support -> "support"
    is Route.Learning -> "learning"
    is Route.Writing -> "writing"
    is Route.Listening -> "listening"
    is Route.Reading -> "reading"
    is Route.About -> "about"
    is Route.Notifications -> "notifications"
    is Route.Accessibility -> "accessibility"
    is Route.Backup -> "backup"
    is Route.DictSource -> "dict_source"
    is Route.DictPopup -> "dict_popup"
    is Route.Profile -> "profile"
    is Route.Day -> "day:" + route.day
    is Route.Review -> "review"
    is Route.FoldReview -> "fold_review"
    is Route.Media -> "media"
    is Route.MediaPlayer -> "media_player:" + route.mediaId
    is Route.Daily -> "daily"
    is Route.ExamReading -> "exam_reading"
    is Route.Notes -> "notes"
    is Route.CardMode -> "card_mode:" + route.bookId
    is Route.DocReader -> "doc_reader:" + route.docId
    is Route.TranslationPractice -> "translation_practice"
    is Route.Sources -> "sources"
    is Route.Diagnostics -> "diagnostics"
    is Route.CustomWords -> "custom_words"
    is Route.SpeechSources -> "speech_sources"
    is Route.ListeningPractice -> "listening_practice"
    is Route.PdfReader -> "pdf_reader"
    is Route.EpubReader -> "epub_reader"
}

private fun decodeRoute(key: String): Route {
    val name = key.substringBefore(':')
    val arg = key.substringAfter(':', "")
    return when (name) {
        "home" -> Route.Home
        "word" -> Route.Word(arg)
        "search" -> Route.Search(arg)
        "search_input" -> Route.SearchInput
        "settings" -> Route.Settings
        "llm" -> Route.Llm
        "academic" -> Route.Academic
        "fonts" -> Route.Fonts
        "offline" -> Route.Offline
        "privacy" -> Route.Privacy
        "clipboard" -> Route.Clipboard
        "experiment" -> Route.Experiment
        "help" -> Route.Help
        "licenses" -> Route.Licenses
        "support" -> Route.Support
        "learning" -> Route.Learning
        "writing" -> Route.Writing
        "listening" -> Route.Listening
        "reading" -> Route.Reading
        "about" -> Route.About
        "notifications" -> Route.Notifications
        "accessibility" -> Route.Accessibility
        "backup" -> Route.Backup
        "dict_source" -> Route.DictSource
        "dict_popup" -> Route.DictPopup
        "profile" -> Route.Profile
        "day" -> Route.Day(arg)
        "review" -> Route.Review
        "fold_review" -> Route.FoldReview
        "media" -> Route.Media
        "media_player" -> Route.MediaPlayer(arg.toLongOrNull() ?: -1L)
        "daily" -> Route.Daily
        "exam_reading" -> Route.ExamReading
        "notes" -> Route.Notes
        "card_mode" -> Route.CardMode(arg.toLongOrNull() ?: -1L)
        "doc_reader" -> Route.DocReader(arg.toLongOrNull() ?: -1L)
        "translation_practice" -> Route.TranslationPractice
        "sources" -> Route.Sources
        "diagnostics" -> Route.Diagnostics
        "custom_words" -> Route.CustomWords
        "speech_sources" -> Route.SpeechSources
        "listening_practice" -> Route.ListeningPractice
        "pdf_reader" -> Route.PdfReader
        "epub_reader" -> Route.EpubReader
        else -> Route.Home
    }
}
