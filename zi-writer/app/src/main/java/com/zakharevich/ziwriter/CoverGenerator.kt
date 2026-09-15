package com.zakharevich.ziwriter

import android.content.Context
import android.graphics.*
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

object CoverGenerator {

    enum class CoverStyle(val displayName: String, val colors: IntArray, val accent: String) {
        GRADIENT("Градиент ULTRA", intArrayOf(Color.parseColor("#2D1B69"), Color.parseColor("#5B4CAD"), Color.parseColor("#FF6B35")), "#FFCCBC"),
        NOIR("Нуар Детектив", intArrayOf(Color.parseColor("#0F0F0F"), Color.parseColor("#2C2C2C"), Color.parseColor("#8B0000")), "#D3D3D3"),
        FANTASY("Космос Фантастика", intArrayOf(Color.parseColor("#0A0E2E"), Color.parseColor("#1A237E"), Color.parseColor("#00BCD4")), "#B2EBF2"),
        ROMANTIC("Романтика", intArrayOf(Color.parseColor("#4A0E4E"), Color.parseColor("#9C27B0"), Color.parseColor("#FF4081")), "#F8BBD0"),
        ADVENTURE("Приключения", intArrayOf(Color.parseColor("#1B5E20"), Color.parseColor("#388E3C"), Color.parseColor("#FFC107")), "#DCEDC8"),
        HORROR("Хоррор", intArrayOf(Color.parseColor("#000000"), Color.parseColor("#212121"), Color.parseColor("#424242")), "#B71C1C"),
        MINIMAL("Минимализм", intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#F5F5F5"), Color.parseColor("#2D1B69")), "#2D1B69")
    }

    fun generateCover(context: Context, title: String, author: String = "ZI Writer", coverStyle: CoverStyle = CoverStyle.GRADIENT): File? {
        return try {
            val width = 800
            val height = 1200
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val isMinimal = coverStyle == CoverStyle.MINIMAL

            // Background
            val paintBg = Paint()
            if (coverStyle.colors.size >= 3) {
                val gradient = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    coverStyle.colors,
                    floatArrayOf(0f, 0.6f, 1f),
                    Shader.TileMode.CLAMP
                )
                paintBg.shader = gradient
            } else {
                paintBg.color = coverStyle.colors[0]
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintBg)

            // Decorative elements based on style
            val paintCircle = Paint().apply { 
                color = if (isMinimal) Color.parseColor("#102D1B69") else Color.parseColor("#22FFFFFF")
                isAntiAlias = true 
            }
            when (coverStyle) {
                CoverStyle.NOIR -> {
                    // Rain effect lines
                    val paintLine = Paint().apply { color = Color.parseColor("#15FFFFFF"); strokeWidth = 2f }
                    for (i in 0..30) {
                        val x = Random.nextFloat() * width
                        val y = Random.nextFloat() * height
                        canvas.drawLine(x, y, x - 20, y + 60, paintLine)
                    }
                }
                CoverStyle.FANTASY -> {
                    // Stars
                    val paintStar = Paint().apply { color = Color.WHITE; isAntiAlias = true }
                    for (i in 0..80) {
                        val x = Random.nextFloat() * width
                        val y = Random.nextFloat() * height * 0.7f
                        val r = Random.nextFloat() * 2 + 1
                        canvas.drawCircle(x, y, r, paintStar)
                    }
                }
                CoverStyle.ROMANTIC -> {
                    // Hearts (circles)
                    for (i in 0..5) {
                        val x = Random.nextFloat() * width
                        val y = Random.nextFloat() * height
                        val r = Random.nextFloat() * 80 + 40
                        canvas.drawCircle(x, y, r, paintCircle)
                    }
                }
                else -> {
                    canvas.drawCircle(width * 0.8f, height * 0.2f, 200f, paintCircle)
                    canvas.drawCircle(width * 0.2f, height * 0.8f, 300f, paintCircle)
                    canvas.drawCircle(width * 0.5f, height * 0.5f, 150f, paintCircle)
                }
            }

            // Border for minimal
            if (isMinimal) {
                val borderPaint = Paint().apply {
                    color = Color.parseColor("#2D1B69")
                    style = Paint.Style.STROKE
                    strokeWidth = 12f
                    isAntiAlias = true
                }
                canvas.drawRect(12f, 12f, width - 12f, height - 12f, borderPaint)
            }

            // Title
            val titleColor = if (isMinimal) Color.parseColor("#2D1B69") else Color.WHITE
            val paintTitle = Paint().apply {
                color = titleColor
                textSize = 58f
                isFakeBoldText = true
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                setShadowLayer(if (isMinimal) 0f else 8f, 0f, 4f, Color.parseColor("#66000000"))
            }

            // Wrap title
            val words = title.split(" ")
            var lines = mutableListOf<String>()
            var current = ""
            for (word in words) {
                val test = if (current.isEmpty()) word else "$current $word"
                if (paintTitle.measureText(test) > width - 120) {
                    if (current.isNotEmpty()) lines.add(current)
                    current = word
                } else {
                    current = test
                }
            }
            if (current.isNotEmpty()) lines.add(current)
            if (lines.size > 5) lines = lines.take(5).toMutableList()

            var y = height * 0.38f
            for (line in lines) {
                canvas.drawText(line.uppercase(), width / 2f, y, paintTitle)
                y += 72
            }

            // Divider line
            val dividerPaint = Paint().apply {
                color = if (isMinimal) Color.parseColor("#2D1B69") else Color.parseColor(coverStyle.accent)
                strokeWidth = 4f
                isAntiAlias = true
            }
            canvas.drawLine(width * 0.3f, y + 10, width * 0.7f, y + 10, dividerPaint)

            // Author
            val authorColor = if (isMinimal) Color.parseColor("#5A5D60") else Color.parseColor(coverStyle.accent)
            val paintAuthor = Paint().apply {
                color = authorColor
                textSize = 30f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            canvas.drawText(author, width / 2f, height * 0.72f, paintAuthor)

            val paintSmall = Paint().apply {
                color = if (isMinimal) Color.parseColor("#8A8D90") else Color.parseColor("#C5CAE9")
                textSize = 18f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Zakharevich Igor +375293371412", width / 2f, height * 0.78f, paintSmall)
            canvas.drawText("ZI Writer v6.0 MEGA • ${coverStyle.displayName}", width / 2f, height * 0.82f, paintSmall)

            // Logo ZI
            val paintLogo = Paint().apply {
                color = if (isMinimal) Color.parseColor("#2D1B69") else Color.WHITE
                isAntiAlias = true
            }
            val logoRect = RectF(width/2f - 65, height*0.12f, width/2f + 65, height*0.12f + 85)
            canvas.drawRoundRect(logoRect, 22f, 22f, paintLogo)
            val paintLogoText = Paint().apply {
                color = if (isMinimal) Color.WHITE else coverStyle.colors[0]
                textSize = 44f
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText("ZI", width/2f, height*0.12f + 58, paintLogoText)

            // Genre badge
            val badgePaint = Paint().apply {
                color = Color.parseColor("#E0FFFFFF")
                isAntiAlias = true
            }
            val badgeRect = RectF(width/2f - 100, height*0.88f, width/2f + 100, height*0.88f + 40)
            canvas.drawRoundRect(badgeRect, 20f, 20f, badgePaint)
            val badgeTextPaint = Paint().apply {
                color = coverStyle.colors[0]
                textSize = 16f
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText(coverStyle.displayName.uppercase(), width/2f, height*0.88f + 26, badgeTextPaint)

            // Save
            val safeTitle = title.replace(Regex("[^A-Za-zА-Яа-я0-9]"), "_").take(15)
            val fileName = "Cover_${safeTitle}_${coverStyle.name}_${System.currentTimeMillis()}.png"
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            val file = File(dir, fileName)
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

            // Копируем в «Загрузки» (MediaStore на Android 10+, прямая запись на более старых)
            DownloadSaver.save(context, file, "image/png")?.let { return it }

            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getRandomStyleForGenre(genre: String): CoverStyle {
        return when (genre) {
            "Детектив" -> CoverStyle.NOIR
            "Фантастика" -> CoverStyle.FANTASY
            "Роман" -> CoverStyle.ROMANTIC
            "Приключения" -> CoverStyle.ADVENTURE
            "Хоррор" -> CoverStyle.HORROR
            "Боевик" -> CoverStyle.GRADIENT
            "Юмор" -> CoverStyle.MINIMAL
            else -> CoverStyle.GRADIENT
        }
    }
}
