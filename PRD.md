PRD — Aura (Notification LED personnalisée, POCO X8 Pro)

Version: 1.0
Date: 2026-08-15
Status: Draft
0. Executive Summary

Aura est une application Android personnelle qui pilote les anneaux LED RGB à l'arrière du POCO X8 Pro pour afficher une couleur et une animation par app, par contact et par groupe, quand l'écran est éteint. Elle remplace la LED de notification par défaut d'HyperOS 3, qui ne permet qu'une couleur globale sans distinction d'expéditeur.

Primary success metric:

    ≥ 95 % des notifications de test produisent la bonne couleur LED (échantillon de 50 notifications sur WhatsApp, Snap, Instagram, appels, SMS).

1. Strategic Context
1.1 Problem Statement

    Current situation: le POCO X8 Pro possède des anneaux LED RGB (8 couleurs, quelques effets prédéfinis) contrôlés par HyperOS 3, mais sans réglage par app ni par expéditeur.
    User pain point: impossible de savoir qui notifie sans regarder l'écran ; les notifications se ressemblent toutes.
    Why this matters now: la valeur des LEDs est gaspillée faute de personnalisation, alors que le hardware le permet.

1.2 Goals

    G1: piloter les LEDs physiques sans root, via Shizuku/ADB, sur un POCO X8 Pro.
    G2: afficher une couleur par app et une couleur+animation par contact/groupe, écran éteint uniquement.
    G3: rester sobre en batterie (pas de réveil permanent, LED bornée dans le temps).

1.3 Non-Goals

This phase will NOT:

    Implement un fallback logiciel (Always-On Display, éclairage d'écran, flash caméra).
    Implement la synchronisation cloud, multi-appareil ou multi-utilisateur.
    Publier sur le Play Store ni respecter ses contraintes de conformité.
    Supporter d'autres modèles de téléphone que le POCO X8 Pro.
    Implémenter la synchronisation musique / effets de jeu (Game Turbo).
    Modifier d'autres réglages HyperOS que ceux nécessaires au contrôle LED.

2. Users & Context
2.1 Primary Persona

    Role: propriétaire du téléphone (utilisateur unique).
    Technical level: à l'aise avec ADB et Shizuku ; refuse le root.
    Context: usage quotidien, notifications WhatsApp, Snapchat, Instagram, appels et SMS.
    Tools/environment: POCO X8 Pro, HyperOS 3, PC pour les commandes ADB ponctuelles.
    Main job-to-be-done: reconnaître l'émetteur d'une notification sans allumer l'écran.

2.2 User Stories
ID	User Story	Priority
US-01	As a propriétaire, I want que l'app détecte les notifications des apps choisies quand l'écran est éteint so that la LED ne s'allume que dans ce cas.	P0
US-02	As a propriétaire, I want définir une couleur par défaut par app so that chaque app ait une signature visuelle distincte.	P0
US-03	As a propriétaire, I want définir une couleur et une animation par contact (apps de messagerie/appels) so that je reconnaisse l'expéditeur sans lire.	P0
US-04	As a propriétaire, I want définir une couleur par groupe WhatsApp so that les groupes soient distinguables.	P0
US-05	As a propriétaire, I want désactiver la LED de notification par défaut d'HyperOS so that l'app soit le seul pilote (aucun conflit).	P0
US-06	As a propriétaire, I want que la LED ignore les notifications écran allumé so that la batterie soit préservée.	P0
US-07	As a propriétaire, I want que la notification la plus récente écrase l'état LED précédent so that « la dernière gagne ».	P0
US-08	As a propriétaire, I want choisir parmi une palette complète de couleurs so that ne pas être limité aux 8 couleurs du hardware (mappage auto).	P1
US-09	As a propriétaire, I want choisir parmi plusieurs animations (chargement, respiration…) so that les contacts importants ressortent.	P1
2.3 User Story Acceptance Criteria
US-01 — Déclenchement écran éteint

    AC1: une notification d'une app choisie, écran éteint, émet une commande LED.
    AC2: écran allumé, aucune commande LED n'est émise.

US-02 — Couleur par app

    AC1: l'app X a la couleur C ; une notification de X (écran éteint, sans règle contact) allume la LED en C.
    AC2: une app non choisie ne déclenche aucune LED.

US-03 — Couleur + animation par contact

    AC1: le contact C a couleur Cc + animation A ; un message de C allume la LED en Cc avec A.
    AC2: un expéditeur inconnu dans une app choisie retombe sur la couleur par défaut de l'app.

US-04 — Couleur par groupe

    AC1: le groupe G a la couleur Cg ; un message dans G allume la LED en Cg.
    AC2: un DM individuel et un message de groupe sont résolus sur la bonne règle.

US-05 — LED système désactivée

    AC1: après l'onboarding, la LED par défaut d'HyperOS est désactivée.
    AC2: une notification ne produit qu'un seul allumage LED (pas de double déclenchement).

US-06 — Écran allumé = rien

    AC1: écran allumé + notification → aucune LED.
    AC2: la LED ne s'allume qu'à une notification reçue écran éteint.

US-07 — La dernière gagne

    AC1: deux notifications à < 1 s d'intervalle → l'état LED final correspond à la dernière.
    AC2: les commandes LED ne s'accumulent pas en file (pas d'effet cascade).

3. Technical Specification
3.1 Architecture Overview

    Stack: Android natif, Kotlin (hypothèse à confirmer — c'est le plus adapté au contrôle matériel via Shizuku).
    Frameworks: Jetpack Compose pour l'UI (hypothèse).
    Database: Room ou DataStore en local uniquement (hypothèse).
    APIs: NotificationListenerService (lecture des notifs), API Shizuku (privilèges ADB sans root).
    External services: aucun — pas de réseau, pas de backend.
    Deployment target: APK installé en sideload, POCO X8 Pro, HyperOS 3.

3.2 DO NOT CHANGE

The coding agent must not change:

    La règle de priorité métier : contact > groupe > app.
    L'interface LEDController (figée à la fin du Phase 0).
    Le stockage 100 % local — interdiction d'ajouter une permission réseau ou un backend.
    Le périmètre matériel : uniquement les anneaux LED arrière du POCO X8 Pro.
    Les réglages HyperOS : uniquement les clés LED identifiées en Phase 0 (contrôle + désactivation), rien d'autre.

3.3 Data Models
Entity	Field	Type	Notes
AppRule	packageName	String (PK)	ex. com.whatsapp
AppRule	displayName	String	libellé UI
AppRule	enabled	Boolean	l'app déclenche-t-elle la LED
AppRule	defaultColorHex	String	#RRGGBB
AppRule	senderParsingEnabled	Boolean	vrai pour messagerie/appels
SenderRule	id	Long (PK)	auto
SenderRule	kind	Enum	CONTACT ou GROUP
SenderRule	appPackage	String	app de la règle
SenderRule	matchKey	String	nom normalisé du contact/groupe
SenderRule	colorHex	String	#RRGGBB
SenderRule	animationId	String?	nullable = couleur statique
Animation	id	String (PK)	ex. breathing, charging
Animation	name	String	libellé UI
Animation	params	JSON	période, motif, durée
Animation	availability	Enum	HARDWARE ou EMULATED
Settings	key	String (PK)	systemLedDisabled, ledTimeoutMs, screenOffOnly
Settings	value	String	valeur sérialisée
3.4 API / Interface Contracts
LEDController (interface interne, figée après Phase 0)

    Method: setColor(colorHex: String): Result<Boolean>
    Method: startAnimation(animationId: String, colorHex: String): Result<Boolean>
    Method: stop(): Result<Boolean>
    Input schema: couleurs #RRGGBB, animationId issu de la table Animation.
    Output schema: Result Kotlin (succès/échec).
    Error cases: SHIZUKU_UNAVAILABLE, LED_UNAVAILABLE, COLOR_UNSUPPORTED (→ mapper sur la couleur supportée la plus proche), ANIMATION_UNSUPPORTED (→ retomber sur breathing ou couleur statique).

Flux de résolution (RuleEngine)

    NotificationListenerService.onNotificationPosted(sbn).
    Si écran allumé → ignorer.
    Si l'app n'est pas enabled → ignorer.
    Extraire l'expéditeur uniquement si senderParsingEnabled.
    Résoudre : contact → groupe → app.
    Émettre LEDController.setColor ou startAnimation.
    Appliquer le timeout ledTimeoutMs puis stop().

3.5 Android / Mobile Requirements

    Permissions: accès aux notifications (NotificationListenerService), Shizuku.
    Autorisation ADB requise à l'installation (une fois) pour démarrer Shizuku.
    Exclusion de l'optimisation batterie HyperOS (demandée à l'utilisateur).
    Pas de permission réseau, pas d'analytics, pas de télémétrie.

4. Features & Requirements
Phase 0 — Spike de faisabilité

Goal: prouver qu'on peut piloter les LEDs physiques sans root via Shizuku/ADB.
Dependency: None
F0.1 — Déclencher une couleur LED

Description:

    Observer logcat + dumpsys quand la LED s'allume, identifier le service, l'intent ou les clés Settings HyperOS responsables.

Acceptance Criteria:

    AC1: une commande ADB/Shizuku documentée produit un allumage LED visible.
    AC2: la commande est reproductible (2/2 essais).

F0.2 — Inventaire des capacités LED

Description:

    Déterminer : 8 couleurs fixes ou RGB arbitraire ; liste des effets prédéfinis ; latence d'une commande ; possibilité d'enchaîner des couleurs (pour l'animation « chargement »).

Acceptance Criteria:

    AC1: une table « couleur supportée + effet + latence mesurée » est produite.
    AC2: la faisabilité de breathing et charging est tranchée (HARDWARE / EMULATED / impossible).

F0.3 — Désactivation de la LED système

Description:

    Identifier la clé/commande qui désactive la LED de notification par défaut d'HyperOS.

Acceptance Criteria:

    AC1: après la commande, la LED ne s'allume plus via le système.
    AC2: l'app peut la réactiver si besoin (réversible).

Phase 0 Completion Checklist

    LED pilotable sans root
    Capacités couleurs/effets documentées
    LED système désactivable
    Décision GO / NO-GO pour le MVP

Phase 1 — MVP

Goal: reconnaître les notifications et piloter la LED par app, contact et groupe, écran éteint.
Dependency: Phase 0 complete
F1.1 — Détection de notifications + gating écran éteint

Related user stories:

    US-01, US-06

Description:

    NotificationListenerService actif en arrière-plan ; n'émet une LED que si l'écran est éteint.

Acceptance Criteria:

    AC1: notification d'une app choisie, écran éteint → commande LED émise.
    AC2: écran allumé → aucune commande.
    AC3: le service reste actif en arrière-plan (survit 24 h sans kill).

Edge Cases:

    If permission notifications révoquée, then afficher un écran de ré-activation et ne rien émettre.
    If l'état d'écran est inconnu (capteur indisponible), then considérer « éteint » par sécurité (comportement par défaut documenté).

F1.2 — Couleur par défaut par app

Related user stories:

    US-02

Description:

    UI pour activer des apps et leur attribuer une couleur statique.

Acceptance Criteria:

    AC1: configurer l'app X en couleur C → LED en C à la notification.
    AC2: désactiver X → plus aucune LED pour X.

Edge Cases:

    If la couleur demandée n'est pas supportée par le hardware, then mapper sur la couleur supportée la plus proche (log en debug).

F1.3 — Règles par contact (couleur + animation)

Related user stories:

    US-03

Description:

    Extraction de l'expéditeur sur les apps de messagerie/appels ; règle contact avec couleur + animation.

Acceptance Criteria:

    AC1: contact connu → LED couleur contact + animation.
    AC2: expéditeur inconnu → couleur par défaut de l'app.
    AC3: si l'animation n'est pas supportée par le hardware, retomber sur breathing puis sur couleur statique.

Edge Cases:

    If l'expéditeur n'est pas identifiable dans la notif (titre vide), then retomber sur la règle app.
    If deux contacts partagent le même matchKey, then appliquer la règle la plus récente (last-wins) et journaliser.

F1.4 — Règles par groupe (WhatsApp)

Related user stories:

    US-04

Description:

    Détecter les notifications de groupe WhatsApp et appliquer une couleur de groupe.

Acceptance Criteria:

    AC1: message d'un groupe configuré → couleur du groupe.
    AC2: DM individuel → règle contact (ou app), jamais la règle groupe.

Edge Cases:

    If le nom du groupe change, then la règle devient obsolète → signaler et ignorer (pas de crash).

F1.5 — Désactivation LED système (pilote unique)

Related user stories:

    US-05

Description:

    Appliquer la commande identifiée en F0.3 à l'onboarding ; l'app devient l'unique source LED.

Acceptance Criteria:

    AC1: après onboarding, la LED système est désactivée.
    AC2: une notification produit un seul allumage (pas de chevauchée).

Edge Cases:

    If la désactivation échoue, then bloquer le pilotage et afficher l'erreur (pas de double LED).

F1.6 — Résolution « la dernière gagne »

Related user stories:

    US-07

Description:

    Toute nouvelle commande LED remplace l'état courant (pas de file).

Acceptance Criteria:

    AC1: 2 notifications rapprochées → l'état final est celui de la dernière.
    AC2: pas d'accumulation de commandes en attente.

Edge Cases:

    If une animation est en cours, then l'interrompre et appliquer la nouvelle commande.

F1.7 — Éditeur de règles + persistance locale

Related user stories:

    US-02, US-03, US-04

Description:

    Écran de configuration : apps, contacts, groupes, couleurs, animations ; stockage Room/DataStore local.

Acceptance Criteria:

    AC1: une règle créée dans l'UI est appliquée à la notification suivante.
    AC2: les règles survivent au redémarrage de l'app et du téléphone.

Edge Cases:

    If la base est corrompue, then repartir sur un état vide sans crash (migration Room propre).

F1.8 — Connexion Shizuku + onboarding

Related user stories:

    US-05

Description:

    Détecter Shizuku, guider l'activation ADB, gérer la reconnexion.

Acceptance Criteria:

    AC1: Shizuku indisponible → l'app affiche un guide et n'émet rien.
    AC2: Shizuku redémarre → reconnexion automatique sans action utilisateur.

Edge Cases:

    If la permission Shizuku est révoquée, then retour à l'écran de guide.

Phase 1 Completion Checklist

    Toutes les user stories P0 implémentées
    Flux complet : notification → résolution → LED, écran éteint
    LED système désactivée, pilote unique
    Aucune régression du fonctionnement normal des notifications

Phase 2 — V1 (polish)

Goal: enrichir couleurs, animations et robustesse batterie.
Dependency: Phase 1 complete
F2.1 — Mapping palette complète → hardware

Related user stories:

    US-08

Description:

    Sélecteur de couleur RGB complet, mappé sur la couleur hardware la plus proche (ou RGB direct si le spike le permet).

Acceptance Criteria:

    AC1: choisir n'importe quelle couleur → la LED affiche la plus proche supportée.
    AC2: l'écart de couleur est visible dans l'UI avant validation.

F2.2 — Bibliothèque d'animations étendue

Related user stories:

    US-09

Description:

    Ajouter des animations au-delà de breathing/charging, selon les capacités mesurées en Phase 0.

Acceptance Criteria:

    AC1: au moins 2 animations supplémentaires disponibles si le hardware le permet.
    AC2: chaque animation a un timeout et s'interrompt proprement.

F2.3 — Robustesse batterie & redémarrage

Description:

    Timeout LED court, pas de wakelock permanent, statut après reboot.

Acceptance Criteria:

    AC1: la LED s'éteint automatiquement après ledTimeoutMs (défaut 10 s).
    AC2: après reboot, l'app indique si Shizuku doit être réactivé.

5. Non-Functional Requirements
Category	Requirement	Target
Performance	Latence notification → commande LED	< 500 ms
Performance	Résolution de règle	< 50 ms
Battery	Durée max d'un allumage LED	≤ 10 s (configurable)
Battery	CPU du service en veille	< 1 % en moyenne
Reliability	Reconnexion Shizuku	automatique
Reliability	Survie en arrière-plan	≥ 24 h sans kill (hors reboot)
Security	Privilèges	pas de root, uniquement Shizuku
Privacy	Réseau	aucune permission réseau, 0 donnée sortante
Error handling	Couleur non supportée	mapper sur la plus proche
Error handling	Shizuku absent	guide utilisateur, aucun crash
Accessibility	UI config	libellés lisibles, contraste correct
6. Success Metrics
Launch Criteria

Must all be true before shipping:

    Précision couleur: ≥ 95 % sur 50 notifications de test (3 apps minimum)
    Latence: LED allumée < 1 s après la notification
    Pilote unique: aucun double allumage observé
    Stabilité: 0 crash sur 24 h en arrière-plan

Ongoing KPIs

    Primary: % de notifications avec la bonne couleur.
    Secondary: temps de latence moyen.
    Guardrail metrics: delta batterie ≤ 5 %/jour attribuable à l'app.

7. Risks & Mitigations
Risk	Likelihood	Impact	Mitigation
LEDs non pilotables sans root	Medium	High	Phase 0 GO/NO-GO ; sinon réévaluer (root bloqué ou abandon)
Limitation à 8 couleurs fixes	Medium	Medium	Mapping palette → couleur la plus proche
Animations limitées aux effets prédéfinis	Medium	Medium	Dégradé charging → breathing → statique
Shizuku à réactiver après reboot	High	Low	Guide + statut clair dans l'app
Service tué par HyperOS	Medium	Medium	Exclusion optimisation batterie + notification persistante
Extraction expéditeur peu fiable (Snap)	Medium	Medium	Fallback app-level + tests par app
8. Open Questions

    8 couleurs ou RGB complet ? à trancher en Phase 0 (impact US-08).
    Animation « chargement » possible ? dépend de la latence d'enchaînement des couleurs (Phase 0).
    Clé exacte de désactivation de la LED système ? à identifier en F0.3.
    Fiabilité de l'extraction expéditeur sur Snap/Instagram ? à valider par test réel.

9. Appendix

    Design references: LED rings POCO X8 Pro (8 couleurs, effets pulsation/musique/jeu).
    Related docs: API Shizuku, NotificationListenerService (Android).
    Glossary: Shizuku = privilèges ADB sans root ; HyperOS 3 = ROM Xiaomi/POCO ; LED rings = anneaux lumineux arrière.

Coding Agent Handoff Prompt

Read the PRD below carefully before writing any code.

Start with Phase 0 only.
Do not implement anything from later phases.
Do not modify anything listed in the DO NOT CHANGE section.
If any requirement is ambiguous, ask before implementing.

After Phase 0 is complete, provide:
- files changed
- implementation summary
- validation steps
- risks
- suggested tests

[PASTE PRD HERE]

