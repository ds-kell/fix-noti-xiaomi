package io.github.hypernotifyfix.core

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import java.util.Locale

/** In-app language: follow the phone, or force Vietnamese / English. */
enum class AppLanguage { SYSTEM, VI, EN }

/**
 * Two-language UI without resource files: every user-visible string is written as `t("tiếng Việt", "English")`.
 * [current] is Compose state, so any composable that calls [t] recomposes when the language changes.
 */
object I18n {
    private const val PREFS = "ui_options"
    private const val KEY = "language"
    private val choice = mutableStateOf(AppLanguage.SYSTEM)
    private val english = mutableStateOf(!systemIsVietnamese())

    val language: AppLanguage get() = choice.value
    val isEnglish: Boolean get() = english.value

    fun init(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        apply(AppLanguage.entries.firstOrNull { it.name == saved } ?: AppLanguage.SYSTEM)
    }

    fun set(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language.name).apply()
        apply(language)
    }

    private fun apply(language: AppLanguage) {
        choice.value = language
        english.value = when (language) { AppLanguage.VI -> false; AppLanguage.EN -> true; AppLanguage.SYSTEM -> !systemIsVietnamese() }
    }

    private fun systemIsVietnamese() = Locale.getDefault().language == "vi"
}

fun t(vi: String, en: String): String = if (I18n.isEnglish) en else vi

/** A string kept in both languages, for catalogs built once; resolves on every read. */
class Txt(val vi: String, val en: String) { override fun toString() = t(vi, en) }
