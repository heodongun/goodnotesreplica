package com.example.goodnotesreplica

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.goodnotesreplica.ui.theme.GoodnotesreplicaTheme
import com.example.goodnotesreplica.ui.NotesApp

/**
 * Compose UI와 앱 테마를 호스팅하는 진입점 액티비티입니다.
 */
class MainActivity : ComponentActivity() {
    /**
     * Edge-to-edge를 설정하고 루트 Compose 트리를 렌더링합니다.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GoodnotesreplicaTheme {
                NotesApp()
            }
        }
    }
}
