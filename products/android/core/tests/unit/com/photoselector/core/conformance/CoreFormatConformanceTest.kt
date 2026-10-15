package com.photoselector.core.conformance

import com.photoselector.core.model.ExifData
import com.photoselector.core.preview.EmbeddedPreviewExtractor
import com.photoselector.core.reader.AndroidExifReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.RandomAccessFile

/**
 * Format conformance of :core against testdata/formats/manifest.json.
 *
 * :core owns the two pieces both Android products route every format through:
 * the EXIF reader ([AndroidExifReader]) and the embedded-preview extractor
 * ([EmbeddedPreviewExtractor]). The app modules prove discovery, display and
 * scoring on top of them.
 *
 * Committed bitmaps always run; the RAW corpus runs when it is downloaded
 * (`python3 scripts/fetch_format_corpus.py`) and is mandatory with
 * `PST_REQUIRE_FORMAT_CORPUS=1`. Known gaps are strict: a listed gap that
 * starts passing fails the test until it is removed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoreFormatConformanceTest {

    private enum class Cap { EXIF, PREVIEW }

    private companion object {
        /**
         * Samples whose embedded preview is genuinely smaller than the display
         * minimum. Callers must fall back to a platform decode for these.
         */
        val PREVIEW_BELOW_DISPLAY_MINIMUM = mapOf(
            "dng-pixel4a" to "Pixel phone DNGs embed a 672 px preview; display falls back to a real decode",
        )

        /** (sample, capability) → reason. Strict: remove an entry once it passes. */
        val KNOWN_GAPS: Map<Pair<String, Cap>, String> = mapOf(
            ("heif-sample" to Cap.EXIF) to
                "ExifInterface reads HEIF through MediaMetadataRetriever, which Robolectric does not implement",
        )
    }

    private val reader = AndroidExifReader()

    private fun exifProblems(exif: ExifData?, e: FormatManifest.Expect): List<String> {
        if (exif == null) return listOf("no ExifData")
        return buildList {
            if (!FormatManifest.sameText(exif.make, e.make)) add("make '${exif.make}' != '${e.make}'")
            if (!FormatManifest.sameText(exif.model, e.model)) add("model '${exif.model}' != '${e.model}'")
            if (exif.iso != e.iso) add("iso ${exif.iso} != ${e.iso}")
            if (!FormatManifest.close(exif.shutterSpeed, e.exposureTimeS)) add("exposure ${exif.shutterSpeed} != ${e.exposureTimeS}")
            if (!FormatManifest.close(exif.aperture, e.fNumber)) add("f ${exif.aperture} != ${e.fNumber}")
            if (!FormatManifest.close(exif.focalLength, e.focalLengthMm)) add("focal ${exif.focalLength} != ${e.focalLengthMm}")
            if (e.lens != null && !FormatManifest.sameText(exif.lens, e.lens)) add("lens '${exif.lens}' != '${e.lens}'")
            if (exif.make != null && exif.make != exif.make.trim()) add("make not trimmed")
            if (exif.model != null && exif.model != exif.model.trim()) add("model not trimmed")
        }
    }

    /** Problems with the preview path, or empty if it meets the display contract. */
    private fun previewProblems(sample: FormatManifest.Sample, file: File): List<String> {
        val required = minOf(FormatManifest.minDisplayLongEdge, sample.expect.longEdgePx)
        val all = RandomAccessFile(file, "r").use { EmbeddedPreviewExtractor.findAll(it.channel) }
        if (all.isEmpty()) return listOf("no embedded preview")
        val chosen = EmbeddedPreviewExtractor.extract(file, required) ?: return listOf("extract failed")
        val bitmap = chosen.decode(maxLongEdge = required)
            ?: return listOf("preview ${chosen.preview.width}x${chosen.preview.height} did not decode")
        val decodedLong = maxOf(bitmap.width, bitmap.height)
        println(
            "${sample.id}: ${all.size} previews ${all.map { "${it.width}x${it.height}" }}, " +
                "chosen ${chosen.preview.width}x${chosen.preview.height} o=${chosen.preview.orientation}, " +
                "decoded ${bitmap.width}x${bitmap.height}"
        )
        bitmap.recycle()
        return buildList {
            if (decodedLong < required && sample.id !in PREVIEW_BELOW_DISPLAY_MINIMUM) {
                add("preview long edge $decodedLong < $required")
            }
            if (decodedLong >= required && sample.id in PREVIEW_BELOW_DISPLAY_MINIMUM) {
                add("preview now reaches $required — remove from PREVIEW_BELOW_DISPLAY_MINIMUM")
            }
            if (chosen.preview.longEdge > maxOf(sample.expect.longEdgePx, 1) * 11 / 10) {
                add("preview ${chosen.preview.longEdge} larger than native ${sample.expect.longEdgePx}")
            }
        }
    }

    private fun run(samples: List<FormatManifest.Sample>, caps: List<Cap>) {
        val results = linkedMapOf<String, Map<Cap, List<String>>>()
        for (s in samples) {
            val file = FormatManifest.fileOf(s)
            val row = linkedMapOf<Cap, List<String>>()
            for (cap in caps) {
                row[cap] = try {
                    when (cap) {
                        Cap.EXIF -> exifProblems(runBlocking { reader.readExif(file) }, s.expect)
                        Cap.PREVIEW -> previewProblems(s, file)
                    }
                } catch (t: Throwable) {
                    listOf("threw $t")
                }
            }
            results[s.id] = row
        }
        val table = buildString {
            appendLine("sample".padEnd(24) + caps.joinToString("") { it.name.padEnd(10) })
            for ((id, row) in results) {
                append(id.padEnd(24))
                row.forEach { (cap, p) ->
                    val gap = (id to cap) in KNOWN_GAPS
                    append((if (p.isEmpty()) (if (gap) "FIXED" else "ok") else if (gap) "GAP" else "FAIL").padEnd(10))
                }
                appendLine()
            }
        }
        println(table)
        val failures = results.flatMap { (id, row) ->
            row.filter { (cap, p) -> p.isNotEmpty() && (id to cap) !in KNOWN_GAPS }
                .map { (cap, p) -> "$id/$cap: $p" }
        }
        val fixed = results.flatMap { (id, row) ->
            row.filter { (cap, p) -> p.isEmpty() && (id to cap) in KNOWN_GAPS }.map { (cap, _) -> "$id/$cap" }
        }
        assertTrue("conformance failures:\n${failures.joinToString("\n")}\n$table", failures.isEmpty())
        assertTrue("known gaps now pass — prune KNOWN_GAPS: $fixed\n$table", fixed.isEmpty())
    }

    @Test
    fun `committed bitmaps - EXIF via AndroidExifReader`() {
        run(FormatManifest.repoSamples, listOf(Cap.EXIF))
    }

    @Test
    fun `committed bitmaps carry no embedded RAW preview`() {
        for (s in FormatManifest.repoSamples) {
            val all = RandomAccessFile(FormatManifest.fileOf(s), "r").use {
                EmbeddedPreviewExtractor.findAll(it.channel)
            }
            if (s.format == "jpeg") continue // a JPEG with an EXIF thumbnail is not a RAW container
            assertEquals("${s.id} must not yield a RAW preview", emptyList<Any>(), all)
        }
    }

    @Test
    fun `corpus RAW samples - EXIF and embedded preview`() {
        FormatManifest.requireCorpus()
        run(FormatManifest.corpusSamples, listOf(Cap.EXIF, Cap.PREVIEW))
    }

    @Test
    fun `corpus RAW samples - every one embeds at least one preview`() {
        FormatManifest.requireCorpus()
        for (s in FormatManifest.corpusSamples) {
            val all = RandomAccessFile(FormatManifest.fileOf(s), "r").use {
                EmbeddedPreviewExtractor.findAll(it.channel)
            }
            assertTrue("${s.id}: no preview found", all.isNotEmpty())
        }
    }
}
