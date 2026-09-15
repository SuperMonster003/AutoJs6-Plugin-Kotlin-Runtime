<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-kotlin-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>Plugin de compilación y ejecución de código Kotlin 2.3.21 de archivo único para AutoJs6</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/commit/17f42fa7aa2a2046c74e558f313b7510d155f365"><img alt="Created" src="https://img.shields.io/date/1787396606?color=2e7d32&label=Created"/></a>
    <br>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Idiomas (Languages)

******

El README.md actual admite los siguientes idiomas:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-fr.md)
- Español [es] # actual
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ar.md)

******

### Introducción

******

El plugin AutoJs6 Kotlin Runtime permite a AutoJs6 compilar y ejecutar directamente código fuente Kotlin de archivo único (`.kt`). Integra el compilador Kotlin/JVM 2.3.21 (K2) y el conversor de bytecode D8 8.13.17, y ejecuta el resultado compilado en un proceso worker desechable; ni el compilador ni el script se ejecutan nunca dentro del proceso de AutoJs6.

Este plugin y [Java Runtime](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime) son plugins hermanos: pueden instalarse juntos, sirviendo respectivamente al código Kotlin / Java, y AutoJs6 recuerda el componente de compilación elegido para cada lenguaje.

******

### Funciones

******

- Proporciona el servicio de compilación/ejecución `org.autojs.plugin.JVM_SOURCE` y el servicio de descubrimiento `org.autojs.plugin.INFO` del centro de plugins, ambos protegidos por firma y ejecutados en procesos auxiliares separados.
- Integra el compilador Kotlin/JVM 2.3.21 (K2); los scripts generan bytecode JVM 1.8, convertido a DEX por D8 8.13.17 antes de ejecutarse.
- Admite cuatro puentes de capacidades del host autorizados individualmente: salida de consola en vivo `console().log/error`, lanzamiento de aplicaciones `app().launch`, `sleep` interrumpible y mensajes `toast`.
- Admite la biblioteca estándar de Kotlin y la concurrencia estructurada de `kotlinx-coroutines-core-jvm` 1.11.0 (`Dispatchers.Default` / `IO` / `Unconfined`).
- Caché de compilación autenticada: mediana de ~46 ms en aciertos calientes frente a ~577 ms de compilación en frío (unas 12,5 veces más rápido en dispositivo); mediana de ejecución de ~30 ms.
- Los errores de compilación conservan los diagnósticos K2 originales con línea/columna; BOM, diferencias de rutas de Windows y truncado de chino/emoji se tratan de forma determinista.
- Cada ejecución se realiza en un proceso worker desechable nuevo que se retira después; detener el script desde el host interrumpe rápidamente sleep y las corrutinas.
- README y CHANGELOG están disponibles en diez idiomas: chino simplificado, chino tradicional (HK/TW), inglés, francés, español, japonés, coreano, ruso y árabe.

******

### Inicio rápido

******

- **Instalar** — Descargue el APK desde [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/releases) e instálelo, o compile localmente como se describe en la sección Compilación. Atención: el plugin debe firmarse con el mismo certificado que AutoJs6; el APK debug con certificado temporal generado por GitHub Actions solo sirve para inspección de build y no puede integrarse con un host real. El código de versión del host AutoJs6 debe ser al menos 5276.
- **Activar** — Ejecutar código JVM es actualmente una función experimental de AutoJs6: active el interruptor experimental en el host y seleccione explícitamente este plugin como componente de compilación para el lenguaje Kotlin. Si falta alguno de los pasos, la ejecución informa los códigos estables `JVM_SOURCE_EXPERIMENT_DISABLED` o `JVM_SOURCE_PROVIDER_NOT_SELECTED` respectivamente.
- **Ejecutar** — Cree un archivo `.kt` en el editor de AutoJs6, escriba una clase de entrada que implemente la interfaz `AutoJsJvmEntry` y pulse ejecutar (vea el ejemplo de uso más abajo). El host actual normaliza el nombre del código a `Main.kt` con el nombre simple de entrada fijado en `Main`; un paquete ASCII ordinario y los imports son opcionales.
- **Solucionar problemas** — Si la compilación falla, la consola muestra los diagnósticos K2 con línea/columna; las explicaciones bilingües de los cuatro errores comunes (import ausente, tipos incompatibles, interfaz de entrada ausente, paquete no conforme) están en [samples/errors](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/tree/main/samples/errors). Los fallos de ejecución solo exponen códigos estables (como `JVM_SOURCE_COMPILE_FAILED`, `JVM_SOURCE_TIMEOUT`) y nunca filtran rutas internas.

******

### Ejemplo de uso

******

Un ejemplo mínimo listo para ejecutar que demuestra las cuatro capacidades actuales del host:

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

`console().log/error` se transmite línea a línea mientras el script se ejecuta; `sleep` se interrumpe rápidamente con una acción de parada. Más ejemplos en el directorio [samples](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/tree/main/samples): prueba de capacidades `m5-capabilities.kt`, demostración de cancelación `m6-cancellation.kt` y ejemplo de corrutinas `coroutines.kt`.

******

### Límites

******

Para garantizar un comportamiento seguro y predecible, la versión actual mantiene deliberadamente los siguientes límites:

- Solo código Kotlin de archivo único, hasta 4 MiB; proyectos multiarchivo, archivos class, JAR y entradas DEX aún no se admiten.
- La clase de entrada debe implementar `AutoJsJvmEntry` (Entry API 2); solo se aceptan identificadores de paquete ASCII ordinarios — los paquetes con backticks o no ASCII se rechazan explícitamente.
- No se resuelven dependencias Maven ni de terceros; las bibliotecas disponibles son exactamente las de la lista Bibliotecas de script.
- El bytecode de los scripts apunta a JVM 1.8; los archivos class superiores a Java 8 se rechazan antes de D8.
- El proceso worker se retira tras cada ejecución: las corrutinas y otras tareas en segundo plano no sobreviven al retorno de `run`; no use `GlobalScope`.
- El protocolo publicado es JVM Source Protocol 1.1; Protocol 1.2 es solo una propuesta — portapapeles/documentos/HTTPS/notificaciones aún no están disponibles.

******

### Bibliotecas de script

******

Las bibliotecas disponibles para compilar y ejecutar scripts forman una lista blanca exactamente fijada:

#### Disponible

- Framework de Android: los símbolos de compilación provienen de stubs class-only de API 24; el comportamiento en ejecución sigue dependiendo de la versión del sistema del dispositivo.
- AutoJs6 JVM Entry API 2: `JvmScriptContext` es el único puente de host admitido.
- Biblioteca estándar de Kotlin 2.3.21 (fijada a la versión del compilador integrado).
- `kotlinx-coroutines-core-jvm` 1.11.0: se admiten `runBlocking`, `async` estructurado, `delay` y `Dispatchers.Default` / `IO` / `Unconfined`.

#### No disponible

- `kotlinx-coroutines-android` y `Dispatchers.Main`: el proceso worker no tiene UI/Looper, por lo que despachar a Main falla.
- `kotlin-reflect` completo: solo quedan referencias básicas de clases de la stdlib; `kotlin.reflect.full.*` no está admitido.
- kotlinx-serialization, módulos debug/test de corrutinas, plugins del compilador y cualquier dependencia Maven transitiva.

******

### Seguridad y aislamiento

******

El plugin está diseñado con denegación por defecto; las siguientes restricciones siempre están en vigor:

- El compilador y el worker se ejecutan en procesos separados y nunca entran en el proceso de AutoJs6; los servicios solo aceptan llamadas de un host con la misma firma.
- Cada capacidad del host (lanzar aplicaciones, toast, etc.) se autoriza por petición; las capacidades no autorizadas se rechazan antes del despacho.
- El código, los artefactos y los diagnósticos tienen límites de tamaño; los diagnósticos solo se truncan en fronteras de puntos de código Unicode — nunca medio emoji ni UTF-8 malformado.
- Los mensajes de error externos solo contienen códigos estables y texto saneado, nunca rutas privadas, huellas ni identidades de procesos.
- La caché de compilación está autenticada; cualquier cambio de herramientas o bibliotecas invalida automáticamente todas las cachés anteriores.

******

### Historial de versiones

******

# v0.7.2

###### 2026/09/15

* `Mejora` compileSdk y targetSdk suben a 37 (Android 17); el comportamiento del plugin no depende del nuevo objetivo

# v0.7.1

###### 2026/09/13

* `Corrección` El centro de complementos puede activar un proveedor recién instalado mediante una entrada protegida; los metadatos reflejan el paquete instalado
* `Mejora` Activación del host, metadatos, documentación traducida y recopilación de APK firmados conforme a las convenciones comunes

# v0.7.0

###### 2026/09/11

* `Mejora` La verificación de compilación rechaza dependencias nativas accidentales y genera un informe JSON

##### Para más historial, consulte

* [CHANGELOG-es.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/app/src/main/assets/doc/CHANGELOG-es.md)

******

### Compilación

******

El repositorio incluye AAR de protocolo congelados (`protocol/`) y se compila sin conexión sin checkout de AutoJs6. Se recomienda JDK 21; el SDK de Android debe proporcionar las platforms 24 y 36. Build debug:

```powershell
.\gradlew.bat :app:assembleDebug --offline
```

Build release:

```powershell
.\gradlew.bat :app:assembleRelease --offline
```

Los parámetros de build se centralizan en `version.properties`: versión actual 0.7.2-m10 (build 27), minSdk 26, targetSdk 37.

Los APK release/debug deben firmarse con el mismo certificado que AutoJs6 para que el host los acepte; el material de firma local reside en `sign.properties` y `app/sm003.jks`, ignorados por el control de versiones. Vea [RELEASE_CHECKLIST](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/docs/RELEASE_CHECKLIST.md) para los comandos de control y el flujo de publicación.

******

### Estructura de recursos

******

```text
.readme/lang_*.json
.readme/template_readme.md
.changelog/lang_*.json
.changelog/template_changelog.md
.python/generate_markdown.py
app/src/main/res/values*/strings.xml
```

`strings.xml` localiza el nombre y la descripción del plugin; README y CHANGELOG se generan con `.python/generate_markdown.py` a partir de las fuentes JSON. Para modificar la documentación, edite las fuentes JSON en lugar del Markdown generado.

******

### Enlaces

******

- Documentación de AutoJs6: https://docs.autojs6.com
- Página del proyecto AutoJs6: https://github.com/SuperMonster003/AutoJs6
- Plugin hermano Java Runtime: https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime
- Proyecto oficial de Kotlin: https://github.com/JetBrains/kotlin
- Directorio de ejemplos: https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/tree/main/samples
- Hoja de ruta (con registros de verificación por hito): https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/ROADMAP.md
- Avisos de terceros: https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/THIRD_PARTY_NOTICES.md


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/docs/16kb.md)
