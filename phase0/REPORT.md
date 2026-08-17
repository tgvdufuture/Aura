# Phase 0 — Spike de faisabilité (Aura)

**Verdict : GO ✅** — Les anneaux LED (strip light) du POCO X8 Pro sont pilotables sans root via ADB/Shizuku.

Date : 2026-08-16
Appareil : POCO X8 Pro — `klee_eea`, model `2511FPC34G`, Android 16 (API 36), HyperOS `OS3.0.303.0.WPJEUXM`.

---

## 1. Résumé

Le contrôle des anneaux arrière passe par un service système Miui dédié :
**`miui.lights.ILightsManager`** (implémenté par `com.android.server.lights.HyperLightsService`).

- Le **shell** (uid 2000) — donc **Shizuku** — est explicitement autorisé (`checkCallerVerify`).
- La méthode `setCustomLight(...)` accepte une **couleur RGB arbitraire** + un mode d'animation + des durées, et **s'éteint automatiquement** après `onMs+offMs` (capé 30 s).
- Les LEDs sont des RGB adressables : **pas limitées aux 8 couleurs** du PRD (la liste de 8 est seulement une palette UI).

---

## 2. F0.1 — Commande qui allume la LED ✅

Interface AIDL `miui.lights.ILightsManager` (4 méthodes, codes de transaction) :

| Code | Méthode | Signature | Usage |
|---|---|---|---|
| 1 | `setColorfulLight` | `(String pkg, int styleType, int userId)` | Effet appel/alarme (couleur via Settings, pas d'argument couleur) |
| 2 | `setColorCommon` | `(int color, String pkg, int styleType, int userId)` | Musique uniquement (styleType 3) |
| 3 | `setColorLed` | `(int color, String pkg, int styleType, int userId, int category)` | **Corps vide** (réservé) |
| 4 | `setCustomLight` | `(int color, int flashMode, int onMs, int offMs, int brightNessMode, String pkg, int styleType, int userId)` | **La voie à utiliser** : couleur arbitraire + animation + auto-extinction |

### Commande ADB documentée (reproductible 2/2)

```
adb shell service call miui.lights.ILightsManager 4 \
  i32 <COLOR> i32 <MODE> i32 <ON_MS> i32 <OFF_MS> i32 0 \
  s16 "com.android.camera" i32 12 i32 0
```

- `<COLOR>` : int RGB 24 bits (ex. `16711680` = rouge `#ff0000`, `255` = bleu `#0000ff`).
- `<MODE>` : `0` = fixe, `1` = flash, `2` = respiration.
- `<ON_MS>/<OFF_MS>` : durées ; l'extinction auto a lieu après `onMs+offMs`.
- `s16 "com.android.camera"` et `i32 12` : requis (seul pkg accepté pour `setCustomLight`), c'est un filtre sur la *chaîne* passée, pas sur l'UID appelant.

> ⚠️ **Piège `service call`** : sur cet Android (16), `service call` **écrit automatiquement le jeton d'interface**. Ne PAS le passer en `s16` (sinon `BadParcelableException: Parcel data not fully consumed`).

Vérifié dans logcat :
```
setCustomLight callingPkg: com.android.camera color:16711680 mode:0 onMs:8000 offMs:0, styleType: 12
realSetLightLocked, # mId: 20, curPriority: 1 color: 32ff0000
```
(`32ff0000` = octet haut `0x32` = luminosité 50, `0xff0000` = rouge.)

---

## 3. F0.2 — Inventaire couleurs / effets / latence ✅

### Couleurs
- **RGB arbitraire confirmé** (rouge, bleu, vert tous acceptés et rendus). La limite « 8 couleurs » n'existe pas au niveau matériel.
- Palette UI (`light_color_list_new`, `/product/etc/device_features/klee.xml`) : 8 presets — `#a5976e #ff0000 #dc2100 #ff8f04 #0aff10 #26ff67 #006c9a #5500f2`.
- Palette de réglage UI (`light_color_setting_list`) : 8 paires clair/sombre.

### Effets (flashMode)
| flashMode | Effet | Fichier de style | Confirmé |
|---|---|---|---|
| 0 | Fixe (couleur continue) | — | ✅ rouge 5 s |
| 1 | Flash (on/off) | `lightstyle_camera.xml` (on 500 / off 500) | ✅ bleu 1,5 s/1,5 s |
| 2 | Respiration (rampe) | — | ✅ vert 2 s/2 s |

Les styles prédéfinis (`/product/etc/lights/lightstyle_*.xml`) : notification, phone, alarm, camera, battery, game, game_colorful. La plupart utilisent `flashMode 0` avec des `onMS/offMS` longs (≈ couleur continue).

### Latence
Commande → `realSetLightLocked` : **≈ 43–46 ms** (logcat). Bien sous le seuil NFR de 500 ms.

### Animation « chargement » (enchaînement de couleurs)
Possible en **émulation** : boucle logicielle de `setCustomLight` (le service fait déjà ça pour le game light via `playColorfulGameLightLocked`). Le mode flash (1) donne un clignotement natif. → **charging = flash natif ou séquence émulée**, breathing = flashMode 2 (natif). Les deux sont faisables.

---

## 4. F0.3 — Désactivation de la LED système ✅

| Clé | Namespace | Effet | Écriture shell/Shizuku |
|---|---|---|---|
| `notification_light_pulse` | system | `0` = désactive le pulse de notification (qui se mappe sur le strip) | ✅ **oui** (testé, réversible) |
| `settings_strip_light_enable` | global | `0` = désactive tout le strip (batterie/notif/appel/**notre app**) | ❌ **non** — `SecurityException: WRITE_SECURE_SETTINGS` requis |

**Clé de désactivation retenue : `notification_light_pulse = 0`** (system), réversible (`= 1`), accessible à Shizuku.

> ⚠️ Risque identifié : `settings put global` est **bloqué pour le shell** sur HyperOS. Si une clé *global* doit être écrite (ex. `settings_strip_light_enable`), il faudra soit « USB debugging (Security settings) » côté téléphone, soit une autre voie (à creuser en Phase 1). Notre MVP n'a *pas* besoin d'écrire de clé global : `setCustomLight` suffit.

### Note « pilote unique » (US-05)
Notre lumière passe par le strip **urgent** (id 20, priorité 1), qui **écrase** le strip notification système (id 18, priorité 2). Même si le système tente d'allumer sa LED de notif, la nôtre gagne. Couplé à `notification_light_pulse=0`, on évite tout double allumage.

---

## 5. Architecture du contrôle (pour Phase 1)

- Service : `HyperLightsService` (extends `LightsService`), publié sous `miui.lights.ILightsManager`.
- IDs strip : `18` notification, `19` musique, `20` urgent (tous mappés sur le HW de la light de notification type 4).
- Priorités : urgent(1) > notification(2) > batterie(3) > musique(4).
- Permission : `checkCallerVerify` accepte `uid==2000` (shell/Shizuku), `1000`, `1001`, `1013`, `0`. `setCustomLight` n'a **aucun check d'UID** (filtre sur la chaîne `pkg`).
- Couleur : `#RRGGBB` → int RGB ; octet haut = luminosité (`lamp_effect_brightness`, défaut ~50/168 barpos).
- Auto-extinction : `onMs+offMs` (capé 30 s).

### Interface LEDController (figée après Phase 0)

```kotlin
interface LEDController {
    // #RRGGBB -> Result<Boolean>
    fun setColor(colorHex: String): Result<Boolean>
    // animationId in {"charging","breathing"}, colorHex #RRGGBB
    fun startAnimation(animationId: String, colorHex: String): Result<Boolean>
    fun stop(): Result<Boolean>
}
```

Mapping vers `setCustomLight(color, flashMode, onMs, offMs, brightnessMode=0, pkg="com.android.camera", styleType=12, userId=0)` :
- `setColor(c)` : mode 0 (fixe), onMs=`ledTimeoutMs`, offMs=0.
- `startAnimation("breathing", c)` : mode 2.
- `startAnimation("charging", c)` : mode 1 (ou séquence émulée de couleurs).
- `stop()` : color 0.

Erreurs :
- `SHIZUKU_UNAVAILABLE` — Shizuku absent/permission révoquée.
- `LED_UNAVAILABLE` — strip désactivé (`settings_strip_light_enable=0`) ou service absent.
- `COLOR_UNSUPPORTED` — non applicable (RGB arbitraire) ; log debug.
- `ANIMATION_UNSUPPORTED` — retomber breathing → statique.

---

## 6. Validation steps (réalisés)

1. `adb devices` → `BEQWMFQWGMMV8HKZ device`.
2. Repérage des clés Settings (`settings_strip_light_enable`, `back_strap_app_notification_color`, `notification_light_pulse`).
3. Décompilation (jadx) de `miui-framework.jar` → AIDL `miui.lights.ILightsManager` + codes de transaction.
4. Décompilation de `miui-services.jar` → `HyperLightsService` (permissions, ids, mapping HW, auto-off).
5. Test `service call ... setCustomLight` : rouge fixe 2/2, bleu flash, vert respiration → confirmés dans logcat.
6. Test désactivation : `notification_light_pulse=0` (écriture + réversible OK) ; `settings_strip_light_enable` (bloqué shell).

## 7. Risques

1. **`settings put global` bloqué pour shell/Shizuku** (WRITE_SECURE_SETTINGS). Impacte toute écriture de clé *global*. Mitigation : le MVP n'en a pas besoin ; sinon activer « USB debugging (Security settings) ».
2. **Extraction expéditeur peu fiable** (Snap/Instagram) — à valider en Phase 1 (fallback app).
3. **Shizuku à réactiver après reboot** — guider l'utilisateur (onboarding).
4. **Service tué par HyperOS** — exclusion batterie + notification persistante (Phase 1).
5. Le **strip est partagé** avec batterie/appel/musique : notre priorité 1 écrase tout, mais il faut s'assurer de l'extinction (auto-off) pour ne pas masquer la batterie indéfiniment.

## 8. Suggested tests

- Test unitaire du mapper couleur `#RRGGBB` → int (et vice-versa).
- Test du RuleEngine : priorité contact > groupe > app ; « la dernière gagne ».
- Test d'intégration `LEDController` réel : setColor/startAnimation/stop sur l'appareil (2/2).
- Test du flux complet : notification écran éteint → bonne couleur (critère ≥95 % / 50 notifs).
- Test de la reconnexion Shizuku après reboot.

## 9. Files changed

- `phase0/REPORT.md` (ce rapport).
- `phase0/dex/jadxout/sources/miui/lights/ILightsManager.java` (AIDL décompilé, référence Phase 1).
- `phase0/dex/jadxout/sources/com/android/server/lights/HyperLightsService.java` (implémentation décompilée, référence).

(Aucun code d'app produit — Phase 0 = spike. Les jars/déx extraits du framework ont été retirés.)
