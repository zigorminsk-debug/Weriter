package com.zakharevich.ziwriter

import kotlin.random.Random

object StoryGenerator {

    data class GenerationConfig(
        val originalText: String,
        val pages: Int,
        val genre: String,
        val style: String,
        val fixGrammar: Boolean = true,
        val customHeroes: List<String> = emptyList(),
        val customPlace: String = "",
        val bookTitle: String = "",
        val authorName: String = "",
        val learnedTemplates: List<String> = emptyList(),
        val useLearned: Boolean = true,
        val learnedHeroes: List<String> = emptyList(),
        val learnedPlaces: List<String> = emptyList(),
        val learnedPhrases: List<String> = emptyList(),
        val learnedPlots: List<String> = emptyList(),
        val detailedPlots: List<MathPlotModel.Plot> = emptyList(),
        val useMathModel: Boolean = true,
        /** Показывать служебную строку о выбранном сюжете прямо в тексте (по умолчанию выключено). */
        val debugInfo: Boolean = false
    )

    data class GeneratedStory(
        val title: String,
        val fullText: String,
        val chars: Int,
        val pages: Int,
        val heroes: List<String>,
        val keywords: List<String>,
        val correctedOriginal: String
    )

    private const val CHARS_PER_PAGE = 1800

    fun detectGenreStatic(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("выстрел") || lower.contains("оружие") -> "Боевик"
            lower.contains("улика") || lower.contains("детектив") -> "Детектив"
            lower.contains("любовь") || lower.contains("сердце") -> "Роман"
            lower.contains("космос") || lower.contains("корабль") -> "Фантастика"
            lower.contains("приключен") -> "Приключения"
            lower.contains("призрак") || lower.contains("хоррор") -> "Хоррор"
            lower.contains("юмор") || lower.contains("пицца") || lower.contains("ворон") || lower.contains("птиц") -> "Юмор"
            lower.contains("философ") || lower.contains("душа") -> "Философская проза"
            else -> "general"
        }
    }

    private fun extractTrigrams(text: String): Set<String> {
        val words = text.lowercase().replace(Regex("[^а-яёa-z ]"), " ").split(Regex("\\s+")).filter { it.length > 2 }
        if (words.size < 3) return emptySet()
        val set = mutableSetOf<String>()
        for (i in 0..words.size-3) set.add("${words[i]} ${words[i+1]} ${words[i+2]}")
        return set
    }

    private fun jaccard(a: String, b: String): Double {
        val sa = a.lowercase().replace(Regex("[^а-яёa-z ]"), " ").split(Regex("\\s+")).filter { it.length > 3 }.toSet()
        val sb = b.lowercase().replace(Regex("[^а-яёa-z ]"), " ").split(Regex("\\s+")).filter { it.length > 3 }.toSet()
        if (sa.isEmpty() || sb.isEmpty()) return 0.0
        val inter = sa.intersect(sb).size
        val union = sa.union(sb).size
        return inter.toDouble() / union
    }

    /**
     * Чистка повторов на уровне предложений.
     *
     * ВАЖНО: абзацы сохраняются. Раньше все предложения склеивались через пробел,
     * поэтому весь готовый текст (роман на десятки листов) превращался в одну
     * длинную строку: терялись абзацы, разметка «— — — Страница N — — —» и
     * заголовки глав, и в DOCX/EPUB/PDF всё уезжало одним абзацем.
     */
    private fun smoothText(text: String): String {
        val paragraphs = text.split(Regex("\\n[ \\t]*\\n"))
        val seen = mutableSetOf<String>()
        val recent = mutableListOf<String>()
        val out = mutableListOf<String>()
        for (paragraph in paragraphs) {
            val p = paragraph.trim()
            if (p.isEmpty()) continue
            // Служебные строки (метки страниц, заголовки глав, «— Конец —») оставляем
            // как есть: у них похожий набор слов, и фильтр повторов их бы выбросил.
            if (isStructuralLine(p)) { out.add(p); continue }
            val sentences = p.split(Regex("(?<=[.!?])\\s+"))
            val kept = mutableListOf<String>()
            for (s in sentences) {
                val t = s.trim()
                if (t.isEmpty()) continue
                if (t.length < 15) { kept.add(t); continue }
                val low = t.lowercase()
                if (low in seen) continue
                if (recent.takeLast(4).any { jaccard(t, it) > 0.72 }) continue
                kept.add(t)
                seen.add(low)
                recent.add(t)
                if (recent.size > 8) recent.removeAt(0)
                if (seen.size > 300) seen.removeAll(seen.take(100).toSet())
            }
            if (kept.isNotEmpty()) out.add(kept.joinToString(" "))
        }
        return out.joinToString("\n\n")
    }

    /** Служебные строки книги: их нельзя ни склеивать, ни удалять как «повтор». */
    private fun isStructuralLine(paragraph: String): Boolean {
        if (paragraph.startsWith("— — — Страница")) return true
        if (paragraph.startsWith("— Конец —")) return true
        val first = paragraph.lineSequence().firstOrNull()?.trim().orEmpty()
        val prefixes = listOf("Глава", "ГЛАВА", "Пролог", "ПРОЛОГ", "Эпилог", "ЭПИЛОГ", "Интерлюдия", "Послесловие", "Предисловие")
        return prefixes.any { first.startsWith(it) } && first.length < 120
    }

    fun generate(config: GenerationConfig): GeneratedStory {
        val rawOriginal = config.originalText.trim()
        val original = if (config.fixGrammar) GrammarCorrector.correct(rawOriginal) else rawOriginal
        val targetChars = config.pages * CHARS_PER_PAGE
        val extractedHeroes = extractHeroesV4(original)
        val heroes = when {
            config.customHeroes.isNotEmpty() -> config.customHeroes
            config.useLearned && config.learnedHeroes.isNotEmpty() -> {
                val learned = config.learnedHeroes.filter { it.length in 3..20 }.shuffled().take(2)
                (extractedHeroes + learned).distinct().take(3).ifEmpty { extractedHeroes }
            }
            else -> extractedHeroes
        }
        val keywords = extractKeywords(original)
        val mainPlace = when {
            config.customPlace.isNotEmpty() -> config.customPlace
            config.useLearned && config.learnedPlaces.isNotEmpty() -> {
                if (Random.nextFloat() < 0.6f) config.learnedPlaces.random() else randomPlaceFull(config.genre, keywords, original)
            }
            else -> randomPlaceFull(config.genre, keywords, original)
        }
        val placeNoun = if (config.useLearned && config.learnedPlaces.isNotEmpty() && Random.nextFloat() < 0.5f) {
            config.learnedPlaces.random().replace(Regex("""^(в|на|у|за|под|над|перед|между|около|возле)\s+"""), "").trim().take(30).ifBlank { randomPlaceNoun(config.genre, keywords) }
        } else randomPlaceNoun(config.genre, keywords)

        val title = if (config.bookTitle.isNotBlank()) config.bookTitle.trim() else generateTitle(original, keywords, config.genre)
        val isPhil = detectPhilosophical(original, keywords)

        val sb = StringBuilder()
        sb.append(original)
        if (!original.endsWith("\n")) sb.append("\n\n")
        sb.append(generateBridge(heroes, mainPlace, isPhil, config.learnedPlaces, config.learnedPhrases, config.learnedHeroes, keywords, original))
        sb.append("\n\n")

        // === v31 MATH MODEL: выбор лучшего сюжета по смыслу ===
        val introKeywords = if (config.useMathModel) MathPlotModel.extractKeywords(original, 12) else keywords
        val bestPlot = if (config.useMathModel && config.detailedPlots.isNotEmpty()) {
            try {
                MathPlotModel.selectBestPlot(original, config.genre, config.detailedPlots, 0.2)
            } catch (e: Exception) { null }
        } else null

        val plotGraph = if (config.useMathModel) {
            try {
                MathPlotModel.buildPlotGraph(bestPlot, introKeywords, config.genre, original)
            } catch (e: Exception) { emptyList() }
        } else emptyList()

        // Если есть лучший сюжет, добавляем его ключевые слова к основным
        val enrichedKeywords = if (bestPlot != null) {
            (keywords + bestPlot.plot.keywords + bestPlot.matchedKeywords).distinct().take(20)
        } else keywords

        // Диагностика мат.модели. Включается только config.debugInfo: раньше эта строка
        // всегда попадала в начало готового текста и уходила в экспорт (TXT/PDF/DOCX/EPUB).
        if (config.debugInfo && bestPlot != null) {
            sb.append("[Мат.модель: выбран сюжет \"${bestPlot.plot.title.take(30)}\" жанр ${bestPlot.plot.genre} score=${String.format(java.util.Locale.US, "%.2f", bestPlot.score)} совпадения: ${bestPlot.matchedKeywords.joinToString(", ")}]\n\n")
        } else if (config.debugInfo && config.useMathModel && config.detailedPlots.isNotEmpty()) {
            sb.append("[Мат.модель: изучено ${config.detailedPlots.size} сюжетов жанра ${config.genre}, подходящий не найден — генерирую по вступлению]\n\n")
        }

        var curPage = 1
        var outlineIdx = 0
        val outline = if (plotGraph.isNotEmpty()) {
            // Используем граф сюжета из мат.модели
            plotGraph.map { "${it.stage.replaceFirstChar { c -> c.uppercase() }}: ${it.keywords.joinToString(" ")} — ${it.description}" }
        } else {
            createOutline(config.pages, config.genre, heroes, enrichedKeywords, isPhil, original)
        }
        val used = mutableMapOf<String, MutableSet<Int>>()
        val usedTrigrams = mutableSetOf<String>()
        val recentParas = mutableListOf<String>()
        val recentPlaces = mutableListOf(mainPlace)

        // v29: DIVERSE PLOT - seed from original + nanoTime + keywords, to avoid 1 сюжет
        val seedBase = original.hashCode() * 31 + keywords.hashCode() * 17 + original.length
        val diverseRandom = Random(seedBase + System.nanoTime().toInt() + (0..10000).random())

        while (sb.length < targetChars) {
            if (sb.length > original.length + 800 && outlineIdx % 4 == 0 && config.pages > 3) {
                curPage++
                if (curPage <= config.pages) sb.append("\n\n— — — Страница $curPage — — —\n\n")
            }
            if (outlineIdx % 3 == 0 && config.pages >= 2 && outlineIdx < outline.size) {
                val ch = outline[outlineIdx]
                if (ch.startsWith("Глава") || ch.startsWith("Пролог") || ch.startsWith("Эпилог")) {
                    sb.append("\n${ch.uppercase()}\n\n")
                }
            }

            val curPlace = if (config.customPlace.isNotEmpty()) config.customPlace else {
                if (diverseRandom.nextFloat() < 0.7f) {
                    var np: String
                    var att = 0
                    do {
                        np = if (config.useLearned && config.learnedPlaces.isNotEmpty() && diverseRandom.nextFloat() < 0.4f) config.learnedPlaces.random() else randomPlaceFull(config.genre, enrichedKeywords, original, diverseRandom)
                        att++
                    } while (np in recentPlaces.takeLast(3) && att < 6)
                    recentPlaces.add(np)
                    if (recentPlaces.size > 6) recentPlaces.removeAt(0)
                    np
                } else mainPlace
            }

            var para: String
            var att = 0
            do {
                para = if (isPhil) {
                    generatePhilosophicalParagraph(config.style, used, heroes, curPlace, recentParas, config.learnedPhrases, config.learnedHeroes, config.learnedPlaces, enrichedKeywords, original, diverseRandom)
                } else {
                    generateNarrativeParagraph(config.genre, config.style, heroes, enrichedKeywords, curPlace, placeNoun, used, recentParas, config.learnedPhrases, config.learnedHeroes, config.learnedPlaces, original, diverseRandom)
                }
                att++
                val sim = recentParas.takeLast(5).any { jaccard(para, it) > 0.58 }
                val trig = extractTrigrams(para).count { it in usedTrigrams } >= 3
                if (!sim && !trig) break
            } while (att < 10)

            usedTrigrams.addAll(extractTrigrams(para))
            if (usedTrigrams.size > 600) {
                val keep = usedTrigrams.take(350).toSet()
                usedTrigrams.clear()
                usedTrigrams.addAll(keep)
            }

            if (recentParas.isNotEmpty() && !isPhil && diverseRandom.nextFloat() < 0.55f) {
                val connectors = if (config.useLearned && config.learnedPhrases.isNotEmpty() && diverseRandom.nextFloat() < 0.5f) {
                    listOf(config.learnedPhrases.random().replaceFirstChar { it.uppercase() } + " ", config.learnedPhrases.random().replaceFirstChar { it.uppercase() } + ", ")
                } else listOf("Потом ", "Через час ", "Вдруг ", "Но ", "Однако ", "Тем временем ", "К вечеру ", "На рассвете ", "Спустя минуту ", "Между тем ", "Чуть позже ", "В тот же миг ", "А еще ", "Кстати, ", "При этом ", "И тут ")
                if (connectors.none { para.startsWith(it.trim()) }) {
                    para = connectors.random(diverseRandom) + para.replaceFirstChar { it.lowercase() }
                }
            }

            recentParas.add(para)
            if (recentParas.size > 15) recentParas.removeAt(0)

            sb.append(para)
            sb.append("\n\n")
            outlineIdx++
            if (outlineIdx > config.pages * 45) break
        }

        sb.append(generateEnding(config.genre, heroes, enrichedKeywords, mainPlace, isPhil, config.authorName, original))
        var finalText = sb.toString().take(targetChars + 1000)
        finalText = smoothText(finalText)
        // Схлопываем только лишние пробелы и табуляции. Раньше здесь стоял Regex("\\s+"),
        // который вместе с пробелами съедал и переводы строк — абзацы и метки страниц
        // пропадали из экспортируемого текста.
        finalText = finalText
            .replace(Regex("[ \\t\\u00A0]+"), " ")
            .replace(Regex("[ \\t]*\\n[ \\t]*"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .replace(" .", ".")
            .replace(" ,", ",")

        val actualPages = (finalText.length + CHARS_PER_PAGE - 1) / CHARS_PER_PAGE
        return GeneratedStory(title, finalText, finalText.length, actualPages, if (heroes.isEmpty()) listOf("он") else heroes, enrichedKeywords, original)
    }

    private fun detectPhilosophical(text: String, keywords: List<String>): Boolean {
        val lower = text.lowercase()
        val markers = listOf("жизнь", "душа", "сознание", "внутренний голос", "голос", "счастье", "любовь", "мироздание", "человек", "сам с собой", "одиночеств", "мысль", "чувств", "детстве", "учителя", "мораль", "судьба", "выбор", "страх", "свобода", "простын")
        val count = markers.count { lower.contains(it) }
        val hasFirstPerson = lower.contains(" я ") || lower.contains(" мне ") || lower.contains(" меня ") || lower.startsWith("я ")
        return count >= 3 || (count >= 2 && hasFirstPerson) || keywords.contains("жизнь") || keywords.contains("голос")
    }

    private val commonFirstNames = setOf(
        "марина", "алексей", "алекс", "игорь", "анна", "елена", "дмитрий", "сергей", "ольга", "татьяна",
        "иван", "петр", "мария", "наталья", "андрей", "михаил", "юлия", "светлана", "виктор", "николай",
        "владимир", "екатерина", "александр", "александра", "ксения", "дарья", "артем", "артём", "максим",
        "кирилл", "павел", "роман", "денис", "евгений", "константин", "валентина", "галина", "лариса", "оксана", "ирина",
        "наташа", "саша", "леша", "дима", "лена", "катя", "маша", "даша", "женя", "костя", "вова", "паша"
    )

    private val extensiveStopLower = setOf(
        "его", "её", "ее", "еще", "ещё", "может", "твоя", "твои", "твоё", "твой", "твоей", "твоем", "твоих", "твоим",
        "она", "он", "оно", "они", "мы", "вы", "ты", "это", "тот", "та", "те", "так", "вот", "там", "тут", "как", "что", "кто", "где", "когда", "почему", "откуда", "зачем",
        "вечер", "утро", "день", "ночь", "город", "дом", "мир", "жизнь", "время", "человек", "люди", "все", "всё", "просто", "странным", "образом", "точка", "улица", "школу", "институт", "работу", "женщин", "старикам", "учителя",
        "весь", "вся", "всю", "всех", "всем", "сам", "сама", "сами", "само", "самой", "самого", "меня", "мне", "мной", "тебя", "тебе", "тобой", "себя", "себе", "собой", "нас", "нам", "нами", "вас", "вам",
        "был", "была", "были", "будет", "будут", "есть", "быть", "можно", "нужно", "надо", "очень", "даже", "уже", "только", "однако", "потому", "поэтому", "чтобы", "если", "когда", "тогда", "сейчас", "потом",
        "мышечная", "последний", "первый", "второй", "третий", "большой", "маленький", "хороший", "плохой", "новый", "старый", "точный", "четко", "чётко", "холодный", "теплый", "горячий",
        "книга", "история", "рассказ", "роман", "глава", "страница", "лист", "текст", "слово", "фраза"
    )

    private val adjectiveEndings = listOf("ая", "яя", "ое", "ее", "ые", "ие", "ый", "ий", "ой", "ых", "их", "ую", "юю", "ем", "им", "ым", "ом", "его", "ого")

    private fun isLikelyAdjective(word: String): Boolean {
        val lower = word.lowercase()
        if (lower.length < 5) return false
        if (lower in commonFirstNames) return false
        return adjectiveEndings.any { lower.endsWith(it) } && lower !in setOf("мария", "софия", "юлия", "анастасия", "дарья", "ксения")
    }

    private fun extractHeroesV4(text: String): List<String> {
        val sentences = text.split(Regex("[.!?]+")).map { it.trim() }.filter { it.isNotEmpty() }
        val candidates = mutableListOf<String>()
        val firstWordsThatAreNames = mutableListOf<String>()
        val nameFreq = mutableMapOf<String, Int>()
        for (sentence in sentences) {
            val words = sentence.split(Regex("\\s+")).map { it.replace(Regex("[^А-Яа-яЁёA-Za-z-]"), "") }.filter { it.isNotEmpty() }
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
                val lowerCount = Regex("\\b${Regex.escape(lower)}\\b", RegexOption.IGNORE_CASE).findAll(text).count()
                val upperCount = Regex("\\b${Regex.escape(w)}\\b").findAll(text).count()
                if (lowerCount > upperCount * 2) continue
                candidates.add(w)
                nameFreq[lower] = (nameFreq[lower] ?: 0) + 1
            }
        }
        val all = (candidates + firstWordsThatAreNames).distinct()
        val sorted = all.sortedWith(compareByDescending<String> { nameFreq[it.lowercase()] ?: 0 }.thenByDescending { it.lowercase() in commonFirstNames })
        return sorted.filter { it.lowercase() in commonFirstNames || candidates.count { c -> c == it } >= 1 }.take(3)
    }

    private fun extractKeywords(text: String): List<String> {
        val words = text.lowercase().replace(Regex("[^а-яёa-z ]"), " ").split(Regex("\\s+")).filter { it.length > 3 }
        val stopWords = setOf(
            "который", "которая", "которые", "которое", "которых", "потому", "однако", "только", "очень", "сейчас", "тогда", "чтобы", "когда", "такой", "такая", "такое", "такие",
            "было", "была", "были", "будет", "будут", "можно", "нужно", "надо", "через", "против", "между", "самый", "самом", "самой",
            "этот", "эта", "это", "эти", "этого", "этой", "этом", "тот", "та", "то", "те", "того", "той", "том",
            "весь", "вся", "всё", "все", "всех", "всем", "всеми", "всю", "сам", "сама", "сами", "его", "её", "ее", "еще", "ещё", "может", "твоя", "твои",
            "меня", "мне", "мной", "тебя", "тебе", "тобой", "себя", "себе", "собой", "нас", "нам", "нами", "вас", "вам"
        )
        val important = setOf("жизнь", "голос", "любовь", "счастье", "душа", "сердце", "мироздание", "сознание", "детство", "школа", "учитель", "мораль", "судьба", "выбор", "страх", "свобода", "одиночество", "тело", "дыхание", "желание", "выстрел", "цель", "тайна", "правда", "время", "пицца", "ворон", "кофе", "птица", "птицы", "вороны", "пиццы")
        val filtered = words.filter { it !in stopWords || it in important }
        val freq = filtered.groupingBy { it }.eachCount()
        return freq.entries.sortedByDescending { it.value }.map { it.key }.take(18)
    }

    // v29: еще больше пиццы и птиц, diverse
    private fun randomPlaceFull(genre: String, keywords: List<String> = emptyList(), original: String = "", rnd: Random = Random): String {
        val lowerOrig = original.lowercase()
        val hasPizza = lowerOrig.contains("пицца") || keywords.contains("пицца") || keywords.contains("пиццы")
        val hasCrow = lowerOrig.contains("ворон") || keywords.contains("ворон") || keywords.contains("вороны") || keywords.contains("птица") || keywords.contains("птицы")
        val hasCoffee = lowerOrig.contains("кофе") || keywords.contains("кофе")

        val extra = mutableListOf<String>()
        if (hasPizza) extra.addAll(listOf("в пиццерии на углу", "за столиком с пиццей", "на кухне с коробкой пиццы", "у доставки пиццы", "в очереди за пиццей", "в пиццерии где пахнет пепперони", "за столиком с маргаритой", "на кухне с пиццей и крошками"))
        if (hasCrow) extra.addAll(listOf("у гнезда ворона", "на крыше с вороном", "в парке где кружат вороны", "под деревом с вороном", "у окна где сидит ворон", "на крыше с птицами", "в парке с голубями и воронами", "у кормушки для птиц", "на балконе с воронами", "в сквере где птицы клюют крошки пиццы"))
        if (hasCoffee) extra.addAll(listOf("у кофемашины", "в кофейне", "за столиком с кофе", "на кухне с ароматом кофе", "у кофемашины где ворон варит кофе"))

        val base = when (genre) {
            "Фантастика" -> listOf("на краю Галактики", "на станции Орион-9", "в заброшенном космопорте", "у квантового разлома", "в городе из стекла", "на орбите Марса", "в лаборатории времени", "на корабле Аврора", "в куполе на Луне", "у черной дыры", "в нейросети", "в колонии на Титане")
            "Детектив", "Боевик" -> listOf("в старом районе города", "на заброшенном складе", "в ночном кафе", "в архиве полиции", "на крыше", "в подвале", "у окна", "на перекрестке", "в переулке", "на вокзале", "в порту", "в гараже", "в кабинете следователя", "в отделе улик", "на месте преступления", "в дежурной части", "у реки за городом")
            "Роман" -> listOf("на берегу моря", "в маленьком городке у реки", "в старом парке", "в уютном кафе", "на многолюдной улице", "в пустой квартире", "у камина", "на мосту", "в саду с розами", "на набережной", "в библиотеке", "в поезде", "в осеннем сквере", "на кухне с видом на двор")
            "Приключения" -> listOf("в джунглях Амазонки", "на затерянном острове", "на горном перевале", "в пустыне Сахара", "в древнем храме", "в пещере с кристаллами", "на пиратском корабле", "в лабиринте", "на вершине Эвереста", "в долине гейзеров", "в ущелье ветров", "на краю карты")
            "Хоррор" -> listOf("в заброшенной больнице", "в старом доме на холме", "в темном лесу", "в подвале с шепотом", "в туманной деревне", "в склепе", "в доме с зеркалами", "на кладбище", "в лесу без птиц", "в квартире 13", "в коридоре без ламп", "у заколоченного окна")
            "Юмор" -> listOf("в офисе с кофемашиной", "в супермаркете в час пик", "в маршрутке 42", "в чате домового ТСЖ", "в квартире с котом", "на корпоративе", "в очереди в поликлинике", "в лифте с соседом", "на даче у тещи", "в коворкинге", "у кулера с водой", "в пиццерии", "на кухне с пиццей и вороном", "у окна где ворон клюет крошки пиццы", "в пиццерии с птицами", "на крыше где вороны едят пиццу")
            else -> listOf("на берегу моря", "в тихом городе", "на перекрестке дорог", "внутри себя", "на многолюдной улице", "в пустой квартире", "у окна", "на краю мира", "в старом парке", "в кафе", "в вагоне метро", "на остановке под дождём", "в пиццерии", "на крыше с вороном", "за столиком с пиццей", "в парке с птицами", "у кормушки для птиц")
        }
        val all = if (extra.isNotEmpty() && rnd.nextFloat() < 0.65f) extra + base else base + extra
        return all.random(rnd)
    }

    private fun randomPlaceNoun(genre: String, keywords: List<String> = emptyList()): String {
        val hasPizza = keywords.contains("пицца") || keywords.contains("пиццы")
        val hasCrow = keywords.contains("ворон") || keywords.contains("вороны") || keywords.contains("птица") || keywords.contains("птицы")
        val extra = mutableListOf<String>()
        if (hasPizza) extra.addAll(listOf("пиццерия", "коробка пиццы", "кусок пиццы", "доставка пиццы", "пепперони", "маргарита"))
        if (hasCrow) extra.addAll(listOf("ворон", "вороны", "гнездо ворона", "крыша с вороном", "парк с воронами", "птица", "птицы", "кормушка для птиц", "стая птиц"))
        val base = when (genre) {
            "Детектив", "Боевик" -> listOf("заброшенный склад", "старый район", "ночное кафе", "архив полиции", "крыша", "подвал", "перекресток", "кабинет следователя", "переулок", "вокзал", "порт", "гараж", "отдел улик", "место преступления")
            "Роман" -> listOf("берег моря", "маленький городок", "старый парк", "уютное кафе", "многолюдная улица", "пустая квартира", "камин", "мост", "сад", "набережная", "библиотека")
            "Фантастика" -> listOf("космопорт", "станция Орион", "квантовый разлом", "город из стекла", "корабль", "лаборатория", "купол", "колония")
            else -> listOf("тихий город", "перекресток дорог", "пустая квартира", "берег моря", "старый парк", "окно", "кафе", "улица", "пиццерия", "крыша", "ворон", "пицца", "птицы")
        }
        return (base + extra).random()
    }

    private fun generateTitle(original: String, keywords: List<String>, genre: String): String {
        val key = keywords.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Тишина"
        val key2 = keywords.getOrElse(1) { "судьба" }
        val hasPizza = original.lowercase().contains("пицца") || keywords.contains("пицца")
        val hasCrow = original.lowercase().contains("ворон") || keywords.contains("ворон") || keywords.contains("птицы")
        val titles = when (genre) {
            "Боевик" -> listOf("Последний выстрел: $key", "Операция $key2", "Код $key")
            "Философская проза" -> listOf("Внутренний голос: $key", "Когда $key молчит")
            "Детектив" -> listOf("Дело № $key", "Тени $key2", "Улика $key")
            "Фантастика" -> listOf("Станция $key", "Сигнал $key2")
            "Роман" -> listOf("Когда $key молчит", "$key и $key2")
            "Юмор" -> {
                if (hasPizza && hasCrow) listOf("Пицца для ворона: $key", "Вороны и $key2", "Доставка $key", "Крыша, пицца и $key", "Птицы, пицца и $key2", "Ворон, который украл $key")
                else if (hasPizza) listOf("Пицца $key", "Доставка $key2", "Коробка $key и $key2")
                else if (hasCrow) listOf("Ворон $key", "Птицы $key2", "Крыша $key", "Стая $key2")
                else listOf("Офис $key", "Кот $key2")
            }
            else -> {
                if (hasPizza && hasCrow) listOf("Пицца и ворон: $key", "Когда $key пахнет пиццей", "Вороны $key2", "Птицы и $key", "История $key и пиццы")
                else listOf("Когда $key молчит", "$key и $key2", "Эхо $key")
            }
        }
        return titles.random()
    }

    private fun placeHolder(): String = listOf("прошлого", "будущего", "тишины", "ночи", "города", "памяти", "пиццы", "ворона", "птиц").random()

    // v29: еще более разнообразные сюжеты
    private fun createOutline(pages: Int, genre: String, heroes: List<String>, keywords: List<String>, isPhilosophical: Boolean, original: String): List<String> {
        val hash = original.hashCode()
        val rnd = Random(hash + keywords.hashCode())
        val kw1 = keywords.getOrElse(0) { "тайна" }
        val kw2 = keywords.getOrElse(1) { "дорога" }
        val kw3 = keywords.getOrElse(2) { "выбор" }
        val hasPizza = original.lowercase().contains("пицца") || keywords.contains("пицца")
        val hasCrow = original.lowercase().contains("ворон") || keywords.contains("ворон") || keywords.contains("птицы")

        return if (isPhilosophical) {
            listOf(
                "Пролог: Голос $kw1 внутри",
                "Глава 1: Откуда $kw1",
                "Глава 2: $kw2 и $kw3",
                "Встреча: ${if (hasCrow) "Ворон" else "Улица"}",
                "Глава 3: ${if (hasPizza) "Пицца и $kw1" else "Право на $kw1"}",
                "Поворот: $kw2",
                "Глава 4: ${if (hasCrow) "Птицы $kw3" else "Учителя $kw1"}",
                "Глава 5: $kw2",
                "Интерлюдия: ${if (hasPizza) "Пицца и тишина" else "Тишина $kw3"}",
                "Глава 6: ${if (hasCrow) "Вороны на крыше $kw1" else "Право идти $kw2"}",
                "Эпилог: $kw3",
                "Послесловие: $kw1"
            )
        } else {
            val base = when (genre) {
                "Боевик" -> listOf("Пролог: $kw1", "Глава 1: $kw2", "Затишье $kw3", "Глава 2: $kw1 — $kw2", "Разбор $kw3", "Глава 3: $kw1", "Глава 4: $kw2", "Глава 5: $kw3", "Эпилог: $kw1")
                "Детектив" -> listOf("Пролог: Звонок про $kw1", "Глава 1: $kw2 на месте $kw3", "Глава 2: Свидетель $kw1", "Улика: $kw2", "Глава 3: $kw3", "Глава 4: Архив $kw1", "Глава 5: $kw2", "Развязка $kw3", "Эпилог: $kw1")
                "Роман" -> listOf("Пролог: $kw1", "Глава 1: $kw2", "Глава 2: $kw3 и $kw1", "Разговор $kw2", "Глава 3: $kw3", "Глава 4: $kw1", "Глава 5: $kw2", "Эпилог: $kw3")
                "Фантастика" -> listOf("Пролог: Сигнал $kw1", "Глава 1: $kw2", "Глава 2: $kw3", "Глава 3: $kw1", "Глава 4: $kw2", "Глава 5: $kw3", "Эпилог: $kw1")
                "Приключения" -> listOf("Пролог: Карта $kw1", "Глава 1: $kw2", "Глава 2: $kw3", "Глава 3: $kw1", "Глава 4: $kw2", "Глава 5: $kw3", "Эпилог: $kw1")
                "Хоррор" -> listOf("Пролог: Шепот $kw1", "Глава 1: $kw2", "Глава 2: $kw3", "Глава 3: $kw1", "Глава 4: $kw2", "Эпилог: $kw3")
                "Юмор" -> {
                    if (hasPizza && hasCrow) listOf("Пролог: Заказ $kw1", "Глава 1: Пицца $kw2", "Глава 2: Вороны $kw3", "Глава 3: Птицы $kw1", "Глава 4: Доставка $kw2", "Глава 5: Крыша $kw3", "Глава 6: Крошки $kw1", "Эпилог: Пицца и птицы $kw2")
                    else if (hasPizza) listOf("Пролог: Голод $kw1", "Глава 1: Пицца $kw2", "Глава 2: $kw3", "Глава 3: $kw1", "Глава 4: $kw2", "Эпилог: $kw3")
                    else if (hasCrow) listOf("Пролог: Кар $kw1", "Глава 1: Ворон $kw2", "Глава 2: Птицы $kw3", "Глава 3: Гнездо $kw1", "Глава 4: Стая $kw2", "Эпилог: $kw3")
                    else listOf("Пролог: $kw1", "Глава 1: Офис $kw2", "Глава 2: $kw3", "Глава 3: $kw1", "Глава 4: $kw2", "Эпилог: $kw3")
                }
                else -> {
                    if (hasPizza && hasCrow) listOf("Пролог: $kw1", "Глава 1: Пицца $kw2", "Глава 2: Вороны $kw3", "Глава 3: Птицы $kw1", "Глава 4: $kw2", "Глава 5: $kw3 и пицца", "Кульминация: $kw1 и птицы", "Эпилог: $kw2")
                    else listOf("Пролог: $kw1", "Глава 1: $kw2", "Встреча $kw3", "Глава 2: $kw1", "Поворот $kw2", "Глава 3: $kw3", "Раскрытие $kw1", "Кульминация $kw2", "Эпилог: $kw3")
                }
            }
            // v29: 3 варианта outline чтобы не было 1 сюжета
            val variant = rnd.nextInt(3)
            when (variant) {
                0 -> base
                1 -> base.reversed()
                else -> base.shuffled(rnd)
            }
        }
    }

    private fun generateBridge(heroes: List<String>, placeFull: String, isPhilosophical: Boolean, learnedPlaces: List<String>, learnedPhrases: List<String>, learnedHeroes: List<String>, keywords: List<String>, original: String): String {
        val hero = heroes.firstOrNull() ?: learnedHeroes.firstOrNull() ?: "он"
        val phrase = if (learnedPhrases.isNotEmpty() && Random.nextFloat() < 0.5f) learnedPhrases.random() else ""
        val connector = if (phrase.isNotEmpty()) "${phrase.replaceFirstChar { it.uppercase() }}, " else ""
        val kw = keywords.firstOrNull() ?: "тайна"
        val hasPizza = original.lowercase().contains("пицца") || keywords.contains("пицца")
        val hasCrow = original.lowercase().contains("ворон") || keywords.contains("ворон") || keywords.contains("птицы")
        return if (isPhilosophical) {
            val variants = listOf(
                "${connector}С этих строк про $kw. $placeFull — лишь декорация.",
                "${connector}Ты пишешь про $kw. $placeFull вокруг, но главное внутри про $kw."
            )
            variants.random()
        } else {
            val variants = mutableListOf<String>()
            variants.add("${connector}История про $kw началась $placeFull. $hero еще не знал про ${keywords.getOrElse(1){"дорогу"}}.")
            variants.add("$hero $placeFull думал про $kw. День станет поворотным из-за ${keywords.getOrElse(1){"птиц"}}.")
            if (hasPizza) {
                variants.add("$hero заказал пиццу $placeFull. В коробке $kw и записка про ${keywords.getOrElse(1){"ворона"}}.")
                variants.add("Запах пиццы $placeFull — $kw. $hero открыл коробку, а там перо птицы и $kw.")
            }
            if (hasCrow) {
                variants.add("Ворон сидел $placeFull с $kw в клюве. $hero понял — птица знает про ${keywords.getOrElse(1){"пиццу"}}.")
                variants.add("$placeFull. Вороны кружат, $kw внизу, $hero смотрит. Птицы принесли $kw.")
                variants.add("Птицы $placeFull. Стая ворон с $kw. $hero понял — это знак про ${keywords.getOrElse(1){"дорогу"}}.")
            }
            if (hasPizza && hasCrow) {
                variants.add("$placeFull. Пицца, вороны, $kw. $hero между коробкой и птицами. Птицы клюют пиццу, а $kw ждет.")
                variants.add("Пицца на столе $placeFull, вороны на подоконнике, $kw в воздухе. $hero делит пиццу с птицами.")
            }
            variants.random()
        }
    }

    private fun generatePhilosophicalParagraph(style: String, used: MutableMap<String, MutableSet<Int>>, heroes: List<String>, placeFull: String, recentParagraphs: List<String>, learnedPhrases: List<String>, learnedHeroes: List<String>, learnedPlaces: List<String>, keywords: List<String>, original: String, rnd: Random): String {
        val hero = heroes.firstOrNull() ?: learnedHeroes.firstOrNull() ?: "ты"
        val place = if (learnedPlaces.isNotEmpty() && rnd.nextFloat() < 0.4f) learnedPlaces.random() else placeFull
        val phraseIntro = if (learnedPhrases.isNotEmpty() && rnd.nextFloat() < 0.5f) learnedPhrases.random().replaceFirstChar { it.uppercase() } + ". " else ""
        val kw = keywords.randomOrNull() ?: "жизнь"
        val key = "phil_${original.hashCode() % 7}"
        val paragraphs = listOf(
            "${phraseIntro}Голос про $kw $place. $hero слышит птиц — вороны каркают $kw.",
            "Иногда $kw — это пицца. Кусок за куском $place. $hero делит $kw с птицами.",
            "Вспомни $kw $place. $hero видел ворона с $kw в клюве. Птица знала всё.",
            "Голос про $kw — это ворон внутри $place. Каркает $kw, когда хочешь тишины.",
            "Представь $kw как стаю птиц $place. $hero смотрит, как птицы несут $kw.",
            "$kw $place. $hero ест пиццу, птицы рядом, всем норм про $kw.",
            "Попробуй $kw завтра $place. Ворон с пиццей. Птица делится $kw.",
            "Свобода — $kw $place. Пицца, птицы, $hero."
        )
        val usedSet = used.getOrPut(key) { mutableSetOf() }
        if (usedSet.size >= paragraphs.size) usedSet.clear()
        var idx: Int
        var attempts = 0
        var text: String
        do {
            val available = paragraphs.indices.filter { it !in usedSet }
            idx = if (available.isNotEmpty()) available.random(rnd) else rnd.nextInt(paragraphs.size)
            text = paragraphs[idx]
            attempts++
        } while (attempts < 6 && recentParagraphs.any { jaccard(text, it) > 0.55 })
        usedSet.add(idx)
        return when (style) {
            "Лаконичный" -> text.split(". ").take(3).joinToString(". ") + "."
            "Поэтический" -> text + " Воздух как перо птицы."
            else -> text
        }
    }

    // v29: FIX 1 сюжет + птицы + пицца + кофе птица а не она
    private fun generateNarrativeParagraph(genre: String, style: String, heroes: List<String>, keywords: List<String>, placeFull: String, placeNoun: String, used: MutableMap<String, MutableSet<Int>>, recentParagraphs: List<String>, learnedPhrases: List<String>, learnedHeroes: List<String>, learnedPlaces: List<String>, original: String, rnd: Random): String {
        val hero = heroes.firstOrNull() ?: learnedHeroes.firstOrNull() ?: "он"
        val heroCap = hero.replaceFirstChar { it.uppercase() }
        val hero2 = heroes.getOrElse(1) { learnedHeroes.getOrElse(1) { "она" } }
        val kw = keywords.randomOrNull() ?: "тайна"
        val kw2 = keywords.getOrElse(1) { "дорога" }
        val kw3 = keywords.getOrElse(2) { "выбор" }
        val hasPizza = original.lowercase().contains("пицца") || keywords.contains("пицца") || rnd.nextFloat() < 0.3f
        val hasCrow = original.lowercase().contains("ворон") || keywords.contains("ворон") || keywords.contains("птицы") || keywords.contains("птица") || rnd.nextFloat() < 0.4f

        val pools = mapOf(
            "Боевик" to listOf(
                "${heroCap} $placeFull: $kw и $kw2. Пицца в рюкзаке, ворон над головой с $kw3.",
                "Выстрел про $kw $placeFull. $heroCap, пицца и птицы — всё в одной точке $kw2.",
                if (hasCrow) "Ворон каркнул $kw $placeFull. Стая птиц про $kw2. $heroCap понял — $kw3 рядом." else "Ветер $kw $placeFull.",
                if (hasPizza) "Коробка пиццы $placeFull с $kw внутри. $heroCap открыл — там $kw2 и перо птицы." else "Записка $kw $placeFull."
            ),
            "Детектив" to listOf(
                "Дождь $placeFull. $heroCap, фото $kw, пицца с $kw2, вороны за окном с $kw3.",
                "Архив $kw $placeFull. Папка $kw2, внутри перья птиц и чек от пиццы с $kw3.",
                "Ворон принес улику $placeFull: $kw. Птица, а не она, сделала кофе! Стая птиц видела $kw2.",
                "Свидетель — ворон $placeFull. Птица каркнула $kw. $heroCap записал $kw2 и $kw3.",
                "Пицца $placeFull — улика $kw. Вороны ели $kw2, а $heroCap искал $kw3."
            ),
            "Роман" to listOf(
                "Письмо $kw $placeFull. $heroCap и $hero2, пицца, птицы, $kw2.",
                if (hasCrow) "Ворон принес кофе $placeFull, а не она. Птица сделала кофе с $kw. Стая птиц наблюдала $kw2." else "Кофе $placeFull от утра с $kw.",
                "Чашку оставил ворон $placeFull, а не она. Птица, а не она, сделала кофе. $heroCap пил $kw с птицами.",
                "Любовь — $kw и $kw2 $placeFull. Пицца на двоих, крошки — птицам, $kw3 — всем.",
                "Пицца $placeFull. $heroCap и $hero2, $kw, птицы едят $kw2."
            ),
            "Фантастика" to listOf(
                "Корабль $placeFull. Экраны $kw, ворон на панели с $kw2, птицы в отсеке с $kw3.",
                "Сигнал $kw $placeFull от стаи птиц. Вороны шлют $kw2 и пиццу.",
                "Пицца в невесомости $placeFull. $heroCap ловит $kw, птицы — $kw2.",
                "Нейросеть голосом ворона $placeFull: $kw — это $kw2, птицы — $kw3."
            ),
            "Юмор" to listOf(
                "Пицца $placeFull. $heroCap заказал $kw, привезли $kw2 и стаю птиц с вороном.",
                "Ворон ест пиццу $placeFull. $heroCap: ты кто? Ворон: я $kw, птицы — $kw2, пицца — $kw3.",
                "Офис $placeFull. Кофе сделал ворон, а не она! Птица, птицы, стая — все варят кофе с $kw.",
                "Маршрутка с пиццей и птицами $placeFull. $heroCap с $kw, ворон с $kw2, птицы с $kw3.",
                "Чат ТСЖ: кто заказал пиццу с $kw? Вороны ответили: мы, птицы. И съели $kw2 и $kw3.",
                "Пицца остыла, вороны согрелись $placeFull. $heroCap и птицы едят $kw.",
                "Ворон принес кофе $placeFull. Не она, а птица! Птицы, стая, все делают кофе с $kw и $kw2."
            )
        )

        val generalPool = mutableListOf<String>()
        generalPool.addAll(listOf(
            "${heroCap} $placeFull с пиццей и птицами. В голове $kw, над головой вороны с $kw2, в руках $kw3.",
            "Вечер $placeFull. $heroCap пишет $kw, птицы клюют $kw2 и $kw3.",
            "Тишина $placeFull. $kw, запах пиццы, карканье ворон про $kw2 и $kw3.",
            "$heroCap вспомнил $kw $placeFull. Птицы кивнули про $kw2.",
            "Воздух $kw и пиццы $placeFull. $heroCap и птицы вдыхают $kw2 и $kw3.",
            // КОФЕ ДЕЛАЕТ ПТИЦА А НЕ ОНА - v29 FIX
            "Кофе $placeFull сделал ворон, а не она. Птица принесла чашку в клюве. $heroCap пил $kw с птицами.",
            "Кофе сделал не она, а птицы. Стая ворон принесла $kw $placeFull. $heroCap пил с перьями и $kw2.",
            "Она оставила кофе? Нет! $placeFull кофе сделали птицы. Вороны, а не она! $heroCap удивился — птицы варят лучше.",
            "Чашка кофе $placeFull от птиц, а не от неё. Вороны принесли $kw. Птица, а не она, сделала кофе.",
            "Кофемашина сломалась $placeFull. Кофе сделали птицы. Вороны, а не она! $heroCap пил $kw и $kw2.",
            "Запах кофе $placeFull от клюва ворона, а не от её рук. Птицы принесли $kw. Не она, а птица!",
            "Кофе от птицы $placeFull. Ворон, а не она, сделал кофе. Стая птиц одобрила $kw.",
            "Птицы сделали кофе $placeFull. Не она, а вороны! $heroCap пил кофе с $kw и крошками пиццы.",
            // ПИЦЦА + ПТИЦЫ
            "Пицца $placeFull. $heroCap открыл коробку $kw, а сверху стая птиц ест $kw2 и $kw3.",
            "Вороны украли пиццу $placeFull. $heroCap гнался за $kw, птицы унесли $kw2.",
            "Коробка пиццы $placeFull со следами клювов птиц. Вороны съели $kw, осталось $kw2.",
            "Пицца и птицы $placeFull. $heroCap делит $kw со стаей. Птицы клюют $kw2, $heroCap — $kw3.",
            "Доставка пиццы $placeFull — вороны. Птицы, а не курьер! Стая в клювах несет $kw.",
            "Вороны на крыше $placeFull с пиццей $kw. $heroCap снизу про $kw2, птицы про $kw3.",
            "Пицца остыла $placeFull, а стая птиц согрелась. $heroCap и птицы едят $kw и $kw2.",
            "Крошки пиццы $placeFull клюют птицы. $heroCap и вороны делят $kw и $kw2.",
            "$heroCap заказал пиццу с $kw $placeFull. Привезли $kw2 и стаю птиц.",
            "Птицы, пицца и $kw $placeFull. $heroCap между коробкой и стаей. $kw2 ждет.",
            "$placeFull. Пицца, птицы, $kw, $kw2, $kw3. $heroCap понял — это сюжет.",
            "Пыль и крошки пиццы $placeFull. Перья птиц и $kw. $heroCap нашел $kw2.",
            "День $heroCap $placeFull с пиццей и птицами. $kw и $kw2 в небе.",
            "$heroCap на ступеньке $placeFull с пиццей. Стая птиц с $kw ест $kw2.",
            "Ночь $placeFull. Пицца, птицы, $kw, $kw2, $kw3 у $heroCap.",
            "$heroCap открыл блокнот $placeFull: пицца, птицы, $kw, $kw2. Пишет $kw3.",
            "Ветер $kw и пиццы $placeFull. Птицы принесли $kw2 и $kw3.",
            "Дверь $placeFull: за ней птицы с пиццей и $kw. $heroCap впустил $kw2.",
            "Записка $placeFull: ушел с птицами за пиццей, ищу $kw и $kw2.",
            "Свет $placeFull: пицца светится, птицы светятся, $kw светится.",
            "$heroCap $placeFull. $kw начинается. Пицца и птицы тоже.",
            "Дождь $placeFull стучит $kw. Птицы слушают $kw2, пицца остывает с $kw3.",
            "Закат как пицца $placeFull. $heroCap ест $kw глазами, птицы — $kw2.",
            "Книга $placeFull про $kw. Рядом пицца и перья птиц про $kw2 и $kw3.",
            "Дорога пуста $placeFull, но крошки пиццы и перья птиц ведут к $kw и $kw2."
        ))

        if (learnedPhrases.isNotEmpty()) {
            val lp = learnedPhrases.random(rnd)
            generalPool.add("$heroCap $placeFull вспомнил: $lp. Про $kw и птиц.")
        }

        val genreKey = when (genre) {
            "Боевик", "Детектив", "Роман", "Фантастика", "Приключения", "Хоррор", "Юмор" -> genre
            else -> "general"
        }
        val pool = pools[genreKey] ?: generalPool
        val finalPool = if (genreKey == "general") generalPool else pool + generalPool.shuffled(rnd).take(15)

        // v29: diverse key with original hash + keywords + rnd to avoid 1 сюжет
        val usedKey = genreKey + "_" + original.hashCode() + "_" + keywords.take(3).joinToString("_") + "_" + (original.length % 10)
        val usedSet = used.getOrPut(usedKey) { mutableSetOf() }
        if (usedSet.size >= finalPool.size) usedSet.clear()
        var idx: Int
        var attempts = 0
        var text: String
        do {
            val available = finalPool.indices.filter { it !in usedSet }
            idx = if (available.isNotEmpty()) available.random(rnd) else rnd.nextInt(finalPool.size)
            text = finalPool[idx]
            attempts++
        } while (attempts < 8 && recentParagraphs.any { jaccard(text, it) > 0.55 })
        usedSet.add(idx)

        if (rnd.nextFloat() < 0.3f) {
            val dialogues = listOf(
                " — $kw? — спросила $hero2.\n— Да, и пицца с птицами, — ответил $heroCap. — $kw2 и $kw3.",
                " — Кофе от птицы? — Да, не она, а вороны. Птицы сделали $kw $placeNoun.",
                " — Пицца с $kw? — Да, и стая птиц. — Кар! — согласились птицы про $kw2.",
                " — Вороны и $kw? — Да, птицы принесли $kw2 и пиццу $kw3 $placeNoun."
            )
            if (rnd.nextBoolean()) text += "\n\n" + dialogues.random(rnd)
        }

        text = when (style) {
            "Лаконичный" -> text.split(". ").take(2).joinToString(". ") + "."
            "Поэтический" -> text + " Свет как сыр на пицце, птицы как перья."
            "Драматичный" -> text + " Сердце как клюв ворона про $kw."
            "Ироничный" -> text + " Доставка пиццы птицами — $kw."
            else -> text
        }
        return text
    }

    private fun generateEnding(genre: String, heroes: List<String>, keywords: List<String>, placeFull: String, isPhilosophical: Boolean, authorName: String, original: String): String {
        val author = if (authorName.isNotBlank()) authorName else "Zakharevich Igor"
        val kw = keywords.firstOrNull() ?: "тайна"
        val kw2 = keywords.getOrElse(1) { "дорога" }
        val kw3 = keywords.getOrElse(2) { "выбор" }
        val heroCap = heroes.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Он"
        val hasPizza = original.lowercase().contains("пицца") || keywords.contains("пицца")
        val hasCrow = original.lowercase().contains("ворон") || keywords.contains("ворон") || keywords.contains("птицы")
        return if (isPhilosophical) {
            """

И вот ты $placeFull с $kw и $kw2. Вороны, птицы, пицца.

$kw — это не она, а птицы. Кофе сделали птицы, а не она. Птицы принесли $kw.

— Конец —

Сгенерировано в ZI Writer v29 BIRDS+PIZZA • $author • $kw • птицы, а не она, сделали кофе
""".trimIndent()
        } else {
            val ending = when (genre) {
                "Боевик" -> """
                    $placeFull. $kw, пицца, птицы, $kw2. $heroCap понял — $kw3.

                    Гильза, крошка пиццы, перо птицы. Вороны каркнули $kw.

                    — Конец —

                    Сгенерировано в ZI Writer v29 BIRDS+PIZZA • $author • $kw • птицы сделали кофе, а не она
                """.trimIndent()
                "Юмор" -> """
                    Пицца съедена $placeFull, птицы наелись $kw, $placeFull опустел.

                    Кофе сделали птицы, а не она! Стая ворон принесла кофе с $kw и $kw2.

                    Птицы, а не она, сделали кофе. Пицца и вороны — $kw.

                    — Конец —

                    Сгенерировано в ZI Writer v29 BIRDS+PIZZA • $author • $kw • пицца, птицы, кофе от птиц
                """.trimIndent()
                else -> """
                    $placeFull. $kw, пицца, птицы, $kw2. $heroes.

                    Кофе сделали птицы, а не она. Вороны принесли чашку в клювах. $kw пахла пиццей и кофе от птиц.

                    Птицы, а не она, сделали кофе. Пицца и вороны — лучший сюжет про $kw.

                    — Конец —

                    Сгенерировано в ZI Writer v29 BIRDS+PIZZA • $author • $kw • ${heroes.joinToString(", ")} • птицы сделали кофе, а не она • пицца, вороны
                """.trimIndent()
            }
            "\n\n$ending"
        }
    }
}
