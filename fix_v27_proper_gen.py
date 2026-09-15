import pathlib
p = pathlib.Path('/home/user/zi-writer/app/src/main/java/com/zakharevich/ziwriter/StoryGenerator.kt')
txt = p.read_text(encoding='utf-8')

# Fix GenerationConfig placeNoun regex
txt = txt.replace(
    'config.learnedPlaces.random().replace(Regex("^(в|на|у|за|под|над|перед|между|около|возле)\\s+"), "")',
    'config.learnedPlaces.random().replace(Regex("""^(в|на|у|за|под|над|перед|между|около|возле)\s+"""), "")'
)

# Rewrite generateBridge to NOT use learned templates directly
old_bridge = '''    private fun generateBridge(heroes: List<String>, placeFull: String, isPhilosophical: Boolean, learned: List<String> = emptyList(), learnedPlaces: List<String> = emptyList(), learnedPhrases: List<String> = emptyList()): String {
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

new_bridge = '''    private fun generateBridge(heroes: List<String>, placeFull: String, isPhilosophical: Boolean, learned: List<String> = emptyList(), learnedPlaces: List<String> = emptyList(), learnedPhrases: List<String> = emptyList()): String {
        // PROPER LEARNING: generate own bridge using accumulated heroes/places/phrases, NOT inserting чужой отрывок
        val hero = heroes.firstOrNull() ?: "он"
        val phrase = if (learnedPhrases.isNotEmpty() && kotlin.random.Random.nextFloat() < 0.5f) learnedPhrases.random() else ""
        val connector = if (phrase.isNotEmpty()) "${phrase.replaceFirstChar { it.uppercase() }}, " else ""
        // Use learned places if available to enrich, but generate own
        val extraPlace = if (learnedPlaces.isNotEmpty() && kotlin.random.Random.nextFloat() < 0.3f) " ${learnedPlaces.random()}" else ""
        // Do NOT use learned templates directly - only own generation based on accumulated data
        '''

if old_bridge in txt:
    txt = txt.replace(old_bridge, new_bridge)
    print("bridge fixed")
else:
    print("bridge old not found")
    # try to find partial
    import re
    m = re.search(r'private fun generateBridge.*?return if \(isPhilosophical\)', txt, re.DOTALL)
    if m:
        print(m.group(0)[:500])

# Rewrite generatePhilosophicalParagraph to remove learned insertion
old_phil = '''    private fun generatePhilosophicalParagraph(style: String, used: MutableMap<String, MutableSet<Int>>, heroes: List<String>, placeFull: String, learned: List<String> = emptyList(), useLearned: Boolean = true, recentParagraphs: List<String> = emptyList(), usedBanned: Set<String> = emptySet(), usedStarters: Map<String, Int> = emptyMap(), learnedPhrases: List<String> = emptyList(), learnedHeroes: List<String> = emptyList(), learnedPlaces: List<String> = emptyList()): String {
        val filteredLearned = learned.filter { isValidLearnedTemplate(it) }
        if (useLearned && filteredLearned.isNotEmpty() && Random.nextFloat() < 0.35f) {
            val key = "phil_learned"
            val usedSet = used.getOrPut(key) { mutableSetOf() }
            if (usedSet.size >= filteredLearned.size) usedSet.clear()
            var attempts = 0
            var idx: Int
            var learnedText: String
            do {
                val available = filteredLearned.indices.filter { it !in usedSet }
                idx = if (available.isNotEmpty()) available.random() else Random.nextInt(filteredLearned.size)
                learnedText = filteredLearned[idx]
                attempts++
            } while (attempts < 5 && recentParagraphs.any { jaccard(learnedText, it) > 0.5 })
            usedSet.add(idx)
            // Proper accumulation: generate own using learned phrase/hero/place, not just insert
            val hero = heroes.firstOrNull() ?: learnedHeroes.firstOrNull() ?: "ты"
            val phrase = if (learnedPhrases.isNotEmpty() && Random.nextFloat() < 0.5f) learnedPhrases.random() + ", " else ""
            val place = if (learnedPlaces.isNotEmpty() && Random.nextFloat() < 0.3f) learnedPlaces.random() else placeFull
            val own = if (learnedText.length > 40) {
                val head = learnedText.split(" ").take(6).joinToString(" ")
                "$phrase$head — $hero $place. ${learnedText.take(80)}"
            } else learnedText
            return if (style == "Лаконичный") {
                own.split(". ").take(2).joinToString(". ") + "."
            } else if (style == "Поэтический") {
                own + " Воздух густой, как чернила."
            } else {
                own
            }
        }'''

new_phil = '''    private fun generatePhilosophicalParagraph(style: String, used: MutableMap<String, MutableSet<Int>>, heroes: List<String>, placeFull: String, learned: List<String> = emptyList(), useLearned: Boolean = true, recentParagraphs: List<String> = emptyList(), usedBanned: Set<String> = emptySet(), usedStarters: Map<String, Int> = emptyMap(), learnedPhrases: List<String> = emptyList(), learnedHeroes: List<String> = emptyList(), learnedPlaces: List<String> = emptyList()): String {
        // PROPER LEARNING v27: NEVER insert чужой отрывок! Generate own using accumulated heroes/places/phrases
        // learned templates are ignored - we use only learnedHeroes, learnedPlaces, learnedPhrases as building blocks
        '''

if old_phil in txt:
    txt = txt.replace(old_phil, new_phil)
    print("phil fixed")
else:
    print("phil old not found")

# Rewrite generateNarrativeParagraph to remove learned insertion
old_narr = '''    private fun generateNarrativeParagraph(genre: String, style: String, heroes: List<String>, keywords: List<String>, placeFull: String, placeNoun: String, used: MutableMap<String, MutableSet<Int>>, learned: List<String> = emptyList(), useLearned: Boolean = true, recentParagraphs: List<String> = emptyList(), usedBanned: Set<String> = emptySet(), usedStarters: Map<String, Int> = emptyMap(), learnedPhrases: List<String> = emptyList(), learnedHeroes: List<String> = emptyList(), learnedPlaces: List<String> = emptyList()): String {
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

new_narr = '''    private fun generateNarrativeParagraph(genre: String, style: String, heroes: List<String>, keywords: List<String>, placeFull: String, placeNoun: String, used: MutableMap<String, MutableSet<Int>>, learned: List<String> = emptyList(), useLearned: Boolean = true, recentParagraphs: List<String> = emptyList(), usedBanned: Set<String> = emptySet(), usedStarters: Map<String, Int> = emptyMap(), learnedPhrases: List<String> = emptyList(), learnedHeroes: List<String> = emptyList(), learnedPlaces: List<String> = emptyList()): String {
        // PROPER LEARNING v27: NEVER insert чужой отрывок напрямую! 
        // Генерируем СВОЁ на основе накопленных героев, мест, оборотов
        // learned (templates) игнорируются - используем только learnedHeroes/Places/Phrases
        '''

if old_narr in txt:
    txt = txt.replace(old_narr, new_narr)
    print("narr fixed")
else:
    print("narr old not found")

# Now fix generate() loop to remove boostLearned 85% insertion
old_loop = '''            var para: String
            var att = 0
            do {
                // For first 2 content paragraphs, boost learned usage to 85% if available
                val boostLearned = isFirstContentPara && config.learnedTemplates.isNotEmpty() && config.useLearned
                val effectiveLearned = if (boostLearned) config.learnedTemplates else config.learnedTemplates
                val effectiveUseLearned = config.useLearned || boostLearned
                para = if (isPhil) {
                    generatePhilosophicalParagraph(config.style, used, heroes, curPlace, effectiveLearned, effectiveUseLearned, recentParas, usedBanned, usedStarters, config.learnedPhrases, config.learnedHeroes, config.learnedPlaces)
                } else {
                    // Force learned for first para by temporarily increasing chance inside function via direct use
                    if (boostLearned && effectiveLearned.isNotEmpty()) {
                        // Directly use learned template 85% of time for first paras
                        if (kotlin.random.Random.nextFloat() < 0.85f) {
                            val filtered = effectiveLearned.filter { isValidLearnedTemplate(it) }
                            if (filtered.isNotEmpty()) {
                                val t = filtered.random()
                                val hero = heroes.firstOrNull() ?: "он"
                                if (!t.contains(hero, ignoreCase = true) && kotlin.random.Random.nextFloat() < 0.3f) {
                                    "${hero.replaceFirstChar { it.uppercase() }} подумал: \\"$t\\""
                                } else {
                                    t
                                }
                            } else {
                                generateNarrativeParagraph(config.genre, config.style, heroes, keywords, curPlace, placeNoun, used, config.learnedTemplates, config.useLearned, recentParas, usedBanned, usedStarters, config.learnedPhrases, config.learnedHeroes, config.learnedPlaces)
                            }
                        } else {
                            generateNarrativeParagraph(config.genre, config.style, heroes, keywords, curPlace, placeNoun, used, config.learnedTemplates, config.useLearned, recentParas, usedBanned, usedStarters, config.learnedPhrases, config.learnedHeroes, config.learnedPlaces)
                        }
                    } else {
                        generateNarrativeParagraph(config.genre, config.style, heroes, keywords, curPlace, placeNoun, used, config.learnedTemplates, config.useLearned, recentParas, usedBanned, usedStarters, config.learnedPhrases, config.learnedHeroes, config.learnedPlaces)
                    }
                }'''

new_loop = '''            var para: String
            var att = 0
            do {
                // v27 PROPER: no direct insertion of чужой текст! Only own generation using accumulated data
                para = if (isPhil) {
                    generatePhilosophicalParagraph(config.style, used, heroes, curPlace, config.learnedTemplates, config.useLearned, recentParas, usedBanned, usedStarters, config.learnedPhrases, config.learnedHeroes, config.learnedPlaces)
                } else {
                    generateNarrativeParagraph(config.genre, config.style, heroes, keywords, curPlace, placeNoun, used, config.learnedTemplates, config.useLearned, recentParas, usedBanned, usedStarters, config.learnedPhrases, config.learnedHeroes, config.learnedPlaces)
                }'''

if old_loop in txt:
    txt = txt.replace(old_loop, new_loop)
    print("loop fixed")
else:
    print("loop old not found")

p.write_text(txt, encoding='utf-8')
print("StoryGenerator v27 proper written")
