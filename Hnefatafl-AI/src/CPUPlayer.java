import java.util.ArrayList;

public class CPUPlayer {
    private int numExploredNodes;
    private Mark maxPlayer;
    private Mark minPlayer;

    public CPUPlayer(Mark cpu){

        this.numExploredNodes = 0;
        this.maxPlayer = cpu;
        this.minPlayer = Converter.getOpponent(cpu);
    }

    public int  getNumOfExploredNodes(){
        return numExploredNodes;
    }

    public ArrayList<Move> getNextMoveAB(Board board, int depth){
        resetNumExploredNodes();
        ArrayList<Move> bestMoves = new ArrayList<>();
        int bestScore = Integer.MIN_VALUE;
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

        for (Move move : board.getPossibleMoves(maxPlayer)) {
            Board boardCopy = board.copy();
            boardCopy.play(move);

            int score = minimaxAB(boardCopy, false, alpha, beta, depth - 1);

            if (score > bestScore) {
                bestScore = score;
                bestMoves.clear();
                bestMoves.add(move);
            } else if (score == bestScore) {
                bestMoves.add(move);
            }
        }
        return bestMoves;
    }

    private int minimaxAB(Board board, boolean isMaxNode, int alpha, int beta, int depth) {
        numExploredNodes++;

        int evaluate = board.evaluate(maxPlayer);

        if (Math.abs(evaluate) == 100 || depth == 0) {
            return evaluate;
        }

        if (isMaxNode) {
            int bestScore = Integer.MIN_VALUE;
            for (Move move : board.getPossibleMoves(maxPlayer)) {
                Board boardCopy = board.copy();
                boardCopy.play(move);
                int score = minimaxAB(boardCopy, false, alpha, beta, depth - 1);
                bestScore = Math.max(bestScore, score);
                alpha = Math.max(alpha, bestScore);
                if (beta <= alpha) break;
            }
            return bestScore;
        } else {
            int bestScore = Integer.MAX_VALUE;
            for (Move move : board.getPossibleMoves(minPlayer)) {
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

    public Mark getMaxPlayer() {
        return this.maxPlayer;
    }

    public void resetNumExploredNodes() {
        this.numExploredNodes = 0;
    }
}
