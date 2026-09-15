package com.zakharevich.ziwriter

object GrammarCorrector {
    private val replacements = listOf(
        Regex("От\\s+куда") to "Откуда",
        Regex("от\\s+куда") to "откуда",
        Regex("За\\s+чем") to "Зачем",
        Regex("за\\s+чем") to "зачем",
        // "Не где" — это "Негде" (место), а не "Нигде" (отрицание): раньше правило меняло смысл.
        Regex("Не\\s+где") to "Негде",
        Regex("не\\s+где") to "негде",
        Regex("Ни\\s+кто") to "Никто",
        Regex("ни\\s+кто") to "никто",
        Regex("Ни\\s+чего") to "Ничего",
        Regex("ни\\s+чего") to "ничего",
        Regex("Не\\s+чего") to "Нечего",
        Regex("не\\s+чего") to "нечего",
        Regex("В\\s+перед") to "Вперед",
        Regex("в\\s+перед") to "вперед",
        Regex("По\\s+чему") to "Почему",
        Regex("по\\s+чему") to "почему",
        Regex("По\\s+тому\\s+что", RegexOption.IGNORE_CASE) to "Потому что",
        Regex("В\\s+туже") to "В ту же",
        Regex("в\\s+туже") to "в ту же",
        Regex("С\\s+подвигло") to "Сподвигло",
        Regex("с\\s+подвигло") to "сподвигло",
        Regex("Из\\s+нутри") to "Изнутри",
        Regex("из\\s+нутри") to "изнутри",
        Regex("Не\\s+истовый") to "Неистовый",
        Regex("не\\s+истовый") to "неистовый",
        Regex("Не\\s+имоверно") to "Неимоверно",
        Regex("не\\s+имоверно") to "неимоверно",
        Regex("Не\\s+иставым") to "Неистовым",
        Regex("не\\s+иставым") to "неистовым",
        Regex("Миразданья") to "Мироздания",
        Regex("миразданья") to "мироздания",
        Regex("Мирозданья") to "Мироздания",
        Regex("Принцы\\s+морали") to "Принципы морали",
        Regex("принцы\\s+морали") to "принципы морали",
        Regex("В\\s+чье\\s+руки") to "В чьи руки",
        Regex("в\\s+чье\\s+руки") to "в чьи руки",
        Regex("Потупивши") to "Потупив",
        Regex("потупивши") to "потупив",
        // Правило "что бы" -> "чтобы" отключено: без разбора предложения оно портит
        // корректные конструкции вида "что бы ни случилось", "что бы мне сделать?".
        // Regex("Что\\s+бы") to "Чтобы",
        // Regex("что\\s+бы") to "чтобы",
        Regex("многлюдной") to "многолюдной",
        Regex("Многлюдной") to "Многолюдной",
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
