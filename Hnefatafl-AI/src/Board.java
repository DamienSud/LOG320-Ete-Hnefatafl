import java.util.ArrayList;

public class Board {
    private static final int[] VALID_VALUES_FOR_PIECES = {0, 2, 4, 5};
    public static final int SIZE = 13;

    private static final int CENTER = SIZE / 2;

    private final Mark[][] board;

    public Board(){
        board = new Mark[SIZE][SIZE];

        for(int i = 0; i < SIZE; i++)
            for(int j = 0; j < SIZE; j++)
                board[i][j] = Mark.EMPTY;
    }

    public Board(String initialBoard){

        board = new Mark[SIZE][SIZE];

        /*
            i -> itere sur tous les charactere de la chaine par indice (int)
            cpt -> ne s'incremente que lorsqu'il rencontre un charactere representant une valeur de piece valide
        */
        for(int i = 0, cpt = 0; i < initialBoard.length(); i++){

            int col = cpt % SIZE;
            int row = cpt / SIZE;

            int convertedValue = initialBoard.charAt(i) - '0';

            if(isAllowedPieceValue(convertedValue)){
                board[row][col] = Converter.pieceValueAsMark(convertedValue);
                cpt++;
            }
        }
    }

    private static boolean isAllowedPieceValue(int value){
        for(int allowedValue: VALID_VALUES_FOR_PIECES)
            if(allowedValue == value) return true;
        return false;
    }

    public void play(Move move){
        Mark piece = board[move.getStartRow()][move.getStartColumn()];
        board[move.getStartRow()][move.getStartColumn()] = Mark.EMPTY;
        board[move.getEndRow()][move.getEndColumn()] = piece;

        // TODO : manage piece captures
    }

    private boolean isSpecialSquare(int row, int col) {
        // TODO 
        return false;
    }

    /**
     * Vérifie et applique les captures déclenchées par la pièce arrivée en (row, col).
     */
    private void checkCaptures(int row, int col, Mark movedPiece) {
        // TODO 
    }

    /** Le roi est capturé si ses 4 côtés sont hostiles (ennemi OU mur/case spéciale). */
    private boolean isKingCaptured(int kingRow, int kingCol) {
        // TODO 
    }

    public boolean isInBoard(int row, int col) {

        // TODO 

        return false;
    }

    public ArrayList<Move> getPossibleMoves(Mark player) {
        // TODO 

        ArrayList<Move> moves = new ArrayList<>();
        return moves;
    }

    public int evaluate(Mark player) {
        // TODO 
        
        return 0;
    }

    private int[] findKing() {
        // TODO 
        
        return null;
    }

    private boolean isCorner(int row, int col) {
        return (row == 0 || row == SIZE - 1) && (col == 0 || col == SIZE - 1);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        for(int i = 0; i < SIZE; i++){
            for(int j = 0; j < SIZE; j++)
                sb.append('[').append(Converter.pieceMarkAsString(board[i][j])).append(']');

            sb.append(System.lineSeparator());
        }

        return sb.toString();
    }

    // Copy profonde

    public Board copy() {
        Board newBoard = new Board();
        for (int i = 0; i < this.board.length; i++) System.arraycopy(this.board[i], 0, newBoard.board[i], 0, this.board[i].length);
        return newBoard;
    }
}
