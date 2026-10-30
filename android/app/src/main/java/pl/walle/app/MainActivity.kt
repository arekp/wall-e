package pl.walle.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import pl.walle.app.ui.WalleApp

class MainActivity : ComponentActivity() {
    private val vm: WalleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                WalleApp(vm)
            }
        }
    }

    override fun onStop() {
        // aplikacja w tle nie może zostawić robota w ruchu ani z włączonym mikrofonem
        vm.stopTalking()
        vm.stop()
        super.onStop()
    }
}
