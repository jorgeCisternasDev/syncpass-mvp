package cl.syncpass.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cl.syncpass.app.ui.SyncPassRoot
import cl.syncpass.app.ui.theme.SyncPassTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as SyncPassApp).container
        setContent {
            SyncPassTheme {
                SyncPassRoot(container = container)
            }
        }
    }
}
