package com.zakharevich.ziwriter

import kotlin.random.Random
import kotlin.math.*

/**
 * Математическая модель построения фраз, диалогов и сюжета
 * Реализация теории из math_model_dialogues.md
 *
 * B = {книги}, H = герои, P = места, F = фразы, K = сюжеты
 * Coherence = α·Lexical + β·Entity + γ·Temporal
 * P(t|Q) = P_syntax * P_hero * P_place * P_plot * P_coherence
 */

object MathPlotModel {

    data class Plot(
        val id: String,
        val keywords: List<String>,
        val genre: String,
        val title: String,
        val authors: String,
        val chars: Int,
        val embedding: Set<String> = keywords.map { it.lowercase() }.toSet()
    )

    data class PlotStage(
        val stage: String, // завязка, развитие1, развитие2, кульминация, развязка
        val keywords: List<String>,
        val description: String,
        val coherenceTarget: Double = 0.4
    )

    data class ScoredPlot(
        val plot: Plot,
        val score: Double,
        val matchedKeywords: List<String>
    )

    // === 1. Genre -> Flibusta queries ===
    object GenreFlibustaMapper {
        fun getQueriesForGenre(genre: String): List<String> {
            return when (genre) {
                "Боевик" -> listOf("боевик", "спецназ", "action", "детектив боевик", "война")
                "Детектив" -> listOf("детектив", "Sherlock Holmes", "Акунин", "Донцова", "Чейз")
                "Роман" -> listOf("любовный роман", "роман любовь", "любовь", "Ремарк", "романтика")
                "Фантастика" -> listOf("фантастика", "Стругацкий", "Лукьяненко", "Булычев", "фэнтези")
                "Приключения" -> listOf("приключения", "Жюль Верн", "Майн Рид", "Стивенсон", "приключенческий роман")
                "Хоррор" -> listOf("ужасы", "хоррор", "Кинг ужасы", "мистика", "Лавкрафт")
                "Юмор" -> listOf("юмор", "юмористическая фантастика", "сатира", "юмористический роман")
                "Философская проза", "Философия" -> listOf("философия", "Достоевский", "Пелевин", "философский роман", "притча")
                "Драма" -> listOf("драма", "современная проза", "жизнь", "судьба")
                "Авто (из текста)", "Авто" -> listOf("популярные книги", "бестселлер", "классика")
                else -> listOf(genre, "книги $genre", "роман $genre")
            }
        }

        fun getGenreKeywords(genre: String): List<String> {
            return when (genre) {
                "Боевик" -> listOf("выстрел", "оружие", "цель", "атака", "память", "спецназ", "враг")
                "Детектив" -> listOf("улика", "след", "дело", "полиция", "преступление", "тайна", "расследование")
                "Роман" -> listOf("любовь", "сердце", "встреча", "чувство", "разлука", "судьба", "страсть")
                "Фантастика" -> listOf("космос", "корабль", "звезда", "галактика", "будущее", "технология", "планета")
                "Приключения" -> listOf("карта", "остров", "путешествие", "сокровище", "опасность", "поход", "открытие")
                "Хоррор" -> listOf("страх", "тьма", "шепот", "кровь", "тень", "ужас", "ночь")
                "Юмор" -> listOf("смех", "шутка", "курьез", "нелепость", "пицца", "ворон", "кофе")
                "Философская проза" -> listOf("жизнь", "душа", "смысл", "сознание", "выбор", "свобода", "одиночество")
                "Драма" -> listOf("жизнь", "судьба", "боль", "надежда", "семья", "память", "прошлое")
                else -> listOf("тайна", "дорога", "выбор")
            }
        }
    }

    // === 2. Keyword extraction (TF-like) ===
    fun extractKeywords(text: String, limit: Int = 15): List<String> {
        if (text.length < 10) return emptyList()
        val cleaned = text.lowercase()
            .replace(Regex("[^а-яёa-z ]"), " ")
            .replace(Regex("\\s+"), " ")
        val words = cleaned.split(" ").filter { it.length > 3 }
        val stop = setOf(
            "который", "которая", "которые", "которое", "которых",
            "потому", "однако", "только", "очень", "сейчас", "тогда", "чтобы", "когда", "такой", "такая", "такое", "такие",
            "было", "была", "были", "будет", "будут", "можно", "нужно", "надо", "через", "против", "между",
            "этот", "эта", "это", "эти", "этого", "этой", "этом", "тот", "та", "то", "те",
            "весь", "вся", "всё", "все", "всех", "всем", "сам", "сама", "сами", "его", "её", "ее"
        )
        val important = setOf("жизнь", "любовь", "пицца", "ворон", "кофе", "птица", "птицы", "вороны", "душа", "сердце", "тайна", "дорога", "выбор", "счастье", "свобода")
        val filtered = words.filter { it !in stop || it in important }
        val freq = filtered.groupingBy { it }.eachCount()
        return freq.entries.sortedByDescending { it.value }.map { it.key }.take(limit)
    }

    // === 3. Semantic similarity ===
    // Sim(Q,P) = Jaccard + bonus for genre + bonus for exact
    fun calculateSimilarity(qKeywords: List<String>, plotKeywords: List<String>, genreMatch: Boolean = false): Double {
        if (qKeywords.isEmpty() || plotKeywords.isEmpty()) return 0.0
        val qSet = qKeywords.map { it.lowercase() }.toSet()
        val pSet = plotKeywords.map { it.lowercase() }.toSet()

        val inter = qSet.intersect(pSet).size
        val union = qSet.union(pSet).size
        val jaccard = if (union == 0) 0.0 else inter.toDouble() / union

        var score = jaccard * 2.0

        // Bonus for exact matched keywords
        score += inter * 0.3

        // Bonus if genre matches
        if (genreMatch) score += 0.5

        // Bonus for important keywords overlap
        val importantOverlap = qSet.intersect(pSet).count { it in setOf("любовь", "тайна", "дорога", "выбор", "жизнь", "смерть", "пицца", "ворон", "кофе") }
        score += importantOverlap * 0.2

        // Length penalty - too short plots are less informative
        if (pSet.size in 3..8) score += 0.2

        return score.coerceIn(0.0, 5.0)
    }

    fun jaccardText(a: String, b: String): Double {
        val sa = a.lowercase().replace(Regex("[^а-яёa-z ]"), " ").split(Regex("\\s+")).filter { it.length > 3 }.toSet()
        val sb = b.lowercase().replace(Regex("[^а-яёa-z ]"), " ").split(Regex("\\s+")).filter { it.length > 3 }.toSet()
        if (sa.isEmpty() || sb.isEmpty()) return 0.0
        return sa.intersect(sb).size.toDouble() / sa.union(sb).size
    }

    // === 4. Select best plot by meaning ===
    fun selectBestPlot(
        introText: String,
        genre: String,
        allPlots: List<Plot>,
        threshold: Double = 0.3
    ): ScoredPlot? {
        if (allPlots.isEmpty()) return null
        val qKeywords = extractKeywords(introText, 12)
        if (qKeywords.isEmpty()) return null

        val scored = allPlots.map { plot ->
            val genreMatch = plot.genre.equals(genre, ignoreCase = true) || genre.startsWith("Авто")
            val score = calculateSimilarity(qKeywords, plot.keywords, genreMatch)
            val matched = qKeywords.filter { q -> plot.keywords.any { p -> p.equals(q, ignoreCase = true) } }
            ScoredPlot(plot, score, matched)
        }.sortedByDescending { it.score }

        val best = scored.firstOrNull() ?: return null
        return if (best.score >= threshold) best else {
            // Even if below threshold, return best if genre matches, to have at least something
            if (best.plot.genre.equals(genre, ignoreCase = true)) best else null
        }
    }

    // === 5. Build plot graph (5 stages) ===
    fun buildPlotGraph(
        bestPlot: ScoredPlot?,
        introKeywords: List<String>,
        genre: String,
        introText: String
    ): List<PlotStage> {
        val baseKeywords = bestPlot?.plot?.keywords ?: introKeywords
        val genreKeywords = GenreFlibustaMapper.getGenreKeywords(genre)

        // Combine: intro keywords + best plot keywords + genre keywords
        val combined = (introKeywords.take(4) + baseKeywords.take(4) + genreKeywords.take(2)).distinct()

        val kw1 = combined.getOrElse(0) { "тайна" }
        val kw2 = combined.getOrElse(1) { "дорога" }
        val kw3 = combined.getOrElse(2) { "выбор" }
        val kw4 = combined.getOrElse(3) { "встреча" }
        val kw5 = combined.getOrElse(4) { "судьба" }

        return when (genre) {
            "Детектив" -> listOf(
                PlotStage("завязка", listOf(kw1, "преступление"), "Звонок про $kw1. $kw1 обнаружено. Начало дела."),
                PlotStage("развитие1", listOf(kw2, "улика", kw1), "След $kw2 ведет к $kw1. Улика."),
                PlotStage("развитие2", listOf(kw3, "подозреваемый", kw2), "Подозреваемый с $kw3. $kw2 и $kw3."),
                PlotStage("кульминация", listOf(kw4, "правда", kw1), "Правда про $kw4. $kw1 раскрыто."),
                PlotStage("развязка", listOf(kw5, "справедливость"), "Справедливость $kw5. Конец дела.")
            )
            "Роман" -> listOf(
                PlotStage("завязка", listOf(kw1, "встреча"), "Встреча $kw1. $kw1 меняет всё."),
                PlotStage("развитие1", listOf(kw2, "чувство", kw1), "Чувство $kw2 к $kw1 растет."),
                PlotStage("развитие2", listOf(kw3, "разлука", kw2), "Разлука $kw3. $kw2 теряет $kw1."),
                PlotStage("кульминация", listOf(kw4, "выбор", kw1), "Выбор $kw4 между $kw1 и $kw3."),
                PlotStage("развязка", listOf(kw5, "любовь"), "Любовь $kw5 побеждает. Вместе.")
            )
            "Фантастика" -> listOf(
                PlotStage("завязка", listOf(kw1, "сигнал"), "Сигнал $kw1 из космоса. $kw1 зовет."),
                PlotStage("развитие1", listOf(kw2, "корабль", kw1), "Корабль $kw2 летит к $kw1."),
                PlotStage("развитие2", listOf(kw3, "открытие", kw2), "Открытие $kw3. $kw2 находит $kw1."),
                PlotStage("кульминация", listOf(kw4, "угроза", kw1), "Угроза $kw4. $kw1 под угрозой."),
                PlotStage("развязка", listOf(kw5, "будущее"), "Будущее $kw5. Новый мир $kw1.")
            )
            "Боевик" -> listOf(
                PlotStage("завязка", listOf(kw1, "задание"), "Задание $kw1. $kw1 — цель."),
                PlotStage("развитие1", listOf(kw2, "враг", kw1), "Враг $kw2 знает про $kw1."),
                PlotStage("развитие2", listOf(kw3, "ловушка", kw2), "Ловушка $kw3. $kw2 и $kw3."),
                PlotStage("кульминация", listOf(kw4, "бой", kw1), "Бой $kw4 за $kw1."),
                PlotStage("развязка", listOf(kw5, "победа"), "Победа $kw5. $kw1 выполнено.")
            )
            "Юмор" -> listOf(
                PlotStage("завязка", listOf(kw1, "пицца", "ворон"), "Заказ $kw1. Пицца, ворон, $kw1."),
                PlotStage("развитие1", listOf(kw2, "птицы", kw1), "Птицы $kw2 крадут $kw1."),
                PlotStage("развитие2", listOf(kw3, "кофе", kw2), "Кофе $kw3 делает птица, а не она. $kw2."),
                PlotStage("кульминация", listOf(kw4, "хаос", kw1), "Хаос $kw4. $kw1, пицца, птицы."),
                PlotStage("развязка", listOf(kw5, "смех"), "Смех $kw5. Все едят пиццу с птицами.")
            )
            "Философская проза" -> listOf(
                PlotStage("завязка", listOf(kw1, "голос"), "Голос $kw1 внутри. $kw1 зовет."),
                PlotStage("развитие1", listOf(kw2, "поиск", kw1), "Поиск $kw2 смысла $kw1."),
                PlotStage("развитие2", listOf(kw3, "встреча", kw2), "Встреча $kw3. $kw2 и $kw1."),
                PlotStage("развитие2_2", listOf(kw4, "приручение", kw1), "Приручение $kw4. $kw1 и ответственность."),
                PlotStage("развязка", listOf(kw5, "сердце"), "Сердце $kw5 видит. $kw1 — главное.")
            )
            else -> listOf(
                PlotStage("завязка", listOf(kw1, kw2), "Начало $kw1. $kw2 появляется."),
                PlotStage("развитие1", listOf(kw2, kw3, kw1), "Развитие $kw2. $kw1 и $kw3."),
                PlotStage("развитие2", listOf(kw3, kw4, kw2), "Поворот $kw3. $kw4 и $kw2."),
                PlotStage("кульминация", listOf(kw4, kw1, kw3), "Кульминация $kw4. $kw1 решает $kw3."),
                PlotStage("развязка", listOf(kw5, kw1), "Развязка $kw5. $kw1 завершено.")
            )
        }
    }

    // === 6. Coherence model ===
    data class CoherenceScore(
        val lexicalOverlap: Double,
        val entityContinuity: Double,
        val temporalContinuity: Double,
        val total: Double
    )

    fun calculateCoherence(prevSentence: String, nextSentence: String, prevHero: String?, nextHero: String?, prevPlace: String?, nextPlace: String?): CoherenceScore {
        val prevWords = prevSentence.lowercase().split(Regex("\\s+")).filter { it.length > 3 }.toSet()
        val nextWords = nextSentence.lowercase().split(Regex("\\s+")).filter { it.length > 3 }.toSet()
        val lexical = if (prevWords.isEmpty() || nextWords.isEmpty()) 0.0 else prevWords.intersect(nextWords).size.toDouble() / prevWords.union(nextWords).size

        val entity = when {
            prevHero != null && nextHero != null && prevHero == nextHero -> 1.0
            prevPlace != null && nextPlace != null && prevPlace == nextPlace -> 0.8
            prevHero != null && nextHero != null -> 0.5
            else -> 0.3
        }

        val temporalMarkers = listOf("потом", "затем", "вдруг", "тем временем", "через", "спустя", "позже")
        val hasTemporal = temporalMarkers.any { nextSentence.lowercase().contains(it) }
        val temporal = if (hasTemporal) 1.0 else 0.7

        val total = 0.5 * lexical + 0.35 * entity + 0.15 * temporal
        return CoherenceScore(lexical, entity, temporal, total)
    }

    // === 7. Convert LearnedBook to Plot ===
    fun learnedBookToPlot(book: LearningManager.LearnedBook): Plot {
        return Plot(
            id = book.path,
            keywords = book.keywords,
            genre = book.genre,
            title = book.title,
            authors = book.authors,
            chars = book.chars
        )
    }

    fun plotsFromLearnedBooks(books: List<LearningManager.LearnedBook>): List<Plot> {
        return books.map { learnedBookToPlot(it) }
    }

    // === 8. Dialogue model ===
    data class DialogueTurn(
        val speaker: String,
        val listener: String,
        val intent: String, // вопрос, ответ, утверждение, просьба
        val text: String,
        val emotion: String
    )

    fun generateDialogueTurn(
        speaker: String,
        listener: String,
        prevIntent: String,
        plotKeywords: List<String>,
        learnedPhrases: List<String>,
        rnd: Random = Random
    ): DialogueTurn {
        val intent = when (prevIntent) {
            "вопрос" -> if (rnd.nextFloat() < 0.8f) "ответ" else "вопрос"
            "ответ" -> listOf("утверждение", "вопрос", "просьба").random(rnd)
            "утверждение" -> if (rnd.nextFloat() < 0.4f) "вопрос" else "утверждение"
            else -> listOf("вопрос", "утверждение").random(rnd)
        }

        val kw = plotKeywords.randomOrNull() ?: "тайна"
        val phrase = if (learnedPhrases.isNotEmpty() && rnd.nextFloat() < 0.3f) learnedPhrases.random() else ""

        val text = when (intent) {
            "вопрос" -> "— $kw? — спросил $speaker. ${if (phrase.isNotEmpty()) phrase else ""}"
            "ответ" -> "— Да, $kw и ${plotKeywords.getOrElse(1) { "дорога" }}, — ответил $speaker."
            "просьба" -> "— $speaker, помоги с $kw, — попросил $listener."
            else -> "— $kw — это ${plotKeywords.getOrElse(1) { "выбор" }}, — сказал $speaker."
        }

        val emotion = listOf("нейтрально", "удивление", "радость", "грусть", "тревога").random(rnd)

        return DialogueTurn(speaker, listener, intent, text, emotion)
    }
}
