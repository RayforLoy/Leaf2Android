package com.leaf2android.localization

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.leaf2android.protocol.ProtocolException
import com.leaf2android.protocol.WireEncoding
import com.leaf2android.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

/** Resource-backed messages remain localizable while the application-scoped session survives recreation. */
data class UiText(
    @param:StringRes val resourceId: Int = 0,
    val arguments: List<Any?> = emptyList(),
    private val raw: String = "",
    private val protocol: ProtocolException? = null,
) : CharSequence {
    fun resolve(): String = protocol?.messageForLanguage(L10n.isChinese)
        ?: if (resourceId == 0) raw else tr(resourceId, *arguments.toTypedArray())
    override val length: Int get() = resolve().length
    override fun get(index: Int): Char = resolve()[index]
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = resolve().subSequence(startIndex, endIndex)
    override fun toString(): String = resolve()
    companion object {
        val Empty = UiText()
        fun raw(value: String) = UiText(raw = value)
        fun protocol(value: ProtocolException) = UiText(protocol = value)
    }
}

fun msg(@StringRes id: Int, vararg arguments: Any?): UiText = UiText(id, arguments.toList())
fun tr(@StringRes id: Int, vararg arguments: Any?): String = L10n.text(id, *arguments)

class LocalizedIOException(val uiText: UiText) : IOException() {
    override val message: String get() = uiText.resolve()
}

fun errorText(error: Throwable, fallback: UiText): UiText = when (error) {
    is LocalizedIOException -> error.uiText
    is ProtocolException -> UiText.protocol(error)
    else -> error.message?.takeIf { it.isNotBlank() }?.let(UiText::raw) ?: fallback
}

object L10n {
    private lateinit var application: Context
    fun initialize(context: Context) { application = context.applicationContext }
    private val mutableRevision = MutableStateFlow(0L)
    val revisions = mutableRevision.asStateFlow()
    fun refresh() { mutableRevision.value += 1 }
    private fun currentContext(): Context {
        val selected = AppCompatDelegate.getApplicationLocales()
        val locales = if (selected.isEmpty) Resources.getSystem().configuration.locales else selected.unwrap() as android.os.LocaleList
        val configuration = Configuration(application.resources.configuration).apply { setLocales(locales) }
        return application.createConfigurationContext(configuration)
    }
    val isChinese: Boolean get() = currentContext().resources.configuration.locales[0]?.language == "zh"
    fun text(@StringRes id: Int, vararg arguments: Any?): String {
        val values = arguments.map { if (it is UiText) it.resolve() else it }.toTypedArray()
        return if (values.isEmpty()) currentContext().getString(id) else currentContext().getString(id, *values)
    }
    fun languageTag(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    fun setLanguage(tag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        refresh()
    }
    fun encodingTitle(encoding: WireEncoding): String = tr(when (encoding) {
        WireEncoding.WESTERN -> R.string.encoding_western
        WireEncoding.GBK -> R.string.encoding_gbk
        WireEncoding.UTF8 -> R.string.encoding_utf8
    })
}
