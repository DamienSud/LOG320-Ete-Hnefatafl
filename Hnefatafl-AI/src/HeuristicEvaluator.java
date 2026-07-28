public class HeuristicEvaluator implements BoardEvaluator {

    public static final int WIN_SCORE = 1_000_000;

    /**
     * Single strategy, defender-oriented score, flipped for red (orientForPlayer):
     *
     * 1) Material — blacks count a bit more (12 vs 24 at start).
     * 2) Escape — ray cast in 4 directions (same slides as the king); bonus if a
     *    ray hits a corner or the edge then a corner.
     * 3) Open corner count — how many corners are one clear slide away.
     * 4) King surround — hostile neighbours (red, throne, off-board; not corner X).
     * 5) Open sides — empty/black cells beside the king (capture not finished).
     * 6) Red lane blockers — reds on the king's rank/file stop rook escapes.
     *
     * All of (3–6) also help red when negated: blocking lanes and closing sides
     * raise red's score without a second evaluation function.
     */
    private static final int BLACK_PIECE_VALUE = 120;
    private static final int RED_PIECE_VALUE = 100;
    private static final int ONE_MOVE_ESCAPE_BONUS = 50_000;
    private static final int TWO_MOVE_ESCAPE_BONUS = 8_000;
    private static final int REACH_STEP_WEIGHT = 40;
    private static final int OPEN_CORNER_RAY_WEIGHT = 3_000;
    private static final int KING_SURROUND_WEIGHT = 70;
    private static final int KING_ALMOST_CAPTURED = 4_000;
    private static final int OPEN_SIDE_WEIGHT = 350;
    private static final int RED_ON_KING_LINE_WEIGHT = 90;

    private static final int[][] DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    @Override
    public int evaluate(Board board, Mark player) {
        if (board.isKingEscaped()) {
            return scoreFor(player, true);
        }
        if (board.isKingCaptured()) {
            return scoreFor(player, false);
        }

        int score = materialBalance(board);
        int[] king = findKing(board);
        if (king != null) {
            int kr = king[0], kc = king[1];
            int last = Board.SIZE - 1;

            score += escapeThreatScore(board, kr, kc, last);
            score += countOpenCornerRays(board, kr, kc) * OPEN_CORNER_RAY_WEIGHT;

            int hostile = countHostileAroundKing(board, kr, kc);
            score -= hostile * KING_SURROUND_WEIGHT;
            if (hostile >= 3) {
                score -= KING_ALMOST_CAPTURED;
            }

            score -= countOpenSidesAroundKing(board, kr, kc) * OPEN_SIDE_WEIGHT;
            score -= countRedsOnKingLines(board, kr, kc) * RED_ON_KING_LINE_WEIGHT;
        }

        return orientForPlayer(player, score);
    }

    private int materialBalance(Board board) {
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
        return black * BLACK_PIECE_VALUE - red * RED_PIECE_VALUE;
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
                if (m == Mark.RED || Board.isThrone(row, col)) {
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
