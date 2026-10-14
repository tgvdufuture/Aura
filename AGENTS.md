# Agent Instructions

## Skills disponibles

Les skills installés dans ce projet vivent dans `.agents/skills/<name>/SKILL.md`.
Chargez-les avec l'outil `skill` par leur nom : `karpathy-guidelines`, `prd-writer`, `test-designer`, `test-reviewer`, `impeccable`.

## Comportement par défaut (obligatoire)

**Chargez `karpathy-guidelines` au démarrage de chaque session de travail sur du code** — avant toute écriture, modification ou refactor de code. C'est le point d'entrée comportemental du projet : il définit comment travailler (hypothèses explicites, simplicité, changements chirurgicaux, critères de succès vérifiables).

Déclencheurs :
- Session start : charger `karpathy-guidelines` une fois, avant la première tâche de code.
- Si un PRD existe (`PRD*.md`, `docs/PRD*.md`) ou si l'utilisateur en mentionne un : suivre §5 du skill (suivi de progression) et créer/mettre à jour `PROGRESS.md`.
- Ne pas recharger le skill plusieurs fois dans la même session.

## Chaînage automatique des skills

`karpathy-guidelines` §6 définit le chaînage. En pratique, déclenchez :

| Situation | Skill à charger |
|---|---|
| Nouvelle feature / produit à définir avant de coder, PRD demandé | `prd-writer` (mode léger si petite feature) |
| Feature finie à encadrer par des tests (voir Definition of Done) | `test-designer` |
| Changement de code qui doit être vérifié (feature, fix, refactor) | `test-designer` |
| Suite de tests existante à auditer, refactorer, dé-flaker | `test-reviewer` |
| Travail frontend/UI (design, polish, audit, responsive) | `impeccable` |

Règles :
- Charger le skill via l'outil `skill` AVANT de commencer la tâche concernée, pas après — sauf `test-designer` au déclencheur « feature finie », à charger avant de déclarer la tâche terminée.
- Un changement de code n'est pas terminé tant qu'il n'est pas vérifié : pour toute tâche non triviale, passez par `test-designer` pour le plan de test, puis exécutez-le.
- Ne chargez pas un skill si son déclencheur ne correspond pas (pas de chaînage spéculatif).

## Definition of Done — feature finie = encadrée par des tests

Quand une feature est implémentée (code écrit, comportement en place), ne concluez PAS la tâche immédiatement. Bouclez la séquence :

1. Chargez `test-designer` via l'outil `skill`.
2. Concevez le plan de test à partir de la **demande originale et des critères d'acceptation** (+ fichiers modifiés comme intake). Ne lisez pas l'implémentation pour en déduire les tests : des tests conçus d'après le code ne vérifient que ce que le code fait déjà.
3. Implémentez les tests du plan (niveau unit/integration/E2E tel que défini dans le plan).
4. Exécutez la suite de tests détectée dans le repo et rapportez les résultats test par test.
   - **Aucun framework de test détecté ?** NE l'installez PAS en silence. Arrêtez-vous, proposez un framework adapté au stack du projet et attendez l'accord de l'utilisateur.
5. Tant qu'un test du plan échoue, la feature n'est pas finie — corrigez et rebouclez.
   - **Borne de boucle : 2 itérations max** sur le même test. Au-delà, arrêtez-vous et rapportez (sortie du test rouge + hypothèse sur la cause) au lieu de tourner en rond.

**Arbitrage avec `karpathy-guidelines` §2** : les tests ne sont PAS une « feature au-delà de la demande » — ils font partie de la définition de « fini ». Ne sautez jamais la DoD en invoquant la simplicité ou le périmètre minimal.

**Commit gate** : ne commitez jamais une feature tant que la DoD n'est pas passée (tests verts). Un commit avec des tests rouges ne se fait que sur demande explicite de l'utilisateur.

**Maintenance de la suite** : tous les 3-4 changements testés, ou dès qu'une suite devient lente ou flaky, proposez un passage `test-reviewer` sur la suite.

Exemptions (jugement, pas de plan formel) : typo, commentaire, renommage sans impact logique, changement purement cosmétique.
