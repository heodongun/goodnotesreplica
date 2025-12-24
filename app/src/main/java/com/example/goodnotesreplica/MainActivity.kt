package com.example.goodnotesreplica

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.goodnotesreplica.ui.theme.GoodnotesreplicaTheme
import com.example.goodnotesreplica.ui.NotesApp

class MainActivity : ComponentActivity() {
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
