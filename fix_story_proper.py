import pathlib

p = pathlib.Path('/home/user/zi-writer/app/src/main/java/com/zakharevich/ziwriter/StoryGenerator.kt')
txt = p.read_text(encoding='utf-8')

# Update GenerationConfig to include learned heroes, places, phrases
old_config = '''    data class GenerationConfig(
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
        val useLearned: Boolean = true
    )'''

new_config = '''    data class GenerationConfig(
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
        val learnedPhrases: List<String> = emptyList()
    )'''

txt = txt.replace(old_config, new_config)

# Update generate() to use learned heroes and places
old_heroes = '''        val extractedHeroes = extractHeroesV4(original)
        val heroes = if (config.customHeroes.isNotEmpty()) config.customHeroes else extractedHeroes
        val keywords = extractKeywords(original)
        val mainPlace = if (config.customPlace.isNotEmpty()) config.customPlace else randomPlaceFull(config.genre)
        val placeNoun = randomPlaceNoun(config.genre)'''

new_heroes = '''        val extractedHeroes = extractHeroesV4(original)
        // Use learned heroes if available - proper accumulation
        val heroes = when {
            config.customHeroes.isNotEmpty() -> config.customHeroes
            config.useLearned && config.learnedHeroes.isNotEmpty() -> {
                // Mix original heroes with learned heroes
                val learned = config.learnedHeroes.filter { it.length in 3..20 }.shuffled().take(2)
                (extractedHeroes + learned).distinct().take(3).ifEmpty { extractedHeroes }
            }
            else -> extractedHeroes
        }
        val keywords = extractKeywords(original)
        // Use learned places if available
        val mainPlace = when {
            config.customPlace.isNotEmpty() -> config.customPlace
            config.useLearned && config.learnedPlaces.isNotEmpty() -> {
                if (kotlin.random.Random.nextFloat() < 0.6f) config.learnedPlaces.random() else randomPlaceFull(config.genre)
            }
            else -> randomPlaceFull(config.genre)
        }
        val placeNoun = if (config.useLearned && config.learnedPlaces.isNotEmpty() && kotlin.random.Random.nextFloat() < 0.5f) {
            // Extract noun from learned place
            config.learnedPlaces.random().replace(Regex("^(в|на|у|за|под|над|перед|между|около|возле)\\s+"), "").trim().take(30).ifBlank { randomPlaceNoun(config.genre) }
        } else randomPlaceNoun(config.genre)'''

txt = txt.replace(old_heroes, new_heroes)

# Update generateBridge to use learned places and phrases
old_bridge_call = '''        sb.append(generateBridge(heroes, mainPlace, isPhil, config.learnedTemplates))'''

new_bridge_call = '''        sb.append(generateBridge(heroes, mainPlace, isPhil, config.learnedTemplates, config.learnedPlaces, config.learnedPhrases))'''

txt = txt.replace(old_bridge_call, new_bridge_call)

# Update generateBridge function signature and implementation
old_bridge_func = '''    private fun generateBridge(heroes: List<String>, placeFull: String, isPhilosophical: Boolean, learned: List<String> = emptyList()): String {
        val filtered = learned.filter { isValidLearnedTemplate(it) && it.length in 40..250 }
        if (filtered.isNotEmpty() && kotlin.random.Random.nextFloat() < 0.6f) {
            // Use learned material for bridge 60% of time when available
            val base = filtered.random()
            val hero = heroes.firstOrNull() ?: "он"
            return "$base $hero оказался $placeFull, и в этот момент всё изменилось."
        }'''

new_bridge_func = '''    private fun generateBridge(heroes: List<String>, placeFull: String, isPhilosophical: Boolean, learned: List<String> = emptyList(), learnedPlaces: List<String> = emptyList(), learnedPhrases: List<String> = emptyList()): String {
        val filtered = learned.filter { isValidLearnedTemplate(it) && it.length in 40..250 }
        // Use learned phrases to make bridge more natural
        val phrase = if (learnedPhrases.isNotEmpty() && kotlin.random.Random.nextFloat() < 0.4f) learnedPhrases.random() + ", " else ""
        if (filtered.isNotEmpty() && kotlin.random.Random.nextFloat() < 0.5f) {
            // Use learned material for bridge but generate own, not just insert
            val base = filtered.random().take(120)
            val hero = heroes.firstOrNull() ?: "он"
            // Generate own sentence using learned components, not just insert
            return "${phrase.replaceFirstChar { it.uppercase() }}${base.trim().trimEnd('.')} — $hero оказался $placeFull, и в этот момент всё изменилось."
        }'''

txt = txt.replace(old_bridge_func, new_bridge_func)

# Update narrative paragraph generation to use learned phrases and generate own, not just insert
# We already have boost logic, but we need to make it generate own using learned components

old_narrative_learned = '''    private fun generateNarrativeParagraph(genre: String, style: String, heroes: List<String>, keywords: List<String>, placeFull: String, placeNoun: String, used: MutableMap<String, MutableSet<Int>>, learned: List<String> = emptyList(), useLearned: Boolean = true, recentParagraphs: List<String> = emptyList(), usedBanned: Set<String> = emptySet(), usedStarters: Map<String, Int> = emptyMap()): String {
        val filteredLearnedN = learned.filter { isValidLearnedTemplate(it) }
        if (useLearned && filteredLearnedN.isNotEmpty() && Random.nextFloat() < 0.55f) {
            val key = "${genre}_learned"
            val usedSet = used.getOrPut(key) { mutableSetOf() }
            if (usedSet.size >= filteredLearnedN.size) usedSet.clear()
            var attempts = 0
            var idx: Int
            var learnedText: String
            do {
                val available = filteredLearnedN.indices.filter { it !in usedSet }
                idx = if (available.isNotEmpty()) available.random() else Random.nextInt(filteredLearnedN.size)
                learnedText = filteredLearnedN[idx]
                attempts++
            } while (attempts < 5 && recentParagraphs.any { jaccard(learnedText, it) > 0.5 })
            usedSet.add(idx)
            val hero = heroes.firstOrNull() ?: "он"
            if (Random.nextFloat() < 0.15f && !learnedText.contains(hero, ignoreCase = true)) {
                learnedText = "${hero.replaceFirstChar { it.uppercase() }} подумал: \\"$learnedText\\""
            }
            return if (style == "Лаконичный") {
                learnedText.split(". ").take(2).joinToString(". ") + "."
            } else {
                learnedText
            }
        }'''

new_narrative_learned = '''    private fun generateNarrativeParagraph(genre: String, style: String, heroes: List<String>, keywords: List<String>, placeFull: String, placeNoun: String, used: MutableMap<String, MutableSet<Int>>, learned: List<String> = emptyList(), useLearned: Boolean = true, recentParagraphs: List<String> = emptyList(), usedBanned: Set<String> = emptySet(), usedStarters: Map<String, Int> = emptyMap(), learnedPhrases: List<String> = emptyList(), learnedHeroes: List<String> = emptyList(), learnedPlaces: List<String> = emptyList()): String {
        val filteredLearnedN = learned.filter { isValidLearnedTemplate(it) }
        if (useLearned && filteredLearnedN.isNotEmpty() && Random.nextFloat() < 0.35f) {
            // Instead of just inserting excerpt, generate own using learned components
            val key = "${genre}_learned"
            val usedSet = used.getOrPut(key) { mutableSetOf() }
            if (usedSet.size >= filteredLearnedN.size) usedSet.clear()
            var attempts = 0
            var idx: Int
            var learnedText: String
            do {
                val available = filteredLearnedN.indices.filter { it !in usedSet }
                idx = if (available.isNotEmpty()) available.random() else Random.nextInt(filteredLearnedN.size)
                learnedText = filteredLearnedN[idx]
                attempts++
            } while (attempts < 5 && recentParagraphs.any { jaccard(learnedText, it) > 0.5 })
            usedSet.add(idx)
            // Generate own sentence based on learned, not just insert
            // Extract key elements from learned and create new
            val hero = heroes.firstOrNull() ?: learnedHeroes.firstOrNull() ?: "он"
            val place = if (learnedPlaces.isNotEmpty() && Random.nextFloat() < 0.5f) learnedPlaces.random() else placeFull
            val phrase = if (learnedPhrases.isNotEmpty() && Random.nextFloat() < 0.4f) learnedPhrases.random() else ""
            val keyword = keywords.randomOrNull() ?: ""
            // Create new sentence using learned as inspiration, not copy
            val inspired = if (learnedText.length > 50) {
                // Take structure from learned but use own heroes/places
                val words = learnedText.split(" ").take(8).joinToString(" ")
                if (phrase.isNotEmpty()) "$phrase, $words — $hero $place. ${keyword.replaceFirstChar { it.uppercase() }} витало в воздухе."
                else "$words — $hero оказался $place. $keyword не давал покоя."
            } else learnedText
            
            val finalText = if (Random.nextFloat() < 0.2f && !inspired.contains(hero, ignoreCase = true)) {
                "${hero.replaceFirstChar { it.uppercase() }} подумал: \\"$inspired\\""
            } else inspired
            
            return if (style == "Лаконичный") {
                finalText.split(". ").take(2).joinToString(". ") + "."
            } else {
                finalText
            }
        }'''

txt = txt.replace(old_narrative_learned, new_narrative_learned)

# Update calls to generateNarrativeParagraph to pass new params
# Find all calls
txt = txt.replace(
    'generateNarrativeParagraph(config.genre, config.style, heroes, keywords, curPlace, placeNoun, used, config.learnedTemplates, config.useLearned, recentParas, usedBanned, usedStarters)',
    'generateNarrativeParagraph(config.genre, config.style, heroes, keywords, curPlace, placeNoun, used, config.learnedTemplates, config.useLearned, recentParas, usedBanned, usedStarters, config.learnedPhrases, config.learnedHeroes, config.learnedPlaces)'
)

# Also need to update the direct call inside boost logic
txt = txt.replace(
    'generateNarrativeParagraph(config.genre, config.style, heroes, keywords, curPlace, placeNoun, used, config.learnedTemplates, config.useLearned, recentParas, usedBanned, usedStarters)\n                            }\n                        } else {\n                            generateNarrativeParagraph(config.genre, config.style, heroes, keywords, curPlace, placeNoun, used, config.learnedTemplates, config.useLearned, recentParas, usedBanned, usedStarters)',
    'generateNarrativeParagraph(config.genre, config.style, heroes, keywords, curPlace, placeNoun, used, config.learnedTemplates, config.useLearned, recentParas, usedBanned, usedStarters, config.learnedPhrases, config.learnedHeroes, config.learnedPlaces)\n                            }\n                        } else {\n                            generateNarrativeParagraph(config.genre, config.style, heroes, keywords, curPlace, placeNoun, used, config.learnedTemplates, config.useLearned, recentParas, usedBanned, usedStarters, config.learnedPhrases, config.learnedHeroes, config.learnedPlaces)'
)

p.write_text(txt, encoding='utf-8')
print("StoryGenerator proper accumulation updated")
