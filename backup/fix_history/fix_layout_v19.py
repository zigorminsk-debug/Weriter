import pathlib, re
p = pathlib.Path('/home/user/zi-writer/app/src/main/res/layout/activity_main.xml')
txt = p.read_text(encoding='utf-8')

# Replace the old technical description TextView with clean description
# Find the TextView that has 440+ шаблонов
old_desc = '                <TextView\n                    android:layout_width="match_parent"\n                    android:layout_height="wrap_content"\n                    android:layout_marginTop="14dp"\n                    android:alpha="0.95"\n                    android:text="440+ шаблонов • TF-IDF умный отбор • FB2/EPUB импорт • Иллюстрации к главам • Авто-обучение топ книг • Библиотека с поиском и избранным • Экспорт/импорт обучения"\n                    android:textColor="#E8E0FF"\n                    android:textSize="12sp"\n                    android:lineSpacingExtra="2dp" />'

new_desc = '''                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="10dp"
                    android:alpha="0.9"
                    android:text="Создавайте книги из отрывков • Умный сюжет • Библиотека"
                    android:textColor="#E8E0FF"
                    android:textSize="13sp"
                    android:lineSpacingExtra="2dp" />'''

if old_desc in txt:
    txt = txt.replace(old_desc, new_desc)
    print("replaced old 440+ description")
else:
    # try to find any TextView with v16 LIVING or similar
    import re
    pattern = re.compile(r'<TextView[^>]*android:text="v16 LIVING[^"]*"[^>]*/?>.*?</TextView>|<TextView[^>]*android:text="v16 LIVING[^"]*"[^>]*?/>', re.DOTALL)
    # more generic: replace any TextView containing "LIVING:" 
    txt = re.sub(r'<TextView[^>]*android:text="[^"]*LIVING:[^"]*"[^>]*?/>', new_desc, txt)
    txt = re.sub(r'<TextView[^>]*android:text="[^"]*440\+[^"]*"[^>]*?/>', new_desc, txt)
    # Also remove the specific header subtitle that had ULTRA PRO
    print("used regex replace")

# Also fix the subtitle under ZI Writer that says v8.0 ULTRA PRO
txt = txt.replace('android:text="Интеллектуальный Писатель v8.0 ULTRA PRO"', 'android:text="Интеллектуальный писатель"')
txt = txt.replace('Интеллектуальный Писатель v16.0 LIVING EDITOR v2', 'Интеллектуальный писатель')
txt = txt.replace('Интеллектуальный Писатель v16.0 LIVING NO-PRONOUN v17', 'Интеллектуальный писатель')
txt = txt.replace('Интеллектуальный Писатель v18.0 TITLE TIME RANDOM', 'Интеллектуальный писатель')

# Fix generate button text
txt = txt.replace('android:text="✨ Развить сюжет (v8 ULTRA PRO • 440+ шаблонов • TF-IDF • Иллюстрации)"', 'android:text="✨ Развить сюжет"')
txt = re.sub(r'android:text="✨ Развить сюжет \([^"]+\)"', 'android:text="✨ Развить сюжет"', txt)

# Fix footer text
footer_old = 'android:text="ZI Writer v8.0 ULTRA PRO • 440+ шаблонов • TF-IDF • FB2/EPUB • Иллюстрации 10 типов • PDF/DOCX/EPUB • 7 обложек\\nZakharevich Igor +375293371412 • Постоянный ключ • Светлая тема • ONLINE"'
footer_new = 'android:text="ZI Writer • Zakharevich Igor +375293371412 • Постоянный ключ"'
txt = txt.replace(footer_old, footer_new)
txt = re.sub(r'android:text="ZI Writer v[^"]*?Zakharevich Igor[^"]*"', footer_new, txt)

# Ensure btnEditFull and cardEntities exist - if not, add them after ExportAll layout
if 'btnEditFull' not in txt:
    old_block = '''                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/btnExportAll"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_marginEnd="4dp"
                            android:layout_weight="1"
                            android:text="📦 Экспорт ВСЁ"
                            android:textSize="11sp"
                            app:cornerRadius="12dp" />

                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/btnContinue"
                            style="@style/Widget.Material3.Button.TonalButton"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:text="➕ Продолжить"
                            android:textSize="11sp" />
                    </LinearLayout>

                    <!-- TTS Speed -->'''

    new_block = '''                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/btnExportAll"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_marginEnd="4dp"
                            android:layout_weight="1"
                            android:text="📦 Экспорт ВСЁ"
                            android:textSize="11sp"
                            app:cornerRadius="12dp" />

                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/btnContinue"
                            style="@style/Widget.Material3.Button.TonalButton"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:text="➕ Продолжить"
                            android:textSize="11sp" />
                    </LinearLayout>

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp"
                        android:orientation="horizontal">

                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/btnEditFull"
                            style="@style/Widget.Material3.Button.TonalButton"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_marginEnd="4dp"
                            android:layout_weight="1"
                            android:text="✏️ Редактор (правка)"
                            android:textSize="11sp"
                            app:cornerRadius="12dp" />

                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/btnToInput"
                            style="@style/Widget.Material3.Button.OutlinedButton"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:text="⬆️ В исходник"
                            android:textSize="11sp" />
                    </LinearLayout>

                    <com.google.android.material.card.MaterialCardView
                        android:id="@+id/cardEntities"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="10dp"
                        app:cardBackgroundColor="#FFF8E1"
                        app:cardCornerRadius="12dp"
                        app:cardElevation="0dp"
                        app:strokeColor="#FFCC02"
                        app:strokeWidth="1dp">

                        <LinearLayout
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:orientation="vertical"
                            android:padding="12dp">

                            <TextView
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:text="🔍 Персонажи и места"
                                android:textColor="#5A5D60"
                                android:textSize="11sp"
                                android:textStyle="bold" />

                            <TextView
                                android:id="@+id/tvEntities"
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:layout_marginTop="6dp"
                                android:text="Появятся после генерации"
                                android:textColor="#1A1C1E"
                                android:textSize="11sp"
                                android:lineSpacingExtra="2dp" />
                        </LinearLayout>
                    </com.google.android.material.card.MaterialCardView>

                    <!-- TTS Speed -->'''
    txt = txt.replace(old_block, new_block)
    print("added editor block")

p.write_text(txt, encoding='utf-8')
print("layout v19 cleaned")
