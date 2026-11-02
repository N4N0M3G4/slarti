package com.georecon.app.offline

import android.content.Context
import com.georecon.app.pin.Pin
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

/** Bounded caching: cap the ambient cache, keep at most one saved area (around the pin). */
object AreaCache {
    fun applyAmbientCap(ctx: Context) =
        OfflineManager.getInstance(ctx).setMaximumAmbientCacheSize(TileBudget.AMBIENT_CACHE_BYTES, null)

    /** Delete every saved area, then download the one around `pin`. Reports progress text. */
    fun saveAreaAround(ctx: Context, pin: Pin, styleUrl: String, progress: (String) -> Unit) {
        val mgr = OfflineManager.getInstance(ctx)
        mgr.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val old = offlineRegions.orEmpty().toList()
                deleteAll(old) { create(ctx, mgr, pin, styleUrl, progress) }
            }
            override fun onError(error: String) = progress("CACHE ERROR: $error")
        })
    }

    private fun deleteAll(regions: List<OfflineRegion>, done: () -> Unit) {
        if (regions.isEmpty()) return done()
        regions.first().delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() = deleteAll(regions.drop(1), done)
            override fun onError(error: String) = deleteAll(regions.drop(1), done)
        })
    }

    private fun create(ctx: Context, mgr: OfflineManager, pin: Pin, styleUrl: String, progress: (String) -> Unit) {
        val b = TileBudget.bounds(pin.lat, pin.lon)
        val def = OfflineTilePyramidRegionDefinition(
            styleUrl, LatLngBounds.from(b[2], b[3], b[0], b[1]),
            TileBudget.AREA_MIN_ZOOM, TileBudget.AREA_MAX_ZOOM, ctx.resources.displayMetrics.density,
        )
        mgr.createOfflineRegion(def, pin.label.toByteArray(), object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                    override fun onStatusChanged(status: OfflineRegionStatus) {
                        val pct = if (status.requiredResourceCount > 0) 100 * status.completedResourceCount / status.requiredResourceCount else 0
                        progress(if (status.isComplete) "AREA SAVED (${status.completedResourceSize / 1_048_576} MB)" else "SAVING AREA $pct%")
                        if (status.isComplete) offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    }
                    override fun onError(error: OfflineRegionError) = progress("SAVE ERROR: ${error.reason}")
                    override fun mapboxTileCountLimitExceeded(limit: Long) = progress("TILE LIMIT $limit HIT")
                })
                offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
            }
            override fun onError(error: String) = progress("SAVE ERROR: $error")
        })
    }
}
