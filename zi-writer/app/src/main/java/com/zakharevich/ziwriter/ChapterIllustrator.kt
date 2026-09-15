package com.zakharevich.ziwriter

import android.content.Context
import android.graphics.*
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

object ChapterIllustrator {

    enum class IllustrationType(val keywords: List<String>, val emoji: String) {
        SEA(listOf("море", "берег", "волна", "океан", "вода", "пляж", "прибой"), "🌊"),
        CITY(listOf("город", "улица", "дом", "здание", "окно", "крыша", "переулок"), "🏙️"),
        FOREST(listOf("лес", "дерево", "ветка", "листва", "тайга", "роща"), "🌲"),
        NIGHT(listOf("ночь", "луна", "звезда", "темнота", "полночь", "сумерки"), "🌙"),
        FIRE(listOf("огонь", "пламя", "костер", "пожар", "дым", "искра"), "🔥"),
        WEAPON(listOf("выстрел", "оружие", "пистолет", "гильза", "цель", "патрон"), "🎯"),
        HEART(listOf("любовь", "сердце", "поцелуй", "роман", "чувств", "объятие"), "❤️"),
        BOOK(listOf("книга", "письмо", "дневник", "архив", "папка", "документ"), "📚"),
        SHIP(listOf("корабль", "станция", "космос", "галактика", "ракета", "орбита"), "🚀"),
        MYSTERY(listOf("тайна", "загадка", "след", "улика", "детектив", "поиск"), "🔍");

        companion object {
            fun detect(text: String): IllustrationType {
                val lower = text.lowercase()
                for (type in values()) {
                    if (type.keywords.any { lower.contains(it) }) return type
                }
                return values().random()
            }
        }
    }

    fun generateIllustration(context: Context, chapterTitle: String, content: String, width: Int = 800, height: Int = 400): File? {
        return try {
            val type = IllustrationType.detect("$chapterTitle $content")
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val bgPaint = Paint()
            val colors = when (type) {
                IllustrationType.SEA -> intArrayOf(Color.parseColor("#0D47A1"), Color.parseColor("#42A5F5"), Color.parseColor("#E1F5FE"))
                IllustrationType.CITY -> intArrayOf(Color.parseColor("#263238"), Color.parseColor("#546E7A"), Color.parseColor("#B0BEC5"))
                IllustrationType.FOREST -> intArrayOf(Color.parseColor("#1B5E20"), Color.parseColor("#66BB6A"), Color.parseColor("#C8E6C9"))
                IllustrationType.NIGHT -> intArrayOf(Color.parseColor("#000000"), Color.parseColor("#1A237E"), Color.parseColor("#5C6BC0"))
                IllustrationType.FIRE -> intArrayOf(Color.parseColor("#BF360C"), Color.parseColor("#FF5722"), Color.parseColor("#FFCCBC"))
                IllustrationType.WEAPON -> intArrayOf(Color.parseColor("#212121"), Color.parseColor("#424242"), Color.parseColor("#9E9E9E"))
                IllustrationType.HEART -> intArrayOf(Color.parseColor("#880E4F"), Color.parseColor("#EC407A"), Color.parseColor("#F8BBD0"))
                IllustrationType.BOOK -> intArrayOf(Color.parseColor("#3E2723"), Color.parseColor("#8D6E63"), Color.parseColor("#D7CCC8"))
                IllustrationType.SHIP -> intArrayOf(Color.parseColor("#0A0E2E"), Color.parseColor("#1A237E"), Color.parseColor("#00BCD4"))
                IllustrationType.MYSTERY -> intArrayOf(Color.parseColor("#1A1A1A"), Color.parseColor("#424242"), Color.parseColor("#BDBDBD"))
            }

            val gradient = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), colors, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            bgPaint.shader = gradient
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            val shapePaint = Paint().apply { color = Color.parseColor("#30FFFFFF"); isAntiAlias = true }
            when (type) {
                IllustrationType.SEA -> {
                    val path = Path()
                    path.moveTo(0f, height * 0.6f)
                    for (x in 0..width step 50) {
                        val y = height * 0.6f + Random.nextFloat() * 40 - 20 + (Math.sin(x / 50.0) * 20).toFloat()
                        path.lineTo(x.toFloat(), y)
                    }
                    path.lineTo(width.toFloat(), height.toFloat())
                    path.lineTo(0f, height.toFloat())
                    path.close()
                    canvas.drawPath(path, shapePaint)
                }
                IllustrationType.CITY -> {
                    var x = 0f
                    while (x < width) {
                        val w = Random.nextFloat() * 80 + 40
                        val h = Random.nextFloat() * height * 0.5f + height * 0.2f
                        canvas.drawRect(x, height - h, x + w, height.toFloat(), shapePaint)
                        x += w + 10
                    }
                }
                IllustrationType.FOREST -> {
                    var x = 0f
                    while (x < width) {
                        val path = Path()
                        val treeH = Random.nextFloat() * 200 + 100
                        path.moveTo(x, height.toFloat())
                        path.lineTo(x + 40, height - treeH)
                        path.lineTo(x + 80, height.toFloat())
                        path.close()
                        canvas.drawPath(path, shapePaint)
                        x += 60
                    }
                }
                IllustrationType.NIGHT -> {
                    val starPaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
                    for (i in 0..50) {
                        val sx = Random.nextFloat() * width
                        val sy = Random.nextFloat() * height * 0.7f
                        canvas.drawCircle(sx, sy, Random.nextFloat() * 2 + 1, starPaint)
                    }
                }
                else -> {
                    for (i in 0..5) {
                        val cx = Random.nextFloat() * width
                        val cy = Random.nextFloat() * height
                        val r = Random.nextFloat() * 100 + 50
                        canvas.drawCircle(cx, cy, r, shapePaint)
                    }
                }
            }

            val titlePaint = Paint().apply {
                color = Color.WHITE
                textSize = 36f
                isFakeBoldText = true
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                setShadowLayer(6f, 0f, 3f, Color.BLACK)
            }

            val emojiPaint = Paint().apply {
                textSize = 64f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }

            canvas.drawText(type.emoji, width / 2f, height * 0.4f, emojiPaint)

            val words = chapterTitle.split(" ")
            var lines = mutableListOf<String>()
            var current = ""
            for (word in words) {
                val test = if (current.isEmpty()) word else "$current $word"
                if (titlePaint.measureText(test) > width - 80) {
                    if (current.isNotEmpty()) lines.add(current)
                    current = word
                } else current = test
            }
            if (current.isNotEmpty()) lines.add(current)
            if (lines.size > 3) lines = lines.take(3).toMutableList()

            var y = height * 0.65f
            for (line in lines) {
                canvas.drawText(line.uppercase(), width / 2f, y, titlePaint)
                y += 42
            }

            val safeTitle = chapterTitle.replace(Regex("[^A-Za-zА-Яа-я0-9]"), "_").take(20)
            val fileName = "Illust_${safeTitle}_${System.currentTimeMillis()}.png"
            val dir = File(context.getExternalFilesDir(null), "illustrations")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }

            try {
                val dlDir = File("/storage/emulated/0/Download/ZIWriter_Illustrations")
                if (!dlDir.exists()) dlDir.mkdirs()
                val dlFile = File(dlDir, fileName)
                file.copyTo(dlFile, true)
            } catch (e: Exception) {}

            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun generateIllustrationsForBook(context: Context, chapterTitles: List<String>, bookTitle: String): List<File> {
        val files = mutableListOf<File>()
        for (title in chapterTitles.take(10)) {
            val illustration = generateIllustration(context, title, "$bookTitle $title")
            if (illustration != null) files.add(illustration)
        }
        return files
    }

    fun generateIllustrationsForBookLegacy(context: Context, title: String, content: String): List<File> {
        val chapters = content.split(Regex("\n(?=Глава|ГЛАВА|Пролог|ПРОЛОГ|Эпилог|ЭПИЛОГ)")).filter { it.length > 50 }
        val files = mutableListOf<File>()
        for (chapter in chapters.take(10)) {
            val chapterTitle = chapter.lines().firstOrNull()?.take(100) ?: "Глава"
            val illustration = generateIllustration(context, chapterTitle, chapter)
            if (illustration != null) files.add(illustration)
        }
        return files
    }
}
