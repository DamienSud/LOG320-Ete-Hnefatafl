import java.util.ArrayList;
import java.util.Random;

/**
 * Tests de non-régression pour les optimisations du moteur.
 * Usage: javac -encoding UTF-8 -cp out -d out tests/ValidationHarness.java
 *        java -cp out ValidationHarness
 */
public class ValidationHarness {

    public static final String START =
            "0000444440000"
          + "0000004000000"
          + "0000000000000"
          + "0000000000000"
          + "4000002000004"
          + "4000022200004"
          + "4400225220044"
          + "4000022200004"
          + "4000002000004"
          + "0000000000000"
          + "0000000000000"
          + "0000004000000"
          + "0000444440000";

    private static final int[] GOLDEN_RED = {51_200};
    private static final int[] GOLDEN_BLACK = {8_800};

    public static void main(String[] args) {
        testKingTracking();
        testMakeUnmake();
        testCaptureRoundTrip();
        testEvalGolden();
        testFixedDepthScores(Mark.RED, 2, 51_200);
        testFixedDepthScores(Mark.BLACK, 2, 8_800);
        testDepthBudget();
        System.out.println("ValidationHarness: all checks passed");
    }

    private static void testKingTracking() {
        Board board = new Board(START);
        Random random = new Random(7);
        Mark side = Mark.RED;
        for (int i = 0; i < 300; i++) {
            verifyKing(board);
            if (board.isKingCaptured() || board.isKingEscaped()) {
                break;
            }
            ArrayList<Move> moves = board.getPossibleMoves(side);
            if (moves.isEmpty()) {
                break;
            }
            board.play(moves.get(random.nextInt(moves.size())));
            verifyKing(board);
            side = Converter.getOpponent(side);
        }
    }

    private static void verifyKing(Board board) {
        int expectedRow = -1;
        int expectedCol = -1;
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                if (board.getCell(row, col) == Mark.KING) {
                    expectedRow = row;
                    expectedCol = col;
                }
            }
        }
        if (board.getKingRow() != expectedRow || board.getKingColumn() != expectedCol) {
            throw new AssertionError("king tracking mismatch");
        }
        if (board.getBlackCount() < 0 || board.getRedCount() < 0) {
            throw new AssertionError("negative piece counts");
        }
    }

    private static void testMakeUnmake() {
        Board board = new Board(START);
        long hashBefore = board.getZobristHash();
        int blackBefore = board.getBlackCount();
        int redBefore = board.getRedCount();
        String boardBefore = board.getBoardAsOneLineString("int");

        Board.Undo undo = new Board.Undo();
        Move move = board.getPossibleMoves(Mark.RED).get(0);
        board.makeMove(move, undo);
        board.unmakeMove(undo);

        if (board.getZobristHash() != hashBefore) {
            throw new AssertionError("zobrist mismatch after unmake");
        }
        if (board.getBlackCount() != blackBefore || board.getRedCount() != redBefore) {
            throw new AssertionError("material mismatch after unmake");
        }
        if (!board.getBoardAsOneLineString("int").equals(boardBefore)) {
            throw new AssertionError("board mismatch after unmake");
        }
    }

    private static void testCaptureRoundTrip() {
        Board board = new Board(START);
        Random random = new Random(11);
        Mark side = Mark.RED;
        for (int i = 0; i < 500; i++) {
            long hash = board.getZobristHash();
            String snapshot = board.getBoardAsOneLineString("int");
            int black = board.getBlackCount();
            int red = board.getRedCount();

            ArrayList<Move> moves = board.getPossibleMoves(side);
            if (moves.isEmpty()) {
                break;
            }
            Move move = moves.get(random.nextInt(moves.size()));
            Board.Undo undo = new Board.Undo();
            board.makeMove(move, undo);
            board.unmakeMove(undo);

            if (board.getZobristHash() != hash
                    || board.getBlackCount() != black
                    || board.getRedCount() != red
                    || !board.getBoardAsOneLineString("int").equals(snapshot)) {
                throw new AssertionError("make/unmake failed after random plies");
            }

            board.play(move);
            if (board.isKingCaptured() || board.isKingEscaped()) {
                break;
            }
            side = Converter.getOpponent(side);
        }
    }

    private static void testEvalGolden() {
        Board board = new Board(START);
        int red = board.evaluate(Mark.RED);
        int black = board.evaluate(Mark.BLACK);
        if (red != GOLDEN_RED[0] && red != GOLDEN_RED[1]) {
            throw new AssertionError("unexpected RED eval: " + red);
        }
        if (black != GOLDEN_BLACK[0] && black != GOLDEN_BLACK[1]) {
            throw new AssertionError("unexpected BLACK eval: " + black);
        }
    }

    private static void testFixedDepthScores(Mark player, int depth, int expectedBest) {
        Board board = new Board(START);
        CPUPlayer cpu = new CPUPlayer(player);
        ArrayList<Move> bestMoves = cpu.getNextMoveAB(board, depth);
        int reference = Integer.MIN_VALUE;
        for (Move move : bestMoves) {
            reference = Math.max(reference, scoreAfterRoot(board, cpu, move, depth));
        }
        if (reference != expectedBest) {
            throw new AssertionError(player + " depth " + depth + " expected " + expectedBest + " got " + reference);
        }
    }

    private static int scoreAfterRoot(Board board, CPUPlayer cpu, Move move, int depth) {
        Board.Undo undo = new Board.Undo();
        board.makeMove(move, undo);
        int score = minimaxReference(board, cpu, false, depth - 1);
        board.unmakeMove(undo);
        return score;
    }

    private static int minimaxReference(Board board, CPUPlayer cpu, boolean isMax, int depth) {
        if (board.isKingEscaped() || board.isKingCaptured() || depth <= 0) {
            return board.evaluate(cpu.getMaxPlayer());
        }
        Mark side = isMax ? cpu.getMaxPlayer() : Converter.getOpponent(cpu.getMaxPlayer());
        ArrayList<Move> moves = board.getPossibleMoves(side);
        if (moves.isEmpty()) {
            return 0;
        }
        int best = isMax ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (Move move : moves) {
            Board.Undo undo = new Board.Undo();
            board.makeMove(move, undo);
            int score = minimaxReference(board, cpu, !isMax, depth - 1);
            board.unmakeMove(undo);
            best = isMax ? Math.max(best, score) : Math.min(best, score);
        }
        return best;
    }

    private static void testDepthBudget() {
        Board board = new Board(START);
        CPUPlayer red = new CPUPlayer(Mark.RED);
        CPUPlayer black = new CPUPlayer(Mark.BLACK);
        red.getBestMoveWithinMillis(board, 4_500, null);
        black.getBestMoveWithinMillis(board, 4_500, null);
        if (red.getLastCompletedDepth() < 3 || black.getLastCompletedDepth() < 4) {
            throw new AssertionError("depth regression: red=" + red.getLastCompletedDepth()
                    + " black=" + black.getLastCompletedDepth());
        }
    }
}
