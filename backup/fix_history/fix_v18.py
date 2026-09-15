import pathlib
path = pathlib.Path('/home/user/zi-writer/app/src/main/java/com/zakharevich/ziwriter/StoryGenerator.kt')
code = r'''
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
        val learnedBooksTitles: List<String> = emptyList(),
        val randomSeed: Long = System.currentTimeMillis()
    )

    data class GeneratedStory(
        val title: String,
        val fullText: String,
        val chars: Int,
        val pages: Int,
        val heroes: List<String>,
        val keywords: List<String>,
        val correctedOriginal: String,
        val places: List<String> = emptyList()
    )

    data class ExtractedEntities(
        val heroes: List<String>,
        val places: List<String>
    )

    private const val CHARS_PER_PAGE = 1800

    private val nonLivingMarkers = setOf(
        "планета", "планеты", "планете", "планету", "планетой",
        "звезда", "звезды", "звезде", "звезду", "звездой",
        "город", "города", "городе", "городу", "городом",
        "станция", "станции", "станцию", "станцией",
        "корабль", "корабля", "корабле", "кораблём", "кораблем",
        "деревня", "деревни", "деревне", "деревню",
        "река", "реки", "реке", "реку", "рекой",
        "море", "моря", "морю", "морем",
        "озеро", "озера", "озере",
        "гора", "горы", "горе", "гору", "горой",
        "лес", "леса", "лесе", "лесу", "лесом",
        "остров", "острова", "острове", "острову",
        "континент", "континента", "континенте",
        "галактика", "галактики", "галактике", "галактику",
        "вселенная", "вселенной", "вселенную",
        "склад", "склада", "складе", "складу",
        "порт", "порта", "порте", "порту", "космопорт", "космопорта", "космопорте",
        "берег", "берега", "берегу", "берегом", "береге"
    )

    private val livingVerbs = setOf(
        "пошёл", "пошел", "пошла", "пошли", "идёт", "идет", "шёл", "шел",
        "сказал", "сказала", "сказали", "говорит", "говорил", "говорила",
        "подумал", "подумала", "подумали", "думает", "думал",
        "увидел", "увидела", "увидели", "видит", "смотрит", "посмотрел",
        "решил", "решила", "решили",
        "улыбнулся", "улыбнулась", "улыбнулись", "улыбается",
        "ответил", "ответила", "ответили", "отвечает",
        "спросил", "спросила", "спросили", "спрашивает",
        "крикнул", "крикнула", "крикнули",
        "прошептал", "прошептала", "шепчет",
        "встал", "встала", "встали", "сел", "села", "сели",
        "лёг", "лег", "легла", "вспрыгнул",
        "пришёл", "пришел", "пришла", "пришли", "ушёл", "ушел", "ушла",
        "вернулся", "вернулась", "вернулись",
        "заметил", "заметила", "заметили",
        "понял", "поняла", "поняли", "понимает",
        "почувствовал", "почувствовала", "чувствует",
        "вспомнил", "вспомнила", "вспоминает",
        "проснулся", "проснулась", "проснулись"
    )

    private val professionDescriptors = listOf(
        "техник", "инженер", "пилот", "капитан", "механик", "охранник",
        "исследователь", "врач", "оператор", "навигатор", "связист",
        "дежурный", "смотритель", "колонист", "андроид", "прохожий",
        "незнакомец", "человек", "сторож", "житель станции", "наблюдатель",
        "аналитик", "диспетчер", "биолог", "геолог", "физик", "химик",
        "стажёр", "практикант", "командир", "лейтенант", "сержант",
        "бортинженер", "штурман", "радист", "повар", "уборщик"
    )

    private val randomFirstNames = listOf(
        "Марк", "Саймон", "Лиза", "Кира", "Томас", "Элина", "Рей", "Алан",
        "Юна", "Кейт", "Лео", "Миро", "Нова", "Ирис", "Варда", "Олан",
        "Тарис", "Лина", "Зорн", "Кейла", "Рин", "Сора", "Янис", "Майя",
        "Дин", "Лора", "Крис", "Анна", "Вик", "Эрис", "Тао", "Луис"
    )

    private fun containsCJK(text: String): Boolean {
        for (c in text) {
            val code = c.code
            if (code in 0x3040..0x309F) return true
            if (code in 0x30A0..0x30FF) return true
            if (code in 0x4E00..0x9FFF) return true
            if (code in 0xAC00..0xD7AF) return true
        }
        return false
    }

    private fun isValidLearnedTemplate(t: String): Boolean {
        if (t.length < 30 || t.length > 400) return false
        if (containsCJK(t)) return false
        val lower = t.lowercase()
        val blacklist = listOf("посёлок", "поселок", "уезд", "префектура", "муниципалитет", "населённый пункт", "камимасики", "кумамото", "касима-мати", "嘉島町", "административный центр", "сельское поселение")
        for (b in blacklist) if (lower.contains(b)) return false
        if (lower.contains("википедия")) return false
        return true
    }

    fun detectGenreStatic(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("выстрел") || lower.contains("оружие") || lower.contains("спецоперация") -> "Боевик"
            lower.contains("улика") || lower.contains("следователь") || lower.contains("детектив") -> "Детектив"
            lower.contains("любовь") || lower.contains("сердце") || lower.contains("поцелуй") -> "Роман"
            lower.contains("космос") || lower.contains("корабль") || lower.contains("галактика") || lower.contains("звезда") || lower.contains("плането") -> "Фантастика"
            lower.contains("приключен") || lower.contains("экспедиция") -> "Приключения"
            lower.contains("призрак") || lower.contains("хоррор") -> "Хоррор"
            lower.contains("юмор") -> "Юмор"
            lower.contains("философ") || lower.contains("душа") || lower.contains("сознание") -> "Философская проза"
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

    private val commonFirstNames = setOf(
        "марина", "алексей", "алекс", "игорь", "анна", "елена", "дмитрий", "сергей", "ольга", "татьяна",
        "иван", "петр", "мария", "наталья", "андрей", "михаил", "юлия", "светлана", "виктор", "николай",
        "владимир", "екатерина", "александр", "александра", "ксения", "дарья", "артем", "артём", "максим",
        "кирилл", "павел", "роман", "денис", "евгений", "константин", "валентина", "галина", "лариса", "оксана", "ирина",
        "наташа", "саша", "леша", "дима", "лена", "катя", "маша", "даша", "женя", "костя", "вова", "паша",
        "озин", "тоби", "аврора", "орион", "марк", "лиза", "кира", "элина", "рей", "алан", "лео", "нова"
    )

    private val extensiveStopLower = setOf(
        "его", "её", "ее", "еще", "ещё", "может", "твоя", "твои", "твоё", "твой", "твоей", "твоем", "твоих", "твоим",
        "она", "он", "оно", "они", "мы", "вы", "ты", "это", "тот", "та", "те", "так", "вот", "там", "тут", "как", "что", "кто", "где", "когда", "почему", "откуда", "зачем",
        "вечер", "утро", "день", "ночь", "город", "дом", "мир", "жизнь", "время", "человек", "люди", "все", "всё", "просто", "странным", "образом", "точка", "улица", "школу", "институт", "работу", "женщин", "старикам", "учителя",
        "весь", "вся", "всю", "всех", "всем", "сам", "сама", "сами", "само", "самой", "самого", "меня", "мне", "мной", "тебя", "тебе", "тобой", "себя", "себе", "собой", "нас", "нам", "нами", "вас", "вам",
        "был", "была", "были", "будет", "будут", "есть", "быть", "можно", "нужно", "надо", "очень", "даже", "уже", "только", "однако", "потому", "поэтому", "чтобы", "если", "когда", "тогда", "сейчас", "потом",
        "обычный", "необычный", "случайный", "многолюдный", "пустой", "полный", "одинокий", "желанный", "упругий", "нежный", "влажный", "неистовый", "сознательный", "счастливый", "безрассудный", "человеческий",
        "книга", "история", "рассказ", "роман", "глава", "страница", "лист", "текст", "слово", "фраза", "сигнал", "корабль", "станция", "город", "вселенная", "галактика"
    )

    private fun extractEntitiesV6(text: String): ExtractedEntities {
        val sentences = text.split(Regex("[.!?\\n]+")).map { it.trim() }.filter { it.isNotEmpty() }
        val heroes = mutableListOf<String>()
        val places = mutableListOf<String>()
        val nameFreq = mutableMapOf<String, Int>()
        val placeFreq = mutableMapOf<String, Int>()

        for (sentence in sentences) {
            val rawWords = sentence.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
            if (rawWords.isEmpty()) continue
            for (i in rawWords.indices) {
                val clean = rawWords[i].replace(Regex("[^А-Яа-яЁёA-Za-z-]"), "")
                if (clean.length < 3 || clean.length > 18) continue
                if (!clean.matches(Regex("[А-ЯЁ][а-яё]{2,}"))) continue
                val lower = clean.lowercase()
                if (lower in extensiveStopLower) continue
                if (lower.length < 3) continue

                val prev1 = if (i > 0) rawWords[i-1].lowercase().replace(Regex("[^а-яёa-z]"), "") else ""
                val prev2 = if (i > 1) rawWords[i-2].lowercase().replace(Regex("[^а-яёa-z]"), "") else ""
                val next1 = if (i < rawWords.size-1) rawWords[i+1].lowercase().replace(Regex("[^а-яёa-z]"), "") else ""
                val next2 = if (i < rawWords.size-2) rawWords[i+2].lowercase().replace(Regex("[^а-яёa-z]"), "") else ""

                val isAfterNonLiving = prev1 in nonLivingMarkers || prev2 in nonLivingMarkers
                val isFollowedByLivingVerb = next1 in livingVerbs || next2 in livingVerbs

                if (isAfterNonLiving) {
                    val marker = if (prev1 in nonLivingMarkers) rawWords[i-1] else rawWords[i-2]
                    val placeFull = "$marker $clean"
                    places.add(placeFull)
                    placeFreq[placeFull.lowercase()] = (placeFreq[placeFull.lowercase()] ?: 0) + 1
                    continue
                }

                if (isFollowedByLivingVerb) {
                    heroes.add(clean)
                    nameFreq[lower] = (nameFreq[lower] ?: 0) + 3
                    continue
                }

                if (lower in commonFirstNames) {
                    heroes.add(clean)
                    nameFreq[lower] = (nameFreq[lower] ?: 0) + 2
                    continue
                }
            }
        }

        val distinctHeroes = heroes.distinct().sortedWith(compareByDescending<String> { nameFreq[it.lowercase()] ?: 0 }.thenBy { it })
        val distinctPlaces = places.distinct().sortedWith(compareByDescending<String> { placeFreq[it.lowercase()] ?: 0 })

        val finalHeroes = distinctHeroes.take(4).ifEmpty { listOf("Озин") }
        val finalPlaces = distinctPlaces.take(6).ifEmpty { listOf("на планете Тоби") }

        return ExtractedEntities(finalHeroes, finalPlaces)
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
        val important = setOf("жизнь", "голос", "любовь", "счастье", "душа", "сердце", "мироздание", "сознание", "сигнал", "корабль", "галактика", "вселенная", "звезда", "планета", "город", "время")
        val filtered = words.filter { it !in stopWords || it in important }
        val freq = filtered.groupingBy { it }.eachCount()
        return freq.entries.sortedByDescending { it.value }.map { it.key }.take(12)
    }

    private fun randomPlaceFull(genre: String, knownPlaces: List<String>, rnd: Random): String {
        if (knownPlaces.isNotEmpty() && rnd.nextFloat() < 0.6f) {
            return knownPlaces.random(rnd)
        }
        val places = when (genre) {
            "Фантастика" -> listOf("на краю Галактики", "на станции Орион-9", "в заброшенном космопорте", "у квантового разлома", "в городе из стекла и света", "на орбите Марса", "в лаборатории времени", "на корабле Аврора", "в куполе на Луне", "у черной дыры", "в нейросети", "в колонии на Титане", "в отсеке с криокапсулами", "на мостике звездолёта", "на планете Тоби", "у звезды Озин", "в секторе 7", "на шлюзовой палубе", "в реакторном отсеке")
            "Детектив", "Боевик" -> listOf("в старом районе города", "на заброшенном складе", "в ночном кафе", "в архиве полиции", "на крыше", "в подвале", "у окна", "на перекрестке", "в переулке", "на вокзале", "в порту", "в гараже", "в кабинете следователя", "в отделе улик", "на месте преступления", "в дежурной части", "у реки за городом")
            "Роман" -> listOf("на берегу моря", "в маленьком городке у реки", "в старом парке", "в уютном кафе", "на многолюдной улице", "в пустой квартире", "у камина", "на мосту", "в саду с розами", "на набережной", "в библиотеке", "в поезде", "в осеннем сквере", "на кухне с видом на двор")
            "Приключения" -> listOf("в джунглях Амазонки", "на затерянном острове", "на горном перевале", "в пустыне Сахара", "в древнем храме", "в пещере с кристаллами", "на пиратском корабле", "в лабиринте", "на вершине Эвереста", "в долине гейзеров", "в ущелье ветров", "на краю карты")
            "Хоррор" -> listOf("в заброшенной больнице", "в старом доме на холме", "в темном лесу", "в подвале с шепотом", "в туманной деревне", "в склепе", "в доме с зеркалами", "на кладбище", "в лесу без птиц", "в квартире 13", "в коридоре без ламп", "у заколоченного окна")
            "Юмор" -> listOf("в офисе с кофемашиной", "в супермаркете в час пик", "в маршрутке 42", "в чате домового ТСЖ", "в квартире с котом", "на корпоративе", "в очереди в поликлинике", "в лифте с соседом", "на даче у тещи", "в коворкинге", "у кулера с водой")
            else -> listOf("на берегу моря", "в тихом городе", "на перекрестке дорог", "внутри себя", "на многолюдной улице", "в пустой квартире", "у окна", "на краю мира", "в старом парке", "в кафе", "в вагоне метро", "на остановке под дождём", "на планете Тоби")
        }
        return places.random(rnd)
    }

    // === V18 TITLE GENERATOR - NATURAL ===
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
    }

    // === TIME AND STATE TRACKING FOR NON-RAGGED PLOT ===

    enum class TimeSlot { DAWN, MORNING, DAY, EVENING, NIGHT, LATE_NIGHT }

    enum class HeroState { ASLEEP, AWAKE, TIRED }

    data class StoryState(
        var time: TimeSlot = TimeSlot.MORNING,
        var heroState: HeroState = HeroState.AWAKE,
        var hasWokenUp: Boolean = true, // герой уже проснулся в начале
        var hasFallenAsleep: Boolean = false,
        var paragraphsSinceWake: Int = 5,
        var paragraphsSinceSleep: Int = 100
    )

    private class StoryContext(
        val mainHero: String,
        val heroes: MutableList<String>,
        val places: MutableList<String>,
        val professionPool: MutableList<String>,
        val namePool: MutableList<String>,
        val usedSentences: MutableSet<String>,
        val usedTrigrams: MutableSet<String>,
        val recentParas: MutableList<String>,
        var currentPlace: String,
        var lastUsedName: Boolean,
        var pageChars: Int,
        var introducedProfessions: MutableMap<String, String>,
        val state: StoryState,
        val rnd: Random
    )

    private fun getProfessionName(prof: String, ctx: StoryContext): String {
        if (ctx.introducedProfessions.containsKey(prof)) {
            return ctx.introducedProfessions[prof]!!
        }
        val giveName = ctx.rnd.nextBoolean()
        return if (giveName) {
            val name = if (ctx.namePool.isNotEmpty()) {
                val n = ctx.namePool.random(ctx.rnd)
                ctx.namePool.remove(n)
                n
            } else randomFirstNames.random(ctx.rnd)
            val full = "$name, $prof"
            ctx.introducedProfessions[prof] = name
            if (!ctx.heroes.contains(name)) ctx.heroes.add(name)
            full
        } else {
            ctx.introducedProfessions[prof] = prof
            prof
        }
    }

    private fun fixPronouns(text: String, ctx: StoryContext): String {
        var res = text
        val mainCap = ctx.mainHero.replaceFirstChar { it.uppercase() }

        val sentences = res.split(Regex("(?<=[.!?])\\s+")).toMutableList()
        val fixed = mutableListOf<String>()
        var lastWasPronoun = !ctx.lastUsedName

        for (i in sentences.indices) {
            var s = sentences[i].trim()
            if (s.isEmpty()) continue

            if (s.startsWith("Он ") || s.startsWith("Он,") || s.startsWith("Он —") || s.startsWith("Она ") || s.startsWith("Они ")) {
                if (lastWasPronoun || ctx.rnd.nextFloat() < 0.8f) {
                    s = s.replaceFirst(Regex("^(Он|Она|Они)\\b"), mainCap)
                    lastWasPronoun = false
                } else {
                    lastWasPronoun = true
                }
            } else if (s.startsWith("он ") || s.startsWith("она ") || s.startsWith("они ")) {
                if (lastWasPronoun || ctx.rnd.nextFloat() < 0.8f) {
                    s = mainCap + " " + s.substringAfter(" ")
                    lastWasPronoun = false
                } else {
                    lastWasPronoun = true
                }
            } else {
                if (s.contains(Regex("\\bон\\b")) && ctx.rnd.nextFloat() < 0.65f) {
                    s = s.replace(Regex("\\bон\\b"), ctx.mainHero.lowercase())
                }
                if (s.contains("Кто-то") || s.contains("кто-то") || s.contains("Кто то")) {
                    val prof = ctx.professionPool.random(ctx.rnd)
                    val profName = getProfessionName(prof, ctx)
                    s = s.replace(Regex("Кто-то|кто-то|Кто то|кто то"), profName)
                }
            }
            fixed.add(s)
        }

        ctx.lastUsedName = !lastWasPronoun
        return fixed.joinToString(" ")
    }

    // Sentence templates with tags for temporal consistency
    data class TaggedSentence(
        val text: String,
        val requiresAwake: Boolean? = null, // null = any, true = must be awake, false = must be asleep
        val setsAwake: Boolean? = null, // what state after
        val timeHint: TimeSlot? = null,
        val isWakeEvent: Boolean = false,
        val isSleepEvent: Boolean = false
    )

    private val fantasticTagged = listOf(
        TaggedSentence("{HERO} проверил показатели на главном экране. Все системы корабля работали в штатном режиме, но лёгкая дрожь корпуса говорила о близости гравитационной аномалии у {PLACE}.", requiresAwake = true),
        TaggedSentence("{HERO} знал, что тридцатисемичасовой день на {PLACE} выматывает даже самых опытных. Поэтому каждый ритуал подготовки к вахте был важен.", requiresAwake = true, timeHint = TimeSlot.MORNING),
        TaggedSentence("Свет от далёкой звезды резал пустоту и падал на обшивку. В этом свете {PLACE} выглядел одновременно чужим и родным.", requiresAwake = true),
        TaggedSentence("{HERO} открыл бортовой журнал и сделал запись. Запись была короткой, но в ней было всё — усталость, надежда и тихое понимание, что путь ещё долог.", requiresAwake = true),
        TaggedSentence("В заброшенном космопорте, где когда-то кипела жизнь, теперь было тихо. {HERO} прошёл мимо рядов пустых контейнеров и почувствовал, как за одной строкой протокола может скрываться целая эпоха.", requiresAwake = true),
        TaggedSentence("{HERO} вспомнил слова старого капитана: космос не прощает спешки. На {PLACE} это правило работало особенно чётко.", requiresAwake = true),
        TaggedSentence("Сигнал, который поймал {HERO}, не был похож на обычный шум. Он был структурирован, почти как речь.", requiresAwake = true),
        TaggedSentence("{PROF} доложил, что реактор стабилен. {HERO} кивнул и продолжил осмотр.", requiresAwake = true),
        TaggedSentence("На {PLACE} время текло иначе. Часы показывали одно, а тело чувствовало другое. {HERO} научился доверять телу больше, чем приборам.", requiresAwake = true),
        TaggedSentence("{HERO} посмотрел на фотографию, приклеенную к панели. На ней был город из стекла и света.", requiresAwake = true),
        TaggedSentence("Квантовый разлом за бортом пульсировал, как живое сердце. {HERO} наблюдал за ним и понимал, что это не просто явление.", requiresAwake = true),
        TaggedSentence("{PROF} тихо сказал, что станция Орион-9 молчала двадцать лет не просто так. {HERO} согласился — тишина в космосе всегда имеет причину.", requiresAwake = true),
        TaggedSentence("{HERO} нашёл в архиве дневник предыдущего экипажа. Последняя запись была короткой: они внутри.", requiresAwake = true),
        TaggedSentence("Искусственный интеллект корабля отказался выполнять приказ. {HERO} не стал спорить, а спросил, почему.", requiresAwake = true),
        TaggedSentence("Город из стекла парил над бездной без видимой опоры. {HERO} долго смотрел на него, прежде чем решиться подойти ближе.", requiresAwake = true),
        TaggedSentence("{HERO} понял, что корабль Аврора искал двести лет не объект, а самого себя.", requiresAwake = true),
        TaggedSentence("Звёзды гасли одна за другой, будто кто-то выключал свет. {HERO} отметил координаты и передал данные {PROF2}.", requiresAwake = true, timeHint = TimeSlot.NIGHT),
        TaggedSentence("{HERO} проснулся в криокапсуле от тихого голоса системы. Система сказала добро пожаловать домой, но дома давно не было — только {PLACE}.", requiresAwake = false, setsAwake = true, isWakeEvent = true, timeHint = TimeSlot.MORNING),
        TaggedSentence("Нейросеть заговорила голосом, которого не было в базе. {HERO} узнал этот голос и понял, что прошлое экипажа всё ещё живёт в проводах корабля.", requiresAwake = true),
        TaggedSentence("Время на {PLACE} было не линией, а лабиринтом. {HERO} шёл по нему осторожно, оставляя метки.", requiresAwake = true),
        TaggedSentence("{HERO} включил двигатель, который работал на воспоминаниях. Чем больше {HERO} помнил, тем дальше корабль мог уйти от {PLACE}.", requiresAwake = true),
        TaggedSentence("{PROF} принёс кофе, который давно остыл. {HERO} взял кружку и поблагодарил, потому что жест был важнее температуры напитка.", requiresAwake = true),
        TaggedSentence("На мостике было тихо. Только аварийное освещение мигало красным, и {HERO} чувствовал, как тишина давит на уши сильнее любого шума.", requiresAwake = true, timeHint = TimeSlot.NIGHT),
        TaggedSentence("{HERO} записал в журнал: свет от звезды разрезал пустоту, как нож. Красиво, но неточно. Пустота не режется, она просто есть.", requiresAwake = true),
        TaggedSentence("Корабль дрейфовал, и {HERO} позволил себе минуту бездействия. Минуту, когда можно просто смотреть на {PLACE} и ни о чём не думать.", requiresAwake = true),
        TaggedSentence("{HERO} проверил скафандр. Шов на левом рукаве снова начал расходиться, и {HERO} сделал пометку попросить {PROF} зашить его после вахты.", requiresAwake = true),
        TaggedSentence("Сигнал пришёл в 03:14. Три коротких, один длинный. {HERO} записал его и понял, что это не азбука Морзе, а что-то старше.", requiresAwake = true, timeHint = TimeSlot.LATE_NIGHT),
        TaggedSentence("{PROF2} спросил, боится ли {HERO}. {HERO} честно ответил, что боится, но идёт дальше, потому что бояться и делать — это и есть смелость.", requiresAwake = true),
        TaggedSentence("{HERO} нашёл в отсеке старый плёночный фотоаппарат. Плёнка внутри была засвечена, но сам факт находки говорил, что здесь были люди до них.", requiresAwake = true),
        TaggedSentence("{HERO} сел у иллюминатора и стал смотреть на {PLACE}. Планета медленно вращалась, и каждый её оборот был похож на вдох.", requiresAwake = true),
        TaggedSentence("В журнале предыдущего экипажа {HERO} нашёл рисунок. Рисунок был детским, но подпись внизу была взрослой: не входите.", requiresAwake = true),
        TaggedSentence("{HERO} понял, что сигнал из будущего — это не мистика, а физика, которую они ещё не выучили.", requiresAwake = true),
        TaggedSentence("Город был пуст, но свет в окнах горел. {HERO} не стал входить сразу, а сначала обошёл его по периметру, как учил устав.", requiresAwake = true),
        TaggedSentence("{HERO} связался с {PROF}. Связь шипела, но голос {PROF} был разборчив.", requiresAwake = true),
        TaggedSentence("Корабль летел триста лет к звезде, которой уже нет. {HERO} знал это, но продолжал лететь, потому что цель была не в звезде.", requiresAwake = true),
        TaggedSentence("{HERO} закрыл глаза и попытался вспомнить, как пахнет земля после дождя. Память услужливо подсунула запах озона на {PLACE}.", requiresAwake = true),
        TaggedSentence("{HERO} лёг спать после вахты, оставив дежурному {PROF} контроль над системами. Сон на {PLACE} был чутким, но необходимым.", requiresAwake = true, setsAwake = false, isSleepEvent = true, timeHint = TimeSlot.NIGHT),
        TaggedSentence("{HERO} встал рано, ещё до сигнала подъёма, и пошёл готовить корабль к очередной вахте. Так начинался новый день на {PLACE}.", requiresAwake = true, timeHint = TimeSlot.DAWN),
        TaggedSentence("Утро на {PLACE} началось с проверки связи. {HERO} включил передатчик и услышал тишину, в которой был скрыт смысл.", requiresAwake = true, timeHint = TimeSlot.MORNING)
    )

    private val generalTagged = listOf(
        TaggedSentence("{HERO} шёл медленно, прислушиваясь к каждому шороху на {PLACE}. Мысли путались, но одна была чёткой — назад дороги нет, только вперёд.", requiresAwake = true),
        TaggedSentence("Вечер наступал постепенно, и {HERO} записывал в дневник одно слово, зачёркивал и писал другое, пытаясь поймать точное чувство от {PLACE}.", requiresAwake = true, timeHint = TimeSlot.EVENING),
        TaggedSentence("Тишина на {PLACE} была обманчивой. За ней скрывалось что-то настойчивое, что требовало внимания, и {HERO} учился это слышать.", requiresAwake = true),
        TaggedSentence("{HERO} вспомнил слова старого смотрителя: бойся не того, кто ищет, а того, кто уже нашёл и молчит. На {PLACE} это звучало особенно правдиво.", requiresAwake = true),
        TaggedSentence("Воздух пах дождём и чем-то ещё — тем, что бывает только перед большими переменами. {HERO} вдохнул и понял, что перемена уже началась.", requiresAwake = true),
        TaggedSentence("{HERO} стоял у окна и смотрел на улицу {PLACE}. Люди спешили по своим делам, и у каждого была своя дорога.", requiresAwake = true),
        TaggedSentence("Время на {PLACE} текло иначе. Секунда длилась вечность, а вечность пролетала за секунду, и {HERO} привыкал к этому ритму.", requiresAwake = true),
        TaggedSentence("Дорога вела вперёд, и {HERO} не знал, куда именно, но знал, что идти надо туда, где свет от звезды режет пустоту.", requiresAwake = true),
        TaggedSentence("{HERO} сделал глоток холодного кофе и поморщился. Кофе был горьким, как правда, которую {PROF} сказал утром на брифинге.", requiresAwake = true, timeHint = TimeSlot.MORNING),
        TaggedSentence("{HERO} открыл блокнот. На последней странице было написано начать сначала. {HERO} улыбнулся и начал, потому что на {PLACE} всегда начинают сначала.", requiresAwake = true),
        TaggedSentence("Ветер принёс запах перемен с {PLACE}. Запах, который бывает только раз в жизни, и {HERO} не хотел его пропустить.", requiresAwake = true),
        TaggedSentence("Дверь скрипнула, и за ней была неизвестность. {HERO} шагнул в неё, потому что на {PLACE} неизвестность — это и есть жизнь.", requiresAwake = true),
        TaggedSentence("{HERO} оставил записку, что ушёл искать себя на {PLACE}. Вернётся, когда найдёт.", requiresAwake = true),
        TaggedSentence("Свет погас, но {HERO} уже не боялся темноты на {PLACE}. Темнота — это просто отсутствие света, а свет {HERO} умел зажигать сам.", requiresAwake = true, timeHint = TimeSlot.NIGHT),
        TaggedSentence("{HERO} стоял и понимал, что всё только начинается. История, которую {HERO} сам пишет на {PLACE}, ещё не знает своего конца.", requiresAwake = true),
        TaggedSentence("Дождь стучал по крыше модуля на {PLACE}, как будто хотел что-то сказать. {HERO} слушал и чувствовал, что это важно для всех.", requiresAwake = true),
        TaggedSentence("Закат на {PLACE} был таким, будто кто-то разлил краску. Краску, которой не жалко, потому что её много во Вселенной.", requiresAwake = true, timeHint = TimeSlot.EVENING),
        TaggedSentence("{PROF} оставил {HERO} кофе на столе. Кофе остыл, но жест остался тёплым, и {HERO} был благодарен за тепло на холодной {PLACE}.", requiresAwake = true),
        TaggedSentence("{HERO} проверил связь. Связь с Землёй шла с задержкой в двадцать минут, и это заставляло говорить короче и честнее.", requiresAwake = true),
        TaggedSentence("{HERO} нашёл в ящике стола старый ключ. Ключ ни к чему не подходил, но {HERO} оставил его, как талисман с {PLACE}.", requiresAwake = true),
        TaggedSentence("{HERO} сел на ступеньку у шлюза и закурил. Руки дрожали, но это была дрожь облегчения после долгого дня на {PLACE}.", requiresAwake = true, timeHint = TimeSlot.EVENING),
        TaggedSentence("Ночь на {PLACE} была тихой. Слишком тихой для того, что произошло днём, и {HERO} записывал тишину в журнал, как событие.", requiresAwake = true, timeHint = TimeSlot.NIGHT)
    )

    private fun pickPool(genre: String): List<TaggedSentence> {
        return when (genre) {
            "Фантастика" -> fantasticTagged
            "Детектив", "Боевик" -> generalTagged.take(12) + fantasticTagged.take(8)
            else -> generalTagged + fantasticTagged.take(10)
        }
    }

    private fun isSentenceValidForState(sentence: TaggedSentence, ctx: StoryContext): Boolean {
        // Проверка сна/бодрствования
        if (sentence.requiresAwake == true && ctx.state.heroState == HeroState.ASLEEP) return false
        if (sentence.requiresAwake == false && ctx.state.heroState == HeroState.AWAKE) return false

        // Если это событие пробуждения, а герой уже бодрствует и недавно просыпался - не давать
        if (sentence.isWakeEvent) {
            if (ctx.state.heroState == HeroState.AWAKE && ctx.state.paragraphsSinceWake < 4) return false
            if (ctx.state.hasWokenUp && ctx.state.paragraphsSinceWake < 3) return false
        }
        if (sentence.isSleepEvent) {
            if (ctx.state.heroState == HeroState.ASLEEP) return false
            if (ctx.state.paragraphsSinceSleep < 4) return false
        }

        // Проверка времени - не даём резких скачков назад
        if (sentence.timeHint != null) {
            val current = ctx.state.time
            val proposed = sentence.timeHint!!
            // если сейчас день, а предлагается рассвет - это назад, не давать если недавно было утро
            if (current == TimeSlot.DAY && proposed == TimeSlot.DAWN) return false
            if (current == TimeSlot.EVENING && (proposed == TimeSlot.MORNING || proposed == TimeSlot.DAWN)) return false
            if (current == TimeSlot.NIGHT && (proposed == TimeSlot.MORNING || proposed == TimeSlot.DAY) && ctx.state.paragraphsSinceWake < 2) {
                // ночью не даём утро без сна
                // но можно если герой спал
                if (ctx.state.heroState == HeroState.AWAKE) return false
            }
        }

        return true
    }

    private fun advanceStateAfterSentence(sentence: TaggedSentence, ctx: StoryContext) {
        if (sentence.setsAwake != null) {
            ctx.state.heroState = if (sentence.setsAwake) HeroState.AWAKE else HeroState.ASLEEP
        }
        if (sentence.isWakeEvent) {
            ctx.state.hasWokenUp = true
            ctx.state.hasFallenAsleep = false
            ctx.state.paragraphsSinceWake = 0
            ctx.state.time = TimeSlot.MORNING
        }
        if (sentence.isSleepEvent) {
            ctx.state.hasFallenAsleep = true
            ctx.state.paragraphsSinceSleep = 0
            ctx.state.time = TimeSlot.NIGHT
        }
        if (sentence.timeHint != null) {
            // плавное продвижение времени
            ctx.state.time = sentence.timeHint!!
        }
    }

    private fun generateLongParagraph(ctx: StoryContext, genre: String, style: String, learned: List<String>, useLearned: Boolean): String {
        val pool = pickPool(genre)
        val filteredLearned = learned.filter { isValidLearnedTemplate(it) }

        if (useLearned && filteredLearned.isNotEmpty() && ctx.rnd.nextFloat() < 0.12f) {
            var tmpl = filteredLearned.random(ctx.rnd)
            if (!tmpl.contains(ctx.mainHero, ignoreCase = true)) {
                tmpl = "${ctx.mainHero.replaceFirstChar { it.uppercase() }} подумал: \"$tmpl\""
            }
            if (tmpl.lowercase() !in ctx.usedSentences && jaccard(tmpl, ctx.recentParas.joinToString(" ")) < 0.55) {
                ctx.usedSentences.add(tmpl.lowercase())
                // проверяем состояние
                val fakeTagged = TaggedSentence(tmpl)
                if (isSentenceValidForState(fakeTagged, ctx)) {
                    return fixPronouns(tmpl, ctx)
                }
            }
        }

        val sentencesNeeded = when (style) {
            "Лаконичный" -> 2
            "Детальный" -> 4
            else -> 3
        }

        val chosen = mutableListOf<TaggedSentence>()
        val chosenTexts = mutableListOf<String>()
        var attempts = 0
        while (chosen.size < sentencesNeeded && attempts < 40) {
            attempts++
            val candidate = pool.random(ctx.rnd)

            if (!isSentenceValidForState(candidate, ctx)) continue

            var s = candidate.text
                .replace("{HERO}", ctx.mainHero)
                .replace("{PLACE}", ctx.currentPlace)
                .replace("{PROF}", professionDescriptors.random(ctx.rnd))
                .replace("{PROF2}", professionDescriptors.random(ctx.rnd))

            if (s.contains("{HERO2}")) {
                val second = if (ctx.heroes.size > 1) ctx.heroes[1] else {
                    val newName = if (ctx.namePool.isNotEmpty()) ctx.namePool.random(ctx.rnd) else randomFirstNames.random(ctx.rnd)
                    if (!ctx.heroes.contains(newName)) ctx.heroes.add(newName)
                    newName
                }
                s = s.replace("{HERO2}", second)
            }

            val low = s.lowercase()
            if (low in ctx.usedSentences) continue
            if (ctx.recentParas.any { jaccard(s, it) > 0.58 }) continue
            val trigrams = extractTrigrams(s)
            if (trigrams.count { it in ctx.usedTrigrams } >= 3) continue

            chosen.add(candidate)
            chosenTexts.add(s)
            ctx.usedSentences.add(low)
            ctx.usedTrigrams.addAll(trigrams)
            if (ctx.usedTrigrams.size > 700) {
                val keep = ctx.usedTrigrams.take(400).toSet()
                ctx.usedTrigrams.clear()
                ctx.usedTrigrams.addAll(keep)
            }
        }

        if (chosenTexts.isEmpty()) {
            val fallback = "${ctx.mainHero.replaceFirstChar { it.uppercase() }} стоял на ${ctx.currentPlace} и смотрел вдаль. Мысли текли медленно, как время на этой планете, и в каждой мысли было место для нового дня."
            return fixPronouns(fallback, ctx)
        }

        // Применяем изменения состояния после выбора предложений
        for (ts in chosen) {
            advanceStateAfterSentence(ts, ctx)
        }
        ctx.state.paragraphsSinceWake++
        ctx.state.paragraphsSinceSleep++

        // Продвигаем время плавно каждые 2 абзаца
        if (ctx.rnd.nextFloat() < 0.35f) {
            ctx.state.time = when (ctx.state.time) {
                TimeSlot.DAWN -> TimeSlot.MORNING
                TimeSlot.MORNING -> if (ctx.rnd.nextBoolean()) TimeSlot.DAY else TimeSlot.MORNING
                TimeSlot.DAY -> if (ctx.rnd.nextBoolean()) TimeSlot.EVENING else TimeSlot.DAY
                TimeSlot.EVENING -> TimeSlot.NIGHT
                TimeSlot.NIGHT -> if (ctx.rnd.nextFloat() < 0.3f) TimeSlot.LATE_NIGHT else TimeSlot.NIGHT
                TimeSlot.LATE_NIGHT -> TimeSlot.DAWN
            }
        }

        var paragraph = chosenTexts.joinToString(" ")
        paragraph = fixPronouns(paragraph, ctx)

        if (paragraph.length < 300) {
            val extra = pool.random(ctx.rnd)
            if (isSentenceValidForState(extra, ctx)) {
                var extraText = extra.text
                    .replace("{HERO}", ctx.mainHero)
                    .replace("{PLACE}", ctx.currentPlace)
                    .replace("{PROF}", professionDescriptors.random(ctx.rnd))
                    .replace("{PROF2}", professionDescriptors.random(ctx.rnd))
                paragraph += " " + fixPronouns(extraText, ctx)
                advanceStateAfterSentence(extra, ctx)
            }
        }

        paragraph = when (style) {
            "Поэтический" -> "$paragraph Свет падал косо, будто извиняясь за правду, и на ${ctx.currentPlace} это выглядело особенно честно."
            "Драматичный" -> "$paragraph Сердце стучало так, будто хотело вырваться и пойти туда, где ещё не был ${ctx.mainHero}."
            "Ироничный" -> "$paragraph Звучит как в кино, да? Только кино — это жизнь, которую смонтировали на ${ctx.currentPlace}."
            else -> paragraph
        }

        return paragraph
    }

    private fun smoothTextV17(text: String): String {
        val paragraphs = text.split(Regex("\\n\\n+"))
        val seenSentences = mutableSetOf<String>()
        val resultParas = mutableListOf<String>()

        for (para in paragraphs) {
            if (para.trim().startsWith("— — — Страница") || para.trim().startsWith("ПРОЛОГ") || para.trim().startsWith("ГЛАВА") || para.trim().startsWith("ЭПИЛОГ") || para.trim().startsWith("— Конец —")) {
                resultParas.add(para)
                continue
            }
            val sentences = para.split(Regex("(?<=[.!?])\\s+")).toMutableList()
            val filtered = mutableListOf<String>()
            for (s in sentences) {
                val t = s.trim()
                if (t.length < 20) { filtered.add(s); continue }
                val low = t.lowercase()
                if (low in seenSentences) continue
                if (filtered.any { jaccard(t, it) > 0.72 }) continue
                filtered.add(s)
                seenSentences.add(low)
                if (seenSentences.size > 1000) {
                    val toRemove = seenSentences.take(300).toSet()
                    seenSentences.removeAll(toRemove)
                }
            }
            if (filtered.isNotEmpty()) {
                resultParas.add(filtered.joinToString(" "))
            }
        }
        return resultParas.joinToString("\n\n")
    }

    fun generate(config: GenerationConfig): GeneratedStory {
        val rnd = Random(config.randomSeed)
        val rawOriginal = config.originalText.trim()
        val original = if (config.fixGrammar) GrammarCorrector.correct(rawOriginal) else rawOriginal
        val targetChars = config.pages * CHARS_PER_PAGE

        val entities = extractEntitiesV6(original)
        val extractedHeroes = entities.heroes
        val extractedPlaces = entities.places

        val heroesInput = if (config.customHeroes.isNotEmpty()) config.customHeroes else extractedHeroes
        val heroesMutable = heroesInput.toMutableList()
        if (heroesMutable.isEmpty()) heroesMutable.add("Озин")
        if (original.contains("Озин", ignoreCase = true) && !heroesMutable.any { it.equals("Озин", ignoreCase = true) }) {
            heroesMutable.add(0, "Озин")
        }
        val mainHero = heroesMutable.first()

        val keywords = extractKeywords(original)
        val mainPlace = if (config.customPlace.isNotEmpty()) config.customPlace else extractedPlaces.firstOrNull() ?: randomPlaceFull(config.genre, extractedPlaces, rnd)

        val title = if (config.bookTitle.isNotBlank()) config.bookTitle.trim() else generateTitleV18(original, keywords, config.genre, extractedPlaces, heroesMutable, rnd)

        val isPhil = detectPhilosophical(original, keywords)

        val ctx = StoryContext(
            mainHero = mainHero,
            heroes = heroesMutable,
            places = extractedPlaces.toMutableList(),
            professionPool = professionDescriptors.toMutableList(),
            namePool = randomFirstNames.filter { it.lowercase() !in heroesMutable.map { h -> h.lowercase() } }.toMutableList(),
            usedSentences = mutableSetOf(),
            usedTrigrams = mutableSetOf(),
            recentParas = mutableListOf(),
            currentPlace = mainPlace,
            lastUsedName = true,
            pageChars = original.length % CHARS_PER_PAGE,
            introducedProfessions = mutableMapOf(),
            state = StoryState(
                time = if (original.lowercase().contains("утро") || original.lowercase().contains("рассвет")) TimeSlot.MORNING else if (original.lowercase().contains("вечер")) TimeSlot.EVENING else if (original.lowercase().contains("ночь")) TimeSlot.NIGHT else TimeSlot.MORNING,
                heroState = HeroState.AWAKE,
                hasWokenUp = true,
                paragraphsSinceWake = 5
            ),
            rnd = rnd
        )

        val originalSentences = original.split(Regex("(?<=[.!?])\\s+")).map { it.trim().lowercase() }.filter { it.length > 20 }
        ctx.usedSentences.addAll(originalSentences)

        val sb = StringBuilder()
        sb.append(original)
        if (!original.endsWith("\n")) sb.append("\n\n")
        sb.append(generateBridgeV17(heroesMutable, mainPlace, isPhil, mainHero, rnd))
        sb.append("\n\n")

        var curPage = 1
        var outlineIdx = 0
        val outline = createOutlineV17(config.pages, config.genre, heroesMutable, keywords, isPhil)
        var paragraphsInCurrentScene = 0

        while (sb.length < targetChars) {
            if (paragraphsInCurrentScene >= 3 && rnd.nextFloat() < 0.3f) {
                val newPlace = if (config.customPlace.isNotEmpty()) config.customPlace else randomPlaceFull(config.genre, ctx.places, rnd)
                if (newPlace != ctx.currentPlace) {
                    ctx.currentPlace = newPlace
                    ctx.places.add(newPlace)
                    if (ctx.places.size > 8) ctx.places.removeAt(0)
                }
                paragraphsInCurrentScene = 0
            }

            if (outlineIdx < outline.size && sb.length > original.length + 2000 && outlineIdx % 2 == 0 && ctx.pageChars > 1200) {
                val ch = outline[outlineIdx]
                if (ch.startsWith("Глава") || ch.startsWith("Пролог") || ch.startsWith("Эпилог")) {
                    sb.append("\n${ch.uppercase()}\n\n")
                    ctx.pageChars += ch.length + 4
                }
            }

            var para: String
            var att = 0
            do {
                para = if (isPhil) {
                    generatePhilosophicalParagraphV17(config.style, ctx, config.learnedTemplates, config.useLearned)
                } else {
                    generateLongParagraph(ctx, config.genre, config.style, config.learnedTemplates, config.useLearned)
                }
                att++
                val tooSimilar = ctx.recentParas.takeLast(3).any { jaccard(para, it) > 0.58 }
                if (!tooSimilar) break
            } while (att < 6)

            if (para.length < 350) {
                val extra = generateLongParagraph(ctx, config.genre, config.style, config.learnedTemplates, config.useLearned)
                para += " " + extra
            }

            ctx.recentParas.add(para)
            if (ctx.recentParas.size > 10) ctx.recentParas.removeAt(0)

            sb.append(para)
            sb.append("\n\n")

            ctx.pageChars += para.length + 2
            paragraphsInCurrentScene++

            if (ctx.pageChars >= 1500 && curPage < config.pages && sb.length < targetChars - 500) {
                curPage++
                sb.append("— — — Страница $curPage — — —\n\n")
                ctx.pageChars = 0
            }

            outlineIdx++
            if (outlineIdx > config.pages * 20) break
            if (sb.length > targetChars + 2000) break
        }

        sb.append(generateEndingV17(config.genre, ctx.heroes, keywords, ctx.currentPlace, isPhil, config.authorName, ctx))

        var finalText = sb.toString().take(targetChars + 1500)
        finalText = smoothTextV17(finalText)
        finalText = finalText.replace(Regex("[ \\t]+"), " ").replace(Regex(" \\n"), "\n").replace(Regex("\\n {1,}"), "\n").replace(Regex("\\n{3,}"), "\n\n")
        finalText = finalText.replace(" .", ".").replace(" ,", ",").replace("  ", " ").trim()

        val actualPages = (finalText.length + CHARS_PER_PAGE - 1) / CHARS_PER_PAGE
        return GeneratedStory(title, finalText, finalText.length, actualPages, ctx.heroes.distinct().take(5), keywords, original, ctx.places.distinct())
    }

    private fun detectPhilosophical(text: String, keywords: List<String>): Boolean {
        val lower = text.lowercase()
        val markers = listOf("жизнь", "душа", "сознание", "внутренний голос", "голос", "счастье", "любовь", "мироздание", "человек", "сам с собой", "одиночеств", "мысль", "чувств", "детстве", "учителя", "мораль", "судьба", "выбор", "страх", "свобода", "простын")
        val count = markers.count { lower.contains(it) }
        val hasFirstPerson = lower.contains(" я ") || lower.contains(" мне ") || lower.contains(" меня ") || lower.startsWith("я ")
        return count >= 3 || (count >= 2 && hasFirstPerson) || keywords.contains("жизнь") || keywords.contains("голос")
    }

    private fun createOutlineV17(pages: Int, genre: String, heroes: List<String>, keywords: List<String>, isPhilosophical: Boolean): List<String> {
        return if (isPhilosophical) {
            listOf(
                "Пролог: Голос внутри",
                "Глава 1: Откуда он взялся",
                "Глава 2: Точка А — точка Б",
                "Глава 3: Право на улыбку",
                "Глава 4: Учителя и мораль",
                "Глава 5: Быть собой",
                "Эпилог: Жить без него"
            )
        } else {
            when (genre) {
                "Боевик" -> listOf("Пролог: Последний выстрел", "Глава 1: Мышечная память", "Глава 2: Точка невозврата", "Глава 3: Попадание в цель", "Эпилог: Дорога вперед")
                "Детектив" -> listOf("Пролог: Звонок в полночь", "Глава 1: Место преступления", "Глава 2: Первый свидетель", "Глава 3: Ложный след", "Эпилог: Дело закрыто")
                "Роман" -> listOf("Пролог: Встреча", "Глава 1: Письмо", "Глава 2: Вечер", "Глава 3: Расстояние", "Эпилог: Вместе")
                "Фантастика" -> listOf("Пролог: Сигнал", "Глава 1: Станция Орион", "Глава 2: Квантовый разлом", "Глава 3: Город из стекла", "Эпилог: Новая Галактика")
                "Приключения" -> listOf("Пролог: Карта", "Глава 1: Сборы", "Глава 2: Перевал", "Глава 3: Храм", "Эпилог: Возвращение")
                "Хоррор" -> listOf("Пролог: Шепот", "Глава 1: Дом на холме", "Глава 2: Зеркала", "Глава 3: Подвал", "Эпилог: Тишина")
                "Юмор" -> listOf("Пролог: Понедельник", "Глава 1: Офис", "Глава 2: Кофемашина", "Глава 3: Корпоратив", "Эпилог: Пятница")
                else -> listOf("Пролог", "Глава 1: Первая тайна", "Глава 2: Тени прошлого", "Глава 3: Испытание", "Эпилог")
            }
        }
    }

    private fun generateBridgeV17(heroes: List<String>, placeFull: String, isPhilosophical: Boolean, mainHero: String, rnd: Random): String {
        return if (isPhilosophical) {
            "С этих строк начинается попытка разобраться. Ты уже сказал главное. Дальше — только честность. Без учителей, без морали, которую тебе навязали. Только ты и то, что внутри. $placeFull — лишь декорация, главное происходит внутри."
        } else {
            val heroCap = mainHero.replaceFirstChar { it.uppercase() }
            val bridges = listOf(
                "С этих строк началась история, которая вскоре вышла за пределы привычного. $heroCap ещё не знал, что каждое слово, сказанное в тот момент на $placeFull, станет частью чего-то большего. Иногда одна секунда меняет всё, и для $heroCap эта секунда уже настала.",
                "То, что случилось на $placeFull, могло бы уместиться в одну строку протокола. Но за строкой была жизнь, и $heroCap чувствовал её вес, проверяя корабль перед вахтой.",
                "Свет от далёкой звезды падал на $placeFull косо, и $heroCap подумал, что именно так начинается новый день — не с приказа, а с тихого света, который режет пустоту.",
                "$heroCap стоял на $placeFull и понимал, что история только начинается. Всё, что было до этого, — лишь пролог к тому, что предстояло.",
                "На $placeFull время текло иначе, и $heroCap учился жить в этом новом времени, где тридцать семь часов — это один день, а один день — целая жизнь."
            )
            bridges.random(rnd)
        }
    }

    private fun generatePhilosophicalParagraphV17(style: String, ctx: StoryContext, learned: List<String>, useLearned: Boolean): String {
        val filteredLearned = learned.filter { isValidLearnedTemplate(it) }
        if (useLearned && filteredLearned.isNotEmpty() && ctx.rnd.nextFloat() < 0.12f) {
            var t = filteredLearned.random(ctx.rnd)
            if (t.lowercase() !in ctx.usedSentences) {
                ctx.usedSentences.add(t.lowercase())
                return fixPronouns(t, ctx)
            }
        }
        val phil = listOf(
            "${ctx.mainHero.replaceFirstChar { it.uppercase() }} думал об этом голосе внутри. Откуда он взялся? В детстве на ${ctx.currentPlace} его не было, а теперь он звучит тембром самого ${ctx.mainHero}.",
            "Иногда ${ctx.mainHero} казалось, что всю жизнь делал всё правильно на ${ctx.currentPlace}. Школа, институт, работа, а в итоге — тишина и голос, который говорит, что недостаточно хорош.",
            "Вспомни ту встречу на ${ctx.currentPlace}. Многолюдная улица, ${ctx.mainHero} идёт, уткнувшись в дела, и вдруг — глаза. Что это, если не шанс улыбнуться?",
            "Кто он такой, этот голос? Может, это страх быть отвергнутым, который ${ctx.mainHero} носит с собой с ${ctx.currentPlace}?",
            "Право на улыбку — это не метафора для ${ctx.mainHero}. На ${ctx.currentPlace} улыбка незнакомцу может стать началом новой истории."
        )
        val p = phil.random(ctx.rnd)
        ctx.usedSentences.add(p.lowercase())
        return fixPronouns(p, ctx)
    }

    private fun generateEndingV17(genre: String, heroes: List<String>, keywords: List<String>, placeFull: String, isPhilosophical: Boolean, authorName: String, ctx: StoryContext): String {
        val author = if (authorName.isNotBlank()) authorName else "Zakharevich Igor"
        val mainHero = heroes.firstOrNull() ?: "Озин"
        val mainCap = mainHero.replaceFirstChar { it.uppercase() }

        val endingBody = when (genre) {
            "Фантастика" -> """
                $mainCap стоял на $placeFull и смотрел, как свет от звезды медленно гаснет за горизонтом. Сигнал, который привёл его сюда, замолчал, но в этом молчании было больше смысла, чем в любых словах.

                ${professionDescriptors.random(ctx.rnd).replaceFirstChar { it.uppercase() }} подошёл тихо и встал рядом. Ничего не сказал, просто кивнул. $mainCap кивнул в ответ. На $placeFull слова были не всегда нужны.

                Корабль Аврора висел на орбите, как напоминание о том, что путь домой всегда есть, даже если дома уже нет. $mainCap знал, что вернётся, но не сегодня. Сегодня $placeFull требовал присутствия.

                — Конец —
            """.trimIndent()
            else -> """
                $mainCap стоял и смотрел вдаль с $placeFull. Всё, с чего началась эта история, теперь было частью чего-то большего. $mainCap улыбнулся, потому что путь был долгим, но он того стоил, и каждый час на $placeFull имел смысл.

                Рядом прошёл ${professionDescriptors.random(ctx.rnd)}, кивнул $mainCap, и $mainCap ответил тем же. На $placeFull даже такие мелочи имели вес.

                — Конец —
            """.trimIndent()
        }

        return "\n\n$endingBody\n\nСгенерировано в ZI Writer v18 TITLE & TIME • Автор: $author • ${keywords.take(5).joinToString(", ")} • ${heroes.take(3).joinToString(", ")} • $placeFull • Изучено: ${ctx.usedSentences.size / 10}"
    }
}
'''
path.write_text(code, encoding='utf-8')
print("StoryGenerator v18 written")
