public class HeuristicEvaluator implements BoardEvaluator {

    /* to adjust */
    private static final int WIN_SCORE            = 1_000_000;
    private static final int PIECE_VALUE          = 100;
    private static final int KING_ESCAPE_WEIGHT   = 40;
    private static final int KING_SURROUND_WEIGHT = 60;
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
            int dist = distanceToNearestCorner(kingRow, kingCol, last);
            score += (last - dist) * KING_ESCAPE_WEIGHT;
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

    private int distanceToNearestCorner(int r, int c, int last) {
        int d1 = r + c, d2 = r + (last - c);
        int d3 = (last - r) + c, d4 = (last - r) + (last - c);
        return Math.min(Math.min(d1, d2), Math.min(d3, d4));
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
