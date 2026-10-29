package io.github.hypernotifyfix

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
        Icons.Outlined.Adb, "1. Khởi động Shizuku", "Cần cho bước kiểm tra, áp dụng và cài helper",
        listOf(
            "Cài Shizuku, bật Tùy chọn nhà phát triển → Gỡ lỗi không dây.",
            "Trong Shizuku chọn “Ghép nối”, nhập mã ghép nối, rồi bấm “Bắt đầu”.",
            "Quay lại HyperNotify và cấp quyền khi được hỏi. Trang chủ phải hiện “Đã kết nối”.",
            "Shizuku sẽ dừng sau mỗi lần khởi động lại máy; chỉ cần bật lại khi muốn đổi cấu hình."
        ),
        done = state.uid == 2000, action = "Mở Trang chủ" to GuideTab.HOME
    ),
    GuideStep(
        Icons.Outlined.Apps, "2. Chọn ứng dụng bị trễ thông báo", "Ví dụ Messenger, Zalo, Gmail, ngân hàng",
        listOf(
            "Vào tab Ứng dụng, tìm và chọn những app hay nhận thông báo chậm.",
            "Google Play services và Google Services Framework luôn được kiểm tra kèm, vì mọi thông báo FCM đều đi qua chúng.",
            "Danh sách đã chọn cũng là danh sách được bảo vệ tự động sau khi khởi động lại."
        ),
        done = state.selected.isNotEmpty(), action = "Chọn ứng dụng" to GuideTab.APPS
    ),
    GuideStep(
        Icons.Outlined.HealthAndSafety, "3. Chạy kiểm tra và xem trước", "Không thay đổi gì cho tới khi bạn xác nhận",
        listOf(
            "Ở Trang chủ bấm “Chạy kiểm tra”. App đọc trạng thái Doze, AppOps, App Standby và whitelist MILLET.",
            "Tab Xem trước liệt kê từng giới hạn, giá trị hiện tại và giá trị mới.",
            "Nếu Xem trước báo “Đã ở trạng thái tối ưu” thì không còn gì cần sửa, nên cũng không có nút áp dụng.",
            "Bấm “Chạy thử an toàn” để mô phỏng trước; xong sẽ hiện nút “Áp dụng thật” để làm thật."
        ),
        done = state.diagnosticCompleted, action = "Mở Xem trước" to GuideTab.PREVIEW
    ),
    GuideStep(
        Icons.Outlined.Backup, "4. Áp dụng và khôi phục", "Mọi thay đổi đều được sao lưu và đọc lại để xác minh",
        listOf(
            "Không có nút sao lưu riêng: bấm “Áp dụng thật” là app tự sao lưu trạng thái gốc rồi mới ghi, sau đó đọc lại từng thay đổi. Nếu một mục không khớp, các mục trước đó được hoàn tác.",
            "Mỗi lần áp dụng thành một phiên trong tab Lịch sử; bấm “Khôi phục” để trả đúng trạng thái trước phiên đó.",
            "Trong tab Ứng dụng có thể hoàn tác riêng từng app (nút ↺) hoặc khôi phục toàn bộ.",
            "App đã tối ưu sẵn từ trước (ví dụ sau khi gỡ và cài lại HyperNotify) vẫn được tính là đã tối ưu nhưng không có bản sao lưu để hoàn tác."
        ),
        done = state.allOptimized.isNotEmpty(), action = "Mở Lịch sử" to GuideTab.HISTORY
    ),
    GuideStep(
        Icons.Outlined.Shield, "5. Cài MILLET helper", "Giữ whitelist sau khi khởi động lại, không cần Shizuku",
        listOf(
            "HyperOS thường xóa Google khỏi whitelist MILLET sau khi khởi động lại hoặc khi PowerKeeper cập nhật.",
            "Trong Cài đặt bấm “Cài helper” (cần Shizuku một lần). Helper là app nhỏ không có biểu tượng, chuyên ghi lại whitelist.",
            "Helper được báo ngay khi danh sách bị đổi và tự thêm lại, không chạy nền liên tục nên gần như không tốn pin.",
            "Mục “HyperOS đã đánh thức helper” có thời gian nghĩa là cơ chế tự sửa đã hoạt động thật trên ROM của bạn.",
            "Nếu Android cảnh báo app cũ hoặc Play Protect hỏi, hãy chọn tiếp tục cài. Có thể gỡ helper bất cứ lúc nào."
        ),
        done = state.helperInstalled && state.helperStatus?.lastSuccess == true, action = "Mở Cài đặt" to GuideTab.SETTINGS
    ),
    GuideStep(
        Icons.Outlined.RocketLaunch, "6. Cho phép HyperNotify chạy nền", "Tự khởi chạy và không tối ưu pin",
        listOf(
            "Bật Tự khởi chạy để HyperNotify nhận sự kiện khởi động máy.",
            "Chọn “Không hạn chế” trong tiết kiệm pin để dịch vụ Aurogon (nếu bật) không bị dừng.",
            "Nên làm thêm cho từng app đã chọn: bật Tự khởi chạy, Tiết kiệm pin = Không hạn chế, cho phép thông báo trên màn hình khóa."
        ),
        done = state.autostart == AutostartState.ALLOWED && state.batteryExempt, action = "Mở Cài đặt" to GuideTab.SETTINGS
    ),
    GuideStep(
        Icons.Outlined.CloudSync, "7. Kiểm tra kết nối FCM", "Kênh push dùng chung của Google",
        listOf(
            "Thẻ FCM ở Trang chủ hỏi Google Play services xem kết nối push có đang mở không.",
            "Nếu “Mất kết nối”, bấm “Kết nối lại”: app gửi heartbeat trước, chỉ ép kết nối lại khi vẫn còn mất.",
            "“Không rõ” nghĩa là GMS không trả lời; không phải lúc nào cũng là lỗi."
        ),
        done = state.fcm?.state?.let { it == FcmState.CONNECTED }, action = "Mở Trang chủ" to GuideTab.HOME
    ),
    GuideStep(
        Icons.Outlined.Tune, "8. Tùy chọn khi vẫn còn trễ", "Chỉ bật khi các bước trên chưa đủ",
        listOf(
            "Giữ allowlist Aurogon: làm mới danh sách cho phép của HyperOS sau khi tắt màn hình 40 giây, 2 phút rồi mỗi 10 phút. Có thông báo thường trực và tốn thêm chút pin.",
            "Tắt bộ giới hạn GMS của Greezer (Tùy chọn nâng cao): ngăn HyperOS đóng băng Google Play services. Cần Shizuku và mất tác dụng sau khi khởi động lại.",
            "Các mục nâng cao khác (Autostart nội bộ, dữ liệu nền, AppOps mạnh, profile toàn hệ thống) đều có sao lưu nhưng có thể làm hao pin."
        ),
        action = "Mở Cài đặt" to GuideTab.SETTINGS
    ),
    GuideStep(
        Icons.Outlined.Mic, "9. Giữ Gemini cho nút nguồn", "Chỉ cần nếu đã gỡ Xiao AI trên ROM nội địa",
        listOf(
            "HyperOS CN xóa dịch vụ trợ lý sau mỗi lần khởi động lại, nên giữ nút nguồn không mở được Gemini.",
            "Trong Cài đặt bật “Giữ trợ lý cho nút nguồn” (cần Shizuku một lần). App tự đặt lại trợ lý ngay khi mở khóa.",
            "Thẻ đó hiện trợ lý đã chọn, dịch vụ đang dùng và lần tự sửa gần nhất."
        ),
        done = state.assistantGuard?.let { it.enabled && it.healthy }, action = "Mở Cài đặt" to GuideTab.SETTINGS
    ),
    GuideStep(
        Icons.AutoMirrored.Outlined.HelpOutline, "Câu hỏi thường gặp", "Giới hạn của app",
        listOf(
            "Có bảo đảm thông báo đến tức thì không? Không. Còn phụ thuộc máy chủ gửi, độ ưu tiên FCM, mạng và cách ROM xử lý.",
            "Sau khi cập nhật HyperOS cần làm gì? Mở app và chạy kiểm tra lại; bằng chứng tự sửa của helper được đặt lại cho bản ROM mới.",
            "Gỡ HyperNotify có trả lại cài đặt cũ không? Không, và bản sao lưu cũng mất theo app. Hãy khôi phục toàn bộ trong tab Ứng dụng và gỡ helper trước khi gỡ app. Cập nhật (cài đè) thì giữ nguyên.",
            "Có cần root không? Không. Shizuku chỉ cần cho các thao tác có quyền shell."
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
                Text("Hướng dẫn sử dụng", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Làm lần lượt từ trên xuống. Dấu tích dựa trên trạng thái thật của máy.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(progress = { tracked.count { it } / tracked.size.toFloat() }, modifier = Modifier.fillMaxWidth().height(6.dp), color = doneColor)
                Text("Đã xong ${tracked.count { it }}/${tracked.size} bước", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (open) "Thu gọn" else "Mở rộng", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
