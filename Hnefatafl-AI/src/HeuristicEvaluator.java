public class HeuristicEvaluator implements BoardEvaluator {



    public static final int WIN_SCORE = 1_000_000;



    private static int DEFENDER_BLACK_PIECE_VALUE = 3_000;

    private static final int DEFENDER_RED_PIECE_VALUE = 1_000;

    private static int ATTACKER_RED_PIECE_VALUE = 5_000;

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

    private static final int THREATENED_RED_ENDGAME_THRESHOLD = 16;



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

        int kr = board.getKingRow();

        int kc = board.getKingColumn();

        if (kr >= 0) {

            int last = Board.SIZE - 1;

            int hostile = 0;

            int openSides = 0;

            int guards = 0;



            for (int[] dir : DIRECTIONS) {

                int row = kr + dir[0];

                int col = kc + dir[1];

                if (!board.isInBoard(row, col)) {

                    hostile++;

                    continue;

                }

                Mark m = board.getCell(row, col);

                if (m == Mark.RED || Board.isSpecialSquare(row, col)) {

                    hostile++;

                }

                if (m == Mark.EMPTY || m == Mark.BLACK) {

                    openSides++;

                }

                if (m == Mark.BLACK) {

                    guards++;

                }

            }



            defenderPositionScore += escapeThreatScore(board, kr, kc, last);

            defenderPositionScore += countOpenCornerRays(board, kr, kc) * OPEN_CORNER_RAY_WEIGHT;

            defenderPositionScore -= hostile * KING_SURROUND_WEIGHT;

            if (hostile >= 2) {

                defenderPositionScore -= KING_NET_ESTABLISHED;

            }

            if (hostile >= 3) {

                defenderPositionScore -= KING_ALMOST_CAPTURED;

            }

            defenderPositionScore += openSides * OPEN_SIDE_WEIGHT;

            defenderPositionScore -= countRedsOnKingLines(board, kr, kc) * RED_ON_KING_LINE_WEIGHT;

            defenderPositionScore += guards * BLACK_GUARD_WEIGHT;



            if (hostile >= 3 && guards > 0) {

                defenderPositionScore += guards * KING_GUARD_FORTRESS_WEIGHT;

            } else {

                defenderPositionScore -= countThreatenedKingGuards(board, kr, kc) * THREATENED_GUARD_WEIGHT;

            }

        }



        int blackCount = board.getBlackCount();

        int redCount = board.getRedCount();

        if (player == Mark.RED) {

            int hangingAttackers = redCount <= THREATENED_RED_ENDGAME_THRESHOLD

                    ? countThreatenedReds(board)

                    : 0;

            return attackerMaterialScore(blackCount, redCount)

                    - defenderPositionScore

                    - hangingAttackers * THREATENED_RED_WEIGHT;

        }

        return defenderMaterialScore(blackCount, redCount) + defenderPositionScore;

    }



    private int defenderMaterialScore(int blackCount, int redCount) {

        return blackCount * DEFENDER_BLACK_PIECE_VALUE - redCount * DEFENDER_RED_PIECE_VALUE;

    }



    private int attackerMaterialScore(int blackCount, int redCount) {

        return redCount * ATTACKER_RED_PIECE_VALUE - blackCount * ATTACKER_BLACK_PIECE_VALUE;

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

            int r = kingRow;

            int c = kingCol;

            while (true) {

                int nextRow = r + dir[0];

                int nextCol = c + dir[1];

                if (!board.isInBoard(nextRow, nextCol) || !kingCanTraverse(board, nextRow, nextCol)) {

                    break;

                }

                r = nextRow;

                c = nextCol;

            }

            reachPotential += Math.abs(r - kingRow) + Math.abs(c - kingCol);



            if (Board.isCorner(r, c)) {

                oneMoveThreat = Math.max(oneMoveThreat, ONE_MOVE_ESCAPE_BONUS);

            } else if (isOnEdge(r, c, last) && edgeLeadsToCorner(board, r, c, last)) {

                twoMoveThreat = Math.max(twoMoveThreat, TWO_MOVE_ESCAPE_BONUS);

            }

        }



        return oneMoveThreat + twoMoveThreat + reachPotential * REACH_STEP_WEIGHT;

    }



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

            int min = Math.min(c1, c2);

            int max = Math.max(c1, c2);

            for (int c = min + 1; c < max; c++) {

                if (!kingCanTraverse(board, r1, c)) {

                    return false;

                }

            }

            return true;

        }

        int min = Math.min(r1, r2);

        int max = Math.max(r1, r2);

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



    private boolean isOnEdge(int row, int col, int last) {

        return row == 0 || row == last || col == 0 || col == last;

    }



    private boolean edgeLeadsToCorner(Board board, int row, int col, int last) {

        if (Board.isCorner(row, col)) {

            return true;

        }

        if (row == 0 || row == last) {

            if (rayReachesCorner(board, row, col, 0, -1)) {

                return true;

            }

            if (rayReachesCorner(board, row, col, 0, 1)) {

                return true;

            }

        }

        if (col == 0 || col == last) {

            if (rayReachesCorner(board, row, col, -1, 0)) {

                return true;

            }

            if (rayReachesCorner(board, row, col, 1, 0)) {

                return true;

            }

        }

        return false;

    }



    private boolean rayReachesCorner(Board board, int startRow, int startCol, int dRow, int dCol) {

        int r = startRow;

        int c = startCol;

        while (true) {

            int nextRow = r + dRow;

            int nextCol = c + dCol;

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



    private int countThreatenedKingGuards(Board board, int kingRow, int kingCol) {

        int threatened = 0;

        for (int[] dir : DIRECTIONS) {

            int guardRow = kingRow + dir[0];

            int guardCol = kingCol + dir[1];

            if (board.isInBoard(guardRow, guardCol)

                    && board.getCell(guardRow, guardCol) == Mark.BLACK

                    && redCanCaptureGuardNext(board, guardRow, guardCol)) {

                threatened++;

            }

        }

        return threatened;

    }



    private boolean redCanCaptureGuardNext(Board board, int guardRow, int guardCol) {

        for (int[] dir : DIRECTIONS) {

            int anvilRow = guardRow + dir[0];

            int anvilCol = guardCol + dir[1];

            int hammerRow = guardRow - dir[0];

            int hammerCol = guardCol - dir[1];



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

            int row = targetRow + dir[0];

            int col = targetCol + dir[1];

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

            int anvilRow = redRow + dir[0];

            int anvilCol = redCol + dir[1];

            int hammerRow = redRow - dir[0];

            int hammerCol = redCol - dir[1];



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

            int row = targetRow + dir[0];

            int col = targetCol + dir[1];

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



    private int countRedsOnKingLines(Board board, int kingRow, int kingCol) {

        int count = 0;

        for (int[] dir : DIRECTIONS) {

            int row = kingRow + dir[0];

            int col = kingCol + dir[1];

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

