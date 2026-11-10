package io.github.hypernotifyfix.toolkit

import io.github.hypernotifyfix.core.shell.PrivilegedCommand

enum class DebloatGroup(val title: String, val subtitle: String) {
    ADS("Quảng cáo & thu thập dữ liệu", "Gỡ MSA và các dịch vụ này là tắt phần lớn quảng cáo trong app hệ thống"),
    BLOAT("App Xiaomi ít dùng", "App cài sẵn mà nhiều người không dùng tới"),
    CHINA("App Trung Quốc cài sẵn", "Thường có trên máy xách tay bản Trung Quốc"),
}

/** SAFE: widely removed by the community without side effects. CAUTION: removable, but read [note] first. */
enum class Safety { SAFE, CAUTION }

data class DebloatEntry(val packageName: String, val label: String, val group: DebloatGroup, val safety: Safety = Safety.SAFE, val note: String? = null)

/**
 * Curated list for HyperOS / MIUI. Only packages that are present on the device are shown.
 * Never add launchers, Security, Settings, SystemUI, Phone, Contacts, Messages, the input method
 * a user is likely to rely on without a warning, or anything the framework binds to at boot.
 */
object DebloatCatalog {
    private const val KEYBOARD = "Là bàn phím: cài và chọn bàn phím khác (ví dụ Gboard) trước khi gỡ"
    val entries = listOf(
        DebloatEntry("com.miui.msa.global", "MSA – dịch vụ quảng cáo (quốc tế)", DebloatGroup.ADS),
        DebloatEntry("com.miui.systemAdSolution", "MSA – dịch vụ quảng cáo (Trung Quốc)", DebloatGroup.ADS),
        DebloatEntry("com.miui.analytics", "Analytics – thu thập dữ liệu sử dụng", DebloatGroup.ADS),
        DebloatEntry("com.miui.daemon", "MIUI Daemon – gửi thống kê", DebloatGroup.ADS, Safety.CAUTION, "Một số máy dùng cho báo cáo hiệu năng; gỡ không ảnh hưởng chức năng chính"),
        DebloatEntry("com.xiaomi.mipicks", "GetApps – gợi ý cài app", DebloatGroup.ADS),
        DebloatEntry("com.miui.android.fashiongallery", "Hình nền màn khoá tự đổi (có quảng cáo)", DebloatGroup.ADS),
        DebloatEntry("com.miui.hybrid", "Quick Apps", DebloatGroup.ADS),
        DebloatEntry("com.miui.hybrid.accessory", "Quick Apps – phụ kiện", DebloatGroup.ADS),
        DebloatEntry("com.miui.newhome", "Bảng tin trên màn hình chính", DebloatGroup.ADS),
        DebloatEntry("com.facebook.services", "Facebook Services", DebloatGroup.ADS),
        DebloatEntry("com.facebook.system", "Facebook App Installer", DebloatGroup.ADS),
        DebloatEntry("com.facebook.appmanager", "Facebook App Manager", DebloatGroup.ADS),

        DebloatEntry("com.miui.videoplayer", "Mi Video", DebloatGroup.BLOAT),
        DebloatEntry("com.miui.player", "Mi Music", DebloatGroup.BLOAT),
        DebloatEntry("com.mi.globalbrowser", "Mi Browser", DebloatGroup.BLOAT, Safety.CAUTION, "Đặt trình duyệt khác làm mặc định trước"),
        DebloatEntry("com.android.browser", "Trình duyệt Xiaomi (bản Trung Quốc)", DebloatGroup.BLOAT, Safety.CAUTION, "Đặt trình duyệt khác làm mặc định trước"),
        DebloatEntry("com.miui.yellowpage", "Trang vàng – danh bạ doanh nghiệp", DebloatGroup.BLOAT),
        DebloatEntry("com.xiaomi.glgm", "Games", DebloatGroup.BLOAT),
        DebloatEntry("com.xiaomi.gamecenter", "Trung tâm game", DebloatGroup.BLOAT),
        DebloatEntry("com.miui.bugreport", "Góp ý & báo lỗi", DebloatGroup.BLOAT),
        DebloatEntry("com.miui.compass", "La bàn", DebloatGroup.BLOAT),
        DebloatEntry("com.duokan.phone.remotecontroller", "Mi Remote (điều khiển hồng ngoại)", DebloatGroup.BLOAT),
        DebloatEntry("com.miui.virtualsim", "Mi Mobile / SIM ảo", DebloatGroup.BLOAT),
        DebloatEntry("com.xiaomi.vipaccount", "Cộng đồng Xiaomi", DebloatGroup.BLOAT),
        DebloatEntry("com.xiaomi.shop", "Xiaomi Mall", DebloatGroup.BLOAT),
        DebloatEntry("com.mipay.wallet", "Ví Mi Pay", DebloatGroup.BLOAT, Safety.CAUTION, "Cần cho thẻ NFC/xe buýt ở Trung Quốc"),
        DebloatEntry("com.mi.android.globalminusscreen", "App Vault (màn hình bên trái)", DebloatGroup.BLOAT, Safety.CAUTION, "Mất màn hình tiện ích khi vuốt sang trái"),
        DebloatEntry("com.miui.personalassistant", "Màn hình tiện ích bên trái", DebloatGroup.BLOAT, Safety.CAUTION, "Mất màn hình tiện ích khi vuốt sang trái"),
        DebloatEntry("com.miui.cleanmaster", "Dọn dẹp (có quảng cáo)", DebloatGroup.BLOAT, Safety.CAUTION, "Nút Dọn dẹp trong app Bảo mật sẽ không mở được"),
        DebloatEntry("com.xiaomi.market", "Cửa hàng Xiaomi", DebloatGroup.BLOAT, Safety.CAUTION, "Bản Trung Quốc dùng nó để cập nhật app hệ thống"),
        DebloatEntry("com.netflix.partner.activation", "Netflix kích hoạt sẵn", DebloatGroup.BLOAT),
        DebloatEntry("com.netflix.mediaclient", "Netflix", DebloatGroup.BLOAT),

        DebloatEntry("com.taobao.taobao", "Taobao", DebloatGroup.CHINA),
        DebloatEntry("com.jingdong.app.mall", "JD.com", DebloatGroup.CHINA),
        DebloatEntry("com.xunmeng.pinduoduo", "Pinduoduo", DebloatGroup.CHINA),
        DebloatEntry("com.ss.android.ugc.aweme", "Douyin", DebloatGroup.CHINA),
        DebloatEntry("com.smile.gifmaker", "Kuaishou", DebloatGroup.CHINA),
        DebloatEntry("com.ss.android.article.news", "Toutiao", DebloatGroup.CHINA),
        DebloatEntry("com.baidu.searchbox", "Baidu", DebloatGroup.CHINA),
        DebloatEntry("com.sina.weibo", "Weibo", DebloatGroup.CHINA),
        DebloatEntry("com.zhihu.android", "Zhihu", DebloatGroup.CHINA),
        DebloatEntry("com.tencent.qqlive", "Tencent Video", DebloatGroup.CHINA),
        DebloatEntry("com.youku.phone", "Youku", DebloatGroup.CHINA),
        DebloatEntry("com.qiyi.video", "iQIYI", DebloatGroup.CHINA),
        DebloatEntry("com.dragon.read", "Fanqie Novel", DebloatGroup.CHINA),
        DebloatEntry("com.autonavi.minimap", "Amap (bản đồ)", DebloatGroup.CHINA),
        DebloatEntry("com.sohu.inputmethod.sogou.xiaomi", "Bàn phím Sogou", DebloatGroup.CHINA, Safety.CAUTION, KEYBOARD),
        DebloatEntry("com.iflytek.inputmethod.miui", "Bàn phím iFlytek", DebloatGroup.CHINA, Safety.CAUTION, KEYBOARD),
        DebloatEntry("com.baidu.input_mi", "Bàn phím Baidu", DebloatGroup.CHINA, Safety.CAUTION, KEYBOARD),
    )
    val byPackage = entries.associateBy { it.packageName }
}

/** Apps commonly hit by HyperOS background killing; tapping a preset pre-selects the installed ones. */
data class KeepAlivePreset(val title: String, val subtitle: String, val packages: List<String>)

object KeepAlivePresets {
    val all = listOf(
        KeepAlivePreset("Nhắn tin", "Zalo, Messenger, Telegram, WhatsApp, Viber, Signal", listOf(
            "com.zing.zalo", "com.facebook.orca", "com.facebook.katana", "org.telegram.messenger", "com.whatsapp", "com.viber.voip", "org.thoughtcrime.securesms", "com.discord", "com.microsoft.teams", "com.Slack")),
        KeepAlivePreset("Email & lịch", "Gmail, Outlook, Google Calendar", listOf(
            "com.google.android.gm", "com.microsoft.office.outlook", "com.google.android.calendar")),
        KeepAlivePreset("Đồng hồ & vòng tay", "Mi Fitness, Zepp, Garmin, Huawei Health, Samsung Wear", listOf(
            "com.xiaomi.wearable", "com.mi.health", "com.xiaomi.hm.health", "com.huami.watch.hmwatchmanager", "com.garmin.android.apps.connectmobile", "com.huawei.health", "com.samsung.android.app.watchmanager", "com.google.android.apps.wear.companion")),
        KeepAlivePreset("Nghe nhạc", "Spotify, YouTube Music, Zing MP3, NhacCuaTui", listOf(
            "com.spotify.music", "com.google.android.apps.youtube.music", "com.zing.mp3", "ht.nct")),
        KeepAlivePreset("Tài xế", "Grab Driver; app khác chọn tay ở tab Ứng dụng", listOf(
            "com.grabtaxi.driver2")),
    )
}

/** Shell steps behind the toolkit. Package names only ever come from the catalog or PackageManager. */
object ToolkitCommands {
    const val ROLE_HOME = "android.app.role.HOME"
    const val ROLE_BROWSER = "android.app.role.BROWSER"
    private val PACKAGE = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")

    /** `-k` keeps data, `--user 0` leaves the APK on the system partition so it can be restored. */
    fun remove(packageName: String) = PrivilegedCommand("debloat.remove", listOf("pm", "uninstall", "-k", "--user", "0", checked(packageName)), 15_000)
    fun restore(packageName: String) = PrivilegedCommand("debloat.restore", listOf("cmd", "package", "install-existing", checked(packageName)), 15_000)

    fun roleHolders(role: String) = PrivilegedCommand("role.get", listOf("cmd", "role", "get-role-holders", checkedRole(role)))
    fun setRole(role: String, packageName: String, currentHolders: Set<String>): List<PrivilegedCommand> = buildList {
        (currentHolders - packageName).forEach { add(PrivilegedCommand("role.remove", listOf("cmd", "role", "remove-role-holder", checkedRole(role), checked(it)))) }
        add(PrivilegedCommand("role.add", listOf("cmd", "role", "add-role-holder", checkedRole(role), checked(packageName))))
    }

    /**
     * Turns developer options off, wireless debugging first: once adb goes down Shizuku stops, so this
     * must be the last thing the app asks Shizuku to do. Everything already applied stays in place.
     */
    val BANK_SAFE_SETTINGS = listOf("adb_wifi_enabled", "adb_enabled", "development_settings_enabled")
    fun bankSafe() = PrivilegedCommand("banksafe.off", listOf("sh", "-c", BANK_SAFE_SETTINGS.joinToString("; ") { "settings put global $it 0" }), 10_000)

    private fun checked(packageName: String) = packageName.also { require(it.matches(PACKAGE)) { "invalid package" } }
    private fun checkedRole(role: String) = role.also { require(it == ROLE_HOME || it == ROLE_BROWSER) { "unsupported role" } }
}
