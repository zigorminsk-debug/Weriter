import pathlib, re

p = pathlib.Path('/home/user/zi-writer/app/src/main/java/com/zakharevich/ziwriter/StoryGenerator.kt')
txt = p.read_text(encoding='utf-8')

# 1. Fix GeneratedStory
txt = txt.replace(
    "    data class GeneratedStory(\n        val title: String,\n        val fullText: String,\n        val chars: Int,\n        val pages: Int,\n        val heroes: List<String>,\n        val keywords: List<String>,\n        val correctedOriginal: String\n    )",
    "    data class GeneratedStory(\n        val title: String,\n        val fullText: String,\n        val chars: Int,\n        val pages: Int,\n        val heroes: List<String>,\n        val keywords: List<String>,\n        val correctedOriginal: String,\n        val places: List<String> = emptyList()\n    )"
)

# 2. Replace commonFirstNames + add markers
old_common = '''    private val commonFirstNames = setOf(
        "марина", "алексей", "алекс", "игорь", "анна", "елена", "дмитрий", "сергей", "ольга", "татьяна",
        "иван", "петр", "мария", "наталья", "андрей", "михаил", "юлия", "светлана", "виктор", "николай",
        "владимир", "екатерина", "александр", "александра", "ксения", "дарья", "артем", "артём", "максим",
        "кирилл", "павел", "роман", "денис", "евгений", "константин", "валентина", "галина", "лариса", "оксана", "ирина",
        "наташа", "саша", "леша", "дима", "лена", "катя", "маша", "даша", "женя", "костя", "вова", "паша"
    )'''

new_common = '''    private val nonLivingMarkers = setOf(
        "планета", "планеты", "планету", "планете", "планетой", "планет",
        "звезда", "звезды", "звезду", "звезде", "звездой", "звёзд", "звезд",
        "система", "системы", "систему", "системе", "системой",
        "галактика", "галактики", "галактику", "галактике", "галактикой",
        "корабль", "корабля", "кораблю", "кораблём", "корабле", "кораблей",
        "станция", "станции", "станцию", "станцией",
        "город", "города", "городу", "городом", "городе", "городов",
        "деревня", "деревни", "деревню", "деревней",
        "остров", "острова", "острову", "островом", "острове",
        "море", "моря", "морю", "морем",
        "озеро", "озера", "озеру", "озером",
        "река", "реки", "реку", "рекой",
        "гора", "горы", "гору", "горой",
        "лес", "леса", "лесу", "лесом",
        "созвездие", "созвездия",
        "астероид", "астероида", "астероиду",
        "спутник", "спутника", "спутнику",
        "луна", "луны", "луну", "луной",
        "континент", "континента",
        "страна", "страны", "страну", "страной",
        "мир", "мира", "миру", "миром",
        "вселенная", "вселенной", "вселенную",
        "туманность", "туманности",
        "комета", "кометы", "комету"
    )

    private val livingVerbs = setOf(
        "пошёл", "пошел", "пошла", "пошли", "пришёл", "пришел", "пришла", "пришли",
        "ушёл", "ушел", "ушла", "ушли", "вошёл", "вошел", "вошла", "вышел", "вышла",
        "сказал", "сказала", "сказали", "говорит", "говорила", "говорили",
        "подумал", "подумала", "подумали", "думает", "думал", "думала",
        "сделал", "сделала", "сделали", "делает", "делал", "делала",
        "увидел", "увидела", "увидели", "видит", "видел", "видела",
        "стоит", "стоял", "стояла", "сидит", "сидел", "сидела",
        "лежит", "лежал", "лежала", "бежит", "бежал", "бежала",
        "идёт", "идет", "шёл", "шел", "шла", "шли",
        "крикнул", "крикнула", "кричали", "кричит",
        "улыбнулся", "улыбнулась", "улыбнулись", "улыбается",
        "засмеялся", "засмеялась", "заплакал", "заплакала",
        "спросил", "спросила", "спросили", "спрашивает",
        "ответил", "ответила", "ответили", "отвечает",
        "посмотрел", "посмотрела", "посмотрели", "смотрит",
        "почувствовал", "почувствовала", "чувствует",
        "решил", "решила", "решили",
        "хотел", "хотела", "хотели", "хочет",
        "мог", "могла", "могли", "может",
        "любит", "любил", "любила"
    )

    private val commonFirstNames = setOf(
        "марина", "алексей", "алекс", "игорь", "анна", "елена", "дмитрий", "сергей", "ольга", "татьяна",
        "иван", "петр", "мария", "наталья", "андрей", "михаил", "юлия", "светлана", "виктор", "николай",
        "владимир", "екатерина", "александр", "александра", "ксения", "дарья", "артем", "артём", "максим",
        "кирилл", "павел", "роман", "денис", "евгений", "константин", "валентина", "галина", "лариса", "оксана", "ирина",
        "наташа", "саша", "леша", "дима", "лена", "катя", "маша", "даша", "женя", "костя", "вова", "паша",
        "озин", "тоби", "лира", "кай", "рея", "зорин", "альта", "вей", "нова"
    )'''

txt = txt.replace(old_common, new_common)

# 3. Fix extensiveStopLower
txt = txt.replace(
    '        "книга", "история", "рассказ", "роман", "глава", "страница", "лист", "текст", "слово", "фраза"\n    )',
    '        "книга", "история", "рассказ", "роман", "глава", "страница", "лист", "текст", "слово", "фраза",\n        "планета", "звезда", "система", "галактика", "корабль", "станция", "город", "остров", "море", "река", "гора", "лес"\n    )'
)

# 4. Replace extractHeroesV4
old_extract = '''    private fun extractHeroesV4(text: String): List<String> {
        val sentences = text.split(Regex("[.!?]+")).map { it.trim() }.filter { it.isNotEmpty() }
        val candidates = mutableListOf<String>()
        val firstWordsThatAreNames = mutableListOf<String>()
        val nameFreq = mutableMapOf<String, Int>()
        for (sentence in sentences) {
            val words = sentence.split(Regex("\\\\s+")).map { it.replace(Regex("[^А-Яа-яЁёA-Za-z-]"), "") }.filter { it.isNotEmpty() }
            if (words.isEmpty()) continue
            val first = words[0]
            if (first.matches(Regex("[А-ЯЁ][а-яё]{2,}")) && first.lowercase() in commonFirstNames) {
                firstWordsThatAreNames.add(first)
                nameFreq[first.lowercase()] = (nameFreq[first.lowercase()] ?: 0) + 1
            }
            for (i in 1 until words.size) {
                val w = words[i]
                if (!w.matches(Regex("[А-ЯЁ][а-яё]{2,}"))) continue
                if (w.length < 3 || w.length > 15) continue
                val lower = w.lowercase()
                if (lower in extensiveStopLower) continue
                if (isLikelyAdjective(w)) continue
                val lowerCount = Regex("\\\\b${Regex.escape(lower)}\\\\b", RegexOption.IGNORE_CASE).findAll(text).count()
                val upperCount = Regex("\\\\b${Regex.escape(w)}\\\\b").findAll(text).count()
                if (lowerCount > upperCount * 2) continue
                candidates.add(w)
                nameFreq[lower] = (nameFreq[lower] ?: 0) + 1
            }
        }
        val all = (candidates + firstWordsThatAreNames).distinct()
        val sorted = all.sortedWith(compareByDescending<String> { nameFreq[it.lowercase()] ?: 0 }.thenByDescending { it.lowercase() in commonFirstNames })
        return sorted.filter { it.lowercase() in commonFirstNames || candidates.count { c -> c == it } >= 1 }.take(3)
    }'''

new_extract = '''    data class ExtractedEntities(
        val heroes: List<String>,
        val places: List<String>
    )

    fun extractEntitiesV5(text: String): ExtractedEntities {
        val sentences = text.split(Regex("[.!?]+")).map { it.trim() }.filter { it.isNotEmpty() }
        val heroCandidates = mutableListOf<String>()
        val placeCandidates = mutableListOf<String>()
        val heroFreq = mutableMapOf<String, Int>()
        val placeFreq = mutableMapOf<String, Int>()

        for (sentence in sentences) {
            val rawWords = sentence.split(Regex("\\\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
            if (rawWords.isEmpty()) continue
            val cleanWords = rawWords.map { it.replace(Regex("[^А-Яа-яЁёA-Za-z0-9-]"), "") }.filter { it.isNotEmpty() }
            val lowerWords = cleanWords.map { it.lowercase() }

            for (i in cleanWords.indices) {
                val w = cleanWords[i]
                if (!w.matches(Regex("[А-ЯЁ][а-яё]{2,}"))) continue
                if (w.length < 3 || w.length > 20) continue
                val lower = w.lowercase()
                if (lower in extensiveStopLower) continue
                if (isLikelyAdjective(w)) continue

                val prev1 = if (i >= 1) lowerWords[i-1] else ""
                val prev2 = if (i >= 2) lowerWords[i-2] else ""
                val next1 = if (i+1 < lowerWords.size) lowerWords[i+1] else ""
                val next2 = if (i+2 < lowerWords.size) lowerWords[i+2] else ""

                val isAfterNonLiving = prev1 in nonLivingMarkers || prev2 in nonLivingMarkers
                val isBeforeNonLiving = next1 in nonLivingMarkers || next2 in nonLivingMarkers
                val isFollowedByLivingVerb = next1 in livingVerbs || next2 in livingVerbs
                val isPrecededByLivingContext = prev1 in setOf("имя", "зовут", "по", "имени")

                if (isAfterNonLiving || isBeforeNonLiving) {
                    val placeName = if (isAfterNonLiving) {
                        val marker = if (prev1 in nonLivingMarkers) rawWords[i-1] else rawWords[i-2]
                        (marker + " " + w).trim()
                    } else {
                        (w + " " + rawWords.getOrElse(i+1){""}).trim()
                    }
                    placeCandidates.add(placeName)
                    placeCandidates.add(w)
                    placeFreq[lower] = (placeFreq[lower] ?: 0) + 2
                    placeFreq[placeName.lowercase()] = (placeFreq[placeName.lowercase()] ?: 0) + 2
                    continue
                }

                if (isFollowedByLivingVerb || lower in commonFirstNames || isPrecededByLivingContext) {
                    heroCandidates.add(w)
                    heroFreq[lower] = (heroFreq[lower] ?: 0) + (if (isFollowedByLivingVerb) 3 else 1)
                } else {
                    val lowerCount = Regex("\\\\b" + Regex.escape(lower) + "\\\\b", RegexOption.IGNORE_CASE).findAll(text).count()
                    val upperCount = Regex("\\\\b" + Regex.escape(w) + "\\\\b").findAll(text).count()
                    if (lowerCount > upperCount * 2) continue
                    val hasLivingVerbNearby = livingVerbs.any { lv -> text.lowercase().contains(lower + " " + lv) || text.lowercase().contains(lower + ", " + lv) }
                    if (hasLivingVerbNearby || lower in commonFirstNames) {
                        heroCandidates.add(w)
                        heroFreq[lower] = (heroFreq[lower] ?: 0) + 1
                    } else if (w.length >= 4) {
                        heroCandidates.add(w)
                        heroFreq[lower] = (heroFreq[lower] ?: 0) + 1
                    }
                }
            }
        }

        val distinctHeroes = heroCandidates.distinct()
        val sortedHeroes = distinctHeroes.sortedWith(compareByDescending<String> { heroFreq[it.lowercase()] ?: 0 }.thenByDescending { it.lowercase() in commonFirstNames }).filter { it.lowercase() !in placeFreq.keys }.take(4)

        val distinctPlaces = placeCandidates.distinct().filter { it.length > 2 }.sortedWith(compareByDescending<String> { placeFreq[it.lowercase()] ?: 0 }).take(6)

        return ExtractedEntities(sortedHeroes, distinctPlaces)
    }

    private fun extractHeroesV4(text: String): List<String> {
        return extractEntitiesV5(text).heroes
    }'''

txt = txt.replace(old_extract, new_extract)

# 5. Fix smoothText
old_smooth = '''    private fun smoothText(text: String): String {
        val sentences = text.split(Regex("(?<=[.!?])\\\\s+"))
        val seen = mutableSetOf<String>()
        val res = mutableListOf<String>()
        for (s in sentences) {
            val t = s.trim()
            if (t.length < 15) { res.add(s); continue }
            val low = t.lowercase()
            if (low in seen) continue
            if (res.takeLast(4).any { jaccard(t, it) > 0.72 }) continue
            res.add(s)
            seen.add(low)
            if (seen.size > 300) {
                seen.removeAll(seen.take(100).toSet())
            }
        }
        return res.joinToString(" ")
    }'''

new_smooth = '''    private fun smoothText(text: String): String {
        val paragraphs = text.split(Regex("\\n\\n+"))
        val seenGlobal = mutableSetOf<String>()
        val resultParas = mutableListOf<String>()
        for (para in paragraphs) {
            val trimmedPara = para.trim()
            if (trimmedPara.isEmpty()) continue
            if (trimmedPara.startsWith("Глава") || trimmedPara.startsWith("ГЛАВА") || trimmedPara.startsWith("Пролог") || trimmedPara.startsWith("ПРОЛОГ") || trimmedPara.startsWith("Эпилог") || trimmedPara.startsWith("— — —")) {
                resultParas.add(trimmedPara)
                continue
            }
            val sentences = trimmedPara.split(Regex("(?<=[.!?])\\\\s+"))
            val res = mutableListOf<String>()
            for (s in sentences) {
                val t = s.trim()
                if (t.length < 15) { res.add(s); continue }
                val low = t.lowercase()
                if (low in seenGlobal) continue
                if (res.takeLast(4).any { jaccard(t, it) > 0.72 }) continue
                res.add(s)
                seenGlobal.add(low)
                if (seenGlobal.size > 400) {
                    val toRemove = seenGlobal.take(150).toSet()
                    seenGlobal.removeAll(toRemove)
                }
            }
            if (res.isNotEmpty()) resultParas.add(res.joinToString(" "))
        }
        return resultParas.joinToString("\\n\\n")
    }'''

txt = txt.replace(old_smooth, new_smooth)

# 6. Fix generate start
old_gen_start = '''    fun generate(config: GenerationConfig): GeneratedStory {
        val rawOriginal = config.originalText.trim()
        val original = if (config.fixGrammar) GrammarCorrector.correct(rawOriginal) else rawOriginal
        val targetChars = config.pages * CHARS_PER_PAGE
        val extractedHeroes = extractHeroesV4(original)
        val heroes = if (config.customHeroes.isNotEmpty()) config.customHeroes else extractedHeroes
        val keywords = extractKeywords(original)
        val mainPlace = if (config.customPlace.isNotEmpty()) config.customPlace else randomPlaceFull(config.genre)
        val placeNoun = randomPlaceNoun(config.genre)
        val title = if (config.bookTitle.isNotBlank()) config.bookTitle.trim() else generateTitle(original, keywords, config.genre)
        val isPhil = detectPhilosophical(original, keywords)

        val sb = StringBuilder()
        sb.append(original)
        if (!original.endsWith("\\n")) sb.append("\\n\\n")
        sb.append(generateBridge(heroes, mainPlace, isPhil))
        sb.append("\\n\\n")'''

new_gen_start = '''    fun generate(config: GenerationConfig): GeneratedStory {
        val rawOriginal = config.originalText.trim()
        val original = if (config.fixGrammar) GrammarCorrector.correct(rawOriginal) else rawOriginal
        val targetChars = config.pages * CHARS_PER_PAGE

        val entities = extractEntitiesV5(original)
        val extractedHeroes = entities.heroes
        val extractedPlaces = entities.places

        val heroes = if (config.customHeroes.isNotEmpty()) config.customHeroes else extractedHeroes
        val keywords = extractKeywords(original)

        val mainPlace = if (config.customPlace.isNotEmpty()) {
            config.customPlace
        } else {
            if (extractedPlaces.isNotEmpty() && Random.nextFloat() < 0.6f) {
                val p = extractedPlaces.first()
                if (p.lowercase().startsWith("планета") || p.lowercase().startsWith("звезда") || p.lowercase().startsWith("город")) p else "на " + p
            } else {
                randomPlaceFull(config.genre)
            }
        }

        val placeNoun = if (extractedPlaces.isNotEmpty() && Random.nextFloat() < 0.5f) {
            extractedPlaces.first().replace(Regex("^(на|в|у|за) "), "")
        } else {
            randomPlaceNoun(config.genre)
        }

        val title = if (config.bookTitle.isNotBlank()) config.bookTitle.trim() else generateTitle(original, keywords, config.genre)
        val isPhil = detectPhilosophical(original, keywords)

        val sb = StringBuilder()
        var fixedOriginal = original.trim()
        if (fixedOriginal.isNotEmpty() && !fixedOriginal.endsWith(".") && !fixedOriginal.endsWith("!") && !fixedOriginal.endsWith("?") && !fixedOriginal.endsWith("…") && !fixedOriginal.endsWith("»")) {
            fixedOriginal += "."
        }
        sb.append(fixedOriginal)
        sb.append("\\n\\n")
        sb.append(generateBridge(heroes, mainPlace, isPhil))
        sb.append("\\n\\n")'''

txt = txt.replace(old_gen_start, new_gen_start)

# 7. Fix finalText cleaning and return
old_end = '''        sb.append(generateEnding(config.genre, heroes, keywords, mainPlace, isPhil, config.authorName))
        var finalText = sb.toString().take(targetChars + 1000)
        finalText = smoothText(finalText)
        finalText = finalText.replace(Regex("\\\\s+"), " ").replace(" .", ".").replace(" ,", ",").replace("  ", " ")

        val actualPages = (finalText.length + CHARS_PER_PAGE - 1) / CHARS_PER_PAGE
        return GeneratedStory(title, finalText, finalText.length, actualPages, if (heroes.isEmpty()) listOf("он") else heroes, keywords, original)'''

new_end = '''        sb.append(generateEnding(config.genre, heroes, keywords, mainPlace, isPhil, config.authorName))
        var finalText = sb.toString().take(targetChars + 1000)
        finalText = smoothText(finalText)
        finalText = finalText.replace(Regex("[ \\t]+"), " ")
            .replace(Regex(" \\n"), "\\n").replace(Regex("\\n "), "\\n")
            .replace(Regex("\\n{3,}"), "\\n\\n")
            .replace(" .", ".").replace(" ,", ",").replace(" !", "!").replace(" ?", "?")
            .replace("  ", " ")
            .trim()
        finalText = finalText.replace(Regex("([а-яёa-z])\\n([А-ЯЁA-Z])"), "$1. $2")

        val actualPages = (finalText.length + CHARS_PER_PAGE - 1) / CHARS_PER_PAGE
        return GeneratedStory(title, finalText, finalText.length, actualPages, if (heroes.isEmpty()) listOf("он") else heroes, keywords, original, extractedPlaces)'''

txt = txt.replace(old_end, new_end)

# 8. Fix randomPlaceFull
old_place = '''    private fun randomPlaceFull(genre: String): String {
        val places = when (genre) {
            "Фантастика" -> listOf("на краю Галактики", "на станции Орион-9", "в заброшенном космопорте", "у квантового разлома", "в городе из стекла и света", "на орбите Марса", "в лаборатории времени", "на корабле Аврора", "в куполе на Луне", "у черной дыры", "в нейросети", "в колонии на Титане", "в отсеке с криокапсулами", "на мостике звездолёта")'''

new_place = '''    private fun randomPlaceFull(genre: String): String {
        val places = when (genre) {
            "Фантастика" -> listOf("на краю Галактики", "на станции Орион-9", "в заброшенном космопорте", "у квантового разлома", "в городе из стекла и света", "на орбите Марса", "в лаборатории времени", "на корабле Аврора", "в куполе на Луне", "у черной дыры", "в нейросети", "в колонии на Титане", "в отсеке с криокапсулами", "на мостике звездолёта", "на планете Тоби", "у звезды Озин", "на астероиде Кай")'''

txt = txt.replace(old_place, new_place)

# 9. Fix generateTitle
old_title = '''    private fun generateTitle(original: String, keywords: List<String>, genre: String): String {
        val key = keywords.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Тишина"
        val key2 = keywords.getOrElse(1) { "судьба" }
        val key3 = keywords.getOrElse(2) { "дорога" }
        val titles = when (genre) {
            "Боевик" -> listOf("Последний выстрел", "Точка невозврата", "Мышечная память", "Попадание в цель", "На пределе", "Один шанс", "Без права на промах", "Холодный расчет", "Линия огня", "Код $key", "Операция $key2", "Последний рубеж")
            "Философская проза" -> listOf("Внутренний голос", "Точка А и точка Б", "Там, где кончается страх", "Письмо к себе", "Право на улыбку", "Быть собой", "Тишина простыней", "Голос внутри", "Право идти прямо", "Когда $key молчит")
            "Детектив" -> listOf("Дело № $key", "Тени $key2", "Улика из $key", "Ночной разговор", "Последний свидетель", "Тайна ${placeHolder()}", "Код $key3", "Архив $key", "След $key2")
            "Фантастика" -> listOf("Станция $key", "Квантовый $key2", "Галактика $key", "Код Орион", "Время $key3", "Сигнал $key", "Последний корабль", "Город из $key2")
            "Роман" -> listOf("Когда $key молчит", "$key и $key2", "Письмо про $key2", "Берег $key", "Мост $key2", "Осень $key", "Право на $key2", "Тихий $key3")
            "Приключения" -> listOf("Остров $key", "Карта $key2", "Сокровище $key", "Перевал $key2", "Храм $key", "Экспедиция $key3")
            "Хоррор" -> listOf("Дом $key", "Шепот $key2", "Тени $key", "Квартира 13", "Лес без $key2", "Зеркала $key")
            "Юмор" -> listOf("Офис $key", "Кот $key2", "Маршрутка $key", "Корпоратив $key3", "Чат ТСЖ", "Супермаркет $key")
            else -> listOf("Когда $key молчит", "$key и $key2", "Тени $key2", "Право на $key2", "Дорога без указателей", "Эхо $key", "История $key", "Код $key3")
        }
        return titles.random()
    }'''

new_title = '''    private fun generateTitle(original: String, keywords: List<String>, genre: String): String {
        val key = keywords.firstOrNull()?.replaceFirstChar { it.uppercaseChar() } ?: "Тишина"
        val key2 = keywords.getOrElse(1) { "судьба" }
        val key3 = keywords.getOrElse(2) { "дорога" }
        val ph = placeHolder()
        val titles = when (genre) {
            "Боевик" -> listOf("Последний выстрел", "Точка невозврата", "Мышечная память", "Попадание в цель", "На пределе", "Один шанс", "Без права на промах", "Холодный расчет", "Линия огня", "Код " + key, "Операция " + key2, "Последний рубеж")
            "Философская проза" -> listOf("Внутренний голос", "Точка А и точка Б", "Там, где кончается страх", "Письмо к себе", "Право на улыбку", "Быть собой", "Тишина простыней", "Голос внутри", "Право идти прямо", "Когда " + key + " молчит")
            "Детектив" -> listOf("Дело № " + key, "Тени " + key2, "Улика из " + key, "Ночной разговор", "Последний свидетель", "Тайна " + ph, "Код " + key3, "Архив " + key, "След " + key2)
            "Фантастика" -> listOf("Станция " + key, "Квантовый " + key2, "Галактика " + key, "Код Орион", "Время " + key3, "Сигнал " + key, "Последний корабль", "Город из " + key2)
            "Роман" -> listOf("Когда " + key + " молчит", key + " и " + key2, "Письмо про " + key2, "Берег " + key, "Мост " + key2, "Осень " + key, "Право на " + key2, "Тихий " + key3)
            "Приключения" -> listOf("Остров " + key, "Карта " + key2, "Сокровище " + key, "Перевал " + key2, "Храм " + key, "Экспедиция " + key3)
            "Хоррор" -> listOf("Дом " + key, "Шепот " + key2, "Тени " + key, "Квартира 13", "Лес без " + key2, "Зеркала " + key)
            "Юмор" -> listOf("Офис " + key, "Кот " + key2, "Маршрутка " + key, "Корпоратив " + key3, "Чат ТСЖ", "Супермаркет " + key)
            else -> listOf("Когда " + key + " молчит", key + " и " + key2, "Тени " + key2, "Право на " + key2, "Дорога без указателей", "Эхо " + key, "История " + key, "Код " + key3)
        }
        return titles.random()
    }'''

txt = txt.replace(old_title, new_title)

# 10. Fix generateBridge
old_bridge = '''    private fun generateBridge(heroes: List<String>, placeFull: String, isPhilosophical: Boolean): String {
        return if (isPhilosophical) {
            "С этих строк начинается попытка разобраться. Ты уже сказал главное. Дальше — только честность. Без учителей, без морали, которую тебе навязали. Только ты и то, что внутри. $placeFull — лишь декорация, главное происходит внутри."
        } else {
            val hero = heroes.firstOrNull() ?: "он"
            "С этих строк началась история, которая вскоре вышла за пределы привычного. $hero еще не знал, что каждое слово, сказанное в тот момент $placeFull, станет частью чего-то большего. Иногда одна секунда меняет всё."
        }
    }'''

new_bridge = '''    private fun generateBridge(heroes: List<String>, placeFull: String, isPhilosophical: Boolean): String {
        val hero = heroes.firstOrNull() ?: "он"
        val heroCap = hero.replaceFirstChar { it.uppercaseChar() }
        return if (isPhilosophical) {
            val philBridges = listOf(
                "С этих строк начинается попытка разобраться честно. Ты уже сказал главное. Дальше — без масок. " + placeFull + " — лишь декорация, главное происходит внутри.",
                "То, что ты написал выше — не просто слова. Это точка, где заканчивается враньё себе. Дальше — только правда, какой бы неудобной она ни была.",
                "Ты остановился на полуслове, и это правильно. Иногда пауза важнее продолжения. В этой паузе — вся жизнь, которую ты пытался не замечать.",
                "Эти строки — как дверь, которую ты долго боялся открыть. За ней нет монстров. За ней — ты сам, настоящий, без учителей и правил.",
                "Написанное выше — уже не текст, а признание. Ты сказал то, что годами держал внутри. Теперь назад дороги нет, только вперёд.",
                "С этого момента начинается другое. Не история про кого-то, а история про тебя. " + placeFull + " вокруг — лишь фон, а в центре — твой выбор.",
                "Ты поставил точку, но на самом деле — это запятая. Продолжение — здесь, в попытке понять, кто говорит внутри тебя твоим голосом.",
                "Выше — твои слова. Ниже — попытка их понять. Без морали, которую навязали, без страха показаться глупым. Только ты.",
                "Иногда достаточно одной честной фразы, чтобы всё изменилось. Ты её уже написал. Осталось — не предать её.",
                "Это не начало книги. Это начало возвращения к себе. К тому, кем ты был до школы, до правил, до голоса."
            )
            philBridges.random()
        } else {
            val bridges = listOf(
                heroCap + " ещё не знал, что этот день " + placeFull + " изменит всё. Одна встреча, одно слово — и привычный мир треснет по швам.",
                "То, что началось " + placeFull + ", казалось мелочью. Но мелочи иногда становятся точкой невозврата.",
                "Утро " + placeFull + " ничем не отличалось от других. Пока " + heroCap + " не понял — сегодня всё будет иначе.",
                "История, начавшаяся " + placeFull + ", не должна была выйти за пределы обычного. Но она вышла.",
                heroCap + " думал, что контролирует ситуацию " + placeFull + ". Он ошибался. Ситуация уже контролировала его.",
                "В тот момент " + placeFull + " всё казалось простым. Просто разговор, просто взгляд. Потом оказалось — это был выбор.",
                "Никто " + placeFull + " не заметил, как изменился воздух. Кроме " + heroCap + ". Он почувствовал — что-то началось.",
                heroCap + " стоял " + placeFull + " и не подозревал, что каждое его слово уже стало частью чужого плана.",
                "Секунда " + placeFull + " растянулась. В ней уместилась целая жизнь — прошлое, настоящее и то, что ещё не случилось.",
                "Это началось тихо " + placeFull + ". Без выстрелов, без криков. Просто один человек решил пойти прямо, когда все шли направо.",
                heroCap + " пришёл " + placeFull + " случайно. Случайно — любимое слово судьбы, когда она хочет вмешаться без спроса.",
                "Тот день " + placeFull + " запомнится не тем, что произошло, а тем, что могло произойти и не произошло.",
                heroCap + " не искал приключений " + placeFull + ". Приключения нашли его сами, как находят тех, кто пытается их избежать.",
                "Всё, что случилось " + placeFull + ", уместилось бы в одну строку протокола. Но за строкой — целая жизнь.",
                "Он/она — " + heroCap + " — ещё не понимал " + placeFull + ", что уже перешёл черту. Обратно — только через правду.",
                placeFull + " было тихо. Слишком тихо, чтобы быть правдой. " + heroCap + " почувствовал это первым.",
                "С этих строк начинается не продолжение, а другая история. Та, где " + heroCap + " уже не может притвориться, что ничего не было.",
                "То, что ты прочитал выше — лишь пролог. Настоящее началось " + placeFull + ", когда " + heroCap + " поднял глаза.",
                "Иногда жизнь меняется не громко " + placeFull + ", а шёпотом. " + heroCap + " услышал этот шёпот.",
                "Дорога " + placeFull + " вела куда-то. " + heroCap + " не знал куда, но знал — идти надо. И пошёл."
            )
            bridges.random()
        }
    }'''

txt = txt.replace(old_bridge, new_bridge)

# 11. Fix generateEnding
old_ending = '''    private fun generateEnding(genre: String, heroes: List<String>, keywords: List<String>, placeFull: String, isPhilosophical: Boolean, authorName: String): String {
        val author = if (authorName.isNotBlank()) authorName else "Zakharevich Igor"
        return if (isPhilosophical) {
            """

И вот ты снова здесь. Один. Но теперь ты знаешь его имя. Это страх.

Ты не обязан его убивать. Просто перестань давать ему микрофон. Пусть говорит на заднем плане. А ты делай своё. Улыбайся, когда хочется. Говори, когда есть что сказать. Иди прямо, когда все идут направо.

Может, завтра на многолюдной улице ты снова встретишь её. И в этот раз не пройдешь мимо. Остановишься. На секунду. И эта секунда станет твоей жизнью.

— Конец —

Сгенерировано в ZI Writer v13 OPDS FLIBUSTA • Автор: $author • ${keywords.take(6).joinToString(", ")} • Флибуста OPDS 213.5.65.159
""".trimIndent()
        } else {
            val ending = when (genre) {
                "Боевик" -> """
                    Он стоял и смотрел вдаль. Выстрел уже прозвучал, цель поражена, но главное было не в этом. Главное — он перестал сомневаться.

                    Гильза под ногами блестела в свете фонаря. Он нагнулся, поднял её и положил в карман. На память. О том дне, когда понял, что последний выстрел — это всегда первый шаг.

                    — Конец —

                    Сгенерировано в ZI Writer v13 OPDS • Автор: $author • ${keywords.take(6).joinToString(", ")} • OPDS 213.5.65.159
                """.trimIndent()
                else -> """
                    Он стоял и смотрел вдаль. Всё, с чего началась эта история, теперь было частью чего-то большего. Он улыбнулся. Путь был долгим, но он того стоил.

                    — Конец —

                    Сгенерировано в ZI Writer v13 OPDS FLIBUSTA • Автор: $author • ${keywords.take(6).joinToString(", ")} • ${heroes.joinToString(", ")} • OPDS 213.5.65.159
                """.trimIndent()
            }
            "\\n\\n$ending"
        }
    }'''

new_ending = '''    private fun generateEnding(genre: String, heroes: List<String>, keywords: List<String>, placeFull: String, isPhilosophical: Boolean, authorName: String): String {
        val author = if (authorName.isNotBlank()) authorName else "Zakharevich Igor"
        val kw = keywords.take(6).joinToString(", ")
        val hr = heroes.joinToString(", ")
        return if (isPhilosophical) {
            "\\n\\nИ вот ты снова здесь. Один. Но теперь ты знаешь его имя. Это страх.\\n\\nТы не обязан его убивать. Просто перестань давать ему микрофон. Пусть говорит на заднем плане. А ты делай своё. Улыбайся, когда хочется. Говори, когда есть что сказать. Иди прямо, когда все идут направо.\\n\\nМожет, завтра на многолюдной улице ты снова встретишь её. И в этот раз не пройдешь мимо. Остановишься. На секунду. И эта секунда станет твоей жизнью.\\n\\n— Конец —\\n\\nСгенерировано в ZI Writer v16 LIVING EDITOR • Автор: " + author + " • " + kw + " • Флибуста OPDS 213.5.65.159"
        } else {
            val baseEnding = when (genre) {
                "Боевик" -> "Он стоял и смотрел вдаль. Выстрел уже прозвучал, цель поражена, но главное было не в этом. Главное — он перестал сомневаться.\\n\\nГильза под ногами блестела в свете фонаря. Он нагнулся, поднял её и положил в карман. На память. О том дне, когда понял, что последний выстрел — это всегда первый шаг.\\n\\n— Конец —\\n\\nСгенерировано в ZI Writer v16 LIVING EDITOR • Автор: " + author + " • " + kw + " • OPDS 213.5.65.159"
                else -> "Он стоял и смотрел вдаль. Всё, с чего началась эта история, теперь было частью чего-то большего. Он улыбнулся. Путь был долгим, но он того стоил.\\n\\n— Конец —\\n\\nСгенерировано в ZI Writer v16 LIVING EDITOR • Автор: " + author + " • " + kw + " • " + hr + " • OPDS 213.5.65.159"
            }
            "\\n\\n" + baseEnding
        }
    }'''

txt = txt.replace(old_ending, new_ending)

p.write_text(txt, encoding='utf-8')
print("v16 fixed")
