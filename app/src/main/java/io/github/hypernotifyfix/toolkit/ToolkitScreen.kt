package io.github.hypernotifyfix.toolkit

import io.github.hypernotifyfix.core.t
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
                Text(t("Công cụ HyperOS", "HyperOS tools"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(t("Gỡ app rác, tắt quảng cáo, đổi app mặc định. Không cần root, mọi thứ hoàn tác được.", "Remove bloatware, turn off ads, change default apps. No root, and everything can be undone."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (!state.shizukuReady && !state.loading) item {
            Note(Icons.Outlined.Info, Indigo, t("Shizuku chưa chạy", "Shizuku is not running"), t("Xem được trạng thái, nhưng muốn gỡ, khôi phục hay đổi mặc định thì cần khởi động Shizuku (Trang chủ). Làm xong thì bật chế độ an toàn cho app ngân hàng ở dưới.", "You can see the status, but removing, restoring or changing defaults needs Shizuku (Home). When done, turn on bank-safe mode below."))
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
            Note(Icons.Outlined.CheckCircle, Mint, t("Không thấy app rác nào trong danh sách", "No bloatware from the list found"), t("Máy này không có app nào trong danh mục, hoặc bạn đã gỡ hết.", "This phone has none of the listed apps, or you already removed them all."))
        }

        item { DefaultAppsCard(state, onPick = { picking = it }, onSystem = { vm.openDefaultAppsSettings(::open) }) }
        item { KeepAliveCard(installed, onPreset) }
        item { Spacer(Modifier.height(24.dp)) }
    }

    confirm?.let { (remove, items) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            icon = { Icon(if (remove) Icons.Outlined.DeleteSweep else Icons.Outlined.Restore, null, tint = if (remove) Rose else Mint) },
            title = { Text(if (remove) t("Gỡ ${items.size} app?", "Remove ${items.size} apps?") else t("Khôi phục ${items.size} app?", "Restore ${items.size} apps?")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (remove) t("Gỡ cho người dùng hiện tại, dữ liệu được giữ lại. App hệ thống khôi phục được bằng một nút; app không phải hệ thống thì phải cài lại từ cửa hàng.", "Removed for the current user, data is kept. System apps come back with one tap; other apps must be reinstalled from a store.") else t("Cài lại các app hệ thống đã gỡ từ bản có sẵn trong máy.", "Reinstalls the removed system apps from the copy already on the phone."), style = MaterialTheme.typography.bodySmall)
                    items.take(8).forEach { Text("• ${it.entry.label}", style = MaterialTheme.typography.bodySmall) }
                    if (items.size > 8) Text(t("… và ${items.size - 8} app khác", "… and ${items.size - 8} more"), style = MaterialTheme.typography.bodySmall)
                    items.filter { it.entry.safety == Safety.CAUTION }.forEach { Text("⚠ ${it.entry.label}: ${it.entry.note}", style = MaterialTheme.typography.bodySmall, color = Amber) }
                }
            },
            confirmButton = { Button({ val pk = items.map { it.entry.packageName }; confirm = null; if (remove) vm.remove(pk) else vm.restore(pk) }) { Text(if (remove) t("Gỡ", "Remove") else t("Khôi phục", "Restore")) } },
            dismissButton = { TextButton({ confirm = null }) { Text(t("Hủy", "Cancel")) } },
        )
    }

    picking?.let { role ->
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(role.title) },
            text = {
                Column {
                    if (role.role == ToolkitCommands.ROLE_HOME) Text(t("HyperOS có thể tắt cử chỉ vuốt toàn màn hình với launcher bên thứ ba.", "HyperOS may disable full-screen gestures with a third-party launcher."), style = MaterialTheme.typography.bodySmall, color = Amber, modifier = Modifier.padding(bottom = 8.dp))
                    role.choices.forEach { choice ->
                        Row(Modifier.fillMaxWidth().clickable { picking = null; vm.setDefault(role.role, choice.packageName) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = choice.packageName == role.current, onClick = null)
                            Spacer(Modifier.width(10.dp))
                            Text(choice.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ picking = null }) { Text(t("Đóng", "Close")) } },
        )
    }

    if (askBankSafe) AlertDialog(
        onDismissRequest = { askBankSafe = false },
        icon = { Icon(Icons.Outlined.AccountBalance, null, tint = Mint) },
        title = { Text(t("Bật chế độ an toàn cho app ngân hàng?", "Turn on bank-safe mode?")) },
        text = { Text(t("Tắt Gỡ lỗi USB, Gỡ lỗi không dây và Tùy chọn nhà phát triển. Shizuku sẽ dừng, nhưng mọi thứ đã áp dụng (sửa thông báo, app đã gỡ, trợ lý AI, app mặc định) vẫn giữ nguyên. Muốn thay đổi thêm thì bật lại Gỡ lỗi không dây và Shizuku.", "Turns off USB debugging, Wireless debugging and Developer options. Shizuku will stop, but everything already applied (notification fixes, removed apps, AI assistant, default apps) stays. To change more later, turn Wireless debugging and Shizuku back on.")) },
        confirmButton = { Button({ askBankSafe = false; if (!vm.enableBankSafe()) open(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }) { Text(t("Tắt ngay", "Turn off now")) } },
        dismissButton = { TextButton({ askBankSafe = false }) { Text(t("Để sau", "Later")) } },
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
                Text(if (status.safe) t("App ngân hàng dùng được", "Banking apps can run") else t("App ngân hàng có thể bị chặn", "Banking apps may be blocked"), fontWeight = FontWeight.Bold)
            }
            if (status.safe) Text(t("Tùy chọn nhà phát triển đang tắt. Máy không root, các thay đổi vẫn giữ nguyên.", "Developer options are off. The phone is not rooted and your changes stay in place."), style = MaterialTheme.typography.bodySmall)
            else {
                Text(t("Nhiều app ngân hàng từ chối mở khi các mục này đang bật:", "Many banking apps refuse to open while these are on:"), style = MaterialTheme.typography.bodySmall)
                listOf(t("Tùy chọn nhà phát triển", "Developer options") to status.developerOptions, t("Gỡ lỗi USB", "USB debugging") to status.usbDebugging, t("Gỡ lỗi không dây (Shizuku)", "Wireless debugging (Shizuku)") to status.wirelessDebugging)
                    .filter { it.second }.forEach { Text(t("• ${it.first}: đang bật", "• ${it.first}: on"), style = MaterialTheme.typography.bodySmall) }
                Text(t("Dùng xong công cụ thì tắt đi. Mọi thiết lập đã áp dụng vẫn được giữ.", "Turn them off when you are done. Everything applied is kept."), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onTurnOff) { Text(t("Tắt ngay", "Turn off now")) }
                    OutlinedButton(onOpen) { Text(t("Mở cài đặt", "Open settings")) }
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
                    Text(group.title.toString(), fontWeight = FontWeight.Bold)
                    Text(t("${installed.size} đang có · ${removed.size} đã gỡ", "${installed.size} present · ${removed.size} removed"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
            }
            Text(group.subtitle.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val safeInstalled = installed.filter { it.entry.safety == Safety.SAFE }
            if (!expanded) {
                if (safeInstalled.isNotEmpty()) FilledTonalButton({ onRemove(safeInstalled) }, enabled = !busy) { Text(t("Gỡ ${safeInstalled.size} mục an toàn", "Remove ${safeInstalled.size} safe items")) }
                return@Column
            }
            items.forEach { item ->
                val pkg = item.entry.packageName
                Row(Modifier.fillMaxWidth().clickable(enabled = item.installed) { picked = if (pkg in picked) picked - pkg else picked + pkg }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (item.installed) Checkbox(checked = pkg in picked, onCheckedChange = null) else Icon(Icons.Outlined.CheckCircle, t("Đã gỡ", "Removed"), tint = Mint, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.entry.label.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(pkg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        item.entry.note?.let { Text("⚠ $it", style = MaterialTheme.typography.labelSmall, color = Amber) }
                        if (item.installed && !item.system) Text(t("Không phải app hệ thống: gỡ xong muốn dùng lại phải cài từ cửa hàng", "Not a system app: after removing it, reinstall from a store to use it again"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!item.installed) TextButton({ onRestore(listOf(item)) }, enabled = !busy) { Text(t("Khôi phục", "Restore")) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (picked.isNotEmpty()) Button({ onRemove(installed.filter { it.entry.packageName in picked }); picked = emptySet() }, enabled = !busy) { Text(t("Gỡ ${picked.size} app", "Remove ${picked.size} apps")) }
                else if (safeInstalled.isNotEmpty()) FilledTonalButton({ onRemove(safeInstalled) }, enabled = !busy) { Text(t("Gỡ ${safeInstalled.size} mục an toàn", "Remove ${safeInstalled.size} safe items")) }
                if (removed.size > 1) OutlinedButton({ onRestore(removed) }, enabled = !busy) { Text(t("Khôi phục tất cả", "Restore all")) }
            }
        }
    }
}

@Composable
private fun DefaultAppsCard(state: ToolkitState, onPick: (DefaultAppRole) -> Unit, onSystem: () -> Unit) = ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.SwapHoriz, null, tint = Indigo)
            Text(t("App mặc định", "Default apps"), fontWeight = FontWeight.Bold)
        }
        Text(t("Đặt trình duyệt và launcher mặc định, kể cả khi HyperOS khoá lựa chọn. Trợ lý AI cho nút nguồn nằm ở tab Cài đặt.", "Set the default browser and launcher, even when HyperOS locks the choice. The power-key AI assistant is in the Settings tab."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.roles.forEach { role ->
            val current = role.choices.firstOrNull { it.packageName == role.current }?.label ?: t("Chưa chọn", "Not set")
            Row(Modifier.fillMaxWidth().clickable(enabled = role.choices.isNotEmpty() && !state.busy) { onPick(role) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(role.title, fontWeight = FontWeight.Medium)
                    Text(current, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(t("Đổi", "Change"), color = Indigo, fontWeight = FontWeight.SemiBold)
            }
        }
        TextButton(onSystem) { Text(t("Mở cài đặt app mặc định của hệ thống", "Open the system default apps settings")) }
    }
}

@Composable
private fun KeepAliveCard(installed: Set<String>, onPreset: (KeepAlivePreset, List<String>) -> Unit) = ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Bolt, null, tint = Indigo)
            Text(t("Giữ app chạy nền", "Keep apps running in the background"), fontWeight = FontWeight.Bold)
        }
        Text(t("Chọn nhanh nhóm app hay bị HyperOS tắt ngầm, rồi chẩn đoán và áp dụng như bình thường.", "Quickly select a group of apps HyperOS tends to kill, then diagnose and apply as usual."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        KeepAlivePresets.all.forEach { preset ->
            val present = preset.packages.filter { it in installed }
            Row(Modifier.fillMaxWidth().clickable(enabled = present.isNotEmpty()) { onPreset(preset, present) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(preset.title.toString(), fontWeight = FontWeight.Medium)
                    Text(preset.subtitle.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (present.isEmpty()) t("Không có", "None") else t("Chọn ${present.size}", "Select ${present.size}"), color = if (present.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Indigo, fontWeight = FontWeight.SemiBold)
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
