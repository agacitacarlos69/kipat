package com.kipat.probe

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * Dev harness. Not part of the product.
 *
 * Two jobs:
 *  1. write a Kipat test tag (URL record + Android Application Record)
 *  2. show the verdicts collected by TapExperienceActivity
 */
class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var writeArmed = false
    private var status by mutableStateOf("Ready.")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Kipat backdrop probe", fontSize = 20.sp)
                        Text(
                            "${Build.MANUFACTURER} ${Build.MODEL} · API ${Build.VERSION.SDK_INT} · " +
                                "NFC ${if (nfcAdapter == null) "absent" else if (nfcAdapter!!.isEnabled) "on" else "off"}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )

                        Button(
                            onClick = { armWrite() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Arm tag write, then tap a blank NTAG") }

                        Button(
                            onClick = { launchProbeDirectly() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Launch probe without NFC (no prior app)") }

                        Text(status, fontFamily = FontFamily.Monospace, fontSize = 12.sp)

                        Text("Verdict log", fontSize = 16.sp)
                        Text(
                            readLog(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (writeArmed) enableForegroundDispatch()
    }

    override fun onPause() {
        super.onPause()
        // Foreground dispatch must always be torn down in onPause, or the
        // adapter keeps a stale PendingIntent pointing at a dead Activity.
        try {
            nfcAdapter?.disableForegroundDispatch(this)
        } catch (e: IllegalStateException) {
            // Thrown if the Activity is already out of the resumed state.
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!writeArmed) return

        val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }

        if (tag == null) {
            status = "Tap produced no tag handle."
            return
        }
        writeTag(tag)
    }

    private fun armWrite() {
        if (nfcAdapter?.isEnabled != true) {
            status = "NFC unavailable or disabled on this device."
            return
        }
        writeArmed = true
        enableForegroundDispatch()
        status = "Armed. Hold a blank NTAG 213/215/216 against the phone."
    }

    private fun enableForegroundDispatch() {
        val intent = Intent(this, javaClass)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val pending = PendingIntent.getActivity(this, 0, intent, flags)
        nfcAdapter?.enableForegroundDispatch(this, pending, null, null)
    }

    /**
     * Writes two records:
     *
     *  [0] the URL, which is what a phone without Kipat installed will follow
     *  [1] an Android Application Record naming this package
     *
     * The AAR is the piece that stops a disambiguation chooser appearing when a
     * browser also matches the https URL. Order matters: the AAR goes last, and
     * Android reads it as "if this package is installed, dispatch to it; if not,
     * fall back to the earlier records".
     */
    private fun writeTag(tag: Tag) {
        val message = NdefMessage(
            arrayOf(
                NdefRecord.createUri(TEST_URL),
                NdefRecord.createApplicationRecord(packageName)
            )
        )

        try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                if (!ndef.isWritable) {
                    status = "Tag is read-only."
                    ndef.close()
                    return
                }
                if (ndef.maxSize < message.toByteArray().size) {
                    status = "Tag too small: needs ${message.toByteArray().size} bytes, has ${ndef.maxSize}."
                    ndef.close()
                    return
                }
                ndef.writeNdefMessage(message)
                ndef.close()
            } else {
                val formatable = NdefFormatable.get(tag)
                    ?: run {
                        status = "Tag is neither NDEF nor NDEF-formatable."
                        return
                    }
                formatable.connect()
                formatable.format(message)
                formatable.close()
            }
            writeArmed = false
            status = "Written. Now open another app, then tap the tag."
            Toast.makeText(this, "Tag written", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            // Tag writes fail constantly in the real world: the tag moves, the
            // field drops, the chip is locked. Never let it crash the harness.
            status = "Write failed: ${e.javaClass.simpleName} ${e.message}"
        }
    }

    /**
     * Control condition. Launching from here means the probe's own task is what
     * sits behind it, which is exactly what you are NOT testing — but it is a
     * useful baseline for the first-frame number.
     */
    private fun launchProbeDirectly() {
        startActivity(
            Intent(this, TapExperienceActivity::class.java)
                .setData(android.net.Uri.parse("kipatprobe://tap/BASELINE"))
        )
    }

    private fun readLog(): String = try {
        val f = File(filesDir, "probe-log.txt")
        if (f.exists()) f.readText().ifBlank { "(empty)" } else "(no verdicts yet)"
    } catch (e: Exception) {
        "(log unreadable: ${e.message})"
    }

    companion object {
        const val TEST_URL = "https://kipat.app/c/TEST123"
    }
}
