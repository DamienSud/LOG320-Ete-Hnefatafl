import java.util.ArrayList;

public class Board {

    /**
     * 0 is EMPTY,
     * 2 is BLACK,
     * 4 is RED,
     * 5 is the KING
     */
    private static final int[] VALID_VALUES_FOR_PIECES = {0, 2, 4, 5};
    public static final int SIZE = 13;

    private static final int CENTER = SIZE / 2;

    private final Mark[][] board;

    public Board(){
        board = new Mark[SIZE][SIZE];

        for(int i = 0; i < SIZE; i++)
            for(int j = 0; j < SIZE; j++)
                if(isSpecialSquare(i, j)){
                    board[i][j] = Mark.SPECIAL;
                } else {
                    board[i][j] = Mark.EMPTY;
                }
    }

    public Board(String initialBoard){

        board = new Mark[SIZE][SIZE];

        for(int i = 0, cpt = 0; i < initialBoard.length(); i++){

            int col = cpt % SIZE;
            int row = cpt / SIZE;

            int convertedValue = initialBoard.charAt(i) - '0';

            if(isSpecialSquare(row, col) && !Converter.pieceValueAsString(convertedValue).equals("K")){
                board[row][col] = Mark.SPECIAL;
                cpt++;
                continue;
            }

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

        // TODO : manage pieces captures
    }

    private boolean isSpecialSquare(int row, int col) {
        return (row == CENTER && col == CENTER) || isCorner(row, col);
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

        return false;
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
            for(int j = 0; j < SIZE; j++){
                sb.append('[').append(Converter.pieceMarkAsString(board[i][j])).append(']');
                System.out.println(i + " " + j);
            }

            sb.append(System.lineSeparator());
        }

        return sb.toString();
    }

    public String getBoardAsOneLineString(String mode) {
        StringBuilder sb = new StringBuilder();

        for(int i = 0; i < SIZE; i++){
            for(int j = 0; j < SIZE; j++)
                switch(mode) {
                    case "mark":
                        sb.append(Converter.pieceMarkAsString(board[i][j]));
                        break;
                    case "int":
                        sb.append(Converter.pieceMarkAsInt(board[i][j]));
                        break;
                    default:
                        sb.append("x"); // placeholder
                        break;
                }

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
