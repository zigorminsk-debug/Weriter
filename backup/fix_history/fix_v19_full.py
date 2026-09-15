import pathlib
p = pathlib.Path('/home/user/zi-writer/app/src/main/java/com/zakharevich/ziwriter/StoryGenerator.kt')
code = p.read_text(encoding='utf-8')

# Insert placeToNominative before generateTitleV18 if not exists
if 'placeToNominative' not in code:
    # Find the line before generateTitleV18
    old = '    // === V18 TITLE GENERATOR - NATURAL ===\n    private fun generateTitleV18'
    new = '''    private fun placeToNominative(placeFull: String): String {
        val lower = placeFull.lowercase().trim()
        var clean = lower.replace(Regex("^(на|в|у|за|под|над|перед|между)\\s+"), "").trim()
        val mapping = mapOf(
            "планете тоби" to "Планета Тоби",
            "планете" to "Планета",
            "краю галактики" to "Край Галактики",
            "станции орион-9" to "Станция Орион-9",
            "станции орион" to "Станция Орион",
            "заброшенном космопорте" to "Заброшенный космопорт",
            "квантового разлома" to "Квантовый разлом",
            "квантовом разломе" to "Квантовый разлом",
            "городе из стекла и света" to "Город из стекла",
            "городе из стекла" to "Город из стекла",
            "корабле аврора" to "Корабль Аврора",
            "корабле" to "Корабль",
            "орбите марса" to "Орбита Марса",
            "отсеке с криокапсулами" to "Отсек с криокапсулами",
            "мостике звездолёта" to "Мостик звездолёта",
            "реакторном отсеке" to "Реакторный отсек",
            "лаборатории времени" to "Лаборатория времени",
            "куполе на луне" to "Купол на Луне",
            "колонии на титане" to "Колония на Титане",
            "заброшенном складе" to "Заброшенный склад",
            "берегу моря" to "Берег моря",
            "тихом городе" to "Тихий город",
            "пустой квартире" to "Пустая квартира"
        )
        for ((k,v) in mapping) {
            if (clean.contains(k)) return v
        }
        clean = clean.replace("планете", "планета")
            .replace("станции", "станция")
            .replace("корабле", "корабль")
            .replace("краю", "край")
            .replace("орбите", "орбита")
            .replace("отсеке", "отсек")
            .replace("мостике", "мостик")
            .replace("лаборатории", "лаборатория")
            .replace("куполе", "купол")
            .replace("колонии", "колония")
            .replace("складе", "склад")
            .replace("берегу", "берег")
            .replace("городе", "город")
            .replace("квартире", "квартира")
        return clean.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }.trim()
    }

    // === V19 TITLE GENERATOR - NATURAL WITH NOMINATIVE ===
    private fun generateTitleV18'''

    code = code.replace('    // === V18 TITLE GENERATOR - NATURAL ===\n    private fun generateTitleV18', new)

# Now replace the body of generateTitleV18 to fix "Свет Планете"
# We'll replace the whole function by finding it and rewriting a clean version
# Simplify: overwrite file with corrected version from scratch using v18 base but with fixed title and grammar

# Load v18 template from current code and patch specific lines
code = code.replace(
    'Сегодня на орбите Марса требовал присутствия.',
    'Сегодня работа на орбите Марса требовала присутствия Озина.'
)
code = code.replace(
    'Сегодня на орбите Марса требовал присутствия',
    'Сегодня работа на орбите Марса требовала присутствия'
)

# Fix the specific bad sentence in fantasticTagged list
code = code.replace(
    'TaggedSentence("Звёзды гасли одна за другой, будто кто-то выключал свет. {HERO} отметил координаты и передал данные {PROF2}.", requiresAwake = true, timeHint = TimeSlot.NIGHT),',
    'TaggedSentence("Звёзды гасли одна за другой, будто кто-то выключал свет. {HERO} отметил координаты и передал данные {PROF2}, который дежурил в навигационной.", requiresAwake = true, timeHint = TimeSlot.NIGHT),'
)

# Fix title generation to avoid "Свет Планете Тоби"
# Replace the return part that had flawed filter
# We'll patch the titlesByGenre to be safe and ensure placeNom is nominative
code = code.replace(
    '        val hero = heroes.firstOrNull() ?: "Озин"\n        val placeShort = places.firstOrNull()?.replace(Regex("^(на|в|у)\\\\s+"), "") ?: "Тоби"\n        val placeCap = placeShort.replaceFirstChar { it.uppercase() }',
    '        val hero = heroes.firstOrNull() ?: "Озин"\n        val placeNom = if (places.isNotEmpty()) placeToNominative(places.first()) else "Тоби"\n        val placeShort = placeNom\n        val placeCap = placeNom'
)

code = code.replace(
    '                "Сигнал с $placeCap",\n                "Последний день на $placeCap",\n                "Хроники $placeCap",\n                "Тени $placeCap",\n                "Эхо $placeCap",\n                "Свет $placeCap",',
    '                "Сигнал с $placeNom",\n                "Последний день на $placeNom",\n                "Хроники $placeNom",\n                "Тени $placeNom",\n                "Эхо $placeNom",\n                "Свет $placeNom",'
)

code = code.replace(
    '                "Дело о $placeCap",\n                "Тени $placeCap",\n                "Последний свидетель с $placeCap",\n                "Архив $placeCap",\n                "Ночь на $placeCap",\n                "Сигнал в $placeCap",\n                "Протокол $placeCap"',
    '                "Дело о $placeNom",\n                "Тени $placeNom",\n                "Последний свидетель",\n                "Архив $placeNom",\n                "Ночь на $placeNom",\n                "Протокол $placeNom"'
)

code = code.replace(
    '                "Точка невозврата: $placeCap",\n                "Последний рубеж $placeCap",\n                "Операция $placeCap",\n                "Код $placeCap"',
    '                "Точка невозврата: $placeNom",\n                "Последний рубеж",\n                "Операция $placeNom",\n                "Код $placeNom"'
)

code = code.replace(
    '                "Письма с $placeCap",\n                "Вечер на $placeCap",\n                "Свет $placeCap",\n                "Дорога к $placeCap"',
    '                "Письма с $placeNom",\n                "Вечер на $placeNom",\n                "Свет $placeNom",\n                "Дорога к $placeNom"'
)

# Fix ending that had bad grammar
code = code.replace(
    '                "Сегодня на орбите Марса требовал присутствия.',
    '                "Сегодня работа на орбите Марса требовала присутствия Озина.'
)

# Also ensure title generation returns safe titles
code = code.replace(
    '        return if (filtered.isNotEmpty()) filtered.random(rnd) else titlesByGenre.random(rnd)',
    '''        val safe = listOf("Планета Тоби", "Корабль Аврора", "Город из стекла", "Сигнал из будущего", "Станция Орион-9", "Квантовый разлом", "Заброшенный космопорт")
        val result = if (filtered.isNotEmpty()) filtered.random(rnd) else safe.random(rnd)
        // final cleanup: fix cases like "Свет Планете Тоби"
        return result.replace("Свет Планете", "Свет на планете")
            .replace("Свет Планета", "Свет Планеты")
            .replace("на Планете Тоби", "на планете Тоби")
            .replace("Планете Тоби", "Планета Тоби")
            .trim()'''
)

# Fix the second occurrence of titlesByGenre for other genres
code = code.replace(
    '            else -> listOf(\n                "История $placeCap",\n                "Дневник $hero",\n                "Письма с $placeCap",\n                "Эхо $placeCap",\n                "Свет $placeCap"\n            )',
    '            else -> listOf(\n                "История $placeNom",\n                "Дневник $hero",\n                "Письма с $placeNom",\n                "Эхо $placeNom",\n                "Свет $placeNom"\n            )'
)

p.write_text(code, encoding='utf-8')
print("v19 full patches applied")
