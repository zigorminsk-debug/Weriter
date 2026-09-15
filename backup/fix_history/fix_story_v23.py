import pathlib, re
p = pathlib.Path('/home/user/zi-writer/app/src/main/java/com/zakharevich/ziwriter/StoryGenerator.kt')
txt = p.read_text(encoding='utf-8')

# Fix generateBridge to have variety and use learned material
old_bridge = '''    private fun generateBridge(heroes: List<String>, placeFull: String, isPhilosophical: Boolean): String {
        return if (isPhilosophical) {
            "С этих строк начинается попытка разобраться. Ты уже сказал главное. Дальше — только честность. Без учителей, без морали, которую тебе навязали. Только ты и то, что внутри. $placeFull — лишь декорация, главное происходит внутри."
        } else {
            val hero = heroes.firstOrNull() ?: "он"
            "С этих строк началась история, которая вскоре вышла за пределы привычного. $hero еще не знал, что каждое слово, сказанное в тот момент $placeFull, станет частью чего-то большего. Иногда одна секунда меняет всё."
        }
    }'''

new_bridge = '''    private fun generateBridge(heroes: List<String>, placeFull: String, isPhilosophical: Boolean, learned: List<String> = emptyList()): String {
        val filtered = learned.filter { isValidLearnedTemplate(it) && it.length in 40..250 }
        if (filtered.isNotEmpty() && kotlin.random.Random.nextFloat() < 0.6f) {
            // Use learned material for bridge 60% of time when available
            val base = filtered.random()
            val hero = heroes.firstOrNull() ?: "он"
            return "$base $hero оказался $placeFull, и в этот момент всё изменилось."
        }
        return if (isPhilosophical) {
            val variants = listOf(
                "С этих строк начинается попытка разобраться. Ты уже сказал главное. Дальше — только честность. Без учителей, без морали, которую тебе навязали. Только ты и то, что внутри. $placeFull — лишь декорация, главное происходит внутри.",
                "Ты пишешь, и это уже шаг. Шаг от точки А, где ты был винтом в системе, к точке Б, где ты сам решаешь. $placeFull вокруг, но главное — внутри.",
                "Всё началось с одной мысли, которую ты боялся сказать вслух. Мысли о том, что можно идти прямо, когда все идут направо. $placeFull — лишь фон для того, что происходит в душе.",
                "Этот текст — не попытка оправдаться. Это попытка вспомнить себя до школы, когда ты говорил то, что думаешь. $placeFull за окном, а внутри — тишина, в которой слышен настоящий голос.",
                "Иногда достаточно одной секунды, чтобы понять: ты живешь не свою жизнь. Секунды, когда поднимаешь глаза и видишь её. $placeFull, но взгляд — важнее места."
            )
            variants.random()
        } else {
            val hero = heroes.firstOrNull() ?: "он"
            val variants = listOf(
                "С этих строк началась история, которая вскоре вышла за пределы привычного. $hero еще не знал, что каждое слово, сказанное в тот момент $placeFull, станет частью чего-то большего. Иногда одна секунда меняет всё.",
                "$hero стоял $placeFull и не подозревал, что этот день станет поворотным. Всё, что было до — лишь пролог к настоящему.",
                "История, которую ты сейчас читаешь, началась $placeFull. $hero даже не догадывался, что случайная встреча изменит всё.",
                "$placeFull. Место, где всё началось. $hero думал, что это обычный день, но судьба уже готовила сюрприз.",
                "Ветер $placeFull принес запах перемен. $hero вдохнул и понял — назад дороги нет. Только вперед.",
                "$hero не знал, что $placeFull станет для него точкой невозврата. Местом, где прошлое встречается с будущим.",
                "Этот день начался как обычно, $placeFull, кофе, спешка. Но иногда обычное — лишь маска для великого.",
                "$hero проснулся $placeFull с чувством, что сегодня всё будет иначе. Интуиция редко обманывает."
            )
            variants.random()
        }
    }'''

txt = txt.replace(old_bridge, new_bridge)

# Update call to generateBridge in generate() to pass learnedTemplates
txt = txt.replace(
    'sb.append(generateBridge(heroes, mainPlace, isPhil))',
    'sb.append(generateBridge(heroes, mainPlace, isPhil, config.learnedTemplates))'
)

# Increase learned usage probability from 0.12f to 0.55f
txt = txt.replace(
    'if (useLearned && filteredLearned.isNotEmpty() && Random.nextFloat() < 0.12f)',
    'if (useLearned && filteredLearned.isNotEmpty() && Random.nextFloat() < 0.55f)'
)
# Also for narrative paragraph, same increase (there are two occurrences, second is filteredLearnedN)
# Replace both occurrences
txt = txt.replace(
    'if (useLearned && filteredLearnedN.isNotEmpty() && Random.nextFloat() < 0.12f)',
    'if (useLearned && filteredLearnedN.isNotEmpty() && Random.nextFloat() < 0.55f)'
)

# Fix ending version from v13 to v22 and IP to flibusta.net
txt = txt.replace('Сгенерировано в ZI Writer v13 OPDS FLIBUSTA', 'Сгенерировано в ZI Writer v22 CLEAN FINAL')
txt = txt.replace('Сгенерировано в ZI Writer v13 OPDS', 'Сгенерировано в ZI Writer v22 CLEAN FINAL')
txt = txt.replace('OPDS 213.5.65.159', 'flibusta.net/opds')
txt = txt.replace('Флибуста OPDS 213.5.65.159', 'flibusta.net/opds')

# Ensure first paragraph after bridge also has high chance to use learned
# In generate(), after bridge, the while loop starts generating paragraphs.
# We can add a flag to force first 2 paragraphs to use learned if available
# Modify the para generation to have higher learned chance for first few outlines

# Add more diverse generalPool to avoid repetition of "Закат был таким, будто кто-то разлил краску"
# That phrase is in generalPool and always appears early. Let's shuffle more and add variety
# Replace that specific phrase with more variants and ensure not always first

# Also increase recentParas check to avoid repetition more strictly? Keep as is.

# Fix isValidLearnedTemplate to allow more templates (currently filters 30..400, which is ok)

p.write_text(txt, encoding='utf-8')
print("story v23 fixed: bridge variety + learned 55% + version")
