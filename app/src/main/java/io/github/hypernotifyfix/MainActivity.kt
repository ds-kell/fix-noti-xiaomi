package io.github.hypernotifyfix

import io.github.hypernotifyfix.core.t
import android.Manifest
import android.os.Build
import android.os.Bundle
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.hypernotifyfix.core.AppContainer
import io.github.hypernotifyfix.core.AppLanguage
import io.github.hypernotifyfix.core.I18n
import io.github.hypernotifyfix.core.model.OptimizationSession
import io.github.hypernotifyfix.core.model.OperationResult
import io.github.hypernotifyfix.core.model.ResultStatus
import io.github.hypernotifyfix.core.model.RiskLevel
import io.github.hypernotifyfix.core.model.SnapshotValue
import io.github.hypernotifyfix.core.system.AutostartState
import io.github.hypernotifyfix.core.system.FcmState
import io.github.hypernotifyfix.toolkit.ToolkitScreen
import io.github.hypernotifyfix.toolkit.ToolkitViewModel
import java.text.DateFormat
import java.util.Date

private val Indigo = Color(0xFF4F46E5)
private val Mint = Color(0xFF10B981)
private val Amber = Color(0xFFF59E0B)
private val Ink = Color(0xFF172033)
private val Canvas = Color(0xFFF7F8FC)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as HyperNotifyApplication).container
        setContent {
            val colors = lightColorScheme(
                primary = Indigo, onPrimary = Color.White, secondary = Mint,
                background = Canvas, surface = Color.White, onSurface = Ink,
                surfaceVariant = Color(0xFFEEF0F6), outline = Color(0xFFD8DCE8)
            )
            MaterialTheme(colorScheme = colors, shapes = Shapes(
                small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(26.dp)
            )) {
                val vm: MainViewModel = viewModel { MainViewModel(container) }
                val toolkit: ToolkitViewModel = viewModel { ToolkitViewModel(container) }
                HyperNotifyApp(vm, toolkit, container)
            }
        }
    }
}

private data class Destination(val tab: Int, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HyperNotifyApp(vm: MainViewModel, toolkit: ToolkitViewModel, container: AppContainer) {
    val state by vm.state.collectAsStateWithLifecycle()
    val toolkitState by toolkit.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showGuide) { showGuide = false }
    val snackbarHostState = remember { SnackbarHostState() }
    // Autostart/battery/WRITE_SETTINGS are changed in system screens; re-read when the user comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshSetupState(); toolkit.refreshLocal() }
    LaunchedEffect(state.message) {
        state.message?.let { message -> snackbarHostState.showSnackbar(message); vm.clearMessage() }
    }
    LaunchedEffect(toolkitState.message) {
        toolkitState.message?.let { message -> snackbarHostState.showSnackbar(message); toolkit.clearMessage() }
    }
    val destinations = listOf(
        // Tab ids stay stable for GuideTab; "Công cụ" (5) is shown second.
        Destination(0, t("Trang chủ", "Home"), Icons.Outlined.Home, Icons.Filled.Home),
        Destination(5, t("Công cụ", "Tools"), Icons.Outlined.Handyman, Icons.Filled.Handyman),
        Destination(1, t("Ứng dụng", "Apps"), Icons.Outlined.Apps, Icons.Filled.Apps),
        Destination(2, t("Xem trước", "Preview"), Icons.AutoMirrored.Outlined.FactCheck, Icons.AutoMirrored.Filled.FactCheck),
        Destination(3, t("Lịch sử", "History"), Icons.Outlined.History, Icons.Filled.History),
        Destination(4, t("Cài đặt", "Settings"), Icons.Outlined.Settings, Icons.Filled.Settings)
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = { if (showGuide) IconButton({ showGuide = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Quay lại", "Back")) } },
                actions = { if (!showGuide) IconButton({ showGuide = true }) { Icon(Icons.AutoMirrored.Outlined.HelpOutline, t("Hướng dẫn sử dụng", "User guide"), tint = Indigo) } },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Same layers as the adaptive launcher icon (Compose cannot paint a layer-list).
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))) {
                            Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
                            Image(painterResource(R.drawable.ic_launcher_foreground), "HyperOS Toolkit", Modifier.fillMaxSize())
                        }
                        Column {
                            Text("HyperOS Toolkit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(t("Thông báo · app rác · trợ lý AI", "Notifications · bloatware · AI assistant"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 3.dp) {
                destinations.forEach { destination ->
                    val index = destination.tab
                    NavigationBarItem(
                        selected = tab == index && !showGuide,
                        onClick = { tab = index; showGuide = false },
                        icon = { Icon(if (tab == index) destination.selectedIcon else destination.icon, destination.label) },
                        label = { Text(destination.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                        alwaysShowLabel = false,
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Indigo.copy(alpha = .12f), selectedIconColor = Indigo, selectedTextColor = Indigo)
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (showGuide) GuideScreen(state) { target -> tab = target; showGuide = false } else when (tab) {
                0 -> HomeScreen(state, vm, onSelectApps = { tab = 1 }, onOpenGuide = { showGuide = true })
                1 -> AppsScreen(state, query, { query = it }, vm::select, vm::selectAll, vm::restorePackage, vm::restoreAll, container.catalog::icon)
                2 -> PreviewScreen(state, vm)
                3 -> HistoryScreen(state, vm::restore)
                5 -> ToolkitScreen(toolkit, state.apps.map { it.packageName }.toSet()) { preset, packages -> vm.selectPreset(preset.title.toString(), packages); tab = 1 }
                else -> {
                    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.setAurogonKeepAlive(true) }
                    SettingsScreen(state, vm) { enabled ->
                        // The keep-alive notification is only visible with POST_NOTIFICATIONS; the service runs either way.
                        if (enabled && Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.setAurogonKeepAlive(enabled)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(state: UiState, vm: MainViewModel, onSelectApps: () -> Unit, onOpenGuide: () -> Unit) = LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
) {
    item {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(t("Tối ưu thông báo nền", "Fix background notifications"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Ink)
            Text(t("Tìm và gỡ các giới hạn khiến ứng dụng nhận thông báo chậm hoặc bị mất.", "Find and lift the restrictions that make apps get notifications late or not at all."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (state.allOptimized.isEmpty()) item {
        Surface(onClick = onOpenGuide, color = Indigo.copy(alpha = .07f), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = Indigo)
                Column(Modifier.weight(1f)) {
                    Text(t("Lần đầu sử dụng?", "First time here?"), fontWeight = FontWeight.SemiBold)
                    Text(t("Xem hướng dẫn từng bước, từ Shizuku tới MILLET helper", "Step-by-step guide, from Shizuku to the MILLET helper"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Indigo)
            }
        }
    }
    item { ShizukuStatusCard(state, vm::requestShizuku) }
    item { FcmStatusCard(state, vm::checkFcm, vm::reconnectFcm) }
    state.device?.let { device -> item { DeviceCard(device.model, device.manufacturer, device.sdk, device.hyperOsVersion, device.romRegion, device.securityPatch) } }
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(t("Đã chọn", "Selected"), state.selected.size.toString(), Icons.Outlined.CheckCircle, Indigo, Modifier.weight(1f))
            MetricCard(t("Đã tối ưu", "Optimized"), state.allOptimized.size.toString(), Icons.Outlined.Bolt, Mint, Modifier.weight(1f))
        }
    }
    item {
        Button(
            onClick = vm::diagnose,
            enabled = state.selected.isNotEmpty() && state.uid == 2000 && !state.operationInProgress,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) { if (state.operationInProgress) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp) else Icon(Icons.Filled.HealthAndSafety, null); Spacer(Modifier.width(10.dp)); Text(if (state.operationInProgress) t("Đang phân tích…", "Analyzing…") else t("Chạy kiểm tra", "Run check"), fontWeight = FontWeight.SemiBold) }
        if (state.selected.isEmpty()) TextButton(onClick = onSelectApps, modifier = Modifier.fillMaxWidth()) { Text(t("Chọn ứng dụng cần kiểm tra", "Select apps to check")) }
    }
    if (state.diagnosticCompleted) item {
        val googleCount = state.diagnosticScope.count { it == "com.google.android.gms" || it == "com.google.android.gsf" }
        Surface(color = Indigo.copy(alpha = .07f), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Hub, null, tint = Indigo)
                Column {
                    Text(t("Đã kiểm tra toàn bộ đường push", "Checked the whole push path"), fontWeight = FontWeight.SemiBold)
                    Text(t("${state.selected.size} ứng dụng đã chọn + $googleCount dịch vụ Google · ${state.plans.size} giới hạn cần xử lý", "${state.selected.size} selected apps + $googleCount Google services · ${state.plans.size} restrictions to fix"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    item {
        Surface(color = Amber.copy(alpha = .10f), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Info, null, tint = Color(0xFF9A6700))
                Text(t("Việc nhận thông báo còn phụ thuộc vào FCM, mạng, máy chủ gửi và cách ROM xử lý. Không ứng dụng nào có thể bảo đảm không bao giờ trễ.", "Notifications also depend on FCM, the network, the sender's server and the ROM. No app can guarantee they are never late."), style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B4E00))
            }
        }
    }
}

@Composable
private fun ShizukuStatusCard(state: UiState, requestPermission: () -> Unit) {
    val connected = state.uid == 2000
    val color = if (connected) Mint else Amber
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White), elevation = CardDefaults.elevatedCardElevation(2.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(14.dp)) {
                Icon(if (connected) Icons.Filled.VerifiedUser else Icons.Outlined.AdminPanelSettings, null, Modifier.padding(11.dp).size(26.dp), tint = color)
            }
            Column(Modifier.weight(1f)) {
                Text("Shizuku", fontWeight = FontWeight.SemiBold)
                Text(if (connected) t("Đã kết nối quyền shell (UID 2000)", "Connected with shell access (UID 2000)") else state.shizuku, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (!connected) FilledTonalButton(onClick = requestPermission, contentPadding = PaddingValues(horizontal = 14.dp)) { Text(t("Kết nối", "Connection")) }
            else Icon(Icons.Filled.CheckCircle, t("Đã kết nối", "Connected"), tint = Mint)
        }
    }
}

@Composable
private fun FcmStatusCard(state: UiState, check: () -> Unit, reconnect: () -> Unit) {
    val (label, color) = when (state.fcm?.state) {
        FcmState.CONNECTED -> t("Đang kết nối", "Connected") to Mint
        FcmState.DISCONNECTED -> t("Mất kết nối", "Disconnected") to Color(0xFFD14343)
        FcmState.UNKNOWN -> t("Không rõ", "Unknown") to Amber
        null -> t("Chưa kiểm tra", "Not checked") to MaterialTheme.colorScheme.onSurfaceVariant
    }
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.CloudSync, null, tint = color)
                Column(Modifier.weight(1f)) {
                    Text(t("Kết nối FCM của Google", "Google FCM connection"), fontWeight = FontWeight.Bold)
                    Text(state.fcm?.let { status -> status.rawStatus?.let { t("Mã trạng thái GMS: $it", "GMS status code: $it") } ?: status.detail } ?: t("Kênh push dùng chung cho mọi ứng dụng dùng FCM", "The push channel shared by every app that uses FCM"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusPill(label, color)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(check, Modifier.weight(1f), enabled = !state.fcmBusy) { Text(if (state.fcmBusy) t("Đang kiểm tra…", "Checking…") else t("Kiểm tra", "Check")) }
                if (state.fcm?.state == FcmState.DISCONNECTED) Button(reconnect, Modifier.weight(1f), enabled = !state.fcmBusy) { Text(t("Kết nối lại", "Reconnect")) }
            }
        }
    }
}

@Composable
private fun DeviceCard(model: String, manufacturer: String, sdk: Int, hyperOs: String?, region: String?, patch: String) {
    OutlinedCard(colors = CardDefaults.outlinedCardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Smartphone, null, tint = Indigo)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) { Text(model, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(manufacturer, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                StatusPill("Android $sdk", Indigo)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth()) {
                Detail("HyperOS", hyperOs ?: t("Không phát hiện", "Not detected"), Modifier.weight(1f))
                Detail(t("Khu vực", "Region"), region ?: t("Không rõ", "Unknown"), Modifier.weight(1f))
                Detail(t("Bản vá", "Patch"), patch.ifBlank { t("Không rõ", "Unknown") }, Modifier.weight(1.2f))
            }
        }
    }
}

@Composable private fun Detail(label: String, value: String, modifier: Modifier = Modifier) = Column(modifier) { Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
@Composable private fun StatusPill(text: String, color: Color) = Surface(color = color.copy(alpha = .10f), shape = RoundedCornerShape(50)) { Text(text, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold) }

@Composable
private fun MetricCard(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier) = Surface(modifier, color = Color.White, shape = RoundedCornerShape(18.dp), shadowElevation = 1.dp) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = color.copy(alpha = .10f), shape = RoundedCornerShape(12.dp)) { Icon(icon, null, Modifier.padding(9.dp).size(21.dp), tint = color) }
        Column { Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String) = Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AppsScreen(state: UiState, query: String, onQuery: (String) -> Unit, select: (String) -> Unit, selectAll: (Set<String>) -> Unit, restore: (String) -> Unit, restoreAll: () -> Unit, iconFor: (String) -> Drawable?) = Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
    var filter by rememberSaveable { mutableStateOf("all") }
    var confirmRestoreAll by rememberSaveable { mutableStateOf(false) }
    var confirmRestore by rememberSaveable { mutableStateOf<String?>(null) }
    ScreenHeader(t("Ứng dụng", "Apps"), t("Chọn ứng dụng cần xử lý hoặc xem lại những ứng dụng đã tối ưu.", "Select the apps to fix, or review the ones already optimized."))
    AppSearchField(query, onQuery)
    Spacer(Modifier.height(8.dp))
    // Scrolls instead of splitting the width: fixed weights clipped the counts once the check icon or a large font appeared.
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(filter == "all", { filter = "all" }, { Text(t("Tất cả (${state.apps.size})", "All (${state.apps.size})"), maxLines = 1) })
        FilterChip(filter == "selected", { filter = "selected" }, { Text(t("Đã chọn (${state.selected.size})", "Selected (${state.selected.size})"), maxLines = 1) })
        FilterChip(filter == "optimized", { filter = "optimized" }, { Text(t("Đã tối ưu (${state.allOptimized.size})", "Optimized (${state.allOptimized.size})"), maxLines = 1) })
    }
    // Optimised apps that are not selected are no longer re-protected by the MILLET helper after a reboot.
    val unselectedOptimized = state.allOptimized - state.selected - setOf("com.google.android.gms", "com.google.android.gsf")
    if (filter == "optimized" && unselectedOptimized.isNotEmpty()) {
        Button(onClick = { selectAll(unselectedOptimized) }, enabled = !state.operationInProgress, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text(t("Chọn lại ${unselectedOptimized.size} app đã tối ưu", "Re-select ${unselectedOptimized.size} optimized apps"))
        }
        Text(t("Các app này đang tối ưu nhưng chưa được chọn, nên sẽ không được tự bảo vệ sau khi khởi động lại.", "These apps are optimized but not selected, so they will not be protected after reboots."), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (filter == "optimized" && state.optimizedPackages.isNotEmpty()) {
        OutlinedButton(onClick = { confirmRestoreAll = true }, enabled = !state.operationInProgress, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Restore, null); Spacer(Modifier.width(8.dp)); Text(t("Khôi phục cài đặt gốc cho tất cả", "Restore original settings for all"))
        }
    }
    val visibleApps = state.apps.filter { app ->
        (query.isBlank() || app.label.contains(query, true) || app.packageName.contains(query, true)) && when (filter) {
            "selected" -> app.packageName in state.selected
            "optimized" -> app.packageName in state.allOptimized
            else -> true
        }
    }
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(visibleApps, key = { it.packageName }) { app ->
            Surface(color = if (app.packageName in state.selected) Indigo.copy(alpha = .07f) else Color.White, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (app.packageName in state.allOptimized) Mint.copy(alpha = .35f) else MaterialTheme.colorScheme.outlineVariant)) {
                ListItem(headlineContent = { Text(app.label, fontWeight = FontWeight.Medium) }, supportingContent = { Column { Text(app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis); if (app.packageName in state.optimizedPackages) Text(t("Đã tối ưu • có thể hoàn tác", "Optimized • can be undone"), color = Mint, style = MaterialTheme.typography.labelSmall) else if (app.packageName in state.alreadyOptimalPackages) Text(t("Đã tối ưu sẵn • khôi phục sẽ đưa về mặc định Android", "Already optimized • restoring resets to Android defaults"), color = Mint, style = MaterialTheme.typography.labelSmall) else Text(t("Chưa có bản tối ưu đã xác minh", "No verified optimization yet"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) } }, leadingContent = { InstalledAppIcon(app.packageName, iconFor) },
                    // The optimised list restores apps; selecting happens in the other two lists.
                    trailingContent = { if (filter == "optimized") TextButton(onClick = { confirmRestore = app.packageName }, enabled = !state.operationInProgress) { Icon(Icons.Outlined.Restore, null, tint = Amber); Spacer(Modifier.width(4.dp)); Text(t("Khôi phục", "Restore"), color = Amber) } else Checkbox(app.packageName in state.selected, { select(app.packageName) }) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
        }
        if (visibleApps.isEmpty()) item { EmptyState(Icons.Outlined.FilterAltOff, t("Không có ứng dụng", "No apps"), if (filter == "optimized") t("Chưa có ứng dụng nào được tối ưu và xác minh.", "No app has been optimized and verified yet.") else t("Thử đổi bộ lọc hoặc từ khóa tìm kiếm.", "Try another filter or search term.")) }
    }
    confirmRestore?.let { pkg ->
        val label = state.apps.firstOrNull { it.packageName == pkg }?.label ?: pkg
        val backedUp = pkg in state.optimizedPackages
        AlertDialog(onDismissRequest = { confirmRestore = null }, icon = { Icon(Icons.Outlined.Restore, null, tint = Amber) }, title = { Text(t("Khôi phục $label?", "Restore $label?")) },
            text = { Text(if (backedUp) t("Trả $label về đúng trạng thái đã sao lưu trước khi tối ưu và bỏ chọn app. Cần Shizuku.", "Returns $label to the exact state backed up before optimizing and deselects it. Needs Shizuku.") else t("$label không có bản sao lưu (được tối ưu từ lần cài trước). App sẽ được đưa về mặc định Android: bỏ khỏi whitelist Doze và MILLET, chạy nền về mặc định, và bỏ chọn. Cần Shizuku.", "$label has no backup (it was optimized by an earlier install). It will be reset to Android defaults: removed from the Doze and MILLET whitelists, background running back to default, and deselected. Needs Shizuku.")) },
            confirmButton = { Button(onClick = { confirmRestore = null; restore(pkg) }) { Text(t("Khôi phục", "Restore")) } }, dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text(t("Hủy", "Cancel")) } })
    }
    if (confirmRestoreAll) {
        AlertDialog(onDismissRequest = { confirmRestoreAll = false }, icon = { Icon(Icons.Outlined.Restore, null, tint = Amber) }, title = { Text(t("Khôi phục toàn bộ?", "Restore everything?")) }, text = { Text(t("HyperOS Toolkit sẽ dùng các bản sao lưu để trả mọi ứng dụng đã tối ưu về đúng trạng thái ban đầu. Thao tác này cần Shizuku đang kết nối.", "HyperOS Toolkit will use the backups to return every optimized app to its exact original state. Shizuku must be connected.")) }, confirmButton = { Button(onClick = { confirmRestoreAll = false; restoreAll() }) { Text(t("Khôi phục tất cả", "Restore all")) } }, dismissButton = { TextButton(onClick = { confirmRestoreAll = false }) { Text(t("Hủy", "Cancel")) } })
    }
}

@Composable
private fun InstalledAppIcon(packageName: String, iconFor: (String) -> Drawable?) {
    val drawable = remember(packageName) { iconFor(packageName) }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(13.dp), modifier = Modifier.size(52.dp)) {
        if (drawable != null) AndroidView(
            factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setPadding(6, 6, 6, 6) } },
            update = { it.setImageDrawable(drawable) },
            modifier = Modifier.fillMaxSize()
        ) else Icon(Icons.Outlined.Android, null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PreviewScreen(state: UiState, vm: MainViewModel) {
    var confirmApply by rememberSaveable { mutableStateOf(false) }
    // After a dry run the main button still says "Chạy thử"; offer the real apply here instead of a switch at the top.
    val dryRunDone = state.dryRun && state.plans.isNotEmpty() && state.results.isNotEmpty() && state.results.all { it.status == ResultStatus.SKIPPED }
    PreviewList(state, vm, dryRunDone) { confirmApply = true }
    if (confirmApply) {
        AlertDialog(onDismissRequest = { confirmApply = false }, icon = { Icon(Icons.Filled.Security, null, tint = Indigo) }, title = { Text(t("Áp dụng thật?", "Apply for real?")) }, text = { Text(t("Tắt chạy thử, sao lưu trạng thái hiện tại rồi áp dụng ${state.plans.size} thay đổi. Có thể hoàn tác ở tab Lịch sử.", "Turns off dry run, backs up the current state, then applies ${state.plans.size} changes. You can undo them in the History tab.")) }, confirmButton = { Button(onClick = { confirmApply = false; vm.setDryRun(false); vm.applyConfirmed() }) { Text(t("Sao lưu và áp dụng", "Back up and apply")) } }, dismissButton = { TextButton(onClick = { confirmApply = false }) { Text(t("Hủy", "Cancel")) } })
    }
}

@Composable
private fun PreviewList(state: UiState, vm: MainViewModel, dryRunDone: Boolean, onApplyForReal: () -> Unit) = LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item { ScreenHeader(t("Xem lại thay đổi", "Review changes"), t("Hệ thống chỉ thay đổi sau khi hoàn tất sao lưu, xem lại và xác nhận.", "The system only changes after backup, review and confirmation.")) }
    if (state.plans.isNotEmpty()) item {
        Surface(color = if (state.dryRun) Mint.copy(alpha = .10f) else Amber.copy(alpha = .12f), shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Science, null, tint = if (state.dryRun) Mint else Amber); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(t("Chạy thử", "Dry run"), fontWeight = FontWeight.SemiBold); Text(if (state.dryRun) t("Chỉ xem trước — hệ thống không thay đổi", "Preview only — the system does not change") else t("Đã cho phép thay đổi thật", "Real changes allowed"), style = MaterialTheme.typography.bodySmall) }
                Switch(state.dryRun, vm::setDryRun)
            }
        }
    }
    if (state.plans.isEmpty()) item {
        if (state.diagnosticCompleted) EmptyState(Icons.Filled.CheckCircle, t("Đã ở trạng thái tối ưu", "Already optimal"), t("Không có thay đổi an toàn nào cần áp dụng cho ứng dụng đã chọn.", "No safe change is needed for the selected apps."), Mint)
        else EmptyState(Icons.AutoMirrored.Outlined.FactCheck, t("Chưa có kết quả", "No results yet"), t("Chọn ứng dụng, sau đó chạy kiểm tra từ Trang chủ.", "Select apps, then run the check from Home."))
    }
    if (state.plans.isNotEmpty()) item {
        Surface(color = Indigo.copy(alpha = .07f), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Info, null, tint = Indigo)
                Text(t("Danh sách dưới đây gồm cả ứng dụng mày chọn và hạ tầng Google Push liên quan. Mỗi thay đổi đều được sao lưu để hoàn tác.", "The list covers the apps you selected and the related Google push services. Every change is backed up so it can be undone."), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    items(state.plans) { plan -> ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row { Text(packageLabel(plan.packageName), Modifier.weight(1f), fontWeight = FontWeight.SemiBold); StatusPill(riskLabel(plan.risk), if (plan.risk == RiskLevel.EXPERIMENTAL) Amber else Indigo) }; Text(operationLabel(plan.operationId), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(plan.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline); HorizontalDivider(); Row { Detail(t("Hiện tại", "Current"), displayValue(plan.operationId, (plan.before.value as? SnapshotValue.Present)?.raw), Modifier.weight(1f)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.outline); Detail(t("Sau khi sửa", "After fix"), displayValue(plan.operationId, plan.desired), Modifier.weight(1f)) }; Text(t("✓ Có thể hoàn tác đúng trạng thái cũ", "✓ Can be undone to the exact old state"), style = MaterialTheme.typography.labelMedium, color = Mint) } } }
    if (state.plans.isNotEmpty()) item { Button(vm::applyConfirmed, Modifier.fillMaxWidth().height(54.dp), enabled = !state.operationInProgress, shape = RoundedCornerShape(16.dp)) { if (state.operationInProgress) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp) else Icon(if (state.dryRun) Icons.Outlined.Science else Icons.Filled.Security, null); Spacer(Modifier.width(10.dp)); Text(if (state.operationInProgress) t("Đang xử lý…", "Working…") else if (state.dryRun) t("Chạy thử an toàn", "Safe dry run") else t("Sao lưu và áp dụng", "Back up and apply"), fontWeight = FontWeight.SemiBold) } }
    if (dryRunDone) item {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(t("Chạy thử xong: chưa có gì bị thay đổi.", "Dry run done: nothing has changed."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onApplyForReal, Modifier.fillMaxWidth().height(54.dp), enabled = !state.operationInProgress, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint)) { Icon(Icons.Filled.Security, null); Spacer(Modifier.width(10.dp)); Text(t("Áp dụng thật", "Apply for real"), fontWeight = FontWeight.SemiBold) }
        }
    }
    if (state.results.isNotEmpty()) item { Text(t("Kết quả", "Results"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)) }
    items(state.results) { result -> OperationResultCard(result, state.apps.firstOrNull { it.packageName == result.packageName }?.label) }
}

private fun packageLabel(packageName: String) = when (packageName) {
    "com.google.android.gms" -> "Google Play services"
    "com.google.android.gsf" -> "Google Services Framework"
    "__device__" -> t("Toàn bộ thiết bị", "Whole device")
    else -> packageName
}

private fun riskLabel(risk: RiskLevel) = when (risk) {
    RiskLevel.LOW -> t("THẤP", "LOW")
    RiskLevel.MEDIUM -> t("TRUNG BÌNH", "MEDIUM")
    RiskLevel.EXPERIMENTAL -> t("THỬ NGHIỆM", "EXPERIMENTAL")
}

private fun operationLabel(operationId: String) = when (operationId) {
    "greezer_gms_limit_off" -> t("Tắt bộ giới hạn GMS của Greezer", "Turn off Greezer's GMS limiter")
    "xiaomi_millet_no_restrict" -> t("Miễn giới hạn nền HyperOS", "Exempt from HyperOS background limits")
    "xiaomi_millet_white" -> t("Danh sách ưu tiên Millet", "Millet priority list")
    "xiaomi_cloud_lowlatency" -> t("Ưu tiên kết nối đám mây Xiaomi", "Xiaomi cloud connection priority")
    "doze_whitelist" -> t("Cho phép hoạt động trong Doze", "Allow running during Doze")
    "appops_run_any_in_background" -> t("Cho phép chạy nền mở rộng", "Allow extended background running")
    "appops_run_in_background" -> t("Cho phép chạy nền", "Allow background running")
    "standby_bucket" -> t("Mức ưu tiên App Standby", "App Standby priority")
    "inactive_state" -> t("Trạng thái ứng dụng không hoạt động", "App inactive state")
    "appops_10053" -> t("Tự khởi chạy HyperOS mới (10053)", "New HyperOS autostart (10053)")
    "appops_10008" -> t("Tự khởi chạy MIUI (10008)", "MIUI autostart (10008)")
    "appops_wake_lock" -> t("Cho phép giữ CPU thức", "Allow keeping the CPU awake")
    "appops_start_foreground" -> t("Cho phép dịch vụ foreground", "Allow foreground services")
    "appops_schedule_exact_alarm" -> t("Cho phép báo thức chính xác", "Allow exact alarms")
    "network_background_whitelist" -> t("Không chặn dữ liệu nền", "Don't block background data")
    "global_app_standby_off" -> t("Tắt App Standby toàn thiết bị", "Turn off App Standby device-wide")
    "global_cached_apps_freezer_off" -> t("Tắt đóng băng tiến trình cached", "Turn off cached process freezing")
    "global_mobile_data_always_on" -> t("Giữ dữ liệu di động hoạt động", "Keep mobile data active")
    "global_wifi_never_sleep" -> t("Không cho Wi-Fi ngủ", "Keep Wi-Fi awake")
    else -> operationId
}

private fun displayValue(operationId: String, value: String?) = when {
    value == null -> t("Không đọc được", "Unreadable")
    operationId.startsWith("xiaomi_") && value == "true" -> t("Được whitelist bởi HyperOS", "Whitelisted by HyperOS")
    operationId.startsWith("xiaomi_") -> t("Chưa có trong whitelist Xiaomi", "Not in the Xiaomi whitelist")
    operationId == "standby_bucket" && value == "5" -> t("Exempted (cao nhất)", "Exempted (highest)")
    operationId == "standby_bucket" && value == "10" -> "Active"
    operationId == "standby_bucket" -> "Bucket $value"
    operationId == "doze_whitelist" && value == "true" -> t("Được miễn trừ", "Exempted")
    operationId == "doze_whitelist" -> t("Bị giới hạn", "Restricted")
    operationId == "inactive_state" && value == "false" -> t("Đang hoạt động", "Active")
    operationId == "inactive_state" -> "Inactive"
    operationId == "network_background_whitelist" && value == "true" -> t("Được dùng dữ liệu nền", "Background data allowed")
    operationId.startsWith("global_") -> value
    operationId == "greezer_gms_limit_off" && value == "true" -> t("Đang giới hạn GMS", "Limiting GMS")
    operationId == "greezer_gms_limit_off" -> t("Không giới hạn GMS", "Not limiting GMS")
    value == "allow" -> t("Cho phép", "Allow")
    else -> value
}

@Composable
private fun AppSearchField(value: String, onValueChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shadowElevation = 1.dp) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(Indigo),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            decorationBox = { innerField ->
                Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) Text(t("Tìm ứng dụng hoặc package…", "Search apps or packages…"), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        innerField()
                    }
                    if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }) { Icon(Icons.Filled.Close, t("Xóa tìm kiếm", "Clear search")) }
                }
            }
        )
    }
}

@Composable
private fun OperationResultCard(result: OperationResult, appLabel: String?) {
    val presentation = when (result.status) {
        ResultStatus.APPLIED_VERIFIED -> Triple(t("Đã áp dụng thành công", "Applied successfully"), t("Thiết lập mới đã được đọc lại và xác minh.", "The new setting was read back and verified."), Mint)
        ResultStatus.VERIFICATION_FAILED -> Triple(t("Không xác minh được", "Could not verify"), t("Lệnh đã chạy nhưng trạng thái đọc lại không khớp. App sẽ thử hoàn tác.", "The command ran but the read-back state does not match. The app will try to undo it."), Color(0xFFD14343))
        ResultStatus.FAILED_BEFORE_CHANGE -> Triple(t("Không thể áp dụng", "Could not apply"), t("Lỗi xảy ra trước khi thay đổi được xác nhận.", "An error happened before the change was confirmed."), Color(0xFFD14343))
        ResultStatus.ROLLED_BACK -> Triple(t("Đã hoàn tác an toàn", "Safely undone"), t("Trạng thái ban đầu đã được khôi phục.", "The original state has been restored."), Amber)
        ResultStatus.ROLLBACK_FAILED -> Triple(t("Hoàn tác thất bại", "Undo failed"), t("Cần giữ Shizuku và kiểm tra lại trong Lịch sử.", "Keep Shizuku running and check again in History."), Color(0xFFD14343))
        ResultStatus.UNSUPPORTED -> Triple(t("Không được hỗ trợ", "Not supported"), t("ROM hoặc Android hiện tại không hỗ trợ thao tác này.", "This ROM or Android version does not support this action."), MaterialTheme.colorScheme.onSurfaceVariant)
        ResultStatus.SKIPPED -> Triple(t("Chạy thử hoàn tất", "Dry run finished"), t("Không có thay đổi nào được ghi vào hệ thống.", "Nothing was written to the system."), Indigo)
    }
    val icon = when (result.status) {
        ResultStatus.APPLIED_VERIFIED -> Icons.Filled.CheckCircle
        ResultStatus.ROLLED_BACK -> Icons.Outlined.Restore
        ResultStatus.SKIPPED -> Icons.Outlined.Science
        ResultStatus.UNSUPPORTED -> Icons.Outlined.Block
        else -> Icons.Filled.Error
    }
    Surface(color = presentation.third.copy(alpha = .09f), shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, presentation.third.copy(alpha = .24f))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = presentation.third, modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(presentation.first, fontWeight = FontWeight.Bold, color = presentation.third)
                result.packageName?.let { pkg -> Text(appLabel?.let { "$it · $pkg" } ?: packageLabel(pkg), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Text(operationDisplayName(result.operationId), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(presentation.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (result.message.isNotBlank() && result.message !in setOf(t("Chạy thử", "Dry run"), t("Đã áp dụng và xác minh", "Applied and verified"))) Text(t("Chi tiết: ${result.message}", "Details: ${result.message}"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun operationDisplayName(id: String) = when (id) {
    "greezer_gms_limit_off" -> t("Tắt bộ giới hạn GMS của Greezer", "Turn off Greezer's GMS limiter")
    "xiaomi_millet_no_restrict" -> t("Miễn giới hạn nền HyperOS", "Exempt from HyperOS background limits")
    "xiaomi_millet_white" -> t("Danh sách ưu tiên Millet", "Millet priority list")
    "xiaomi_cloud_lowlatency" -> t("Ưu tiên kết nối đám mây Xiaomi", "Xiaomi cloud connection priority")
    "doze_whitelist" -> t("Cho phép hoạt động trong Doze", "Allow running during Doze")
    "appops_run_any_in_background" -> t("Cho phép chạy nền (AppOps)", "Allow background running (AppOps)")
    "appops_run_in_background" -> t("Cho phép chạy nền", "Allow background running")
    "appops_10053" -> t("Tự khởi chạy HyperOS mới (10053)", "New HyperOS autostart (10053)")
    "appops_10008" -> t("Tự khởi chạy MIUI (10008)", "MIUI autostart (10008)")
    "appops_wake_lock" -> t("Giữ CPU thức (WAKE_LOCK)", "Keep CPU awake (WAKE_LOCK)")
    "appops_start_foreground" -> t("Khởi chạy foreground service", "Start foreground services")
    "appops_schedule_exact_alarm" -> t("Báo thức chính xác", "Exact alarms")
    "network_background_whitelist" -> t("Whitelist dữ liệu nền", "Background data whitelist")
    "global_app_standby_off" -> t("Tắt App Standby toàn thiết bị", "Turn off App Standby device-wide")
    "global_cached_apps_freezer_off" -> t("Tắt cached app freezer", "Turn off cached app freezer")
    "global_mobile_data_always_on" -> t("Giữ dữ liệu di động hoạt động", "Keep mobile data active")
    "global_wifi_never_sleep" -> t("Không cho Wi-Fi ngủ", "Keep Wi-Fi awake")
    else -> id
}

@Composable
private fun HistoryScreen(state: UiState, restore: (OptimizationSession) -> Unit) = LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    item { ScreenHeader(t("Lịch sử sao lưu", "Backup history"), t("Khôi phục từng phiên tối ưu về đúng trạng thái đã ghi nhận.", "Restore each optimization session to the exact recorded state.")) }
    if (state.history.isEmpty()) item { EmptyState(Icons.Outlined.History, t("Chưa có bản sao lưu", "No backups yet"), t("Bản sao lưu sẽ xuất hiện trước lần thay đổi thật đầu tiên.", "Backups appear before the first real change.")) }
    items(state.history, key = { it.sessionId }) { session -> OutlinedCard(colors = CardDefaults.outlinedCardColors(containerColor = Color.White)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Backup, null, tint = Indigo); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(t("Phiên ${session.sessionId.take(8)}", "Session ${session.sessionId.take(8)}"), fontWeight = FontWeight.SemiBold); Text(t("${session.snapshots.size} trạng thái • định dạng ${session.schemaVersion}", "${session.snapshots.size} states • format ${session.schemaVersion}"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; TextButton({ restore(session) }) { Text(t("Khôi phục", "Restore")) } } } }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, text: String, color: Color = Indigo) = Column(Modifier.fillMaxWidth().padding(vertical = 42.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) { Surface(color = color.copy(alpha = .08f), shape = RoundedCornerShape(20.dp)) { Icon(icon, null, Modifier.padding(16.dp).size(30.dp), tint = color) }; Text(title, fontWeight = FontWeight.SemiBold); Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable
private fun SettingsScreen(state: UiState, vm: MainViewModel, onAurogon: (Boolean) -> Unit) = LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item { ScreenHeader(t("Cài đặt", "Settings"), t("Điều khiển nâng cao và các bước HyperOS cần người dùng xác nhận.", "Advanced controls and the HyperOS steps that need your confirmation.")) }
    item { LanguageCard(onChanged = vm::refresh) }
    item { MaintenanceCard(state, vm) }
    item { SetupCard(state, vm) }
    item { AssistantGuardCard(state, vm) }
    item { SettingsToggle(Icons.Outlined.ScreenLockPortrait, t("Giữ allowlist Aurogon khi tắt màn hình", "Keep the Aurogon allowlist while the screen is off"), t("Làm mới sau 40 giây, 2 phút rồi mỗi 10 phút; chạy dịch vụ nền, hơi tốn pin", "Refreshes after 40 seconds, 2 minutes, then every 10 minutes; runs a background service, uses a little battery"), state.aurogonKeepAlive, onAurogon) }
    item { SettingsToggle(Icons.Outlined.Tune, t("Tùy chọn nâng cao", "Advanced options"), t("Mở các can thiệp không cần thiết cho mọi ứng dụng", "Unlocks changes most apps do not need"), state.advanced, vm::setAdvanced) }
    if (state.advanced) {
        item {
            Surface(color = Amber.copy(alpha = .10f), shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Amber.copy(alpha = .30f))) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.WarningAmber, null, tint = Color(0xFF9A6700))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(t("Chỉ bật khi cấu hình an toàn chưa đủ", "Only turn on when the safe setup is not enough"), fontWeight = FontWeight.Bold, color = Color(0xFF6B4E00))
                        Text(t("Các mục dưới đây được sao lưu và xác minh, nhưng có thể tăng pin tiêu thụ hoặc thay đổi hành vi của toàn thiết bị.", "The items below are backed up and verified, but they can increase battery use or change how the whole device behaves."), style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B4E00))
                    }
                }
            }
        }
        item { SettingsToggle(Icons.Outlined.AcUnit, t("Tắt bộ giới hạn GMS của Greezer", "Turn off Greezer's GMS limiter"), t("Thử nghiệm, cần Shizuku; HyperOS bật lại sau mỗi lần khởi động", "Experimental, needs Shizuku; HyperOS turns it back on after every boot"), state.greezerGmsLimit, vm::setGreezerGmsLimit) }
        item { SettingsToggle(Icons.Outlined.AutoAwesome, t("Autostart nội bộ Xiaomi", "Xiaomi internal autostart"), t("Thử AppOps 10053 và 10008 nếu ROM hỗ trợ", "Tries AppOps 10053 and 10008 if the ROM supports them"), state.xiaomiAutostartOps, vm::setXiaomiAutostartOps) }
        item { SettingsToggle(Icons.Outlined.NetworkCheck, t("Whitelist dữ liệu nền", "Background data whitelist"), t("Cho app dùng mạng khi Data Saver hoặc policy nền hoạt động", "Lets apps use the network while Data Saver or a background policy is active"), state.networkWhitelist, vm::setNetworkWhitelist) }
        item { SettingsToggle(Icons.Outlined.Memory, t("AppOps chạy nền mạnh", "Aggressive background AppOps"), t("WAKE_LOCK, foreground service và exact alarm", "WAKE_LOCK, foreground service and exact alarm"), state.aggressiveAppOps, vm::setAggressiveAppOps) }
        item { SettingsToggle(Icons.Outlined.BatteryAlert, t("Profile toàn hệ thống — hao pin", "System-wide profile — drains battery"), t("Tắt App Standby/freezer, giữ mobile data và Wi-Fi hoạt động", "Turns off App Standby/freezer, keeps mobile data and Wi-Fi active"), state.aggressiveSystemProfile, vm::setAggressiveSystemProfile) }
    }
}

/**
 * Language row in the same style as the other settings rows; tapping opens a picker.
 * The title is always bilingual so someone who picked the wrong language can still find it.
 */
@Composable
private fun LanguageCard(onChanged: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    val systemName = if (java.util.Locale.getDefault().language == "vi") "Tiếng Việt" else "English"
    data class Option(val language: AppLanguage, val name: String, val detail: String)
    val options = listOf(
        Option(AppLanguage.SYSTEM, t("Theo máy", "System default"), t("Đang là $systemName", "Currently $systemName")),
        Option(AppLanguage.VI, "Tiếng Việt", "Vietnamese"),
        Option(AppLanguage.EN, "English", "Tiếng Anh"),
    )
    val current = options.first { it.language == I18n.language }
    Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { open = true }, color = Color.White, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Translate, null, tint = Indigo)
            Column(Modifier.weight(1f)) {
                Text("Ngôn ngữ · Language", fontWeight = FontWeight.Medium)
                Text(if (current.language == AppLanguage.SYSTEM) "${current.name} · $systemName" else current.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        icon = { Icon(Icons.Outlined.Translate, null, tint = Indigo) },
        title = { Text("Ngôn ngữ · Language") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    val selected = option.language == I18n.language
                    Surface(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { I18n.set(context, option.language); onChanged(); open = false },
                        color = if (selected) Indigo.copy(alpha = .08f) else Color.Transparent,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Indigo.copy(alpha = .45f) else MaterialTheme.colorScheme.outline.copy(alpha = .5f)),
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(option.name, fontWeight = FontWeight.SemiBold, color = if (selected) Indigo else MaterialTheme.colorScheme.onSurface)
                                Text(option.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (selected) Icon(Icons.Filled.CheckCircle, null, tint = Indigo)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ open = false }) { Text(t("Đóng", "Close")) } },
    )
}

@Composable
private fun SettingsToggle(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit, enabled: Boolean = true) = Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(icon, null, tint = if (enabled) Indigo else MaterialTheme.colorScheme.outline); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked, onChecked, enabled = enabled) }
}

private fun formatTime(epochMs: Long) = if (epochMs <= 0) t("chưa ghi nhận", "not recorded") else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMs))

@Composable
private fun MaintenanceCard(state: UiState, vm: MainViewModel) = ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
    val helper = state.helperStatus
    val healthy = if (state.helperInstalled) helper?.canWrite == true && helper.lastSuccess else state.maintenanceCanWrite
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(if (healthy) Icons.Filled.VerifiedUser else Icons.Outlined.BuildCircle, null, tint = if (healthy) Mint else Amber)
            Column(Modifier.weight(1f)) {
                Text(t("Tự phục hồi whitelist MILLET", "MILLET whitelist self-repair"), fontWeight = FontWeight.Bold)
                Text(if (state.helperInstalled) t("MILLET helper đã cài — hoạt động không cần Shizuku", "MILLET helper installed — works without Shizuku") else t("Chưa có helper: Android chặn app targetSdk mới ghi khóa MILLET", "No helper yet: Android blocks apps with a new targetSdk from writing the MILLET keys"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(t("Đang bảo vệ ${state.maintenancePackages} package cùng Play Store và HyperOS Toolkit. Helper theo dõi thay đổi của MILLET và ghi lại ngay khi HyperOS xóa, kể cả sau khi khởi động lại.", "Protecting ${state.maintenancePackages} packages plus Play Store and HyperOS Toolkit. The helper watches MILLET and writes the apps back as soon as HyperOS removes them, including after reboots."), style = MaterialTheme.typography.bodySmall)
        if (state.helperInstalled && helper != null) {
            Detail(t("Quyền sửa cài đặt của helper", "Helper's modify-settings permission"), if (helper.canWrite) t("Đã có", "Granted") else t("Chưa có", "Missing"))
            Detail(t("Theo dõi MILLET", "MILLET watcher"), if (helper.watcherScheduled) t("Đang chờ thay đổi", "Waiting for changes") else t("Chưa đăng ký", "Not registered"))
            Detail(t("HyperOS đã đánh thức helper (ROM này)", "HyperOS woke the helper (this ROM)"), formatTime(helper.watcherTriggeredAt))
            Detail(t("Tự sửa lần gần nhất", "Last self-repair"), formatTime(helper.autoRepairAt))
        }
        state.maintenanceStatus?.let { Text(t("Lần gần nhất: $it", "Last: $it"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!state.helperInstalled || state.helperNeedsUpdate) Button(vm::installHelper, Modifier.weight(1f), enabled = state.uid == 2000 && !state.operationInProgress) { Text(if (state.helperNeedsUpdate) t("Cập nhật helper", "Update helper") else t("Cài helper", "Install helper")) }
            OutlinedButton(vm::reapplyMaintenance, Modifier.weight(1f)) { Text(t("Kiểm tra và áp dụng", "Check and apply")) }
        }
        if (!state.helperInstalled && state.uid != 2000) Text(t("Cài helper cần Shizuku một lần. Sau đó helper tự chạy mà không cần Shizuku.", "Installing the helper needs Shizuku once. After that it runs without Shizuku."), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!state.helperInstalled && !state.maintenanceCanWrite) TextButton(vm::requestMaintenancePermission, Modifier.fillMaxWidth()) { Text(t("Cấp quyền sửa cài đặt cho HyperOS Toolkit", "Let HyperOS Toolkit modify settings")) }
        if (state.helperInstalled) TextButton(vm::uninstallHelper, Modifier.fillMaxWidth(), enabled = state.uid == 2000 && !state.operationInProgress) { Text(t("Gỡ MILLET helper", "Remove MILLET helper")) }
    }
}

@Composable
private fun SetupCard(state: UiState, vm: MainViewModel) = ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(t("Thiết lập nền cho HyperOS Toolkit", "Background setup for HyperOS Toolkit"), fontWeight = FontWeight.Bold)
        val (autostartLabel, autostartColor) = when (state.autostart) {
            AutostartState.ALLOWED -> t("Đã bật", "On") to Mint
            AutostartState.DENIED -> t("Đang tắt", "Off") to Color(0xFFD14343)
            AutostartState.UNKNOWN -> t("Không đọc được", "Unreadable") to Amber
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("Tự khởi chạy", "Autostart"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            StatusPill(autostartLabel, autostartColor)
            if (state.autostart != AutostartState.ALLOWED) TextButton(vm::openAutostart) { Text(t("Mở", "Open")) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("Không tối ưu pin", "No battery optimization"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            StatusPill(if (state.batteryExempt) t("Đã miễn", "Exempted") to Mint else t("Đang tối ưu", "Optimizing") to Amber)
            if (!state.batteryExempt) TextButton(vm::requestBatteryExemption) { Text(t("Cho phép", "Allow")) }
        }
    }
}

@Composable
private fun AssistantGuardCard(state: UiState, vm: MainViewModel) = ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
    val guard = state.assistantGuard
    val enabled = guard?.enabled == true
    val current = guard?.assistant?.substringBefore('/')
    val label = { pkg: String? -> state.assistantCandidates.firstOrNull { it.packageName == pkg }?.label ?: pkg }
    var picked by rememberSaveable(current) { mutableStateOf(current) }
    val target = state.assistantCandidates.firstOrNull { it.packageName == picked }
    val canRun = state.uid == 2000 && !state.operationInProgress
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Mic, null, tint = if (enabled && guard?.healthy == true) Mint else Amber)
            Column(Modifier.weight(1f)) {
                Text(t("Trợ lý AI cho nút nguồn", "Power-key AI assistant"), fontWeight = FontWeight.Bold)
                Text(t("Chọn AI mở ra khi giữ nút nguồn rồi bấm Áp dụng", "Choose the AI that opens when you hold the power key, then tap Apply"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.assistantCandidates.isEmpty()) Text(t("Chưa có app trợ lý nào. Cài Gemini (app Google), Claude hoặc ChatGPT trước.", "No assistant app yet. Install Gemini (the Google app), Claude or ChatGPT first."), style = MaterialTheme.typography.bodySmall)
        state.assistantCandidates.forEach { candidate ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { picked = candidate.packageName }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(picked == candidate.packageName, { picked = candidate.packageName })
                Column(Modifier.weight(1f)) {
                    Text(candidate.label, fontWeight = FontWeight.Medium)
                    Text(if (candidate.isXiaoAi) t("Trợ lý gốc của Xiaomi", "Xiaomi's built-in assistant") else candidate.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (candidate.packageName == current) StatusPill(t("Đang dùng", "In use"), Mint)
            }
        }
        if (target != null) {
            Text(
                if (target.isXiaoAi) t("Áp dụng sẽ cài lại Xiao AI và trả nút nguồn về mặc định Xiaomi.", "Apply reinstalls Xiao AI and returns the power key to the Xiaomi default.")
                else t("Áp dụng sẽ cấp micro và quyền mở cửa sổ khi chạy nền cho ${target.label}, đặt làm trợ lý, đổi hành động giữ nút nguồn, tắt Xiao AI (chọn lại Xiao AI để khôi phục) và bật tự giữ bên dưới.", "Apply grants ${target.label} the microphone and background pop-up permission, makes it the assistant, changes the power-key long press, turns off Xiao AI (pick Xiao AI again to restore it) and turns on the guard below."),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Without Shizuku, switching between already-set-up assistants goes through the system picker.
            val viaSystem = state.uid != 2000 && guard?.setUp == true && !target.isXiaoAi
            Button({ vm.applyAssistant(target) }, Modifier.fillMaxWidth(), enabled = !state.operationInProgress && (state.uid == 2000 || viaSystem)) { Text(if (target.packageName == current) t("Áp dụng lại ${target.label}", "Apply ${target.label} again") else t("Áp dụng ${target.label}", "Apply ${target.label}")) }
            if (viaSystem) Text(t("Shizuku đang tắt: Áp dụng sẽ mở Cài đặt để chọn ${target.label} ở “Digital assistant app” (kèm trang quyền cửa sổ nền nếu cần).", "Shizuku is off: Apply opens Settings so you can pick ${target.label} under “Digital assistant app” (plus the background pop-up permission page if needed)."), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(t("Tự giữ sau khi khởi động lại", "Keep after reboot"), fontWeight = FontWeight.Medium)
                Text(t("HyperOS CN xóa dịch vụ trợ lý mỗi lần khởi động; tự đặt lại ngay khi mở khóa", "HyperOS China clears the assistant service on every boot; sets it again as soon as you unlock"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(enabled, vm::setAssistantGuard, enabled = canRun)
        }
        if (guard != null) {
            Detail(t("Dịch vụ đang dùng", "Service in use"), guard.service?.let { label(it.substringBefore('/')) } ?: t("Trống — giữ nút nguồn sẽ không mở trợ lý", "Empty — holding the power key will not open the assistant"))
            Detail(t("Tự sửa lần gần nhất", "Last self-repair"), formatTime(guard.lastRun))
            guard.lastResult?.let { Text(t("Lần gần nhất: $it", "Last: $it"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (state.uid != 2000 && guard?.setUp != true) Text(t("Lần áp dụng đầu tiên cần Shizuku. Sau đó đổi giữa các AI và phần tự giữ không cần Shizuku (trừ khi đổi về Xiao AI).", "The first apply needs Shizuku. After that, switching between AIs and the guard work without Shizuku (except switching back to Xiao AI)."), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun StatusPill(pair: Pair<String, Color>) = StatusPill(pair.first, pair.second)
