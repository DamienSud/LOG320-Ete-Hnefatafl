import java.util.ArrayList;
import java.util.Set;

public class CPUPlayer {

    private static final int INFINITY = Integer.MAX_VALUE / 2;
    private static final int MAX_ITERATIVE_DEPTH = 32;
    private static final int TIME_CHECK_MASK = 255;
    private static final long MIN_TIME_FOR_NEXT_DEPTH_NS = 100_000_000L;
    private static final int REPETITION_PENALTY = 200_000;
    private static final int IMMEDIATE_REVERSAL_PENALTY = 25_000;
    private static final int ASPIRATION_WINDOW = 50;
    private static final int MAX_KILLERS = 2;
    private static final int HISTORY_SIZE = Board.SIZE * Board.SIZE * Board.SIZE * Board.SIZE;
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
    private final TranspositionTable transpositionTable = new TranspositionTable();

    private final ArrayList<Move> moveBuffer = new ArrayList<>(128);
    private final Board.Undo[] undoStack = new Board.Undo[MAX_ITERATIVE_DEPTH + 4];
    private final Move[][] killerMoves = new Move[MAX_ITERATIVE_DEPTH + 1][MAX_KILLERS];
    private final int[] historyScores = new int[HISTORY_SIZE];

    public CPUPlayer(Mark cpu) {
        this.numExploredNodes = 0;
        this.maxPlayer = cpu;
        this.minPlayer = Converter.getOpponent(cpu);
        for (int i = 0; i < undoStack.length; i++) {
            undoStack[i] = new Board.Undo();
        }
    }

    public int getNumOfExploredNodes() {
        return numExploredNodes;
    }

    public int getLastCompletedDepth() {
        return lastCompletedDepth;
    }

    public TranspositionTable getTranspositionTable() {
        return transpositionTable;
    }

    /**
     * API à profondeur fixe conservée pour les tests du laboratoire.
     */
    public ArrayList<Move> getNextMoveAB(Board board, int depth) {
        resetNumExploredNodes();
        java.util.Arrays.fill(historyScores, 0);
        for (Move[] killers : killerMoves) {
            java.util.Arrays.fill(killers, null);
        }

        ArrayList<Move> bestMoves = new ArrayList<>();
        int bestScore = -INFINITY;
        int alpha = -INFINITY;

        board.generateMoves(maxPlayer, moveBuffer);
        Move[] rootMoves = moveBuffer.toArray(new Move[0]);
        for (Move move : rootMoves) {
            board.makeMove(move, undoStack[0]);
            try {
                int score = minimaxAB(board, false, alpha, INFINITY, depth - 1, 1);
                if (score > bestScore) {
                    bestScore = score;
                    bestMoves.clear();
                    bestMoves.add(move);
                } else if (score == bestScore) {
                    bestMoves.add(move);
                }
                alpha = Math.max(alpha, bestScore);
            } finally {
                board.unmakeMove(undoStack[0]);
            }
        }
        return bestMoves;
    }

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
        transpositionTable.resetStatistics();
        java.util.Arrays.fill(historyScores, 0);
        for (Move[] killers : killerMoves) {
            java.util.Arrays.fill(killers, null);
        }

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
            board.generateMoves(maxPlayer, moveBuffer);
            if (moveBuffer.isEmpty()) {
                throw new IllegalStateException("no legal moves found");
            }
            bestMove = moveBuffer.get(0);
        }
        lastPlayedMove = bestMove;
        return bestMove;
    }

    private Move searchRoot(Board board, int depth, Move previousBest) {
        board.generateMoves(maxPlayer, moveBuffer);
        if (moveBuffer.isEmpty()) {
            return null;
        }
        orderRootMoves(board, moveBuffer, previousBest, depth);
        Move[] rootMoves = moveBuffer.toArray(new Move[0]);

        Move bestMove = null;
        int bestScore = -INFINITY;
        int alpha = -INFINITY;
        int beta = INFINITY;

        if (depth > 1 && lastCompletedDepth > 0) {
            alpha = lastRootScore - ASPIRATION_WINDOW;
            beta = lastRootScore + ASPIRATION_WINDOW;
        }

        while (true) {
            bestMove = null;
            bestScore = -INFINITY;
            int currentAlpha = alpha;

            for (Move move : rootMoves) {
                checkDeadline();
                board.makeMove(move, undoStack[0]);
                try {
                    int score;
                    if (bestMove == null) {
                        score = minimaxAB(board, false, currentAlpha, beta, depth - 1, 1);
                    } else {
                        score = minimaxAB(board, false, currentAlpha, currentAlpha + 1, depth - 1, 1);
                        if (score <= currentAlpha) {
                            score = minimaxAB(board, false, currentAlpha, beta, depth - 1, 1);
                        }
                    }
                    if (wouldRepeatRootPosition(board)) {
                        score -= REPETITION_PENALTY;
                    }
                    if (isImmediateReverse(move)) {
                        score -= IMMEDIATE_REVERSAL_PENALTY;
                    }

                    if (bestMove == null || score > bestScore) {
                        bestScore = score;
                        bestMove = move;
                    }
                    currentAlpha = Math.max(currentAlpha, bestScore);
                } finally {
                    board.unmakeMove(undoStack[0]);
                }
            }

            if (depth <= 1 || lastCompletedDepth == 0) {
                break;
            }
            if (bestScore <= alpha) {
                alpha -= ASPIRATION_WINDOW * 2;
            } else if (bestScore >= beta) {
                beta += ASPIRATION_WINDOW * 2;
            } else {
                break;
            }
            if (alpha <= -INFINITY / 4 || beta >= INFINITY / 4) {
                break;
            }
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

    private int minimaxAB(
            Board board, boolean isMaxNode, int alpha, int beta, int depth, int ply) {
        numExploredNodes++;
        if ((++nodesSinceTimeCheck & TIME_CHECK_MASK) == 0) {
            checkDeadline();
        }

        if (board.isKingEscaped() || board.isKingCaptured()) {
            return evaluateCached(board);
        }
        if (depth <= 0) {
            return evaluateCached(board);
        }

        long key = positionKey(board, isMaxNode);
        TranspositionTable.Entry cached = transpositionTable.get(key);
        if (cached != null && cached.depth >= depth) {
            if (cached.flag == TranspositionTable.EXACT) {
                return cached.score;
            }
            if (cached.flag == TranspositionTable.LOWER_BOUND) {
                alpha = Math.max(alpha, cached.score);
            } else {
                beta = Math.min(beta, cached.score);
            }
            if (alpha >= beta) {
                return cached.score;
            }
        }

        Mark sideToMove = isMaxNode ? maxPlayer : minPlayer;
        board.generateMoves(sideToMove, moveBuffer);
        if (moveBuffer.isEmpty()) {
            return 0;
        }
        orderMoves(board, moveBuffer, cached != null ? cached.bestMove : null, depth, ply);
        Move[] moves = moveBuffer.toArray(new Move[0]);

        int windowAlpha = alpha;
        int windowBeta = beta;
        Move bestMove = null;
        int bestScore;
        boolean firstChild = true;

        if (isMaxNode) {
            bestScore = -INFINITY;
            for (Move move : moves) {
                board.makeMove(move, undoStack[ply]);
                try {
                    int score;
                    if (firstChild) {
                        score = minimaxAB(board, false, alpha, beta, depth - 1, ply + 1);
                        firstChild = false;
                    } else {
                        score = minimaxAB(board, false, alpha, alpha + 1, depth - 1, ply + 1);
                        if (score <= alpha) {
                            score = minimaxAB(board, false, alpha, beta, depth - 1, ply + 1);
                        }
                    }
                    if (score > bestScore) {
                        bestScore = score;
                        bestMove = move;
                    }
                    alpha = Math.max(alpha, bestScore);
                    if (beta <= alpha) {
                        storeKiller(ply, move);
                        addHistory(move, depth);
                        break;
                    }
                } finally {
                    board.unmakeMove(undoStack[ply]);
                }
            }
        } else {
            bestScore = INFINITY;
            for (Move move : moves) {
                board.makeMove(move, undoStack[ply]);
                try {
                    int score;
                    if (firstChild) {
                        score = minimaxAB(board, true, alpha, beta, depth - 1, ply + 1);
                        firstChild = false;
                    } else {
                        score = minimaxAB(board, true, beta - 1, beta, depth - 1, ply + 1);
                        if (score >= beta) {
                            score = minimaxAB(board, true, alpha, beta, depth - 1, ply + 1);
                        }
                    }
                    if (score < bestScore) {
                        bestScore = score;
                        bestMove = move;
                    }
                    beta = Math.min(beta, bestScore);
                    if (beta <= alpha) {
                        storeKiller(ply, move);
                        addHistory(move, depth);
                        break;
                    }
                } finally {
                    board.unmakeMove(undoStack[ply]);
                }
            }
        }

        int flag = bestScore <= windowAlpha ? TranspositionTable.UPPER_BOUND
                : bestScore >= windowBeta ? TranspositionTable.LOWER_BOUND
                : TranspositionTable.EXACT;
        transpositionTable.store(key, depth, bestScore, flag, bestMove);
        return bestScore;
    }

    private long positionKey(Board board, boolean isMaxNode) {
        return isMaxNode
                ? board.getZobristHash()
                : board.getZobristHash() ^ Board.SIDE_TO_MOVE_KEY;
    }

    private int evaluateCached(Board board) {
        long boardKey = board.getZobristHash();
        Integer cached = transpositionTable.getEvaluation(boardKey);
        if (cached != null) {
            return cached;
        }
        int score = board.evaluate(maxPlayer);
        transpositionTable.storeEvaluation(boardKey, score);
        return score;
    }

    private void checkDeadline() {
        if (System.nanoTime() >= deadlineNanos) {
            throw new SearchTimeout();
        }
    }

    private void orderRootMoves(Board board, ArrayList<Move> moves, Move previousBest, int depth) {
        moves.sort((a, b) -> Integer.compare(moveScore(board, b, previousBest, depth, 0),
                moveScore(board, a, previousBest, depth, 0)));
    }

    private void orderMoves(Board board, ArrayList<Move> moves, Move ttMove, int depth, int ply) {
        moves.sort((a, b) -> Integer.compare(moveScore(board, b, ttMove, depth, ply),
                moveScore(board, a, ttMove, depth, ply)));
    }

    private int moveScore(Board board, Move move, Move preferred, int depth, int ply) {
        if (preferred != null && sameMove(move, preferred)) {
            return 1_000_000;
        }
        if (isLikelyCapture(board, move)) {
            return 500_000 + historyIndex(move);
        }
        Move[] killers = killerMoves[Math.min(ply, killerMoves.length - 1)];
        for (Move killer : killers) {
            if (killer != null && sameMove(move, killer)) {
                return 400_000;
            }
        }
        return historyScores[historyIndex(move)];
    }

    private static int historyIndex(Move move) {
        return (((move.getStartRow() * Board.SIZE + move.getStartColumn()) * Board.SIZE
                + move.getEndRow()) * Board.SIZE + move.getEndColumn());
    }

    private void storeKiller(int ply, Move move) {
        if (ply >= killerMoves.length) {
            return;
        }
        Move[] killers = killerMoves[ply];
        if (killers[0] != null && sameMove(killers[0], move)) {
            return;
        }
        killers[1] = killers[0];
        killers[0] = move;
    }

    private void addHistory(Move move, int depth) {
        int idx = historyIndex(move);
        historyScores[idx] += depth * depth;
        if (historyScores[idx] > 1_000_000) {
            for (int i = 0; i < historyScores.length; i++) {
                historyScores[i] >>= 1;
            }
        }
    }

    private boolean isLikelyCapture(Board board, Move move) {
        Mark piece = board.getCell(move.getStartRow(), move.getStartColumn());
        int row = move.getEndRow();
        int col = move.getEndColumn();

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
