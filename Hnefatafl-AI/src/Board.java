import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

public class Board {

    private static final int[] VALID_VALUES_FOR_PIECES = {0, 2, 4, 5};
    public static final int SIZE = 13;
    private static final int CENTER = SIZE / 2;
    private static final int MAX_RED = 24;
    private static final int MAX_BLACK = 12;

    private static final long[][][] ZOBRIST_KEYS = buildZobristKeys();
    public static final long SIDE_TO_MOVE_KEY = new Random(0x5EED_C0DEL).nextLong();

    private static final BoardEvaluator SHARED_EVALUATOR = new HeuristicEvaluator();

    private static long[][][] buildZobristKeys() {
        Random random = new Random(0x9E37_79B9_7F4A_7C15L);
        long[][][] keys = new long[SIZE][SIZE][Mark.values().length];
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                for (int piece = 0; piece < keys[row][col].length; piece++) {
                    keys[row][col][piece] = random.nextLong();
                }
            }
        }
        return keys;
    }

    /** État minimal pour annuler un coup sans recopier le plateau. */
    public static final class Undo {
        int startRow;
        int startCol;
        int endRow;
        int endCol;
        Mark movedPiece;
        Mark startReplacement;
        Mark endReplacement;
        int captureCount;
        final int[] capRow = new int[4];
        final int[] capCol = new int[4];
        final Mark[] capMark = new Mark[4];
    }

    private final Mark[][] board;
    private final BoardEvaluator evaluator;

    private long zobristHash;
    private int kingRow = -1;
    private int kingCol = -1;
    private int blackCount;
    private int redCount;

    private final int[] redRows = new int[MAX_RED];
    private final int[] redCols = new int[MAX_RED];
    private int redListSize;
    private final int[] blackRows = new int[MAX_BLACK];
    private final int[] blackCols = new int[MAX_BLACK];
    private int blackListSize;

    public Board() {
        this.evaluator = SHARED_EVALUATOR;
        board = new Mark[SIZE][SIZE];
        fillWithEmptySquares();
        rebuildIncrementalState();
    }

    public Board(String initialBoard) {
        this.evaluator = SHARED_EVALUATOR;
        board = new Mark[SIZE][SIZE];
        fillWithEmptySquares();

        for (int i = 0, cpt = 0; i < initialBoard.length(); i++) {
            int col = cpt % SIZE;
            int row = cpt / SIZE;
            int convertedValue = initialBoard.charAt(i) - '0';

            if (isSpecialSquare(row, col)
                    && !Converter.pieceValueAsString(convertedValue).equals("K")) {
                board[row][col] = Mark.SPECIAL;
                cpt++;
                continue;
            }

            if (isAllowedPieceValue(convertedValue)) {
                board[row][col] = Converter.pieceValueAsMark(convertedValue);
                cpt++;
            }
        }

        rebuildIncrementalState();
    }

    private Board(Board other) {
        this.evaluator = SHARED_EVALUATOR;
        board = new Mark[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++) {
            System.arraycopy(other.board[i], 0, board[i], 0, SIZE);
        }
        zobristHash = other.zobristHash;
        kingRow = other.kingRow;
        kingCol = other.kingCol;
        blackCount = other.blackCount;
        redCount = other.redCount;
        redListSize = other.redListSize;
        blackListSize = other.blackListSize;
        System.arraycopy(other.redRows, 0, redRows, 0, redListSize);
        System.arraycopy(other.redCols, 0, redCols, 0, redListSize);
        System.arraycopy(other.blackRows, 0, blackRows, 0, blackListSize);
        System.arraycopy(other.blackCols, 0, blackCols, 0, blackListSize);
    }

    private void fillWithEmptySquares() {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (isSpecialSquare(i, j)) {
                    board[i][j] = Mark.SPECIAL;
                } else {
                    board[i][j] = Mark.EMPTY;
                }
            }
        }
    }

    private void rebuildIncrementalState() {
        redListSize = 0;
        blackListSize = 0;
        blackCount = 0;
        redCount = 0;
        kingRow = -1;
        kingCol = -1;
        zobristHash = 0L;

        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                Mark mark = board[row][col];
                zobristHash ^= ZOBRIST_KEYS[row][col][mark.ordinal()];
                trackPieceAdded(mark, row, col);
            }
        }
    }

    public long getZobristHash() {
        return zobristHash;
    }

    public int getBlackCount() {
        return blackCount;
    }

    public int getRedCount() {
        return redCount;
    }

    private void setCell(int row, int col, Mark mark) {
        Mark previous = board[row][col];
        if (previous == mark) {
            return;
        }

        zobristHash ^= ZOBRIST_KEYS[row][col][previous.ordinal()];
        trackPieceRemoved(previous, row, col);

        board[row][col] = mark;

        trackPieceAdded(mark, row, col);
        zobristHash ^= ZOBRIST_KEYS[row][col][mark.ordinal()];
    }

    private void trackPieceRemoved(Mark piece, int row, int col) {
        if (piece == Mark.KING) {
            kingRow = -1;
            kingCol = -1;
        } else if (piece == Mark.BLACK) {
            blackCount--;
            removeFromList(blackRows, blackCols, blackListSize, row, col);
            blackListSize--;
        } else if (piece == Mark.RED) {
            redCount--;
            removeFromList(redRows, redCols, redListSize, row, col);
            redListSize--;
        }
    }

    private void trackPieceAdded(Mark piece, int row, int col) {
        if (piece == Mark.KING) {
            kingRow = row;
            kingCol = col;
        } else if (piece == Mark.BLACK) {
            blackCount++;
            blackRows[blackListSize] = row;
            blackCols[blackListSize] = col;
            blackListSize++;
        } else if (piece == Mark.RED) {
            redCount++;
            redRows[redListSize] = row;
            redCols[redListSize] = col;
            redListSize++;
        }
    }

    private static void removeFromList(int[] rows, int[] cols, int size, int row, int col) {
        for (int i = 0; i < size; i++) {
            if (rows[i] == row && cols[i] == col) {
                rows[i] = rows[size - 1];
                cols[i] = cols[size - 1];
                return;
            }
        }
    }

    private static boolean isAllowedPieceValue(int value) {
        for (int allowedValue : VALID_VALUES_FOR_PIECES) {
            if (allowedValue == value) {
                return true;
            }
        }
        return false;
    }

    public void play(Move move) {
        makeMove(move, null);
    }

    public void makeMove(Move move, Undo undo) {
        int startRow = move.getStartRow();
        int startCol = move.getStartColumn();
        int endRow = move.getEndRow();
        int endCol = move.getEndColumn();

        Mark movedPiece = board[startRow][startCol];
        Mark startReplacement = isSpecialSquare(startRow, startCol) ? Mark.SPECIAL : Mark.EMPTY;
        Mark endReplacement = board[endRow][endCol];

        if (undo != null) {
            undo.startRow = startRow;
            undo.startCol = startCol;
            undo.endRow = endRow;
            undo.endCol = endCol;
            undo.movedPiece = movedPiece;
            undo.startReplacement = startReplacement;
            undo.endReplacement = endReplacement;
            undo.captureCount = 0;
        }

        setCell(startRow, startCol, startReplacement);
        setCell(endRow, endCol, movedPiece);
        applyCaptures(endRow, endCol, movedPiece, undo);
    }

    public void unmakeMove(Undo undo) {
        for (int i = undo.captureCount - 1; i >= 0; i--) {
            setCell(undo.capRow[i], undo.capCol[i], undo.capMark[i]);
        }
        setCell(undo.endRow, undo.endCol, undo.endReplacement);
        setCell(undo.startRow, undo.startCol, undo.movedPiece);
    }

    private void applyCaptures(int attackerEndRow, int attackerEndCol, Mark movedPiece, Undo undo) {
        for (int[] dir : DIRECTIONS) {
            int targetRow = attackerEndRow + dir[0];
            int targetCol = attackerEndCol + dir[1];
            if (!isInBoard(targetRow, targetCol)) {
                continue;
            }
            Mark neighbour = board[targetRow][targetCol];
            if (!isOpponent(movedPiece, neighbour)) {
                continue;
            }
            if (isCaptured(targetRow, targetCol, dir[0], dir[1])) {
                if (undo != null && undo.captureCount < undo.capMark.length) {
                    int idx = undo.captureCount++;
                    undo.capRow[idx] = targetRow;
                    undo.capCol[idx] = targetCol;
                    undo.capMark[idx] = neighbour;
                }
                setCell(targetRow, targetCol, Mark.EMPTY);
            }
        }
    }

    public static boolean isSpecialSquare(int row, int col) {
        return isThrone(row, col) || isCorner(row, col);
    }

    public static boolean isThrone(int row, int col) {
        return row == CENTER && col == CENTER;
    }

    public static boolean isCorner(int row, int col) {
        return (row == 0 || row == SIZE - 1) && (col == 0 || col == SIZE - 1);
    }

    private boolean isCaptured(int targetRow, int targetCol, int targetRowDirFromAtk, int targetColDirFromAtk) {
        Mark attacked = board[targetRow][targetCol];
        if (attacked == Mark.KING) {
            return isKingSandwiched(targetRow, targetCol);
        }
        if (attacked != Mark.RED && attacked != Mark.BLACK) {
            return false;
        }
        if (targetRowDirFromAtk != 0) {
            return isCaptureSideOk(targetRow - 1, targetCol, attacked)
                    && isCaptureSideOk(targetRow + 1, targetCol, attacked);
        }
        return isCaptureSideOk(targetRow, targetCol - 1, attacked)
                && isCaptureSideOk(targetRow, targetCol + 1, attacked);
    }

    private boolean isKingSandwiched(int row, int col) {
        for (int[] dir : DIRECTIONS) {
            int sideRow = row + dir[0];
            int sideCol = col + dir[1];
            if (!isInBoard(sideRow, sideCol)) {
                continue;
            }
            Mark side = board[sideRow][sideCol];
            if (side != Mark.RED && side != Mark.SPECIAL) {
                return false;
            }
        }
        return true;
    }

    /** Même règle que SubBoard.isCaptured pour une case voisine du capturé. */
    private boolean isCaptureSideOk(int row, int col, Mark attacked) {
        if (!isInBoard(row, col)) {
            return false;
        }
        Mark side = board[row][col];
        return isOpponent(side, attacked)
                || side == Mark.SPECIAL
                || isThrone(row, col);
    }

    public ArrayList<Move> getPossibleMoves(Mark player) {
        ArrayList<Move> moves = new ArrayList<>();
        generateMoves(player, moves);
        return moves;
    }

    public void generateMoves(Mark player, ArrayList<Move> moves) {
        moves.clear();
        if (player == Mark.RED) {
            for (int i = 0; i < redListSize; i++) {
                addMovesForPiece(redRows[i], redCols[i], Mark.RED, moves);
            }
            return;
        }

        for (int i = 0; i < blackListSize; i++) {
            addMovesForPiece(blackRows[i], blackCols[i], Mark.BLACK, moves);
        }
        if (kingRow >= 0) {
            addMovesForPiece(kingRow, kingCol, Mark.KING, moves);
        }
    }

    public static boolean isOpponent(Mark self, Mark other) {
        return switch (self) {
            case RED -> other == Mark.KING || other == Mark.BLACK;
            case BLACK, KING -> other == Mark.RED;
            case EMPTY, SPECIAL, OUT -> false;
        };
    }

    private static final int[][] DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    private void addMovesForPiece(int row, int col, Mark piece, ArrayList<Move> moves) {
        boolean isKing = piece == Mark.KING;
        for (int[] dir : DIRECTIONS) {
            int r = row + dir[0];
            int c = col + dir[1];
            while (isInBoard(r, c) && canGoThrough(r, c)) {
                if (isSpecialSquare(r, c)) {
                    if (isKing) {
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

    private boolean canGoThrough(int row, int col) {
        Mark m = board[row][col];
        return m == Mark.EMPTY || m == Mark.SPECIAL;
    }

    public boolean isInBoard(int row, int col) {
        return row >= 0 && row < SIZE && col >= 0 && col < SIZE;
    }

    public Mark getCell(int row, int col) {
        if (!isInBoard(row, col)) {
            return Mark.OUT;
        }
        return board[row][col];
    }

    public int getKingRow() {
        return kingRow;
    }

    public int getKingColumn() {
        return kingCol;
    }

    public boolean isKingEscaped() {
        return kingRow >= 0 && isCorner(kingRow, kingCol);
    }

    public boolean isKingCaptured() {
        return kingRow < 0;
    }

    public int evaluate(Mark player) {
        return evaluator.evaluate(this, player);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                sb.append("[").append(Converter.pieceMarkAsString(board[i][j])).append("]");
            }
            sb.append(System.lineSeparator());
        }
        return sb.toString();
    }

    public String getBoardAsOneLineString(String mode) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                switch (mode) {
                    case "mark" -> sb.append(Converter.pieceMarkAsString(board[i][j]));
                    case "int" -> sb.append(Converter.pieceMarkAsInt(board[i][j]));
                    default -> sb.append("x");
                }
            }
        }
        return sb.toString();
    }

    public Board copy() {
        return new Board(this);
    }
}

/**
 * Wrapper legacy conservé pour compatibilité avec d'éventuels usages externes.
 */
class SubBoard implements Iterator<Mark> {

    public final static int SIZE = 3;

    private final Mark[][] ref;
    private final int startRow;
    private final int startCol;
    private final int initialRow;
    private final int initialCol;

    private int iterCpt = -1;
    private static final int[][] fullWatchSides = {{0, 1}, {1, 0}, {1, 2}, {2, 1}};
    private static final int[][] verticalSides = {{0, 1}, {2, 1}};
    private static final int[][] horizontalSides = {{1, 0}, {1, 2}};

    public enum WatchMode {
        FULL, VERTICAL, HORIZONTAL
    }

    WatchMode watchMode = WatchMode.FULL;

    public SubBoard(Mark[][] board, int row, int col) {
        this.ref = board;
        if (this.outOfBoard(row, col)) {
            throw new IllegalArgumentException("You can't create a subboard view from outside of the board");
        }
        this.startRow = row - 1;
        this.startCol = col - 1;
        this.initialRow = row;
        this.initialCol = col;
    }

    public Mark get(int row, int col) {
        if (this.outOfSubBoard(row, col)) {
            throw new IllegalArgumentException("You can't reach a value outside of the subboard");
        }
        return this.getValueOrOUT(row, col);
    }

    private Mark getValueOrOUT(int row, int col) {
        int absRow = row + this.startRow;
        int absCol = col + this.startCol;
        if (this.outOfBoard(row, col)) {
            return Mark.OUT;
        }
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
        if (this.outOfSubBoard(row, col)) {
            throw new IllegalArgumentException("You can't reach a value outside of the subboard");
        }
        if (this.outOfBoard(row, col)) {
            throw new IllegalArgumentException("You can't edit a subboard value that is outside of the board");
        }
        int absRow = row + this.startRow;
        int absCol = col + this.startCol;
        this.ref[absRow][absCol] = mark;
    }

    public boolean isCaptured(WatchMode watchMode) {
        Mark attakedPiece = this.get(1, 1);
        switch (attakedPiece) {
            case KING -> {
                for (int[] danger : fullWatchSides) {
                    if (this.get(danger[0], danger[1]) != Mark.RED
                            && this.get(danger[0], danger[1]) != Mark.OUT
                            && this.get(danger[0], danger[1]) != Mark.SPECIAL) {
                        return false;
                    }
                }
                return true;
            }
            case RED, BLACK -> {
                switch (watchMode) {
                    case FULL -> {
                        return false;
                    }
                    case VERTICAL -> {
                        for (int[] danger : verticalSides) {
                            if (!Board.isOpponent(this.get(danger[0], danger[1]), attakedPiece)
                                    && this.get(danger[0], danger[1]) != Mark.SPECIAL
                                    && !Board.isThrone(this.initialRow + danger[0] - 1,
                                    this.initialCol + danger[1] - 1)) {
                                return false;
                            }
                        }
                        return true;
                    }
                    case HORIZONTAL -> {
                        for (int[] danger : horizontalSides) {
                            if (!Board.isOpponent(this.get(danger[0], danger[1]), attakedPiece)
                                    && this.get(danger[0], danger[1]) != Mark.SPECIAL
                                    && !Board.isThrone(this.initialRow + danger[0] - 1,
                                    this.initialCol + danger[1] - 1)) {
                                return false;
                            }
                        }
                        return true;
                    }
                }
            }
            default -> {
            }
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                Mark piece = this.get(i, j);
                sb.append("[").append(Converter.pieceMarkAsString(piece)).append("]");
            }
            sb.append(System.lineSeparator());
        }
        return sb.toString();
    }

    @Override
    public boolean hasNext() {
        return switch (this.watchMode) {
            case FULL -> iterCpt < fullWatchSides.length - 1;
            case VERTICAL -> iterCpt < verticalSides.length - 1;
            case HORIZONTAL -> iterCpt < horizontalSides.length - 1;
        };
    }

    @Override
    public Mark next() {
        iterCpt++;
        int[] side = fullWatchSides[iterCpt];
        switch (this.watchMode) {
            case FULL -> side = fullWatchSides[iterCpt];
            case VERTICAL -> side = verticalSides[iterCpt];
            case HORIZONTAL -> side = horizontalSides[iterCpt];
        }
        return get(side[0], side[1]);
    }

    public int[] getCurrentOrientation() {
        int[] orientation = {fullWatchSides[iterCpt][0], fullWatchSides[iterCpt][1]};
        orientation[0]--;
        orientation[1]--;
        return orientation;
    }

    public void setWatchMode(WatchMode watchMode) {
        this.watchMode = watchMode;
        this.iterCpt = -1;
    }

    public static boolean isVertical(int[] pos) {
        for (int[] side : verticalSides) {
            if (pos[0] == side[0] && pos[1] == side[1]) {
                return true;
            }
        }
        return false;
    }

    public static boolean isHorizontal(int[] pos) {
        for (int[] side : horizontalSides) {
            if (pos[0] == side[0] && pos[1] == side[1]) {
                return true;
            }
        }
        return false;
    }
}
