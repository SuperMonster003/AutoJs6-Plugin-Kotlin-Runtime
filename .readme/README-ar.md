<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-kotlin-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>إضافة لترجمة وتشغيل شيفرة Kotlin 2.3.21 أحادية الملف لتطبيق AutoJs6</p>

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

### اللغات (Languages)

******

يدعم README.md الحالي اللغات التالية:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ru.md)
- العربية [ar] # الحالي

******

### مقدمة

******

تتيح إضافة AutoJs6 Kotlin Runtime لتطبيق AutoJs6 ترجمة وتشغيل شيفرة Kotlin المصدرية أحادية الملف (`.kt`) مباشرة. تتضمن الإضافة مترجم Kotlin/JVM 2.3.21 (K2) ومحوّل البايت كود D8 8.13.17، ويُنفَّذ الناتج المترجم في عملية worker أحادية الاستخدام؛ فلا المترجم ولا السكربت يعملان أبدًا داخل عملية AutoJs6.

هذه الإضافة و[Java Runtime](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime) إضافتان شقيقتان: يمكن تثبيتهما معًا لتخدم كل منهما شيفرة Kotlin / Java على التوالي، ويتذكر AutoJs6 مكوّن المترجم المختار لكل لغة على حدة.

******

### الميزات

******

- توفّر خدمة الترجمة/التنفيذ `org.autojs.plugin.JVM_SOURCE` وخدمة الاكتشاف `org.autojs.plugin.INFO` لمركز الإضافات، وكلتاهما محمية بالتوقيع وتعملان في عمليات مساعدة منفصلة.
- تتضمن مترجم Kotlin/JVM 2.3.21 (K2)؛ تستهدف السكربتات بايت كود JVM 1.8، ويحوَّل إلى DEX عبر D8 8.13.17 قبل التنفيذ.
- تدعم أربعة جسور لقدرات المضيف بتفويض فردي: إخراج الطرفية الحي `console().log/error`، وتشغيل التطبيقات `app().launch`، و`sleep` القابل للمقاطعة، ورسائل `toast`.
- تدعم مكتبة Kotlin القياسية والتزامن المهيكل عبر `kotlinx-coroutines-core-jvm` 1.11.0 (`Dispatchers.Default` / `IO` / `Unconfined`).
- ذاكرة ترجمة مخبأة موثّقة: وسيط الإصابات الدافئة للمصدر نفسه نحو 46 ملّي ثانية مقابل نحو 577 ملّي ثانية للترجمة الباردة (أسرع بنحو 12.5 مرة على الجهاز)؛ وسيط التنفيذ نحو 30 ملّي ثانية.
- تحتفظ أخطاء الترجمة بتشخيصات K2 الأصلية مع مواضع السطر/العمود؛ وتُعالَج ترويسة BOM واختلافات مسارات Windows واقتطاع النص الصيني/الإيموجي بشكل حتمي.
- كل تنفيذ يجري في عملية worker جديدة أحادية الاستخدام تُصفّى بعده؛ وإيقاف السكربت من المضيف يقاطع sleep والكوروتينات فورًا.
- يتوفر README و CHANGELOG بعشر لغات: الصينية المبسطة والصينية التقليدية (هونغ كونغ/تايوان) والإنجليزية والفرنسية والإسبانية واليابانية والكورية والروسية والعربية.

******

### البدء السريع

******

- **التثبيت** — نزّل ملف APK من [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/releases) وثبّته، أو ابنِ محليًا كما في قسم البناء أدناه. تنبيه: يجب توقيع الإضافة بنفس شهادة AutoJs6؛ وملف debug APK ذو الشهادة المؤقتة الصادر عن GitHub Actions مخصص لفحص البناء فقط ولا يمكنه التكامل مع مضيف حقيقي. يجب ألا يقل رمز إصدار مضيف AutoJs6 عن 5276.
- **التفعيل** — تشغيل شيفرة JVM حاليًا ميزة تجريبية في AutoJs6: فعّل مفتاح التجربة في المضيف، ثم اختر هذه الإضافة صراحةً كمكوّن المترجم للغة Kotlin. إن لم تفعل، يعرض التشغيل رمزي الخطأ الثابتين `JVM_SOURCE_EXPERIMENT_DISABLED` أو `JVM_SOURCE_PROVIDER_NOT_SELECTED` على التوالي.
- **التشغيل** — أنشئ ملف `.kt` في محرر AutoJs6، واكتب صنف دخول ينفّذ الواجهة `AutoJsJvmEntry` ثم اضغط تشغيل (انظر مثال الاستخدام أدناه). يوحّد المضيف الحالي اسم المصدر إلى `Main.kt` مع تثبيت الاسم البسيط للدخول على `Main`؛ وحزمة ASCII عادية وعبارات import اختيارية.
- **استكشاف الأخطاء** — عند فشل الترجمة تعرض الطرفية تشخيصات K2 مع مواضع السطر/العمود؛ وتوجد شروح ثنائية اللغة للأخطاء الأربعة الشائعة (import مفقود، وعدم توافق الأنواع، وغياب واجهة الدخول، وعدم توافق الحزمة) في [samples/errors](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/tree/main/samples/errors). أما إخفاقات التشغيل فتُظهر رموز خطأ ثابتة فقط (مثل `JVM_SOURCE_COMPILE_FAILED` و`JVM_SOURCE_TIMEOUT`) ولا تسرّب المسارات الداخلية أبدًا.

******

### مثال الاستخدام

******

مثال أدنى جاهز للتشغيل يوضح قدرات المضيف الأربع الحالية جميعها:

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

يُبثّ `console().log/error` سطرًا بسطر أثناء تشغيل السكربت؛ ويُقاطَع `sleep` فورًا بإجراء الإيقاف. مزيد من الأمثلة في مجلد [samples](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/tree/main/samples): اختبار القدرات `m5-capabilities.kt`، وعرض الإلغاء `m6-cancellation.kt`، ومثال الكوروتينات `coroutines.kt`.

******

### الحدود

******

حفاظًا على سلوك آمن وقابل للتنبؤ، يحافظ الإصدار الحالي عمدًا على الحدود التالية:

- شيفرة Kotlin أحادية الملف فقط بحد أقصى 4 MiB؛ ولا تُدعم بعدُ المشاريع متعددة الملفات وملفات class و JAR ومدخلات DEX.
- يجب أن ينفّذ صنف الدخول الواجهة `AutoJsJvmEntry` (Entry API 2)؛ وتدعم أسماء الحزم معرّفات ASCII العادية فقط — وتُرفض الحزم المهربة بعلامات backtick وغير ASCII صراحةً.
- لا تُحل أي تبعيات Maven أو تبعيات خارجية؛ والمكتبات المتاحة للسكربتات هي بالضبط قائمة «مكتبات السكربت» أدناه.
- يستهدف بايت كود السكربتات JVM 1.8؛ وتُرفض ملفات class الأعلى من Java 8 قبل D8.
- تُصفّى عملية worker بعد كل تنفيذ، فلا تبقى الكوروتينات وأعمال الخلفية بعد عودة `run`؛ لا تستخدم `GlobalScope`.
- البروتوكول المنشور هو JVM Source Protocol 1.1؛ و Protocol 1.2 مجرد اقتراح — فقدرات الحافظة/المستندات/HTTPS/الإشعارات غير متاحة بعد.

******

### مكتبات السكربت

******

تشكّل المكتبات المتاحة لترجمة السكربتات وتنفيذها قائمة سماح مثبتة بدقة:

#### متاح

- إطار عمل Android: رموز وقت الترجمة تأتي من أنصال class-only لواجهة API 24؛ ويظل سلوك وقت التشغيل معتمدًا على إصدار نظام الجهاز.
- AutoJs6 JVM Entry API 2: يمثل `JvmScriptContext` جسر المضيف الوحيد المدعوم.
- مكتبة Kotlin القياسية 2.3.21 (مثبتة على إصدار المترجم المدمج).
- `kotlinx-coroutines-core-jvm` 1.11.0: تُدعم `runBlocking` و`async` المهيكلة و`delay` و`Dispatchers.Default` / `IO` / `Unconfined`.

#### غير متاح

- `kotlinx-coroutines-android` و`Dispatchers.Main`: لا تملك عملية worker واجهة UI/Looper، لذا يفشل الإرسال إلى Main.
- `kotlin-reflect` الكاملة: تبقى مراجع الأصناف الأساسية للمكتبة القياسية فقط؛ و`kotlin.reflect.full.*` غير مدعومة.
- kotlinx-serialization ووحدات debug/test للكوروتينات وإضافات المترجم وأي تبعيات Maven متعدية.

******

### الأمان والعزل

******

صُممت الإضافة على مبدأ الرفض الافتراضي؛ والقيود التالية سارية دائمًا:

- يعمل المترجم و worker في عمليتين منفصلتين ولا يدخلان أبدًا عملية AutoJs6؛ ولا تقبل الخدمات سوى استدعاءات مضيف بنفس التوقيع.
- تُفوَّض كل قدرة مضيف (تشغيل التطبيقات و toast وغيرها) لكل طلب على حدة؛ وتُرفض القدرات غير المفوضة قبل الإرسال.
- للمصدر والمخرجات والتشخيصات جميعًا سقوف حجم؛ ولا تُقتطع التشخيصات إلا عند حدود نقاط ترميز Unicode — فلا نصف إيموجي ولا UTF-8 مشوّه أبدًا.
- لا تحمل رسائل الخطأ الخارجية سوى رموز ثابتة ونص منقّى، ولا تكشف أبدًا المسارات الخاصة أو البصمات أو هويات العمليات.
- ذاكرة الترجمة المخبأة موثّقة؛ وأي تغيير في سلسلة الأدوات أو مكتبات وقت التشغيل يبطل تلقائيًا كل الذواكر السابقة.

******

### سجل الإصدارات

******

# v0.7.0

###### 2026/09/11

* `تحسين` التحقق أثناء البناء لمنع إدخال تبعيات أصلية غير مقصودة, مع تقرير JSON

# v0.7.0-m10

###### 2026/08/26

* `تلميح` تبقى القدرات المنشورة عند Protocol 1.1 / Entry API 2؛ ولا تُفتح أي قدرة من 1.2 قبل تنفيذها في المضيف
* `ميزة` إضافة اقتراح قدرات JVM Source Protocol 1.2 وتقديمه لمراجعة المضيف: حافظة محدودة، ومستندات بإذن المستخدم، و HTTPS عبر وكيل المضيف، وإشعارات يديرها المضيف
* `ميزة` إضافة خط أنابيب لاستدعاء المضيف من أربع مراحل (التفويض ← التحقق من الحمولة ← الإرسال ← التحقق من الاستجابة)؛ ونُقل `app.launch` و `toast.show` الحاليان دون تغيير في السلوك
* `تحسين` ترقية ملفات AAR المجمّدة للبروتوكول إلى قفل مصدر schema-2، مع سكربت تحديث خاص بالتحضير و SOP كاملة
* `تحسين` إضافة سبعة اختبارات توليفية لتفاوض 1.1/1.2 تغطي اقتران المضيف/الإضافة القديم والجديد ومسارات التخفيض والرفض الثابت

# v0.6.0-m9

###### 2026/08/26

* `تلميح` لا تزال `Dispatchers.Main` و`kotlin-reflect` الكاملة و kotlinx-serialization خارج سطح السكربت
* `ميزة` إضافة `kotlinx-coroutines-core-jvm` 1.11.0 المثبتة بدقة إلى مكتبات السكربت: تزامن مهيكل وإلغاء تعاوني
* `ميزة` إضافة مثال الكوروتينات الجاهز للتشغيل `samples/coroutines.kt`
* `تحسين` أصبحت بصمات وقت التشغيل ومفاتيح ذاكرة الترجمة المخبأة تتضمن هوية مكتبة الكوروتينات، فتُبطل كل الذواكر السابقة تلقائيًا

##### لمزيد من سجل الإصدارات، انظر

* [CHANGELOG-ar.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.changelog/CHANGELOG-ar.md)

******

### البناء

******

يتضمن المستودع ملفات AAR مجمّدة للبروتوكول (`protocol/`) ويُبنى دون اتصال ودون الحاجة إلى نسخة من AutoJs6. يوصى بـ JDK 21؛ ويجب أن يوفر Android SDK المنصتين 24 و 36. بناء Debug:

```powershell
.\gradlew.bat :app:assembleDebug --offline
```

بناء Release:

```powershell
.\gradlew.bat :app:assembleRelease --offline
```

تتركز معاملات البناء في `version.properties`: الإصدار الحالي 0.7.0-m10 (build 7)، minSdk 26، targetSdk 36.

يجب توقيع APK بنسختي release/debug بنفس شهادة AutoJs6 ليقبلها المضيف؛ وتوجد مواد التوقيع المحلية في `sign.properties` و `app/sm003.jks` المتجاهلين من نظام التحكم بالإصدارات. انظر [RELEASE_CHECKLIST](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/docs/RELEASE_CHECKLIST.md) لأوامر البوابات وسير الإصدار كاملة.

******

### بنية الموارد

******

```text
.readme/lang_*.json
.readme/template_readme.md
.changelog/lang_*.json
.changelog/template_changelog.md
.python/generate_markdown.py
app/src/main/res/values*/strings.xml
```

يوفّر `strings.xml` توطين اسم الإضافة ووصفها؛ ويُولَّد README و CHANGELOG بواسطة `.python/generate_markdown.py` من مصادر JSON. لتعديل الوثائق حرّر مصادر JSON لا ملفات Markdown المولّدة.

******

### روابط

******

- وثائق AutoJs6: https://docs.autojs6.com
- الصفحة الرئيسية لمشروع AutoJs6: https://github.com/SuperMonster003/AutoJs6
- الإضافة الشقيقة Java Runtime: https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime
- مشروع Kotlin الرسمي: https://github.com/JetBrains/kotlin
- مجلد الأمثلة: https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/tree/main/samples
- خارطة طريق المشروع (مع سجلات التحقق لكل مرحلة): https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/ROADMAP.md
- إشعارات الأطراف الثالثة: https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/THIRD_PARTY_NOTICES.md


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/docs/16kb.md)
