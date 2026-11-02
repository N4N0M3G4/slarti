package com.georecon.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.georecon.app.capture.CaptureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection

object HubPrefs {
    private const val NAME = "hub"
    fun save(ctx: Context, p: HubPairing) =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("url", p.baseUrl).putString("token", p.token).apply()
    fun load(ctx: Context): HubPairing? {
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return HubPairing(sp.getString("url", null) ?: return null, sp.getString("token", null) ?: return null)
    }
}

/**
 * Uploads every capture that has no `<id>.uploaded` marker. PUT by capture id,
 * so a retry after a dropped connection overwrites instead of duplicating.
 */
class UploadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val hub = HubPrefs.load(applicationContext) ?: return@withContext Result.success()
        val dir = CaptureStore.dir(applicationContext)
        var failed = false
        for (meta in dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty()) {
            val id = meta.nameWithoutExtension
            val photo = File(dir, "$id.jpg")
            val marker = File(dir, "$id.uploaded")
            if (marker.exists() || !photo.exists()) continue
            val ok = put(hub, "$id/photo.jpg", photo, "image/jpeg") && put(hub, "$id/meta.json", meta, "application/json")
            if (ok) marker.writeText(System.currentTimeMillis().toString()) else failed = true
        }
        if (!uploadScans(hub)) failed = true
        if (failed) Result.retry() else Result.success()
    }

    /** Object scans: session.json, each frame (jpg+json, marker per frame), then POST complete. Resumable. */
    private fun uploadScans(hub: HubPairing): Boolean {
        var ok = true
        for (dir in com.georecon.app.scan.ScanStore.root(applicationContext).listFiles().orEmpty()) {
            if (!File(dir, "complete").exists() || File(dir, "uploaded").exists()) continue
            val id = dir.name
            val session = File(dir, "session.json")
            if (!session.exists() || !put(hub, "scans/$id/session.json", session, "application/json")) { ok = false; continue }
            var all = true
            for (jpg in File(dir, "frames").listFiles { f -> f.name.endsWith(".jpg") }.orEmpty().sortedBy { it.name }) {
                val n = jpg.nameWithoutExtension
                val marker = File(dir, "frames/$n.uploaded")
                if (marker.exists()) continue
                val json = File(dir, "frames/$n.json")
                if (put(hub, "scans/$id/frames/$n.jpg", jpg, "image/jpeg") && put(hub, "scans/$id/frames/$n.json", json, "application/json"))
                    marker.writeText("1") else { all = false; break }
            }
            if (all && post(hub, "scans/$id/complete")) File(dir, "uploaded").writeText(System.currentTimeMillis().toString()) else ok = false
        }
        return ok
    }

    private fun post(hub: HubPairing, path: String): Boolean = runCatching {
        val c = NetGuard.check("${hub.baseUrl}/api/$path", hub.baseUrl).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 8_000; c.readTimeout = 30_000
        c.setRequestProperty("Authorization", "Bearer ${hub.token}")
        c.doOutput = true; c.setFixedLengthStreamingMode(0); c.outputStream.close()
        (c.responseCode in 200..299).also { c.disconnect() }
    }.getOrDefault(false)

    private fun put(hub: HubPairing, path: String, file: File, type: String): Boolean = runCatching {
        val c = NetGuard.check("${hub.baseUrl}/api/${if (path.startsWith("scans/")) path else "captures/$path"}", hub.baseUrl).openConnection() as HttpURLConnection
        c.requestMethod = "PUT"; c.doOutput = true; c.connectTimeout = 8_000; c.readTimeout = 30_000
        c.setRequestProperty("Authorization", "Bearer ${hub.token}")
        c.setRequestProperty("Content-Type", type)
        c.setFixedLengthStreamingMode(file.length())
        c.outputStream.use { out -> file.inputStream().use { it.copyTo(out) } }
        (c.responseCode in 200..299).also { c.disconnect() }
    }.getOrDefault(false)

    companion object {
        /** Queue an upload pass; runs when the phone is on an unmetered network (home Wi-Fi). */
        fun enqueue(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("upload", ExistingWorkPolicy.REPLACE, req)
        }
    }
}
