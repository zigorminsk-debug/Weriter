import pathlib
p = pathlib.Path('/home/user/zi-writer/app/src/main/java/com/zakharevich/ziwriter/MainActivity.kt')
txt = p.read_text(encoding='utf-8')

old_listeners = '''        binding.btnSave.setOnClickListener { saveAsTxt() }
        binding.btnSavePdf.setOnClickListener { saveAsPdf() }
        binding.btnSaveDocx.setOnClickListener { saveAsDocx() }
        binding.btnSaveEpub.setOnClickListener { saveAsEpub() }
        binding.btnCover.setOnClickListener { generateCover() }
        try { binding.btnIllustrations.setOnClickListener { generateIllustrations() } } catch (e: Exception) {}
        binding.btnTts.setOnClickListener { toggleTts() }
        binding.btnShare.setOnClickListener { shareStory() }
        binding.btnCopy.setOnClickListener { copyToClipboard(binding.etResult.text.toString()) }
        binding.btnExportAll.setOnClickListener { exportAll() }'''

new_listeners = '''        binding.btnSave.setOnClickListener { saveAsTxt() }
        binding.btnSavePdf.setOnClickListener { saveAsPdf() }
        binding.btnSaveDocx.setOnClickListener { saveAsDocx() }
        binding.btnSaveEpub.setOnClickListener { saveAsEpub() }
        binding.btnCover.setOnClickListener { generateCover() }
        try { binding.btnIllustrations.setOnClickListener { generateIllustrations() } } catch (e: Exception) {}
        binding.btnTts.setOnClickListener { toggleTts() }
        binding.btnShare.setOnClickListener { shareStory() }
        binding.btnCopy.setOnClickListener { copyToClipboard(binding.etResult.text.toString()) }
        binding.btnExportAll.setOnClickListener { exportAll() }
        try { binding.btnEditFull.setOnClickListener { showFullEditor() } } catch (e: Exception) {}
        try { binding.btnToInput.setOnClickListener {
            val t = binding.etResult.text.toString()
            if (t.isNotBlank()) {
                binding.etInput.setText(t)
                Toast.makeText(this, "Текст скопирован в исходник для правки", Toast.LENGTH_SHORT).show()
                binding.etInput.requestFocus()
            }
        } } catch (e: Exception) {}
        try { binding.etResult.setOnLongClickListener { showFullEditor(); true } } catch (e: Exception) {}'''

txt = txt.replace(old_listeners, new_listeners)

old_generate = '''            binding.tvResultTitle.text = generatedStory!!.title
            binding.etResult.setText(generatedStory!!.fullText)
            binding.tvStats.text = "${generatedStory!!.pages} ${pluralPages(generatedStory!!.pages)}"
            val words = generatedStory!!.chars / 5
            val minutes = words / 180
            val learnedInfo = if (learnedTemplates.isNotEmpty()) " • 🧠 ${learnedTemplates.size} изученных (OPDS)" else ""
            binding.tvReadingStats.text = "~${generatedStory!!.chars} знаков • ~$words слов • ~$minutes мин • $genre$learnedInfo"
            binding.cardResult.visibility = View.VISIBLE
            binding.layoutTtsControls.visibility = View.VISIBLE
            HistoryManager.saveToHistory(this@MainActivity, generatedStory!!)
            Toast.makeText(this@MainActivity, "Готово! ${generatedStory!!.pages} листов • $genre • ${words} слов$learnedInfo", Toast.LENGTH_LONG).show()'''

new_generate = '''            binding.tvResultTitle.text = generatedStory!!.title
            binding.etResult.setText(generatedStory!!.fullText)
            binding.etResult.isFocusable = true
            binding.etResult.isFocusableInTouchMode = true
            binding.etResult.requestFocus()
            binding.tvStats.text = "${generatedStory!!.pages} ${pluralPages(generatedStory!!.pages)}"
            val words = generatedStory!!.chars / 5
            val minutes = words / 180
            val learnedInfo = if (learnedTemplates.isNotEmpty()) " • 🧠 ${learnedTemplates.size} изученных (OPDS)" else ""
            val heroesInfo = if (generatedStory!!.heroes.isNotEmpty()) " • 👤 " + generatedStory!!.heroes.joinToString(", ") else ""
            val placesInfo = if (generatedStory!!.places.isNotEmpty()) " • 🌍 " + generatedStory!!.places.take(3).joinToString(", ") else ""
            binding.tvReadingStats.text = "~${generatedStory!!.chars} знаков • ~$words слов • ~$minutes мин • $genre$learnedInfo$heroesInfo$placesInfo"
            binding.cardResult.visibility = View.VISIBLE
            binding.layoutTtsControls.visibility = View.VISIBLE
            try { binding.cardEntities.visibility = View.VISIBLE } catch (e: Exception) {}
            HistoryManager.saveToHistory(this@MainActivity, generatedStory!!)
            val entityMsg = buildString {
                if (generatedStory!!.heroes.isNotEmpty()) append("Живые: " + generatedStory!!.heroes.joinToString(", ") + "\\n")
                if (generatedStory!!.places.isNotEmpty()) append("Неживые/Места: " + generatedStory!!.places.joinToString(", "))
            }
            try {
                binding.tvEntities.text = if (entityMsg.isBlank()) "Не найдено" else entityMsg + "\\n\\nПример: 'планета Тоби' = планета (не человек), 'Озин пошёл' = человек (живой)."
            } catch (e: Exception) {}
            if (entityMsg.isNotBlank()) {
                Snackbar.make(binding.root, "🔍 " + entityMsg.replace("\\n", " | "), Snackbar.LENGTH_LONG).setAction("Редактор") { showFullEditor() }.show()
            } else {
                Snackbar.make(binding.root, "Готово! Откройте редактор для правки", Snackbar.LENGTH_LONG).setAction("✏️ Редактор") { showFullEditor() }.show()
            }
            Toast.makeText(this@MainActivity, "Готово! ${generatedStory!!.pages} листов • $genre • ${words} слов$learnedInfo\\n$entityMsg", Toast.LENGTH_LONG).show()'''

txt = txt.replace(old_generate, new_generate)

old_history = '''    private fun showHistory() {
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
}'''

new_history = '''    private fun showFullEditor() {
        val story = generatedStory ?: return
        val editText = android.widget.EditText(this).apply {
            setText(story.fullText)
            textSize = currentFontSize
            setPadding(24, 24, 24, 24)
            isSingleLine = false
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            minLines = 20
        }
        val scrollView = android.widget.ScrollView(this).apply {
            addView(editText)
        }
        AlertDialog.Builder(this)
            .setTitle("✏️ Редактор — правка готового текста")
            .setMessage("Живые: ${story.heroes.joinToString(", ")} | Места: ${story.places.joinToString(", ")}\\nОтредактируйте текст и сохраните.")
            .setView(scrollView)
            .setPositiveButton("💾 Сохранить") { _, _ ->
                val newText = editText.text.toString()
                if (newText.isNotBlank()) {
                    binding.etResult.setText(newText)
                    generatedStory = story.copy(fullText = newText, chars = newText.length, pages = (newText.length + 1799) / 1800)
                    binding.tvStats.text = "${generatedStory!!.pages} ${pluralPages(generatedStory!!.pages)}"
                    binding.tvReadingStats.text = "~${newText.length} знаков • отредактировано • 👤 ${story.heroes.joinToString(", ")} • 🌍 ${story.places.take(2).joinToString(", ")}"
                    HistoryManager.saveToHistory(this, generatedStory!!)
                    Toast.makeText(this, "Текст сохранён в редакторе! ${newText.length} знаков", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("📋 В буфер") { _, _ ->
                copyToClipboard(editText.text.toString())
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showHistory() {
        val history = HistoryManager.getHistory(this)
        if (history.isEmpty()) { Snackbar.make(binding.root, "Библиотека пуста", Snackbar.LENGTH_SHORT).show(); return }
        val items = history.map { "${it.title} • ${it.chars} зн" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("📚 Библиотека (${history.size})").setItems(items) { _, which ->
            val selected = history[which]
            HistoryManager.loadHistoryItem(selected.path)?.let { content ->
                binding.etResult.setText(content); binding.tvResultTitle.text = selected.title; binding.cardResult.visibility = View.VISIBLE
                generatedStory = StoryGenerator.GeneratedStory(selected.title, content, content.length, (content.length + 1799) / 1800, emptyList(), emptyList(), "", emptyList())
                Snackbar.make(binding.root, "Загружено из библиотеки — откройте редактор", Snackbar.LENGTH_LONG).setAction("Редактор") { showFullEditor() }.show()
            }
        }.setNegativeButton("Закрыть", null).show()
    }
}'''

txt = txt.replace(old_history, new_history)

p.write_text(txt, encoding='utf-8')
print("main v16 fixed")
