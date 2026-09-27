package com.levidor.kehribarvideo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.levidor.kehribarvideo.ui.FproAppScreen
import com.levidor.kehribarvideo.ui.theme.FproAiTheme
import com.levidor.kehribarvideo.viewmodel.StudioViewModel

class MainActivity : ComponentActivity() {

    private val studioViewModel: StudioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FproAiTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FproAppScreen(viewModel = studioViewModel)
                }
            }
        }
    }
}
