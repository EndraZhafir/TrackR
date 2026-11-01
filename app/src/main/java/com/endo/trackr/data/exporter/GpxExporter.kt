package com.endo.trackr.data.exporter

import com.endo.trackr.core.util.DateTimeUtils
import com.endo.trackr.data.local.entity.LocationPointEntity
import com.endo.trackr.data.local.entity.StayPointEntity
import java.util.Locale

/**
 * Generator dokumen GPX 1.1 (GPS Exchange Format) standar OGC.
 * Format ini dapat diimpor langsung ke Garmin BaseCamp, Strava, OsmAnd, Google Earth, dan perangkat GPS fisik.
 */
object GpxExporter {

    fun export(
        sessionTitle: String,
        points: List<LocationPointEntity>,
        stayPoints: List<StayPointEntity> = emptyList(),
        includeStayPoints: Boolean = true,
        filterJitter: Boolean = true
    ): String {
        val exportTimeUtc = DateTimeUtils.formatIso8601Utc(System.currentTimeMillis())
        val validPoints = if (filterJitter) points.filter { !it.isJitter } else points

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine(
                """<gpx version="1.1" creator="TrackR - Lightweight GPS Logger" """ +
                    """xmlns="http://www.topografix.com/GPX/1/1" """ +
                    """xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" """ +
                    """xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">"""
            )

            // 1. Metadata
            appendLine("  <metadata>")
            appendLine("    <name>${escapeXml(sessionTitle)}</name>")
            appendLine("    <desc>Ekspor jejak lokasi dan riwayat perjalanan dari aplikasi TrackR.</desc>")
            appendLine("    <time>$exportTimeUtc</time>")
            appendLine("  </metadata>")

            // 2. Waypoints untuk Titik Singgah (Stay Points)
            if (includeStayPoints) {
                stayPoints.forEachIndexed { index, stay ->
                    val arriveIso = DateTimeUtils.formatIso8601Utc(stay.arrivalTime)
                    val stayName = stay.resolvedAddress ?: "Titik Singgah #${index + 1}"
                    val durationStr = DateTimeUtils.formatDuration(stay.durationSeconds)
                    val desc = "Singgah selama $durationStr (${stay.pointCount} titik). Tiba: ${DateTimeUtils.formatTimeOnly(stay.arrivalTime)}, Berangkat: ${DateTimeUtils.formatTimeOnly(stay.departureTime)}"

                    appendLine(
                        String.format(
                            Locale.US,
                            """  <wpt lat="%.6f" lon="%.6f">""",
                            stay.latitude,
                            stay.longitude
                        )
                    )
                    appendLine("    <name>${escapeXml(stayName)}</name>")
                    appendLine("    <desc>${escapeXml(desc)}</desc>")
                    appendLine("    <time>$arriveIso</time>")
                    appendLine("    <type>StayPoint</type>")
                    appendLine("  </wpt>")
                }
            }

            // 3. Track & Track Segments
            appendLine("  <trk>")
            appendLine("    <name>${escapeXml(sessionTitle)}</name>")
            appendLine("    <trkseg>")

            for (point in validPoints) {
                val pointTimeIso = DateTimeUtils.formatIso8601Utc(point.timestamp)
                appendLine(
                    String.format(
                        Locale.US,
                        """      <trkpt lat="%.6f" lon="%.6f">""",
                        point.latitude,
                        point.longitude
                    )
                )
                appendLine("        <time>$pointTimeIso</time>")
                if (point.speed > 0f) {
                    appendLine(String.format(Locale.US, "        <speed>%.2f</speed>", point.speed))
                }
                appendLine("      </trkpt>")
            }

            appendLine("    </trkseg>")
            appendLine("  </trk>")
            appendLine("</gpx>")
        }
    }

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
