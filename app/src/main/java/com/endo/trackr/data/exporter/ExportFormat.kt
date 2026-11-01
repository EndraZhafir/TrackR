package com.endo.trackr.data.exporter

/**
 * Format file GIS yang didukung untuk ekspor riwayat perjalanan TrackR.
 */
enum class ExportFormat(
    val extension: String,
    val mimeType: String,
    val title: String,
    val description: String
) {
    GPX(
        extension = "gpx",
        mimeType = "application/gpx+xml",
        title = "GPX (GPS Exchange Format)",
        description = "Standar global untuk perangkat GPS, smartwatch, Garmin, dan Strava."
    ),
    KML(
        extension = "kml",
        mimeType = "application/vnd.google-earth.kml+xml",
        title = "KML (Keyhole Markup Language)",
        description = "Format XML standar OGC untuk Google Earth dan Google My Maps."
    ),
    GEOJSON(
        extension = "geojson",
        mimeType = "application/geo+json",
        title = "GeoJSON (RFC 7946)",
        description = "Format JSON modern untuk web mapping, Leaflet, Mapbox, dan QGIS."
    )
}
