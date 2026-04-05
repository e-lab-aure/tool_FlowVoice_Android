package com.flowvoice.android.ui

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.flowvoice.android.BuildConfig
import com.flowvoice.android.R
import com.flowvoice.android.api.ApiClient
import com.flowvoice.android.databinding.ActivityMainBinding
import com.flowvoice.android.overlay.OverlayService
import com.flowvoice.android.settings.AppSettings
import com.flowvoice.android.settings.SettingsRepository
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsRepo: SettingsRepository

    // Language data backing the spinner: list of (code, displayName)
    private var languageList: List<Pair<String, String>> = SettingsRepository.FALLBACK_LANGUAGES

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filter { !it.value }.keys
        if (denied.isNotEmpty()) {
            Toast.makeText(this, "Microphone permission required for voice input", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsRepo = SettingsRepository(this)

        binding.tvVersion.text = "v${BuildConfig.VERSION_NAME}"

        loadSettings()
        populateLanguageSpinner(languageList)
        setupListeners()
        requestRuntimePermissions()
    }

    override fun onResume() {
        super.onResume()
        updateStatusCards()
    }

    override fun onPause() {
        super.onPause()
        saveSettings()
    }

    // --- Settings persistence ---

    private fun loadSettings() {
        val s = settingsRepo.load()
        binding.etHost.setText(s.host)
        binding.etPort.setText(s.port.toString())
        binding.switchPreprocess.isChecked = s.preprocess

        // Select the saved language in the spinner (deferred until spinner is populated)
        val index = languageList.indexOfFirst { it.first == s.language }
        if (index >= 0) binding.spinnerLanguage.setSelection(index)
    }

    private fun saveSettings() {
        val host = binding.etHost.text?.toString()?.trim() ?: SettingsRepository.DEFAULT_HOST
        val port = binding.etPort.text?.toString()?.toIntOrNull() ?: SettingsRepository.DEFAULT_PORT
        val language = languageList.getOrNull(binding.spinnerLanguage.selectedItemPosition)?.first
            ?: SettingsRepository.DEFAULT_LANGUAGE
        val preprocess = binding.switchPreprocess.isChecked

        settingsRepo.save(AppSettings(host, port, language, preprocess))
    }

    // --- Language spinner ---

    private fun populateLanguageSpinner(languages: List<Pair<String, String>>) {
        languageList = languages
        val displayNames = languages.map { it.second }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, displayNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLanguage.adapter = adapter

        // Restore saved selection
        val savedCode = settingsRepo.load().language
        val index = languages.indexOfFirst { it.first == savedCode }
        if (index >= 0) binding.spinnerLanguage.setSelection(index)
    }

    // --- Button listeners ---

    private fun setupListeners() {
        binding.btnTestConnection.setOnClickListener { testConnection() }
        binding.btnGrantOverlay.setOnClickListener { openOverlayPermissionSettings() }
        binding.btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun testConnection() {
        val host = binding.etHost.text?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() } ?: run {
            showSnackbar("Enter a server host first")
            return
        }
        val port = binding.etPort.text?.toString()?.toIntOrNull() ?: SettingsRepository.DEFAULT_PORT

        binding.btnTestConnection.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val langs = ApiClient.getLanguages(host, port)
                val asPairs = langs.entries.map { it.key to it.value }

                withContext(Dispatchers.Main) {
                    populateLanguageSpinner(asPairs)
                    showSnackbar(getString(R.string.msg_connection_success, langs.size))
                    binding.btnTestConnection.isEnabled = true

                    // Connection confirmed - start the overlay service if permissions are in place
                    if (Settings.canDrawOverlays(this@MainActivity)) {
                        OverlayService.start(this@MainActivity)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showSnackbar(getString(R.string.msg_connection_failed, e.message ?: "Unknown error"))
                    binding.btnTestConnection.isEnabled = true
                }
            }
        }
    }

    private fun openOverlayPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    // --- Status card ---

    private fun updateStatusCards() {
        val overlayGranted = Settings.canDrawOverlays(this)
        val accessibilityEnabled = isAccessibilityServiceEnabled()

        binding.tvStatusOverlay.text = if (overlayGranted)
            getString(R.string.status_overlay_granted)
        else
            getString(R.string.status_overlay_missing)

        binding.tvStatusOverlay.setTextColor(
            ContextCompat.getColor(this, if (overlayGranted) R.color.status_ok else R.color.status_error)
        )

        binding.tvStatusAccessibility.text = if (accessibilityEnabled)
            getString(R.string.status_accessibility_enabled)
        else
            getString(R.string.status_accessibility_disabled)

        binding.tvStatusAccessibility.setTextColor(
            ContextCompat.getColor(this, if (accessibilityEnabled) R.color.status_ok else R.color.status_error)
        )

        // Hide permission buttons when permissions are already granted
        binding.btnGrantOverlay.visibility = if (overlayGranted)
            android.view.View.GONE else android.view.View.VISIBLE

        binding.btnEnableAccessibility.visibility = if (accessibilityEnabled)
            android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    // --- Permissions ---

    private fun requestRuntimePermissions() {
        val toRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            toRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                toRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (toRequest.isNotEmpty()) {
            permissionLauncher.launch(toRequest.toTypedArray())
        }
    }

    // --- Helpers ---

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
}
