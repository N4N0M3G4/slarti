package com.georecon.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.georecon.app.sync.HubPairing
import com.georecon.app.sync.HubPrefs
import com.georecon.app.sync.UploadWorker
import com.georecon.app.ui.GeoReconApp
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        com.georecon.app.data.FossilDetails.smithsonianKey = BuildConfig.SI_API_KEY
        handlePairing(intent)
        UploadWorker.enqueue(this)
        setContent { GeoReconApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePairing(intent)
    }

    /** georecon://pair?url=...&token=... from the hub's pairing QR. */
    private fun handlePairing(intent: Intent?) {
        val data = intent?.data?.toString() ?: return
        val pairing = HubPairing.parse(data)
        if (pairing == null) {
            Toast.makeText(this, "That pairing link won't do: the hub must be on your local network", Toast.LENGTH_LONG).show()
            return
        }
        HubPrefs.save(this, pairing)
        Toast.makeText(this, "Paired with hub ${pairing.baseUrl}", Toast.LENGTH_SHORT).show()
        UploadWorker.enqueue(this)
    }
}
