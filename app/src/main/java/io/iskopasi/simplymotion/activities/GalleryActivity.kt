package io.iskopasi.simplymotion.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import io.iskopasi.galleryview.GalleryModel
import io.iskopasi.galleryview.GalleryModelFactory
import io.iskopasi.galleryview.GridGalleryView
import io.iskopasi.galleryview.ui.theme.SimplyMotionTheme
import io.iskopasi.simplymotion.models.GeneralRepo
import javax.inject.Inject

@AndroidEntryPoint
class GalleryActivity : ComponentActivity() {
    @Inject
    lateinit var repo: GeneralRepo
    private val galleryModel: GalleryModel by viewModels {
        GalleryModelFactory(
            this.application,
            filesDir
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        galleryModel.onDelete {
            repo.logDelete()
        }
        galleryModel.onClick { file ->
            repo.requestVideoPlay(file, this)
        }
        galleryModel.start()


        enableEdgeToEdge()
        setContent {
            SimplyMotionTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 1.0f),
                                        Color.White.copy(alpha = 0.2f),
                                        Color.White.copy(alpha = 0.2f),
                                    )
                                )
                            )
                            .padding(innerPadding)
                            .padding(vertical = 16.dp, horizontal = 16.dp)
                    ) {
                        GridGalleryView(galleryModel)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        galleryModel.onDestroy()
    }
}