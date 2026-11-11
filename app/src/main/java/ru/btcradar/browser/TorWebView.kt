package ru.btcradar.browser

import android.content.Context
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.webkit.WebView

/**
 * WebView с «анонимной клавиатурой».
 *
 * IME_FLAG_NO_PERSONALIZED_LEARNING говорит экранной клавиатуре не запоминать
 * то, что печатает пользователь (не подмешивать в словарь, не слать в облачную
 * персонализацию). Это и есть «инкогнито-клавиатура». Плюс отключаем автоподсказки.
 *
 * Поля ввода внутри веб-страницы рисует сам WebView, поэтому флаг надо
 * навесить здесь, в onCreateInputConnection — иначе он до веб-инпутов не дойдёт.
 */
class TorWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {

    var incognitoKeyboard: Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val ic = super.onCreateInputConnection(outAttrs)
        if (incognitoKeyboard) {
            outAttrs.imeOptions = outAttrs.imeOptions or
                    EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            outAttrs.inputType = outAttrs.inputType or
                    EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        return ic
    }
}
