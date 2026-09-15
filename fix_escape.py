import pathlib

# Fix LearningManager.kt
p = pathlib.Path('/home/user/zi-writer/app/src/main/java/com/zakharevich/ziwriter/LearningManager.kt')
txt = p.read_text(encoding='utf-8')

# Replace the bad line - we will replace any occurrence of Regex("(?:в|на|...
# Use a marker: replace the whole line containing placeRegex

lines = txt.splitlines()
new_lines = []
for line in lines:
    if 'placeRegex' in line and 'Regex(' in line:
        # replace with correct raw string
        new_lines.append('            val placeRegex = Regex("""(?:в|на|у|за|под|над|перед|между|около|возле|рядом с|внутри|среди)\\s+[а-яёA-ЯЁa-zA-Z\\-]{3,20}(?:\\s+[а-яёA-ЯЁa-zA-Z\\-]{2,20}){0,2}""", RegexOption.IGNORE_CASE)')
    else:
        new_lines.append(line)

txt = "\n".join(new_lines)
p.write_text(txt, encoding='utf-8')
print("LearningManager fixed")

# Fix StoryGenerator.kt
p2 = pathlib.Path('/home/user/zi-writer/app/src/main/java/com/zakharevich/ziwriter/StoryGenerator.kt')
txt2 = p2.read_text(encoding='utf-8')
# Fix the line with placeNoun extraction
# Look for pattern: replace(Regex("^(в|на|у|за|под|над|перед|между|около|возле)\s+")...
# We need to replace with raw string

# There are two occurrences: one with double escaped \\s+ and one with single \s+
# We'll replace both with raw version

# First, fix double escaped version if present
txt2 = txt2.replace(
    'replace(Regex("^(в|на|у|за|под|над|перед|между|около|возле)\\\\s+"), "")',
    'replace(Regex("""^(в|на|у|за|под|над|перед|между|около|возле)\\s+"""), "")'
)
# Fix single escaped version
# Need to handle that file may contain single backslash which Python string reading will interpret?
# We'll search for the literal text with single backslash - in the file it's written as \s (single)
# In Python string, to match single backslash, we need to search for "\s+" but file contains backslash+s
# Let's try to replace any occurrence of Regex("^(в|на|у|за|под|над|перед|между|около|возле)\s+") pattern
# We can do a simple replace by reading raw and replacing \s with \\s inside raw string? Actually we want raw string.

# To be safe, replace any Regex("^(в|...)\s+") with raw
import re
# This regex matches Regex("^(в|на|у|за|под|над|перед|между|около|возле)\s+") with single backslash
# In file content, it appears as ...\s+ inside quotes
# We'll use a pattern to find it
pattern = r'Regex\("\^\(в\|на\|у\|за\|под\|над\|перед\|между\|около\|возле\)\\s\+"\)'
# But Python re will interpret \s as whitespace, so need to escape properly
# Instead, do simple string search for the bad sequence: the file contains literal backslash + s
# Let's just replace all occurrences of 'Regex("^(в|на|у|за|под|над|перед|между|около|возле)\s+")' where \s is single backslash
# We can search for the substring 'Regex("^(в|на|у|за|под|над|перед|между|около|возле)\\s+")' - but file has single, so we need to check bytes

# Let's directly write correct line: find line containing 'replace(Regex(' and '^(в|на'
lines2 = txt2.splitlines()
new_lines2 = []
for line in lines2:
    if '^(в|на|у|за|под|над|перед|между|около|возле)' in line and 'Regex(' in line:
        # Replace whole Regex(...) part with raw version
        # Keep surrounding code
        # Example: config.learnedPlaces.random().replace(Regex("^(в|на|у|за|под|над|перед|между|около|возле)\\s+"), "").trim()
        # We want to replace Regex("...") with Regex("""...""")
        # Use regex to replace
        new_line = re.sub(r'Regex\(".*?\^\(в\|на\|у\|за\|под\|над\|перед\|между\|около\|возле\)\\+s\+"\)', 'Regex("""^(в|на|у|за|под|над|перед|между|около|возле)\\s+""")', line)
        # If not replaced, try single backslash version
        if new_line == line:
            new_line = re.sub(r'Regex\("\^\(в\|на\|у\|за\|под\|над\|перед\|между\|около\|возле\)\s+"\)', 'Regex("""^(в|на|у|за|под|над|перед|между|около|возле)\\s+""")', line)
        # Fallback manual
        if 'Regex(' in new_line and '\\s+' in new_line and '"""' not in new_line:
            # Force replace
            new_line = line.split('Regex(')[0] + 'Regex("""^(в|на|у|за|под|над|перед|между|около|возле)\\s+""")' + line.split('"), ""')[1] if '"), ""' in line else '            config.learnedPlaces.random().replace(Regex("""^(в|на|у|за|под|над|перед|между|около|возле)\\s+"""), "").trim().take(30).ifBlank { randomPlaceNoun(config.genre) }'
        new_lines2.append(new_line)
    else:
        new_lines2.append(line)

txt2 = "\n".join(new_lines2)
p2.write_text(txt2, encoding='utf-8')
print("StoryGenerator fixed")
