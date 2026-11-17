package app.yume.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.yume.android.data.Backup

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { YumeApp() }
    }

    override fun onStop() {
        super.onStop()
        // как в iOS: копия обновляется при сворачивании приложения
        try {
            Backup.writeAuto(applicationContext)
        } catch (e: Exception) {
            // копия не должна ронять приложение
        }
    }
}
