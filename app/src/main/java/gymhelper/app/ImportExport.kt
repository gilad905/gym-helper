package gymhelper.app

import android.content.Context
import android.net.Uri
import gymhelper.core.HistoryMarkdown
import gymhelper.core.Plan
import gymhelper.core.PlanMarkdown
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ImportExport {
    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "gif")

    /**
     * Reads a .md file or a .zip (a Notion export or one made by [export]). Images in the zip are
     * copied into app storage and the plan is pointed at them. Returns the plan; does not apply it.
     */
    fun import(context: Context, uri: Uri): Plan {
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (!isZip) return PlanMarkdown.parse(bytes.toString(Charsets.UTF_8))

        val markdown = mutableListOf<Pair<String, String>>()
        val images = mutableMapOf<String, ByteArray>()
        readZip(bytes, markdown, images, depth = 0)

        val md = markdown.firstOrNull { !it.first.substringAfterLast('/').equals("history.md", true) }
            ?: error("No markdown file found in the zip")
        val plan = PlanMarkdown.parse(md.second)

        val imgDir = File(AppState.dir, "images").apply { mkdirs() }
        val saved = mutableMapOf<String, String>()
        for ((name, data) in images) {
            val base = name.substringAfterLast('/')
            File(imgDir, base).writeBytes(data)
            saved[base] = "images/$base"
        }
        return plan.copy(
            workouts = plan.workouts.map { w ->
                w.copy(exercises = w.exercises.map { e ->
                    val base = e.image?.substringAfterLast('/')
                    if (base != null && base in saved) e.copy(image = saved[base]) else e
                })
            },
        )
    }

    private fun readZip(
        bytes: ByteArray,
        markdown: MutableList<Pair<String, String>>,
        images: MutableMap<String, ByteArray>,
        depth: Int,
    ) {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || entry.name.contains("__MACOSX")) continue
                val ext = entry.name.substringAfterLast('.', "").lowercase()
                when {
                    ext == "md" -> markdown += entry.name to zip.readBytes().toString(Charsets.UTF_8)
                    ext in imageExt -> images[entry.name] = zip.readBytes()
                    // Notion sometimes wraps the export in another zip.
                    ext == "zip" && depth < 2 -> readZip(zip.readBytes(), markdown, images, depth + 1)
                }
            }
        }
    }

    /** Writes plan.md, the images it uses, and history.md into a zip. */
    fun export(context: Context, uri: Uri) {
        val plan = AppState.plan.value
        context.contentResolver.openOutputStream(uri)!!.use { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("plan.md"))
                zip.write(PlanMarkdown.write(plan).toByteArray())
                zip.closeEntry()
                val written = mutableSetOf<String>()
                for (e in plan.workouts.flatMap { it.exercises }) {
                    val path = e.image ?: continue
                    val f = AppState.imageFile(path) ?: continue
                    if (!written.add(path)) continue
                    zip.putNextEntry(ZipEntry(path))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry("history.md"))
                zip.write(HistoryMarkdown.write(AppState.history.value).toByteArray())
                zip.closeEntry()
            }
        }
    }
}
