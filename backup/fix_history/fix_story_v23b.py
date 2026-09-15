import pathlib
p = pathlib.Path('/home/user/zi-writer/app/src/main/java/com/zakharevich/ziwriter/StoryGenerator.kt')
txt = p.read_text(encoding='utf-8')

# Add logic to force first 2 paragraphs to use learned when available
# Find the while loop start and inject firstParagraph flag
old_while = '''        while (sb.length < targetChars) {
            if (sb.length > original.length + 800 && outlineIdx % 4 == 0 && config.pages > 3) {'''

new_while = '''        var isFirstContentPara = true
        while (sb.length < targetChars) {
            if (sb.length > original.length + 800 && outlineIdx % 4 == 0 && config.pages > 3) {'''

txt = txt.replace(old_while, new_while)

# Now in para generation, increase chance for first paras
# Find the do { para = if (isPhil) ... } block and add logic before

old_do = '''            var para: String
            var att = 0
            do {
                para = if (isPhil) {
                    generatePhilosophicalParagraph(config.style, used, heroes, curPlace, config.learnedTemplates, config.useLearned, recentParas, usedBanned, usedStarters)
                } else {
                    generateNarrativeParagraph(config.genre, config.style, heroes, keywords, curPlace, placeNoun, used, config.learnedTemplates, config.useLearned, recentParas, usedBanned, usedStarters)
                }'''

new_do = '''            var para: String
            var att = 0
            do {
                // For first 2 content paragraphs, boost learned usage to 85% if available
                val boostLearned = isFirstContentPara && config.learnedTemplates.isNotEmpty() && config.useLearned
                val effectiveLearned = if (boostLearned) config.learnedTemplates else config.learnedTemplates
                val effectiveUseLearned = config.useLearned || boostLearned
                para = if (isPhil) {
                    generatePhilosophicalParagraph(config.style, used, heroes, curPlace, effectiveLearned, effectiveUseLearned, recentParas, usedBanned, usedStarters)
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
                                generateNarrativeParagraph(config.genre, config.style, heroes, keywords, curPlace, placeNoun, used, config.learnedTemplates, config.useLearned, recentParas, usedBanned, usedStarters)
                            }
                        } else {
                            generateNarrativeParagraph(config.genre, config.style, heroes, keywords, curPlace, placeNoun, used, config.learnedTemplates, config.useLearned, recentParas, usedBanned, usedStarters)
                        }
                    } else {
                        generateNarrativeParagraph(config.genre, config.style, heroes, keywords, curPlace, placeNoun, used, config.learnedTemplates, config.useLearned, recentParas, usedBanned, usedStarters)
                    }
                }'''

txt = txt.replace(old_do, new_do)

# After para is appended, set isFirstContentPara false after 2 paras
old_append = '''            recentParas.add(para)
            if (recentParas.size > 12) recentParas.removeAt(0)

            sb.append(para)
            sb.append("\\n\\n")
            outlineIdx++
            if (outlineIdx > config.pages * 35) break'''

new_append = '''            recentParas.add(para)
            if (recentParas.size > 12) recentParas.removeAt(0)

            sb.append(para)
            sb.append("\\n\\n")
            outlineIdx++
            if (isFirstContentPara && outlineIdx >= 2) isFirstContentPara = false
            if (outlineIdx > config.pages * 35) break'''

txt = txt.replace(old_append, new_append)

# Also add more variety to generalPool - expand it to avoid repetition
# Find generalPool and add more entries
old_pool_end = '''            "Закат был таким, будто кто-то разлил краску. Краску, которой не жалко, потому что её много.",
            "Она оставила ему кофе на столе. Кофе остыл, но жест — нет. Жест остался теплым."
        )'''

new_pool_end = '''            "Закат был таким, будто кто-то разлил краску. Краску, которой не жалко, потому что её много.",
            "Она оставила ему кофе на столе. Кофе остыл, но жест — нет. Жест остался теплым.",
            "Дождь барабанил по стеклу, будто хотел ворваться внутрь и рассказать свою историю.",
            "Он открыл книгу на случайной странице и понял, что случайностей не бывает.",
            "Город просыпался медленно, как будто не хотел отпускать ночь.",
            "Она смотрела в окно и видела не улицу, а своё отражение в будущем.",
            "Ветер шептал что-то на языке, который он почти понимал.",
            "Часы тикали громче обычного, будто отсчитывали не секунды, а возможности.",
            "Он нашел записку в кармане старого пальто. Записку, которую сам не писал.",
            "Свет в окне напротив горел всю ночь. Кто-то тоже не спал.",
            "Она улыбнулась, и в этой улыбке было больше правды, чем в тысяче слов.",
            "Дорога была пуста, но следы на ней говорили, что кто-то прошел здесь до него.",
            "Он вдохнул глубоко и почувствовал запах свободы. Запах, который забыл в городе.",
            "Книга на столе была открыта на странице, где герой делал выбор. Выбор, который предстоял и ему.",
            "Она оставила дверь приоткрытой. Приоткрытая дверь — это всегда приглашение.",
            "Он шел по улице и вдруг понял, что идет не туда, куда надо, а туда, куда хочет.",
            "Тишина в квартире была такой густой, что её можно было резать ножом.",
            "Она писала ему письма, которые никогда не отправляла. Письма, в которых была вся правда.",
            "Ветер принес не только запах, но и воспоминание. Воспоминание, которое он пытался забыть.",
            "Он посмотрел на свои руки и увидел руки человека, который что-то строит, а не разрушает."
        )'''

txt = txt.replace(old_pool_end, new_pool_end)

p.write_text(txt, encoding='utf-8')
print("story v23b fixed: first para boost + expanded pool")
