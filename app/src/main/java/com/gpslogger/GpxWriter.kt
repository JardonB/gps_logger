package com.gpslogger

import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class GpxWriter(private val file: File) {

    private val writer: BufferedWriter
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    init {
        writer = BufferedWriter(FileWriter(file, false))
    }

    @Synchronized
    fun startTrack(trackName: String = "GPS Log") {
        writer.write("""<?xml version="1.0" encoding="UTF-8"?>""")
        writer.newLine()
        writer.write("""<gpx version="1.1" creator="GPS Logger for Android"""")
        writer.newLine()
        writer.write("""    xmlns="http://www.topografix.com/GPX/1/1"""")
        writer.newLine()
        writer.write("""    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"""")
        writer.newLine()
        writer.write("""    xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">""")
        writer.newLine()
        writer.write("  <metadata>")
        writer.newLine()
        writer.write("    <name>${escapeXml(trackName)}</name>")
        writer.newLine()
        writer.write("    <time>${isoFormat.format(Date())}</time>")
        writer.newLine()
        writer.write("  </metadata>")
        writer.newLine()
        writer.write("  <trk>")
        writer.newLine()
        writer.write("    <name>${escapeXml(trackName)}</name>")
        writer.newLine()
        writer.write("    <trkseg>")
        writer.newLine()
        writer.flush()
    }

    @Synchronized
    fun addTrackPoint(lat: Double, lon: Double, ele: Double, time: Long) {
        val timeStr = isoFormat.format(Date(time))
        writer.write("""      <trkpt lat="$lat" lon="$lon">""")
        writer.newLine()
        writer.write("        <ele>$ele</ele>")
        writer.newLine()
        writer.write("        <time>$timeStr</time>")
        writer.newLine()
        writer.write("      </trkpt>")
        writer.newLine()
        writer.flush()
    }

    @Synchronized
    fun endTrack() {
        writer.write("    </trkseg>")
        writer.newLine()
        writer.write("  </trk>")
        writer.newLine()
        writer.write("</gpx>")
        writer.newLine()
        writer.flush()
        writer.close()
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
