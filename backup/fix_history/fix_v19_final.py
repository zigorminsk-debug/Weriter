import pathlib
path = pathlib.Path('/home/user/zi-writer/app/src/main/java/com/zakharevich/ziwriter/StoryGenerator.kt')

# We will take the current v18 code and patch it to v19 with proper title
txt = path.read_text(encoding='utf-8')

# Add placeToNominative if not present
if 'fun placeToNominative' not in txt:
    insert_after = '    private fun randomPlaceFull(genre: String, knownPlaces: List<String>, rnd: Random): String {'
    replacement = '''    private fun placeToNominative(placeFull: String): String {
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

    private fun randomPlaceFull(genre: String, knownPlaces: List<String>, rnd: Random): String {'''
    txt = txt.replace(insert_after, replacement)
    print("added placeToNominative")

# Now completely replace generateTitleV18 function with a safe version
# Find the function
import re
pattern = re.compile(r'    // === V18 TITLE GENERATOR - NATURAL ===\n    private fun generateTitleV18\(original: String, keywords: List<String>, genre: String, places: List<String>, heroes: List<String>, rnd: Random\): String \{.*?return if \(filtered\.isNotEmpty\(\)\) filtered\.random\(rnd\) else titlesByGenre\.random\(rnd\)\n    \}', re.DOTALL)

safe_title_func = '''    // === V19 TITLE GENERATOR - SAFE GRAMMAR ===
    private fun generateTitleV18(original: String, keywords: List<String>, genre: String, places: List<String>, heroes: List<String>, rnd: Random): String {
        val lowerOrig = original.lowercase()

        // Прямые красивые фразы из оригинала - приоритет, только именительный падеж
        val directCandidates = mutableListOf<String>()
        if (lowerOrig.contains("город из стекла")) directCandidates.add("Город из стекла")
        if (lowerOrig.contains("планете тоби") || lowerOrig.contains("планета тоби")) directCandidates.add("Планета Тоби")
        if (lowerOrig.contains("корабль аврора") || lowerOrig.contains("корабле аврора")) directCandidates.add("Корабль Аврора")
        if (lowerOrig.contains("станция орион")) directCandidates.add("Станция Орион-9")
        if (lowerOrig.contains("квантовый разлом")) directCandidates.add("Квантовый разлом")
        if (lowerOrig.contains("заброшенном космопорте")) directCandidates.add("Заброшенный космопорт")
        if (lowerOrig.contains("сигнал из будущего") || lowerOrig.contains("сигнал — не извне") || lowerOrig.contains("сигнал - не извне")) directCandidates.add("Сигнал из будущего")
        if (lowerOrig.contains("новая галактика")) directCandidates.add("Новая галактика")

        // Места в именительном
        for (pl in places) {
            val nom = placeToNominative(pl)
            if (nom.length in 4..35) directCandidates.add(nom)
        }

        // Убираем дубли и кривые
        val distinctDirect = directCandidates.distinct().filter { it.length in 4..35 && !it.lowercase().contains("планете тоби") && !it.contains("Планете") }

        if (distinctDirect.isNotEmpty()) {
            return distinctDirect.random(rnd)
        }

        val hero = heroes.firstOrNull() ?: "Озин"
        val placeNom = if (places.isNotEmpty()) placeToNominative(places.first()) else "Тоби"

        // Только безопасные, грамматически верные заголовки
        val safeTitles = when (genre) {
            "Фантастика" -> listOf(
                "Планета Тоби",
                "Корабль Аврора",
                "Город из стекла",
                "Сигнал из будущего",
                "Станция Орион-9",
                "Квантовый разлом",
                "Заброшенный космопорт",
                "Новая галактика",
                "Последний сигнал",
                "Дневник $hero",
                "Вахта $hero",
                "Орбита $placeNom",
                "Хроники $placeNom",
                "Эхо $placeNom"
            )
            "Детектив" -> listOf(
                "Дело о $placeNom",
                "Тени $placeNom",
                "Последний свидетель",
                "Архив $placeNom",
                "Протокол $placeNom"
            )
            "Боевик" -> listOf(
                "Точка невозврата",
                "Последний рубеж",
                "Операция $placeNom",
                "Код $placeNom"
            )
            "Роман" -> listOf(
                "Письма с $placeNom",
                "Вечер на $placeNom",
                "Дорога к звёздам"
            )
            else -> listOf(
                "Планета Тоби",
                "Корабль Аврора",
                "Город из стекла",
                "История $placeNom",
                "Дневник $hero"
            )
        }

        // Фильтр от кривых типа "Свет Планете Тоби"
        val filtered = safeTitles.filter { t ->
            !t.contains("Планете") && !t.contains("Свет Планете") && t.length in 4..40
        }

        return filtered.random(rnd)
    }'''

if pattern.search(txt):
    txt = pattern.sub(safe_title_func, txt)
    print("replaced title generator with safe v19")
else:
    print("pattern not found for title generator, trying alternative")
    # Try to find and replace manually by searching start and end
    start = txt.find('    // === V18 TITLE GENERATOR')
    if start != -1:
        # find next function after it (TIME AND STATE)
        end = txt.find('    // === TIME AND STATE', start)
        if end != -1:
            txt = txt[:start] + safe_title_func + "\n\n    " + txt[end:]
            print("manual replace done")

# Fix grammar templates
txt = txt.replace('Сегодня на орбите Марса требовал присутствия.', 'Сегодня работа на орбите Марса требовала присутствия Озина.')
txt = txt.replace('Сегодня на орбите Марса требовал присутствия', 'Сегодня работа на орбите Марса требовала присутствия')
txt = txt.replace('Сегодня на орбите Марса требовала присутствия Озина.', 'Сегодня присутствие Озина требовалось на орбите Марса.')
txt = txt.replace('на орбите Марса требовал присутствия', 'на орбите Марса требовалось присутствие')

# Fix ending sentence that was bad
txt = txt.replace(
    'Корабль Аврора висел на орбите, как напоминание о том, что путь домой всегда есть, даже если дома уже нет. Озин знал, что вернётся, но не сегодня. Сегодня $placeFull требовал присутствия.',
    'Корабль Аврора висел на орбите, как напоминание о том, что путь домой всегда есть, даже если дома уже нет. Озин знал, что вернётся, но не сегодня — сегодня его присутствие требовалось на $placeFull.'
)

# Also fix the other ending that had $placeFull требовал
txt = txt.replace(
    'Сегодня $placeFull требовал присутствия.',
    'Сегодня присутствие Озина требовалось на $placeFull.'
)

path.write_text(txt, encoding='utf-8')
print("v19 final patches applied")
