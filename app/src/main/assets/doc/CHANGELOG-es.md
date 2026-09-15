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

# v0.7.0-m10

###### 2026/08/26

* `Nota` Las capacidades publicadas se mantienen en Protocol 1.1 / Entry API 2; ninguna capacidad 1.2 se abre antes de que el host la implemente
* `Novedad` Añadida la propuesta de capacidades JVM Source Protocol 1.2, enviada a revisión del host: portapapeles acotado, documentos autorizados por el usuario, HTTPS mediado por el host y notificaciones gestionadas por el host
* `Novedad` Añadida una canalización de llamadas al host en cuatro etapas (autorización → validación del payload → despacho → validación de la respuesta); `app.launch` y `toast.show` migrados sin cambios de comportamiento
* `Mejora` Los AAR de protocolo congelados pasan a bloqueo de procedencia schema-2, con script de actualización staging-only y SOP completa
* `Mejora` Añadidas siete pruebas de negociación 1.1/1.2 que cubren combinaciones de host/plugin antiguos y nuevos, degradación y rechazos estables

# v0.6.0-m9

###### 2026/08/26

* `Nota` `Dispatchers.Main`, `kotlin-reflect` completo y kotlinx-serialization siguen fuera de la superficie de script
* `Novedad` Añadida `kotlinx-coroutines-core-jvm` 1.11.0, fijada exactamente, a las bibliotecas de script: concurrencia estructurada y cancelación cooperativa
* `Novedad` Añadido el ejemplo de corrutinas listo para ejecutar `samples/coroutines.kt`
* `Mejora` Las huellas de runtime y las claves de la caché de compilación ahora incluyen la identidad de la biblioteca de corrutinas: todas las cachés anteriores se invalidan automáticamente

# v0.5.0-m8

###### 2026/08/25

* `Novedad` Añadidos ejemplos bilingües que explican cuatro errores de compilación comunes: import ausente, tipos incompatibles, interfaz de entrada ausente y paquete no conforme
* `Corrección` Corregida la pérdida de posiciones línea/columna de K2 por diferencias en los separadores de ruta de Windows
* `Corrección` Un BOM UTF-8 inicial ahora se elimina sin desplazar las posiciones de la primera línea
* `Mejora` Redacción más clara de la política de paquetes: solo se aceptan identificadores ASCII ordinarios, con mensajes estables y legibles al rechazar
* `Mejora` Los diagnósticos se presupuestan en UTF-8 y solo se truncan en fronteras de puntos de código Unicode — nunca medio emoji

# v0.4.0-m7

###### 2026/08/25

* `Novedad` Añadido un arnés de estrés e inyección de fallos en dispositivo (`m7-harness`) con scripts PowerShell/POSIX de un solo comando
* `Corrección` Corregida la persistencia del estado del compilador entre épocas de binding del host con la caché desactivada
* `Mejora` APK release reducido de 45,34 MB a 30,38 MB (-32,99 %) usando un classpath de compilación determinista class-only API 24
* `Mejora` Caché de compilación: mediana de 46 ms en caliente frente a 577 ms en frío — unas 12,5 veces más rápido en dispositivo
* `Mejora` El proceso del compilador ahora vive por binding y se retira en el último unbind; el descubrimiento de metadatos pasa al proceso separado `:discovery`

# v0.3.0-m6

###### 2026/08/25

* `Novedad` Añadidos el CHANGELOG, la lista de verificación de publicación y las puertas CI completas de GitHub Actions: pruebas unitarias, ambos APK, Lint e integridad del protocolo
* `Mejora` Lógica de build centralizada en build-logic con una instantánea del código del plugin platform-versions: un checkout limpio compila tal cual
* `Mejora` Eliminadas las ramas de API de Android hechas redundantes por minSdk 26

# v0.2.0-m5

###### 2026/08/22

* `Nota` Primera versión hito utilizable (cubre M1–M5); verificación de host con la misma firma, autorización por capacidad, procesos worker desechables y validación fail-closed activos desde el inicio
* `Novedad` Plugin independiente de compilación/ejecución de código Kotlin establecido: Kotlin/JVM 2.3.21 y D8 8.13.17 integrados, con compilación y ejecución en procesos separados
* `Novedad` Implementado el perfil de código de archivo único de Protocol 1.1: análisis de paquete y clase de entrada, diagnósticos saneados y mapeo completo de fases de fallo
* `Novedad` Implementadas cuatro capacidades del host autorizadas individualmente: lanzamiento de aplicaciones, flujo de consola, sleep y toast
* `Novedad` Implementada la caché de compilación autenticada con telemetría
