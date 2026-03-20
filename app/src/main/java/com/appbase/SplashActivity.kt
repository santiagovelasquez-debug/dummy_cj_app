package com.appbase

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.activity.ComponentActivity

class SplashActivity : ComponentActivity() {

    private val SPLASH_TIME_OUT: Long = 2000 // 2 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val tvVersion: TextView = findViewById(R.id.tvVersion)
        
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val version = pInfo.versionName
            tvVersion.text = "Version $version"
        } catch (e: Exception) {
            e.printStackTrace()
            tvVersion.text = "Version 1.0"
        }

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, SelectionActivity::class.java))
            finish()
        }, SPLASH_TIME_OUT)
    }
}
