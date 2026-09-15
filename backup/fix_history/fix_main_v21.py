import pathlib, re
p = pathlib.Path('/home/user/zi-writer/app/src/main/java/com/zakharevich/ziwriter/MainActivity.kt')
txt = p.read_text(encoding='utf-8')

# Update chip text
txt = txt.replace('OPDS 213.5.65.159', 'OPDS flibusta.net')
txt = txt.replace('213.5.65.159/opds', 'flibusta.net/opds')
txt = txt.replace('213.5.65.159', 'flibusta.net')

# Update statistics dialog v13 -> v21
txt = txt.replace('Статистика v13 OPDS', 'Статистика v21 OPDS flibusta.net')
txt = txt.replace('Статистика v20', 'Статистика v21')
txt = txt.replace('Статистика v13', 'Статистика v21')

# Fix message that shows sources
txt = txt.replace('🔥 213.5.65.159/opds - Флибуста', '🔥 flibusta.net/opds - Флибуста (работает в РБ)')
txt = txt.replace('🔥 flibusta.net/opds - Флибуста', '🔥 flibusta.net/opds - Флибуста (работает в РБ)')
# Ensure message contains flibusta.net
if 'flibusta.net/opds - Флибуста (работает в РБ)' not in txt:
    txt = txt.replace('Источники:', 'Источники:\n• 🔥 flibusta.net/opds - Флибуста (работает в РБ)')

# Update titles
txt = txt.replace('OPDS 213.5.65.159', 'OPDS flibusta.net')
txt = txt.replace('Найдено ${allItems.size} книг (OPDS 213.5.65.159)', 'Найдено ${allItems.size} книг (flibusta.net)')
txt = txt.replace('Найдено ${allItems.size} книг (OPDS flibusta.net)', 'Найдено ${allItems.size} книг (flibusta.net)')

# Update auto-learn messages
txt = txt.replace('Авто-обучение Флибуста OPDS', 'Авто-обучение Флибуста flibusta.net')
txt = txt.replace('Изучить 3 популярных книг с Флибусты OPDS 213.5.65.159', 'Изучить 3 популярных книг с Флибусты flibusta.net? Работает в РБ без VPN.')
txt = txt.replace('Изучить 3 популярных книг с Флибусты flibusta.net', 'Изучить 3 популярных книг с Флибусты flibusta.net? Работает в РБ без VPN.')
txt = txt.replace('Изучено $count книг с Флибусты 213.5.65.159', 'Изучено $count книг с flibusta.net')
txt = txt.replace('Изучено $count книг с flibusta.net', 'Изучено $count книг с flibusta.net')

# Update snackbar
txt = txt.replace('Книги не найдены. Проверьте VPN для Флибусты 213.5.65.159', 'Книги не найдены. Попробуйте другое слово. Флибуста flibusta.net работает в РБ.')
txt = txt.replace('Книги не найдены. Проверьте VPN для Флибусты flibusta.net', 'Книги не найдены. Попробуйте другое слово. Флибуста flibusta.net работает в РБ.')

# Ensure dialog for 0 books fallback mentions flibusta.net
# The previous fix_main_v19 had VPN message, update it to say flibusta.net should work
txt = txt.replace('Флибуста требует VPN', 'Флибуста flibusta.net обычно работает в РБ без VPN')
txt = txt.replace('Флибуста flibusta.net обычно работает в РБ без VPN в BY', 'Флибуста flibusta.net обычно работает в РБ без VPN')

p.write_text(txt, encoding='utf-8')
print("main v21 fixed")
