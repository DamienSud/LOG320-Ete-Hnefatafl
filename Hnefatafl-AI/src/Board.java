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

    // TODO make private
    public final Mark[][] board;

    private int quantityRED;
    private int quantityBLACK;

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

            /* if the king is not on a special square, display a SPECIAL */
            if(isSpecialSquare(row, col) && !Converter.pieceValueAsString(convertedValue).equals("K")){
                board[row][col] = Mark.SPECIAL;
                cpt++;
                continue;
            }

            if(isAllowedPieceValue(convertedValue)){

                board[row][col] = Converter.pieceValueAsMark(convertedValue);

                switch(board[row][col]){
                    case BLACK, KING -> quantityBLACK++;
                    case RED -> quantityRED++;
                }

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
        if(this.isSpecialSquare(move.getStartRow(), move.getStartColumn())) {
            board[move.getStartRow()][move.getStartColumn()] = Mark.SPECIAL;
        } else {
            board[move.getStartRow()][move.getStartColumn()] = Mark.EMPTY;
        }
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

        System.out.println("local move : ");
        System.out.println(sub);

        while(sub.hasNext()) {
            int currIterRow = sub.getIterRow(), currIterCol = sub.getIterCol();
            int dirRow = currIterRow - 1, dirCol = currIterCol - 1;

            Mark piece = sub.next();

            String pieceAsStr = Converter.pieceMarkAsString(piece);

            if(piece == opponentPiece){
                System.out.printf("direction (%d, %d) : %s (opponent)\n", dirRow, dirCol, pieceAsStr);

                int[][] posistionsInReach = {{0, 1}, {1, 0}, {1, 2}, {2, 1}};

                for(int[] position : posistionsInReach) {
                    if(currIterRow == position[0] && currIterCol == position[1]){
                        System.out.printf("Checking capture for opponent piece on direction (%d, %d)\n", dirRow, dirCol);

                        // check capture of that opponent piece
                        int absRow = endRow + dirRow, absCol = endCol + dirCol;
                        if(this.isCaptured(absRow, absCol)) {
                            switch(board[absRow][absCol]){
                                case BLACK, KING -> quantityBLACK--;
                                case RED -> quantityRED--;
                            }
                            this.board[absRow][absCol] = Mark.EMPTY;
                        }
                    }
                }
            }
        }

        System.out.println("\n");
    }

    private boolean isSpecialSquare(int row, int col) { return isThrone(row, col) || isCorner(row, col); }

    private boolean isThrone(int row, int col) { return row == CENTER && col == CENTER; }

    private boolean isCorner(int row, int col) { return (row == 0 || row == SIZE - 1) && (col == 0 || col == SIZE - 1); }

    private boolean isCaptured(int row, int col) {
        SubBoard sub = new SubBoard(this.board, row, col);
        return sub.isCaptured();
    }

    public ArrayList<Move> getPossibleMoves(Mark player) {
        ArrayList<Move> moves = new ArrayList<>();

        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                Mark piece = board[row][col];
                if (belongsToPlayer(piece, player)) {
                    addMovesForPiece(row, col, piece, moves);
                }
            }
        }

        System.out.printf("%d moves found.\n", moves.size());
        return moves;
    }

    private boolean belongsToPlayer(Mark piece, Mark player) {
        return switch(player){
            case BLACK, KING -> piece == Mark.BLACK || piece == Mark.KING;
            case RED -> piece == Mark.RED;
            default -> false;
        };
    }

    private static final int[][] DIRECTIONS = {
            {-1, 0}, {1, 0}, {0, -1}, {0, 1}
    };

    private void addMovesForPiece(int row, int col, Mark piece, ArrayList<Move> moves) {
        boolean isKing = (piece == Mark.KING);

        for (int[] dir : DIRECTIONS) {
            int r = row + dir[0];
            int c = col + dir[1];

            while (isInBoard(r, c) && canGoThrough(r, c)) {
                if (isSpecialSquare(r, c)) {
                    if (isKing) { // only a KING can stop on a SPECIAL slot
                        moves.add(new Move(row, col, r, c, "local"));
                    }
                } else {
                    moves.add(new Move(row, col, r, c, "local"));
                }
                r += dir[0];
                c += dir[1];
            }
        }
    }

    /** EMPTY slot or unused SPECIAL slot (no king on it) */
    private boolean canGoThrough(int row, int col) {
        Mark m = board[row][col];
        return m == Mark.EMPTY || m == Mark.SPECIAL;
    }

    public boolean isInBoard(int row, int col) {
        return row >= 0 && row < SIZE && col >= 0 && col < SIZE;
    }

    public int evaluate(Mark player) {
        // TODO
        
        return 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        for(int i = 0; i < SIZE; i++){
            for(int j = 0; j < SIZE; j++){
                sb.append("[").append(Converter.pieceMarkAsString(board[i][j])).append("]");
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

    public boolean isCaptured() {
        Mark piece = this.get(1, 1);

        switch(piece) {
            case KING -> {
                System.out.println("Is king Captured?");

                int[][] dangers = {{0, 1}, {1, 0}, {1, 2}, {2, 1}};
                for(int[] danger : dangers) {
                    if(
                            this.get(danger[0], danger[1]) != Mark.RED
                            && this.get(danger[0], danger[1]) != Mark.OUT
                            && this.get(danger[0], danger[1]) != Mark.SPECIAL
                    ) return false;
                }
                return true;
            }
            case RED, BLACK -> {
                System.out.printf("Is %s Captured?\n", Converter.pieceMarkAsString(piece));
                Mark opponent = Converter.getOpponent(piece);
                boolean captured = true;

                int[][] vertical = {{0, 1}, {2, 1}};
                int[][] horizontal = {{1, 0}, {1, 2}};

                /* check for vertical capture */
                for(int[] danger : vertical) {
                    if(
                            this.get(danger[0], danger[1]) != opponent
                            && this.get(danger[0], danger[1]) != Mark.SPECIAL
                    ) captured = false;
                }

                if(captured) return true;

                captured = true;

                /* check for horizontal capture */
                for(int[] danger : horizontal) {
                    if(
                            this.get(danger[0], danger[1]) != opponent
                            && this.get(danger[0], danger[1]) != Mark.SPECIAL
                    ) captured = false;
                }

                if(captured) return true;
            }
        }

        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        for(int i = 0; i < SIZE; i++){
            for(int j = 0; j < SIZE; j++){
                Mark piece = this.get(i, j);
                sb.append("[").append(Converter.pieceMarkAsString(piece)).append("]");
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