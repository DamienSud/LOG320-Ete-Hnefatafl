public class HeuristicEvaluator implements BoardEvaluator {

    /* to adjust */
    private static final int WIN_SCORE              = 1_000_000;
    private static final int PIECE_VALUE            = 100;
    private static final int ONE_MOVE_ESCAPE_BONUS  = 50_000;
    private static final int TWO_MOVE_ESCAPE_BONUS  = 8_000;
    private static final int REACH_STEP_WEIGHT      = 40;
    private static final int KING_SURROUND_WEIGHT   = 60;
    /* ---------------------------------------------------------------------------- */

    private static final int[][] DIRECTIONS = {{-1,0},{1,0},{0,-1},{0,1}};

    @Override
    public int evaluate(Board board, Mark player) {
        if (board.isKingEscaped())  return scoreFor(player, true);
        if (board.isKingCaptured()) return scoreFor(player, false);

        int score = 0;
        int size = Board.SIZE;
        int last = size - 1;
        int blackCount = 0, redCount = 0, kingRow = -1, kingCol = -1;

        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                Mark m = board.getCell(row, col);
                if (m == Mark.BLACK)      blackCount++;
                else if (m == Mark.RED)   redCount++;
                else if (m == Mark.KING) { kingRow = row; kingCol = col; }
            }
        }

        score += (blackCount - redCount) * PIECE_VALUE;

        if (kingRow != -1) {
            score += escapeThreatScore(board, kingRow, kingCol, last);
            score -= countHostileAroundKing(board, kingRow, kingCol) * KING_SURROUND_WEIGHT;
        }

        return orientForPlayer(player, score);
    }

    /* ----------------------- Heuristic logic (private) ----------------------- */

    private boolean isDefender(Mark player) {
        return player == Mark.BLACK || player == Mark.KING;
    }

    private int orientForPlayer(Mark player, int defenderScore) {
        return isDefender(player) ? defenderScore : -defenderScore;
    }

    private int scoreFor(Mark player, boolean defenderWins) {
        return orientForPlayer(player, defenderWins ? WIN_SCORE : -WIN_SCORE);
    }

    /** Score oriented defenders: open escape corridors (ray cast), not Manhattan distance. */
    private int escapeThreatScore(Board board, int kingRow, int kingCol, int last) {
        int oneMoveThreat = 0;
        int twoMoveThreat = 0;
        int reachPotential = 0;

        for (int[] dir : DIRECTIONS) {
            int[] far = farthestKingReach(board, kingRow, kingCol, dir[0], dir[1]);
            int farRow = far[0], farCol = far[1];
            reachPotential += Math.abs(farRow - kingRow) + Math.abs(farCol - kingCol);

            if (Board.isCorner(farRow, farCol)) {
                oneMoveThreat = Math.max(oneMoveThreat, ONE_MOVE_ESCAPE_BONUS);
            } else if (isOnEdge(farRow, farCol, last) && edgeLeadsToCorner(board, farRow, farCol, last)) {
                twoMoveThreat = Math.max(twoMoveThreat, TWO_MOVE_ESCAPE_BONUS);
            }
        }

        return oneMoveThreat + twoMoveThreat + reachPotential * REACH_STEP_WEIGHT;
    }

    private boolean kingCanTraverse(Board board, int row, int col) {
        Mark m = board.getCell(row, col);
        return m == Mark.EMPTY || m == Mark.SPECIAL;
    }

    /** Last cell the king can reach sliding in one direction (same rules as Board.canGoThrough). */
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

    /** From an edge cell, can the king slide along the border to a corner in one more move? */
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
            if (!board.isInBoard(row, col)) count++;
            else {
                Mark m = board.getCell(row, col);
                if (m == Mark.RED || board.isSpecialSquare(row, col)) count++;
            }
        }
        return count;
    }
}
