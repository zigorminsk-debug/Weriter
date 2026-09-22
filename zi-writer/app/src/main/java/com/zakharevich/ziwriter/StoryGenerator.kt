package com.zakharevich.ziwriter

import kotlin.random.Random

/**
 * StoryGenerator v35 — генератор на основе РЕАЛЬНЫХ изученных текстов
 *
 * Ключевое отличие от v33/v34:
 * - НЕ использует шаблоны ("ночь встал посмотрел" = бред)
 * - БЕРЁТ РЕАЛЬНЫЕ предложения из learnedPhrases (изученные книги с Флибусты)
 * - Строит текст как последовательность настоящих литературных фраз
 * - Адаптирует: заменяет имена, корректирует род глаголов
 *
 * Результат: связный текст в стиле изученных авторов.
 */

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

    // === ОПРЕДЕЛЕНИЕ ЖАНРА ===
    fun detectGenreStatic(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("выстрел") || lower.contains("оружие") || lower.contains("спецназ") -> "Боевик"
            lower.contains("улика") || lower.contains("детектив") || lower.contains("расследование") -> "Детектив"
            lower.contains("любовь") || lower.contains("сердце") || lower.contains("поцелуй") -> "Роман"
            lower.contains("космос") || lower.contains("корабль") || lower.contains("галактика") -> "Фантастика"
            lower.contains("приключен") || lower.contains("остров") || lower.contains("сокровищ") -> "Приключения"
            lower.contains("ужас") || lower.contains("хоррор") || lower.contains("призрак") -> "Хоррор"
            lower.contains("юмор") || lower.contains("смешно") || lower.contains("комедия") -> "Юмор"
            lower.contains("философ") || lower.contains("душа") || lower.contains("смысл жизни") -> "Философская проза"
            else -> "Драма"
        }
    }

    // === ИЗВЛЕЧЕНИЕ КЛЮЧЕВЫХ СЛОВ ===
    private fun extractKeywords(text: String): List<String> {
        val words = text.lowercase()
            .replace(Regex("[^а-яёa-z ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length in 4..20 }

        val stopWords = setOf(
            "который", "которая", "которые", "которое", "которых", "потому", "однако",
            "только", "очень", "сейчас", "тогда", "чтобы", "когда", "такой", "такая",
            "такое", "такие", "было", "была", "были", "будет", "будут", "можно",
            "нужно", "надо", "через", "против", "между", "самый", "самом", "самой",
            "этот", "эта", "это", "эти", "этого", "этой", "этом", "тот", "та", "то",
            "те", "того", "той", "том", "весь", "вся", "всё", "все", "всех", "всем",
            "всеми", "всю", "сам", "сама", "сами", "его", "её", "ее", "еще", "ещё",
            "может", "твоя", "твои", "меня", "мне", "мной", "тебя", "тебе", "тобой",
            "себя", "себе", "собой", "нас", "нам", "нами", "вас", "вам"
        )

        val filtered = words.filter { it !in stopWords }
        return filtered.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { it.key }
            .take(20)
    }

    // === ИЗВЛЕЧЕНИЕ ГЕРОЕВ ИЗ ТЕКСТА ===
    private val commonFirstNames = setOf(
        "марина", "алексей", "алекс", "игорь", "анна", "елена", "дмитрий", "сергей",
        "ольга", "татьяна", "иван", "петр", "мария", "наталья", "андрей", "михаил",
        "юлия", "светлана", "виктор", "николай", "владимир", "екатерина", "александр",
        "александра", "ксения", "дарья", "артем", "артём", "максим", "кирилл", "павел",
        "роман", "денис", "евгений", "константин", "валентина", "гал", "оксана", "ирина"
    )

    private fun extractHeroesFromText(text: String): List<String> {
        val sentences = text.split(Regex("[.!?]+")).map { it.trim() }.filter { it.isNotEmpty() }
        val candidates = mutableMapOf<String, Int>()

        for (sentence in sentences.take(50)) {
            val words = sentence.split(Regex("\\s+"))
            for (word in words) {
                val clean = word.replace(Regex("[^А-Яа-яЁёA-Za-z-]"), "")
                if (clean.length < 3 || clean.length > 15) continue
                if (!clean.matches(Regex("[А-ЯЁ][а-яё]{2,}"))) continue

                val lower = clean.lowercase()
                if (lower in commonFirstNames) {
                    candidates[clean] = (candidates[clean] ?: 0) + 2
                } else {
                    val countInText = Regex("\\b${Regex.escape(clean)}\\b", RegexOption.IGNORE_CASE)
                        .findAll(text).count()
                    if (countInText <= 10) {
                        candidates[clean] = (candidates[clean] ?: 0) + 1
                    }
                }
            }
        }

        return candidates.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(3)
    }

    // === ОПРЕДЕЛЕНИЕ ПОЛА ИМЕНИ ===
    private fun detectGender(name: String): String {
        val lower = name.lowercase()
        return when {
            lower in setOf("никита", "илья", "лука", "фома", "савва") -> "m"
            lower.endsWith("а") || lower.endsWith("я") -> "f"
            lower.endsWith("ина") || lower.endsWith("ёна") -> "f"
            else -> "m"
        }
    }

    // === ГЛАВНАЯ ФУНКЦИЯ ГЕНЕРАЦИИ ===

    fun generate(config: GenerationConfig): GeneratedStory {
        val rawOriginal = config.originalText.trim()
        val original = if (config.fixGrammar) GrammarCorrector.correct(rawOriginal) else rawOriginal
        val targetChars = config.pages * CHARS_PER_PAGE

        // Извлекаем ключевые слова и героев
        val keywords = extractKeywords(original)
        val extractedHeroes = extractHeroesFromText(original)
        val heroes = when {
            config.customHeroes.isNotEmpty() -> config.customHeroes
            config.useLearned && config.learnedHeroes.isNotEmpty() -> {
                val learned = config.learnedHeroes.filter { it.length in 3..20 }.shuffled().take(2)
                (extractedHeroes + learned).distinct().take(3).ifEmpty { extractedHeroes }
            }
            else -> extractedHeroes
        }

        val mainHero = heroes.firstOrNull() ?: "Герой"
        val heroGender = detectGender(mainHero)

        // Заголовок
        val title = if (config.bookTitle.isNotBlank()) {
            config.bookTitle.trim()
        } else {
            generateTitle(original, keywords, config.genre)
        }

        // === ГЕНЕРАЦИЯ ПРОДОЛЖЕНИЯ ===
        val sb = StringBuilder()
        sb.append(original)
        sb.append("\n\n")

        if (config.useLearned) {
            // v35: основа текста — РЕАЛЬНЫЕ предложения изученных книг (learnedTemplates),
            // короткие коннекторы ("тем временем", "вдруг") служат связками между ними
            val continuation = generateFromLearned(
                keywords = keywords,
                templates = config.learnedTemplates,
                connectors = config.learnedPhrases.filter { it.split(Regex("\\s+")).size in 1..3 },
                targetChars = targetChars - original.length,
                hero = mainHero,
                heroGender = heroGender,
                rng = Random(original.hashCode() + System.nanoTime().toInt())
            )
            sb.append(continuation ?: generateMinimalContinuation(original, keywords, mainHero, heroGender))
        } else {
            // Минимальное продолжение если изучение отключено
            sb.append(generateMinimalContinuation(original, keywords, mainHero, heroGender))
        }

        // Концовка
        sb.append("\n\n— Конец —\n")

        // Форматируем
        var finalText = sb.toString()
        finalText = formatText(finalText)

        val actualPages = (finalText.length + CHARS_PER_PAGE - 1) / CHARS_PER_PAGE

        return GeneratedStory(
            title = title,
            fullText = finalText,
            chars = finalText.length,
            pages = actualPages,
            heroes = heroes,
            keywords = keywords,
            correctedOriginal = original
        )
    }

    // === ГЕНЕРАЦИЯ ИЗ РЕАЛЬНЫХ ИЗУЧЕННЫХ ФРАЗ ===
    /**
     * Генерация из РЕАЛЬНЫХ предложений изученных книг.
     *
     * @param templates настоящие литературные предложения (learnedTemplates, 30-250 симв.)
     * @param connectors короткие связки ("тем временем", "вдруг", "однако") — learnedPhrases
     * @return текст продолжения или null, если нет подходящего материала
     */
    private fun generateFromLearned(
        keywords: List<String>,
        templates: List<String>,
        connectors: List<String>,
        targetChars: Int,
        hero: String,
        heroGender: String,
        rng: Random
    ): String? {
        val sb = StringBuilder()
        var currentChars = 0
        var lastKeywords = keywords.take(5).toSet()
        val usedPhrases = mutableSetOf<String>()

        // Фильтр качества: только полноценные литературные предложения
        val validPhrases = templates.filter { phrase ->
            phrase.length in 40..400 &&
            phrase.count { it.isLetter() } > phrase.length * 0.55 &&
            phrase.split(Regex("\\s+")).size in 4..40 &&
            !phrase.contains("http") && !phrase.contains("@") && !phrase.contains("�") &&
            phrase.contains(Regex("[А-Яа-яЁё]"))
        }.distinct()

        if (validPhrases.isEmpty()) return null

        // Слова, с которых безопасно начинать предложение после коннектора
        // (строчная буква); имена и незнакомые слова оставляем с заглавной
        val lowercaseSafe = setOf(
            "она", "он", "они", "оно", "мы", "вы", "я", "ты", "все", "всё", "это",
            "этот", "эта", "тот", "в", "на", "за", "по", "у", "но", "а", "и",
            "никто", "ничто", "вдруг", "тут", "затем", "потом", "однако", "казалось",
            "оказалось", "наконец", "сначала", "поток", "день", "ночь", "утро", "вечер"
        )

        // Собираем абзацы по 3-5 предложений, связанных по ключевым словам
        val paragraph = mutableListOf<String>()
        var attempts = 0

        fun flushParagraph() {
            if (paragraph.isEmpty()) return
            sb.append(paragraph.joinToString(" "))
            sb.append("\n\n")
            paragraph.clear()
        }

        while (currentChars < targetChars && attempts < validPhrases.size * 4) {
            attempts++

            // Приоритет: фраза, связанная по смыслу с предыдущей
            val candidate = selectNextPhrase(validPhrases, lastKeywords, usedPhrases, rng)
                ?: validPhrases.filter { it !in usedPhrases }.randomOrNull(rng)

            if (candidate == null) {
                usedPhrases.clear()
                continue
            }

            usedPhrases.add(candidate)
            lastKeywords = extractKeywordsFromPhrase(candidate).take(5).toSet()
            var adapted = adaptPhrase(candidate, hero, heroGender)

            // Связка-коннектор в начале нового абзаца (35% случаев)
            if (paragraph.isEmpty() && sb.isNotBlank() && rng.nextDouble() < 0.35 && connectors.isNotEmpty()) {
                val conn = connectors.random(rng).replaceFirstChar { it.uppercase() }
                val firstWord = adapted.split(Regex("[\\s,.!?]+")).firstOrNull()?.lowercase() ?: ""
                val lowered = if (firstWord in lowercaseSafe) {
                    adapted.replaceFirstChar { it.lowercase() }
                } else adapted
                adapted = if (conn.endsWith(",") || conn.endsWith(".") || conn.endsWith("!") || conn.endsWith("?")) {
                    "$conn $lowered"
                } else {
                    "$conn, $lowered"
                }
            }

            paragraph.add(adapted)
            currentChars += adapted.length + 1

            // Закрываем абзац
            if (paragraph.size >= listOf(3, 4, 5).random(rng)) {
                flushParagraph()
            }

            // Контроль повторов
            if (usedPhrases.size > validPhrases.size * 0.8) {
                usedPhrases.clear()
            }
        }

        flushParagraph()

        return if (sb.isBlank()) null else sb.toString()
    }

    // === ВЫБОР СЛЕДУЮЩЕЙ ФРАЗЫ ПО КЛЮЧЕВЫМ СЛОВАМ ===
    private fun selectNextPhrase(
        phrases: List<String>,
        currentKeywords: Set<String>,
        usedPhrases: Set<String>,
        rng: Random
    ): String? {
        if (currentKeywords.isEmpty()) return null

        // Ищем фразы, содержащие хотя бы одно из текущих ключевых слов
        val candidates = phrases.filter { phrase ->
            phrase !in usedPhrases &&
            currentKeywords.any { kw -> phrase.lowercase().contains(kw) }
        }

        return if (candidates.isNotEmpty()) {
            // Выбираем по релевантности (больше совпадений = выше приоритет)
            candidates.maxByOrNull { phrase ->
                currentKeywords.count { kw -> phrase.lowercase().contains(kw) }
            } ?: candidates.random(rng)
        } else null
    }

    // === АДАПТАЦИЯ ФРАЗЫ: замена имён и рода глаголов ===
    private fun adaptPhrase(phrase: String, hero: String, heroGender: String): String {
        var result = phrase

        // Заменяем распространённые имена на героя пользователя
        val commonNames = listOf(
            "Анна", "Мария", "Елена", "Ольга", "Татьяна", "Наталья", "Юлия",
            "Алексей", "Дмитрий", "Иван", "Сергей", "Андрей", "Михаил", "Виктор"
        )
        for (name in commonNames) {
            result = result.replace(Regex("\\b$name\\b"), hero)
        }

        // Корректируем род глаголов
        result = adjustVerbGender(result, heroGender)

        return result
    }

    // === КОРРЕКТИРОВКА ГЛАГОЛОВ ПО РОДУ ===
    private fun adjustVerbGender(text: String, gender: String): String {
        if (gender == "f") {
            // Мужской род → женский
            var result = text
            result = result.replace(Regex("\\b(пошёл|стоял|увидел|сказал|спросил|ответил|подумал|понял|вспомнил|вышел|вошёл|сел|лёг|встал|подошёл|остановился|обернулся|улыбнулся|задумался|замолчал|кивнул)\\b")) { match ->
                when (match.value) {
                    "пошёл" -> "пошла"
                    "стоял" -> "стояла"
                    "увидел" -> "увидела"
                    "сказал" -> "сказала"
                    "спросил" -> "спросила"
                    "ответил" -> "ответила"
                    "подумал" -> "подумала"
                    "понял" -> "поняла"
                    "вспомнил" -> "вспомнила"
                    "вышел" -> "вышла"
                    "вошёл" -> "вошла"
                    "сел" -> "села"
                    "лёг" -> "легла"
                    "встал" -> "встала"
                    "подошёл" -> "подошла"
                    "остановился" -> "остановилась"
                    "обернулся" -> "обернулась"
                    "улыбнулся" -> "улыбнулась"
                    "задумался" -> "задумалась"
                    "замолчал" -> "замолчала"
                    "кивнул" -> "кивнула"
                    else -> match.value + "а"
                }
            }
            return result
        }
        return text
    }

    // === ИЗВЛЕЧЕНИЕ КЛЮЧЕВЫХ СЛОВ ИЗ ФРАЗЫ ===
    private fun extractKeywordsFromPhrase(phrase: String): List<String> {
        val words = phrase.lowercase()
            .replace(Regex("[^а-яёa-z ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length in 4..15 }

        val stopWords = setOf(
            "который", "которая", "которые", "потому", "однако", "только", "очень",
            "было", "была", "были", "будет", "можно", "нужно", "надо", "через",
            "этот", "эта", "это", "тот", "та", "то", "весь", "вся", "всё", "все"
        )

        return words.filter { it !in stopWords }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { it.key }
    }

    // === МИНИМАЛЬНОЕ ПРОДОЛЖЕНИЕ (если нет изученных фраз) ===
    private fun generateMinimalContinuation(
        original: String,
        keywords: List<String>,
        hero: String,
        heroGender: String
    ): String {
        val keyword = keywords.firstOrNull() ?: "тайна"
        val ending = if (heroGender == "f") "а" else ""
        val stood = verbPast("стоял", heroGender)
        val realized = verbPast("понял", heroGender)
        val ponder = if (heroGender == "f") "размышляла" else "размышлял"
        val pronoun = if (heroGender == "f") "Она" else "Он"

        return buildString {
            append("$hero $stood и думал$ending о $keyword. ")
            if (keywords.size > 1) {
                append("Мысли снова и снова возвращались к слову «${keywords[1]}». ")
            }
            append("Сначала всё казалось простым, но чем дольше $hero $ponder, тем больше вопросов возникало. ")
            append("Ответ был где-то рядом — нужно было лишь решиться сделать первый шаг. ")
            val pulledTogether = verbPast("взял", heroGender)
            append("$pronoun $realized это и $pulledTogether себя в руки.")
        }
    }

    private fun verbPast(base: String, gender: String): String {
        return if (gender == "f") {
            when (base) {
                "пошёл" -> "пошла"
                "стоял" -> "стояла"
                "увидел" -> "увидела"
                "сказал" -> "сказала"
                "спросил" -> "спросила"
                "ответил" -> "ответила"
                "подумал" -> "подумала"
                "понял" -> "поняла"
                "вспомнил" -> "вспомнила"
                else -> base + "а"
            }
        } else base
    }

    // === ГЕНЕРАЦИЯ ЗАГОЛОВКА ===
    private fun generateTitle(original: String, keywords: List<String>, genre: String): String {
        val key = keywords.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Тишина"
        val key2 = keywords.getOrElse(1) { "судьба" }

        return when (genre) {
            "Боевик" -> "Последний выстрел: $key"
            "Детектив" -> "Дело № $key"
            "Фантастика" -> "Станция $key"
            "Роман" -> "Когда $key молчит"
            "Приключения" -> "Путь к $key"
            "Хоррор" -> "Шёпот $key"
            "Юмор" -> "Невероятный $key"
            "Философская проза" -> "Голос $key"
            else -> "$key и $key2"
        }
    }

    // === ФОРМАТИРОВАНИЕ ТЕКСТА ===
    private fun formatText(text: String): String {
        var result = text
        result = result.replace(Regex(" {2,}"), " ")
        result = result.replace(Regex("\n{3,}"), "\n\n")
        result = result.replace(Regex(" \n"), "\n")
        result = result.replace(Regex("\\s+([.!?,:;])"), "$1")
        result = result.replace(Regex("([.!?])([^\\s\\n])"), "$1 $2")
        result = result.replace(Regex(",([^\\s])"), ", $1")
        return result.trim()
    }
}
