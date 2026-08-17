# Aura — LED de notification personnalisée pour POCO X8 Pro (HyperOS)

Application Android **open source** qui pilote les **anneaux LED RGB** à l'arrière du **POCO X8 Pro** (Xiaomi / **HyperOS 3**) pour afficher une **couleur** et une **animation** différentes selon l'**application**, le **contact** ou le **groupe** — **quand l'écran est éteint**. Elle remplace la LED de notification par défaut d'HyperOS, limitée à une couleur globale sans distinction d'expéditeur.

> LED de notification · LED RGB · anneau lumineux · POCO X8 Pro · Xiaomi · HyperOS · Shizuku · Android · custom notification LED · RGB notification ring — **sans root**.

- 100 % local : aucune permission réseau, aucun backend, aucune télémétrie.
- Aucun root : contrôle matériel via **Shizuku** (privilèges ADB).
- Priorité de résolution : **contact > groupe > application**.

> Voir [`PRD.md`](PRD.md) pour le cahier des charges complet et [`phase0/REPORT.md`](phase0/REPORT.md) pour le spike de faisabilité (découverte du service `miui.lights.ILightsManager`).

## Fonctionnalités

- **Couleur par application** : chaque app activée a une couleur de LED par défaut.
- **Couleur + animation par contact/groupe** : messages (WhatsApp…) et appels peuvent être distingués par expéditeur (respiration, clignotement, arc-en-ciel, alerte).
- **Écran éteint uniquement** : aucune LED quand l'écran est allumé (économie de batterie).
- **« La dernière gagne »** : une nouvelle notification remplace immédiatement l'état LED précédent.
- **Désactivation de la LED système** HyperOS pour éviter tout double allumage (pilote unique).
- **Durée d'allumage configurable** (1–30 s, défaut 10 s).
- Fonctionne même si le **contenu des notifications est masqué sur l'écran de verrouillage** (récupération du contenu via Shizuku — voir *Comment ça marche*).

## Prérequis

### Matériel / système
- **POCO X8 Pro** sous **HyperOS 3** (Android 16). C'est le seul appareil ciblé ; d'autres modèles ne sont pas supportés.
- **Shizuku** installé et activé (voir ci-dessous).

### Environnement de build
- JDK **17**
- **Android SDK** (compileSdk 36, minSdk 26)
- Gradle **8.13+** (le wrapper est fourni, rien à installer)

## Build

```bash
# Linux / macOS
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug
```

L'APK est généré dans :

```
app/build/outputs/apk/debug/app-debug.apk
```

### Installation sur l'appareil

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Mise en route

1. **Activer Shizuku** sur le téléphone (démarrage ADB via un PC, ou démarrage sans fil), puis installer l'APK.
2. Ouvrir **Aura** et accorder :
   - l'**autorisation de notifications** ;
   - l'**autorisation Shizuku** (bouton dans l'app).
3. Activer les applications souhaitées et choisir leur **couleur par défaut**.
4. Pour les messageries/appels, activer **« Identifier l'expéditeur »** puis ajouter des règles **contact** / **groupe** (couleur + animation).
5. Désactiver la **LED système HyperOS** depuis l'app (section « LED système ») pour éviter le double allumage.
6. Recommandé : **exclure Aura de l'optimisation batterie** (section « Robustesse ») pour que le service survive en arrière-plan.

## Comment ça marche

- **`notification/AuraNotificationListener`** : détecte les notifications (`NotificationListenerService`) et n'émet une commande LED que si l'écran est éteint.
- **`engine/RuleEngine`** : résout la règle selon la priorité contact → groupe → app.
- **`led/ShizukuLEDController`** : pilote les anneaux via le service système `miui.lights.ILightsManager` (`setCustomLight`), appelé à travers Shizuku (UID shell autorisé). La couleur est arbitraire (RGB) et la LED s'éteint automatiquement après le timeout.
- **`notification/FullNotificationReader`** : quand Android masque le contenu des notifications sur l'écran de verrouillage, le listener ne reçoit qu'une version expurgée (titre/texte vides). Aura récupère alors le contenu complet via `dumpsys notification --noredact` (exécuté par Shizuku) afin que les règles par contact/groupe — et leurs animations — continuent de fonctionner écran éteint.
- **`data/`** : persistance locale des règles et réglages avec **Room**.
- **`ui/`** : interface **Jetpack Compose**.

## Stack technique

- Kotlin 2.1, Jetpack Compose (Material 3)
- Room (persistance locale)
- Kotlin Coroutines / Flow
- [Shizuku](https://github.com/RikkaApps/Shizuku) (API 13.1.5)

## Licence

[MIT](LICENSE) — © 2026 Aura Contributors
