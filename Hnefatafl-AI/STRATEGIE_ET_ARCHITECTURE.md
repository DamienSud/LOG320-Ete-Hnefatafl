# Hnefatafl — État du projet, architecture et stratégie heuristique

Document de référence pour comprendre **ce que fait le code aujourd’hui**, comment cela se compare à **`hnefatafl.pdf`** (LOG320, lab. #2), et **pourquoi les noirs fonctionnent mieux que les rouges**.

---

## 1. Objectifs du laboratoire (PDF)

| Exigence (PDF) | Fichier / mécanisme |
|----------------|---------------------|
| Plateau **13×13**, rouges jouent en premier | `Board.SIZE = 13`, protocole serveur `1` = rouge |
| Déplacement **tour** (lignes/colonnes), pas de saut | `Board.addMovesForPiece` |
| Seul le **roi** peut s’arrêter sur trône / **coins** (sorties) | `isSpecialSquare`, roi seul sur `SPECIAL` |
| Capture par **encadrement** ; trône, coin, **bord** comptent | `SubBoard.isCaptured` |
| Roi capturé si **entouré** (y compris 2 pièces + bord/coin) | `Board.isKingCaptured()` |
| Défenseur gagne si roi sur un **coin** | `Board.isKingEscaped()` |
| Match nul : **aucun coup légal** | *Non implémenté dans `CPUPlayer` actuel* (voir §5) |
| Match nul : répétition 3× | Serveur **ne détecte pas** (PDF) — pas obligatoire côté client |
| **Minimax + alpha-beta** | `CPUPlayer.minimaxAB` |
| **Fonction d’évaluation** (+ fin de partie) | `HeuristicEvaluator.evaluate` |
| **5 secondes** max par coup | *Non respecté dans `Client` actuel* (`DEPTH = 2` fixe, voir §5) |
| Vérifier coups adverses | *Non fait dans `Client` actuel* |
| Battre serveur niv. 1 **rouge et noir** | Objectif performance ; aujourd’hui surtout **noir** OK |

Référence : `hnefatafl.pdf` à la racine du repo (non versionné dans git).

---

## 2. Structure du projet (`Hnefatafl-AI/src/`)

```
Client.java          → Socket serveur (8888), protocole 1/2/3/4/5
CPUPlayer.java       → Minimax + alpha-beta, choix du coup
HeuristicEvaluator.java → evaluate(board, player)  ← stratégie principale
Board.java           → Plateau, coups légaux, captures, copie
BoardEvaluator.java  → Interface evaluate
Move.java / Converter.java / Mark.java → Coups et pièces
SubBoard (dans Board.java) → Détection capture (3×3)
```

### Flux d’une partie (réseau)

1. Serveur envoie **`1`** + chaîne plateau → client **rouge** joue tout de suite un coup.
2. Serveur envoie **`2`** + plateau → client **noir** attend.
3. À chaque tour : **`3`** + coup adversaire → client met à jour `board`, calcule, envoie son coup.
4. **`5`** → fin (capture ou échappement).

Le rôle rouge/noir est choisi dans le **GUI serveur** (Réseau joueur 1 ou 2), pas dans `main` du client.

### Qui appelle quoi

```
Client.computeAndSendMove()
    → cpu.getNextMoveAB(board, DEPTH)     // DEPTH = 2 aujourd’hui
        → pour chaque coup legal :
            minimaxAB(..., depth-1)
                → board.evaluate(maxPlayer)   // HeuristicEvaluator
```

`maxPlayer` est `Mark.RED` ou `Mark.BLACK` selon le camp du client. Minimax **maximise toujours** le score retourné par `evaluate(..., maxPlayer)`.

---

## 3. Stratégie heuristique actuelle (`HeuristicEvaluator`)

### 3.1 Idée directrice (une seule stratégie)

Le score est calculé **du point de vue des défenseurs** (noir + roi) : plus il est **élevé**, mieux c’est pour **faire échapper le roi** et **éviter la capture**.

Pour les **rouges**, on ne recalcule pas une deuxième fonction : on **inverse** le score :

```text
eval(rouge) = - eval_défenseur   (orientForPlayer)
```

C’est cohérent avec le PDF (objectifs **opposés**), mais ce n’est qu’une **approximation** : tous les termes sont pensés « fuite / survie du roi », pas « attaque coordonnée des rouges ».

### 3.2 Termes du score défenseur

| Terme | Poids (ordre de grandeur) | Alignement PDF / règles |
|-------|-----------------------------|-------------------------|
| **Matériel** `120×noirs − 100×rouges` | ~100–120 / pièce | Asymétrie 12 vs 24 ; pas imposé par le PDF, choix de design |
| **escapeThreatScore** | jusqu’à **50 000** + mobilité | Déplacement roi = tour ; **ray cast** dans 4 directions sur cases `EMPTY`/`SPECIAL` |
| → bonus si un rayon s’arrête sur un **coin** | 50 000 | Victoire défenseur = roi sur coin |
| → bonus bord puis coin en 2 temps | 8 000 | Approximation chemin le long du bord |
| → `reachPotential × 40` | somme des cases libres par direction | Mobilité du roi |
| **countOpenCornerRays × 3000** | par coin atteignable en **1 glissée** | Même idée : couloirs réels vers les sorties |
| **Hostiles autour du roi × 70** | bord, rouge, **trône** (pas coin X) | Capture roi = entouré ; PDF : bord/trône/coin utiles à la capture |
| **Bonus si ≥ 3 hostiles** | −4000 au score défenseur | Proche de la capture (4 côtés) |
| **Côtés ouverts × 350** | cases vides ou **noires** adjacentes au roi | Filet pas fermé — bon pour défenseur si *peu* de côtés ouverts |
| **Rouges sur lignes du roi × 90** | même rang/colonne, bloquent la glissée | Bloquer les **couloirs** vers les coins |

**Fin de partie** (obligatoire PDF pour l’éval) :

- `isKingEscaped()` → `± WIN_SCORE` (`1_000_000`) selon le camp.
- `isKingCaptured()` → l’inverse.

### 3.3 Cohérence règles PDF vs implémentation

| Règle PDF | Heuristique |
|-----------|-------------|
| Pièces glissent comme une tour | `farthestKingReach`, `clearLine`, `countRedsOnKingLines` |
| Cases de sortie = coins | `Board.isCorner`, bonus évasion |
| Trône central | Compte comme hostile adjacent (capture), pas comme coin |
| Rouge ne peut pas occuper coin/trône | *Non modélisé explicitement* dans l’heuristique (génération de coups dans `Board`) |
| Capture roi = entouré | `countHostileAroundKing`, `countOpenSidesAroundKing` |

**Limite importante** : l’heuristique ne simule **pas** « le roi gagne au prochain coup » ni « le roi est capturable au prochain coup » — seulement un **instantané** statique. Avec une **faible profondeur** de recherche, le roi peut s’échapper ou ne jamais être encerclé malgré un score « plausible ».

---

## 4. Pourquoi les **noirs** marchent mieux que les **rouges**

### 4.1 Alignement stratégie ↔ camp

| Camp | Objectif PDF | Heuristique |
|------|--------------|-------------|
| **Noir** | Échapper avec le roi | Score défenseur **direct** : fuite, couloirs, mobilité |
| **Rouge** | Capturer le roi | **−score défenseur** : indirect |

Quand le roi a encore de la mobilité, le score défenseur est **fort** → le rouge voit un score **très négatif** sur beaucoup de coups. Minimax choisit ce qui **monte** ce score négatif, mais :

- Les termes « encerclement » (`hostile`, `open sides`, `reds on lines`) sont **plus petits** que les termes « évasion » (50 000 + rayons) tant que le roi n’est pas coincé.
- Le rouge n’a pas de terme du type « **occuper la case vide à côté du roi** » avec un poids comparable à la menace d’évasion.
- Le **matériel** inversé pousse parfois à des coups qui **approchent** le roi sans fermer les 4 côtés (horizon court).

Résultat observé : **défense solide**, **attaque qui patine** (égalité longue ou défaite si le roi atteint un coin).

### 4.2 Effet « partie qui n’avance plus »

Avec **profondeur 2** et scores **égaux** sur plusieurs coups, le client choisit **au hasard** parmi les ex-aequo (`Random` dans `Client`). Deux joueurs faibles + hasard → **oscillations** (E10↔E7, etc.) sans progression vers capture ou coin.

---

## 5. Écarts importants : moteur de recherche / client vs PDF

État **réel** de `CPUPlayer.java` et `Client.java` (à la date de ce document) :

| Problème | Impact |
|----------|--------|
| `Client.DEPTH = 2` **fixe** | N’utilise **pas** les 5 s ; ~1–2 plies de prévision |
| `Math.abs(evaluate) == 100` | **Bug** : les fins de partie utilisent `WIN_SCORE = 1_000_000`, donc la condition **ne coupe jamais** l’arbre sur victoire/défaite |
| Pas de test `isKingEscaped` / `isKingCaptured` **avant** de continuer minimax | Peut explorer inutilement sous des positions déjà terminées |
| Pas de score **0** si aucun coup légal | PDF : match nul ; non conforme |
| Pas de validation du coup adversaire | PDF : devrait vérifier la légalité |
| Message **`4`** → exception | PDF : renvoyer un autre coup |
| Pas d’alpha remonté à la **racine** entre les coups frères | Élagage sous-optimal (mineur) |

**Conséquence** : même avec une bonne heuristique, le **rouge** est pénalisé deux fois — eval indirecte **et** recherche très shallow + bugs de terminaison.

Les **noirs** « fonctionnent » surtout parce que l’heuristique **colle au camp** et que profondeur 2 suffit parfois à **suivre** des couloirs vers le bord ; ce n’est pas garanti contre un adversaire fort.

---

## 6. Schéma mental du score (défenseur)

```text
score_def =
    matériel
  + menace_évasion (rayons, coins, mobilité)      ← domine souvent en milieu de partie
  + nb_coins_1_glissée × 3000
  − pression_encerclement (hostiles, côtés ouverts)
  − rouges_sur_lignes_du_roi × 90
```

```text
eval(RED)  = - score_def
eval(BLACK)=   score_def
```

Pour **encercler** en fin de partie, il faudrait que la branche « encerclement » **passe devant** « évasion » quand le roi est localisé — aujourd’hui les poids et la **profondeur** ne le garantissent pas, surtout pour le rouge.

---

## 7. Pistes d’évolution (pour le rapport / la suite)

Sans tout mélanger, le PDF suggère deux axes **distincts** :

1. **Heuristique** (fichier demandé au labo)  
   - Garder la stratégie « rayons + filet ».  
   - Pour le rouge : soit **renforcer** les termes encerclement / couloirs fermés, soit (plus tard) un **petit** bloc `computeAttackerScore` si le simple `−score_def` ne suffit pas.  
   - Ajuster les poids pour que `hostile ≥ 3` et `open sides → 0` rivalisent avec l’évasion quand le roi est au centre.

2. **Moteur / client** (hors « modifier uniquement HeuristicEvaluator », mais **obligatoire PDF**)  
   - Couper sur `WIN_SCORE` et positions terminales.  
   - Profondeur itérative ~**4,5 s**.  
   - Match nul sans coups → **0**.  
   - Validation des coups adverses.

---

## 8. Compilation et exécution

```powershell
cd Hnefatafl-AI
javac -encoding UTF-8 -d out src\*.java
java -cp out Client
```

Serveur GUI : joueur en **Réseau**, port **8888**, l’autre joueur Ordinateur niv. 1 ou 2.

---

## 9. Résumé en une phrase

**Aujourd’hui, le projet encode surtout une stratégie défenseur (« ouvrir/garder des couloirs vers les coins, éviter l’encerclement ») dans `HeuristicEvaluator`, ce qui est aligné avec le PDF pour les noirs ; les rouges utilisent le négatif de ce score, ce qui explique les égalités et l’échec à **fermer** le roi en fin de partie, aggravés par une recherche minimax en profondeur 2 et des coupes de fin de partie incorrectes dans `CPUPlayer`.**
