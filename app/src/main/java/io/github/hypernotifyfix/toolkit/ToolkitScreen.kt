package io.github.hypernotifyfix.toolkit

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val Indigo = Color(0xFF4F46E5)
private val Mint = Color(0xFF10B981)
private val Amber = Color(0xFF9A6700)
private val Rose = Color(0xFFE11D48)

/**
 * "Công cụ" tab: bank-safe mode, debloat, ads, default apps and keep-alive presets.
 * [installed] lets presets show how many of their apps are on the phone; [onPreset] selects them in the Apps tab.
 */
@Composable
fun ToolkitScreen(vm: ToolkitViewModel, installed: Set<String>, onPreset: (KeepAlivePreset, List<String>) -> Unit) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    fun open(intent: Intent) = try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    var confirm by remember { mutableStateOf<Pair<Boolean, List<DebloatItem>>?>(null) }
    var picking by remember { mutableStateOf<DefaultAppRole?>(null) }
    var askBankSafe by rememberSaveable { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Công cụ HyperOS", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Gỡ app rác, tắt quảng cáo, đổi app mặc định. Không cần root, mọi thứ hoàn tác được.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (!state.shizukuReady && !state.loading) item {
            Note(Icons.Outlined.Info, Indigo, "Shizuku chưa chạy", "Xem được trạng thái, nhưng muốn gỡ, khôi phục hay đổi mặc định thì cần khởi động Shizuku (Trang chủ). Làm xong thì bật chế độ an toàn cho app ngân hàng ở dưới.")
        }
        item { BankSafeCard(state, onTurnOff = { askBankSafe = true }, onOpen = { open(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }) }

        DebloatGroup.entries.forEach { group ->
            val items = state.debloat.filter { it.entry.group == group }
            if (items.isNotEmpty()) item(key = group.name) {
                DebloatCard(group, items, state.busy,
                    onRemove = { confirm = true to it },
                    onRestore = { confirm = false to it })
            }
        }
        if (!state.loading && state.debloat.isEmpty()) item {
            Note(Icons.Outlined.CheckCircle, Mint, "Không thấy app rác nào trong danh sách", "Máy này không có app nào trong danh mục, hoặc bạn đã gỡ hết.")
        }

        item { DefaultAppsCard(state, onPick = { picking = it }, onSystem = { vm.openDefaultAppsSettings(::open) }) }
        item { KeepAliveCard(installed, onPreset) }
        item { Spacer(Modifier.height(24.dp)) }
    }

    confirm?.let { (remove, items) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            icon = { Icon(if (remove) Icons.Outlined.DeleteSweep else Icons.Outlined.Restore, null, tint = if (remove) Rose else Mint) },
            title = { Text(if (remove) "Gỡ ${items.size} app?" else "Khôi phục ${items.size} app?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (remove) "Gỡ cho người dùng hiện tại, dữ liệu được giữ lại. App hệ thống khôi phục được bằng một nút; app không phải hệ thống thì phải cài lại từ cửa hàng." else "Cài lại các app hệ thống đã gỡ từ bản có sẵn trong máy.", style = MaterialTheme.typography.bodySmall)
                    items.take(8).forEach { Text("• ${it.entry.label}", style = MaterialTheme.typography.bodySmall) }
                    if (items.size > 8) Text("… và ${items.size - 8} app khác", style = MaterialTheme.typography.bodySmall)
                    items.filter { it.entry.safety == Safety.CAUTION }.forEach { Text("⚠ ${it.entry.label}: ${it.entry.note}", style = MaterialTheme.typography.bodySmall, color = Amber) }
                }
            },
            confirmButton = { Button({ val pk = items.map { it.entry.packageName }; confirm = null; if (remove) vm.remove(pk) else vm.restore(pk) }) { Text(if (remove) "Gỡ" else "Khôi phục") } },
            dismissButton = { TextButton({ confirm = null }) { Text("Hủy") } },
        )
    }

    picking?.let { role ->
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(role.title) },
            text = {
                Column {
                    if (role.role == ToolkitCommands.ROLE_HOME) Text("HyperOS có thể tắt cử chỉ vuốt toàn màn hình với launcher bên thứ ba.", style = MaterialTheme.typography.bodySmall, color = Amber, modifier = Modifier.padding(bottom = 8.dp))
                    role.choices.forEach { choice ->
                        Row(Modifier.fillMaxWidth().clickable { picking = null; vm.setDefault(role.role, choice.packageName) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = choice.packageName == role.current, onClick = null)
                            Spacer(Modifier.width(10.dp))
                            Text(choice.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ picking = null }) { Text("Đóng") } },
        )
    }

    if (askBankSafe) AlertDialog(
        onDismissRequest = { askBankSafe = false },
        icon = { Icon(Icons.Outlined.AccountBalance, null, tint = Mint) },
        title = { Text("Bật chế độ an toàn cho app ngân hàng?") },
        text = { Text("Tắt Gỡ lỗi USB, Gỡ lỗi không dây và Tùy chọn nhà phát triển. Shizuku sẽ dừng, nhưng mọi thứ đã áp dụng (sửa thông báo, app đã gỡ, trợ lý AI, app mặc định) vẫn giữ nguyên. Muốn thay đổi thêm thì bật lại Gỡ lỗi không dây và Shizuku.") },
        confirmButton = { Button({ askBankSafe = false; if (!vm.enableBankSafe()) open(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }) { Text("Tắt ngay") } },
        dismissButton = { TextButton({ askBankSafe = false }) { Text("Để sau") } },
    )

    LaunchedEffect(Unit) { vm.refresh() }
}

@Composable
private fun BankSafeCard(state: ToolkitState, onTurnOff: () -> Unit, onOpen: () -> Unit) {
    val status = state.bankSafe ?: return
    val color = if (status.safe) Mint else Amber
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = .08f)), border = BorderStroke(1.dp, color.copy(alpha = .3f)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.AccountBalance, null, tint = color)
                Text(if (status.safe) "App ngân hàng dùng được" else "App ngân hàng có thể bị chặn", fontWeight = FontWeight.Bold)
            }
            if (status.safe) Text("Tùy chọn nhà phát triển đang tắt. Máy không root, các thay đổi vẫn giữ nguyên.", style = MaterialTheme.typography.bodySmall)
            else {
                Text("Nhiều app ngân hàng từ chối mở khi các mục này đang bật:", style = MaterialTheme.typography.bodySmall)
                listOf("Tùy chọn nhà phát triển" to status.developerOptions, "Gỡ lỗi USB" to status.usbDebugging, "Gỡ lỗi không dây (Shizuku)" to status.wirelessDebugging)
                    .filter { it.second }.forEach { Text("• ${it.first}: đang bật", style = MaterialTheme.typography.bodySmall) }
                Text("Dùng xong công cụ thì tắt đi. Mọi thiết lập đã áp dụng vẫn được giữ.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onTurnOff) { Text("Tắt ngay") }
                    OutlinedButton(onOpen) { Text("Mở cài đặt") }
                }
            }
        }
    }
}

@Composable
private fun DebloatCard(group: DebloatGroup, items: List<DebloatItem>, busy: Boolean, onRemove: (List<DebloatItem>) -> Unit, onRestore: (List<DebloatItem>) -> Unit) {
    var picked by rememberSaveable(group) { mutableStateOf(setOf<String>()) }
    var expanded by rememberSaveable(group) { mutableStateOf(group == DebloatGroup.ADS) }
    val installed = items.filter { it.installed }
    val removed = items.filter { !it.installed }
    val icon = when (group) { DebloatGroup.ADS -> Icons.Outlined.Block; DebloatGroup.BLOAT -> Icons.Outlined.CleaningServices; DebloatGroup.CHINA -> Icons.Outlined.Language }
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null, tint = Indigo)
                Column(Modifier.weight(1f)) {
                    Text(group.title, fontWeight = FontWeight.Bold)
                    Text("${installed.size} đang có · ${removed.size} đã gỡ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
            }
            Text(group.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val safeInstalled = installed.filter { it.entry.safety == Safety.SAFE }
            if (!expanded) {
                if (safeInstalled.isNotEmpty()) FilledTonalButton({ onRemove(safeInstalled) }, enabled = !busy) { Text("Gỡ ${safeInstalled.size} mục an toàn") }
                return@Column
            }
            items.forEach { item ->
                val pkg = item.entry.packageName
                Row(Modifier.fillMaxWidth().clickable(enabled = item.installed) { picked = if (pkg in picked) picked - pkg else picked + pkg }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (item.installed) Checkbox(checked = pkg in picked, onCheckedChange = null) else Icon(Icons.Outlined.CheckCircle, "Đã gỡ", tint = Mint, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.entry.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(pkg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        item.entry.note?.let { Text("⚠ $it", style = MaterialTheme.typography.labelSmall, color = Amber) }
                        if (item.installed && !item.system) Text("Không phải app hệ thống: gỡ xong muốn dùng lại phải cài từ cửa hàng", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!item.installed) TextButton({ onRestore(listOf(item)) }, enabled = !busy) { Text("Khôi phục") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (picked.isNotEmpty()) Button({ onRemove(installed.filter { it.entry.packageName in picked }); picked = emptySet() }, enabled = !busy) { Text("Gỡ ${picked.size} app") }
                else if (safeInstalled.isNotEmpty()) FilledTonalButton({ onRemove(safeInstalled) }, enabled = !busy) { Text("Gỡ ${safeInstalled.size} mục an toàn") }
                if (removed.size > 1) OutlinedButton({ onRestore(removed) }, enabled = !busy) { Text("Khôi phục tất cả") }
            }
        }
    }
}

@Composable
private fun DefaultAppsCard(state: ToolkitState, onPick: (DefaultAppRole) -> Unit, onSystem: () -> Unit) = ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.SwapHoriz, null, tint = Indigo)
            Text("App mặc định", fontWeight = FontWeight.Bold)
        }
        Text("Đặt trình duyệt và launcher mặc định, kể cả khi HyperOS khoá lựa chọn. Trợ lý AI cho nút nguồn nằm ở tab Cài đặt.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.roles.forEach { role ->
            val current = role.choices.firstOrNull { it.packageName == role.current }?.label ?: "Chưa chọn"
            Row(Modifier.fillMaxWidth().clickable(enabled = role.choices.isNotEmpty() && !state.busy) { onPick(role) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(role.title, fontWeight = FontWeight.Medium)
                    Text(current, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Đổi", color = Indigo, fontWeight = FontWeight.SemiBold)
            }
        }
        TextButton(onSystem) { Text("Mở cài đặt app mặc định của hệ thống") }
    }
}

@Composable
private fun KeepAliveCard(installed: Set<String>, onPreset: (KeepAlivePreset, List<String>) -> Unit) = ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Bolt, null, tint = Indigo)
            Text("Giữ app chạy nền", fontWeight = FontWeight.Bold)
        }
        Text("Chọn nhanh nhóm app hay bị HyperOS tắt ngầm, rồi chẩn đoán và áp dụng như bình thường.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        KeepAlivePresets.all.forEach { preset ->
            val present = preset.packages.filter { it in installed }
            Row(Modifier.fillMaxWidth().clickable(enabled = present.isNotEmpty()) { onPreset(preset, present) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(preset.title, fontWeight = FontWeight.Medium)
                    Text(preset.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (present.isEmpty()) "Không có" else "Chọn ${present.size}", color = if (present.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Indigo, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Note(icon: ImageVector, color: Color, title: String, text: String) = Surface(color = color.copy(alpha = .08f), shape = RoundedCornerShape(18.dp)) {
    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = color)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}
