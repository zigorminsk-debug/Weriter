
package com.zakharevich.ziwriter

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.zakharevich.ziwriter.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private var generatedStory: StoryGenerator.GeneratedStory? = null
    private var currentFontSize = 14f
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsSpeed = 1.0f
    private var selectedCoverStyle: CoverGenerator.CoverStyle? = null
    private var lastSearchResults: List<InternetBookFetcher.BookInfo> = emptyList()
    private var lastOpdsResults: List<OpdsFetcher.OpdsBook> = emptyList()
    private var pendingUpdate: UpdateManager.ReleaseInfo? = null

    private val storagePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        // Читаем не больше 30000 знаков: крупный файл целиком в память не тянем (иначе OOM)
                        val text = input.bufferedReader().use { readLimited(it, 30000) }
                        binding.etInput.setText(text)
                        Toast.makeText(this, "Загружено ${text.length} знаков", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val zipPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        val tempFile = File(cacheDir, "import_learned.zip")
                        tempFile.outputStream().use { out -> input.copyTo(out) }
                        val ok = LearningManager.importLearnedFromZip(this, tempFile)
                        if (ok) {
                            updateLearnedCount()
                            Toast.makeText(this, "Обучение импортировано!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "Ошибка импорта", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val fb2PickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        val tempFile = File(cacheDir, "import_${System.currentTimeMillis()}.fb2")
                        tempFile.outputStream().use { out -> input.copyTo(out) }
                        lifecycleScope.launch(Dispatchers.IO) {
                            val learned = LearningManager.learnFromFb2File(this@MainActivity, tempFile)
                            withContext(Dispatchers.Main) {
                                if (learned != null) {
                                    updateLearnedCount()
                                    Toast.makeText(this@MainActivity, "FB2 изучен: ${learned.title} ${learned.chars} зн", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(this@MainActivity, "Ошибка FB2", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val epubPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        val tempFile = File(cacheDir, "import_${System.currentTimeMillis()}.epub")
                        tempFile.outputStream().use { out -> input.copyTo(out) }
                        lifecycleScope.launch(Dispatchers.IO) {
                            val learned = LearningManager.learnFromEpubFile(this@MainActivity, tempFile)
                            withContext(Dispatchers.Main) {
                                if (learned != null) {
                                    updateLearnedCount()
                                    Toast.makeText(this@MainActivity, "EPUB изучен: ${learned.title}", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(this@MainActivity, "Ошибка EPUB", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
        } catch (e: Exception) {
            try {
                val tv = android.widget.TextView(this)
                tv.text = "Не удалось загрузить интерфейс. Данные обучения сохранены — перезапустите приложение."
                setContentView(tv)
            } catch (ex: Exception) {}
            return
        }
        try { tts = TextToSpeech(this, this) } catch (e: Exception) { android.util.Log.e("ZIWriter", "TTS init failed", e) }
        try { setupDropdowns() } catch (e: Exception) { android.util.Log.e("ZIWriter", "setupDropdowns failed", e) }
        try { setupListeners() } catch (e: Exception) { android.util.Log.e("ZIWriter", "setupListeners failed", e) }
        try { updateEstimate() } catch (e: Exception) { android.util.Log.e("ZIWriter", "updateEstimate failed", e) }
        // Раньше здесь стоял «crash_marker»: если файл-маркер оставался от прошлого запуска
        // (т.е. после ЛЮБОГО падения приложения), база обучения полностью удалялась.
        // Это приводило к потере всех изученных книг — теперь данные никогда не стираются
        // автоматически, только по явной команде пользователя (кнопка «Очистить»).
        try { updateLearnedCount() } catch (e: Exception) {}

        // На Android 9 и ниже для сохранения файлов в «Загрузки» нужно разрешение
        // (на Android 10+ используется MediaStore, разрешение не требуется).
        try {
            if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P &&
                checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                storagePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        } catch (e: Exception) {}

        // Проверяем наличие обновлений с GitHub (фоновая проверка)
        try { checkForUpdates(silent = true) } catch (e: Exception) { android.util.Log.e("ZIWriter", "checkForUpdates failed", e) }
    }

    private fun readLimited(reader: java.io.Reader, limit: Int): String {
        val buffer = CharArray(limit)
        var total = 0
        while (total < limit) {
            val read = reader.read(buffer, total, limit - total)
            if (read <= 0) break
            total += read
        }
        return String(buffer, 0, total)
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            val result = tts?.setLanguage(Locale("ru", "RU"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
            }
            tts?.setSpeechRate(ttsSpeed)
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun setupDropdowns() {
        val genres = arrayOf("Авто (из текста)", "Фантастика", "Детектив", "Боевик", "Роман", "Приключения", "Хоррор", "Юмор", "Драма", "Философская проза")
        val styles = arrayOf("Детальный", "Нейтральный", "Лаконичный", "Поэтический", "Драматичный", "Ироничный")
        binding.actvGenre.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genres))
        binding.actvStyle.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, styles))
        binding.spGenre.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, genres)
        binding.spStyle.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, styles)
    }

    private fun setupListeners() {
        binding.sliderPages.addOnChangeListener { _, value, _ ->
            binding.tvPages.text = value.toInt().toString()
            updateEstimate()
            binding.chipGroupPages.clearCheck()
        }
        binding.chipGroupPages.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chip = group.findViewById<com.google.android.material.chip.Chip>(checkedIds[0])
                chip.tag?.toString()?.toIntOrNull()?.let { tag ->
                    binding.sliderPages.value = tag.toFloat()
                    binding.tvPages.text = tag.toString()
                    updateEstimate()
                }
            }
        }
        binding.chipGroupCover.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chip = group.findViewById<com.google.android.material.chip.Chip>(checkedIds[0])
                val tag = chip.tag?.toString() ?: "AUTO"
                selectedCoverStyle = when (tag) {
                    "GRADIENT" -> CoverGenerator.CoverStyle.GRADIENT
                    "NOIR" -> CoverGenerator.CoverStyle.NOIR
                    "FANTASY" -> CoverGenerator.CoverStyle.FANTASY
                    "ROMANTIC" -> CoverGenerator.CoverStyle.ROMANTIC
                    "MINIMAL" -> CoverGenerator.CoverStyle.MINIMAL
                    "ADVENTURE" -> CoverGenerator.CoverStyle.ADVENTURE
                    "HORROR" -> CoverGenerator.CoverStyle.HORROR
                    else -> null
                }
            } else {
                selectedCoverStyle = null
            }
        }
        binding.sliderTtsSpeed.addOnChangeListener { _, value, _ ->
            ttsSpeed = value
            tts?.setSpeechRate(ttsSpeed)
            if (tts?.isSpeaking == true) {
                binding.btnTts.text = "⏹ Стоп (${String.format("%.2f", ttsSpeed)}x)"
            }
        }
        binding.etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.tvInputCounter.text = "${s?.length ?: 0} знаков"
            }
        })
        binding.btnLoad.setOnClickListener { openFilePicker() }
        try { binding.btnImportFb2.setOnClickListener { openFb2Picker() } } catch (e: Exception) {}
        try { binding.btnImportEpub.setOnClickListener { openEpubPicker() } } catch (e: Exception) {}
        binding.btnPaste.setOnClickListener { pasteFromClipboard() }
        binding.btnClear.setOnClickListener {
            binding.etInput.text?.clear()
            binding.etBookTitle.text?.clear()
            binding.etAuthor.text?.clear()
            binding.etHeroes.text?.clear()
            binding.etPlace.text?.clear()
            binding.etResult.text?.clear()
            binding.cardResult.visibility = View.GONE
            binding.layoutTtsControls.visibility = View.GONE
            generatedStory = null
        }
        binding.btnHistory.setOnClickListener { showHistory() }
        binding.btnGenerate.setOnClickListener { generateStory(isContinue = false) }
        binding.btnContinue.setOnClickListener { generateStory(isContinue = true) }
        binding.btnSave.setOnClickListener { saveAsTxt() }
        binding.btnSavePdf.setOnClickListener { saveAsPdf() }
        binding.btnSaveDocx.setOnClickListener { saveAsDocx() }
        binding.btnSaveEpub.setOnClickListener { saveAsEpub() }
        binding.btnCover.setOnClickListener { generateCover() }
        try { binding.btnIllustrations.setOnClickListener { generateIllustrations() } } catch (e: Exception) {}
        binding.btnTts.setOnClickListener { toggleTts() }
        binding.btnShare.setOnClickListener { shareStory() }
        binding.btnCopy.setOnClickListener { copyToClipboard(binding.etResult.text.toString()) }
        binding.btnExportAll.setOnClickListener { exportAll() }
        binding.btnInternetSearch.setOnClickListener { searchInternetBooks() }
        binding.btnRandomPlot.setOnClickListener { fetchRandomPlot() }
        binding.btnLearnCurrent.setOnClickListener { learnFromCurrentText() }
        binding.btnLearnedBooks.setOnClickListener { showLearnedBooks() }
        binding.btnLearningStats.setOnClickListener { showLearningStats() }
        binding.btnClearLearned.setOnClickListener { clearLearned() }
        try { binding.btnExportLearned.setOnClickListener { exportLearned() } } catch (e: Exception) {}
        try { binding.btnImportLearned.setOnClickListener { importLearned() } } catch (e: Exception) {}
        try { binding.btnAutoLearn.setOnClickListener { autoLearnFlibusta() } } catch (e: Exception) {}
        try { binding.btnCheckUpdate.setOnClickListener { checkForUpdates(silent = false) } } catch (e: Exception) {}
        binding.btnFontMinus.setOnClickListener {
            if (currentFontSize > 10) {
                currentFontSize -= 1
                binding.etResult.textSize = currentFontSize
                binding.etInput.textSize = currentFontSize
            }
        }
        binding.btnFontPlus.setOnClickListener {
            if (currentFontSize < 22) {
                currentFontSize += 1
                binding.etResult.textSize = currentFontSize
                binding.etInput.textSize = currentFontSize
            }
        }
        binding.btnEditFull.setOnClickListener { openFullEditor() }
        binding.btnToInput.setOnClickListener { copyResultToInput() }

        // v31 MATH MODEL: при выборе жанра — изучить книги этого жанра с Флибусты
        try {
            binding.spGenre.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selectedGenre = parent?.getItemAtPosition(position)?.toString() ?: return
                    if (selectedGenre.startsWith("Авто")) return
                    onGenreSelectedForLearning(selectedGenre)
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        } catch (e: Exception) {}
        try {
            binding.actvGenre.setOnItemClickListener { parent, _, position, _ ->
                val selectedGenre = parent.getItemAtPosition(position)?.toString() ?: return@setOnItemClickListener
                if (selectedGenre.startsWith("Авто")) return@setOnItemClickListener
                onGenreSelectedForLearning(selectedGenre)
            }
        } catch (e: Exception) {}
    }

    private fun onGenreSelectedForLearning(genre: String) {
        // Проверяем сколько книг этого жанра уже изучено
        lifecycleScope.launch(Dispatchers.IO) {
            val booksByGenre = try { LearningManager.getLearnedBooksByGenre(this@MainActivity, genre) } catch (e: Exception) { emptyList() }
            val genreStats = try { LearningManager.getGenreStats(this@MainActivity) } catch (e: Exception) { emptyMap() }
            val count = booksByGenre.size
            withContext(Dispatchers.Main) {
                try {
                    if (count < 3) {
                        binding.tvInternetStatus.text = "📚 Жанр $genre: изучено $count книг, ищу еще с Флибусты..."
                        binding.progressInternet.visibility = View.VISIBLE
                        Snackbar.make(binding.root, "Жанр $genre: изучено $count книг. Запускаю дообучение с Флибусты (flibusta.net/opds)...", Snackbar.LENGTH_LONG).show()
                        // Авто-доучивание фоном
                        lifecycleScope.launch(Dispatchers.IO) {
                            val learned = try {
                                LearningManager.autoLearnByGenre(this@MainActivity, genre, 2) { msg ->
                                    lifecycleScope.launch(Dispatchers.Main) {
                                        try { binding.tvInternetStatus.text = msg } catch (e: Exception) {}
                                    }
                                }
                            } catch (e: Exception) { 0 }
                            withContext(Dispatchers.Main) {
                                binding.progressInternet.visibility = View.GONE
                                if (learned > 0) {
                                    binding.tvInternetStatus.text = "✅ Жанр $genre: доучено $learned книг с Флибусты"
                                    updateLearnedCount()
                                    Toast.makeText(this@MainActivity, "Жанр $genre: изучено +$learned книг с Флибусты", Toast.LENGTH_LONG).show()
                                } else {
                                    binding.tvInternetStatus.text = "Жанр $genre: $count книг (Флибуста недоступна, нужен VPN?)"
                                }
                            }
                        }
                    } else {
                        binding.tvInternetStatus.text = "✅ Жанр $genre: изучено $count книг, мат.модель готова"
                    }
                } catch (e: Exception) {}
            }
        }
    }

    private fun updateEstimate() {
        val pages = binding.sliderPages.value.toInt()
        val chars = pages * 1800
        val words = chars / 5
        val minutes = words / 180
        binding.tvCharsEstimate.text = "~$chars знаков • ~$words слов • $pages ${pluralPages(pages)}"
        binding.tvReadingTime.text = "~$minutes мин чтения"
    }

    private fun updateLearnedCount() {
        try {
            if (isFinishing || isDestroyed) return
            lifecycleScope.launch(Dispatchers.IO) {
                var count = try { LearningManager.getAllLearnedDataCount(this@MainActivity) } catch (e: Exception) {
                    try { LearningManager.getAllLearnedTemplatesCount(this@MainActivity) } catch (ex: Exception) { 0 }
                }
                var templates = try { LearningManager.getAllLearnedTemplatesCount(this@MainActivity) } catch (e: Exception) { 0 }
                var heroes = try { LearningManager.getLearnedHeroes(this@MainActivity, 1000).size } catch (e: Exception) { 0 }
                var places = try { LearningManager.getLearnedPlaces(this@MainActivity, 1000).size } catch (e: Exception) { 0 }
                var phrases = try { LearningManager.getLearnedPhrases(this@MainActivity, 1000).size } catch (e: Exception) { 0 }
                val books = try { LearningManager.getLearnedBooks(this@MainActivity).size } catch (e: Exception) { 0 }

                // Auto-rebuild if old data: 691 фраз, 0 гер, 0 мест, 0 обор - значит старая база, нужно пересобрать
                if (templates > 0 && heroes == 0 && places == 0 && phrases == 0 && books > 0) {
                    try {
                        val rebuilt = LearningManager.reprocessExistingBooks(this@MainActivity)
                        // recount after rebuild
                        templates = try { LearningManager.getAllLearnedTemplatesCount(this@MainActivity) } catch (e: Exception) { templates }
                        heroes = try { LearningManager.getLearnedHeroes(this@MainActivity, 1000).size } catch (e: Exception) { 0 }
                        places = try { LearningManager.getLearnedPlaces(this@MainActivity, 1000).size } catch (e: Exception) { 0 }
                        phrases = try { LearningManager.getLearnedPhrases(this@MainActivity, 1000).size } catch (e: Exception) { 0 }
                        count = try { LearningManager.getAllLearnedDataCount(this@MainActivity) } catch (e: Exception) { count }
                    } catch (e: Exception) {}
                }

                withContext(Dispatchers.Main) {
                    try {
                        if (isFinishing || isDestroyed) return@withContext
                        binding.chipLearnedCount.text = "$count данных ($templates фр, $heroes гер, $places мест, $phrases обор) • $books книг • OPDS flibusta.net"
                    } catch (e: Exception) {
                        try { binding.chipLearnedCount.text = "0 фраз • 0 книг • OPDS flibusta.net" } catch (ex: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            try { binding.chipLearnedCount.text = "0 фраз • 0 книг • OPDS flibusta.net" } catch (ex: Exception) {}
        }
    }

    private fun rebuildLearnedData() {
        AlertDialog.Builder(this).setTitle("🔧 Пересобрать базу?")
            .setMessage("Пересобрать героев, места, обороты из уже изученных 24 книг?\n\nЭто исправит 0 гер, 0 мест, 0 обор.")
            .setPositiveButton("Пересобрать") { _, _ ->
                binding.progressInternet.visibility = View.VISIBLE
                binding.tvInternetStatus.text = "Пересборка базы..."
                lifecycleScope.launch(Dispatchers.IO) {
                    val rebuilt = try { LearningManager.clearAndRebuildFromBooks(this@MainActivity) } catch (e: Exception) { 0 }
                    withContext(Dispatchers.Main) {
                        binding.progressInternet.visibility = View.GONE
                        updateLearnedCount()
                        Toast.makeText(this@MainActivity, "Пересобрано из $rebuilt книг", Toast.LENGTH_LONG).show()
                        showLearningStats()
                    }
                }
            }
            .setNegativeButton("Отмена", null).show()
    }

    // === АВТООБНОВЛЕНИЕ ===

    /**
     * Проверяет наличие новой версии на GitHub Releases.
     * 
     * @param silent если true — не показывает ошибки, работает фоном;
     *               если false — показывает Snackbar при ошибке/отсутствии обновления
     */
    private fun checkForUpdates(silent: Boolean = false) {
        lifecycleScope.launch {
            // Показываем индикатор загрузки
            if (!silent) {
                binding.tvInternetStatus.text = "🔄 Проверка обновлений..."
                binding.progressInternet.visibility = View.VISIBLE
            }

            val release = withContext(Dispatchers.IO) {
                try { UpdateManager.checkForUpdate(this@MainActivity) } catch (e: Exception) { null }
            }

            if (!silent) {
                binding.progressInternet.visibility = View.GONE
            }

            if (release == null) {
                if (!silent) {
                    Snackbar.make(binding.root, "Не удалось проверить обновления. Проверьте интернет.", Snackbar.LENGTH_LONG).show()
                }
                return@launch
            }

            if (release.isAvailable && release.apkUrl != null) {
                pendingUpdate = release
                showUpdateDialog(release)
            } else {
                if (!silent) {
                    val currentVersion = try {
                        packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
                    } catch (e: Exception) { "unknown" }
                    Snackbar.make(binding.root, "✅ У вас последняя версия ($currentVersion). Обновлений нет.", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Показывает диалог с предложением обновиться
     */
    private fun showUpdateDialog(release: UpdateManager.ReleaseInfo) {
        val currentVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) { "unknown" }

        val message = buildString {
            append("Доступна новая версия: ")
            append(release.versionName)
            append("\nТекущая версия: ")
            append(currentVersion)
            if (release.releaseNotes.isNotBlank()) {
                append("\n\n📝 Что нового:\n")
                append(release.releaseNotes.take(500))
            }
            append("\n\nСкачать и установить APK?")
        }

        AlertDialog.Builder(this)
            .setTitle("🔄 Доступно обновление!")
            .setMessage(message)
            .setPositiveButton("📥 Обновить") { _, _ ->
                startUpdate(release)
            }
            .setNeutralButton("📋 На GitHub") { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/zigorminsk-debug/Weriter/releases"))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Позже", null)
            .show()
    }

    /**
     * Запускает скачивание и установку обновления
     */
    private fun startUpdate(release: UpdateManager.ReleaseInfo) {
        val apkUrl = release.apkUrl ?: run {
            Toast.makeText(this, "URL APK не найден", Toast.LENGTH_SHORT).show()
            return
        }
        val fileName = release.apkFileName ?: "ZI-Writer-v${release.versionName}.apk"

        binding.tvInternetStatus.text = "📥 Загрузка ${release.versionName}..."
        binding.progressInternet.visibility = View.VISIBLE

        // Проверяем разрешение на установку из неизвестных источников
        if (!UpdateManager.canInstallFromUnknownSources(this)) {
            AlertDialog.Builder(this)
                .setTitle("Разрешение на установку")
                .setMessage("Для установки обновления нужно разрешить установку из неизвестных источников.\n\nПосле открытия настроек включите переключатель для ZI Writer.")
                .setPositiveButton("Открыть настройки") { _, _ ->
                    UpdateManager.requestInstallPermission(this)
                }
                .setNegativeButton("Отмена", null)
                .show()
            return
        }

        val success = UpdateManager.downloadAndInstall(this, apkUrl, fileName)
        if (!success) {
            binding.progressInternet.visibility = View.GONE
            Toast.makeText(this, "Не удалось начать загрузку", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Загрузка обновления начата. Следите за уведомлениями.", Toast.LENGTH_LONG).show()
        }
    }

    private fun pluralPages(n: Int): String = when {
        n % 10 == 1 && n % 100 != 11 -> "лист"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "листа"
        else -> "листов"
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "text/*" }
        filePickerLauncher.launch(intent)
    }
    private fun openFb2Picker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"; putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/x-fictionbook+xml", "text/xml", "*/*")) }
        fb2PickerLauncher.launch(intent)
    }
    private fun openEpubPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"; putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/epub+zip", "*/*")) }
        epubPickerLauncher.launch(intent)
    }
    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        if (!clip.isNullOrEmpty()) { binding.etInput.setText(clip); Toast.makeText(this, "Вставлено ${clip.length} знаков", Toast.LENGTH_SHORT).show() } else { Toast.makeText(this, "Буфер пуст", Toast.LENGTH_SHORT).show() }
    }

    private fun generateStory(isContinue: Boolean) {
        val inputRaw = if (isContinue && generatedStory != null) generatedStory!!.fullText else binding.etInput.text.toString().trim()
        if (inputRaw.length < 10) { Snackbar.make(binding.root, "Введите хотя бы 10 символов!", Snackbar.LENGTH_SHORT).show(); return }
        val pages = binding.sliderPages.value.toInt()
        val genreRaw = binding.actvGenre.text.toString().ifEmpty { "Авто (из текста)" }
        val style = binding.actvStyle.text.toString().ifEmpty { "Детальный" }
        val fixGrammar = binding.cbFixGrammar.isChecked
        val customHeroesText = binding.etHeroes.text.toString().trim()
        val customPlaceText = binding.etPlace.text.toString().trim()
        val bookTitle = binding.etBookTitle.text.toString().trim()
        val authorName = binding.etAuthor.text.toString().trim()
        val useLearned = binding.cbUseLearned.isChecked
        val customHeroes = if (customHeroesText.isNotEmpty()) customHeroesText.split(",").map { it.trim() }.filter { it.length >= 2 } else emptyList()
        val genre = if (genreRaw.startsWith("Авто")) detectGenre(inputRaw) else genreRaw
        binding.progress.visibility = View.VISIBLE
        binding.btnGenerate.isEnabled = false
        binding.btnContinue.isEnabled = false
        lifecycleScope.launch {
            // v31 MATH MODEL: learnedTemplates больше НЕ используются для вставки чужих отрывков! Только герои/места/обороты + сюжеты
            val learnedTemplates = withContext(Dispatchers.IO) { emptyList<String>() }
            val learnedHeroes = withContext(Dispatchers.IO) { if (useLearned) LearningManager.getLearnedHeroes(this@MainActivity, 150) else emptyList() }
            val learnedPlaces = withContext(Dispatchers.IO) { if (useLearned) LearningManager.getLearnedPlaces(this@MainActivity, 150) else emptyList() }
            val learnedPhrases = withContext(Dispatchers.IO) { if (useLearned) LearningManager.getLearnedPhrases(this@MainActivity, 150) else emptyList() }
            val learnedPlots = withContext(Dispatchers.IO) { if (useLearned) LearningManager.getLearnedPlotsByGenre(this@MainActivity, genre, 50) else emptyList() }
            val detailedPlots = withContext(Dispatchers.IO) { if (useLearned) LearningManager.getDetailedPlotsByGenre(this@MainActivity, genre, 50) else emptyList() }
            // Если по жанру мало данных (<3 книг), пробуем авто-доучить фоном (не блокируя генерацию)
            if (useLearned && detailedPlots.size < 3) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val added = LearningManager.autoLearnByGenre(this@MainActivity, genre, 2) { msg ->
                            // progress ignored in background
                        }
                        if (added > 0) {
                            withContext(Dispatchers.Main) {
                                try { updateLearnedCount() } catch (e: Exception) {}
                            }
                        }
                    } catch (e: Exception) {}
                }
            }
            delay(200)
            val story = withContext(Dispatchers.Default) {
                StoryGenerator.generate(StoryGenerator.GenerationConfig(inputRaw, pages, genre, style, fixGrammar && !isContinue, customHeroes, customPlaceText, bookTitle, authorName, learnedTemplates, useLearned, learnedHeroes, learnedPlaces, learnedPhrases, learnedPlots, detailedPlots, useMathModel = true))
            }
            binding.progress.visibility = View.GONE
            binding.btnGenerate.isEnabled = true
            binding.btnContinue.isEnabled = true
            generatedStory = if (isContinue && generatedStory != null) {
                val combined = generatedStory!!.fullText + "\n\n" + story.fullText.substringAfter("— — — Страница 1 — — —", "").let { if (it.isEmpty()) story.fullText else it }.take(pages * 1800)
                story.copy(fullText = combined, chars = combined.length, pages = (combined.length + 1799) / 1800)
            } else story
            binding.tvResultTitle.text = generatedStory!!.title
            binding.etResult.setText(generatedStory!!.fullText)
            binding.tvStats.text = "${generatedStory!!.pages} ${pluralPages(generatedStory!!.pages)}"
            val words = generatedStory!!.chars / 5
            val minutes = words / 180
            val mathInfo = if (detailedPlots.isNotEmpty()) " • 📐 мат.модель: ${detailedPlots.size} сюжетов жанра $genre • 🧠 ${learnedHeroes.size} гер, ${learnedPlaces.size} мест" else " • 🧠 ${learnedHeroes.size} гер, ${learnedPlaces.size} мест"
            binding.tvReadingStats.text = "~${generatedStory!!.chars} знаков • ~$words слов • ~$minutes мин • $genre$mathInfo"
            binding.cardResult.visibility = View.VISIBLE
            binding.layoutTtsControls.visibility = View.VISIBLE
            HistoryManager.saveToHistory(this@MainActivity, generatedStory!!)
            Toast.makeText(this@MainActivity, "Готово! ${generatedStory!!.pages} листов • $genre • ${words} слов$mathInfo", Toast.LENGTH_LONG).show()
        }
    }

    private fun extractKeywordsForRelevance(text: String): List<String> {
        return text.lowercase().replace(Regex("[^а-яёa-z ]"), " ").split(Regex("\\s+")).filter { it.length > 3 }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.take(12)
    }

    private fun detectGenre(text: String): String {
        val lower = text.lowercase()
        return when {
            listOf("выстрел", "мышечная", "цель", "оружие").any { lower.contains(it) } -> "Боевик"
            listOf("космос", "корабль", "галактика").any { lower.contains(it) } -> "Фантастика"
            listOf("убийство", "детектив", "следователь", "улика").any { lower.contains(it) } -> "Детектив"
            listOf("любовь", "сердце", "поцелуй").any { lower.contains(it) } -> "Роман"
            listOf("джунгли", "остров", "храм", "сокровище").any { lower.contains(it) } -> "Приключения"
            listOf("жизнь", "душа", "голос", "сознание").any { lower.contains(it) } -> "Философская проза"
            else -> "Драма"
        }
    }

    private fun searchInternetBooks() {
        val query = binding.etInternetSearch.text.toString().trim()
        if (query.length < 2) { Snackbar.make(binding.root, "Введите запрос", Snackbar.LENGTH_SHORT).show(); return }
        binding.progressInternet.visibility = View.VISIBLE
        binding.tvInternetStatus.text = "Поиск (Gutendex + OpenLib + Флибуста OPDS)..."
        binding.btnInternetSearch.isEnabled = false
        lifecycleScope.launch(Dispatchers.IO) {
            val results = try { InternetBookFetcher.searchBooks(query) } catch (e: Exception) { emptyList() }
            val opdsDetailed = try { InternetBookFetcher.searchFlibustaOpdsDetailed(query) } catch (e: Exception) { emptyList() }
            withContext(Dispatchers.Main) {
                binding.progressInternet.visibility = View.GONE
                binding.btnInternetSearch.isEnabled = true
                lastSearchResults = results
                lastOpdsResults = opdsDetailed
                if (results.isEmpty() && opdsDetailed.isEmpty()) {
                    binding.tvInternetStatus.text = "Ничего не найдено"
                    Snackbar.make(binding.root, "Книги не найдены. Проверьте VPN для Флибусты flibusta.net", Snackbar.LENGTH_LONG).show()
                } else {
                    binding.tvInternetStatus.text = "Найдено: ${results.size} общ + ${opdsDetailed.size} OPDS"
                    showSearchResultsMixed(results, opdsDetailed)
                }
            }
        }
    }

    private fun showSearchResultsMixed(results: List<InternetBookFetcher.BookInfo>, opdsBooks: List<OpdsFetcher.OpdsBook>) {
        val allItems = mutableListOf<String>()
        val combinedList = mutableListOf<Any>()
        for (b in opdsBooks.take(15)) {
            val formats = b.downloadLinks.keys.joinToString(",").uppercase()
            allItems.add("📚 [Флибуста OPDS] ${b.title} — ${b.authors} • $formats")
            combinedList.add(b)
        }
        for (b in results.take(20)) {
            allItems.add("${b.title} — ${b.authors} • ${b.source}")
            combinedList.add(b)
        }
        AlertDialog.Builder(this).setTitle("🌐 Найдено ${allItems.size} книг (OPDS flibusta.net)").setItems(allItems.toTypedArray()) { _, which ->
            val selected = combinedList[which]
            if (selected is OpdsFetcher.OpdsBook) showOpdsBookDetails(selected) else if (selected is InternetBookFetcher.BookInfo) showBookDetails(selected)
        }.setNegativeButton("Закрыть", null).show()
    }

    private fun showOpdsBookDetails(book: OpdsFetcher.OpdsBook) {
        val details = "📖 ${book.title}\n\n👤 ${book.authors}\n📚 Источник: ${book.source} (flibusta.net/opds)\n📄 Форматы: ${book.downloadLinks.keys.joinToString(", ")}\n\n${book.description.take(300)}\n\n✅ Доступен для самообучения! FB2, EPUB, TXT"
        AlertDialog.Builder(this).setTitle("Флибуста OPDS").setMessage(details)
            .setPositiveButton("📥 Загрузить и обучить") { _, _ -> downloadAndLearnOpds(book) }
            .setNeutralButton("📖 Только загрузить") { _, _ -> downloadOnlyOpds(book) }
            .setNegativeButton("Закрыть", null).show()
    }

    private fun showBookDetails(book: InternetBookFetcher.BookInfo) {
        val isFlibusta = book.source.contains("Flibusta") || book.downloadUrl?.contains("flibusta.net") == true
        val details = "📖 ${book.title}\n\n👤 ${book.authors}\n📚 Источник: ${book.source}\n\nID: ${book.id}\n${if (book.downloadUrl != null) "✅ Доступен текст" else "⚠️ Только метаданные"}${if (isFlibusta) "\n🔥 Флибуста OPDS flibusta.net" else ""}"
        val builder = AlertDialog.Builder(this).setTitle(if (isFlibusta) "📚 Флибуста OPDS" else "Книга").setMessage(details).setNegativeButton("Закрыть", null)
        if (book.downloadUrl != null) {
            builder.setPositiveButton("📥 Загрузить и обучить") { _, _ ->
                if (isFlibusta) {
                    val opds = lastOpdsResults.find { it.title == book.title } ?: OpdsFetcher.OpdsBook(book.id, book.title, book.authors, "", mapOf("fb2" to (book.downloadUrl ?: "")), source = "Flibusta OPDS")
                    downloadAndLearnOpds(opds)
                } else downloadAndLearn(book)
            }
            builder.setNeutralButton("📖 Только загрузить") { _, _ -> downloadOnly(book) }
        }
        builder.show()
    }

    private fun downloadAndLearnOpds(book: OpdsFetcher.OpdsBook) {
        binding.progressInternet.visibility = View.VISIBLE
        binding.tvInternetStatus.text = "Загрузка OPDS ${book.title.take(20)}..."
        lifecycleScope.launch(Dispatchers.IO) {
            val text = try { OpdsFetcher.fetchBookText(book) } catch (e: Exception) { null }
            if (text == null || text.length < 200) {
                withContext(Dispatchers.Main) { binding.progressInternet.visibility = View.GONE; binding.tvInternetStatus.text = "Ошибка OPDS"; Toast.makeText(this@MainActivity, "Не удалось загрузить с Флибусты. Попробуйте VPN", Toast.LENGTH_LONG).show() }
                return@launch
            }
            val learned = try { LearningManager.learnFromOpdsBook(this@MainActivity, book, text) } catch (e: Exception) { null }
            withContext(Dispatchers.Main) {
                binding.progressInternet.visibility = View.GONE
                if (learned != null) {
                    binding.tvInternetStatus.text = "Изучено OPDS: ${book.title.take(15)} (${text.length} зн)"
                    updateLearnedCount()
                    Snackbar.make(binding.root, "✅ OPDS Изучено: ${book.title} • ${text.length} знаков", Snackbar.LENGTH_LONG).show()
                    AlertDialog.Builder(this@MainActivity).setTitle("Книга изучена! Флибуста").setMessage("Книга ${book.title} изучена. ${text.length} знаков.\nИспользовать отрывок?").setPositiveButton("Да") { _, _ ->
                        binding.etInput.setText(text.take(5000)); binding.etBookTitle.setText(book.title); binding.etAuthor.setText(book.authors)
                    }.setNegativeButton("Нет", null).show()
                } else { binding.tvInternetStatus.text = "Ошибка обучения" }
            }
        }
    }

    private fun downloadOnlyOpds(book: OpdsFetcher.OpdsBook) {
        binding.progressInternet.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val text = try { OpdsFetcher.fetchBookText(book) } catch (e: Exception) { null }
            withContext(Dispatchers.Main) {
                binding.progressInternet.visibility = View.GONE
                if (text != null) { binding.etInput.setText(text.take(10000)); binding.etBookTitle.setText(book.title); binding.etAuthor.setText(book.authors); Toast.makeText(this@MainActivity, "Загружено ${text.length} знаков", Toast.LENGTH_LONG).show() } else { Toast.makeText(this@MainActivity, "Ошибка OPDS", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun downloadAndLearn(book: InternetBookFetcher.BookInfo) {
        val url = book.downloadUrl ?: return
        binding.progressInternet.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val text = try { InternetBookFetcher.fetchBookText(url) } catch (e: Exception) { null }
            if (text == null || text.length < 100) { withContext(Dispatchers.Main) { binding.progressInternet.visibility = View.GONE; Toast.makeText(this@MainActivity, "Ошибка загрузки", Toast.LENGTH_LONG).show() }; return@launch }
            val genre = detectGenre(text)
            val learned = try { LearningManager.learnFromText(this@MainActivity, book.title, book.authors, text, genre) } catch (e: Exception) { null }
            withContext(Dispatchers.Main) {
                binding.progressInternet.visibility = View.GONE
                if (learned != null) { updateLearnedCount(); Snackbar.make(binding.root, "✅ Изучено: ${book.title}", Snackbar.LENGTH_LONG).show() }
            }
        }
    }

    private fun downloadOnly(book: InternetBookFetcher.BookInfo) {
        val url = book.downloadUrl ?: return
        binding.progressInternet.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val text = try { InternetBookFetcher.fetchBookText(url) } catch (e: Exception) { null }
            withContext(Dispatchers.Main) { binding.progressInternet.visibility = View.GONE; if (text != null) { binding.etInput.setText(text.take(10000)); binding.etBookTitle.setText(book.title) } }
        }
    }

    private fun fetchRandomPlot() {
        binding.progressInternet.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val plot = try { InternetBookFetcher.fetchRandomPlot() } catch (e: Exception) { null }
            withContext(Dispatchers.Main) {
                binding.progressInternet.visibility = View.GONE
                if (plot != null) {
                    AlertDialog.Builder(this@MainActivity).setTitle("🎲 ${plot.title}").setMessage("${plot.plot}\n\nИсточник: ${plot.source}").setPositiveButton("Использовать") { _, _ -> binding.etInput.setText(plot.plot) }.setNegativeButton("Закрыть", null).show()
                }
            }
        }
    }

    private fun autoLearnFlibusta() {
        AlertDialog.Builder(this).setTitle("🤖 Авто-обучение Флибуста OPDS").setMessage("Изучить 3 популярных книг с Флибусты OPDS flibusta.net?\nТребуется интернет, возможно VPN.").setPositiveButton("3 книги") { _, _ -> startAutoLearnFlibusta(3) }.setNeutralButton("5 книг") { _, _ -> startAutoLearnFlibusta(5) }.setNegativeButton("Отмена", null).show()
    }

    private fun startAutoLearnFlibusta(maxBooks: Int) {
        binding.progressInternet.visibility = View.VISIBLE
        binding.tvInternetStatus.text = "Авто-обучение Флибуста: 0/$maxBooks"
        try { binding.btnAutoLearn.isEnabled = false } catch (e: Exception) {}
        lifecycleScope.launch(Dispatchers.IO) {
            var count = 0
            val queries = LearningManager.getFlibustaPopularQueries().shuffled().take(maxBooks)
            for ((idx, q) in queries.withIndex()) {
                withContext(Dispatchers.Main) { binding.tvInternetStatus.text = "Флибуста: $q (${idx+1}/$maxBooks)" }
                try {
                    val opdsBooks = OpdsFetcher.searchFlibusta(q, OpdsFetcher.PRIMARY_OPDS, 0)
                    val book = opdsBooks.firstOrNull { it.downloadLinks.isNotEmpty() } ?: continue
                    withContext(Dispatchers.Main) { binding.tvInternetStatus.text = "Загрузка: ${book.title.take(25)}" }
                    val text = OpdsFetcher.fetchBookText(book) ?: continue
                    if (text.length < 500) continue
                    val learned = LearningManager.learnFromOpdsBook(this@MainActivity, book, text)
                    if (learned != null) count++
                    delay(2000)
                } catch (e: Exception) { continue }
            }
            withContext(Dispatchers.Main) {
                binding.progressInternet.visibility = View.GONE
                try { binding.btnAutoLearn.isEnabled = true } catch (e: Exception) {}
                binding.tvInternetStatus.text = "Авто-обучено: $count/$maxBooks (OPDS)"
                updateLearnedCount()
                AlertDialog.Builder(this@MainActivity).setTitle("Авто-обучение завершено").setMessage("Изучено $count книг с Флибусты flibusta.net").setPositiveButton("OK", null).show()
            }
        }
    }

    private fun learnFromCurrentText() {
        val text = binding.etInput.text.toString().trim()
        if (text.length < 100) { Snackbar.make(binding.root, "Введите 100 символов", Snackbar.LENGTH_SHORT).show(); return }
        val genre = detectGenre(text)
        lifecycleScope.launch(Dispatchers.IO) {
            val learned = try { LearningManager.learnFromCurrentText(this@MainActivity, text, genre) } catch (e: Exception) { null }
            withContext(Dispatchers.Main) { if (learned != null) { updateLearnedCount(); Snackbar.make(binding.root, "✅ Обучено", Snackbar.LENGTH_LONG).show() } }
        }
    }

    private fun showLearnedBooks() {
        lifecycleScope.launch(Dispatchers.IO) {
            val books = LearningManager.getLearnedBooks(this@MainActivity)
            withContext(Dispatchers.Main) {
                if (books.isEmpty()) { Snackbar.make(binding.root, "Нет изученных книг. Ищите через Флибуста OPDS!", Snackbar.LENGTH_LONG).show(); return@withContext }
                val items = books.map { "${it.title.take(30)} • ${it.chars} зн • ${it.genre}" }.toTypedArray()
                AlertDialog.Builder(this@MainActivity).setTitle("📚 Изученные (${books.size}) OPDS").setItems(items) { _, which ->
                    val selected = books[which]
                    AlertDialog.Builder(this@MainActivity).setTitle(selected.title).setMessage("Автор: ${selected.authors}\nЖанр: ${selected.genre}\nЗнаков: ${selected.chars}").setPositiveButton("Загрузить") { _, _ ->
                        try { val content = File(selected.path).readText().take(10000); binding.etInput.setText(content) } catch (e: Exception) {}
                    }.setNegativeButton("Закрыть", null).show()
                }.setNegativeButton("Закрыть", null).show()
            }
        }
    }

    private fun showLearningStats() {
        lifecycleScope.launch(Dispatchers.IO) {
            val stats = LearningManager.getStats(this@MainActivity)
            val templatesCount = LearningManager.getAllLearnedTemplatesCount(this@MainActivity)
            val heroesCount = try { LearningManager.getLearnedHeroes(this@MainActivity, 1000).size } catch (e: Exception) { 0 }
            val placesCount = try { LearningManager.getLearnedPlaces(this@MainActivity, 1000).size } catch (e: Exception) { 0 }
            val phrasesCount = try { LearningManager.getLearnedPhrases(this@MainActivity, 1000).size } catch (e: Exception) { 0 }
            val plotsCount = try { LearningManager.getLearnedPlots(this@MainActivity, 1000).size } catch (e: Exception) { 0 }
            val detailedPlots = try { LearningManager.getDetailedPlots(this@MainActivity, 200).size } catch (e: Exception) { 0 }
            val genreStats = try { LearningManager.getGenreStats(this@MainActivity) } catch (e: Exception) { emptyMap() }
            val totalData = try { LearningManager.getAllLearnedDataCount(this@MainActivity) } catch (e: Exception) { templatesCount }
            withContext(Dispatchers.Main) {
                val totalBooks = stats.optInt("totalBooks", 0)
                val totalChars = stats.optInt("totalChars", 0)
                val genreBreakdown = if (genreStats.isNotEmpty()) genreStats.entries.joinToString("\n") { "• ${it.key}: ${it.value} книг" } else "• Нет данных по жанрам"
                val message = "📊 Статистика v31 MATH MODEL\n\n📚 Книг: $totalBooks\n📝 Знаков: $totalChars\n🧠 Всего данных: $totalData\n• Шаблонов: $templatesCount\n• Героев: $heroesCount\n• Мест: $placesCount\n• Оборотов: $phrasesCount\n• Сюжетов: $plotsCount (простых)\n• Детальных сюжетов: $detailedPlots (с жанром)\n\n📚 По жанрам:\n$genreBreakdown\n\n✅ v31 MATH MODEL:\n• Мат.модель: B={книги}, H,P,F,K пулы\n• Coherence = α·Lex + β·Entity + γ·Temp\n• P(t|Q)=P_syntax*P_hero*P_place*P_plot*P_coherence\n• При выборе жанра — авто-изучение с Флибусты этого жанра\n• При генерации — выбор наиболее подходящего сюжета по смыслу вступления (Jaccard + семантика)\n• Построение графа сюжета: завязка→развитие→кульминация→развязка с ключевыми словами из вступления + лучшего сюжета\n• Диалоги как цепь Маркова интенций\n\nИсточники:\n• 🔥 flibusta.net/opds - Флибуста (работает в РБ) + http://flibusta.net/opds\n• Gutendex 70k+\n• OpenLibrary 40M+"
                AlertDialog.Builder(this@MainActivity).setTitle("Статистика v31 MATH MODEL").setMessage(message)
                    .setPositiveButton("OK", null)
                    .setNeutralButton("🔧 Пересобрать") { _, _ -> rebuildLearnedData() }
                    .setNegativeButton("Авто Флибуста") { _, _ -> autoLearnFlibusta() }
                    .show()
            }
        }
    }

    private fun clearLearned() {
        AlertDialog.Builder(this).setTitle("Очистить?").setMessage("Удалить все изученные?").setPositiveButton("Удалить") { _, _ ->
            lifecycleScope.launch(Dispatchers.IO) { LearningManager.clearLearned(this@MainActivity); withContext(Dispatchers.Main) { updateLearnedCount() } }
        }.setNegativeButton("Отмена", null).show()
    }

    private fun exportLearned() {
        try {
            val fileName = "ZIWriter_Learned_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.zip"
            val file = File(getExternalFilesDir(null), fileName)
            val ok = LearningManager.exportLearnedToZip(this, file)
            if (ok) Toast.makeText(this, "Экспорт: ${file.absolutePath}", Toast.LENGTH_LONG).show() else Toast.makeText(this, "Нет данных", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private fun importLearned() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/zip" }
        zipPickerLauncher.launch(intent)
    }

    private fun saveAsTxt() {
        val story = generatedStory ?: return
        try {
            val fileName = "ZIWriter_${story.title.replace(Regex("[^a-zA-Zа-яА-Я0-9]"), "_").take(15)}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.txt"
            val file = File(getExternalFilesDir(null) ?: filesDir, fileName)
            FileOutputStream(file).use { it.write(story.fullText.toByteArray()) }
            val saved = DownloadSaver.save(this, file, "text/plain")
            if (saved != null) Toast.makeText(this, "TXT: Download/${saved.name}", Toast.LENGTH_LONG).show()
            else Toast.makeText(this, "Сохранено: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) { Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private fun saveAsPdf() {
        val story = generatedStory ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val file = PdfExporter.exportAsPdf(this@MainActivity, story.title, story.fullText)
            withContext(Dispatchers.Main) { if (file != null) Toast.makeText(this@MainActivity, "PDF: ${file.absolutePath}", Toast.LENGTH_LONG).show() }
        }
    }
    private fun saveAsDocx() {
        val story = generatedStory ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val file = DocxExporter.exportAsDocx(this@MainActivity, story.title, story.fullText)
            withContext(Dispatchers.Main) { if (file != null) Toast.makeText(this@MainActivity, "DOCX: ${file.absolutePath}", Toast.LENGTH_LONG).show() }
        }
    }
    private fun saveAsEpub() {
        val story = generatedStory ?: return
        val author = binding.etAuthor.text.toString().ifEmpty { "ZI Writer" }
        lifecycleScope.launch(Dispatchers.IO) {
            val file = EpubExporter.exportAsEpub(this@MainActivity, story.title, story.fullText, author)
            withContext(Dispatchers.Main) { if (file != null) Toast.makeText(this@MainActivity, "EPUB: ${file.absolutePath}", Toast.LENGTH_LONG).show() }
        }
    }
    private fun generateCover() {
        val story = generatedStory ?: return
        val author = binding.etAuthor.text.toString().ifEmpty { "ZI Writer" }
        val genre = detectGenre(story.fullText)
        val style = selectedCoverStyle ?: CoverGenerator.getRandomStyleForGenre(genre)
        lifecycleScope.launch(Dispatchers.IO) {
            val file = CoverGenerator.generateCover(this@MainActivity, story.title, author, style)
            withContext(Dispatchers.Main) { if (file != null) Toast.makeText(this@MainActivity, "Обложка: ${file.absolutePath}", Toast.LENGTH_LONG).show() }
        }
    }
    private fun generateIllustrations() {
        val story = generatedStory ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val files = ChapterIllustrator.generateIllustrationsForBookLegacy(this@MainActivity, story.title, story.fullText)
            withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "Иллюстраций: ${files.size}", Toast.LENGTH_LONG).show() }
        }
    }
    private fun exportAll() {
        val story = generatedStory ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val author = binding.etAuthor.text.toString().ifEmpty { "ZI Writer" }
            val genre = detectGenre(story.fullText)
            val style = selectedCoverStyle ?: CoverGenerator.getRandomStyleForGenre(genre)
            val txt = try { val fn = "ZIWriter_${story.title.take(15)}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.txt"; val f = File(getExternalFilesDir(null), fn); FileOutputStream(f).use { it.write(story.fullText.toByteArray()) }; f } catch (e: Exception) { null }
            val pdf = PdfExporter.exportAsPdf(this@MainActivity, story.title, story.fullText)
            val docx = DocxExporter.exportAsDocx(this@MainActivity, story.title, story.fullText)
            val epub = EpubExporter.exportAsEpub(this@MainActivity, story.title, story.fullText, author)
            val cover = CoverGenerator.generateCover(this@MainActivity, story.title, author, style)
            withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "Экспорт ВСЁ: ${listOf(txt,pdf,docx,epub,cover).count { it != null }}/5", Toast.LENGTH_LONG).show() }
        }
    }
    private fun toggleTts() {
        val story = generatedStory ?: return
        if (!ttsReady) { Toast.makeText(this, "TTS не готов", Toast.LENGTH_SHORT).show(); return }
        if (tts?.isSpeaking == true) { tts?.stop(); binding.btnTts.text = "Озвучить" } else {
            tts?.speak(story.fullText.take(4000), TextToSpeech.QUEUE_FLUSH, null, "ziwriter")
            binding.btnTts.text = "⏹ Стоп"
        }
    }
    private fun shareStory() {
        val story = generatedStory ?: return
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, story.title); putExtra(Intent.EXTRA_TEXT, story.fullText) }
        startActivity(Intent.createChooser(intent, "Поделиться"))
    }
    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("ZI Writer", text))
        Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show()
    }
    private fun showHistory() {
        val history = HistoryManager.getHistory(this)
        if (history.isEmpty()) { Snackbar.make(binding.root, "Библиотека пуста", Snackbar.LENGTH_SHORT).show(); return }
        val items = history.map { "${it.title} • ${it.chars} зн" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("📚 Библиотека (${history.size})").setItems(items) { _, which ->
            val selected = history[which]
            HistoryManager.loadHistoryItem(selected.path)?.let { content ->
                binding.etResult.setText(content); binding.tvResultTitle.text = selected.title; binding.cardResult.visibility = View.VISIBLE
                generatedStory = StoryGenerator.GeneratedStory(selected.title, content, content.length, (content.length + 1799) / 1800, emptyList(), emptyList(), "")
            }
        }.setNegativeButton("Закрыть", null).show()
    }

    // v30: Full editor for entire generated text
    private fun openFullEditor() {
        val story = generatedStory
        if (story == null && binding.etResult.text.toString().isBlank()) {
            Toast.makeText(this, "Нет сгенерированного текста", Toast.LENGTH_SHORT).show()
            return
        }
        val currentText = binding.etResult.text.toString().ifBlank { story?.fullText ?: "" }
        val currentTitle = binding.tvResultTitle.text.toString().ifBlank { story?.title ?: "Без названия" }

        val dialogView = layoutInflater.inflate(R.layout.dialog_full_editor, null)
        val etTitle = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etFullTitle)
        val etContent = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etFullContent)
        val tvStats = dialogView.findViewById<android.widget.TextView>(R.id.tvFullStats)
        val btnMinus = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFullFontMinus)
        val btnPlus = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFullFontPlus)

        etTitle.setText(currentTitle)
        etContent.setText(currentText)
        etContent.textSize = currentFontSize

        fun updateStats() {
            val txt = etContent.text.toString()
            val chars = txt.length
            val words = if (txt.isBlank()) 0 else txt.trim().split(Regex("\\s+")).size
            val pages = (chars + 1799) / 1800
            tvStats.text = "$chars знаков • $words слов • $pages ${pluralPages(pages)}"
        }
        updateStats()
        etContent.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { updateStats() }
        })

        btnMinus.setOnClickListener {
            if (currentFontSize > 10) {
                currentFontSize -= 1
                etContent.textSize = currentFontSize
            }
        }
        btnPlus.setOnClickListener {
            if (currentFontSize < 24) {
                currentFontSize += 1
                etContent.textSize = currentFontSize
            }
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("✏️ Редактор — весь текст (правка)")
            .setView(dialogView)
            .setPositiveButton("💾 Сохранить") { _, _ ->
                val newTitle = etTitle.text.toString().trim().ifBlank { currentTitle }
                val newContent = etContent.text.toString()
                if (newContent.isNotBlank()) {
                    binding.tvResultTitle.text = newTitle
                    binding.etResult.setText(newContent)
                    generatedStory = generatedStory?.copy(title = newTitle, fullText = newContent, chars = newContent.length, pages = (newContent.length + 1799) / 1800)
                        ?: StoryGenerator.GeneratedStory(newTitle, newContent, newContent.length, (newContent.length + 1799) / 1800, emptyList(), emptyList(), "")
                    binding.tvStats.text = "${generatedStory!!.pages} ${pluralPages(generatedStory!!.pages)}"
                    binding.tvReadingStats.text = "~${newContent.length} знаков • ~${newContent.length/5} слов"
                    generatedStory?.let { HistoryManager.saveToHistory(this, it) }
                    Toast.makeText(this, "Сохранено: ${newContent.length} знаков", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .setNeutralButton("📋 Копировать") { _, _ ->
                copyToClipboard(etContent.text.toString())
            }
            .create()

        dialog.show()
        // Make dialog full-screen
        dialog.window?.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun copyResultToInput() {
        val resultText = binding.etResult.text.toString()
        if (resultText.isBlank()) {
            Toast.makeText(this, "Нет текста для копирования", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("⬆️ В исходник?")
            .setMessage("Скопировать весь сгенерированный текст (${resultText.length} знаков) в поле ввода для дальнейшей правки?\n\nТекущий ввод будет заменен.")
            .setPositiveButton("Копировать") { _, _ ->
                binding.etInput.setText(resultText)
                binding.etInput.setSelection(resultText.length)
                Toast.makeText(this, "Скопировано в исходник: ${resultText.length} знаков", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Отмена", null)
            .setNeutralButton("Добавить") { _, _ ->
                val current = binding.etInput.text.toString()
                val combined = if (current.isBlank()) resultText else current + "\n\n" + resultText
                binding.etInput.setText(combined)
                Toast.makeText(this, "Добавлено: ${combined.length} знаков", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}
