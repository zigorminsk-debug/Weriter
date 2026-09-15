import pathlib, re
p = pathlib.Path('/home/user/zi-writer/app/src/main/res/layout/activity_main.xml')
txt = p.read_text(encoding='utf-8')

# Clean technical texts
replacements = {
    "🌐 Интернет и самообучение ULTRA": "🌐 Интернет и обучение",
    "TF-IDF умный отбор • Gutendex 70k+ • Open Library 40M+ • Авто-обучение топ популярных • FB2/EPUB импорт • Экспорт/импорт обучения": "Поиск книг • Обучение на ваших книгах • Импорт FB2/EPUB",
    "✨ Исправить грамматику (30+ правил)": "✨ Исправлять грамматику",
    "🚫 Защита от повторов v8 ULTRA (440+ шаблонов)": "🚫 Защита от повторов",
    "🎨 Иллюстрации к главам (10 типов)": "🎨 Иллюстрации к главам",
    "🧠 TF-IDF релевантность (35% шанс)": "🧠 Использовать изученные книги",
    "🔍 Разделение живых/неживых (v17 LIVING)": "🔍 Персонажи и места",
    "🔍 Разделение живых/неживых (v18 TITLE)": "🔍 Персонажи и места",
    "🔍 Персонажи и места": "🔍 Персонажи и места",
}

for old, new in replacements.items():
    if old in txt:
        txt = txt.replace(old, new)
        print(f"replaced: {old[:30]}")

# Fix dark-on-dark: replace hardcoded dark text colors with theme attributes where appropriate
# etInput and etResult have #1A1C1E which is dark, should be ?attr/colorOnSurface for day/night adaptation
# We'll replace textColor #1A1C1E with ?attr/colorOnSurface in input fields
txt = txt.replace('android:textColor="#1A1C1E"', 'android:textColor="?attr/colorOnSurface"')
# For secondary text #5A5D60 -> ?attr/colorOnSurfaceVariant
txt = txt.replace('android:textColor="#5A5D60"', 'android:textColor="?attr/colorOnSurfaceVariant"')
# For hint #8A8D90 -> keep but ensure visible, use ?attr/colorOnSurfaceVariant with alpha
# Replace boxBackgroundColor #F2F0F7 with ?attr/colorSurfaceContainer
txt = txt.replace('app:boxBackgroundColor="#F2F0F7"', 'app:boxBackgroundColor="?attr/colorSurfaceContainer"')
txt = txt.replace('android:background="#F2F0F7"', 'android:background="?attr/colorSurfaceContainer"')
# Card backgrounds #FFFBFE -> ?attr/colorSurface
txt = txt.replace('app:cardBackgroundColor="#FFFBFE"', 'app:cardBackgroundColor="?attr/colorSurface"')
# #F3EDF7 -> ?attr/colorSurfaceContainer
txt = txt.replace('app:cardBackgroundColor="#F3EDF7"', 'app:cardBackgroundColor="?attr/colorSurfaceContainer"')
# Fix header gradient text colors remain white (okay)
# Ensure input counter background is theme aware
txt = txt.replace('android:background="#F2F0F7"', 'android:background="?attr/colorSurfaceContainer"')

# Clean remaining version strings in footer and header
txt = re.sub(r'ZI Writer v[0-9.]+ [A-Z ]+ • [^\n]*?ONLINE', 'ZI Writer • Zakharevich Igor +375293371412', txt)
txt = re.sub(r'ZI Writer v[0-9.]+ [^\n]*?Zakharevich', 'ZI Writer • Zakharevich Igor +375293371412', txt)

# Ensure generate button is clean
txt = re.sub(r'android:text="✨ Развить сюжет[^"]*"', 'android:text="✨ Развить сюжет"', txt)

# Ensure footer is clean
txt = re.sub(r'android:text="ZI Writer[^"]*?Постоянный ключ[^"]*"', 'android:text="ZI Writer • Zakharevich Igor +375293371412 • Постоянный ключ"', txt)

# Fix chip text that shows technical
txt = txt.replace('android:text="ONLINE"', 'android:text="ONLINE"') # keep

# Ensure etResult and etInput use theme colors for hint as well
txt = txt.replace('android:textColorHint="#8A8D90"', 'android:textColorHint="?attr/colorOnSurfaceVariant"')

p.write_text(txt, encoding='utf-8')
print("layout v20 cleaned and themed")
