package com.appbase


import android.os.Bundle
import androidx.activity.ComponentActivity
import android.util.Log
import android.widget.Button
import android.widget.Switch
import android.widget.Toast
import android.graphics.Color
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val switchControl: Switch = findViewById(R.id.switchControl)

        switchControl.isChecked = StateProvider.pin_drive_status
        if (switchControl.isChecked) switchControl.setTextColor(Color.parseColor("#4178CA"))
        else switchControl.setTextColor(Color.parseColor("#E90C42"))
        switchControl.setOnCheckedChangeListener { _, isChecked ->
            StateProvider.pin_drive_status = isChecked
            switchControl.text = if (isChecked) "pin drive detected" else "pin drive not detected"
            if (isChecked) switchControl.setTextColor(Color.parseColor("#4178CA"))
            else switchControl.setTextColor(Color.parseColor("#E90C42"))



        }

        findViewById<Button>(R.id.button).setOnClickListener {

            finish()
        }
    }
}

