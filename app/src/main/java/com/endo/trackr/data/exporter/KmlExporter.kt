package com.endo.trackr.data.exporter

import com.endo.trackr.core.util.DateTimeUtils
import com.endo.trackr.data.local.entity.LocationPointEntity
import com.endo.trackr.data.local.entity.StayPointEntity
import java.util.Locale

/**
 * Generator dokumen KML 2.2 (Keyhole Markup Language) standar OGC.
 * Format ini kompatibel dengan Google Earth (Desktop & Web), Google My Maps, dan ArcGIS.
 */
object KmlExporter {

    fun export(
        sessionTitle: String,
        points: List<LocationPointEntity>,
        stayPoints: List<StayPointEntity> = emptyList(),
        includeStayPoints: Boolean = true,
        filterJitter: Boolean = true
    ): String {
        val validPoints = if (filterJitter) points.filter { !it.isJitter } else points

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2">""")
            appendLine("  <Document>")
            appendLine("    <name>${escapeXml(sessionTitle)}</name>")
            appendLine("    <description>Riwayat rute perjalanan TrackR diekspor pada ${DateTimeUtils.formatIso8601Utc(System.currentTimeMillis())}</description>")

            // Gaya Garis Rute (Biru Khas TrackR: aabbggrr -> ffe65100 atau ffff8800)
            appendLine("    <Style id=\"trackrLineStyle\">")
            appendLine("      <LineStyle>")
            appendLine("        <color>ffdb4437</color>") // Hex format KML: AABBGGRR
            appendLine("        <width>5</width>")
            appendLine("      </LineStyle>")
            appendLine("    </Style>")

            // Gaya Titik Singgah (Oranye)
            appendLine("    <Style id=\"stayPointStyle\">")
            appendLine("      <IconStyle>")
            appendLine("        <color>ff0055e6</color>")
            appendLine("        <scale>1.2</scale>")
            appendLine("      </IconStyle>")
            appendLine("    </Style>")

            // 1. Placemark Garis Rute (LineString)
            if (validPoints.isNotEmpty()) {
                appendLine("    <Placemark>")
                appendLine("      <name>Rute Perjalanan - ${escapeXml(sessionTitle)}</name>")
                appendLine("      <styleUrl>#trackrLineStyle</styleUrl>")
                appendLine("      <LineString>")
                appendLine("        <extrude>1</extrude>")
                appendLine("        <tessellate>1</tessellate>")
                appendLine("        <altitudeMode>clampToGround</altitudeMode>")
                appendLine("        <coordinates>")

                // Format KML: longitude,latitude,altitude (dipisahkan spasi)
                val coordString = validPoints.joinToString(" ") { p ->
                    String.format(Locale.US, "%.6f,%.6f,0", p.longitude, p.latitude)
                }
                appendLine("          $coordString")

                appendLine("        </coordinates>")
                appendLine("      </LineString>")
                appendLine("    </Placemark>")
            }

            // 2. Placemark untuk Setiap Titik Singgah (Point)
            if (includeStayPoints) {
                stayPoints.forEachIndexed { index, stay ->
                    val stayName = stay.resolvedAddress ?: "Titik Singgah #${index + 1}"
                    val durationStr = DateTimeUtils.formatDuration(stay.durationSeconds)
                    val desc = "Durasi Singgah: $durationStr (${stay.pointCount} titik)<br/>" +
                        "Waktu Tiba: ${DateTimeUtils.formatTimeOnly(stay.arrivalTime)}<br/>" +
                        "Waktu Berangkat: ${DateTimeUtils.formatTimeOnly(stay.departureTime)}"

                    appendLine("    <Placemark>")
                    appendLine("      <name>${escapeXml(stayName)}</name>")
                    appendLine("      <description><![CDATA[$desc]]></description>")
                    appendLine("      <styleUrl>#stayPointStyle</styleUrl>")
                    appendLine("      <Point>")
                    appendLine(
                        String.format(
                            Locale.US,
                            "        <coordinates>%.6f,%.6f,0</coordinates>",
                            stay.longitude,
                            stay.latitude
                        )
                    )
                    appendLine("      </Point>")
                    appendLine("    </Placemark>")
                }
            }

            appendLine("  </Document>")
            appendLine("</kml>")
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
