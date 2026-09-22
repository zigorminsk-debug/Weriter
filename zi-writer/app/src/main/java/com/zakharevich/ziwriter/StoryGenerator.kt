package com.zakharevich.ziwriter

import kotlin.random.Random

/**
 * StoryGenerator v32 — Литературный движок генерации
 * 
 * Архитектура:
 * 1. TextAnalyzer — анализ изученных текстов, извлечение шаблонов и стиля
 * 2. NarrativeArc — построение структуры повествования (завязка→развитие→кульминация→развязка)
 * 3. ParagraphBuilder — генерация абзацев на основе шаблонов + контекста
 * 4. CoherenceEngine — контроль связности и логической последовательности
 * 5. DialogueGenerator — модель диалогов на основе интенций (вопрос→ответ→утверждение)
 * 
 * Ключевые принципы:
 * - Используем learnedPhrases как основу для реальных речевых оборотов
 * - Каждый абзац логически вытекает из предыдущего
 * - Стиль соответствует жанру
 * - Диалоги строятся по модели Маркова интенций
 * - Никаких принудительных вставок (пицца/вороны) — только если реально есть в изученном материале
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

    /**
     * Определяет жанр по содержимому текста
     */
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

    // === КОНТЕКСТ ИСТОРИИ ===
    data class StoryContext(
        val originalText: String,
        val genre: String,
        val style: String,
        val heroes: List<String>,
        val mainPlace: String,
        val keywords: List<String>,
        val sentenceTemplates: List<SentenceTemplate>,
        val dialoguePatterns: List<DialoguePattern>,
        val transitionPhrases: List<String>,
        val isPhilosophical: Boolean,
        val authorName: String
    )

    // Шаблон предложения с пропусками для подстановки
    data class SentenceTemplate(
        val pattern: String, // "Она посмотрела на {hero} и поняла, что {action}"
        val intent: ParagraphIntent, // описание, действие, диалог, мысль
        val genre: String = "",
        val source: String = "" // откуда извлечён
    )

    // Паттерн диалога
    data class DialoguePattern(
        val intent: String, // вопрос, ответ, утверждение, просьба
        val pattern: String // "{speaker}, — {phrase}"
    )

    // Намерение абзаца
    enum class ParagraphIntent {
        DESCRIPTION, // описание места, атмосферы
        ACTION, // действие персонажа
        DIALOGUE, // диалог
        THOUGHT, // мысли героя
        TRANSITION, // связка между сценами
        CLIMAX, // кульминация
        RESOLUTION // развязка
    }

    // === АНАЛИЗ ТЕКСТА ===
    object TextAnalyzer {
        
        /**
         * Извлекает шаблоны предложений из изученных фраз
         */
        fun extractSentenceTemplates(phrases: List<String>, genre: String): List<SentenceTemplate> {
            if (phrases.isEmpty()) return generateDefaultTemplates(genre)
            
            val templates = mutableListOf<SentenceTemplate>()
            
            for (phrase in phrases) {
                if (phrase.length < 20 || phrase.length > 200) continue
                
                // Определяем интенцию по структуре
                val intent = detectIntent(phrase)
                
                // Создаём шаблон с пропусками
                val pattern = createPattern(phrase)
                
                templates.add(SentenceTemplate(pattern, intent, genre, "learned"))
            }
            
            // Добавляем жанровые шаблоны по умолчанию
            templates.addAll(generateDefaultTemplates(genre))
            
            return templates.distinctBy { it.pattern }.take(200)
        }
        
        /**
         * Определяет интенцию предложения по его структуре
         */
        private fun detectIntent(phrase: String): ParagraphIntent {
            val lower = phrase.lowercase()
            return when {
                // Диалог
                lower.contains("—") || lower.contains("\"") || lower.contains("сказал") || 
                lower.contains("спросил") || lower.contains("ответил") -> ParagraphIntent.DIALOGUE
                
                // Мысль
                lower.contains("подумал") || lower.contains("решил") || lower.contains("понял") ||
                lower.contains("осознал") || lower.contains("вспомнил") || lower.contains("казалось") -> ParagraphIntent.THOUGHT
                
                // Действие
                lower.contains("пошёл") || lower.contains("сделал") || lower.contains("взял") ||
                lower.contains("открыл") || lower.contains("закрыл") || lower.contains("посмотрел") ||
                Regex("\\b(went|did|took|opened|closed|looked)\\b").containsMatchIn(lower) -> ParagraphIntent.ACTION
                
                // Описание
                else -> ParagraphIntent.DESCRIPTION
            }
        }
        
        /**
         * Создаёт шаблон предложения, заменяя имена и числа на плейсхолдеры
         */
        private fun createPattern(phrase: String): String {
            var pattern = phrase
            
            // Заменяем числа
            pattern = pattern.replace(Regex("\\d+"), "{number}")
            
            // Заменяем конкретные имена (заглавные слова длиной 3+)
            pattern = pattern.replace(Regex("\\b[А-ЯЁ][а-яё]{2,}\\b")) { match ->
                val word = match.value
                if (word.length > 3 && !listOf("Когда", "Потом", "Вдруг", "Однако", "Вместе").contains(word)) {
                    "{name}"
                } else word
            }
            
            return pattern
        }
        
        /**
         * Генерирует базовые шаблоны для жанра, если нет изученных
         */
        private fun generateDefaultTemplates(genre: String): List<SentenceTemplate> {
            return when (genre) {
                "Детектив" -> listOf(
                    SentenceTemplate("{hero} внимательно осмотрел {place}. Что-то здесь не так.", ParagraphIntent.DESCRIPTION, genre),
                    SentenceTemplate("— Ты уверен, что {keyword}? — спросил {hero2}.", ParagraphIntent.DIALOGUE, genre),
                    SentenceTemplate("{hero} понял, что разгадка ближе, чем кажется.", ParagraphIntent.THOUGHT, genre),
                    SentenceTemplate("Он осторожно подошёл к {object}. Улика была неоспоримой.", ParagraphIntent.ACTION, genre)
                )
                "Роман" -> listOf(
                    SentenceTemplate("{hero} посмотрел на {hero2}, и сердце забилось чаще.", ParagraphIntent.DESCRIPTION, genre),
                    SentenceTemplate("— Я не могу больше молчать, — прошептал {hero}.", ParagraphIntent.DIALOGUE, genre),
                    SentenceTemplate("Она думала о нём, о том, что между ними произошло.", ParagraphIntent.THOUGHT, genre),
                    SentenceTemplate("{hero} протянул руку и коснулся её плеча.", ParagraphIntent.ACTION, genre)
                )
                "Фантастика" -> listOf(
                    SentenceTemplate("Корабль {hero} приближался к {place}. Датчики показывали аномалию.", ParagraphIntent.DESCRIPTION, genre),
                    SentenceTemplate("— Мы не одни во вселенной, — сказал {hero} {hero2}.", ParagraphIntent.DIALOGUE, genre),
                    SentenceTemplate("{hero} смотрел на звёзды и думал о том, что ждёт впереди.", ParagraphIntent.THOUGHT, genre),
                    SentenceTemplate("Он активировал систему и приготовился к прыжку.", ParagraphIntent.ACTION, genre)
                )
                "Боевик" -> listOf(
                    SentenceTemplate("{hero} проверил оружие и вышел из {place}.", ParagraphIntent.ACTION, genre),
                    SentenceTemplate("— Цель обнаружена, — доложил {hero2} по рации.", ParagraphIntent.DIALOGUE, genre),
                    SentenceTemplate("{hero} знал, что отступать некуда.", ParagraphIntent.THOUGHT, genre),
                    SentenceTemplate("Он услышал шаги за спиной и обернулся.", ParagraphIntent.DESCRIPTION, genre)
                )
                "Приключения" -> listOf(
                    SentenceTemplate("{hero} открыл карту и изучил маршрут.", ParagraphIntent.ACTION, genre),
                    SentenceTemplate("— Мы нашли его! — воскликнул {hero2}.", ParagraphIntent.DIALOGUE, genre),
                    SentenceTemplate("{hero} смотрел на {place} и чувствовал, что приключение начинается.", ParagraphIntent.THOUGHT, genre),
                    SentenceTemplate("Впереди их ждал неизвестный путь через {place}.", ParagraphIntent.DESCRIPTION, genre)
                )
                "Хоррор" -> listOf(
                    SentenceTemplate("{hero} услышал странный звук в {place}.", ParagraphIntent.DESCRIPTION, genre),
                    SentenceTemplate("— Ты это слышал? — прошептал {hero2}.", ParagraphIntent.DIALOGUE, genre),
                    SentenceTemplate("{hero} чувствовал, что за ним кто-то наблюдает.", ParagraphIntent.THOUGHT, genre),
                    SentenceTemplate("Он медленно обернулся и увидел...", ParagraphIntent.ACTION, genre)
                )
                "Юмор" -> listOf(
                    SentenceTemplate("{hero} посмотрел на {hero2} и не мог сдержать улыбку.", ParagraphIntent.DESCRIPTION, genre),
                    SentenceTemplate("— Это не то, на что похоже, — сказал {hero}.", ParagraphIntent.DIALOGUE, genre),
                    SentenceTemplate("{hero} думал, что хуже уже не будет. Он ошибался.", ParagraphIntent.THOUGHT, genre),
                    SentenceTemplate("Он попытался сделать всё серьёзно, но получилось смешно.", ParagraphIntent.ACTION, genre)
                )
                else -> listOf(
                    SentenceTemplate("{hero} стоял в {place} и думал о {keyword}.", ParagraphIntent.DESCRIPTION, genre),
                    SentenceTemplate("— Знаешь, {keyword} — это важно, — сказал {hero2}.", ParagraphIntent.DIALOGUE, genre),
                    SentenceTemplate("{hero} понимал, что {keyword} изменит всё.", ParagraphIntent.THOUGHT, genre),
                    SentenceTemplate("Он сделал шаг вперёд и посмотрел вперёд.", ParagraphIntent.ACTION, genre)
                )
            }
        }
        
        /**
         * Извлекает переходные фразы из изученных текстов
         */
        fun extractTransitionPhrases(phrases: List<String>): List<String> {
            val transitions = phrases.filter { phrase ->
                val lower = phrase.lowercase()
                lower.startsWith("потом") || lower.startsWith("затем") || 
                lower.startsWith("тем временем") || lower.startsWith("вдруг") ||
                lower.startsWith("однако") || lower.startsWith("между тем") ||
                lower.startsWith("чуть позже") || lower.startsWith("спустя")
            }
            
            return transitions.map { it.take(50) }.distinct().take(30) + 
                listOf("Потом", "Затем", "Тем временем", "Вдруг", "Однако", "Между тем", "Чуть позже", "Спустя минуту")
        }
    }

    // === СТРУКТУРА ПОВЕСТВОВАНИЯ ===
    object NarrativeArc {
        
        /**
         * Строит структуру повествования по страницам
         */
        fun buildArc(pages: Int, genre: String, isPhilosophical: Boolean): List<ParagraphIntent> {
            val intents = mutableListOf<ParagraphIntent>()
            
            if (isPhilosophical) {
                // Философская проза: больше размышлений
                intents.add(ParagraphIntent.THOUGHT) // пролог
                repeat(pages - 2) { intents.add(ParagraphIntent.THOUGHT) }
                intents.add(ParagraphIntent.RESOLUTION) // эпилог
            } else {
                // Классическая структура
                // Завязка (20% текста)
                val intro = maxOf(1, pages / 5)
                repeat(intro) { 
                    intents.add(ParagraphIntent.DESCRIPTION)
                    intents.add(ParagraphIntent.THOUGHT)
                }
                
                // Развитие (50% текста)
                val development = maxOf(1, pages / 2)
                repeat(development) {
                    intents.add(ParagraphIntent.ACTION)
                    intents.add(ParagraphIntent.DIALOGUE)
                    intents.add(ParagraphIntent.DESCRIPTION)
                }
                
                // Кульминация (20% текста)
                val climax = maxOf(1, pages / 5)
                repeat(climax) {
                    intents.add(ParagraphIntent.CLIMAX)
                    intents.add(ParagraphIntent.ACTION)
                }
                
                // Развязка (10% текста)
                intents.add(ParagraphIntent.RESOLUTION)
            }
            
            return intents
        }
    }

    // === ГЕНЕРАТОР ПАРАГРАФОВ ===
    object ParagraphBuilder {
        
        /**
         * Генерирует абзац на основе интенции и контекста
         */
        fun generate(
            intent: ParagraphIntent,
            context: StoryContext,
            previousParagraphs: List<String>,
            rng: Random
        ): String {
            val hero = context.heroes.firstOrNull() ?: "он"
            val hero2 = context.heroes.getOrElse(1) { context.heroes.firstOrNull() ?: "она" }
            val keyword = context.keywords.randomOrNull(rng) ?: "тайна"
            val keyword2 = context.keywords.getOrElse(1) { "дорога" }
            val place = context.mainPlace
            
            // Выбираем подходящий шаблон
            val matchingTemplates = context.sentenceTemplates.filter { it.intent == intent }
            val template = if (matchingTemplates.isNotEmpty()) {
                matchingTemplates.random(rng)
            } else {
                context.sentenceTemplates.random(rng)
            }
            
            // Заполняем шаблон
            var paragraph = fillTemplate(template.pattern, hero, hero2, keyword, keyword2, place, rng)
            
            // Добавляем связность
            if (previousParagraphs.isNotEmpty() && rng.nextFloat() < 0.3f) {
                val transition = context.transitionPhrases.randomOrNull(rng)
                if (transition != null) {
                    paragraph = "$transition ${paragraph[0].lowercase()}${paragraph.substring(1)}"
                }
            }
            
            // Применяем стиль
            paragraph = applyStyle(paragraph, context.style)
            
            return paragraph
        }
        
        /**
         * Заполняет шаблон конкретными значениями
         */
        private fun fillTemplate(
            template: String,
            hero: String,
            hero2: String,
            keyword: String,
            keyword2: String,
            place: String,
            rng: Random
        ): String {
            var result = template
            
            result = result.replace("{hero}", hero.replaceFirstChar { it.uppercase() })
            result = result.replace("{hero2}", hero2)
            result = result.replace("{keyword}", keyword)
            result = result.replace("{keyword2}", keyword2)
            result = result.replace("{place}", place)
            result = result.replace("{number}", rng.nextInt(1, 100).toString())
            result = result.replace("{name}", hero)
            result = result.replace("{object}", listOf("дверь", "окно", "стол", "ящик", "конверт").random(rng))
            
            return result
        }
        
        /**
         * Применяет стилистические параметры
         */
        private fun applyStyle(text: String, style: String): String {
            return when (style) {
                "Лаконичный" -> {
                    val sentences = text.split(". ").take(2)
                    sentences.joinToString(". ") + "."
                }
                "Поэтический" -> text + " Слова падали, как листья осенью."
                "Драматичный" -> text + " Сердце билось чаще."
                "Ироничный" -> text + " Ну, это была точно не лучшая идея."
                else -> text
            }
        }
    }

    // === ГЕНЕРАТОР ДИАЛОГОВ ===
    object DialogueGenerator {
        
        /**
         * Генерирует диалог на основе модели интенций
         */
        fun generate(
            context: StoryContext,
            prevIntent: String,
            rng: Random
        ): String {
            val hero = context.heroes.firstOrNull() ?: "он"
            val hero2 = context.heroes.getOrElse(1) { "она" }
            val keyword = context.keywords.randomOrNull(rng) ?: "тайна"
            
            // Модель Маркова интенций
            val intent = when (prevIntent) {
                "вопрос" -> if (rng.nextFloat() < 0.7f) "ответ" else "вопрос"
                "ответ" -> listOf("утверждение", "вопрос", "просьба").random(rng)
                "утверждение" -> if (rng.nextFloat() < 0.4f) "вопрос" else "утверждение"
                else -> listOf("вопрос", "утверждение").random(rng)
            }
            
            // Выбираем подходящий паттерн
            val pattern = context.dialoguePatterns
                .filter { it.intent == intent }
                .randomOrNull(rng) ?: generateDefaultDialogue(intent, hero, hero2, keyword)
            
            var dialogue = pattern.pattern
                .replace("{speaker}", hero)
                .replace("{listener}", hero2)
                .replace("{keyword}", keyword)
            
            return dialogue
        }
        
        private fun generateDefaultDialogue(intent: String, hero: String, hero2: String, keyword: String): DialoguePattern {
            return when (intent) {
                "вопрос" -> DialoguePattern("вопрос", "— Ты знаешь про $keyword? — спросил $hero.")
                "ответ" -> DialoguePattern("ответ", "— Да, конечно, — ответил $hero2.")
                "утверждение" -> DialoguePattern("утверждение", "— $keyword — это важно, — сказал $hero.")
                "просьба" -> DialoguePattern("просьба", "— Помоги мне, — попросил $hero.")
                else -> DialoguePattern("утверждение", "— Это интересно, — заметил $hero.")
            }
        }
    }

    // === ГЛАВНАЯ ФУНКЦИЯ ГЕНЕРАЦИИ ===
    
    fun generate(config: GenerationConfig): GeneratedStory {
        val rawOriginal = config.originalText.trim()
        val original = if (config.fixGrammar) GrammarCorrector.correct(rawOriginal) else rawOriginal
        val targetChars = config.pages * CHARS_PER_PAGE
        val rng = Random(original.hashCode() + System.nanoTime().toInt())
        
        // Извлекаем героев и ключевые слова
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
        
        // Создаём контекст истории
        val context = buildContext(original, config, heroes, keywords)
        
        // Генерируем заголовок
        val title = if (config.bookTitle.isNotBlank()) {
            config.bookTitle.trim()
        } else {
            generateTitle(original, keywords, config.genre)
        }
        
        // Строим структуру повествования
        val narrativeArc = NarrativeArc.buildArc(config.pages, config.genre, context.isPhilosophical)
        
        // Генерируем текст
        val sb = StringBuilder()
        sb.append(original)
        if (!original.endsWith("\n")) sb.append("\n\n")
        
        // Добавляем мостик от оригинала к продолжению
        sb.append(generateBridge(context, rng))
        sb.append("\n\n")
        
        val recentParagraphs = mutableListOf<String>()
        var curPage = 1
        
        // Генерируем абзацы согласно структуре
        for ((idx, intent) in narrativeArc.withIndex()) {
            if (sb.length >= targetChars) break
            
            // Разделитель страниц
            if (idx > 0 && idx % 5 == 0 && curPage < config.pages) {
                curPage++
                sb.append("\n\n— — — Страница $curPage — — —\n\n")
            }
            
            // Генерируем абзац
            val paragraph = ParagraphBuilder.generate(intent, context, recentParagraphs, rng)
            
            // Проверяем связность
            val isCoherent = checkCoherence(paragraph, recentParagraphs)
            if (!isCoherent && recentParagraphs.isNotEmpty()) {
                // Пробуем ещё раз
                val alternative = ParagraphBuilder.generate(intent, context, recentParagraphs, rng)
                sb.append(alternative)
                recentParagraphs.add(alternative)
            } else {
                sb.append(paragraph)
                recentParagraphs.add(paragraph)
            }
            
            sb.append("\n\n")
            
            // Добавляем диалог иногда
            if (intent == ParagraphIntent.ACTION && rng.nextFloat() < 0.3f) {
                val dialogue = DialogueGenerator.generate(context, "утверждение", rng)
                sb.append(dialogue)
                sb.append("\n\n")
            }
            
            if (recentParagraphs.size > 10) recentParagraphs.removeAt(0)
        }
        
        // Добавляем концовку
        sb.append(generateEnding(config.genre, heroes, keywords, context.mainPlace, context.isPhilosophical, config.authorName))
        
        // Форматируем финальный текст
        var finalText = sb.toString().take(targetChars + 1000)
        finalText = smoothText(finalText)
        finalText = finalText.replace(Regex("\\s+"), " ").replace(" .", ".").replace(" ,", ",").replace("  ", " ")
        
        val actualPages = (finalText.length + CHARS_PER_PAGE - 1) / CHARS_PER_PAGE
        
        return GeneratedStory(
            title = title,
            fullText = finalText,
            chars = finalText.length,
            pages = actualPages,
            heroes = if (heroes.isEmpty()) listOf("он") else heroes,
            keywords = keywords,
            correctedOriginal = original
        )
    }
    
    // === ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ===
    
    private fun buildContext(
        original: String,
        config: GenerationConfig,
        heroes: List<String>,
        keywords: List<String>
    ): StoryContext {
        val isPhilosophical = detectPhilosophical(original, keywords)
        val mainPlace = if (config.customPlace.isNotEmpty()) {
            config.customPlace
        } else if (config.useLearned && config.learnedPlaces.isNotEmpty()) {
            config.learnedPlaces.random()
        } else {
            randomPlaceFull(config.genre, keywords, original)
        }
        
        val sentenceTemplates = TextAnalyzer.extractSentenceTemplates(
            if (config.useLearned) config.learnedPhrases else emptyList(),
            config.genre
        )
        
        val transitionPhrases = TextAnalyzer.extractTransitionPhrases(
            if (config.useLearned) config.learnedPhrases else emptyList()
        )
        
        // Создаём базовые паттерны диалогов для жанра
        val dialoguePatterns = when (config.genre) {
            "Детектив" -> listOf(
                DialoguePattern("вопрос", "— Ты уверен в этом? — спросил {speaker}."),
                DialoguePattern("ответ", "— Да, улики неоспоримы, — ответил {listener}."),
                DialoguePattern("утверждение", "— Мы близко к разгадке, — сказал {speaker}.")
            )
            "Роман" -> listOf(
                DialoguePattern("вопрос", "— Почему ты молчишь? — спросил {speaker}."),
                DialoguePattern("ответ", "— Я не знаю, что сказать, — ответил {listener}.")
            )
            "Фантастика" -> listOf(
                DialoguePattern("вопрос", "— Что показывают датчики? — спросил {speaker}."),
                DialoguePattern("ответ", "— Аномалия в секторе семь, — ответил {listener}."),
                DialoguePattern("утверждение", "— Мы не одни во вселенной, — сказал {speaker}.")
            )
            else -> listOf(
                DialoguePattern("вопрос", "— Ты знаешь, что случилось? — спросил {speaker}."),
                DialoguePattern("ответ", "— Да, конечно, — ответил {listener}."),
                DialoguePattern("утверждение", "— Это изменит всё, — сказал {speaker}.")
            )
        }
        
        return StoryContext(
            originalText = original,
            genre = config.genre,
            style = config.style,
            heroes = heroes,
            mainPlace = mainPlace,
            keywords = keywords,
            sentenceTemplates = sentenceTemplates,
            dialoguePatterns = dialoguePatterns,
            transitionPhrases = transitionPhrases,
            isPhilosophical = isPhilosophical,
            authorName = config.authorName
        )
    }
    
    private fun checkCoherence(newParagraph: String, previousParagraphs: List<String>): Boolean {
        if (previousParagraphs.isEmpty()) return true
        
        val lastParagraph = previousParagraphs.last()
        val similarity = jaccard(newParagraph, lastParagraph)
        
        // Если слишком похоже — это повтор
        return similarity < 0.6
    }
    
    private fun jaccard(a: String, b: String): Double {
        val wordsA = a.lowercase().replace(Regex("[^а-яёa-z ]"), " ").split(Regex("\\s+")).filter { it.length > 3 }.toSet()
        val wordsB = b.lowercase().replace(Regex("[^а-яёa-z ]"), " ").split(Regex("\\s+")).filter { it.length > 3 }.toSet()
        if (wordsA.isEmpty() || wordsB.isEmpty()) return 0.0
        val intersection = wordsA.intersect(wordsB).size
        val union = wordsA.union(wordsB).size
        return intersection.toDouble() / union
    }
    
    private fun smoothText(text: String): String {
        // Нормализуем пробелы и пустые строки, не ломая структуру абзацев
        var result = text
        result = result.replace(Regex(" {2,}"), " ")
        result = result.replace(Regex("\n{3,}"), "\n\n")
        result = result.replace(Regex(" \n"), "\n")
        // Убираем пробелы перед знаками препинания
        result = result.replace(Regex("\\s+([.!?,:;])"), "$1")
        // Добавляем пробелы после знаков препинания, если их нет
        result = result.replace(Regex("([.!?])([^\\s\\n])"), "$1 $2")
        result = result.replace(Regex(",([^\\s])"), ", $1")
        return result.trim()
    }
    
    // === ИЗВЛЕЧЕНИЕ ГЕРОЕВ И КЛЮЧЕВЫХ СЛОВ ===
    
    private val commonFirstNames = setOf(
        "марина", "алексей", "алекс", "игорь", "анна", "елена", "дмитрий", "сергей", "ольга", "татьяна",
        "иван", "петр", "мария", "наталья", "андрей", "михаил", "юлия", "светлана", "виктор", "николай",
        "владимир", "екатерина", "александр", "александра", "ксения", "дарья", "артем", "артём", "максим",
        "кирилл", "павел", "роман", "денис", "евгений", "константин", "валентина", "галина", "лариса", "оксана", "ирина"
    )
    
    private val extensiveStopLower = setOf(
        "это", "тот", "та", "там", "тут", "как", "что", "кто", "где", "когда", "почему",
        "был", "была", "были", "будет", "есть", "быть", "можно", "нужно", "надо", "очень"
    )
    
    private fun extractHeroesV4(text: String): List<String> {
        val sentences = text.split(Regex("[.!?]+")).map { it.trim() }.filter { it.isNotEmpty() }
        val candidates = mutableListOf<String>()
        
        for (sentence in sentences.take(50)) {
            val words = sentence.split(Regex("\\s+")).map { it.replace(Regex("[^А-Яа-яЁёA-Za-z-]"), "") }.filter { it.isNotEmpty() }
            if (words.isEmpty()) continue
            
            for (word in words) {
                if (word.matches(Regex("[А-ЯЁ][а-яё]{2,}"))) {
                    val lower = word.lowercase()
                    if (lower in commonFirstNames || (lower !in extensiveStopLower && word.length in 3..15)) {
                        candidates.add(word)
                    }
                }
            }
        }
        
        return candidates.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { it.key }
            .take(3)
    }
    
    private fun extractKeywords(text: String): List<String> {
        val words = text.lowercase().replace(Regex("[^а-яёa-z ]"), " ").split(Regex("\\s+")).filter { it.length > 3 }
        val stopWords = setOf(
            "который", "которая", "которые", "потому", "однако", "только", "очень", "сейчас",
            "было", "была", "были", "будет", "можно", "нужно", "надо", "через", "между",
            "этот", "эта", "это", "тот", "та", "то", "весь", "вся", "всё", "все", "всех"
        )
        
        val filtered = words.filter { it !in stopWords }
        val freq = filtered.groupingBy { it }.eachCount()
        
        return freq.entries.sortedByDescending { it.value }
            .map { it.key }
            .take(15)
    }
    
    private fun detectPhilosophical(text: String, keywords: List<String>): Boolean {
        val lower = text.lowercase()
        val markers = listOf("жизнь", "душа", "сознание", "смысл", "свобода", "одиночество", "выбор", "страх")
        val count = markers.count { lower.contains(it) }
        
        return count >= 2 || keywords.contains("жизнь") || keywords.contains("душа")
    }
    
    private fun generateTitle(original: String, keywords: List<String>, genre: String): String {
        val key = keywords.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Тишина"
        val key2 = keywords.getOrElse(1) { "судьба" }
        
        val titles = when (genre) {
            "Боевик" -> listOf("Последний выстрел", "Операция $key", "Код $key")
            "Детектив" -> listOf("Дело № $key", "Тени $key", "Улика")
            "Фантастика" -> listOf("Станция $key", "Сигнал $key", "Коридор времени")
            "Роман" -> listOf("Когда $key молчит", "$key и $key2", "Сердце помнит")
            "Приключения" -> listOf("Путь к $key", "Тайна $key", "Экспедиция")
            "Хоррор" -> listOf("Шёпот $key", "Тени $key", "Ночь без сна")
            "Юмор" -> listOf("Невероятный $key", "$key и другие неприятности", "Как я попал...")
            else -> listOf("Когда $key молчит", "$key и $key2", "Эхо $key")
        }
        
        return titles.random()
    }
    
    private fun generateBridge(context: StoryContext, rng: Random): String {
        val hero = context.heroes.firstOrNull() ?: "он"
        val keyword = context.keywords.firstOrNull() ?: "тайна"
        val place = context.mainPlace
        
        val variants = listOf(
            "История про $keyword началась $place. $hero ещё не знал, что его ждёт.",
            "$hero стоял $place и думал о $keyword. Этот день изменит всё.",
            "Всё началось $place. $hero даже не подозревал о $keyword."
        )
        
        return variants.random(rng)
    }
    
    private fun generateEnding(genre: String, heroes: List<String>, keywords: List<String>, place: String, isPhilosophical: Boolean, authorName: String): String {
        val hero = heroes.firstOrNull() ?: "Он"
        val keyword = keywords.firstOrNull() ?: "тайна"
        val author = if (authorName.isNotBlank()) authorName else "Автор"
        
        val ending = if (isPhilosophical) {
            "И вот $hero стоит $place, размышляя о $keyword. Жизнь продолжается, но ничего уже не будет прежним."
        } else {
            "$place. $hero понял, что $keyword — это только начало. Впереди его ждёт новый путь."
        }
        
        return "\n\n$ending\n\n— Конец —\n\nСгенерировано в ZI Writer • $author"
    }
    
    private fun randomPlaceFull(genre: String, keywords: List<String> = emptyList(), original: String = "", rng: Random = Random): String {
        // Если в исходном тексте есть специфические места — учитываем их
        val lowerOrig = original.lowercase()
        val extra = mutableListOf<String>()
        if (lowerOrig.contains("море") || keywords.contains("море")) extra.addAll(listOf("на берегу моря", "у волны", "на набережной"))
        if (lowerOrig.contains("лес") || keywords.contains("лес")) extra.addAll(listOf("в лесу", "на лесной тропе", "среди деревьев"))
        if (lowerOrig.contains("город") || keywords.contains("город")) extra.addAll(listOf("на улице города", "в переулке", "на площади"))
        
        val base = when (genre) {
            "Детектив" -> listOf("в старом районе", "на заброшенном складе", "в ночном кафе", "в архиве полиции")
            "Роман" -> listOf("на берегу моря", "в маленьком городке", "в старом парке", "в уютном кафе")
            "Фантастика" -> listOf("на станции Орион", "в космопорте", "на корабле", "в лаборатории")
            "Боевик" -> listOf("на складе оружия", "в тренировочном лагере", "в зоне конфликта")
            "Приключения" -> listOf("в джунглях", "на затерянном острове", "в древнем храме")
            "Хоррор" -> listOf("в заброшенном доме", "в тёмном лесу", "в старой больнице")
            "Юмор" -> listOf("в офисе", "в супермаркете", "в маршрутке", "в парке")
            else -> listOf("в тихом городе", "на перекрёстке", "в кафе", "у окна")
        }
        
        val all = if (extra.isNotEmpty()) base + extra else base
        return all.random(rng)
    }
}
