public class HeuristicEvaluator implements BoardEvaluator {

    public static final int WIN_SCORE = 1_000_000;

    /**
     * Positional strategy expressed from the defenders' point of view. The
     * positional part is flipped for red, but material is evaluated separately:
     * attackers must preserve enough pieces to finish the four-sided capture.
     *
     * 1) Material — blacks count a bit more (12 vs 24 at start).
     * 2) Escape — ray cast in 4 directions (same slides as the king); bonus if a
     *    ray hits a corner or the edge then a corner.
     * 3) Open corner count — how many corners are one clear slide away.
     * 4) King surround — hostile neighbours (red, throne, corner, off-board).
     * 5) Open sides — empty/black cells beside the king (capture not finished).
     * 6) Red lane blockers — reds on the king's rank/file stop rook escapes.
     * 7) King guards — adjacent black pieces must be removed before closing the net.
     * 8) Hanging attackers — reds capturable by black on the next move.
     *
     * All of (3–7) help red when the positional score is negated.
     */
    /*
     * Defenders start with 12 pieces and attackers with 24. Giving a black piece
     * twice the value keeps the initial material score neutral. More importantly,
     * a red loss now costs 1000 points: speculative positioning can no longer hide
     * several sacrificed attackers.
     */
    private static int DEFENDER_BLACK_PIECE_VALUE = 3_000; // final 2000
    private static final int DEFENDER_RED_PIECE_VALUE = 1_000;
    /*
     * Red needs several coordinated pieces to capture the king. Losing one attacker
     * is therefore more serious than failing to capture one ordinary defender.
     */
    private static int ATTACKER_RED_PIECE_VALUE = 5_000; // final 3000
    private static final int ATTACKER_BLACK_PIECE_VALUE = 1_000;
    private static final int ONE_MOVE_ESCAPE_BONUS = 50_000;
    private static final int TWO_MOVE_ESCAPE_BONUS = 8_000;
    private static final int REACH_STEP_WEIGHT = 40;
    private static final int OPEN_CORNER_RAY_WEIGHT = 3_000;
    private static final int KING_SURROUND_WEIGHT = 300;
    private static final int KING_NET_ESTABLISHED = 1_000;
    private static final int KING_ALMOST_CAPTURED = 4_000;
    private static final int OPEN_SIDE_WEIGHT = 400;
    private static final int RED_ON_KING_LINE_WEIGHT = 120;
    private static final int BLACK_GUARD_WEIGHT = 1_800;
    private static final int THREATENED_GUARD_WEIGHT = 3_500;
    private static final int KING_GUARD_FORTRESS_WEIGHT = 12_000;
    private static final int THREATENED_RED_WEIGHT = 2_000;

    private static final int[][] DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    @Override
    public int evaluate(Board board, Mark player) {
        if(player == Mark.BLACK || player == Mark.KING) {
            DEFENDER_BLACK_PIECE_VALUE = 2000;
            ATTACKER_RED_PIECE_VALUE = 3000;
        } else {
            DEFENDER_BLACK_PIECE_VALUE = 3000;
            ATTACKER_RED_PIECE_VALUE = 5000;
        }

        if (board.isKingEscaped()) {
            return scoreFor(player, true);
        }
        if (board.isKingCaptured()) {
            return scoreFor(player, false);
        }

        int defenderPositionScore = 0;
        int[] king = findKing(board);
        if (king != null) {
            int kr = king[0], kc = king[1];
            int last = Board.SIZE - 1;

            defenderPositionScore += escapeThreatScore(board, kr, kc, last);
            defenderPositionScore += countOpenCornerRays(board, kr, kc)
                    * OPEN_CORNER_RAY_WEIGHT;

            int hostile = countHostileAroundKing(board, kr, kc);
            defenderPositionScore -= hostile * KING_SURROUND_WEIGHT;
            if (hostile >= 2) {
                defenderPositionScore -= KING_NET_ESTABLISHED;
            }
            if (hostile >= 3) {
                defenderPositionScore -= KING_ALMOST_CAPTURED;
            }

            /*
             * Open sides help the defender, so this term must be positive in the
             * defender score. The previous '-' made red prefer leaving the king open.
             */
            defenderPositionScore += countOpenSidesAroundKing(board, kr, kc)
                    * OPEN_SIDE_WEIGHT;
            defenderPositionScore -= countRedsOnKingLines(board, kr, kc)
                    * RED_ON_KING_LINE_WEIGHT;

            /*
             * A black piece beside the king is not merely an "open side": it is a
             * guard that red must sandwich first. Reward it for defenders, but make
             * a guard that red can capture next move strongly favourable to red.
             */
            int guards = countBlackGuardsAroundKing(board, kr, kc);
            defenderPositionScore += guards * BLACK_GUARD_WEIGHT;

            if (hostile >= 3 && guards > 0) {
                /*
                 * Three red sides plus one black guard is a fortress, not a mating
                 * net: capturing the guard leaves an empty square and the king moves
                 * into it before red can close it. Red must remove guards while the
                 * king still has another exit, then build the final sides.
                 */
                defenderPositionScore += guards * KING_GUARD_FORTRESS_WEIGHT;
            } else {
                defenderPositionScore -= countThreatenedKingGuards(board, kr, kc)
                        * THREATENED_GUARD_WEIGHT;
            }
        }

        if (player == Mark.RED) {
            /*
             * A threatened red is still physically on the board, so material alone
             * cannot see the coming loss at the search horizon. Penalise it now.
             */
            int hangingAttackers = countThreatenedReds(board);
            return attackerMaterialScore(board)
                    - defenderPositionScore
                    - hangingAttackers * THREATENED_RED_WEIGHT;
        }
        return defenderMaterialScore(board) + defenderPositionScore;
    }

    private int defenderMaterialScore(Board board) {
        int[] counts = countPieces(board);
        return counts[0] * DEFENDER_BLACK_PIECE_VALUE
                - counts[1] * DEFENDER_RED_PIECE_VALUE;
    }

    private int attackerMaterialScore(Board board) {
        int[] counts = countPieces(board);
        return counts[1] * ATTACKER_RED_PIECE_VALUE
                - counts[0] * ATTACKER_BLACK_PIECE_VALUE;
    }

    /** Returns {blackCount, redCount}. */
    private int[] countPieces(Board board) {
        int black = 0, red = 0;
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                Mark m = board.getCell(row, col);
                if (m == Mark.BLACK) {
                    black++;
                } else if (m == Mark.RED) {
                    red++;
                }
            }
        }
        return new int[]{black, red};
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

    private boolean isDefender(Mark player) {
        return player == Mark.BLACK || player == Mark.KING;
    }

    private int orientForPlayer(Mark player, int defenderScore) {
        return isDefender(player) ? defenderScore : -defenderScore;
    }

    private int scoreFor(Mark player, boolean defenderWins) {
        return orientForPlayer(player, defenderWins ? WIN_SCORE : -WIN_SCORE);
    }

    private int escapeThreatScore(Board board, int kingRow, int kingCol, int last) {
        int oneMoveThreat = 0;
        int twoMoveThreat = 0;
        int reachPotential = 0;

        for (int[] dir : DIRECTIONS) {
            int[] far = farthestKingReach(board, kingRow, kingCol, dir[0], dir[1]);
            reachPotential += Math.abs(far[0] - kingRow) + Math.abs(far[1] - kingCol);

            if (Board.isCorner(far[0], far[1])) {
                oneMoveThreat = Math.max(oneMoveThreat, ONE_MOVE_ESCAPE_BONUS);
            } else if (isOnEdge(far[0], far[1], last) && edgeLeadsToCorner(board, far[0], far[1], last)) {
                twoMoveThreat = Math.max(twoMoveThreat, TWO_MOVE_ESCAPE_BONUS);
            }
        }

        return oneMoveThreat + twoMoveThreat + reachPotential * REACH_STEP_WEIGHT;
    }

    /** Corners reachable with one unobstructed slide from the king. */
    private int countOpenCornerRays(Board board, int kingRow, int kingCol) {
        int last = Board.SIZE - 1;
        int count = 0;
        int[][] corners = {{0, 0}, {0, last}, {last, 0}, {last, last}};
        for (int[] corner : corners) {
            if (canSlideToCorner(board, kingRow, kingCol, corner[0], corner[1])) {
                count++;
            }
        }
        return count;
    }

    private boolean canSlideToCorner(Board board, int kingRow, int kingCol, int cornerRow, int cornerCol) {
        if (kingRow == cornerRow && kingCol == cornerCol) {
            return true;
        }
        if (kingRow == cornerRow && clearLine(board, kingRow, kingCol, kingRow, cornerCol)) {
            return true;
        }
        return kingCol == cornerCol && clearLine(board, kingRow, kingCol, cornerRow, kingCol);
    }

    private boolean clearLine(Board board, int r1, int c1, int r2, int c2) {
        if (r1 != r2 && c1 != c2) {
            return false;
        }
        if (r1 == r2) {
            int min = Math.min(c1, c2), max = Math.max(c1, c2);
            for (int c = min + 1; c < max; c++) {
                if (!kingCanTraverse(board, r1, c)) {
                    return false;
                }
            }
            return true;
        }
        int min = Math.min(r1, r2), max = Math.max(r1, r2);
        for (int r = min + 1; r < max; r++) {
            if (!kingCanTraverse(board, r, c1)) {
                return false;
            }
        }
        return true;
    }

    private boolean kingCanTraverse(Board board, int row, int col) {
        Mark m = board.getCell(row, col);
        return m == Mark.EMPTY || m == Mark.SPECIAL;
    }

    private int[] farthestKingReach(Board board, int row, int col, int dRow, int dCol) {
        int r = row, c = col;
        while (true) {
            int nextRow = r + dRow, nextCol = c + dCol;
            if (!board.isInBoard(nextRow, nextCol) || !kingCanTraverse(board, nextRow, nextCol)) {
                break;
            }
            r = nextRow;
            c = nextCol;
        }
        return new int[]{r, c};
    }

    private boolean isOnEdge(int row, int col, int last) {
        return row == 0 || row == last || col == 0 || col == last;
    }

    private boolean edgeLeadsToCorner(Board board, int row, int col, int last) {
        if (Board.isCorner(row, col)) {
            return true;
        }
        if (row == 0 || row == last) {
            if (rayReachesCorner(board, row, col, 0, -1)) return true;
            if (rayReachesCorner(board, row, col, 0, 1)) return true;
        }
        if (col == 0 || col == last) {
            if (rayReachesCorner(board, row, col, -1, 0)) return true;
            if (rayReachesCorner(board, row, col, 1, 0)) return true;
        }
        return false;
    }

    private boolean rayReachesCorner(Board board, int startRow, int startCol, int dRow, int dCol) {
        int r = startRow, c = startCol;
        while (true) {
            int nextRow = r + dRow, nextCol = c + dCol;
            if (!board.isInBoard(nextRow, nextCol) || !kingCanTraverse(board, nextRow, nextCol)) {
                return false;
            }
            r = nextRow;
            c = nextCol;
            if (Board.isCorner(r, c)) {
                return true;
            }
        }
    }

    private int countHostileAroundKing(Board board, int kingRow, int kingCol) {
        int count = 0;
        for (int[] dir : DIRECTIONS) {
            int row = kingRow + dir[0], col = kingCol + dir[1];
            if (!board.isInBoard(row, col)) {
                count++;
            } else {
                Mark m = board.getCell(row, col);
                /*
                 * PDF p.3 and Board/SubBoard: throne and exit squares can help
                 * capture the king. Both are represented by special coordinates.
                 */
                if (m == Mark.RED || Board.isSpecialSquare(row, col)) {
                    count++;
                }
            }
        }
        return count;
    }

    private int countOpenSidesAroundKing(Board board, int kingRow, int kingCol) {
        int open = 0;
        for (int[] dir : DIRECTIONS) {
            int row = kingRow + dir[0], col = kingCol + dir[1];
            if (!board.isInBoard(row, col)) {
                continue;
            }
            Mark m = board.getCell(row, col);
            if (m == Mark.EMPTY || m == Mark.BLACK) {
                open++;
            }
        }
        return open;
    }

    private int countBlackGuardsAroundKing(Board board, int kingRow, int kingCol) {
        int guards = 0;
        for (int[] dir : DIRECTIONS) {
            int row = kingRow + dir[0], col = kingCol + dir[1];
            if (board.isInBoard(row, col) && board.getCell(row, col) == Mark.BLACK) {
                guards++;
            }
        }
        return guards;
    }

    /**
     * Counts adjacent black guards that red can sandwich on its next move.
     *
     * Example from the observed position: the king has three hostile sides and F8
     * is the last guard. If a red already forms the anvil and another red can slide
     * onto the opposite empty square, this term guides minimax toward removing F8;
     * after that, red can occupy the newly empty side and capture the king.
     */
    private int countThreatenedKingGuards(Board board, int kingRow, int kingCol) {
        int threatened = 0;
        for (int[] dir : DIRECTIONS) {
            int guardRow = kingRow + dir[0], guardCol = kingCol + dir[1];
            if (board.isInBoard(guardRow, guardCol)
                    && board.getCell(guardRow, guardCol) == Mark.BLACK
                    && redCanCaptureGuardNext(board, guardRow, guardCol)) {
                threatened++;
            }
        }
        return threatened;
    }

    private boolean redCanCaptureGuardNext(Board board, int guardRow, int guardCol) {
        /*
         * Check both orientations of each axis. "Anvil" is already red/special;
         * "hammer" is the empty square where another red can legally slide.
         */
        for (int[] dir : DIRECTIONS) {
            int anvilRow = guardRow + dir[0], anvilCol = guardCol + dir[1];
            int hammerRow = guardRow - dir[0], hammerCol = guardCol - dir[1];

            if (!isRedCaptureAnvil(board, anvilRow, anvilCol)
                    || !board.isInBoard(hammerRow, hammerCol)
                    || board.getCell(hammerRow, hammerCol) != Mark.EMPTY
                    || Board.isSpecialSquare(hammerRow, hammerCol)) {
                continue;
            }

            if (redCanSlideTo(board, hammerRow, hammerCol)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRedCaptureAnvil(Board board, int row, int col) {
        if (!board.isInBoard(row, col)) {
            return false;
        }
        Mark cell = board.getCell(row, col);
        return cell == Mark.RED || Board.isSpecialSquare(row, col);
    }

    private boolean redCanSlideTo(Board board, int targetRow, int targetCol) {
        for (int[] dir : DIRECTIONS) {
            int row = targetRow + dir[0], col = targetCol + dir[1];
            while (board.isInBoard(row, col)) {
                Mark cell = board.getCell(row, col);
                if (cell == Mark.RED) {
                    return true;
                }
                if (cell != Mark.EMPTY && cell != Mark.SPECIAL) {
                    break;
                }
                row += dir[0];
                col += dir[1];
            }
        }
        return false;
    }

    /** Red pieces that a black piece or the king can sandwich on its next move. */
    private int countThreatenedReds(Board board) {
        int threatened = 0;
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                if (board.getCell(row, col) == Mark.RED
                        && defenderCanCaptureRedNext(board, row, col)) {
                    threatened++;
                }
            }
        }
        return threatened;
    }

    private boolean defenderCanCaptureRedNext(Board board, int redRow, int redCol) {
        for (int[] dir : DIRECTIONS) {
            int anvilRow = redRow + dir[0], anvilCol = redCol + dir[1];
            int hammerRow = redRow - dir[0], hammerCol = redCol - dir[1];

            if (!isDefenderCaptureAnvil(board, anvilRow, anvilCol)
                    || !board.isInBoard(hammerRow, hammerCol)
                    || board.getCell(hammerRow, hammerCol) != Mark.EMPTY) {
                continue;
            }

            if (defenderCanSlideTo(board, hammerRow, hammerCol)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDefenderCaptureAnvil(Board board, int row, int col) {
        if (!board.isInBoard(row, col)) {
            return false;
        }
        Mark cell = board.getCell(row, col);
        return cell == Mark.BLACK
                || cell == Mark.KING
                || Board.isSpecialSquare(row, col);
    }

    private boolean defenderCanSlideTo(Board board, int targetRow, int targetCol) {
        for (int[] dir : DIRECTIONS) {
            int row = targetRow + dir[0], col = targetCol + dir[1];
            while (board.isInBoard(row, col)) {
                Mark cell = board.getCell(row, col);
                if (cell == Mark.BLACK || cell == Mark.KING) {
                    return true;
                }
                if (cell != Mark.EMPTY && cell != Mark.SPECIAL) {
                    break;
                }
                row += dir[0];
                col += dir[1];
            }
        }
        return false;
    }

    /** Reds on the same row/column as the king (block rook slides toward corners). */
    private int countRedsOnKingLines(Board board, int kingRow, int kingCol) {
        int count = 0;
        for (int[] dir : DIRECTIONS) {
            int row = kingRow + dir[0], col = kingCol + dir[1];
            while (board.isInBoard(row, col)) {
                if (board.getCell(row, col) == Mark.RED) {
                    count++;
                }
                Mark cell = board.getCell(row, col);
                if (cell != Mark.EMPTY && cell != Mark.SPECIAL) {
                    break;
                }
                row += dir[0];
                col += dir[1];
            }
        }
        return count;
    }
}
