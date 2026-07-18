import java.util.ArrayList;
import java.util.Iterator;

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

    /**
     * this method suppose a valid move and does NOT check for validity (danger of desynchronizing with server)
     * @param move
     */
    public void play(Move move){
        Mark piece = board[move.getStartRow()][move.getStartColumn()];
        board[move.getStartRow()][move.getStartColumn()] = Mark.EMPTY;
        board[move.getEndRow()][move.getEndColumn()] = piece;

        this.checkCaptures(move, piece);
    }

    /**
     * Vérifie et applique les captures déclenchées par la pièce arrivée en (row, col).
     */
    private void checkCaptures(Move move, Mark movedPiece) {
        int endRow = move.getEndRow(), endCol = move.getEndColumn();
        Mark opponentPiece = Converter.getOpponent(movedPiece);

        SubBoard sub = new SubBoard(this.board, endRow, endCol);

        System.out.println("debug hasNext and next");

        while(sub.hasNext()) {
            int currRow = sub.getIterRow(), currCol = sub.getIterCol();

            Mark piece = sub.next();

            boolean isOpponent = piece == opponentPiece;
            boolean isSelf = (currRow == 1 && currCol == 1);

            String pieceAsStr = Converter.pieceMarkAsString(piece);

            if(isOpponent){
                System.out.printf("direction (%d, %d) : %s (opponent)\n", currRow - 1, currCol - 1, pieceAsStr);
            } else if (isSelf) {
                System.out.printf("direction (%d, %d) : %s (self)\n", 0, 0, pieceAsStr);
            } else {
                System.out.printf("direction (%d, %d) : %s\n", currRow - 1, currCol - 1, pieceAsStr);
            }
        }
    }

    private boolean isSpecialSquare(int row, int col) { return isThrone(row, col) || isCorner(row, col); }

    private boolean isThrone(int row, int col) { return row == CENTER && col == CENTER; }

    private boolean isCorner(int row, int col) { return (row == 0 || row == SIZE - 1) && (col == 0 || col == SIZE - 1); }

    public boolean isInBoard(int row, int col) {
        return row >= 0 && row < SIZE && col >= 0 && col < SIZE;
    }

    /** Le roi est capturé si ses 4 côtés sont hostiles (ennemi OU mur/case spéciale). */
    private boolean isKingCaptured(int kingRow, int kingCol) {

        return false;
    }

    /** Une piece classique est capturé si 2 de ses côtés sont hostiles (ennemi OU case spéciale). */
    private boolean isPieceCaptured(int kingRow, int kingCol) {

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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        for(int i = 0; i < SIZE; i++){
            for(int j = 0; j < SIZE; j++){
                sb.append('[').append(Converter.pieceMarkAsString(board[i][j])).append(']');
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
                        sb.append("x"); // placeholder, should never happend
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

/**
 * Wrapper to act on 3x3 sub array of a bigger board
 */
class SubBoard implements Iterator<Mark> {

    public final static int SIZE = 3;

    private final Mark[][] ref;
    private final int startRow; // row of the upper left corner
    private final int startCol; // col of the upper left corner

    /* following are for iterator's method */
    private int IterRow = 0;
    private int IterCol = 0;
    /* ----------------------------------- */

    public SubBoard(Mark[][] board, int row, int col) {
        this.ref = board;

        if (this.outOfBoard(row, col))
            throw new IllegalArgumentException("You can't create a subboard view from outside of the board");

        /**
         * Takes the upper left corner's coordinate
         */
        this.startRow = row - 1;
        this.startCol = col - 1;
    }

    public Mark get(int row, int col) {
        if(this.outOfSubBoard(row, col))
            throw new IllegalArgumentException("You can't reach a value outside of the subboard");

        return this.getValueOrOUT(row, col);
    }

    private Mark getValueOrOUT(int row, int col) {
        int absRow = row + this.startRow;
        int absCol = col + this.startCol;

        if(this.outOfBoard(row, col))
            return Mark.OUT;

        return ref[absRow][absCol];
    }

    public boolean outOfBoard(int row, int col) {
        int absRow = row + this.startRow;
        int absCol = col + this.startCol;

        return absRow >= this.ref.length || absCol >= this.ref[0].length || absRow < 0 || absCol < 0;
    }

    private boolean outOfSubBoard(int row, int col) {

        return row >= SIZE || col >= SIZE || row < 0 || col < 0;
    }

    public void set(int row, int col, Mark mark) {
        if(this.outOfSubBoard(row, col))
            throw new IllegalArgumentException("You can't reach a value outside of the subboard");

        if (this.outOfBoard(row, col))
            throw new IllegalArgumentException("You can't edit a subboard value that is outside of the board");

        int absRow = row + this.startRow;
        int absCol = col + this.startCol;

        this.ref[absRow][absCol] = mark;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        for(int i = 0; i < SIZE; i++){
            for(int j = 0; j < SIZE; j++){
                sb.append('[').append(Converter.pieceMarkAsString(this.get(i, j))).append(']');
            }

            sb.append(System.lineSeparator());
        }

        return sb.toString();
    }

    @Override
    public boolean hasNext() {
        return this.IterRow < SIZE &&  this.IterCol < SIZE;
    }

    @Override
    public Mark next() {
        int currX = this.IterRow, currY = this.IterCol;

        if(IterCol == SIZE - 1) {
            this.IterCol = 0;
            this.IterRow++;
        } else {
            this.IterCol++;
        }

        return get(currX, currY);
    }

    public void resetIterator() {
        this.IterRow = 0;
        this.IterCol = 0;
    }

    public int getIterRow() {
        return this.IterRow;
    }

    public int getIterCol() {
        return this.IterCol;
    }
}