package com.mobilelinux.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.mobilelinux.R
import com.mobilelinux.runtime.UbuntuRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * First-run setup wizard.
 * Shown when the Ubuntu environment is not yet configured.
 *
 * Steps:
 * 1. Extract proot binary
 * 2. Extract scripts
 * 3. Extract Ubuntu rootfs (~150MB)
 * 4. Run setup.sh configuration
 * 5. Install developer packages
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var tvStep: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvError: TextView
    private lateinit var errorScroll: View
    private lateinit var btnStart: MaterialButton
    private lateinit var btnRetry: MaterialButton
    private lateinit var layoutSetup: View
    private lateinit var layoutComplete: View

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        val setupRoot = findViewById<View>(R.id.setup_root)
        val basePad = (24 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(setupRoot) { v, insets ->
            val sysInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                top = sysInsets.top + basePad,
                bottom = sysInsets.bottom + basePad,
                left = sysInsets.left + basePad,
                right = sysInsets.right + basePad
            )
            insets
        }

        progressBar = findViewById(R.id.progress_bar)
        tvStatus = findViewById(R.id.tv_status)
        tvStep = findViewById(R.id.tv_step)
        tvTitle = findViewById(R.id.tv_title)
        tvError = findViewById(R.id.tv_error)
        errorScroll = findViewById(R.id.error_scroll)
        btnStart = findViewById(R.id.btn_start_setup)
        btnRetry = findViewById(R.id.btn_retry)
        layoutSetup = findViewById(R.id.layout_setup_progress)
        layoutComplete = findViewById(R.id.layout_complete)

        layoutSetup.visibility = View.GONE
        layoutComplete.visibility = View.GONE

        btnStart.setOnClickListener {
            btnStart.visibility = View.GONE
            layoutSetup.visibility = View.VISIBLE
            startSetup()
        }

        btnRetry.setOnClickListener {
            btnRetry.visibility = View.GONE
            errorScroll.visibility = View.GONE
            layoutSetup.visibility = View.VISIBLE
            // Clear cached setup data so extraction starts fresh
            clearSetupData()
            startSetup()
        }
    }

    private fun startSetup() {
        val runtime = UbuntuRuntime.getInstance(this)

        lifecycleScope.launch {
            val success = runtime.performSetup(
                onProgress = { progress, message ->
                    runOnUiThread {
                        progressBar.progress = (progress * 100).toInt()
                        tvStatus.text = message
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        showError(error)
                    }
                }
            )

            withContext(Dispatchers.Main) {
                if (success) {
                    markSetupComplete()
                    showComplete()
                }
            }
        }
    }

    /**
     * Clears setup markers and rootfs so the next attempt starts fresh.
     * This is called when the user taps "Retry" after a failure.
     */
    private fun clearSetupData() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Remove setup complete markers
                File(filesDir, ".setup_complete").delete()
                // Remove rootfs extraction marker so it re-extracts
                val rootfsDir = File(filesDir, "ubuntu-rootfs")
                File(rootfsDir, ".rootfs_extracted").delete()
                // Don't delete the full rootfs — it's huge and was likely extracted fine.
                // The retry will re-run the configuration step on the existing rootfs.
            } catch (e: Exception) {
                // Ignore cleanup failures
            }
        }
    }

    private fun markSetupComplete() {
        getSharedPreferences("mobilelinux_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("setup_complete", true)
            .apply()
    }

    private fun showComplete() {
        layoutSetup.visibility = View.GONE
        layoutComplete.visibility = View.VISIBLE

        val btnLaunch = findViewById<MaterialButton>(R.id.btn_launch)
        btnLaunch.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun showError(error: String) {
        layoutSetup.visibility = View.GONE
        tvError.text = "Setup Error:\n$error\n\nTap Retry to try again. If the problem persists, try uninstalling and reinstalling the app."
        errorScroll.visibility = View.VISIBLE
        btnRetry.visibility = View.VISIBLE
        btnRetry.text = "Retry Setup"
    }
}

