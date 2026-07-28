import java.util.ArrayList;
import java.util.Set;

public class CPUPlayer {

    private static final int INFINITY = Integer.MAX_VALUE / 2;
    private static final int MAX_ITERATIVE_DEPTH = 32;
    private static final int TIME_CHECK_MASK = 255;
    private static final long MIN_TIME_FOR_NEXT_DEPTH_NS = 100_000_000L;
    private static final int REPETITION_PENALTY = 200_000;
    private static final int IMMEDIATE_REVERSAL_PENALTY = 25_000;
    private static final int[][] DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    private static class SearchTimeout extends RuntimeException {
        SearchTimeout() {
            super(null, null, false, false);
        }
    }

    private int numExploredNodes;
    private final Mark maxPlayer;
    private final Mark minPlayer;

    private long deadlineNanos = Long.MAX_VALUE;
    private int nodesSinceTimeCheck;
    private int lastCompletedDepth;
    private int lastRootScore;
    private Set<String> seenPositions;
    private Move lastPlayedMove;

    public CPUPlayer(Mark cpu) {
        this.numExploredNodes = 0;
        this.maxPlayer = cpu;
        this.minPlayer = Converter.getOpponent(cpu);
    }

    public int getNumOfExploredNodes() {
        return numExploredNodes;
    }

    public int getLastCompletedDepth() {
        return lastCompletedDepth;
    }

    /**
     * API à profondeur fixe conservée pour les tests du laboratoire.
     */
    public ArrayList<Move> getNextMoveAB(Board board, int depth) {
        resetNumExploredNodes();
        ArrayList<Move> bestMoves = new ArrayList<>();
        int bestScore = -INFINITY;
        int alpha = -INFINITY;

        for (Move move : board.getPossibleMoves(maxPlayer)) {
            Board boardCopy = board.copy();
            boardCopy.play(move);

            int score = minimaxAB(boardCopy, false, alpha, INFINITY, depth - 1);

            if (score > bestScore) {
                bestScore = score;
                bestMoves.clear();
                bestMoves.add(move);
            } else if (score == bestScore) {
                bestMoves.add(move);
            }
            alpha = Math.max(alpha, bestScore);
        }
        return bestMoves;
    }

    /**
     * Approfondissement itératif : profondeur 1, 2, 3... jusqu'au délai.
     * Une profondeur incomplète est rejetée; le dernier résultat complet est joué.
     */
    public Move getBestMoveWithinMillis(Board board, long millisBudget) {
        return getBestMoveWithinMillis(board, millisBudget, null);
    }

    public Move getBestMoveWithinMillis(
            Board board, long millisBudget, Set<String> positionsAlreadySeen) {
        resetNumExploredNodes();
        deadlineNanos = System.nanoTime() + millisBudget * 1_000_000L;
        nodesSinceTimeCheck = 0;
        lastCompletedDepth = 0;
        seenPositions = positionsAlreadySeen;

        Move bestMove = null;
        try {
            for (int depth = 1; depth <= MAX_ITERATIVE_DEPTH; depth++) {
                long remaining = deadlineNanos - System.nanoTime();
                if (remaining <= 0
                        || (depth > 1 && remaining < MIN_TIME_FOR_NEXT_DEPTH_NS)) {
                    break;
                }

                Move result = searchRoot(board, depth, bestMove);
                if (result == null) {
                    break;
                }

                bestMove = result;
                lastCompletedDepth = depth;
                if (Math.abs(lastRootScore) >= HeuristicEvaluator.WIN_SCORE) {
                    break;
                }
            }
        } catch (SearchTimeout ignored) {
            // On conserve le coup de la dernière profondeur entièrement terminée.
        } finally {
            deadlineNanos = Long.MAX_VALUE;
            seenPositions = null;
        }

        if (bestMove == null) {
            ArrayList<Move> legal = board.getPossibleMoves(maxPlayer);
            if (legal.isEmpty()) {
                throw new IllegalStateException("no legal moves found");
            }
            bestMove = legal.get(0);
        }
        lastPlayedMove = bestMove;
        return bestMove;
    }

    private Move searchRoot(Board board, int depth, Move previousBest) {
        ArrayList<Move> moves = board.getPossibleMoves(maxPlayer);
        if (moves.isEmpty()) {
            return null;
        }
        orderPreferredFirst(moves, previousBest);

        Move bestMove = null;
        int bestScore = -INFINITY;
        int alpha = -INFINITY;

        for (Move move : moves) {
            checkDeadline();
            Board boardCopy = board.copy();
            boardCopy.play(move);
            int score = minimaxAB(boardCopy, false, alpha, INFINITY, depth - 1);
            if (wouldRepeatRootPosition(boardCopy)) {
                score -= REPETITION_PENALTY;
            }
            if (isImmediateReverse(move)) {
                score -= IMMEDIATE_REVERSAL_PENALTY;
            }

            if (bestMove == null || score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
            alpha = Math.max(alpha, bestScore);
        }

        lastRootScore = bestScore;
        return bestMove;
    }

    private boolean isImmediateReverse(Move move) {
        return lastPlayedMove != null
                && move.getStartRow() == lastPlayedMove.getEndRow()
                && move.getStartColumn() == lastPlayedMove.getEndColumn()
                && move.getEndRow() == lastPlayedMove.getStartRow()
                && move.getEndColumn() == lastPlayedMove.getStartColumn();
    }

    private boolean wouldRepeatRootPosition(Board boardAfterMove) {
        return seenPositions != null
                && seenPositions.contains(
                        boardAfterMove.getBoardAsOneLineString("int"));
    }

    private int minimaxAB(Board board, boolean isMaxNode, int alpha, int beta, int depth) {
        numExploredNodes++;
        if ((++nodesSinceTimeCheck & TIME_CHECK_MASK) == 0) {
            checkDeadline();
        }

        /*
         * Le PDF exige que l'évaluation reconnaisse la victoire. Il faut arrêter
         * ici, avant de générer des descendants d'une partie déjà terminée.
         */
        if (board.isKingEscaped() || board.isKingCaptured()) {
            return board.evaluate(maxPlayer);
        }
        if (depth <= 0) {
            return board.evaluate(maxPlayer);
        }

        Mark sideToMove = isMaxNode ? maxPlayer : minPlayer;
        ArrayList<Move> moves = board.getPossibleMoves(sideToMove);
        if (moves.isEmpty()) {
            return 0; // PDF p.3 : aucun coup légal => match nul.
        }
        orderCapturesFirst(board, moves);

        if (isMaxNode) {
            int bestScore = -INFINITY;
            for (Move move : moves) {
                Board boardCopy = board.copy();
                boardCopy.play(move);
                int score = minimaxAB(boardCopy, false, alpha, beta, depth - 1);
                bestScore = Math.max(bestScore, score);
                alpha = Math.max(alpha, bestScore);
                if (beta <= alpha) break;
            }
            return bestScore;
        } else {
            int bestScore = INFINITY;
            for (Move move : moves) {
                Board boardCopy = board.copy();
                boardCopy.play(move);
                int score = minimaxAB(boardCopy, true, alpha, beta, depth - 1);
                bestScore = Math.min(bestScore, score);
                beta = Math.min(beta, bestScore);
                if (beta <= alpha) break;
            }
            return bestScore;
        }
    }

    private void checkDeadline() {
        if (System.nanoTime() >= deadlineNanos) {
            throw new SearchTimeout();
        }
    }

    private void orderPreferredFirst(ArrayList<Move> moves, Move preferred) {
        if (preferred == null) {
            return;
        }
        for (int i = 0; i < moves.size(); i++) {
            if (sameMove(moves.get(i), preferred)) {
                Move first = moves.get(0);
                moves.set(0, moves.get(i));
                moves.set(i, first);
                return;
            }
        }
    }

    /**
     * Tri léger : les captures probables en premier améliorent les coupures
     * alpha-beta sans modifier le résultat du minimax.
     */
    private void orderCapturesFirst(Board board, ArrayList<Move> moves) {
        int insertion = 0;
        for (int i = 0; i < moves.size(); i++) {
            if (isLikelyCapture(board, moves.get(i))) {
                Move capture = moves.get(i);
                moves.set(i, moves.get(insertion));
                moves.set(insertion, capture);
                insertion++;
            }
        }
    }

    private boolean isLikelyCapture(Board board, Move move) {
        Mark piece = board.getCell(move.getStartRow(), move.getStartColumn());
        int row = move.getEndRow(), col = move.getEndColumn();

        for (int[] dir : DIRECTIONS) {
            Mark neighbour = board.getCell(row + dir[0], col + dir[1]);
            if (!Board.isOpponent(piece, neighbour)) {
                continue;
            }

            int behindRow = row + 2 * dir[0];
            int behindCol = col + 2 * dir[1];
            Mark behind = board.getCell(behindRow, behindCol);
            if (behind == piece
                    || behind == Mark.SPECIAL
                    || (piece == Mark.RED && behind == Mark.RED)
                    || (piece != Mark.RED && (behind == Mark.BLACK || behind == Mark.KING))) {
                return true;
            }
        }
        return false;
    }

    private boolean sameMove(Move a, Move b) {
        return a.getStartRow() == b.getStartRow()
                && a.getStartColumn() == b.getStartColumn()
                && a.getEndRow() == b.getEndRow()
                && a.getEndColumn() == b.getEndColumn();
    }

    public Mark getMaxPlayer() {
        return this.maxPlayer;
    }

    public void resetNumExploredNodes() {
        this.numExploredNodes = 0;
    }
}
