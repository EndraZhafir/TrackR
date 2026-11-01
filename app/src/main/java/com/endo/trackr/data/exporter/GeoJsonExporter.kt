package com.endo.trackr.data.exporter

import com.endo.trackr.core.util.DateTimeUtils
import com.endo.trackr.data.local.entity.LocationPointEntity
import com.endo.trackr.data.local.entity.StayPointEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Generator dokumen GeoJSON (RFC 7946) standar IETF.
 * Format ini adalah standar emas untuk sistem Web GIS (Leaflet, Mapbox, OpenLayers, kepler.gl, QGIS).
 */
object GeoJsonExporter {

    fun export(
        sessionTitle: String,
        points: List<LocationPointEntity>,
        stayPoints: List<StayPointEntity> = emptyList(),
        includeStayPoints: Boolean = true,
        filterJitter: Boolean = true
    ): String {
        val validPoints = if (filterJitter) points.filter { !it.isJitter } else points
        val root = JSONObject()
        root.put("type", "FeatureCollection")

        // 1. Metadata Properti Ekspor
        val metadata = JSONObject().apply {
            put("generator", "TrackR - Lightweight GPS Logger")
            put("sessionTitle", sessionTitle)
            put("exportedAt", DateTimeUtils.formatIso8601Utc(System.currentTimeMillis()))
            put("totalTrackPoints", validPoints.size)
            put("stayPointsCount", if (includeStayPoints) stayPoints.size else 0)
        }
        root.put("metadata", metadata)

        val features = JSONArray()

        // 2. Feature Rute Perjalanan (LineString)
        if (validPoints.isNotEmpty()) {
            val lineFeature = JSONObject()
            lineFeature.put("type", "Feature")

            val lineProperties = JSONObject().apply {
                put("name", sessionTitle)
                put("type", "TrajectoryLine")
                put("pointCount", validPoints.size)
                put("startTime", DateTimeUtils.formatIso8601Utc(validPoints.first().timestamp))
                put("endTime", DateTimeUtils.formatIso8601Utc(validPoints.last().timestamp))
            }
            lineFeature.put("properties", lineProperties)

            val geometry = JSONObject()
            geometry.put("type", "LineString")

            val coordsArray = JSONArray()
            for (p in validPoints) {
                val pointCoord = JSONArray()
                // Format GeoJSON: [longitude, latitude, altitude]
                pointCoord.put(p.longitude)
                pointCoord.put(p.latitude)
                pointCoord.put(0.0)
                coordsArray.put(pointCoord)
            }
            geometry.put("coordinates", coordsArray)
            lineFeature.put("geometry", geometry)

            features.put(lineFeature)
        }

        // 3. Feature Titik Singgah (Point)
        if (includeStayPoints) {
            stayPoints.forEachIndexed { index, stay ->
                val pointFeature = JSONObject()
                pointFeature.put("type", "Feature")

                val pointProps = JSONObject().apply {
                    put("id", stay.id)
                    put("name", stay.resolvedAddress ?: "Titik Singgah #${index + 1}")
                    put("type", "StayPoint")
                    put("address", stay.resolvedAddress ?: JSONObject.NULL)
                    put("durationSeconds", stay.durationSeconds)
                    put("durationFormatted", DateTimeUtils.formatDuration(stay.durationSeconds))
                    put("arrivalTime", DateTimeUtils.formatIso8601Utc(stay.arrivalTime))
                    put("departureTime", DateTimeUtils.formatIso8601Utc(stay.departureTime))
                    put("clusteredPoints", stay.pointCount)
                }
                pointFeature.put("properties", pointProps)

                val geometry = JSONObject()
                geometry.put("type", "Point")

                val pointCoord = JSONArray()
                pointCoord.put(stay.longitude)
                pointCoord.put(stay.latitude)
                pointCoord.put(0.0)
                geometry.put("coordinates", pointCoord)

                pointFeature.put("geometry", geometry)
                features.put(pointFeature)
            }
        }

        root.put("features", features)

        // Indentasi 2 spasi agar mudah dibaca manusia maupun parser
        return root.toString(2)
    }
}
