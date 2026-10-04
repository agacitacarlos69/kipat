package com.kipat.probe

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The four realistic production options for what sits behind the reveal. */
enum class Backdrop { TRANSPARENT, SCRIM, BLUR, WALLPAPER }

/**
 * Diagnostic rig for section 13 of the spec.
 *
 * This is NOT the Reveal. It draws almost nothing on purpose: the point is to
 * leave the screen empty so you can see what the system actually composites
 * behind a translucent, NFC-dispatched Activity on this specific handset.
 *
 * Cycle the backdrop modes on each device in the matrix and record the verdict.
 */
class TapExperienceActivity : ComponentActivity() {

    private var createdAtNanos = 0L
    private var firstFrameMs = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        createdAtNanos = SystemClock.elapsedRealtimeNanos()
        super.onCreate(savedInstanceState)

        // Belt and braces: the theme already does this, but an OEM-patched
        // framework occasionally resets the window background on resume.
        window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // API 29+: tells the framework this window stays translucent even where
        // a transition would otherwise convert it to opaque. Cheap insurance
        // against a mid-animation blackout.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setTranslucent(true)
        }

        measureFirstFrame()

        val action = intent?.action ?: "(none)"
        val payload = intent?.data?.toString() ?: "(no data uri)"
        Log.i(TAG, "launched action=$action data=$payload")

        setContent {
            var backdrop by remember { mutableStateOf(Backdrop.TRANSPARENT) }
            var frameMs by remember { mutableStateOf(-1L) }

            // Read the measured value back once the frame has landed.
            LaunchedEffect(Unit) {
                delay(300)
                frameMs = firstFrameMs
            }

            LaunchedEffect(backdrop) { applyBackdrop(backdrop) }

            Box(Modifier.fillMaxSize()) {
                // Deliberately empty. Anything drawn here is something you
                // cannot see the backdrop through.
                ProbePanel(
                    backdrop = backdrop,
                    onBackdropChange = { backdrop = it },
                    payload = payload,
                    firstFrameMs = frameMs,
                    onVerdict = { verdict ->
                        recordVerdict(backdrop, verdict)
                        finish()
                    },
                    onDismiss = { finish() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .systemBarsPadding()
                )
            }
        }
    }

    /** singleTask: a second tap arrives here, not in a new instance. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.i(TAG, "re-tap data=${intent.data}")
    }

    /**
     * Time from onCreate entry to the first frame actually committed to the
     * compositor. The spec budgets 0-100 ms. On a cold start you will typically
     * see 120-400 ms, which is the real argument for keeping the first frame
     * trivial and streaming everything else in behind it.
     */
    private fun measureFirstFrame() {
        val decor = window.decorView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            decor.viewTreeObserver.registerFrameCommitCallback {
                if (firstFrameMs < 0) {
                    firstFrameMs = (SystemClock.elapsedRealtimeNanos() - createdAtNanos) / 1_000_000
                    Log.i(TAG, "first frame committed at ${firstFrameMs}ms")
                }
            }
        } else {
            decor.post {
                if (firstFrameMs < 0) {
                    firstFrameMs = (SystemClock.elapsedRealtimeNanos() - createdAtNanos) / 1_000_000
                    Log.i(TAG, "first frame (post) at ${firstFrameMs}ms")
                }
            }
        }
    }

    private fun applyBackdrop(backdrop: Backdrop) {
        val lp = window.attributes

        // Reset every flag first so modes cannot accumulate across toggles.
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        lp.dimAmount = 0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            lp.blurBehindRadius = 0
        }

        when (backdrop) {
            Backdrop.TRANSPARENT -> Unit

            Backdrop.SCRIM -> {
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                lp.dimAmount = 0.45f
            }

            Backdrop.BLUR -> {
                // Cross-window blur is API 31+, and even there the OEM or the
                // user can switch it off (battery saver, low-end device config).
                // isCrossWindowBlurEnabled is the only honest check.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    windowManager.isCrossWindowBlurEnabled
                ) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    lp.blurBehindRadius = 48
                } else {
                    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    lp.dimAmount = 0.45f
                    Log.w(TAG, "cross-window blur unavailable; fell back to dim")
                }
            }

            Backdrop.WALLPAPER -> {
                // Shows the wallpaper, not the prior app. Included so you can
                // see the difference yourself and stop wondering whether it
                // would have solved a black backdrop.
                window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            }
        }
        window.attributes = lp
    }

    private fun recordVerdict(backdrop: Backdrop, verdict: String) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val line = listOf(
            stamp,
            "${Build.MANUFACTURER} ${Build.MODEL}",
            "API ${Build.VERSION.SDK_INT}",
            backdrop.name,
            verdict,
            "firstFrame=${firstFrameMs}ms"
        ).joinToString(" | ")

        Log.i(TAG, "VERDICT $line")
        try {
            File(filesDir, "probe-log.txt").appendText(line + "\n")
        } catch (e: Exception) {
            // A failed log write must never take the reveal down with it.
            Log.w(TAG, "could not persist verdict", e)
        }
    }

    companion object {
        const val TAG = "KipatProbe"
    }
}

/** The only thing drawn over the backdrop: small, bottom-anchored, opaque. */
@Composable
private fun ProbePanel(
    backdrop: Backdrop,
    onBackdropChange: (Backdrop) -> Unit,
    payload: String,
    firstFrameMs: Long,
    onVerdict: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xE6101014))
            .padding(16.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "backdrop: ${backdrop.name}   first frame: ${if (firstFrameMs < 0) "…" else "$firstFrameMs ms"}",
            color = Color(0xFF8AE6C0),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
        Text(
            text = payload,
            color = Color(0xFF9AA0A6),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )

        Text("Switch backdrop", color = Color.White, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Backdrop.entries.forEach { mode ->
                OutlinedButton(
                    onClick = { onBackdropChange(mode) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)
                ) {
                    Text(mode.name.take(4), fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(2.dp))
        Text("What is behind this panel?", color = Color.White, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("LIVE", "FROZEN", "BLACK", "HOME").forEach { verdict ->
                Button(
                    onClick = { onVerdict(verdict) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2B33))
                ) {
                    Text(verdict, fontSize = 11.sp)
                }
            }
        }

        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Dismiss without recording", fontSize = 12.sp)
        }
    }
}
