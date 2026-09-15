import pathlib
p = pathlib.Path('/home/user/zi-writer/app/src/main/res/layout/activity_main.xml')
txt = p.read_text(encoding='utf-8')
txt = txt.replace('Интеллектуальный Писатель v8.0 ULTRA PRO', 'Интеллектуальный Писатель v16.0 LIVING EDITOR v2')
txt = txt.replace('440+ шаблонов • TF-IDF умный отбор • FB2/EPUB импорт • Иллюстрации к главам • Авто-обучение топ книг • Библиотека с поиском и избранным • Экспорт/импорт обучения',
                  'v16 LIVING: планета Тоби ≠ человек, Озин пошёл = человек • 440+ шаблонов • TF-IDF • FB2/EPUB • Иллюстрации • Редактор готового текста • OPDS Флибуста 213.5.65.159')
txt = txt.replace('ZI Writer v8.0 ULTRA PRO • 440+ шаблонов • TF-IDF • FB2/EPUB • Иллюстрации 10 типов • PDF/DOCX/EPUB • 7 обложек\\nZakharevich Igor +375293371412 • Постоянный ключ • Светлая тема • ONLINE',
                  'ZI Writer v16.0 LIVING EDITOR v2 • Живые vs Неживые • Редактор готового текста • 440+ шаблонов • TF-IDF • FB2/EPUB • Иллюстрации • PDF/DOCX/EPUB\\nZakharevich Igor +375293371412 • Постоянный ключ • OPDS 213.5.65.159 • ONLINE')
txt = txt.replace('✨ Развить сюжет (v8 ULTRA PRO • 440+ шаблонов • TF-IDF • Иллюстрации)',
                  '✨ Развить сюжет (v16 LIVING EDITOR v2 • Живые≠Неживые • Редактор • 440+ шаблонов • OPDS)')

old = '''                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp"
                        android:orientation="horizontal">

                        <com.google.android.material.button.MaterialButton
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

new = '''                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp"
                        android:orientation="horizontal">

                        <com.google.android.material.button.MaterialButton
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
                                android:text="🔍 Разделение живых/неживых (v16 LIVING)"
                                android:textColor="#5A5D60"
                                android:textSize="11sp"
                                android:textStyle="bold" />

                            <TextView
                                android:id="@+id/tvEntities"
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:layout_marginTop="6dp"
                                android:text="Герои и места появятся после генерации"
                                android:textColor="#1A1C1E"
                                android:textSize="11sp"
                                android:lineSpacingExtra="2dp" />
                        </LinearLayout>
                    </com.google.android.material.card.MaterialCardView>

                    <!-- TTS Speed -->'''

txt = txt.replace(old, new)
p.write_text(txt, encoding='utf-8')
print("layout v16 fixed")
