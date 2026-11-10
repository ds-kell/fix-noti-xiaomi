package io.github.hypernotifyfix

import io.github.hypernotifyfix.core.t
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.hypernotifyfix.core.system.AutostartState
import io.github.hypernotifyfix.core.system.FcmState

/** Tab indices of the bottom navigation, used by guide shortcuts. */
object GuideTab { const val HOME = 0; const val APPS = 1; const val PREVIEW = 2; const val HISTORY = 3; const val SETTINGS = 4 }

private data class GuideStep(
    val icon: ImageVector,
    val title: String,
    val summary: String,
    val details: List<String>,
    /** null = informational step without a measurable state. */
    val done: Boolean? = null,
    val action: Pair<String, Int>? = null,
)

private fun guideSteps(state: UiState) = listOf(
    GuideStep(
        Icons.Outlined.Adb, t("1. Khởi động Shizuku", "1. Start Shizuku"), t("Cần cho bước kiểm tra, áp dụng và cài helper", "Needed to check, apply and install the helper"),
        listOf(
            t("Cài Shizuku, bật Tùy chọn nhà phát triển → Gỡ lỗi không dây.", "Install Shizuku, then turn on Developer options → Wireless debugging."),
            t("Trong Shizuku chọn “Ghép nối”, nhập mã ghép nối, rồi bấm “Bắt đầu”.", "In Shizuku choose “Pairing”, enter the pairing code, then tap “Start”."),
            t("Quay lại HyperOS Toolkit và cấp quyền khi được hỏi. Trang chủ phải hiện “Đã kết nối”.", "Come back to HyperOS Toolkit and grant permission when asked. Home must show “Connected”."),
            t("Shizuku sẽ dừng sau mỗi lần khởi động lại máy; chỉ cần bật lại khi muốn đổi cấu hình.", "Shizuku stops after every reboot; start it again only when you want to change something.")
        ),
        done = state.uid == 2000, action = t("Mở Trang chủ", "Open Home") to GuideTab.HOME
    ),
    GuideStep(
        Icons.Outlined.Apps, t("2. Chọn ứng dụng bị trễ thông báo", "2. Pick the apps with late notifications"), t("Ví dụ Messenger, Zalo, Gmail, ngân hàng", "For example Messenger, Zalo, Gmail, banking"),
        listOf(
            t("Vào tab Ứng dụng, tìm và chọn những app hay nhận thông báo chậm.", "In the Apps tab, find and select the apps whose notifications arrive late."),
            t("Google Play services và Google Services Framework luôn được kiểm tra kèm, vì mọi thông báo FCM đều đi qua chúng.", "Google Play services and Google Services Framework are always checked too, because every FCM notification goes through them."),
            t("Danh sách đã chọn cũng là danh sách được bảo vệ tự động sau khi khởi động lại.", "The selected apps are also the ones protected automatically after reboots.")
        ),
        done = state.selected.isNotEmpty(), action = t("Chọn ứng dụng", "Select apps") to GuideTab.APPS
    ),
    GuideStep(
        Icons.Outlined.HealthAndSafety, t("3. Chạy kiểm tra và xem trước", "3. Run the check and preview"), t("Không thay đổi gì cho tới khi bạn xác nhận", "Nothing changes until you confirm"),
        listOf(
            t("Ở Trang chủ bấm “Chạy kiểm tra”. App đọc trạng thái Doze, AppOps, App Standby và whitelist MILLET.", "On Home tap “Run check”. The app reads Doze, AppOps, App Standby and the MILLET whitelist."),
            t("Tab Xem trước liệt kê từng giới hạn, giá trị hiện tại và giá trị mới.", "The Preview tab lists each restriction with its current and new value."),
            t("Nếu Xem trước báo “Đã ở trạng thái tối ưu” thì không còn gì cần sửa, nên cũng không có nút áp dụng.", "If Preview says “Already optimal”, nothing needs fixing, so there is no apply button."),
            t("Bấm “Chạy thử an toàn” để mô phỏng trước; xong sẽ hiện nút “Áp dụng thật” để làm thật.", "Tap “Safe dry run” to simulate first; then “Apply for real” appears.")
        ),
        done = state.diagnosticCompleted, action = t("Mở Xem trước", "Open Preview") to GuideTab.PREVIEW
    ),
    GuideStep(
        Icons.Outlined.Backup, t("4. Áp dụng và khôi phục", "4. Apply and restore"), t("Mọi thay đổi đều được sao lưu và đọc lại để xác minh", "Every change is backed up and read back to verify"),
        listOf(
            t("Không có nút sao lưu riêng: bấm “Áp dụng thật” là app tự sao lưu trạng thái gốc rồi mới ghi, sau đó đọc lại từng thay đổi. Nếu một mục không khớp, các mục trước đó được hoàn tác.", "There is no separate backup button: “Apply for real” backs up the original state before writing, then reads back every change. If one item does not match, the earlier ones are undone."),
            t("Mỗi lần áp dụng thành một phiên trong tab Lịch sử; bấm “Khôi phục” để trả đúng trạng thái trước phiên đó.", "Each apply becomes a session in the History tab; tap “Restore” to return to the exact state before that session."),
            t("Trong tab Ứng dụng → Đã tối ưu, bấm “Khôi phục” cạnh từng app để trả app đó về cài đặt gốc và bỏ chọn nó.", "In Apps → Optimized, tap “Restore” next to an app to return it to its original settings and deselect it."),
            t("App đã tối ưu sẵn từ trước (ví dụ sau khi gỡ và cài lại HyperOS Toolkit) không có bản sao lưu: “Khôi phục” sẽ đưa app về mặc định Android.", "Apps optimized earlier (for example before reinstalling HyperOS Toolkit) have no backup: “Restore” resets them to Android defaults.")
        ),
        done = state.allOptimized.isNotEmpty(), action = t("Mở Lịch sử", "Open History") to GuideTab.HISTORY
    ),
    GuideStep(
        Icons.Outlined.Shield, t("5. Cài MILLET helper", "5. Install the MILLET helper"), t("Giữ whitelist sau khi khởi động lại, không cần Shizuku", "Keeps the whitelist after reboots, without Shizuku"),
        listOf(
            t("HyperOS thường xóa Google khỏi whitelist MILLET sau khi khởi động lại hoặc khi PowerKeeper cập nhật.", "HyperOS often removes Google from the MILLET whitelist after a reboot or when PowerKeeper updates."),
            t("Trong Cài đặt bấm “Cài helper” (cần Shizuku một lần). Helper là app nhỏ không có biểu tượng, chuyên ghi lại whitelist.", "In Settings tap “Install helper” (needs Shizuku once). The helper is a small app without an icon that rewrites the whitelist."),
            t("Helper được báo ngay khi danh sách bị đổi và tự thêm lại, không chạy nền liên tục nên gần như không tốn pin.", "The helper is notified as soon as the list changes and adds the apps back; it does not run all the time, so it uses almost no battery."),
            t("Mục “HyperOS đã đánh thức helper” có thời gian nghĩa là cơ chế tự sửa đã hoạt động thật trên ROM của bạn.", "A time next to “HyperOS woke the helper” means self-repair has really worked on your ROM."),
            t("Nếu Android cảnh báo app cũ hoặc Play Protect hỏi, hãy chọn tiếp tục cài. Có thể gỡ helper bất cứ lúc nào.", "If Android warns about an old app or Play Protect asks, choose to install anyway. You can remove the helper at any time.")
        ),
        done = state.helperInstalled && state.helperStatus?.lastSuccess == true, action = t("Mở Cài đặt", "Open Settings") to GuideTab.SETTINGS
    ),
    GuideStep(
        Icons.Outlined.RocketLaunch, t("6. Cho phép HyperOS Toolkit chạy nền", "6. Let HyperOS Toolkit run in the background"), t("Tự khởi chạy và không tối ưu pin", "Autostart and no battery optimization"),
        listOf(
            t("Bật Tự khởi chạy để HyperOS Toolkit nhận sự kiện khởi động máy.", "Turn on Autostart so HyperOS Toolkit receives the boot event."),
            t("Chọn “Không hạn chế” trong tiết kiệm pin để dịch vụ Aurogon (nếu bật) không bị dừng.", "Choose “No restrictions” in battery saver so the Aurogon service (if on) is not stopped."),
            t("Nên làm thêm cho từng app đã chọn: bật Tự khởi chạy, Tiết kiệm pin = Không hạn chế, cho phép thông báo trên màn hình khóa.", "Also worth doing for each selected app: Autostart on, Battery saver = No restrictions, notifications allowed on the lock screen.")
        ),
        done = state.autostart == AutostartState.ALLOWED && state.batteryExempt, action = t("Mở Cài đặt", "Open Settings") to GuideTab.SETTINGS
    ),
    GuideStep(
        Icons.Outlined.CloudSync, t("7. Kiểm tra kết nối FCM", "7. Check the FCM connection"), t("Kênh push dùng chung của Google", "Google's shared push channel"),
        listOf(
            t("Thẻ FCM ở Trang chủ hỏi Google Play services xem kết nối push có đang mở không.", "The FCM card on Home asks Google Play services whether the push connection is open."),
            t("Nếu “Mất kết nối”, bấm “Kết nối lại”: app gửi heartbeat trước, chỉ ép kết nối lại khi vẫn còn mất.", "If it says “Disconnected”, tap “Reconnect”: the app sends a heartbeat first and only forces a reconnect if it is still down."),
            t("“Không rõ” nghĩa là GMS không trả lời; không phải lúc nào cũng là lỗi.", "“Unknown” means GMS did not answer; that is not always a problem.")
        ),
        done = state.fcm?.state?.let { it == FcmState.CONNECTED }, action = t("Mở Trang chủ", "Open Home") to GuideTab.HOME
    ),
    GuideStep(
        Icons.Outlined.Tune, t("8. Tùy chọn khi vẫn còn trễ", "8. Options if notifications are still late"), t("Chỉ bật khi các bước trên chưa đủ", "Only turn these on if the steps above were not enough"),
        listOf(
            t("Giữ allowlist Aurogon: làm mới danh sách cho phép của HyperOS sau khi tắt màn hình 40 giây, 2 phút rồi mỗi 10 phút. Có thông báo thường trực và tốn thêm chút pin.", "Keep the Aurogon allowlist: refreshes HyperOS's allowlist 40 seconds and 2 minutes after the screen turns off, then every 10 minutes. Shows a permanent notification and uses a little more battery."),
            t("Tắt bộ giới hạn GMS của Greezer (Tùy chọn nâng cao): ngăn HyperOS đóng băng Google Play services. Cần Shizuku và mất tác dụng sau khi khởi động lại.", "Turn off Greezer's GMS limiter (Advanced options): stops HyperOS freezing Google Play services. Needs Shizuku and is lost after a reboot."),
            t("Các mục nâng cao khác (Autostart nội bộ, dữ liệu nền, AppOps mạnh, profile toàn hệ thống) đều có sao lưu nhưng có thể làm hao pin.", "The other advanced items (internal Autostart, background data, aggressive AppOps, system-wide profile) are backed up but can drain the battery.")
        ),
        action = t("Mở Cài đặt", "Open Settings") to GuideTab.SETTINGS
    ),
    GuideStep(
        Icons.Outlined.Mic, t("9. Chọn trợ lý AI cho nút nguồn", "9. Choose the AI assistant for the power key"), "Gemini, Claude, ChatGPT… thay cho Xiao AI",
        listOf(
            t("Trong Cài đặt, thẻ “Trợ lý AI cho nút nguồn” liệt kê mọi app trợ lý đã cài. Chọn một app rồi bấm “Áp dụng” (cần Shizuku).", "In Settings, the “Power-key AI assistant” card lists every installed assistant. Pick one and tap “Apply” (needs Shizuku)."),
            t("Áp dụng sẽ cấp micro và quyền mở cửa sổ khi chạy nền (Xiaomi chặn Claude, ChatGPT… nếu thiếu), đặt app làm trợ lý, đổi hành động giữ nút nguồn, tắt Xiao AI và bật “Tự giữ sau khi khởi động lại”.", "Apply grants the microphone and the background pop-up permission (Xiaomi blocks Claude, ChatGPT… without it), makes the app the assistant, changes the power-key long press, turns off Xiao AI and turns on “Keep after reboot”."),
            t("HyperOS CN xóa dịch vụ trợ lý sau mỗi lần khởi động; phần tự giữ đặt lại ngay khi mở khóa, không cần Shizuku.", "HyperOS China clears the assistant service on every boot; the guard sets it again as soon as you unlock, without Shizuku."),
            t("Muốn quay về Xiao AI: chọn Xiao AI rồi bấm Áp dụng.", "To go back to Xiao AI: pick Xiao AI and tap Apply.")
        ),
        done = state.assistantGuard?.let { it.enabled && it.healthy }, action = t("Mở Cài đặt", "Open Settings") to GuideTab.SETTINGS
    ),
    GuideStep(
        Icons.AutoMirrored.Outlined.HelpOutline, t("Câu hỏi thường gặp", "FAQ"), t("Giới hạn của app", "What the app cannot do"),
        listOf(
            t("Có bảo đảm thông báo đến tức thì không? Không. Còn phụ thuộc máy chủ gửi, độ ưu tiên FCM, mạng và cách ROM xử lý.", "Are instant notifications guaranteed? No. They also depend on the sender's server, FCM priority, the network and the ROM."),
            t("Sau khi cập nhật HyperOS cần làm gì? Mở app và chạy kiểm tra lại; bằng chứng tự sửa của helper được đặt lại cho bản ROM mới.", "What to do after a HyperOS update? Open the app and run the check again; the helper's self-repair evidence is reset for the new ROM."),
            t("Gỡ HyperOS Toolkit có trả lại cài đặt cũ không? Không, và bản sao lưu cũng mất theo app. Hãy khôi phục toàn bộ trong tab Ứng dụng và gỡ helper trước khi gỡ app. Cập nhật (cài đè) thì giữ nguyên.", "Does uninstalling HyperOS Toolkit restore the old settings? No, and the backups go with the app. Restore everything in the Apps tab and remove the helper before uninstalling. Updating (installing over it) keeps everything."),
            t("Có cần root không? Không. Shizuku chỉ cần cho các thao tác có quyền shell.", "Is root needed? No. Shizuku is only needed for actions that require shell access.")
        )
    ),
)

@Composable
fun GuideScreen(state: UiState, onNavigate: (Int) -> Unit) {
    val steps = guideSteps(state)
    val tracked = steps.mapNotNull { it.done }
    var expanded by rememberSaveable { mutableIntStateOf(steps.indexOfFirst { it.done == false }.coerceAtLeast(0)) }
    val accent = MaterialTheme.colorScheme.primary
    val doneColor = MaterialTheme.colorScheme.secondary
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("Hướng dẫn sử dụng", "User guide"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(t("Làm lần lượt từ trên xuống. Dấu tích dựa trên trạng thái thật của máy.", "Go from top to bottom. Check marks reflect the phone's real state."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(progress = { tracked.count { it } / tracked.size.toFloat() }, modifier = Modifier.fillMaxWidth().height(6.dp), color = doneColor)
                Text(t("Đã xong ${tracked.count { it }}/${tracked.size} bước", "${tracked.count { it }}/${tracked.size} steps done"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(steps.size) { index ->
            val step = steps[index]
            val open = expanded == index
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.animateContentSize()) {
                Column(Modifier.fillMaxWidth().clickable { expanded = if (open) -1 else index }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(if (step.done == true) Icons.Filled.CheckCircle else step.icon, null, tint = if (step.done == true) doneColor else accent)
                        Column(Modifier.weight(1f)) {
                            Text(step.title, fontWeight = FontWeight.SemiBold)
                            Text(step.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (open) t("Thu gọn", "Collapse") else t("Mở rộng", "Expand"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (open) {
                        step.details.forEach { line ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("•", color = accent)
                                Text(line, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        step.action?.let { (label, tab) -> FilledTonalButton({ onNavigate(tab) }, Modifier.fillMaxWidth()) { Text(label) } }
                    }
                }
            }
        }
    }
}
