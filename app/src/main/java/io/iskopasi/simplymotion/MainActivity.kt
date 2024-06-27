package io.iskopasi.simplymotion

import android.Manifest
import android.R
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import io.iskopasi.simplymotion.screens.LogScreen
import io.iskopasi.simplymotion.screens.MainScreen
import io.iskopasi.simplymotion.utils.e
import kotlinx.coroutines.launch


object MainRoute
object LogRoute
class MainActivity : ComponentActivity() {
    private val uiModel: UIModel by viewModels()
    private val cameraPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultMap ->
            if (resultMap.values.all { it }) {

            } else {
                Toast.makeText(this, "We need your permission", Toast.LENGTH_LONG)
            }
        }

    private fun checkPermissions(context: Context) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!checkPermissions(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                cameraPermissionRequest.launch(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.POST_NOTIFICATIONS,
                    )
                )
            } else {
                cameraPermissionRequest.launch(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        }

//        enableEdgeToEdge()

        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        // Lock screen brightness
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

//        lifecycle.removeObserver(uiModel)
//        lifecycle.addObserver(uiModel)
//        uiModel.setupCamera(this, viewfinder.surfaceProvider)

        uiModel.startService(this)

        lifecycleScope.launch {
            uiModel.isBrightnessUp.collect { isBrightnessUp ->
                if (isBrightnessUp) {
                    brightnessUp()
                } else {
                    brightnessDown()
                }
            }
        }

        setContent {
            // Looks like Compose Navigation does not support previous screen retaining
            // which results in rebuilding it everytime on back button press.
            val navController = rememberNavController()

            NavHost(navController = navController, startDestination = MainRoute.toString()) {
                composable(MainRoute.toString()) {
                    MainScreen(
                        uiModel,
                        toLogs = {
                            navController.navigate(
                                route = LogRoute.toString(),
                                navOptions = navOptions {
                                    anim {
                                        enter = R.animator.fade_in
                                        exit = R.animator.fade_out
                                    }
                                }

                            )
                        })
                }
                composable(LogRoute.toString()) { LogScreen(uiModel) }
            }
        }
    }

    private fun brightnessDown() = lifecycleScope.launch {
        "brightnessDown".e
//        context.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
//        context.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        window.attributes = window.attributes.apply {
            dimAmount = 1f
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    private fun brightnessUp() = lifecycleScope.launch {
        "brightnessUp".e
        window.attributes = window.attributes.apply {
            dimAmount = 0.5f
            screenBrightness = 0.5f
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        uiModel.unbindService(this)
    }

    override fun onStop() {
        super.onStop()

        if (!uiModel.isArmed) {
            uiModel.stopService(this)
        }
    }
}
