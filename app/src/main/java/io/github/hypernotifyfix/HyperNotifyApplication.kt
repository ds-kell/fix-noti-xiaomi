package io.github.hypernotifyfix
import android.app.Application
import io.github.hypernotifyfix.core.AppContainer
class HyperNotifyApplication : Application() {
    val container by lazy { AppContainer(this) }
    override fun onCreate() { super.onCreate(); io.github.hypernotifyfix.core.I18n.init(this) }
}
