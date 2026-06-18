package com.example.tv

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class TvSettingsActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var btnListenKey: Button
    private lateinit var tvLastKey: TextView
    private lateinit var spinnerActions: Spinner
    private lateinit var btnSaveMapping: Button
    private lateinit var btnClearMappings: Button
    private lateinit var rvMappings: RecyclerView

    private var currentListening = false
    private var lastScannedKeyCode = -1
    private var lastScannedKeyName = ""

    private val actionsList = listOf(
        "Play/Pause", 
        "Skip Reverse 5s", 
        "Skip Forward 15s", 
        "Next Video", 
        "Previous Video",
        "Speed Settings"
    )

    private val actionValues = listOf(
        "PLAY_PAUSE",
        "SKIP_REVERSE_5",
        "SKIP_FORWARD_15",
        "NEXT",
        "PREVIOUS",
        "SPEED_SETTINGS"
    )

    private var savedMappings = mutableListOf<Pair<Int, String>>()
    private lateinit var adapter: MappingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_settings)

        prefs = getSharedPreferences("ButtonMappings", Context.MODE_PRIVATE)

        btnListenKey = findViewById(R.id.btn_listen_key)
        tvLastKey = findViewById(R.id.tv_last_key)
        spinnerActions = findViewById(R.id.spinner_actions)
        btnSaveMapping = findViewById(R.id.btn_save_mapping)
        btnClearMappings = findViewById(R.id.btn_clear_mappings)
        rvMappings = findViewById(R.id.rv_mappings)

        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, actionsList)
        spinnerActions.adapter = spinnerAdapter

        btnListenKey.setOnClickListener {
            currentListening = true
            btnListenKey.text = "Listening... Press a button!"
            btnListenKey.requestFocus()
        }

        btnListenKey.setOnKeyListener { _, keyCode, event ->
            if (currentListening && event.action == KeyEvent.ACTION_DOWN) {
                // filter out normal navigation if possible, but actually we want to allow mapping anything
                if (keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN && keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT && keyCode != KeyEvent.KEYCODE_ENTER && keyCode != KeyEvent.KEYCODE_DPAD_CENTER) {
                    lastScannedKeyCode = keyCode
                    lastScannedKeyName = KeyEvent.keyCodeToString(keyCode) ?: "UNKNOWN ($keyCode)"
                    tvLastKey.text = "Key: $lastScannedKeyName"
                    currentListening = false
                    btnListenKey.text = "Click to Listen for Key"
                    return@setOnKeyListener true
                }
            }
            false
        }

        btnSaveMapping.setOnClickListener {
            if (lastScannedKeyCode != -1) {
                val selectedAction = actionValues[spinnerActions.selectedItemPosition]
                prefs.edit().putString(lastScannedKeyCode.toString(), selectedAction).apply()
                loadMappings()
                lastScannedKeyCode = -1
                tvLastKey.text = "Key: None"
            }
        }

        btnClearMappings.setOnClickListener {
            prefs.edit().clear().apply()
            loadMappings()
        }

        rvMappings.layoutManager = LinearLayoutManager(this)
        adapter = MappingAdapter()
        rvMappings.adapter = adapter

        loadMappings()
    }

    private fun loadMappings() {
        savedMappings.clear()
        val all = prefs.all
        for ((keyStr, value) in all) {
            val code = keyStr.toIntOrNull()
            if (code != null && value is String) {
                savedMappings.add(Pair(code, value))
            }
        }
        adapter.notifyDataSetChanged()
    }

    inner class MappingAdapter : RecyclerView.Adapter<MappingAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvKeyCode: TextView = view.findViewById(R.id.tv_key_code)
            val tvAction: TextView = view.findViewById(R.id.tv_action)
            val btnDelete: Button = view.findViewById(R.id.btn_delete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_mapping, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val mapping = savedMappings[position]
            holder.tvKeyCode.text = "Key: ${KeyEvent.keyCodeToString(mapping.first) ?: mapping.first.toString()}"
            val index = actionValues.indexOf(mapping.second)
            val actionName = if (index != -1) actionsList[index] else mapping.second
            holder.tvAction.text = "Action: $actionName"
            holder.btnDelete.setOnClickListener {
                prefs.edit().remove(mapping.first.toString()).apply()
                loadMappings()
            }
        }

        override fun getItemCount() = savedMappings.size
    }
}
