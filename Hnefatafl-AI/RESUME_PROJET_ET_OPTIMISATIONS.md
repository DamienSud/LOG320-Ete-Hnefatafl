# Résumé du projet Hnefatafl — stratégie et optimisations

Ce document résume l’état actuel de l’IA, les changements depuis la version décrite dans `EXPLICATION_DETAILLEE.md`, et explique en détail le mécanisme **make/unmake** qui a permis de gagner une profondeur de recherche.

---

## 1. De quoi parle le projet ?

L’IA joue au **Hnefatafl** via un client réseau Java :

```text
Serveur (partie réelle)
    ↓ envoie la position (350 caractères)
Client.java
    ↓ construit un Board, appelle CPUPlayer
CPUPlayer
    ↓ minimax + alpha-bêta, ~4,5 s de réflexion
    ↓ renvoie le meilleur coup
Serveur
```

**Budget temps :** le serveur accorde 5 s par coup. Le client garde 500 ms pour le réseau et la JVM → **4,5 s de recherche**.

**Objectif des deux camps :**
- **Rouge (attaquant)** : capturer le roi.
- **Noir (défenseur)** : faire atteindre un coin au roi.

---

## 2. Comment l’IA choisit un coup (vue d’ensemble)

| Couche | Rôle |
|--------|------|
| **Approfondissement itératif** | Teste profondeur 1, puis 2, 3… jusqu’à expiration du temps. Garde le dernier résultat **entièrement terminé**. |
| **Minimax + alpha-bêta** | Simule nos coups (MAX) et ceux de l’adversaire (MIN). Alpha-bêta coupe les branches inutiles sans changer le résultat exact. |
| **HeuristicEvaluator** | Quand la profondeur max est atteinte (ou fin de partie), estime si la position est bonne ou mauvaise pour notre camp. |
| **Anti-boucle** | Pénalise les répétitions (−200 000) et les allers-retours immédiats (−25 000) à la racine. |

L’heuristique est **asymétrique** : elle calcule d’abord un score « défenseur » (mobilité du roi, encerclement, gardes, matériel…), puis l’oriente selon que l’on joue rouge ou noir.

---

## 3. Ce qui a changé depuis `EXPLICATION_DETAILLEE.md`

Le markdown `EXPLICATION_DETAILLEE.md` décrit surtout la **stratégie de jeu**. Il listait aussi des limites techniques, dont :

- pas de table de transposition ;
- recalcul de positions identiques ;
- lenteur due aux copies de plateau dans l’arbre.

Le commit **« Best chance »** et le plan d’optimisation ont corrigé ces points.

### 3.1 Changements principaux

| Fichier | Avant | Après |
|---------|-------|-------|
| `Board.java` | `copy()` + `play()` à chaque nœud | **make/unmake**, hash Zobrist, listes de pièces, comptes incrémentaux |
| `CPUPlayer.java` | Copies dans minimax | **make/unmake**, PVS, aspiration, killers, historique |
| `TranspositionTable.java` | N’existait pas | Table compacte (recherche + cache d’évaluation) |
| `HeuristicEvaluator.java` | Scans complets du plateau | Comptes via `Board`, fusion des voisins du roi |
| `tests/` | Pas de tests auto | `ValidationHarness`, `BenchmarkHarness` |

### 3.2 Résultat mesuré (ouverture, 4,5 s)

| Camp | Avant | Après |
|------|-------|-------|
| **Rouge** | profondeur 4 | **profondeur 5** |
| **Noir** | profondeur 5 | profondeur 4 (variable selon le temps restant) |
| **Nœuds/s (rouge)** | ~1,3 M | ~1,4 M (mais ~6 M nœuds explorés au total car on va plus profond) |

**Conclusion :** oui, on gagne **+1 ply pour le rouge**. Les optimisations « légères » seules (cache eval, roi mémorisé) ne suffisaient pas ; c’est surtout le **make/unmake** qui a fait la différence.

---

## 4. Make/unmake — explication détaillée

C’est la pièce centrale à comprendre. Le reste (TT, PVS…) accélère encore, mais make/unmake est le changement structurel majeur.

### 4.1 Le problème : simuler des coups dans l’arbre

Minimax ne joue pas vraiment sur le plateau du serveur. Il **imagine** des coups :

```text
Position actuelle
├── Coup A → position A → réponses adverses → scores
├── Coup B → position B → réponses adverses → scores
└── Coup C → ...
```

Pour chaque branche, il faut **appliquer** un coup, **explorer** les suites, puis **revenir** à la position de départ pour essayer le coup suivant.

### 4.2 L’ancienne méthode : copier tout le plateau

**Avant**, à chaque simulation :

```java
Board boardCopy = board.copy();  // recopie les 13×13 = 169 cases
boardCopy.play(move);            // joue le coup sur la copie
minimaxAB(boardCopy, ...);       // explore à partir de la copie
// la copie est jetée → la position originale est intacte
```

**Analogie :** pour essayer une variante dans une partie d’échecs, tu **photocopies** toute la feuille de scores à chaque coup testé, tu écris dessus, tu lis le résultat, puis tu jettes la photocopie.

**Coût :** à chaque nœud de l’arbre, on duplique 169 cases + hash + comptes. En profondeur 5, on visite des **millions** de nœuds → des **millions** de copies.

### 4.3 La nouvelle méthode : modifier puis annuler (make/unmake)

**Maintenant**, on garde **un seul** plateau et on le modifie temporairement :

```java
board.makeMove(move, undo);      // applique le coup, enregistre ce qu’on a changé
try {
    minimaxAB(board, ...);       // explore avec le plateau modifié
} finally {
    board.unmakeMove(undo);      // annule le coup → plateau identique à avant
}
```

**Analogie :** au lieu de photocopier, tu **écris le coup au crayon** sur la feuille originale, tu notes dans la marge « case (3,5) avait un rouge, case (3,7) était vide », tu lis les suites, puis tu **effaces** en remettant exactement ce qu’il y avait.

**Coût :** on ne touche qu’à **2 à 5 cases** par coup (départ, arrivée, éventuelles captures), pas 169.

### 4.4 Qu’est-ce qu’un `Undo` ?

`Undo` est un **petit carnet** qui note tout ce qu’on a changé pour pouvoir restaurer l’état exact :

```java
public static final class Undo {
    int startRow, startCol;      // d’où part la pièce
    int endRow, endCol;          // où elle arrive
    Mark movedPiece;             // quelle pièce a bougé
    Mark startReplacement;       // ce qu’on met à la case de départ (EMPTY ou SPECIAL)
    Mark endReplacement;         // ce qu’il y avait à l’arrivée avant le coup
    int captureCount;            // nombre de pièces capturées (0 à 4)
    int[] capRow, capCol;        // coordonnées des captures
    Mark[] capMark;              // type de pièce capturée à chaque case
}
```

**Exemple concret :** un rouge glisse de (3, 5) vers (3, 7) et capture une pièce noire en (3, 6).

1. **makeMove** enregistre dans `Undo` :
   - départ (3,5) avait un ROUGE → remplacé par EMPTY
   - arrivée (3,7) était EMPTY → remplacée par ROUGE
   - capture : (3,6) avait BLACK → remplacée par EMPTY

2. **unmakeMove** fait l’inverse, dans l’ordre :
   - remet BLACK en (3,6)
   - remet EMPTY en (3,7)
   - remet ROUGE en (3,5)

Après `unmakeMove`, le plateau, le hash Zobrist, la position du roi et les comptes de pièces sont **identiques** à avant le coup.

### 4.5 Pourquoi `undoStack[ply]` ?

En récursion, minimax appelle minimax qui appelle minimax…

```text
Profondeur 0 (racine) : makeMove coup A
    Profondeur 1 : makeMove réponse adverse
        Profondeur 2 : makeMove ...
```

Si on réutilise **un seul** objet `Undo` pour tous les niveaux, le niveau profond **écrase** les notes du niveau au-dessus → `unmakeMove` casse l’état.

**Solution :** une pile d’`Undo`, une entrée par niveau de profondeur :

```java
board.makeMove(move, undoStack[ply]);
// ...
board.unmakeMove(undoStack[ply]);
```

C’est comme empiler des carnets : chaque niveau a son propre carnet de modifications.

### 4.6 Schéma comparatif

```text
ANCIEN (copy)                         NOUVEAU (make/unmake)
─────────────────                     ─────────────────────

Plateau original                      Plateau unique
     │                                     │
     ├─ copy → Plateau A                  ├─ makeMove → modifie 3 cases
     │     play, explore                   │     explore récursivement
     │     (jeté)                          │     unmakeMove → restaure
     │                                     │
     ├─ copy → Plateau B                  ├─ makeMove (autre coup)
     │     play, explore                   │     ...
     │     (jeté)                          │
     │                                     │
Coût : O(169) par nœud                 Coût : O(1 à 5) par nœud
```

### 4.7 Lien avec le gain de profondeur

L’approfondissement itératif consomme un **budget temps fixe** (4,5 s).

- **Avant :** chaque nœud coûtait cher (copy) → moins de nœuds explorés → profondeur 4 terminée, profondeur 5 impossible.
- **Après :** chaque nœud coûte peu (make/unmake) → ~5× plus de nœuds en 4,5 s → **profondeur 5 terminée** pour le rouge.

Ce n’est pas que l’IA « voit plus loin magiquement » : elle **explore plus de l’arbre dans le même temps**.

---

## 5. Les autres optimisations (en bref)

### 5.1 Table de transposition (`TranspositionTable.java`)

Mémorise le résultat d’une recherche déjà faite pour une position (hash Zobrist + trait).

- Si on retombe sur la même position avec une profondeur suffisante → on réutilise le score sans tout recalculer.
- Cache séparé pour l’**évaluation statique** (feuilles).

### 5.2 PVS (Principal Variation Search)

Variante exacte d’alpha-bêta :
- premier coup fils : fenêtre complète `[alpha, beta]` ;
- coups suivants : fenêtre nulle `[alpha, alpha+1]` ;
- si le score sort de la fenêtre → nouvelle recherche complète.

Plus de coupures → moins de nœuds pour la même profondeur.

### 5.3 Fenêtres d’aspiration (à la racine)

On suppose que le score de la profondeur précédente est proche du score actuel. On cherche d’abord dans une fenêtre étroite autour de ce score. Si le vrai score est hors fenêtre, on élargit et on recommence.

### 5.4 Ordre des coups

1. Coup de la table de transposition / profondeur précédente  
2. Captures probables  
3. Killers (coups qui ont causé une coupure à cette profondeur)  
4. Historique (coups souvent bons par le passé)

Un bon ordre → alpha-bêta coupe plus tôt → plus rapide.

### 5.5 État incrémental dans `Board`

En plus du make/unmake sur les cases :

- **Position du roi** mémorisée (`kingRow`, `kingCol`) → plus de scan pour `isKingEscaped`.
- **Comptes** `blackCount` / `redCount` → plus de scan dans l’heuristique.
- **Listes de positions** des pièces → génération de coups sans parcourir les 169 cases.

---

## 6. Architecture actuelle

```text
Client.java
  ├─ reçoit position serveur → new Board(string)
  ├─ seenPositions (anti-répétition)
  └─ cpu.getBestMoveWithinMillis(board, 4500, seenPositions)

CPUPlayer.java
  ├─ Approfondissement itératif (depth 1…N)
  ├─ searchRoot : aspiration + ordre des coups
  ├─ minimaxAB : PVS + alpha-bêta + TT
  │     ├─ makeMove / unmakeMove (undoStack[ply])
  │     └─ evaluateCached → HeuristicEvaluator
  └─ TranspositionTable

Board.java
  ├─ makeMove / unmakeMove / Undo
  ├─ Zobrist hash incrémental
  ├─ listes de pièces + comptes
  └─ generateMoves (recherche) / play (réseau)
```

---

## 7. Tests et validation

```powershell
cd Hnefatafl-AI
javac -encoding UTF-8 -d out src\*.java
javac -encoding UTF-8 -cp out -d out tests\ValidationHarness.java
java -cp out ValidationHarness

javac -encoding UTF-8 -cp out -d out tests\BenchmarkHarness.java
java -cp out BenchmarkHarness
```

**ValidationHarness** vérifie :
- suivi du roi et des comptes ;
- make/unmake restaure plateau + hash + matériel ;
- scores heuristiques stables ;
- scores minimax à profondeur fixe ;
- profondeur minimale à 4,5 s.

**BenchmarkHarness** affiche profondeur, nœuds, nœuds/s et taux de hit TT.

---

## 8. Ce qui reste limité

| Limite | Commentaire |
|--------|-------------|
| Pas de quiescence | La recherche peut s’arrêter au milieu d’une séquence de captures. |
| Poids heuristiques manuels | Non appris automatiquement. |
| Aspiration parfois coûteuse | Le noir peut ne pas finir le ply 5 dans le budget. |
| Message serveur `'4'` | Coup invalide → exception (non géré proprement). |
| Connexion réseau | `Connection timed out` = serveur injoignable, pas un bug du moteur. |

---

## 9. Phrase de synthèse pour l’oral

> Notre IA combine minimax alpha-bêta avec une heuristique asymétrique adaptée au Hnefatafl. Pour respecter la limite de 4,5 secondes, nous avons remplacé les copies de plateau par un mécanisme make/unmake qui modifie et restaure l’état en O(1) au lieu de O(n²). Couplé à une table de transposition et au PVS, cela nous a permis de passer de la profondeur 4 à 5 pour le rouge à l’ouverture, donc de voir un demi-coup de plus les menaces et réponses adverses.

---

## 10. Fichiers utiles

| Fichier | Contenu |
|---------|---------|
| `EXPLICATION_DETAILLEE.md` | Stratégie heuristique, raycasting, forteresse, oral |
| `RESUME_PROJET_ET_OPTIMISATIONS.md` | Ce document — optimisations et make/unmake |
| `src/Board.java` | makeMove, unmakeMove, Undo |
| `src/CPUPlayer.java` | minimax, PVS, undoStack |
| `tests/ValidationHarness.java` | Tests de non-régression |
