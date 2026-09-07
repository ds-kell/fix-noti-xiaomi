package io.github.hypernotifyfix

import android.os.Bundle
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.FactCheck
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.hypernotifyfix.core.AppContainer
import io.github.hypernotifyfix.core.model.OptimizationSession
import io.github.hypernotifyfix.core.model.OperationResult
import io.github.hypernotifyfix.core.model.ResultStatus
import io.github.hypernotifyfix.core.model.RiskLevel
import io.github.hypernotifyfix.core.model.SnapshotValue

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
                HyperNotifyApp(vm, container)
            }
        }
    }
}

private data class Destination(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HyperNotifyApp(vm: MainViewModel, container: AppContainer) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { message -> snackbarHostState.showSnackbar(message); vm.clearMessage() }
    }
    val destinations = listOf(
        Destination("Trang chủ", Icons.Outlined.Home, Icons.Filled.Home),
        Destination("Ứng dụng", Icons.Outlined.Apps, Icons.Filled.Apps),
        Destination("Xem trước", Icons.AutoMirrored.Outlined.FactCheck, Icons.AutoMirrored.Filled.FactCheck),
        Destination("Lịch sử", Icons.Outlined.History, Icons.Filled.History),
        Destination("Cài đặt", Icons.Outlined.Settings, Icons.Filled.Settings)
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Image(painterResource(R.drawable.hypernotify_launcher), "HyperNotifyFix", Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)))
                        Column {
                            Text("HyperNotify", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Tối ưu thông báo nền an toàn", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 3.dp) {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(if (tab == index) destination.selectedIcon else destination.icon, destination.label) },
                        label = { Text(destination.label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Indigo.copy(alpha = .12f), selectedIconColor = Indigo, selectedTextColor = Indigo)
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> HomeScreen(state, vm, onSelectApps = { tab = 1 })
                1 -> AppsScreen(state, query, { query = it }, vm::select, vm::revokePackage, vm::restoreAll, container.catalog::icon)
                2 -> PreviewScreen(state, vm)
                3 -> HistoryScreen(state, vm::restore)
                else -> SettingsScreen(state, vm, container)
            }
        }
    }
}

@Composable
private fun HomeScreen(state: UiState, vm: MainViewModel, onSelectApps: () -> Unit) = LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
) {
    item {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Tối ưu thông báo nền", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Ink)
            Text("Tìm và gỡ các giới hạn khiến ứng dụng nhận thông báo chậm hoặc bị mất.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    item { ShizukuStatusCard(state, vm::requestShizuku) }
    state.device?.let { device -> item { DeviceCard(device.model, device.manufacturer, device.sdk, device.hyperOsVersion, device.romRegion, device.securityPatch) } }
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Đã chọn", state.selected.size.toString(), Icons.Outlined.CheckCircle, Indigo, Modifier.weight(1f))
            MetricCard("Bản sao lưu", state.history.size.toString(), Icons.Outlined.Backup, Mint, Modifier.weight(1f))
        }
    }
    item {
        Button(
            onClick = vm::diagnose,
            enabled = state.selected.isNotEmpty() && state.uid == 2000 && !state.operationInProgress,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) { if (state.operationInProgress) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp) else Icon(Icons.Filled.HealthAndSafety, null); Spacer(Modifier.width(10.dp)); Text(if (state.operationInProgress) "Đang phân tích…" else "Chạy kiểm tra", fontWeight = FontWeight.SemiBold) }
        if (state.selected.isEmpty()) TextButton(onClick = onSelectApps, modifier = Modifier.fillMaxWidth()) { Text("Chọn ứng dụng cần kiểm tra") }
    }
    if (state.diagnosticCompleted) item {
        val googleCount = state.diagnosticScope.count { it == "com.google.android.gms" || it == "com.google.android.gsf" }
        Surface(color = Indigo.copy(alpha = .07f), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Hub, null, tint = Indigo)
                Column {
                    Text("Đã kiểm tra toàn bộ đường push", fontWeight = FontWeight.SemiBold)
                    Text("${state.selected.size} ứng dụng đã chọn + $googleCount dịch vụ Google · ${state.plans.size} giới hạn cần xử lý", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    item {
        Surface(color = Amber.copy(alpha = .10f), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Info, null, tint = Color(0xFF9A6700))
                Text("Việc nhận thông báo còn phụ thuộc vào FCM, mạng, máy chủ gửi và cách ROM xử lý. Không ứng dụng nào có thể bảo đảm không bao giờ trễ.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B4E00))
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
                Text(if (connected) "Đã kết nối quyền shell (UID 2000)" else state.shizuku, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (!connected) FilledTonalButton(onClick = requestPermission, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Kết nối") }
            else Icon(Icons.Filled.CheckCircle, "Đã kết nối", tint = Mint)
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
                Detail("HyperOS", hyperOs ?: "Không phát hiện", Modifier.weight(1f))
                Detail("Khu vực", region ?: "Không rõ", Modifier.weight(1f))
                Detail("Bản vá", patch.ifBlank { "Không rõ" }, Modifier.weight(1.2f))
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
private fun AppsScreen(state: UiState, query: String, onQuery: (String) -> Unit, select: (String) -> Unit, revoke: (String) -> Unit, restoreAll: () -> Unit, iconFor: (String) -> Drawable?) = Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
    var filter by rememberSaveable { mutableStateOf("all") }
    var confirmRestoreAll by rememberSaveable { mutableStateOf(false) }
    ScreenHeader("Ứng dụng", "Chọn ứng dụng cần xử lý hoặc xem lại những ứng dụng đã tối ưu.")
    AppSearchField(query, onQuery)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(filter == "all", { filter = "all" }, { Text("Tất cả") }, modifier = Modifier.weight(1f))
        FilterChip(filter == "selected", { filter = "selected" }, { Text("Đã chọn (${state.selected.size})", maxLines = 1) }, modifier = Modifier.weight(1.35f))
        FilterChip(filter == "optimized", { filter = "optimized" }, { Text("Đã tối ưu (${state.optimizedPackages.size})", maxLines = 1) }, modifier = Modifier.weight(1.55f))
    }
    if (filter == "optimized" && state.optimizedPackages.isNotEmpty()) {
        OutlinedButton(onClick = { confirmRestoreAll = true }, enabled = !state.operationInProgress, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Restore, null); Spacer(Modifier.width(8.dp)); Text("Khôi phục cài đặt gốc cho tất cả")
        }
    }
    val visibleApps = state.apps.filter { app ->
        (query.isBlank() || app.label.contains(query, true) || app.packageName.contains(query, true)) && when (filter) {
            "selected" -> app.packageName in state.selected
            "optimized" -> app.packageName in state.optimizedPackages
            else -> true
        }
    }
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(visibleApps, key = { it.packageName }) { app ->
            Surface(color = if (app.packageName in state.selected) Indigo.copy(alpha = .07f) else Color.White, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (app.packageName in state.optimizedPackages) Mint.copy(alpha = .35f) else MaterialTheme.colorScheme.outlineVariant)) {
                ListItem(headlineContent = { Text(app.label, fontWeight = FontWeight.Medium) }, supportingContent = { Column { Text(app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis); if (app.packageName in state.optimizedPackages) Text("Đã tối ưu • có thể hoàn tác", color = Mint, style = MaterialTheme.typography.labelSmall) else Text("Chưa có bản tối ưu đã xác minh", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) } }, leadingContent = { InstalledAppIcon(app.packageName, iconFor) }, trailingContent = { Row(verticalAlignment = Alignment.CenterVertically) { if (app.packageName in state.optimizedPackages) IconButton(onClick = { revoke(app.packageName) }, enabled = !state.operationInProgress) { Icon(Icons.Outlined.Restore, "Hoàn tác tối ưu", tint = Amber) }; Checkbox(app.packageName in state.selected, { select(app.packageName) }) } }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
        }
        if (visibleApps.isEmpty()) item { EmptyState(Icons.Outlined.FilterAltOff, "Không có ứng dụng", if (filter == "optimized") "Chưa có ứng dụng nào được tối ưu và xác minh." else "Thử đổi bộ lọc hoặc từ khóa tìm kiếm.") }
    }
    if (confirmRestoreAll) {
        AlertDialog(onDismissRequest = { confirmRestoreAll = false }, icon = { Icon(Icons.Outlined.Restore, null, tint = Amber) }, title = { Text("Khôi phục toàn bộ?") }, text = { Text("HyperNotify sẽ dùng các bản sao lưu để trả mọi ứng dụng đã tối ưu về đúng trạng thái ban đầu. Thao tác này cần Shizuku đang kết nối.") }, confirmButton = { Button(onClick = { confirmRestoreAll = false; restoreAll() }) { Text("Khôi phục tất cả") } }, dismissButton = { TextButton(onClick = { confirmRestoreAll = false }) { Text("Hủy") } })
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
private fun PreviewScreen(state: UiState, vm: MainViewModel) = LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item { ScreenHeader("Xem lại thay đổi", "Hệ thống chỉ thay đổi sau khi hoàn tất sao lưu, xem lại và xác nhận.") }
    if (state.plans.isNotEmpty()) item {
        Surface(color = if (state.dryRun) Mint.copy(alpha = .10f) else Amber.copy(alpha = .12f), shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Science, null, tint = if (state.dryRun) Mint else Amber); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text("Chạy thử", fontWeight = FontWeight.SemiBold); Text(if (state.dryRun) "Chỉ xem trước — hệ thống không thay đổi" else "Đã cho phép thay đổi thật", style = MaterialTheme.typography.bodySmall) }
                Switch(state.dryRun, vm::setDryRun)
            }
        }
    }
    if (state.plans.isEmpty()) item {
        if (state.diagnosticCompleted) EmptyState(Icons.Filled.CheckCircle, "Đã ở trạng thái tối ưu", "Không có thay đổi an toàn nào cần áp dụng cho ứng dụng đã chọn.", Mint)
        else EmptyState(Icons.AutoMirrored.Outlined.FactCheck, "Chưa có kết quả", "Chọn ứng dụng, sau đó chạy kiểm tra từ Trang chủ.")
    }
    if (state.plans.isNotEmpty()) item {
        Surface(color = Indigo.copy(alpha = .07f), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Info, null, tint = Indigo)
                Text("Danh sách dưới đây gồm cả ứng dụng mày chọn và hạ tầng Google Push liên quan. Mỗi thay đổi đều được sao lưu để hoàn tác.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    items(state.plans) { plan -> ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row { Text(packageLabel(plan.packageName), Modifier.weight(1f), fontWeight = FontWeight.SemiBold); StatusPill(riskLabel(plan.risk), if (plan.risk == RiskLevel.EXPERIMENTAL) Amber else Indigo) }; Text(operationLabel(plan.operationId), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(plan.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline); HorizontalDivider(); Row { Detail("Hiện tại", displayValue(plan.operationId, (plan.before.value as? SnapshotValue.Present)?.raw), Modifier.weight(1f)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.outline); Detail("Sau khi sửa", displayValue(plan.operationId, plan.desired), Modifier.weight(1f)) }; Text("✓ Có thể hoàn tác đúng trạng thái cũ", style = MaterialTheme.typography.labelMedium, color = Mint) } } }
    if (state.plans.isNotEmpty()) item { Button(vm::applyConfirmed, Modifier.fillMaxWidth().height(54.dp), enabled = !state.operationInProgress, shape = RoundedCornerShape(16.dp)) { if (state.operationInProgress) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp) else Icon(if (state.dryRun) Icons.Outlined.Science else Icons.Filled.Security, null); Spacer(Modifier.width(10.dp)); Text(if (state.operationInProgress) "Đang xử lý…" else if (state.dryRun) "Chạy thử an toàn" else "Sao lưu và áp dụng", fontWeight = FontWeight.SemiBold) } }
    if (state.results.isNotEmpty()) item { Text("Kết quả", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)) }
    items(state.results) { result -> OperationResultCard(result, state.apps.firstOrNull { it.packageName == result.packageName }?.label) }
}

private fun packageLabel(packageName: String) = when (packageName) {
    "com.google.android.gms" -> "Google Play services"
    "com.google.android.gsf" -> "Google Services Framework"
    else -> packageName
}

private fun riskLabel(risk: RiskLevel) = when (risk) {
    RiskLevel.LOW -> "THẤP"
    RiskLevel.MEDIUM -> "TRUNG BÌNH"
    RiskLevel.EXPERIMENTAL -> "THỬ NGHIỆM"
}

private fun operationLabel(operationId: String) = when (operationId) {
    "xiaomi_millet_no_restrict" -> "Miễn giới hạn nền HyperOS"
    "xiaomi_millet_white" -> "Danh sách ưu tiên Millet"
    "xiaomi_cloud_lowlatency" -> "Ưu tiên kết nối đám mây Xiaomi"
    "doze_whitelist" -> "Cho phép hoạt động trong Doze"
    "appops_run_any_in_background" -> "Cho phép chạy nền mở rộng"
    "appops_run_in_background" -> "Cho phép chạy nền"
    "standby_bucket" -> "Mức ưu tiên App Standby"
    "inactive_state" -> "Trạng thái ứng dụng không hoạt động"
    else -> operationId
}

private fun displayValue(operationId: String, value: String?) = when {
    value == null -> "Không đọc được"
    operationId.startsWith("xiaomi_") && value == "true" -> "Được whitelist bởi HyperOS"
    operationId.startsWith("xiaomi_") -> "Chưa có trong whitelist Xiaomi"
    operationId == "standby_bucket" && value == "5" -> "Exempted (cao nhất)"
    operationId == "standby_bucket" && value == "10" -> "Active"
    operationId == "standby_bucket" -> "Bucket $value"
    operationId == "doze_whitelist" && value == "true" -> "Được miễn trừ"
    operationId == "doze_whitelist" -> "Bị giới hạn"
    operationId == "inactive_state" && value == "false" -> "Đang hoạt động"
    operationId == "inactive_state" -> "Inactive"
    value == "allow" -> "Cho phép"
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
                        if (value.isEmpty()) Text("Tìm ứng dụng hoặc package…", maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        innerField()
                    }
                    if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }) { Icon(Icons.Filled.Close, "Xóa tìm kiếm") }
                }
            }
        )
    }
}

@Composable
private fun OperationResultCard(result: OperationResult, appLabel: String?) {
    val presentation = when (result.status) {
        ResultStatus.APPLIED_VERIFIED -> Triple("Đã áp dụng thành công", "Thiết lập mới đã được đọc lại và xác minh.", Mint)
        ResultStatus.VERIFICATION_FAILED -> Triple("Không xác minh được", "Lệnh đã chạy nhưng trạng thái đọc lại không khớp. App sẽ thử hoàn tác.", Color(0xFFD14343))
        ResultStatus.FAILED_BEFORE_CHANGE -> Triple("Không thể áp dụng", "Lỗi xảy ra trước khi thay đổi được xác nhận.", Color(0xFFD14343))
        ResultStatus.ROLLED_BACK -> Triple("Đã hoàn tác an toàn", "Trạng thái ban đầu đã được khôi phục.", Amber)
        ResultStatus.ROLLBACK_FAILED -> Triple("Hoàn tác thất bại", "Cần giữ Shizuku và kiểm tra lại trong Lịch sử.", Color(0xFFD14343))
        ResultStatus.UNSUPPORTED -> Triple("Không được hỗ trợ", "ROM hoặc Android hiện tại không hỗ trợ thao tác này.", MaterialTheme.colorScheme.onSurfaceVariant)
        ResultStatus.SKIPPED -> Triple("Chạy thử hoàn tất", "Không có thay đổi nào được ghi vào hệ thống.", Indigo)
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
                if (result.message.isNotBlank() && result.message !in setOf("Chạy thử", "Đã áp dụng và xác minh")) Text("Chi tiết: ${result.message}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun operationDisplayName(id: String) = when (id) {
    "xiaomi_millet_no_restrict" -> "Miễn giới hạn nền HyperOS"
    "xiaomi_millet_white" -> "Danh sách ưu tiên Millet"
    "xiaomi_cloud_lowlatency" -> "Ưu tiên kết nối đám mây Xiaomi"
    "doze_whitelist" -> "Cho phép hoạt động trong Doze"
    "appops_run_any_in_background" -> "Cho phép chạy nền (AppOps)"
    else -> id
}

@Composable
private fun HistoryScreen(state: UiState, restore: (OptimizationSession) -> Unit) = LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    item { ScreenHeader("Lịch sử sao lưu", "Khôi phục từng phiên tối ưu về đúng trạng thái đã ghi nhận.") }
    if (state.history.isEmpty()) item { EmptyState(Icons.Outlined.History, "Chưa có bản sao lưu", "Bản sao lưu sẽ xuất hiện trước lần thay đổi thật đầu tiên.") }
    items(state.history, key = { it.sessionId }) { session -> OutlinedCard(colors = CardDefaults.outlinedCardColors(containerColor = Color.White)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Backup, null, tint = Indigo); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Phiên ${session.sessionId.take(8)}", fontWeight = FontWeight.SemiBold); Text("${session.snapshots.size} trạng thái • định dạng ${session.schemaVersion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; TextButton({ restore(session) }) { Text("Khôi phục") } } } }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, text: String, color: Color = Indigo) = Column(Modifier.fillMaxWidth().padding(vertical = 42.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) { Surface(color = color.copy(alpha = .08f), shape = RoundedCornerShape(20.dp)) { Icon(icon, null, Modifier.padding(16.dp).size(30.dp), tint = color) }; Text(title, fontWeight = FontWeight.SemiBold); Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable
private fun SettingsScreen(state: UiState, vm: MainViewModel, container: AppContainer) = LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item { ScreenHeader("Cài đặt", "Điều khiển nâng cao và các bước HyperOS cần người dùng xác nhận.") }
    item {
        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(if (state.maintenanceCanWrite) Icons.Filled.VerifiedUser else Icons.Outlined.BuildCircle, null, tint = if (state.maintenanceCanWrite) Mint else Amber)
                    Column(Modifier.weight(1f)) {
                        Text("Tự phục hồi sau khi khởi động", fontWeight = FontWeight.Bold)
                        Text(if (state.maintenanceCanWrite) "Đã có quyền sửa cài đặt hệ thống" else "Cần cấp quyền một lần, không phụ thuộc Shizuku", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("Đang bảo vệ ${state.maintenancePackages} package. App sẽ ghi lại whitelist Xiaomi khi máy khởi động, người dùng mở khóa hoặc HyperNotify được cập nhật.", style = MaterialTheme.typography.bodySmall)
                state.maintenanceStatus?.let { Text("Lần gần nhất: $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!state.maintenanceCanWrite) Button(vm::requestMaintenancePermission, Modifier.weight(1f)) { Text("Cấp quyền") }
                    OutlinedButton(vm::reapplyMaintenance, Modifier.weight(1f)) { Text("Kiểm tra và áp dụng") }
                }
                TextButton({ container.settings.xiaomiAutostart("io.github.hypernotifyfix") }, Modifier.fillMaxWidth()) { Text("Mở cài đặt tự khởi chạy HyperNotify") }
            }
        }
    }
    item { SettingsToggle(Icons.Outlined.Tune, "Chế độ nâng cao", "Hiển thị các tùy chọn tương thích thử nghiệm", state.advanced, vm::setAdvanced) }
    item { SettingsToggle(Icons.Outlined.WarningAmber, "Kiểm tra Xiaomi thử nghiệm", "Chưa được xác minh đầy đủ trên Xiaomi 15 Ultra", state.experimental, vm::setExperimental, state.advanced) }
    state.selected.firstOrNull()?.let { pkg ->
        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Danh sách kiểm tra thủ công", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf("Đã bật tự khởi chạy", "Tiết kiệm pin: Không hạn chế", "Đã bật thông báo và màn hình khóa", "Đã cho phép dữ liệu nền").forEach { label -> var checked by rememberSaveable(pkg, label) { mutableStateOf(false) }; Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, { checked = it }); Text(label, style = MaterialTheme.typography.bodyMedium) } }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { TextButton({ container.settings.appInfo(pkg) }) { Text("Thông tin") }; TextButton({ container.settings.xiaomiAutostart(pkg) }) { Text("Tự khởi chạy") }; TextButton({ container.settings.notifications(pkg) }) { Text("Thông báo") } }
                }
            }
        }
    }
}

@Composable
private fun SettingsToggle(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit, enabled: Boolean = true) = Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(icon, null, tint = if (enabled) Indigo else MaterialTheme.colorScheme.outline); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked, onChecked, enabled = enabled) }
}
