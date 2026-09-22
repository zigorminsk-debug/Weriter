package com.zakharevich.ziwriter

import kotlin.random.Random

/**
 * StoryGenerator v33 — литературный генератор с осмысленным повествованием
 *
 * Основные принципы:
 * 1. Анализ исходного текста: пол героев, время, стиль, настроение
 * 2. Извлечение речевых паттернов из изученных текстов
 * 3. Связное повествование с причиной-следствием
 * 4. Правильная морфология (склонение имён, местоимений)
 * 5. Сюжетная линия, а не набор случайных предложений
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

    // === ПРОФИЛЬ ТЕКСТА ===
    data class TextProfile(
        val gender: String,    // "f" / "m"
        val tense: String,     // "past" / "present"
        val person: String,    // "first" / "third"
        val mood: String       // "serious", "light", "dramatic", "philosophical"
    )

    // === ГЕРОЙ С ПОЛОМ ===
    data class Character(
        val name: String,
        val gender: String,    // "m" или "f"
        val pronoun: String,   // "он" / "она"
        val possessive: String // "его" / "её"
    ) {
        /** Глагол прошедшего времени с правильным окончанием по роду.
         *  Пример: verbPast("понял") → "поняла" для ж.р. */
        fun verbPast(base: String): String {
            // Базовая форма должна быть в муж.роде: "стоял", "пошёл", "увидел"
            return if (gender == "f") {
                when {
                    base.endsWith("ёл") -> base.dropLast(2) + "ла"   // пошёл → пошла
                    base.endsWith("ил") -> base.dropLast(2) + "ла"   // увидел → увидела
                    base.endsWith("ёл") -> base.dropLast(2) + "ла"
                    else -> base + "а"
                }
            } else base
        }
    }

    // === ОПРЕДЕЛЕНИЕ ЖАНРА (нужен LearningManager) ===
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

    // === АНАЛИЗ ПРОФИЛЯ ИСХОДНОГО ТЕКСТА ===
    private fun analyzeProfile(text: String): TextProfile {
        val lower = text.lowercase()

        // Пол главного героя — ищем первое встретившееся женское или мужское имя
        val femaleNames = setOf("анна", "мария", "елена", "ольга", "татьяна", "наталья", "юлия", "дарья", "ксения", "ирина", "светлана", "лариса", "гал", "вали", "кат", "маш", "лен", "тан", "наст", "оксан", "вери", "александр")
        val maleNames = setOf("алекс", "игорь", "дмитрий", "сергей", "иван", "петр", "андрей", "михаил", "виктор", "николай", "владимир", "артём", "максим", "кирилл", "павел", "роман", "денис", "евгений", "константин")

        val hasFemale = femaleNames.any { lower.contains(it) }
        val hasMale = maleNames.any { lower.contains(it) }
        val gender = when {
            hasFemale && !hasMale -> "f"
            hasMale && !hasFemale -> "m"
            lower.contains(" она ") -> "f"
            else -> "m"
        }

        // Время повествования
        val tense = if (lower.contains("стоял") || lower.contains("пошёл") || lower.contains("увидел") ||
            lower.contains("сказал") || lower.contains("подумал")
        ) "past" else "present"

        // Лицо повествования
        val person = if (lower.contains(" я ") || lower.contains(" мне ") || lower.startsWith("я ")) "first" else "third"

        // Настроение
        val mood = when {
            lower.contains("странное") || lower.contains("тревог") || lower.contains("испуг") -> "dramatic"
            lower.contains("жизнь") || lower.contains("смысл") || lower.contains("душа") -> "philosophical"
            lower.contains("смешно") || lower.contains("радост") || lower.contains("весел") -> "light"
            else -> "serious"
        }

        return TextProfile(gender, tense, person, mood)
    }

    // === ИЗВЛЕЧЕНИЕ ГЕРОЕВ С ПОЛОМ ===
    private fun extractCharacters(text: String, customHeroes: List<String>): List<Character> {
        if (customHeroes.isNotEmpty()) {
            return customHeroes.map { name -> createCharacter(name) }
        }

        // Извлекаем имена из текста: слова с заглавной буквы длиной 3+
        val candidates = mutableMapOf<String, Int>()
        val sentences = text.split(Regex("[.!?]+")).map { it.trim() }.filter { it.isNotEmpty() }

        val femaleNames = setOf("анна", "мария", "елена", "ольга", "татьяна", "наталья", "юлия", "дарья", "ксения", "ирина", "светлана", "лариса", "валентина", "гал", "кат", "маш", "лен", "тан", "наст", "оксан", "вери", "екатерин", "александр")
        val maleNames = setOf("алекс", "игорь", "дмитрий", "сергей", "иван", "петр", "андрей", "михаил", "виктор", "николай", "владимир", "артём", "максим", "кирилл", "павел", "роман", "денис", "евгений", "константин")

        // Исключаем слова, которые не являются именами
        val nonNames = setOf("Это", "Тот", "Та", "Там", "Тут", "Как", "Что", "Кто", "Где", "Когда", "Зачем", "Почему", "Однако", "Потом", "Вдруг", "Между")

        for (sentence in sentences.take(50)) {
            val words = sentence.split(Regex("\\s+"))
            for (word in words) {
                val clean = word.replace(Regex("[^А-Яа-яЁёA-Za-z-]"), "")
                if (clean.length < 3) continue
                if (clean in nonNames) continue
                if (clean.matches(Regex("[А-ЯЁ][а-яё]{2,}"))) {
                    candidates[clean] = (candidates[clean] ?: 0) + 1
                }
            }
        }

        // Сортируем по частоте, берём топ-3
        val topNames = candidates.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(3)

        return topNames.map { createCharacter(it) }
    }

    private fun createCharacter(name: String): Character {
        val lower = name.lowercase()
        val isFemale = when {
            lower.endsWith("а") || lower.endsWith("я") -> true
            lower.endsWith("ина") || lower.endsWith("ёна") -> true
            // Известные мужские имена, оканчивающиеся на "а"
            lower in setOf("никита", "илья", "лука", "фома", "савва") -> false
            else -> false
        }
        return if (isFemale) {
            Character(name, "f", "она", "её")
        } else {
            Character(name, "m", "он", "его")
        }
    }

    // === ОПРЕДЕЛЕНИЕ МЕСТА ДЕЙСТВИЯ ИЗ ТЕКСТА ===
    private fun detectSetting(text: String, customPlace: String, profile: TextProfile): String {
        if (customPlace.isNotEmpty()) return customPlace
        val lower = text.lowercase()
        return when {
            lower.contains("офис") || lower.contains("работ") -> "в офисе"
            lower.contains("дом") || lower.contains("квартир") -> "дома"
            lower.contains("улиц") || lower.contains("город") -> "на улице города"
            lower.contains("лес") || lower.contains("парк") -> "в парке"
            lower.contains("море") || lower.contains("берег") -> "на берегу моря"
            lower.contains("кафе") || lower.contains("ресторан") -> "в кафе"
            lower.contains("вокзал") || lower.contains("поезд") -> "на вокзале"
            lower.contains("школ") || lower.contains("университет") -> "в учебном заведении"
            lower.contains("больниц") -> "в больнице"
            else -> "в знакомом месте"
        }
    }

    // === ОПРЕДЕЛЕНИЕ ВРЕМЕНИ СУТОК ===
    private fun detectTimeOfDay(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("утр") || lower.contains("рассвет") -> "Утром"
            lower.contains("вечер") || lower.contains("закат") -> "Вечером"
            lower.contains("ночь") || lower.contains("темн") -> "Ночью"
            else -> "Днём"
        }
    }

    // === ГЛАВНАЯ ФУНКЦИЯ ГЕНЕРАЦИИ ===
    fun generate(config: GenerationConfig): GeneratedStory {
        val rawOriginal = config.originalText.trim()
        val original = if (config.fixGrammar) GrammarCorrector.correct(rawOriginal) else rawOriginal
        val targetChars = config.pages * CHARS_PER_PAGE

        // Анализируем профиль исходного текста
        val profile = analyzeProfile(original)

        // Извлекаем героев с правильным полом
        val characters = extractCharacters(original, config.customHeroes)
        val mainHero = characters.firstOrNull() ?: Character("Герой", "m", "он", "его")
        val secondHero = characters.getOrElse(1) { null }

        // Определяем место и время
        val setting = detectSetting(original, config.customPlace, profile)
        val timeOfDay = detectTimeOfDay(original)

        // Ключевые слова (для темы)
        val keywords = extractKeywords(original)
        val mainKeyword = keywords.firstOrNull() ?: "тайна"
        val secondKeyword = keywords.getOrElse(1) { "судьба" }

        // Заголовок
        val title = if (config.bookTitle.isNotBlank()) {
            config.bookTitle.trim()
        } else {
            generateTitle(original, keywords, config.genre)
        }

        // Строим текст
        val sb = StringBuilder()
        sb.append(original)
        sb.append("\n\n")

        // Мостик — плавный переход от оригинала к продолжению
        sb.append(buildBridge(mainHero, secondHero, setting, timeOfDay, mainKeyword, profile, config.genre))
        sb.append("\n\n")

        // Генерируем сюжетные сцены
        val scenes = buildScenes(config.pages, config.genre, profile, mainHero, secondHero, setting, timeOfDay, mainKeyword, secondKeyword, keywords)

        var currentPage = 1
        var charsSincePageStart = 0

        for (scene in scenes) {
            // Проверяем, нужно ли начать новую страницу
            if (charsSincePageStart > CHARS_PER_PAGE - 200 && currentPage < config.pages) {
                currentPage++
                sb.append("\n\n— — — Страница $currentPage — — —\n\n")
                charsSincePageStart = 0
            }

            val sceneText = renderScene(scene, mainHero, secondHero, setting, timeOfDay, mainKeyword, secondKeyword, profile, config.style)
            sb.append(sceneText)
            sb.append("\n\n")
            charsSincePageStart += sceneText.length

            if (sb.length >= targetChars) break
        }

        // Концовка
        sb.append(buildEnding(mainHero, secondHero, setting, mainKeyword, profile, config.authorName))

        // Форматируем
        var finalText = sb.toString()
        finalText = smoothText(finalText)

        val actualPages = (finalText.length + CHARS_PER_PAGE - 1) / CHARS_PER_PAGE

        return GeneratedStory(
            title = title,
            fullText = finalText,
            chars = finalText.length,
            pages = actualPages,
            heroes = characters.map { it.name },
            keywords = keywords,
            correctedOriginal = original
        )
    }

    // === СЦЕНА ===
    data class Scene(
        val type: String,     // "setting", "action", "dialogue", "thought", "conflict", "climax", "resolution"
        val focus: String     // на чём фокус сцены
    )

    private fun buildScenes(
        pages: Int,
        genre: String,
        profile: TextProfile,
        mainHero: Character,
        secondHero: Character?,
        setting: String,
        timeOfDay: String,
        mainKeyword: String,
        secondKeyword: String,
        keywords: List<String>
    ): List<Scene> {
        val scenes = mutableListOf<Scene>()
        val totalScenes = pages * 3 // примерно 3 сцены на страницу

        // Завязка (20%)
        val introCount = maxOf(1, totalScenes / 5)
        scenes.add(Scene("setting", "атмосфера и обстановка"))
        scenes.add(Scene("action", "${mainHero.name} приходит в действие"))
        if (secondHero != null) {
            scenes.add(Scene("action", "появление второго персонажа"))
        }

        // Развитие (50%)
        val devCount = maxOf(1, totalScenes / 2)
        repeat(devCount) { i ->
            when (i % 4) {
                0 -> scenes.add(Scene("action", "действие ${mainHero.name}"))
                1 -> if (secondHero != null) scenes.add(Scene("dialogue", "разговор"))
                2 -> scenes.add(Scene("thought", "размышление ${mainHero.name}"))
                3 -> scenes.add(Scene("action", "новое событие"))
            }
        }

        // Кульминация (20%)
        scenes.add(Scene("conflict", "напряжение нарастает"))
        scenes.add(Scene("climax", "решающий момент"))

        // Развязка (10%)
        scenes.add(Scene("resolution", "итоги"))

        return scenes
    }

    private fun renderScene(
        scene: Scene,
        mainHero: Character,
        secondHero: Character?,
        setting: String,
        timeOfDay: String,
        mainKeyword: String,
        secondKeyword: String,
        profile: TextProfile,
        style: String
    ): String {
        return when (scene.type) {
            "setting" -> renderSetting(mainHero, setting, timeOfDay, mainKeyword, profile)
            "action" -> renderAction(mainHero, secondHero, setting, mainKeyword, profile)
            "dialogue" -> renderDialogue(mainHero, secondHero, mainKeyword, secondKeyword, profile)
            "thought" -> renderThought(mainHero, mainKeyword, secondKeyword, profile)
            "conflict" -> renderConflict(mainHero, secondHero, setting, mainKeyword, profile)
            "climax" -> renderClimax(mainHero, setting, mainKeyword, profile)
            "resolution" -> renderResolution(mainHero, setting, mainKeyword, profile)
            else -> renderAction(mainHero, secondHero, setting, mainKeyword, profile)
        }.let { applyStyle(it, style) }
    }

    // === РЕНДЕР СЦЕН ПО ТИПАМ ===

    private fun renderSetting(hero: Character, setting: String, timeOfDay: String, keyword: String, profile: TextProfile): String {
        val endings = hero.verbPast("стоял")
        val heroEnd = if (hero.gender == "f") "а" else ""
        val moodDesc = when (profile.mood) {
            "dramatic" -> "тревожно и напряжённо"
            "philosophical" -> "тихо, словно мир затаил дыхание"
            "light" -> "уютно и спокойно"
            else -> "обычно, ничто не предвещало перемен"
        }
        return "$timeOfDay ${hero.name} $endings $setting. Вокруг было $moodDesc. В воздухе витало что-то, связанное с $keyword — ${hero.pronoun} ещё не мог$heroEnd это объяснить."
    }

    private fun renderAction(hero: Character, secondHero: Character?, setting: String, keyword: String, profile: TextProfile): String {
        val actions = listOf(
            "${hero.name} медленно ${hero.verbPast("пошёл")} вперёд, внимательно осматриваясь.",
            "${hero.pronoun.capitalize()} ${hero.verbPast("остановился")} и прислушал${if (hero.gender == "f") "ась" else "ся"}.",
            "${hero.name} ${hero.verbPast("оглядел")}ся по сторонам.",
            "${hero.pronoun.capitalize()} ${hero.verbPast("сделал")} шаг и замер${if (hero.gender == "f") "а" else ""}.",
            "${hero.name} ${hero.verbPast("подошёл")} ближе."
        )

        val reactions = listOf(
            " Что-то изменилось.",
            " ${hero.pronoun.capitalize()} чувствовал${if (hero.gender == "f") "а" else ""}, что $keyword близ${if (hero.gender == "f") "ка" else "ок"}.",
            " Впереди было что-то важное.",
            ""
        )

        return actions.random() + reactions.random()
    }

    private fun renderDialogue(hero: Character, secondHero: Character?, keyword: String, keyword2: String, profile: TextProfile): String {
        val heroEnding = if (hero.gender == "f") "а" else ""
        val secondEnding = if (secondHero?.gender == "f") "а" else ""

        if (secondHero == null) {
            val line = when (profile.mood) {
                "dramatic" -> "Что-то здесь не так..."
                "philosophical" -> "Зачем всё это?"
                "light" -> "Ну что ж, посмотрим."
                else -> "Надо разобраться."
            }
            return "— $line, — ${hero.verbPast("прошептал")} ${hero.name}."
        }

        val dialogues = listOf(
            "— Ты знаешь, о чём я думаю? — спросил$heroEnding ${hero.name}.\n" +
            "— О $keyword? — ответил$secondEnding ${secondHero.name}. — Я тоже об этом.\n" +
            "${hero.name} ${hero.verbPast("кивнул")}. — Тогда нам нужно действовать.",

            "— ${secondHero.name}, ты ${secondHero.verbPast("заметил")} что-то странное? — ${hero.verbPast("спросил")} ${hero.name}.\n" +
            "${secondHero.name} ${secondHero.verbPast("помолчал")}.\n" +
            "— Да, — наконец произнёс$secondEnding ${secondHero.name}. — И мне это не нравится.",

            "— Мы не можем больше молчать, — сказал$heroEnding ${hero.name}. — $keyword сам${if (hero.gender == "f") "а" else ""} по себе не разрешится.\n" +
            "${secondHero.name} ${secondHero.verbPast("взглянул")} на ${hero.name}.\n" +
            "— Ты прав${heroEnding}. Но с чего начнём?"
        )

        return dialogues.random()
    }

    private fun renderThought(hero: Character, keyword: String, keyword2: String, profile: TextProfile): String {
        val heroEnding = if (hero.gender == "f") "а" else ""
        val followUp = when (profile.mood) {
            "dramatic" -> "Страх сжимал ${hero.possessive} сердце."
            "philosophical" -> "В чём смысл всего происходящего?"
            "light" -> "Может, всё не так уж и плохо."
            else -> "Нужно собраться с мыслями."
        }

        val thoughts = listOf(
            "${hero.name} ${hero.verbPast("подумал")}, что всё это не случайно. $keyword и $keyword2 — они связаны, ${hero.pronoun} чувствовал$heroEnding это.",
            "${hero.pronoun.capitalize()} ${hero.verbPast("вспомнил")} слова, сказанные давно. Может, в них и был ответ на вопрос о $keyword?",
            "${hero.name} ${hero.verbPast("понял")}: ничего уже не будет прежним. И это пугало$heroEnding больше всего.",
            "${hero.pronoun.capitalize()} ${hero.verbPast("задумался")}. $followUp"
        )
        return thoughts.random()
    }

    private fun renderConflict(hero: Character, secondHero: Character?, setting: String, keyword: String, profile: TextProfile): String {
        val conflicts = listOf(
            "Вдруг всё изменилось. ${hero.name} ${hero.verbPast("увидел")} то, чего не ожидал${if (hero.gender == "f") "а" else ""}. $keyword — это было серьезнее, чем ${hero.pronoun} думал${if (hero.gender == "f") "а" else ""}.",
            "Что-то пошло не так. ${hero.name} ${hero.verbPast("почувствовал")} опасность. Нужно было решать — сейчас.",
            if (secondHero != null) {
                "${secondHero.name} ${secondHero.verbPast("сказал")} то, что перевернуло всё. ${hero.name} ${hero.verbPast("не мог")} поверить своим ушам."
            } else {
                "${hero.name} ${hero.verbPast("остался")} ${if (hero.gender == "f") "ась" else "ся"} один на один с проблемой. Выхода, казалось, не было."
            }
        )
        return conflicts.random()
    }

    private fun renderClimax(hero: Character, setting: String, keyword: String, profile: TextProfile): String {
        val climaxes = listOf(
            "И тогда ${hero.name} ${hero.verbPast("решил")}. ${hero.pronoun.capitalize()} ${hero.verbPast("сделал")} то, что должен${if (hero.gender == "f") "а" else ""} был. Всё или ничего.",
            "Наступил момент истины. ${hero.name} ${hero.verbPast("взглянул")} вперёд — и ${hero.verbPast("пошёл")} навстречу $keyword.",
            "${hero.pronoun.capitalize()} ${hero.verbPast("понял")}: именно для этого всё и происходило. Именно к этому ${hero.pronoun} шёл${if (hero.gender == "f") "а" else ""}."
        )
        return climaxes.random()
    }

    private fun renderResolution(hero: Character, setting: String, keyword: String, profile: TextProfile): String {
        val resolutions = listOf(
            "Всё закончилось. ${hero.name} ${hero.verbPast("выдохнул")}. $keyword — позади, но опыт останется навсегда.",
            "${hero.name} ${hero.verbPast("оглядел")}ся. Мир стал другим — или это ${hero.pronoun} стал${if (hero.gender == "f") "а" else ""} другим?",
            "История подходила к концу. Но ${hero.name} знал${if (hero.gender == "f") "а" else ""}: впереди ещё много всего."
        )
        return resolutions.random()
    }

    private fun applyStyle(text: String, style: String): String {
        return when (style) {
            "Лаконичный" -> {
                val sentences = text.split(". ").take(3)
                sentences.joinToString(". ") + "."
            }
            "Поэтический" -> text + " Свет ложился мягко, как будто мир тоже задумался."
            "Драматичный" -> text + " Сердце билось где-то в горле."
            "Ироничный" -> text + " Ну, классический поворот, ничего не скажешь."
            else -> text
        }
    }

    // === МОСТИК ===
    private fun buildBridge(
        hero: Character,
        secondHero: Character?,
        setting: String,
        timeOfDay: String,
        keyword: String,
        profile: TextProfile,
        genre: String
    ): String {
        return when (genre) {
            "Детектив" -> "$timeOfDay ${hero.name} всё ещё думал${if (hero.gender == "f") "а" else ""} о случившемся. $setting хранил${if (setting.contains("а")) "а" else "о"} тайну, и ${hero.pronoun} был${if (hero.gender == "f") "а" else ""} на пороге разгадки."
            "Роман" -> "$timeOfDay ${hero.name} ${hero.verbPast("вспомнил")} ту встречу. Сердце всё ещё билось чаще, когда ${hero.pronoun} думал${if (hero.gender == "f") "а" else ""} о $keyword."
            "Фантастика" -> "$timeOfDay ${hero.name} смотрел${if (hero.gender == "f") "а" else ""} на $setting. $keyword манил${if (hero.gender == "f") "а" else ""}, как далёкая звезда."
            "Философская проза" -> "$timeOfDay ${hero.name} ${hero.verbPast("задумался")}. $keyword — что это, как не отражение собственной души?"
            else -> "$timeOfDay ${hero.name} ${hero.verbPast("стоял")} $setting и ${hero.verbPast("понял")}: всё только начинается. $keyword станет путеводной нитью."
        }
    }

    // === ЗАГОЛОВОК ===
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

    // === КОНЦОВКА ===
    private fun buildEnding(
        hero: Character,
        secondHero: Character?,
        setting: String,
        keyword: String,
        profile: TextProfile,
        authorName: String
    ): String {
        val author = if (authorName.isNotBlank()) authorName else "Автор"

        val lastLines = if (profile.mood == "philosophical") {
            "${hero.name} ${hero.verbPast("остановился")} $setting. ${hero.pronoun.capitalize()} ${hero.verbPast("понял")}: $keyword — это не конец, а лишь новая глава. Жизнь продолжается, и впереди ещё столько всего."
        } else if (profile.mood == "dramatic") {
            "${hero.name} ${hero.verbPast("выдохнул")}. Всё позади. $setting ${hero.verbPast("казался")} теперь другим — таким, каким ${hero.pronoun} ${hero.verbPast("видел")} его раньше. Но ${hero.pronoun} знал${if (hero.gender == "f") "а" else ""}: ничего уже не будет прежним."
        } else {
            "${hero.name} ${hero.verbPast("улыбнул")}ся. $keyword — это только начало. Впереди ${hero.possessive} ждёт новый путь, и ${hero.pronoun} готов${if (hero.gender == "f") "а" else ""} к нему."
        }

        return "\n\n$lastLines\n\n— Конец —\n\nСгенерировано в ZI Writer • $author"
    }

    // === ИЗВЛЕЧЕНИЕ КЛЮЧЕВЫХ СЛОВ ===
    private fun extractKeywords(text: String): List<String> {
        val words = text.lowercase().replace(Regex("[^а-яёa-z ]"), " ").split(Regex("\\s+")).filter { it.length > 3 }
        val stopWords = setOf(
            "который", "которая", "которые", "потому", "однако", "только", "очень", "сейчас",
            "было", "была", "были", "будет", "можно", "нужно", "надо", "через", "между",
            "этот", "эта", "это", "тот", "та", "то", "весь", "вся", "всё", "все", "всех",
            "затем", "потом", "когда", "если", "тогда", "также", "поэтому"
        )
        val filtered = words.filter { it !in stopWords }
        return filtered.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { it.key }
            .take(15)
    }

    // === ФОРМАТИРОВАНИЕ ТЕКСТА ===
    private fun smoothText(text: String): String {
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
