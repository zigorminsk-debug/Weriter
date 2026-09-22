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
        // (начинаются с заглавной или тире — диалог; без мусора)
        val validPhrases = templates.filter { phrase ->
            phrase.length in 40..400 &&
            phrase.count { it.isLetter() } > phrase.length * 0.55 &&
            phrase.split(Regex("\\s+")).size in 4..40 &&
            !phrase.contains("http") && !phrase.contains("@") && !phrase.contains("�") &&
            phrase.contains(Regex("[А-Яа-яЁё]")) &&
            (phrase.first().isUpperCase() || phrase.first() == '—')
        }.distinct()

        if (validPhrases.isEmpty()) return null

        // Защита от повторов: не начинаем соседние предложения одним и тем же словом
        val recentFirstWords = ArrayDeque<String>()
        fun firstWordOf(p: String): String = p.trim().split(Regex("\\s+")).firstOrNull()?.lowercase() ?: ""

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
        var lastCandidate: String? = null // защита от двух одинаковых фраз подряд

        fun flushParagraph() {
            if (paragraph.isEmpty()) return
            sb.append(paragraph.joinToString(" "))
            sb.append("\n\n")
            paragraph.clear()
        }

        while (currentChars < targetChars && attempts < validPhrases.size * 4) {
            attempts++

            // Приоритет: фраза, связанная по смыслу с предыдущей
            var candidate = selectNextPhrase(validPhrases, lastKeywords, usedPhrases, rng)
                ?: validPhrases.filter { it !in usedPhrases }.randomOrNull(rng)

            if (candidate == null) {
                usedPhrases.clear()
                continue
            }

            // Пытаемся избежать повтора первого слова соседних предложений
            var retries = 0
            while ((firstWordOf(candidate) in recentFirstWords || candidate == lastCandidate) && retries < 5) {
                retries++
                val alt = validPhrases
                    .filter { it !in usedPhrases && firstWordOf(it) !in recentFirstWords && it != lastCandidate }
                    .randomOrNull(rng) ?: break
                candidate = alt
            }

            usedPhrases.add(candidate)
            lastCandidate = candidate
            val fw = firstWordOf(candidate)
            recentFirstWords.addLast(fw)
            if (recentFirstWords.size > 3) recentFirstWords.removeFirst()

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

    // === СКЛОНЕНИЕ ГЛАГОЛОВ ПО РОДУ (мужской ↔ женский) ===
    private val verbGenderPairs = mapOf(
        "пошёл" to "пошла", "пошел" to "пошла",
        "вошёл" to "вошла", "вошел" to "вошла",
        "вышел" to "вышла", "ушёл" to "ушла", "ушел" to "ушла",
        "зашёл" to "зашла", "зашел" to "зашла",
        "подошёл" to "подошла", "подошел" to "подошла",
        "шёл" to "шла", "шел" to "шла",
        "стоял" to "стояла", "сидел" to "сидела", "лежал" to "лежала",
        "лёг" to "легла", "сел" to "села", "встал" to "встала",
        "увидел" to "увидела", "сказал" to "сказала", "спросил" to "спросила",
        "ответил" to "ответила", "подумал" to "подумала", "понял" to "поняла",
        "вспомнил" to "вспомнила", "решил" to "решила", "начал" to "начала",
        "продолжил" to "продолжила", "хотел" to "хотела", "мог" to "могла",
        "смог" to "смогла", "ждал" to "ждала", "взял" to "взяла",
        "отдал" to "отдала", "открыл" to "открыла", "закрыл" to "закрыла",
        "заметил" to "заметила", "чувствовал" to "чувствовала",
        "почувствовал" to "почувствовала", "говорил" to "говорила",
        "промолчал" to "промолчала", "замолчал" to "замолчала",
        "кивнул" to "кивнула", "улыбнулся" to "улыбнулась",
        "усмехнулся" to "усмехнулась", "обернулся" to "обернулась",
        "отвернулся" to "отвернулась", "остановился" to "остановилась",
        "задумался" to "задумалась", "вздохнул" to "вздохнула",
        "посмотрел" to "посмотрела", "крикнул" to "крикнула",
        "шепнул" to "шепнула", "махнул" to "махнула", "держал" to "держала",
        "исчез" to "исчезла", "появился" to "появилась", "вернулся" to "вернулась"
    )

    /**
     * Возвращает глагол в нужном роде или null, если слово не глагол прошлого времени.
     * Известные пары берутся из словаря, неизвестные — по эвристике -л/-ла.
     */
    private fun conjugateVerb(verbLower: String, targetGender: String): String? {
        val isKnownMasculine = verbGenderPairs.containsKey(verbLower)
        val isKnownFeminine = verbGenderPairs.containsValue(verbLower)

        return when (targetGender) {
            "f" -> when {
                isKnownFeminine -> verbLower                                   // уже женская
                isKnownMasculine -> verbGenderPairs[verbLower]                 // м → ж по словарю
                verbLower.endsWith("л") -> verbLower + "а"                     // эвристика: поглядел → поглядела
                else -> null
            }
            else -> when {
                isKnownMasculine -> verbLower                                  // уже мужская
                isKnownFeminine -> verbGenderPairs.entries.first { it.value == verbLower }.key // ж → м
                verbLower.endsWith("ла") -> verbLower.dropLast(1)              // эвристика: поглядела → поглядел
                else -> null
            }
        }
    }

    // Служебные слова, на которых согласование подлежащего НЕ прерывается
    // (предлоги, союзы, наречия): "Анна медленно встала", "стоял у окна и смотрел"
    private val agreementFunctionWords = setOf(
        "и", "не", "бы", "же", "но", "а", "что", "как", "чтобы", "то", "ведь", "будто",
        "в", "на", "у", "к", "с", "о", "об", "за", "по", "от", "до", "из", "под", "над",
        "при", "без", "для", "уже", "ещё", "еще", "только", "даже", "почти", "снова",
        "опять", "тут", "там", "теперь", "потом", "затем", "наконец", "вдруг",
        "медленно", "быстро", "тихо", "громко", "молча", "вслух", "внимательно",
        "осторожно", "резко", "плавно", "сразу", "долго"
    )

    private val punctuationChars = charOf(',', '.', '!', '?', ':', ';', '"', '\'', '«', '»', '—', '(', ')', '…')

    /**
     * Слово — известный глагол из словаря пар (в любой форме рода)
     */
    private fun isKnownVerb(lower: String): Boolean =
        verbGenderPairs.containsKey(lower) || verbGenderPairs.containsValue(lower)

    /**
     * Согласует глаголы прошлого времени с ПОДЛЕЖАЩИМ.
     *
     * Конечный автомат:
     * - активируется на подлежащем (имя героя / местоимение);
     * - не гаснет на служебных словах и наречиях ("медленно встала");
     * - однородные сказуемые через "и" даже после дополнений
     *   ("взял письмо и перечитал", "стоял у окна и смотрел");
     * - для словарных глаголов возобновление надёжно, для неизвестных —
     *   только после служебного слова (не трогаем "свеча погасла");
     * - гаснет на новом подлежащем — чужие фразы не изменяем.
     */
    private fun conjugateForSubject(text: String, subject: String, gender: String): String {
        if (!text.contains(subject)) return text
        val tokens = text.split(" ")
        val out = StringBuilder()
        var active = false        // текущее слово — наше подлежащее
        var subjectSeen = false   // подлежащее уже встречалось во фразе
        var resume = false        // ждём однородное сказуемое после "и"
        var prev = ""             // предыдущее слово (для эвристики)

        for (token in tokens) {
            val core = token.trim(punctuationChars)
            if (core == subject) {
                active = true
                subjectSeen = true
                resume = false
                out.append(token).append(' ')
                prev = subject.lowercase()
                continue
            }
            val lower = core.lowercase()

            if (active) {
                val fixed = conjugateVerb(lower, gender)
                if (fixed != null) {
                    out.append(token.replaceFirst(core, fixed)).append(' ')
                    prev = fixed
                    continue
                }
            }

            if (resume) {
                if (lower in agreementFunctionWords) {
                    out.append(token).append(' ')
                    prev = lower
                    continue // "и внимательно перечитал"
                }
                val fixed = conjugateVerb(lower, gender)
                if (fixed != null && (isKnownVerb(lower) || prev in agreementFunctionWords || isKnownVerb(prev))) {
                    out.append(token.replaceFirst(core, fixed)).append(' ')
                    prev = fixed
                    continue
                }
                resume = false
            }

            if (lower == "и") {
                resume = subjectSeen
                out.append(token).append(' ')
                prev = lower
                continue
            }

            if (lower !in agreementFunctionWords) {
                active = false
            }
            out.append(token).append(' ')
            prev = lower
        }
        return out.toString().trimEnd()
    }

    /** Гарантирует точку в конце предложения (при изучении точки терялись при разбивке) */
    private fun ensureSentenceEnd(s: String): String {
        val t = s.trimEnd()
        if (t.isEmpty()) return t
        val last = t.last()
        return when {
            last == '.' || last == '!' || last == '?' || last == '…' -> t
            last == ',' -> t.dropLast(1) + "."
            else -> "$t."
        }
    }

    // === АДАПТАЦИЯ ФРАЗЫ: замена имён + согласование рода ТОЛЬКО у героя ===
    private fun adaptPhrase(phrase: String, hero: String, heroGender: String): String {
        var result = phrase

        // ВАЖНО: \b в Java-регулярках не работает с кириллицей (\w = латиница),
        // поэтому границы слова — через lookaround по буквам
        fun wordRegex(w: String) = Regex("(?<![А-Яа-яЁёA-Za-z])${Regex.escape(w)}(?![А-Яа-яЁёA-Za-z])")

        // Замена местоимений на род героя (до склонения глаголов)
        result = if (heroGender == "f") {
            result.replace(wordRegex("Он"), "Она").replace(wordRegex("он"), "она")
        } else {
            result.replace(wordRegex("Она"), "Он").replace(wordRegex("она"), "он")
        }

        // Заменяем распространённые имена на героя пользователя
        // (только в именительном падеже; косвенные формы оставляем как в книге)
        val commonNames = listOf(
            "Анна", "Мария", "Марина", "Елена", "Ольга", "Татьяна", "Наталья", "Юлия",
            "Ирина", "Оксана", "Галина", "Ксения", "Дарья", "Светлана", "Екатерина",
            "Александра", "Валентина",
            "Алексей", "Александр", "Дмитрий", "Иван", "Сергей", "Андрей", "Михаил",
            "Виктор", "Игорь", "Николай", "Владимир", "Павел", "Роман", "Денис",
            "Евгений", "Борис", "Григорий", "Кирилл", "Максим", "Артём", "Артем",
            "Пётр", "Петр", "Фёдор", "Федор", "Константин"
        )
        for (name in commonNames) {
            result = result.replace(wordRegex(name), hero)
        }

        // Согласуем глаголы только там, где подлежащее — герой (или местоимение).
        // Остальной текст книги уже грамматически верен — его не трогаем.
        result = conjugateForSubject(result, hero, heroGender)
        if (heroGender == "f") {
            result = conjugateForSubject(result, "Она", "f")
            result = conjugateForSubject(result, "она", "f")
        } else {
            result = conjugateForSubject(result, "Он", "m")
            result = conjugateForSubject(result, "он", "m")
        }

        return ensureSentenceEnd(result)
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
        // Запятая перед союзом в косвенной речи: "понял что" -> "понял, что"
        // (в изученных предложениях пунктуация могла быть утеряна при извлечении)
        result = result.replace(
            Regex("(?i)(понял|поняла|решил|решила|сказал|сказала|думал|думала|знал|знала|видел|видела|чувствовал|чувствовала|заметил|заметила) (что|как|где|когда|чтобы|почему|зачем)"),
            "$1, $2"
        )
        return result.trim()
    }
}
