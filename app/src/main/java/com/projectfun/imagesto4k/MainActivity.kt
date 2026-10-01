package com.projectfun.imagesto4k

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.projectfun.imagesto4k.ui.HomeScreen
import com.projectfun.imagesto4k.ui.theme.ImagesTo4KTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ImagesTo4KTheme {
                HomeScreen()
            }
        }
    }
}
