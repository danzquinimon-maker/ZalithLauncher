package com.movtery.zalithlauncher.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.movtery.zalithlauncher.InfoCenter
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.ActivityErrorBinding
import com.movtery.zalithlauncher.utils.ZHTools
import com.movtery.zalithlauncher.feature.log.CrashAnalyzer
import com.movtery.zalithlauncher.utils.path.PathManager
import java.io.File
import java.io.RandomAccessFile
import net.kdt.pojavlaunch.Tools

class ErrorActivity : BaseActivity() {
    private lateinit var binding: ActivityErrorBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityErrorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val extras = intent.extras
        extras ?: run {
            finish()
            return
        }

        binding.errorConfirm.setOnClickListener { finish() }
        binding.errorRestart.setOnClickListener {
            startActivity(Intent(this@ErrorActivity, SplashActivity::class.java))
        }
        binding.shareLog.setOnClickListener { ZHTools.shareLogs(this) }
        binding.analyzeCrash.setOnClickListener {
    analyzeCrash()
        }
        if (extras.getBoolean(BUNDLE_IS_LAUNCHER_CRASH, false)) {
            showLauncherCrash(extras)
            return
        }
        if (extras.getBoolean(BUNDLE_IS_GAME_CRASH, false)) {
            //如果不是应用崩溃，那么这个页面就不允许截图
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            showGameCrash(extras)
            return
        }
        if (extras.getBoolean(BUNDLE_EASTER_EGG, false)) {
            showEasterEgg()
            return
        }

        finish()
    }

    private fun showLauncherCrash(extras: Bundle) {
        val context = this

        val throwable = extras.getSerializable(BUNDLE_THROWABLE) as Throwable?
        val stackTrace = if (throwable != null) Tools.printToString(throwable) else "<null>"
        val strSavePath = extras.getString(BUNDLE_SAVE_PATH)
        val errorText = "$strSavePath :\r\n\r\n$stackTrace"
        launcherCrashLog = stackTrace
        binding.apply {
            this.errorTitle.text = InfoCenter.replaceName(context, R.string.error_fatal)
            this.errorText.text = errorText

            this.topView.setBackgroundColor(ContextCompat.getColor(context, R.color.background_menu_top_error))
            this.background.setBackgroundColor(ContextCompat.getColor(context, R.color.background_app_error))
        }
    }

    private fun showGameCrash(extras: Bundle) {
        val code = extras.getInt(BUNDLE_CODE, 0)
        if (code == 0) {
            finish()
            return
        }
        val errorText = if (extras.getBoolean(BUNDLE_IS_SIGNAL)) R.string.game_singnal_message else R.string.game_exit_message

        val context = this

        binding.apply {
            this.errorTitle.setText(R.string.generic_wrong_tip)
            this.errorText.apply {
                text = getString(errorText, code)
                textSize = 14f
            }
            this.errorTip.visibility = View.VISIBLE
            this.errorNoScreenshot.visibility = View.VISIBLE

            this.topView.setBackgroundColor(ContextCompat.getColor(context, R.color.background_menu_top))
            this.background.setBackgroundColor(ContextCompat.getColor(context, R.color.background_app))
        }
    }

    private fun showEasterEgg() {
        val context = this

        binding.apply {
            this.topView.visibility = View.GONE
            this.scrollView.visibility = View.GONE
            this.shareLog.visibility = View.GONE
            this.centerText.visibility = View.VISIBLE

            this.centerText.text = InfoCenter.replaceName(context, R.string.error_fatal)

            this.topView.setBackgroundColor(ContextCompat.getColor(context, R.color.background_menu_top_error))
            this.background.setBackgroundResource(R.drawable.image_xibao)
        }
    }
    private var launcherCrashLog: String? = null

    private fun analyzeCrash() {
        val logText = launcherCrashLog ?: run {
            val logFile = File(PathManager.DIR_GAME_HOME, "latestlog.txt")

            if (!logFile.exists()) {
                binding.errorText.text =
                    "No game log found. Try sharing your logs."
                return
            }

            try {
                RandomAccessFile(logFile, "r").use { file ->
                    val length = file.length()
                    val start = (length - 2L * 1024 * 1024).coerceAtLeast(0)
                    file.seek(start)

                    ByteArray((length - start).toInt()).also {
                        file.readFully(it)
                    }.toString(Charsets.UTF_8)
                }
            } catch (e: Exception) {
                binding.errorText.text =
                    "Could not read the crash log: ${e.message}"
                return
            }
        }

        val report = CrashAnalyzer.analyze(logText)

        binding.errorText.text = buildString {
            appendLine("CRASH ANALYSIS")
            appendLine()
            appendLine(report.summary)
            appendLine()

            if (report.hasFindings) {
                report.findings.forEachIndexed { index, finding ->
                    appendLine("${index + 1}. ${finding.title}")
                    appendLine(finding.explanation)
                    appendLine("Suggested fix: ${finding.suggestedFix}")
                    appendLine("Log evidence: ${finding.matchedText}")
                    appendLine()
                }
            } else {
                appendLine("No recognized error pattern was found.")
                appendLine("Share the logs for further investigation.")
            }
        }
    }
    companion object {
        private const val BUNDLE_IS_LAUNCHER_CRASH = "is_launcher_crash"
        private const val BUNDLE_IS_GAME_CRASH = "is_game_crash"
        private const val BUNDLE_IS_SIGNAL = "is_signal"
        private const val BUNDLE_CODE = "code"
        private const val BUNDLE_THROWABLE = "throwable"
        private const val BUNDLE_SAVE_PATH = "save_path"
        private const val BUNDLE_EASTER_EGG = "easter_egg"

        @JvmStatic
        fun showLauncherCrash(ctx: Context, savePath: String?, th: Throwable?) {
            val intent = Intent(ctx, ErrorActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(BUNDLE_THROWABLE, th)
            intent.putExtra(BUNDLE_SAVE_PATH, savePath)
            intent.putExtra(BUNDLE_IS_LAUNCHER_CRASH, true)
            ctx.startActivity(intent)
        }

        @JvmStatic
        fun showExitMessage(
            ctx: Context,
            code: Int,
            isSignal: Boolean
        ) {
            val intent = Intent(ctx, ErrorActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(BUNDLE_CODE, code)
            intent.putExtra(BUNDLE_IS_LAUNCHER_CRASH, false)
            intent.putExtra(BUNDLE_IS_SIGNAL, isSignal)
            intent.putExtra(BUNDLE_IS_GAME_CRASH, true)
            ctx.startActivity(intent)
        }

        @JvmStatic
        fun showEasterEgg(ctx: Context) {
            val intent = Intent(ctx, ErrorActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(BUNDLE_EASTER_EGG, true)
            ctx.startActivity(intent)
        }
    }
}
