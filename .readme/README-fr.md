<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-kotlin-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>Plugin de compilation et d'exécution de source Kotlin 2.3.21 mono-fichier pour AutoJs6</p>

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

### Langues (Languages)

******

Le README.md actuel prend en charge les langues suivantes:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-en.md)
- Français [fr] # actuel
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.readme/README-ar.md)

******

### Présentation

******

Le plugin AutoJs6 Kotlin Runtime permet à AutoJs6 de compiler et d'exécuter directement du code source Kotlin mono-fichier (`.kt`). Il embarque le compilateur Kotlin/JVM 2.3.21 (K2) et le convertisseur de bytecode D8 8.13.17, et exécute le résultat compilé dans un processus worker jetable ; ni le compilateur ni le script ne s'exécutent jamais dans le processus AutoJs6.

Ce plugin et [Java Runtime](https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime) sont des plugins jumeaux : ils peuvent être installés côte à côte, chacun servant respectivement le source Kotlin / Java, et AutoJs6 mémorise le composant de compilation choisi pour chaque langage.

******

### Fonctionnalités

******

- Fournit le service de compilation/exécution `org.autojs.plugin.JVM_SOURCE` et le service de découverte `org.autojs.plugin.INFO` pour le centre de plugins, tous deux protégés par signature et exécutés dans des processus auxiliaires séparés.
- Embarque le compilateur Kotlin/JVM 2.3.21 (K2) ; les scripts ciblent le bytecode JVM 1.8, converti en DEX par D8 8.13.17 avant exécution.
- Prend en charge quatre ponts de capacités hôte autorisés individuellement : sortie console en direct `console().log/error`, lancement d'application `app().launch`, `sleep` interruptible et messages `toast`.
- Prend en charge la bibliothèque standard Kotlin et la concurrence structurée `kotlinx-coroutines-core-jvm` 1.11.0 (`Dispatchers.Default` / `IO` / `Unconfined`).
- Cache de compilation authentifié : médiane d'environ 46 ms en cache chaud contre environ 577 ms en compilation à froid (environ 12,5x plus rapide sur appareil) ; médiane d'exécution d'environ 30 ms.
- Les erreurs de compilation conservent les diagnostics K2 d'origine avec ligne/colonne ; BOM, différences de chemins Windows et troncature chinois/emoji sont traités de manière déterministe.
- Chaque exécution se déroule dans un processus worker jetable neuf, retiré ensuite ; arrêter le script depuis l'hôte interrompt rapidement sleep et les coroutines.
- README et CHANGELOG sont disponibles en dix langues : chinois simplifié, chinois traditionnel (HK/TW), anglais, français, espagnol, japonais, coréen, russe et arabe.

******

### Démarrage rapide

******

- **Installer** — Téléchargez l'APK depuis [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/releases) et installez-le, ou compilez localement comme décrit dans la section Compilation ci-dessous. Attention : le plugin doit être signé avec le même certificat qu'AutoJs6 ; l'APK debug à certificat temporaire produit par GitHub Actions ne sert qu'à l'inspection de build et ne peut pas s'intégrer à un hôte réel. Le code de version de l'hôte AutoJs6 doit être d'au moins 5276.
- **Activer** — L'exécution de source JVM est actuellement une fonctionnalité expérimentale d'AutoJs6 : activez l'interrupteur expérimental dans l'hôte, puis sélectionnez explicitement ce plugin comme composant de compilation pour le langage Kotlin. Sinon, l'exécution signale respectivement les codes d'erreur stables `JVM_SOURCE_EXPERIMENT_DISABLED` ou `JVM_SOURCE_PROVIDER_NOT_SELECTED`.
- **Exécuter** — Créez un fichier `.kt` dans l'éditeur AutoJs6, écrivez une classe d'entrée implémentant l'interface `AutoJsJvmEntry`, puis lancez l'exécution (voir l'exemple ci-dessous). L'hôte actuel normalise le nom du source en `Main.kt` avec le nom simple d'entrée fixé à `Main` ; un package ASCII ordinaire et des imports sont optionnels.
- **Dépanner** — En cas d'échec de compilation, la console affiche les diagnostics K2 avec ligne/colonne ; des explications bilingues des quatre erreurs courantes (import manquant, incompatibilité de types, interface d'entrée manquante, package non conforme) se trouvent dans [samples/errors](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/tree/main/samples/errors). Les échecs d'exécution n'exposent que des codes d'erreur stables (comme `JVM_SOURCE_COMPILE_FAILED`, `JVM_SOURCE_TIMEOUT`) et ne divulguent jamais de chemins internes.

******

### Exemple d'utilisation

******

Un exemple minimal prêt à l'emploi démontrant les quatre capacités hôte actuelles:

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

`console().log/error` est diffusé ligne par ligne pendant l'exécution du script ; `sleep` est rapidement interrompu par une action d'arrêt. Plus d'exemples dans le répertoire [samples](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/tree/main/samples) : test de capacités `m5-capabilities.kt`, démonstration d'annulation `m6-cancellation.kt` et exemple de coroutines `coroutines.kt`.

******

### Limites

******

Pour garantir un comportement sûr et prévisible, la version actuelle maintient délibérément les limites suivantes:

- Source Kotlin mono-fichier uniquement, jusqu'à 4 MiB ; les projets multi-fichiers, fichiers class, JAR et entrées DEX ne sont pas encore pris en charge.
- La classe d'entrée doit implémenter `AutoJsJvmEntry` (Entry API 2) ; seuls les identifiants de package ASCII ordinaires sont acceptés — les packages échappés par backticks ou non ASCII sont explicitement rejetés.
- Aucune dépendance Maven ou tierce n'est résolue ; les bibliothèques disponibles sont exactement celles de la liste Bibliothèques ci-dessous.
- Le bytecode des scripts cible JVM 1.8 ; les fichiers class au-dessus de Java 8 sont rejetés avant D8.
- Le processus worker est retiré après chaque exécution : les coroutines et autres tâches d'arrière-plan ne survivent pas au retour de `run` ; n'utilisez pas `GlobalScope`.
- Le protocole publié est JVM Source Protocol 1.1 ; Protocol 1.2 n'est qu'une proposition — presse-papiers/documents/HTTPS/notifications ne sont pas encore disponibles.

******

### Bibliothèques de script

******

Les bibliothèques disponibles pour la compilation et l'exécution des scripts forment une liste blanche exactement verrouillée:

#### Disponible

- Framework Android : les symboles de compilation proviennent de stubs class-only API 24 ; le comportement à l'exécution dépend toujours de la version du système de l'appareil.
- AutoJs6 JVM Entry API 2 : `JvmScriptContext` est le seul pont hôte pris en charge.
- Bibliothèque standard Kotlin 2.3.21 (verrouillée sur la version du compilateur embarqué).
- `kotlinx-coroutines-core-jvm` 1.11.0 : `runBlocking`, `async` structuré, `delay` et `Dispatchers.Default` / `IO` / `Unconfined` sont pris en charge.

#### Indisponible

- `kotlinx-coroutines-android` et `Dispatchers.Main` : le processus worker n'a pas d'UI/Looper, le dispatch vers Main échoue.
- `kotlin-reflect` complet : seules les références de classes de base de la stdlib restent ; `kotlin.reflect.full.*` n'est pas pris en charge.
- kotlinx-serialization, modules debug/test de coroutines, plugins de compilateur et toute dépendance Maven transitive.

******

### Sécurité et isolation

******

Le plugin est conçu en refus par défaut ; les restrictions suivantes sont toujours en vigueur:

- Le compilateur et le worker s'exécutent dans des processus séparés et n'entrent jamais dans le processus AutoJs6 ; les services n'acceptent que les appels d'un hôte de même signature.
- Chaque capacité hôte (lancement d'application, toast, etc.) est autorisée par requête ; les capacités non autorisées sont rejetées avant dispatch.
- Source, artefacts et diagnostics ont tous des plafonds de taille ; les diagnostics ne sont tronqués qu'aux frontières de points de code Unicode — jamais un demi-emoji ni d'UTF-8 malformé.
- Les messages d'erreur externes ne contiennent que des codes stables et un texte assaini, jamais de chemins privés, d'empreintes ou d'identités de processus.
- Le cache de compilation est authentifié ; tout changement de chaîne d'outils ou de bibliothèque invalide automatiquement tous les caches précédents.

******

### Historique des versions

******

# v0.7.0

###### 2026/09/11

* `Amélioration` La vérification de compilation rejette les dépendances natives involontaires et produit un rapport JSON

# v0.7.0-m10

###### 2026/08/26

* `Note` Les capacités publiées restent en Protocol 1.1 / Entry API 2 ; aucune capacité 1.2 n'est ouverte avant son arrivée côté hôte
* `Nouveauté` Ajout de la proposition de capacités JVM Source Protocol 1.2, soumise à la revue de l'hôte : presse-papiers borné, documents autorisés par l'utilisateur, HTTPS proxifié par l'hôte et notifications gérées par l'hôte
* `Nouveauté` Ajout d'un pipeline d'appel hôte en quatre étapes (autorisation → validation du payload → dispatch → validation de la réponse) ; `app.launch` et `toast.show` migrés sans changement de comportement
* `Amélioration` Passage des AAR de protocole gelés au verrouillage de provenance schema-2, avec script de rafraîchissement staging-only et SOP complète
* `Amélioration` Ajout de sept tests de négociation 1.1/1.2 couvrant les paires hôte/plugin anciennes et nouvelles, la rétrogradation et les rejets stables

# v0.6.0-m9

###### 2026/08/26

* `Note` `Dispatchers.Main`, `kotlin-reflect` complet et kotlinx-serialization restent hors de la surface de script
* `Nouveauté` Ajout de `kotlinx-coroutines-core-jvm` 1.11.0, épinglé exactement, aux bibliothèques de script : concurrence structurée et annulation coopérative
* `Nouveauté` Ajout de l'exemple de coroutines prêt à l'emploi `samples/coroutines.kt`
* `Amélioration` Les empreintes d'exécution et les clés de cache de compilation intègrent désormais l'identité de la bibliothèque de coroutines : tous les caches antérieurs sont invalidés automatiquement

##### Pour plus d'historique, voir

* [CHANGELOG-fr.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/.changelog/CHANGELOG-fr.md)

******

### Compilation

******

Le dépôt inclut des AAR de protocole gelés (`protocol/`) et se compile hors ligne sans checkout d'AutoJs6. JDK 21 recommandé ; le SDK Android doit fournir les platforms 24 et 36. Build debug:

```powershell
.\gradlew.bat :app:assembleDebug --offline
```

Build release:

```powershell
.\gradlew.bat :app:assembleRelease --offline
```

Les paramètres de build sont centralisés dans `version.properties` : version actuelle 0.7.0-m10 (build 7), minSdk 26, targetSdk 36.

Les APK release/debug doivent être signés avec le même certificat qu'AutoJs6 pour être acceptés par l'hôte ; le matériel de signature local réside dans `sign.properties` et `app/sm003.jks`, ignorés par le contrôle de version. Voir [RELEASE_CHECKLIST](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/docs/RELEASE_CHECKLIST.md) pour les commandes de contrôle et le flux de publication.

******

### Organisation des ressources

******

```text
.readme/lang_*.json
.readme/template_readme.md
.changelog/lang_*.json
.changelog/template_changelog.md
.python/generate_markdown.py
app/src/main/res/values*/strings.xml
```

`strings.xml` localise le nom et la description du plugin ; README et CHANGELOG sont générés par `.python/generate_markdown.py` à partir des sources JSON. Pour modifier la documentation, éditez les sources JSON plutôt que le Markdown généré.

******

### Liens

******

- Documentation AutoJs6: https://docs.autojs6.com
- Page du projet AutoJs6: https://github.com/SuperMonster003/AutoJs6
- Plugin jumeau Java Runtime: https://github.com/SuperMonster003/AutoJs6-Plugin-Java-Runtime
- Projet officiel Kotlin: https://github.com/JetBrains/kotlin
- Répertoire d'exemples: https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/tree/main/samples
- Feuille de route (avec les registres de vérification par jalon): https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/ROADMAP.md
- Mentions tierces: https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/THIRD_PARTY_NOTICES.md


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Kotlin-Runtime/blob/main/docs/16kb.md)
