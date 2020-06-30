package com.github.basshelal.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.github.basshelal.R
import com.github.basshelal.example.fragments.PickerFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, PickerFragment(), "PickerFragment")
                .commit()

    }
}