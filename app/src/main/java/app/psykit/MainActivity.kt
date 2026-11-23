package app.psykit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.psykit.ui.PetScreen
import app.psykit.ui.PetViewModel
import app.psykit.ui.PsykitTheme

class MainActivity : ComponentActivity() {
    private val vm: PetViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PsykitTheme { PetScreen(vm) } }
    }

    override fun onResume() {
        super.onResume()
        vm.repo.refresh()
        vm.startSteps()
    }

    override fun onPause() {
        super.onPause()
        vm.stopSteps()
    }
}
