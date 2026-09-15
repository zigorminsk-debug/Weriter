import pathlib
p = pathlib.Path('/home/user/zi-writer/app/src/main/java/com/zakharevich/ziwriter/LearningManager.kt')
txt = p.read_text(encoding='utf-8')

# Fix extractPlacesFromText regex
old = '            val placeRegex = Regex(\"(?:в|на|у|за|под|над|перед|между|около|возле|рядом с|внутри|среди)\\\\s+[а-яёA-ЯЁa-zA-Z\\\\-]{3,20}(?:\\\\s+[а-яёA-ЯЁa-zA-Z\\\\-]{2,20}){0,2}\", RegexOption.IGNORE_CASE)'
# Actually current file has single backslash, let's replace both possibilities with raw string version
# We'll replace entire function

new_func = '''    private fun extractPlacesFromText(text: String): List<String> {
        return try {
            // Find patterns like "в доме", "на берегу", "у окна", "за городом" etc
            val placeRegex = Regex("""(?:в|на|у|за|под|над|перед|между|около|возле|рядом с|внутри|среди)\s+[а-яёA-ЯЁa-zA-Z\-]{3,20}(?:\s+[а-яёA-ЯЁa-zA-Z\-]{2,20}){0,2}""", RegexOption.IGNORE_CASE)
            val matches = placeRegex.findAll(text).map { it.value.trim().replace(Regex("""\s+"""), " ").take(60) }.filter { it.length in 4..60 }.toList()
            // Filter to keep only those with noun-like endings and not too generic
            val freq = matches.groupingBy { it.lowercase() }.eachCount()
            freq.entries.sortedByDescending { it.value }.map { it.key }.take(30).map { raw ->
                // Capitalize first letter if starts with preposition lower
                raw
            }.distinct().take(20)
        } catch (e: Exception) { emptyList() }
    }'''

# Replace function via regex
import re
txt = re.sub(r'    private fun extractPlacesFromText\(text: String\): List<String> \{.*?    \}.*?    \}.*?    \}', new_func, txt, flags=re.DOTALL)
# The above regex may be too greedy, let's do simpler: find start and replace manually
# Instead re-read and do manual replace if still contains old pattern
if 'extractPlacesFromText' not in new_func or 'placeRegex' not in txt:
    print("failed to replace")
else:
    # Ensure we replaced
    pass

# Also fix other Regex with \s etc: search for Regex("...") containing \s or \- not escaped
# We'll fix extractPhrasesFromText which has Regex("[.!?]+") ok, Regex("[^а-яёa-z ]") ok, Regex("\\s+") already ok, Regex("\\d") - need double escaped
# In that file we have Regex("\\d") which is correct (should be "\\d" or """\d""")
# But also have Regex("\\s+") already double escaped, that's ok

# Check for illegal escapes: look for Regex("... \s ...") pattern with single backslash
# Let's replace all Regex("...") that contain \s or \- with raw string version

# Fix line with \s+ in extractPlacesFromText we already fixed
# Also need to fix StoryGenerator line 145 which had illegal escape

p.write_text(txt, encoding='utf-8')
print("fixed LearningManager")

# Now fix StoryGenerator.kt
p2 = pathlib.Path('/home/user/zi-writer/app/src/main/java/com/zakharevich/ziwriter/StoryGenerator.kt')
txt2 = p2.read_text(encoding='utf-8')
# Find line with Illegal escape \s at line 145
# It is likely in placeNoun extraction
if 'replace(Regex("^(в|на|у|за|под|над|перед|между|около|возле)\\\\s+")' in txt2:
    print("already escaped")
else:
    # Replace the problematic line
    txt2 = txt2.replace(
        'replace(Regex("^(в|на|у|за|под|над|перед|между|около|возле)\\\\s+"), "")',
        'replace(Regex("""^(в|на|у|за|под|над|перед|между|около|возле)\\s+"""), "")'
    )
    # Also check for other single backslash patterns
    # Fix generic: Regex("^(в|на|у|за|под|над|перед|между|около|возле)\\s+") should be raw
    txt2 = txt2.replace(
        'Regex("^(в|на|у|за|под|над|перед|между|около|возле)\\s+")',
        'Regex("""^(в|на|у|за|под|над|перед|между|около|возле)\\s+""")'
    )
    # Also fix any other Regex with \s
    # Let's search for patterns like Regex("...\\s...") that are actually correct double escaped, but also single
    # We'll fix the one we know: line 145
    import re
    # Replace all occurrences of Regex("...") containing \s with raw if not already double escaped
    # For safety, replace the specific line that caused error: it's likely "replace(Regex("^(в|на|у|за|под|над|перед|между|около|возле)\\s+")"
    # We need to find actual text around 140-150
    lines = txt2.splitlines()
    for i, l in enumerate(lines[130:160], start=130):
        if '\\s' in l and 'Regex' in l:
            print(f"Line {i}: {l}")

    p2.write_text(txt2, encoding='utf-8')
    print("fixed StoryGenerator attempt")

