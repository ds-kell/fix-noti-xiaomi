package io.github.hypernotifyfix.toolkit

import io.github.hypernotifyfix.core.Txt
import io.github.hypernotifyfix.core.shell.PrivilegedCommand

enum class DebloatGroup(val title: Txt, val subtitle: Txt) {
    ADS(Txt("Quảng cáo & thu thập dữ liệu", "Ads & data collection"), Txt("Gỡ MSA và các dịch vụ này là tắt phần lớn quảng cáo trong app hệ thống", "Removing MSA and these services turns off most ads in system apps")),
    BLOAT(Txt("App Xiaomi ít dùng", "Rarely used Xiaomi apps"), Txt("App cài sẵn mà nhiều người không dùng tới", "Preinstalled apps many people never use")),
    CHINA(Txt("App Trung Quốc cài sẵn", "Preinstalled Chinese apps"), Txt("Thường có trên máy xách tay bản Trung Quốc", "Usually found on China-ROM phones")),
}

/** SAFE: widely removed by the community without side effects. CAUTION: removable, but read [note] first. */
enum class Safety { SAFE, CAUTION }

data class DebloatEntry(val packageName: String, val label: Txt, val group: DebloatGroup, val safety: Safety = Safety.SAFE, val note: Txt? = null)

/**
 * Curated list for HyperOS / MIUI. Only packages that are present on the device are shown.
 * Never add launchers, Security, Settings, SystemUI, Phone, Contacts, Messages, the input method
 * a user is likely to rely on without a warning, or anything the framework binds to at boot.
 */
object DebloatCatalog {
    private val KEYBOARD = Txt("Là bàn phím: cài và chọn bàn phím khác (ví dụ Gboard) trước khi gỡ", "A keyboard: install and switch to another one (e.g. Gboard) before removing")
    val entries = listOf(
        DebloatEntry("com.miui.msa.global", Txt("MSA – dịch vụ quảng cáo (quốc tế)", "MSA – ad service (global)"), DebloatGroup.ADS),
        DebloatEntry("com.miui.systemAdSolution", Txt("MSA – dịch vụ quảng cáo (Trung Quốc)", "MSA – ad service (China)"), DebloatGroup.ADS),
        DebloatEntry("com.miui.analytics", Txt("Analytics – thu thập dữ liệu sử dụng", "Analytics – usage data collection"), DebloatGroup.ADS),
        DebloatEntry("com.miui.daemon", Txt("MIUI Daemon – gửi thống kê", "MIUI Daemon – sends statistics"), DebloatGroup.ADS, Safety.CAUTION, Txt("Một số máy dùng cho báo cáo hiệu năng; gỡ không ảnh hưởng chức năng chính", "Some phones use it for performance reports; removing it does not affect core features")),
        DebloatEntry("com.xiaomi.mipicks", Txt("GetApps – gợi ý cài app", "GetApps – app install suggestions"), DebloatGroup.ADS),
        DebloatEntry("com.miui.android.fashiongallery", Txt("Hình nền màn khoá tự đổi (có quảng cáo)", "Lock screen wallpaper carousel (shows ads)"), DebloatGroup.ADS),
        DebloatEntry("com.miui.hybrid", Txt("Quick Apps", "Quick Apps"), DebloatGroup.ADS),
        DebloatEntry("com.miui.hybrid.accessory", Txt("Quick Apps – phụ kiện", "Quick Apps – accessory"), DebloatGroup.ADS),
        DebloatEntry("com.miui.newhome", Txt("Bảng tin trên màn hình chính", "Home screen news feed"), DebloatGroup.ADS),
        DebloatEntry("com.facebook.services", Txt("Facebook Services", "Facebook Services"), DebloatGroup.ADS),
        DebloatEntry("com.facebook.system", Txt("Facebook App Installer", "Facebook App Installer"), DebloatGroup.ADS),
        DebloatEntry("com.facebook.appmanager", Txt("Facebook App Manager", "Facebook App Manager"), DebloatGroup.ADS),

        DebloatEntry("com.miui.videoplayer", Txt("Mi Video", "Mi Video"), DebloatGroup.BLOAT),
        DebloatEntry("com.miui.player", Txt("Mi Music", "Mi Music"), DebloatGroup.BLOAT),
        DebloatEntry("com.mi.globalbrowser", Txt("Mi Browser", "Mi Browser"), DebloatGroup.BLOAT, Safety.CAUTION, Txt("Đặt trình duyệt khác làm mặc định trước", "Set another browser as default first")),
        DebloatEntry("com.android.browser", Txt("Trình duyệt Xiaomi (bản Trung Quốc)", "Xiaomi Browser (China)"), DebloatGroup.BLOAT, Safety.CAUTION, Txt("Đặt trình duyệt khác làm mặc định trước", "Set another browser as default first")),
        DebloatEntry("com.miui.yellowpage", Txt("Trang vàng – danh bạ doanh nghiệp", "Yellow Pages – business directory"), DebloatGroup.BLOAT),
        DebloatEntry("com.xiaomi.glgm", Txt("Games", "Games"), DebloatGroup.BLOAT),
        DebloatEntry("com.xiaomi.gamecenter", Txt("Trung tâm game", "Game Center"), DebloatGroup.BLOAT),
        DebloatEntry("com.miui.bugreport", Txt("Góp ý & báo lỗi", "Feedback & bug report"), DebloatGroup.BLOAT),
        DebloatEntry("com.miui.compass", Txt("La bàn", "Compass"), DebloatGroup.BLOAT),
        DebloatEntry("com.duokan.phone.remotecontroller", Txt("Mi Remote (điều khiển hồng ngoại)", "Mi Remote (IR remote control)"), DebloatGroup.BLOAT),
        DebloatEntry("com.miui.virtualsim", Txt("Mi Mobile / SIM ảo", "Mi Mobile / virtual SIM"), DebloatGroup.BLOAT),
        DebloatEntry("com.xiaomi.vipaccount", Txt("Cộng đồng Xiaomi", "Xiaomi Community"), DebloatGroup.BLOAT),
        DebloatEntry("com.xiaomi.shop", Txt("Xiaomi Mall", "Xiaomi Mall"), DebloatGroup.BLOAT),
        DebloatEntry("com.mipay.wallet", Txt("Ví Mi Pay", "Mi Pay wallet"), DebloatGroup.BLOAT, Safety.CAUTION, Txt("Cần cho thẻ NFC/xe buýt ở Trung Quốc", "Needed for NFC transit cards in China")),
        DebloatEntry("com.mi.android.globalminusscreen", Txt("App Vault (màn hình bên trái)", "App Vault (left screen)"), DebloatGroup.BLOAT, Safety.CAUTION, Txt("Mất màn hình tiện ích khi vuốt sang trái", "You lose the widget screen on the left")),
        DebloatEntry("com.miui.personalassistant", Txt("Màn hình tiện ích bên trái", "Left widget screen"), DebloatGroup.BLOAT, Safety.CAUTION, Txt("Mất màn hình tiện ích khi vuốt sang trái", "You lose the widget screen on the left")),
        DebloatEntry("com.miui.cleanmaster", Txt("Dọn dẹp (có quảng cáo)", "Cleaner (shows ads)"), DebloatGroup.BLOAT, Safety.CAUTION, Txt("Nút Dọn dẹp trong app Bảo mật sẽ không mở được", "The Cleaner button in the Security app will stop working")),
        DebloatEntry("com.xiaomi.market", Txt("Cửa hàng Xiaomi", "Xiaomi App Store"), DebloatGroup.BLOAT, Safety.CAUTION, Txt("Bản Trung Quốc dùng nó để cập nhật app hệ thống", "China ROMs use it to update system apps")),
        DebloatEntry("com.netflix.partner.activation", Txt("Netflix kích hoạt sẵn", "Netflix preinstall activation"), DebloatGroup.BLOAT),
        DebloatEntry("com.netflix.mediaclient", Txt("Netflix", "Netflix"), DebloatGroup.BLOAT),

        DebloatEntry("com.taobao.taobao", Txt("Taobao", "Taobao"), DebloatGroup.CHINA),
        DebloatEntry("com.jingdong.app.mall", Txt("JD.com", "JD.com"), DebloatGroup.CHINA),
        DebloatEntry("com.xunmeng.pinduoduo", Txt("Pinduoduo", "Pinduoduo"), DebloatGroup.CHINA),
        DebloatEntry("com.ss.android.ugc.aweme", Txt("Douyin", "Douyin"), DebloatGroup.CHINA),
        DebloatEntry("com.smile.gifmaker", Txt("Kuaishou", "Kuaishou"), DebloatGroup.CHINA),
        DebloatEntry("com.ss.android.article.news", Txt("Toutiao", "Toutiao"), DebloatGroup.CHINA),
        DebloatEntry("com.baidu.searchbox", Txt("Baidu", "Baidu"), DebloatGroup.CHINA),
        DebloatEntry("com.sina.weibo", Txt("Weibo", "Weibo"), DebloatGroup.CHINA),
        DebloatEntry("com.zhihu.android", Txt("Zhihu", "Zhihu"), DebloatGroup.CHINA),
        DebloatEntry("com.tencent.qqlive", Txt("Tencent Video", "Tencent Video"), DebloatGroup.CHINA),
        DebloatEntry("com.youku.phone", Txt("Youku", "Youku"), DebloatGroup.CHINA),
        DebloatEntry("com.qiyi.video", Txt("iQIYI", "iQIYI"), DebloatGroup.CHINA),
        DebloatEntry("com.dragon.read", Txt("Fanqie Novel", "Fanqie Novel"), DebloatGroup.CHINA),
        DebloatEntry("com.autonavi.minimap", Txt("Amap (bản đồ)", "Amap (maps)"), DebloatGroup.CHINA),
        DebloatEntry("com.sohu.inputmethod.sogou.xiaomi", Txt("Bàn phím Sogou", "Sogou keyboard"), DebloatGroup.CHINA, Safety.CAUTION, KEYBOARD),
        DebloatEntry("com.iflytek.inputmethod.miui", Txt("Bàn phím iFlytek", "iFlytek keyboard"), DebloatGroup.CHINA, Safety.CAUTION, KEYBOARD),
        DebloatEntry("com.baidu.input_mi", Txt("Bàn phím Baidu", "Baidu keyboard"), DebloatGroup.CHINA, Safety.CAUTION, KEYBOARD),
    )
    val byPackage = entries.associateBy { it.packageName }
}

/** Apps commonly hit by HyperOS background killing; tapping a preset pre-selects the installed ones. */
data class KeepAlivePreset(val title: Txt, val subtitle: Txt, val packages: List<String>)

object KeepAlivePresets {
    val all = listOf(
        KeepAlivePreset(Txt("Nhắn tin", "Messaging"), Txt("Zalo, Messenger, Telegram, WhatsApp, Viber, Signal", "Zalo, Messenger, Telegram, WhatsApp, Viber, Signal"), listOf(
            "com.zing.zalo", "com.facebook.orca", "com.facebook.katana", "org.telegram.messenger", "com.whatsapp", "com.viber.voip", "org.thoughtcrime.securesms", "com.discord", "com.microsoft.teams", "com.Slack")),
        KeepAlivePreset(Txt("Email & lịch", "Email & calendar"), Txt("Gmail, Outlook, Google Calendar", "Gmail, Outlook, Google Calendar"), listOf(
            "com.google.android.gm", "com.microsoft.office.outlook", "com.google.android.calendar")),
        KeepAlivePreset(Txt("Đồng hồ & vòng tay", "Watches & bands"), Txt("Mi Fitness, Zepp, Garmin, Huawei Health, Samsung Wear", "Mi Fitness, Zepp, Garmin, Huawei Health, Samsung Wear"), listOf(
            "com.xiaomi.wearable", "com.mi.health", "com.xiaomi.hm.health", "com.huami.watch.hmwatchmanager", "com.garmin.android.apps.connectmobile", "com.huawei.health", "com.samsung.android.app.watchmanager", "com.google.android.apps.wear.companion")),
        KeepAlivePreset(Txt("Nghe nhạc", "Music"), Txt("Spotify, YouTube Music, Zing MP3, NhacCuaTui", "Spotify, YouTube Music, Zing MP3, NhacCuaTui"), listOf(
            "com.spotify.music", "com.google.android.apps.youtube.music", "com.zing.mp3", "ht.nct")),
        KeepAlivePreset(Txt("Tài xế", "Drivers"), Txt("Grab Driver; app khác chọn tay ở tab Ứng dụng", "Grab Driver; pick other apps by hand in the Apps tab"), listOf(
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
