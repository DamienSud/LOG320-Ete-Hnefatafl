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

    private final BoardEvaluator evaluator;

    public Board(){
        this.evaluator = new HeuristicEvaluator();
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
        this.evaluator = new HeuristicEvaluator();
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
        int attackerEndRow = move.getEndRow(), attackerEndCol = move.getEndColumn();

        SubBoard sub = new SubBoard(this.board, attackerEndRow, attackerEndCol);
        sub.setWatchMode(SubBoard.WatchMode.FULL);

        // check on the four side of the moved piece
        while(sub.hasNext()) {
            Mark piece = sub.next();
            int[] direction = sub.getCurrentOrientation();
            int dirRow = direction[0], dirCol = direction[1];

            if (Board.isOpponent(movedPiece, piece)) {
                int targetAbsPosRow = attackerEndRow + dirRow, targetAbsPosCol = attackerEndCol + dirCol;
                //System.out.printf("opponent absolute position : (%d, %d)\n\n", targetAbsPosRow, targetAbsPosCol);

                if(this.isCaptured(
                        targetAbsPosRow, targetAbsPosCol, dirRow, dirCol))
                    this.board[targetAbsPosRow][targetAbsPosCol] = Mark.EMPTY;
            }
        }
    }

    public static boolean isSpecialSquare(int row, int col) { return isThrone(row, col) || isCorner(row, col); }

    public static boolean isThrone(int row, int col) { return row == CENTER && col == CENTER; }

    public static boolean isCorner(int row, int col) { return (row == 0 || row == SIZE - 1) && (col == 0 || col == SIZE - 1); }

    private boolean isCaptured(int targetRow, int targetCol, int targetRowDirFromAtk, int targetColDirFromAtk) {
        //System.out.printf("attacking (%d, %d)\n", targetRow, targetCol);
        SubBoard sub = new SubBoard(this.board, targetRow, targetCol);

        int[] side = {targetRowDirFromAtk + 1, targetColDirFromAtk + 1};
        //System.out.printf("target's side : (%d, %d)\n\n", side[0], side[1]);

        SubBoard.WatchMode watchmode = SubBoard.isVertical(side) ?
                SubBoard.WatchMode.VERTICAL :
                (SubBoard.isHorizontal(side) ?
                SubBoard.WatchMode.HORIZONTAL : null);

        if(watchmode == null) throw new IllegalStateException("watchmode cannot be null");

        return sub.isCaptured(watchmode);
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

        //System.out.printf("%d moves found.\n", moves.size());
        return moves;
    }

    public static boolean isOpponent(Mark self, Mark other) {
        return switch(self) {
            case RED -> other == Mark.KING || other == Mark.BLACK;
            case BLACK, KING -> other == Mark.RED;
            case EMPTY, SPECIAL, OUT -> false;
        };
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

    public Mark getCell(int row, int col) {
        if(!isInBoard(row, col)) return Mark.OUT;
        return board[row][col];
    }

    public boolean isKingEscaped() {
        for(int row = 0; row < SIZE; row++)
            for(int col = 0; col < SIZE; col++)
                if(board[row][col] == Mark.KING && isCorner(row, col)) return true;
        return false;
    }

    public boolean isKingCaptured() {
        for(int row = 0; row < SIZE; row++)
            for(int col = 0; col < SIZE; col++)
                if(board[row][col] == Mark.KING) return false;
        return true;
    }

    public int evaluate(Mark player) {
        return this.evaluator.evaluate(this, player);
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

    private final int initialRow;
    private final int initialCol;

    /* following are for iterator's method */
    private int iterCpt = -1;
    private static final int[][] fullWatchSides = {{0, 1}, {1, 0}, {1, 2}, {2, 1}};
    private static final int[][] verticalSides = {{0, 1}, {2, 1}};
    private static final int[][] horizontalSides = {{1, 0}, {1, 2}};

    public enum WatchMode {
        FULL, // 4 sides
        VERTICAL, // above and below
        HORIZONTAL // left and right
    }

    WatchMode watchMode = WatchMode.FULL;
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

        this.initialRow = row;
        this.initialCol = col;
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

    // TODO
    public boolean isCaptured(WatchMode watchMode) {
        Mark attakedPiece = this.get(1, 1);

        switch(attakedPiece) {
            case KING -> {
                //System.out.println("Checking for king capture...\n");
                for(int[] danger : fullWatchSides) {
                    if(
                            this.get(danger[0], danger[1]) != Mark.RED
                            && this.get(danger[0], danger[1]) != Mark.OUT
                            && this.get(danger[0], danger[1]) != Mark.SPECIAL
                    ) return false;
                }
                return true;
            }
            case RED, BLACK -> {
                // can be captured by 2 enemies or SPECIAL slot
                switch(watchMode) {
                    case FULL -> { return false; } // normal piece are not compatible with a full capture
                    case VERTICAL -> {
                        //System.out.printf("Checking for %s vertical capture...\n\n", Converter.pieceMarkAsString(attakedPiece));
                        for(int[] danger : verticalSides) {
                            if(
                                    !Board.isOpponent(this.get(danger[0], danger[1]), attakedPiece)
                                    && this.get(danger[0], danger[1]) != Mark.SPECIAL
                                    && !Board.isThrone(this.initialRow + danger[0] - 1, this.initialCol + danger[1] - 1)
                            ) return false;
                        }
                        //System.out.printf("%s is verticaly captured...\n\n", Converter.pieceMarkAsString(attakedPiece));
                        return true;
                    }
                    case HORIZONTAL -> {
                        //System.out.printf("Checking for %s horizontal capture...\n\n", Converter.pieceMarkAsString(attakedPiece));
                        for(int[] danger : horizontalSides) {
                            if(
                                    !Board.isOpponent(this.get(danger[0], danger[1]), attakedPiece)
                                    && this.get(danger[0], danger[1]) != Mark.SPECIAL
                                    && !Board.isThrone(this.initialRow + danger[0] - 1, this.initialCol + danger[1] - 1)
                            ) return false;
                        }
                        //System.out.printf("%s is horizontaly captured...\n\n", Converter.pieceMarkAsString(attakedPiece));
                        return true;
                    }
                };
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
        return switch(this.watchMode) {
            case FULL -> iterCpt < fullWatchSides.length - 1;
            case VERTICAL -> iterCpt < verticalSides.length - 1;
            case HORIZONTAL -> iterCpt < horizontalSides.length - 1;
        };
    }

    /**
     * iterate over every sides of the watchSide mode chosed (strictly above, strict left side, strict right side and strictly below)
     * @return
     */
    @Override
    public Mark next() {
        iterCpt++;
        int[] side = fullWatchSides[iterCpt]; // DEFAULT
        switch(this.watchMode) {
            case FULL -> side = fullWatchSides[iterCpt];
            case VERTICAL -> side = verticalSides[iterCpt];
            case HORIZONTAL -> side = horizontalSides[iterCpt];
        }
        return get(side[0], side[1]);
    }

    /**
     * return the current watched side as an orientation relative to the center of the subboard
     * @return
     */
    public int[] getCurrentOrientation() {
        int[] orientation = {fullWatchSides[iterCpt][0],  fullWatchSides[iterCpt][1]};
        orientation[0]--;
        orientation[1]--;
        return orientation;
    }

    public void setWatchMode(WatchMode watchMode) {
        this.watchMode = watchMode;
        this.iterCpt = -1;
    }

    public static boolean isVertical(int[] pos) {
        for(int[] side: verticalSides) {
            if(pos[0] == side[0] && pos[1] == side[1]) return true;
        }
        return false;
    }

    public static boolean isHorizontal(int[] pos) {
        for(int[] side: horizontalSides) {
            if(pos[0] == side[0] && pos[1] == side[1]) return true;
        }
        return false;
    }
}