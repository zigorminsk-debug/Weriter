import pathlib
p = pathlib.Path('/home/user/zi-writer/app/src/main/java/com/zakharevich/ziwriter/GrammarCorrector.kt')
code = r'''
package com.zakharevich.ziwriter

object GrammarCorrector {
    private val replacements = listOf(
        Regex("От\\s+куда", RegexOption.IGNORE_CASE) to "Откуда",
        Regex("от\\s+куда") to "откуда",
        Regex("За\\s+чем", RegexOption.IGNORE_CASE) to "Зачем",
        Regex("за\\s+чем") to "зачем",
        Regex("Не\\s+где", RegexOption.IGNORE_CASE) to "Нигде",
        Regex("не\\s+где") to "нигде",
        Regex("Ни\\s+кто", RegexOption.IGNORE_CASE) to "Никто",
        Regex("ни\\s+кто") to "никто",
        Regex("Ни\\s+чего", RegexOption.IGNORE_CASE) to "Ничего",
        Regex("ни\\s+чего") to "ничего",
        Regex("Не\\s+чего", RegexOption.IGNORE_CASE) to "Нечего",
        Regex("не\\s+чего") to "нечего",
        Regex("В\\s+перед", RegexOption.IGNORE_CASE) to "Вперед",
        Regex("в\\s+перед") to "вперед",
        Regex("По\\s+чему", RegexOption.IGNORE_CASE) to "Почему",
        Regex("по\\s+чему") to "почему",
        Regex("По\\s+тому\\s+что", RegexOption.IGNORE_CASE) to "Потому что",
        Regex("В\\s+туже", RegexOption.IGNORE_CASE) to "В ту же",
        Regex("в\\s+туже") to "в ту же",
        Regex("С\\s+подвигло", RegexOption.IGNORE_CASE) to "Сподвигло",
        Regex("с\\s+подвигло") to "сподвигло",
        Regex("Из\\s+нутри", RegexOption.IGNORE_CASE) to "Изнутри",
        Regex("из\\s+нутри") to "изнутри",
        Regex("Не\\s+истовый", RegexOption.IGNORE_CASE) to "Неистовый",
        Regex("не\\s+истовый") to "неистовый",
        Regex("Не\\s+имоверно", RegexOption.IGNORE_CASE) to "Неимоверно",
        Regex("не\\s+имоверно") to "неимоверно",
        Regex("Миразданья", RegexOption.IGNORE_CASE) to "Мироздания",
        Regex("миразданья") to "мироздания",
        Regex("Мирозданья") to "Мироздания",
        Regex("Принцы\\s+морали", RegexOption.IGNORE_CASE) to "Принципы морали",
        Regex("принцы\\s+морали") to "принципы морали",
        Regex("Что\\s+бы", RegexOption.IGNORE_CASE) to "Чтобы",
        Regex("что\\s+бы") to "чтобы",
        Regex("многлюдной") to "многолюдной",
        Regex("Многлюдной") to "Многолюдной",
        // v16 additions
        Regex("в\\s+берегу", RegexOption.IGNORE_CASE) to "на берегу",
        Regex("В\\s+берегу") to "На берегу",
        Regex("в\\s+складе", RegexOption.IGNORE_CASE) to "на складе",
        Regex("В\\s+складе") to "На складе",
        Regex("на\\s+складе\\s+моря", RegexOption.IGNORE_CASE) to "на берегу моря",
        // v17 additions for common typos from example
        Regex("пошол", RegexOption.IGNORE_CASE) to "пошёл",
        Regex("Пошол") to "Пошёл",
        Regex("карабль", RegexOption.IGNORE_CASE) to "корабль",
        Regex("Карабль") to "Корабль",
        Regex("карабля", RegexOption.IGNORE_CASE) to "корабля",
        Regex("карабле", RegexOption.IGNORE_CASE) to "корабле",
        Regex("корабль\\s+Аврора\\s+нашел", RegexOption.IGNORE_CASE) to "корабль Аврора нашёл",
        Regex("нашел", RegexOption.IGNORE_CASE) to "нашёл",
        Regex("пришел", RegexOption.IGNORE_CASE) to "пришёл",
        Regex("ушел", RegexOption.IGNORE_CASE) to "ушёл",
        Regex("прошел", RegexOption.IGNORE_CASE) to "прошёл",
        Regex("в\\s+заброшенном\\s+космопорте", RegexOption.IGNORE_CASE) to "в заброшенном космопорте",
        Regex("37\\s+часовой", RegexOption.IGNORE_CASE) to "37-часовой",
        Regex("очердной", RegexOption.IGNORE_CASE) to "очередной",
        Regex("Очердной") to "Очередной"
    )

    fun correct(text: String): String {
        var result = text
        for ((regex, replacement) in replacements) {
            result = regex.replace(result, replacement)
        }
        result = result.replace(Regex("\\s+,"), ",")
        result = result.replace(Regex("\\s+\\."), ".")
        result = result.replace(Regex("\\s+!"), "!")
        result = result.replace(Regex("\\s+\\?"), "?")
        result = result.replace(Regex("\\s+:"), ":")
        result = result.replace(Regex("\\s+;"), ";")
        result = result.replace(Regex(",([^\\s])"), ", $1")
        result = result.replace(Regex("\\.([^\\s\\d])"), ". $1")
        result = result.replace(Regex(" {2,}"), " ")
        result = result.replace(Regex("\\n{3,}"), "\n\n")
        result = result.replace(Regex(" \\n"), "\n")
        result = capitalizeSentences(result)
        return result.trim()
    }

    private fun capitalizeSentences(text: String): String {
        var result = text
        result = Regex("([.!?]\\s+)([а-яё])").replace(result) {
            it.groupValues[1] + it.groupValues[2].uppercase()
        }
        if (result.isNotEmpty()) {
            result = result.replaceFirstChar { it.uppercase() }
        }
        result = Regex("(\\n\\s*)([а-яё])").replace(result) {
            it.groupValues[1] + it.groupValues[2].uppercase()
        }
        return result
    }

    fun getCorrectionsList(original: String, corrected: String): List<String> {
        val list = mutableListOf<String>()
        if (original != corrected) {
            for ((regex, replacement) in replacements) {
                if (regex.containsMatchIn(original)) {
                    val before = regex.findAll(original).map { it.value }.toList()
                    if (before.isNotEmpty()) {
                        list.add("${before.first()} → $replacement")
                    }
                }
            }
        }
        return list.distinct().take(10)
    }
}
'''
p.write_text(code, encoding='utf-8')
print("grammar v17 written")
