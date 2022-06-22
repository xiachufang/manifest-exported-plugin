package com.xiachufang.manifest.exported.simple

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.petterp.androidexportedplugin.R

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}