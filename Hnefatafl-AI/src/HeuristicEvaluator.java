/**
 * Évaluation statique d'une position de Hnefatafl 13x13.
 *
 * <p>Contrat : le score retourné est toujours exprimé du point de vue du
 * joueur passé en paramètre. Un score positif est favorable à ce joueur.
 *
 * <p>Cette classe est sans état : une instance est allouée par {@link Board}
 * à chaque copie de plateau, l'absence de champ rend ce coût négligeable.
 *
 * <p>Règles encodées :
 * <ul>
 *   <li>Les noirs (défenseurs, 12 pions + roi) gagnent si le roi atteint
 *       l'un des 4 coins.</li>
 *   <li>Les rouges (attaquants, 24 pions) gagnent si le roi est entouré
 *       de 4 cases hostiles (pion rouge, bord de plateau, trône ou coin).</li>
 * </ul>
 */
public class HeuristicEvaluator implements BoardEvaluator {

    /** Score d'une position gagnée. Doit rester très au-dessus des termes positionnels. */
    public static final int WIN_SCORE = 1_000_000;

    /* --- Poids des termes. Un seul endroit à toucher pour le réglage. --- */

    /** Valeur d'un pion rouge pour les rouges. */
    private static final int RED_PIECE_VALUE = 1_000;

    /**
     * Valeur d'un pion noir pour les noirs. Supérieure à celle d'un rouge :
     * avec un ratio 24 contre 12, un échange 1 pour 1 favorise les rouges.
     */
    private static final int BLACK_PIECE_VALUE = 2_000;

    /** Poids de la progression du roi vers un coin, par case gagnée. */
    private static final int KING_PROGRESS_WEIGHT = 600;

    /** Bonus par route libre roi -> coin. Non linéaire : deux routes sont imparables. */
    private static final int KING_OPEN_PATH_BONUS = 30_000;

    /** Pénalité par côté du roi déjà hostile, pour les noirs. */
    private static final int KING_ENCIRCLEMENT_WEIGHT = 4_000;

    private static final int[][] DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    private static final int[][] CORNERS = {
            {0, 0}, {0, Board.SIZE - 1}, {Board.SIZE - 1, 0}, {Board.SIZE - 1, Board.SIZE - 1}
    };

    @Override
    public int evaluate(Board board, Mark player) {
        int scoreForBlack = evaluateForBlack(board);
        return isBlackSide(player) ? scoreForBlack : -scoreForBlack;
    }

    /**
     * Cœur de l'évaluation, exprimé une seule fois du point de vue des noirs.
     *
     * <p>Un unique point de vue interne garantit l'antisymétrie du score, qui
     * est une précondition de correction du minimax : sans elle, un même
     * plateau peut être jugé bon pour les deux camps simultanément.
     */
    private int evaluateForBlack(Board board) {
        if (board.isKingEscaped()) {
            return WIN_SCORE;
        }
        if (board.isKingCaptured()) {
            return -WIN_SCORE;
        }

        int[] king = findKing(board);
        if (king == null) {
            return -WIN_SCORE; // défensif : roi absent sans capture détectée
        }

        return materialBalance(board)
                + kingProgress(king)
                + kingOpenPaths(board, king)
                - kingEncirclement(board, king);
    }

    /* ------------------------------------------------------------------ */
    /* Terme 1 : matériel                                                  */
    /* ------------------------------------------------------------------ */

    /** Différence de matériel, positive si les noirs sont avantagés. */
    private int materialBalance(Board board) {
        int blacks = 0;
        int reds = 0;

        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                Mark cell = board.getCell(row, col);
                if (cell == Mark.BLACK) {
                    blacks++;
                } else if (cell == Mark.RED) {
                    reds++;
                }
            }
        }
        return blacks * BLACK_PIECE_VALUE - reds * RED_PIECE_VALUE;
    }

    /* ------------------------------------------------------------------ */
    /* Terme 2 : progression du roi                                        */
    /* ------------------------------------------------------------------ */

    /**
     * Récompense la proximité du roi au coin le plus proche, en distance de
     * Chebyshev : le roi se déplaçant en ligne, deux coups suffisent pour
     * couvrir un déplacement diagonal si les routes sont libres.
     */
    private int kingProgress(int[] king) {
        int best = Integer.MAX_VALUE;
        for (int[] corner : CORNERS) {
            int distance = Math.max(
                    Math.abs(king[0] - corner[0]),
                    Math.abs(king[1] - corner[1]));
            best = Math.min(best, distance);
        }
        return (Board.SIZE - best) * KING_PROGRESS_WEIGHT;
    }

    /* ------------------------------------------------------------------ */
    /* Terme 3 : routes libres vers un coin                                */
    /* ------------------------------------------------------------------ */

    /**
     * Compte les coins que le roi peut atteindre en un coup, puis récompense
     * de façon quadratique.
     *
     * <p>La non-linéarité est délibérée : une seule route ouverte se bloque,
     * deux routes simultanées sont une victoire forcée que la recherche doit
     * voir même au-delà de son horizon.
     */
    private int kingOpenPaths(Board board, int[] king) {
        int openPaths = 0;
        for (int[] direction : DIRECTIONS) {
            if (rayReachesCorner(board, king[0], king[1], direction)) {
                openPaths++;
            }
        }
        return openPaths * openPaths * KING_OPEN_PATH_BONUS;
    }

    /** Vrai si la case atteinte en glissant depuis (row, col) est un coin libre. */
    private boolean rayReachesCorner(Board board, int row, int col, int[] direction) {
        int r = row + direction[0];
        int c = col + direction[1];

        while (board.isInBoard(r, c)) {
            Mark cell = board.getCell(r, c);
            if (cell != Mark.EMPTY && cell != Mark.SPECIAL) {
                return false; // route bloquée
            }
            if (Board.isCorner(r, c)) {
                return true;
            }
            r += direction[0];
            c += direction[1];
        }
        return false;
    }

    /* ------------------------------------------------------------------ */
    /* Terme 4 : encerclement du roi                                       */
    /* ------------------------------------------------------------------ */

    /**
     * Compte les côtés du roi déjà hostiles, pondéré de façon quadratique.
     *
     * <p>Une case hostile est un pion rouge, un bord de plateau ou une case
     * spéciale. Le roi est capturé à 4 côtés hostiles, donc 3 côtés est une
     * menace immédiate qui doit dominer tout gain matériel.
     */
    private int kingEncirclement(Board board, int[] king) {
        int hostileSides = 0;
        for (int[] direction : DIRECTIONS) {
            if (isHostileToKing(board, king[0] + direction[0], king[1] + direction[1])) {
                hostileSides++;
            }
        }
        return hostileSides * hostileSides * KING_ENCIRCLEMENT_WEIGHT;
    }

    /** Une case compte comme hostile si elle participerait à la capture du roi. */
    private boolean isHostileToKing(Board board, int row, int col) {
        Mark cell = board.getCell(row, col);
        return cell == Mark.RED
                || cell == Mark.OUT
                || cell == Mark.SPECIAL;
    }

    /* ------------------------------------------------------------------ */
    /* Utilitaires                                                         */
    /* ------------------------------------------------------------------ */

    /** Les noirs et le roi forment un seul camp du point de vue du jeu. */
    private static boolean isBlackSide(Mark player) {
        return player == Mark.BLACK || player == Mark.KING;
    }

    private int[] findKing(Board board) {
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                if (board.getCell(row, col) == Mark.KING) {
                    return new int[]{row, col};
                }
            }
        }
        return null;
    }

    /* ------------------------------------------------------------------ */
    /* Diagnostic                                                          */
    /* ------------------------------------------------------------------ */

    /**
     * Décomposition lisible du score, pour analyser une position litigieuse.
     * À appeler manuellement depuis {@code Client} quand l'IA joue un coup
     * douteux, jamais depuis la recherche.
     */
    public String explain(Board board, Mark player) {
        if (board.isKingEscaped()) {
            return "TERMINAL: roi echappe";
        }
        if (board.isKingCaptured()) {
            return "TERMINAL: roi capture";
        }

        int[] king = findKing(board);
        if (king == null) {
            return "TERMINAL: roi absent";
        }

        int material = materialBalance(board);
        int progress = kingProgress(king);
        int paths = kingOpenPaths(board, king);
        int encirclement = kingEncirclement(board, king);
        int totalForBlack = material + progress + paths - encirclement;

        return String.format(
                "roi=(%d,%d) | materiel=%+d progression=%+d routes=%+d encerclement=%+d"
                        + " | total(noirs)=%+d total(%s)=%+d",
                king[0], king[1],
                material, progress, paths, -encirclement,
                totalForBlack, player, evaluate(board, player));
    }
}