<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-kotlin-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>Плагин компиляции и запуска однофайлового исходного кода Kotlin 2.3.21 для AutoJs6</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/commit/17f42fa7aa2a2046c74e558f313b7510d155f365"><img alt="Created" src="https://img.shields.io/date/1787396606?color=2e7d32&label=Created"/></a>
    <br>
    <a href="https://developer.android.com/studio/archive"><img alt="Android Studio" src="https://img.shields.io/badge/Android%20Studio-2023.3+-B64FC8"/></a>
    <a href="https://www.jetbrains.com/idea/download/other.html"><img alt="IntelliJ IDEA" src="https://img.shields.io/badge/IntelliJ%20IDEA-2023.3+-EE4677"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Языки (Languages)

******

Текущий README.md поддерживает следующие языки:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ko.md)
- Русский [ru] # текущий
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ar.md)

******

### Введение

******

Плагин AutoJs6 Kotlin Runtime позволяет AutoJs6 напрямую компилировать и запускать однофайловый исходный код Kotlin (`.kt`). Плагин содержит встроенный компилятор Kotlin/JVM 2.3.21 (K2) и конвертер байткода D8 8.13.17; скомпилированный результат выполняется в одноразовом процессе worker. Ни компилятор, ни скрипт никогда не выполняются внутри процесса AutoJs6.

Этот плагин и [Java Runtime](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime) — родственные плагины: их можно установить одновременно, каждый обслуживает соответственно исходники Kotlin / Java, а AutoJs6 запоминает выбранный компонент компилятора отдельно для каждого языка.

******

### Возможности

******

- Предоставляет сервис компиляции/выполнения `org.autojs.plugin.JVM_SOURCE` и сервис обнаружения `org.autojs.plugin.INFO` для центра плагинов; оба защищены подписью и работают в отдельных вспомогательных процессах.
- Встроенный компилятор Kotlin/JVM 2.3.21 (K2); скрипты нацелены на байткод JVM 1.8, преобразуемый в DEX через D8 8.13.17 перед выполнением.
- Поддерживает четыре индивидуально авторизуемых моста возможностей хоста: живой вывод консоли `console().log/error`, запуск приложений `app().launch`, прерываемый `sleep` и сообщения `toast`.
- Поддерживает стандартную библиотеку Kotlin и структурированный параллелизм `kotlinx-coroutines-core-jvm` 1.11.0 (`Dispatchers.Default` / `IO` / `Unconfined`).
- Аутентифицированный кэш компиляции: медиана тёплых попаданий для того же исходника около 46 мс против около 577 мс холодной компиляции (примерно в 12,5 раза быстрее на устройстве); медиана выполнения около 30 мс.
- Ошибки компиляции сохраняют исходную диагностику K2 с позициями строка/столбец; BOM, различия путей Windows и усечение китайского текста/эмодзи обрабатываются детерминированно.
- Каждое выполнение происходит в свежем одноразовом процессе worker, который затем утилизируется; остановка скрипта из хоста немедленно прерывает sleep и корутины.
- README и CHANGELOG доступны на десяти языках: упрощённый китайский, традиционный китайский (Гонконг/Тайвань), английский, французский, испанский, японский, корейский, русский и арабский.

******

### Быстрый старт

******

- **Установка** — Скачайте APK со страницы [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/releases) и установите его, либо соберите локально согласно разделу «Сборка» ниже. Внимание: плагин должен быть подписан тем же сертификатом, что и AutoJs6; debug-APK с временным сертификатом из GitHub Actions пригоден только для инспекции сборки и не может интегрироваться с реальным хостом. Код версии хоста AutoJs6 должен быть не ниже 5276.
- **Включение** — Запуск JVM-исходников — пока экспериментальная функция AutoJs6: включите экспериментальный переключатель в хосте и явно выберите этот плагин как компонент компилятора для языка Kotlin. Иначе при запуске появятся стабильные коды ошибок `JVM_SOURCE_EXPERIMENT_DISABLED` или `JVM_SOURCE_PROVIDER_NOT_SELECTED` соответственно.
- **Запуск** — Создайте файл `.kt` в редакторе AutoJs6, напишите входной класс, реализующий интерфейс `AutoJsJvmEntry`, и нажмите запуск (см. пример ниже). Текущий хост нормализует имя исходника в `Main.kt`, простое имя входа фиксировано как `Main`; обычный ASCII-пакет и импорты необязательны.
- **Диагностика** — При ошибке компиляции консоль показывает диагностику K2 с позициями строка/столбец; двуязычные разборы четырёх типичных ошибок (пропущенный import, несоответствие типов, отсутствие входного интерфейса, несоответствие пакета) находятся в [samples/errors](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/tree/main/samples/errors). Сбои выполнения выдают только стабильные коды (например `JVM_SOURCE_COMPILE_FAILED`, `JVM_SOURCE_TIMEOUT`) и никогда не раскрывают внутренние пути.

******

### Пример использования

******

Минимальный готовый к запуску пример, демонстрирующий все четыре текущие возможности хоста:

```kotlin
import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry
import org.autojs.plugin.jvmsource.api.JvmScriptContext

class Main : AutoJsJvmEntry {
    override fun run(context: JvmScriptContext): Any {
        context.console().log("Hello from Kotlin 2.3.21")
        context.toast("AutoJs6 Kotlin Runtime")
        context.sleep(500L)
        val launched = context.app().launch("org.autojs.autojs6")
        return launched
    }
}
```

`console().log/error` передаётся построчно в реальном времени во время работы скрипта; `sleep` немедленно прерывается операцией остановки. Больше примеров в каталоге [samples](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/tree/main/samples): смоук возможностей `m5-capabilities.kt`, демо отмены `m6-cancellation.kt`, пример корутин `coroutines.kt`.

******

### Границы возможностей

******

Ради безопасности и предсказуемости текущая версия сознательно сохраняет следующие границы:

- Только однофайловый исходник Kotlin, не более 4 MiB; многофайловые проекты, class-файлы, JAR и DEX-входы пока не поддерживаются.
- Входной класс должен реализовывать `AutoJsJvmEntry` (Entry API 2); имена пакетов поддерживают только обычные ASCII-идентификаторы — пакеты с обратными кавычками и не-ASCII отклоняются явно.
- Никакие зависимости Maven или сторонние не разрешаются; библиотеки, доступные скриптам, — ровно те, что в списке «Библиотеки скриптов» ниже.
- Байткод скриптов нацелен на JVM 1.8; class-файлы выше Java 8 отклоняются до D8.
- Процесс worker утилизируется после каждого выполнения, поэтому корутины и другая фоновая работа не переживают возврат из `run`; не используйте `GlobalScope`.
- Опубликованный протокол — JVM Source Protocol 1.1; Protocol 1.2 — только предложение: буфер обмена/документы/HTTPS/уведомления пока недоступны.

******

### Библиотеки скриптов

******

Библиотеки, доступные для компиляции и выполнения скриптов, образуют точно зафиксированный белый список:

#### Доступно

- Android framework: символы компиляции берутся из class-only заглушек API 24; поведение во время выполнения по-прежнему зависит от версии ОС устройства.
- AutoJs6 JVM Entry API 2: `JvmScriptContext` — единственный поддерживаемый мост хоста.
- Стандартная библиотека Kotlin 2.3.21 (зафиксирована на версии встроенного компилятора).
- `kotlinx-coroutines-core-jvm` 1.11.0: поддерживаются `runBlocking`, структурированный `async`, `delay` и `Dispatchers.Default` / `IO` / `Unconfined`.

#### Недоступно

- `kotlinx-coroutines-android` и `Dispatchers.Main`: у процесса worker нет UI/Looper, поэтому диспетчеризация в Main завершается ошибкой.
- Полный `kotlin-reflect`: остаются только базовые ссылки на классы stdlib; `kotlin.reflect.full.*` не поддерживается.
- kotlinx-serialization, debug/test-модули корутин, плагины компилятора и любые транзитивные зависимости Maven.

******

### Безопасность и изоляция

******

Плагин спроектирован по принципу запрета по умолчанию; следующие ограничения действуют всегда:

- Компилятор и worker работают в отдельных процессах и никогда не входят в процесс AutoJs6; сервисы принимают вызовы только от хоста с той же подписью.
- Каждая возможность хоста (запуск приложений, toast и т.д.) авторизуется по каждому запросу; неавторизованные возможности отклоняются до диспетчеризации.
- Исходники, артефакты и диагностика имеют пределы размера; диагностика усекается только на границах кодовых точек Unicode — никогда пол-эмодзи или некорректный UTF-8.
- Внешние сообщения об ошибках содержат только стабильные коды и очищенный текст, никогда — приватные пути, дайджесты или идентификаторы процессов.
- Кэш компиляции аутентифицирован; любое изменение тулчейна или библиотек времени выполнения автоматически инвалидирует все прежние кэши.

******

### История выпусков

******

# v0.7.0

###### 2026/09/11

* `Улучшение` Проверка сборки отклоняет непреднамеренные нативные зависимости и создает отчет JSON

# v0.7.0-m10

###### 2026/08/26

* `Подсказка` Опубликованные возможности остаются на Protocol 1.1 / Entry API 2; ни одна возможность 1.2 не открывается до реализации на стороне хоста
* `Новое` Добавлено предложение возможностей JVM Source Protocol 1.2, направленное на ревью хоста: ограниченный буфер обмена, документы с разрешения пользователя, HTTPS через прокси хоста и уведомления, управляемые хостом
* `Новое` Добавлен четырёхэтапный конвейер вызовов хоста (авторизация → валидация payload → диспетчеризация → валидация ответа); существующие `app.launch` и `toast.show` переведены без изменения поведения
* `Улучшение` Замороженные AAR протокола переведены на фиксацию происхождения schema-2, добавлены staging-скрипт обновления и полная SOP
* `Улучшение` Добавлены семь комбинационных тестов согласования 1.1/1.2, покрывающих пары старый/новый хост-плагин, даунгрейд и стабильные отказы

# v0.6.0-m9

###### 2026/08/26

* `Подсказка` `Dispatchers.Main`, полный `kotlin-reflect` и kotlinx-serialization по-прежнему вне поверхности скриптов
* `Новое` В библиотеки скриптов добавлена точно зафиксированная `kotlinx-coroutines-core-jvm` 1.11.0: структурированный параллелизм и кооперативная отмена
* `Новое` Добавлен готовый к запуску пример корутин `samples/coroutines.kt`
* `Улучшение` Отпечатки рантайма и ключи кэша компиляции теперь включают идентичность библиотеки корутин, поэтому все прежние кэши инвалидируются автоматически

##### Подробнее об истории выпусков см.

* [CHANGELOG-ru.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.changelog/CHANGELOG-ru.md)

******

### Сборка

******

Репозиторий содержит замороженные AAR протокола (`protocol/`) и собирается офлайн без чекаута AutoJs6. Рекомендуется JDK 21; Android SDK должен предоставлять platforms 24 и 36. Debug-сборка:

```powershell
.\gradlew.bat :app:assembleDebug --offline
```

Release-сборка:

```powershell
.\gradlew.bat :app:assembleRelease --offline
```

Параметры сборки централизованы в `version.properties`: текущая версия 0.7.0-m10 (build 7), minSdk 26, targetSdk 36.

Release/debug APK должны быть подписаны тем же сертификатом, что и AutoJs6, чтобы хост их принял; локальные материалы подписи находятся в игнорируемых системой контроля версий `sign.properties` и `app/sm003.jks`. Полные команды контроля и процесс выпуска см. в [RELEASE_CHECKLIST](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/docs/RELEASE_CHECKLIST.md).

******

### Структура ресурсов

******

```text
.readme/lang_*.json
.readme/template_readme.md
.changelog/lang_*.json
.changelog/template_changelog.md
.python/generate_markdown.py
app/src/main/res/values*/strings.xml
```

`strings.xml` локализует имя и описание плагина; README и CHANGELOG генерируются скриптом `.python/generate_markdown.py` из JSON-источников. Для изменения документации редактируйте JSON-источники, а не сгенерированный Markdown.

******

### Ссылки

******

- Документация AutoJs6: https://docs.autojs6.com
- Домашняя страница проекта AutoJs6: https://github.com/SuperMonster003/AutoJs6
- Родственный плагин Java Runtime: https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime
- Официальный проект Kotlin: https://github.com/JetBrains/kotlin
- Каталог примеров: https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/tree/main/samples
- Дорожная карта проекта (с записями проверок по вехам): https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/ROADMAP.md
- Уведомления о сторонних компонентах: https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/THIRD_PARTY_NOTICES.md


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/docs/16kb.md)
