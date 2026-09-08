package com.mobilelinux.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
    private lateinit var tvProgressPercent: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvError: TextView
    private lateinit var errorScroll: View
    private lateinit var btnStart: MaterialButton
    private lateinit var btnRetry: MaterialButton
    private lateinit var layoutInitial: View
    private lateinit var layoutSetup: View
    private lateinit var layoutComplete: View

    // 4-Phase Pipeline Stepper Views
    private lateinit var ivStep1: ImageView
    private lateinit var ivStep2: ImageView
    private lateinit var ivStep3: ImageView
    private lateinit var ivStep4: ImageView
    private lateinit var tvStep1Title: TextView
    private lateinit var tvStep2Title: TextView
    private lateinit var tvStep3Title: TextView
    private lateinit var tvStep4Title: TextView
    private lateinit var tvStep1Sub: TextView
    private lateinit var tvStep2Sub: TextView
    private lateinit var tvStep3Sub: TextView
    private lateinit var tvStep4Sub: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        val setupRoot = findViewById<View>(R.id.setup_root)
        val hPad = (20 * resources.displayMetrics.density).toInt()
        val vPad = (16 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(setupRoot) { v, insets ->
            val sysInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                top = sysInsets.top + vPad,
                bottom = sysInsets.bottom + vPad,
                left = sysInsets.left + hPad,
                right = sysInsets.right + hPad
            )
            insets
        }

        progressBar = findViewById(R.id.progress_bar)
        tvStatus = findViewById(R.id.tv_status)
        tvStep = findViewById(R.id.tv_step)
        tvProgressPercent = findViewById(R.id.tv_progress_percent)
        tvTitle = findViewById(R.id.tv_title)
        tvError = findViewById(R.id.tv_error)
        errorScroll = findViewById(R.id.error_scroll)
        btnStart = findViewById(R.id.btn_start_setup)
        btnRetry = findViewById(R.id.btn_retry)
        layoutInitial = findViewById(R.id.layout_initial)
        layoutSetup = findViewById(R.id.layout_setup_progress)
        layoutComplete = findViewById(R.id.layout_complete)

        // Pipeline stepper bindings
        ivStep1 = findViewById(R.id.iv_step1_status)
        ivStep2 = findViewById(R.id.iv_step2_status)
        ivStep3 = findViewById(R.id.iv_step3_status)
        ivStep4 = findViewById(R.id.iv_step4_status)
        tvStep1Title = findViewById(R.id.tv_step1_title)
        tvStep2Title = findViewById(R.id.tv_step2_title)
        tvStep3Title = findViewById(R.id.tv_step3_title)
        tvStep4Title = findViewById(R.id.tv_step4_title)
        tvStep1Sub = findViewById(R.id.tv_step1_sub)
        tvStep2Sub = findViewById(R.id.tv_step2_sub)
        tvStep3Sub = findViewById(R.id.tv_step3_sub)
        tvStep4Sub = findViewById(R.id.tv_step4_sub)

        layoutInitial.visibility = View.VISIBLE
        layoutSetup.visibility = View.GONE
        layoutComplete.visibility = View.GONE

        btnStart.setOnClickListener {
            layoutInitial.visibility = View.GONE
            layoutSetup.alpha = 0f
            layoutSetup.visibility = View.VISIBLE
            layoutSetup.animate().alpha(1f).setDuration(250).start()
            updatePipelineStepper(1)
            startSetup()
        }

        btnRetry.setOnClickListener {
            btnRetry.visibility = View.GONE
            errorScroll.visibility = View.GONE
            layoutInitial.visibility = View.GONE
            layoutSetup.alpha = 0f
            layoutSetup.visibility = View.VISIBLE
            layoutSetup.animate().alpha(1f).setDuration(250).start()
            updatePipelineStepper(1)
            // Clear cached setup data so extraction starts fresh
            clearSetupData()
            startSetup()
        }
    }

    private fun updatePipelineStepper(currentStep: Int) {
        val green = ContextCompat.getColor(this, R.color.accent_green)
        val blue = ContextCompat.getColor(this, R.color.accent_blue)
        val grey = ContextCompat.getColor(this, R.color.text_hint)
        val textPrimary = ContextCompat.getColor(this, R.color.text_primary)
        val textSecondary = ContextCompat.getColor(this, R.color.text_secondary)

        val icons = listOf(ivStep1, ivStep2, ivStep3, ivStep4)
        val titles = listOf(tvStep1Title, tvStep2Title, tvStep3Title, tvStep4Title)
        val subs = listOf(tvStep1Sub, tvStep2Sub, tvStep3Sub, tvStep4Sub)

        val defaultSubTexts = listOf(
            "Preparing runtime binaries & architecture hooks",
            "Extracting complete filesystem archive (~150MB)",
            "Configuring bash profile, DNS resolvers & users",
            "Linking /sdcard storage & validating package manager"
        )

        for (i in 0..3) {
            val stepNum = i + 1
            when {
                stepNum < currentStep -> {
                    icons[i].setImageResource(R.drawable.ic_check_circle)
                    icons[i].imageTintList = ColorStateList.valueOf(green)
                    titles[i].setTextColor(textPrimary)
                    subs[i].text = "Completed"
                    subs[i].setTextColor(green)
                }
                stepNum == currentStep -> {
                    icons[i].setImageResource(R.drawable.ic_active_dot)
                    icons[i].imageTintList = ColorStateList.valueOf(blue)
                    titles[i].setTextColor(blue)
                    subs[i].text = defaultSubTexts[i]
                    subs[i].setTextColor(textSecondary)
                }
                else -> {
                    icons[i].setImageResource(R.drawable.ic_pending_circle)
                    icons[i].imageTintList = ColorStateList.valueOf(grey)
                    titles[i].setTextColor(grey)
                    subs[i].text = defaultSubTexts[i]
                    subs[i].setTextColor(grey)
                }
            }
        }
    }

    private fun startSetup() {
        val runtime = UbuntuRuntime.getInstance(this)

        lifecycleScope.launch {
            val success = runtime.performSetup(
                onProgress = { progress, message ->
                    runOnUiThread {
                        val pct = (progress * 100).toInt().coerceIn(0, 100)
                        progressBar.progress = pct
                        tvProgressPercent.text = "$pct%"
                        tvStatus.text = message

                        when {
                            pct < 15 -> {
                                tvStep.text = "Initializing runtime environment..."
                                updatePipelineStepper(1)
                            }
                            pct < 85 -> {
                                tvStep.text = "Extracting Ubuntu 24.04 rootfs..."
                                updatePipelineStepper(2)
                            }
                            pct < 98 -> {
                                tvStep.text = "Configuring Linux packages..."
                                updatePipelineStepper(3)
                            }
                            else -> {
                                tvStep.text = "Finalizing setup..."
                                updatePipelineStepper(4)
                            }
                        }
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
        layoutInitial.visibility = View.GONE
        layoutSetup.visibility = View.GONE
        layoutComplete.alpha = 0f
        layoutComplete.visibility = View.VISIBLE
        layoutComplete.animate().alpha(1f).setDuration(300).start()

        val btnLaunch = findViewById<MaterialButton>(R.id.btn_launch)
        btnLaunch.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun showError(error: String) {
        layoutInitial.visibility = View.GONE
        layoutSetup.visibility = View.GONE
        tvError.text = "Setup Error:\n$error\n\nTap Retry to try again. If the problem persists, try uninstalling and reinstalling the app."
        errorScroll.visibility = View.VISIBLE
        btnRetry.visibility = View.VISIBLE
        btnRetry.text = "Retry Setup"
    }
}
