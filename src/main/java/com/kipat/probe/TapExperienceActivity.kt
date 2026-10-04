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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.kipat.probe.domain.model.ExperienceManifest
import com.kipat.probe.domain.model.ProfileData
import com.kipat.probe.domain.model.VerificationInfo
import com.kipat.probe.domain.model.VerificationLevel
import com.kipat.probe.ui.animation.DripReveal
import kotlinx.coroutines.delay
import com.kipat.probe.ui.animation.DripReveal
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

        // API 30+: tells the framework this window stays translucent even where
        // a transition would otherwise convert it to opaque. Cheap insurance
        // against a mid-animation blackout.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setTranslucent(true)
        }

        measureFirstFrame()

        val payload = intent?.data?.toString() ?: "kipatprobe://tap/MOCK_USER"
        
        // Mock manifest for Phase 1 prototype
        val mockManifest = ExperienceManifest(
            experienceId = "exp_123",
            subjectUserId = "user_456",
            verification = VerificationInfo(
                level = VerificationLevel.SECURE_CARD_LIVE,
                label = "Kipat Card Verified"
            ),
            profile = ProfileData(
                displayName = "Vash Puno",
                subtitle = "BS Computer Science",
                organization = "Polytechnic University of the Philippines"
            ),
            profileTemplateSlug = "student_minimal_v1",
            animationTemplateSlug = "drip_reveal_v1",
            themeSlug = "pup_maroon_v1"
        )

        setContent {
            var selectedIdentity by remember { mutableStateOf<String?>(null) }
            
            Box(Modifier.fillMaxSize()) {
                if (selectedIdentity == null) {
                    IdentitySelector(
                        onIdentitySelected = { selectedIdentity = it },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .systemBarsPadding()
                            .padding(bottom = 24.dp)
                    )
                } else {
                    DripReveal {
                        Box(Modifier.fillMaxSize()) {
                            IdentityRevealContent(
                                manifest = mockManifest,
                                onDismiss = { finish() },
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .systemBarsPadding()
                            )
                        }
                    }
                }
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

/** Identity selector buttons at the bottom of the screen. */
@Composable
private fun IdentitySelector(
    onIdentitySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IdentityPill(
            text = "Student NFC",
            onClick = { onIdentitySelected("student") },
            modifier = Modifier.weight(1f)
        )
        IdentityPill(
            text = "Company NFC",
            onClick = { onIdentitySelected("company") },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun IdentityPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color.Black
        ),
        shape = RoundedCornerShape(28.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
@Composable
private fun IdentityRevealContent(
    manifest: ExperienceManifest,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        // Spec timing sequence
        delay(600)
        step = 1 // Logo/Verification
        delay(300)
        step = 2 // Name
        delay(300)
        step = 3 // Subtitle/Org
        delay(600)
        step = 4 // Actions
    }

    Column(
        modifier = modifier
            .padding(24.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Verification Badge
        if (step >= 1) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF8AE6C0).copy(alpha = 0.2f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = manifest.verification.label,
                    color = Color(0xFF8AE6C0),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Profile Logo / Avatar Placeholder
        if (step >= 1) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text("K", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(24.dp))

        // Name
        if (step >= 2) {
            Text(
                text = manifest.profile.displayName,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        // Role / Subtitle
        if (step >= 3) {
            Text(
                text = manifest.profile.subtitle ?: "",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = manifest.profile.organization ?: "",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(48.dp))

        // Actions
        if (step >= 4) {
            Button(
                onClick = { /* Save Connection */ },
                modifier = Modifier.fillMaxWidth(0.8f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Connect", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            
            Spacer(Modifier.height(12.dp))
            
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(0.8f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("Dismiss")
            }
        }
    }
}
