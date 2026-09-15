import pathlib
p = pathlib.Path('/home/user/zi-writer/app/src/main/java/com/zakharevich/ziwriter/StoryGenerator.kt')
txt = p.read_text(encoding='utf-8')

old_phil_sig = "    private fun generatePhilosophicalParagraph(style: String, used: MutableMap<String, MutableSet<Int>>, heroes: List<String>, placeFull: String, learned: List<String> = emptyList(), useLearned: Boolean = true, recentParagraphs: List<String> = emptyList(), usedBanned: Set<String> = emptySet(), usedStarters: Map<String, Int> = emptyMap()): String {\n        val filteredLearned = learned.filter { isValidLearnedTemplate(it) }\n        if (useLearned && filteredLearned.isNotEmpty() && Random.nextFloat() < 0.55f) {\n            val key = \"phil_learned\"\n            val usedSet = used.getOrPut(key) { mutableSetOf() }\n            if (usedSet.size >= filteredLearned.size) usedSet.clear()\n            var attempts = 0\n            var idx: Int\n            var learnedText: String\n            do {\n                val available = filteredLearned.indices.filter { it !in usedSet }\n                idx = if (available.isNotEmpty()) available.random() else Random.nextInt(filteredLearned.size)\n                learnedText = filteredLearned[idx]\n                attempts++\n            } while (attempts < 5 && recentParagraphs.any { jaccard(learnedText, it) > 0.5 })\n            usedSet.add(idx)\n            return if (style == \"Лаконичный\") {\n                learnedText.split(\". \").take(2).joinToString(\". \") + \".\"\n            } else if (style == \"Поэтический\") {\n                learnedText + \" Воздух густой, как чернила.\"\n            } else {\n                learnedText\n            }\n        }"

new_phil_sig = """    private fun generatePhilosophicalParagraph(style: String, used: MutableMap<String, MutableSet<Int>>, heroes: List<String>, placeFull: String, learned: List<String> = emptyList(), useLearned: Boolean = true, recentParagraphs: List<String> = emptyList(), usedBanned: Set<String> = emptySet(), usedStarters: Map<String, Int> = emptyMap(), learnedPhrases: List<String> = emptyList(), learnedHeroes: List<String> = emptyList(), learnedPlaces: List<String> = emptyList()): String {
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
        }"""

if old_phil_sig in txt:
    txt = txt.replace(old_phil_sig, new_phil_sig)
    print("phil patched")
else:
    print("old phil sig not found")
    # try to find start
    import re
    m = re.search(r"private fun generatePhilosophicalParagraph\(.*?\)\: String \{\n.*?filteredLearned", txt, re.DOTALL)
    print(m.group(0)[:500] if m else "no match")

# Now update call sites for philosophical
# In generate() loop, there is call generatePhilosophicalParagraph(config.style, used, heroes, curPlace, effectiveLearned, effectiveUseLearned, recentParas, usedBanned, usedStarters)
old_call = "generatePhilosophicalParagraph(config.style, used, heroes, curPlace, effectiveLearned, effectiveUseLearned, recentParas, usedBanned, usedStarters)"
new_call = "generatePhilosophicalParagraph(config.style, used, heroes, curPlace, effectiveLearned, effectiveUseLearned, recentParas, usedBanned, usedStarters, config.learnedPhrases, config.learnedHeroes, config.learnedPlaces)"
txt = txt.replace(old_call, new_call)

p.write_text(txt, encoding='utf-8')
