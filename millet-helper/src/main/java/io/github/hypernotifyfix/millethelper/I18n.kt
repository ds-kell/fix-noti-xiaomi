package io.github.hypernotifyfix.millethelper

import java.util.Locale

/** The helper has no UI of its own; its status lines follow the phone language. */
fun t(vi: String, en: String): String = if (Locale.getDefault().language == "vi") vi else en
