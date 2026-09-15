import pathlib
path = pathlib.Path('/home/user/zi-writer/app/src/main/java/com/zakharevich/ziwriter/StoryGenerator.kt')
txt = path.read_text(encoding='utf-8')

# Add placeToNominative function and fix title generator
old_title_func = """    // === V18 TITLE GENERATOR - NATURAL ===
    private fun generateTitleV18(original: String, keywords: List<String>, genre: String, places: List<String>, heroes: List<String>, rnd: Random): String {
        val lowerOrig = original.lowercase()

        // 1. Попытка вытащить готовые красивые фразы из оригинала
        val directCandidates = mutableListOf<String>()
        if (lowerOrig.contains("город из стекла")) directCandidates.add("Город из стекла")
        if (lowerOrig.contains("планете тоби") || lowerOrig.contains("планета тоби")) directCandidates.add("Планета Тоби")
        if (lowerOrig.contains("корабль аврора") || lowerOrig.contains("корабле аврора")) directCandidates.add("Корабль Аврора")
        if (lowerOrig.contains("станция орион")) directCandidates.add("Станция Орион-9")
        if (lowerOrig.contains("квантовый разлом")) directCandidates.add("Квантовый разлом")
        if (lowerOrig.contains("заброшенном космопорте")) directCandidates.add("Заброшенный космопорт")
        if (lowerOrig.contains("сигнал из будущего")) directCandidates.add("Сигнал из будущего")
        if (lowerOrig.contains("сигнал — не извне") || lowerOrig.contains("сигнал - не извне")) directCandidates.add("Сигнал из будущего")
        if (lowerOrig.contains("новая галактика")) directCandidates.add("Новая галактика")

        // Если есть прямое вхождение места - используем его
        for (pl in places) {
            val clean = pl.replace(Regex("^(на|в|у|за)\\s+"), "").trim()
            if (clean.length in 4..30 && rnd.nextFloat() < 0.5f) {
                directCandidates.add(clean.replaceFirstChar { it.uppercase() })
            }
        }

        if (directCandidates.isNotEmpty() && rnd.nextFloat() < 0.7f) {
            return directCandidates.random(rnd)
        }

        // 2. Шаблоны по жанрам - естественные, не склейка двух существительных
        val hero = heroes.firstOrNull() ?: "Озин"
        val placeShort = places.firstOrNull()?.replace(Regex("^(на|в|у)\\s+"), "") ?: "Тоби"
        val placeCap = placeShort.replaceFirstChar { it.uppercase() }

        val titlesByGenre = when (genre) {
            "Фантастика" -> listOf(
                "Сигнал с $placeCap",
                "Последний день на $placeCap",
                "Хроники $placeCap",
                "Тени $placeCap",
                "Эхо $placeCap",
                "Свет $placeCap",
                "Последний сигнал $hero",
                "Дневник $hero",
                "Корабль $hero",
                "Вахта $hero",
                "37 часов на $placeCap",
                "Сигнал из будущего",
                "Город из стекла",
                "Квантовый разлом",
                "Станция Орион-9",
                "Заброшенный космопорт",
                "Новая галактика",
                "Письма с $placeCap",
                "Орбита $placeCap"
            )
            "Детектив" -> listOf(
                "Дело о $placeCap",
                "Тени $placeCap",
                "Последний свидетель с $placeCap",
                "Архив $placeCap",
                "Ночь на $placeCap",
                "Сигнал в $placeCap",
                "Протокол $placeCap"
            )
            "Боевик" -> listOf(
                "Точка невозврата: $placeCap",
                "Последний рубеж $placeCap",
                "Операция $placeCap",
                "Код $placeCap"
            )
            "Роман" -> listOf(
                "Письма с $placeCap",
                "Вечер на $placeCap",
                "Свет $placeCap",
                "Дорога к $placeCap"
            )
            else -> listOf(
                "История $placeCap",
                "Дневник $hero",
                "Письма с $placeCap",
                "Эхо $placeCap",
                "Свет $placeCap"
            )
        }

        // 3. Если есть ключевые слова, делаем красивые комбинации с предлогами
        val prettyTemplates = listOf(
            "Последний ${keywords.getOrElse(0) { "сигнал" }}",
            "${keywords.getOrElse(0) { "Сигнал" }.replaceFirstChar { it.uppercase() }} из будущего",
            "${keywords.getOrElse(0) { "Город" }.replaceFirstChar { it.uppercase() }} из ${keywords.getOrElse(1) { "стекла" }}",
            "Тени ${keywords.getOrElse(0) { "прошлого" }}",
            "Эхо ${keywords.getOrElse(0) { "времени" }}",
            "Дорога к ${keywords.getOrElse(0) { "звёздам" }}"
        )

        val allCandidates = (titlesByGenre + prettyTemplates).distinct().filter { it.length in 4..35 && !it.contains("  ") && !it.matches(Regex(".*\\b\\w+\\s+\\w+\\b.*")) || it.split(" ").size <= 4 }

        // Фильтруем плохие типа "Сигнал Корабль" - два существительных без предлога
        val filtered = allCandidates.filter { title ->
            val words = title.split(" ").filter { it.isNotEmpty() }
            if (words.size == 2) {
                val w1 = words[0].lowercase()
                val w2 = words[1].lowercase()
                // если оба слова из keywords и нет предлога - плохо
                val bothKeywords = keywords.contains(w1) && keywords.contains(w2)
                if (bothKeywords) return@filter false
            }
            // не допускаем два существительных подряд без предлога если оба с заглавной
            if (title == "Сигнал Корабль") return@filter false
            true
        }

        return if (filtered.isNotEmpty()) filtered.random(rnd) else titlesByGenre.random(rnd)
    }"""

new_title_func = """    private fun placeToNominative(placeFull: String): String {
        val lower = placeFull.lowercase().trim()
        // убираем предлоги
        var clean = lower.replace(Regex("^(на|в|у|за|под|над|перед|между)\\s+"), "").trim()
        // маппинг предложный -> именительный
        val mapping = mapOf(
            "планете тоби" to "Планета Тоби",
            "планете" to "Планета",
            "краю галактики" to "Край Галактики",
            "станции орион-9" to "Станция Орион-9",
            "станции орион" to "Станция Орион",
            "заброшенном космопорте" to "Заброшенный космопорт",
            "квантового разлома" to "Квантовый разлом",
            "квантовом разломе" to "Квантовый разлом",
            "городе из стекла и света" to "Город из стекла",
            "городе из стекла" to "Город из стекла",
            "корабле аврора" to "Корабль Аврора",
            "корабле" to "Корабль",
            "орбите марса" to "Орбита Марса",
            "отсеке с криокапсулами" to "Отсек с криокапсулами",
            "мостике звездолёта" to "Мостик звездолёта",
            "реакторном отсеке" to "Реакторный отсек",
            "лаборатории времени" to "Лаборатория времени",
            "куполе на луне" to "Купол на Луне",
            "колонии на титане" to "Колония на Титане",
            "заброшенном складе" to "Заброшенный склад",
            "берегу моря" to "Берег моря",
            "тихом городе" to "Тихий город",
            "пустой квартире" to "Пустая квартира"
        )
        for ((k,v) in mapping) {
            if (clean.contains(k)) return v
        }
        // общий случай: планете -> планета, корабле -> корабль, станции -> станция
        clean = clean.replace("планете", "планета")
            .replace("станции", "станция")
            .replace("корабле", "корабль")
            .replace("краю", "край")
            .replace("орбите", "орбита")
            .replace("отсеке", "отсек")
            .replace("мостике", "мостик")
            .replace("лаборатории", "лаборатория")
            .replace("куполе", "купол")
            .replace("колонии", "колония")
            .replace("складе", "склад")
            .replace("берегу", "берег")
            .replace("городе", "город")
            .replace("квартире", "квартира")
        // делаем заглавную
        return clean.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }.replace(" И ", " из ").replace(" Из ", " из ").trim()
    }

    // === V19 TITLE GENERATOR - NATURAL WITH NOMINATIVE ===
    private fun generateTitleV18(original: String, keywords: List<String>, genre: String, places: List<String>, heroes: List<String>, rnd: Random): String {
        val lowerOrig = original.lowercase()

        // 1. Прямые красивые фразы из оригинала - приоритет
        val directCandidates = mutableListOf<String>()
        if (lowerOrig.contains("город из стекла")) directCandidates.add("Город из стекла")
        if (lowerOrig.contains("планете тоби") || lowerOrig.contains("планета тоби")) directCandidates.add("Планета Тоби")
        if (lowerOrig.contains("корабль аврора") || lowerOrig.contains("корабле аврора")) directCandidates.add("Корабль Аврора")
        if (lowerOrig.contains("станция орион")) directCandidates.add("Станция Орион-9")
        if (lowerOrig.contains("квантовый разлом")) directCandidates.add("Квантовый разлом")
        if (lowerOrig.contains("заброшенном космопорте")) directCandidates.add("Заброшенный космопорт")
        if (lowerOrig.contains("сигнал из будущего") || lowerOrig.contains("сигнал — не извне") || lowerOrig.contains("сигнал - не извне")) directCandidates.add("Сигнал из будущего")
        if (lowerOrig.contains("новая галактика")) directCandidates.add("Новая галактика")

        // Добавляем места в именительном падеже
        for (pl in places) {
            val nom = placeToNominative(pl)
            if (nom.length in 4..35) directCandidates.add(nom)
        }

        if (directCandidates.isNotEmpty() && rnd.nextFloat() < 0.75f) {
            // выбираем самый частый или случайный красивый
            return directCandidates.distinct().random(rnd)
        }

        val hero = heroes.firstOrNull() ?: "Озин"
        val placeNom = if (places.isNotEmpty()) placeToNominative(places.first()) else "Тоби"

        val titlesByGenre = when (genre) {
            "Фантастика" -> listOf(
                "Сигнал с $placeNom",
                "Последний день на $placeNom",
                "Хроники $placeNom",
                "Тени $placeNom",
                "Эхо $placeNom",
                "Свет $placeNom",
                "Последний сигнал",
                "Дневник $hero",
                "Вахта $hero",
                "37 часов на $placeNom",
                "Сигнал из будущего",
                "Город из стекла",
                "Квантовый разлом",
                "Станция Орион-9",
                "Заброшенный космопорт",
                "Новая галактика",
                "Письма с $placeNom",
                "Орбита $placeNom",
                "Корабль Аврора"
            )
            "Детектив" -> listOf(
                "Дело о $placeNom",
                "Тени $placeNom",
                "Последний свидетель",
                "Архив $placeNom",
                "Ночь на $placeNom",
                "Протокол $placeNom"
            )
            "Боевик" -> listOf(
                "Точка невозврата: $placeNom",
                "Последний рубеж",
                "Операция $placeNom",
                "Код $placeNom"
            )
            "Роман" -> listOf(
                "Письма с $placeNom",
                "Вечер на $placeNom",
                "Свет $placeNom",
                "Дорога к $placeNom"
            )
            else -> listOf(
                "История $placeNom",
                "Дневник $hero",
                "Письма с $placeNom",
                "Эхо $placeNom",
                "Свет $placeNom"
            )
        }

        // Фильтруем плохие
        val filtered = titlesByGenre.filter { title ->
            title.length in 4..40 &&
            title != "Сигнал Корабль" &&
            !title.contains("  ") &&
            !title.matches(Regex("^\\w+\\s+\\w+$")) || title.split(" ").size <= 4 // разрешаем 2 слова только если красиво
        }.map { it.replace("Планете", "Планета").replace("Корабле", "Корабль").replace("Станции", "Станция") }

        // Дополнительная чистка грамматики
        val cleaned = filtered.map { t ->
            t.replace("Свет Планете", "Свет на планете")
             .replace("Свет Планета", "Свет Планеты")
             .replace("на Планете Тоби", "на планете Тоби")
             .trim()
        }.filter { !it.lowercase().contains("свет планете") }

        // Если всё равно плохо, возвращаем безопасные
        val safe = listOf("Планета Тоби", "Корабль Аврора", "Город из стекла", "Сигнал из будущего", "Станция Орион-9", "Квантовый разлом", "Заброшенный космопорт")

        return (if (cleaned.isNotEmpty()) cleaned else safe).random(rnd)
    }"""

if old_title_func in txt:
    txt = txt.replace(old_title_func, new_title_func)
    print("title generator v19 replaced")
else:
    print("old title func not found, trying partial")
    # try replace function header
    import re
    txt = re.sub(r'private fun generateTitleV18\(.*?\{.*?return.*?random\(rnd\)\n    \}', new_title_func, txt, flags=re.DOTALL)
    print("regex title replace attempted")

# Fix grammar errors in sentence templates
# Replace "Сегодня на орбите Марса требовал присутствия."
txt = txt.replace('Сегодня на орбите Марса требовал присутствия.', 'Сегодня работа на орбите Марса требовала присутствия Озина.')
txt = txt.replace('Сегодня на орбите Марса требовал присутствия', 'Сегодня работа на орбите Марса требовала присутствия')
# Fix "Корабль Аврора висел..." - ensure next sentence has subject
# Fix other templates with missing subject
txt = txt.replace(
    'Корабль Аврора висел на орбите, как напоминание о том, что путь домой всегда есть, даже если дома уже нет. Озин знал, что вернётся, но не сегодня. Сегодня на орбите Марса требовал присутствия.',
    'Корабль Аврора висел на орбите, как напоминание о том, что путь домой всегда есть, даже если дома уже нет. Озин знал, что вернётся, но не сегодня — сегодня его присутствие требовалось на орбите Марса.'
)
# Fix "на орбите Марса требовал присутствия" generic
txt = txt.replace('на орбите Марса требовал присутствия', 'на орбите Марса требовала присутствия')
txt = txt.replace('на орбите Марса требовала присутствия Озина', 'на орбите Марса требовала присутствия')

# Fix other bad sentences
# Ensure "Озин знал, что вернётся" etc is okay
# Fix ending that had grammar issues
old_ending = 'Корабль Аврора висел на орбите, как напоминание о том, что путь домой всегда есть, даже если дома уже нет. Озин знал, что вернётся, но не сегодня. Сегодня на орбите Марса требовал присутствия.'
# already replaced above

path.write_text(txt, encoding='utf-8')
print("v19 patches applied")
