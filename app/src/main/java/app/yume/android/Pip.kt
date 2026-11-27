package app.yume.android

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Картинка в картинке для плеера. */
object Pip {
    /** Открыт плеер: при сворачивании приложения видео уходит в окошко. */
    var enabled by mutableStateOf(false)
    var inPip by mutableStateOf(false)

    fun enter(activity: Activity?): Boolean {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            activity.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
            true
        } catch (e: Exception) {
            false
        }
    }
}
