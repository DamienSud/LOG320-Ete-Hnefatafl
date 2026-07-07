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

        /* IMPORTANT ICI : gerer le cas ou on entoure un pion adverse ou le roi */
        checkCaptures(move.getEndRow(), move.getEndColumn(), piece);
    }

    private boolean isSpecialSquare(int row, int col) {
        // 4 coins
        boolean corner = (row == 0 || row == SIZE - 1) && (col == 0 || col == SIZE - 1);
        // case centrale (trône)
        boolean center = (row == CENTER && col == CENTER);
        return corner || center;
    }

    /**
     * Vérifie et applique les captures déclenchées par la pièce arrivée en (row, col).
     */
    private void checkCaptures(int row, int col, Mark movedPiece) {
        int[][] directions = { {-1, 0}, {1, 0}, {0, -1}, {0, 1} };

        for (int[] dir : directions) {
            int adjRow = row + dir[0];
            int adjCol = col + dir[1];

            if (!isInBoard(adjRow, adjCol)) continue;

            Mark target = board[adjRow][adjCol];
            if (!isEnemy(movedPiece, target)) continue;

            if (target == Mark.KING) {
                if (isKingCaptured(adjRow, adjCol)) {
                    board[adjRow][adjCol] = Mark.EMPTY;
                    // gérer la fin de partie ici si besoin
                }
            } else {
                // Pion classique : on regarde la case DERRIÈRE la victime
                int behindRow = adjRow + dir[0];
                int behindCol = adjCol + dir[1];

                if (isHostileTo(movedPiece, behindRow, behindCol)) {
                    board[adjRow][adjCol] = Mark.EMPTY;
                }
            }
        }
    }

    /** Le roi est capturé si ses 4 côtés sont hostiles (ennemi OU mur/case spéciale). */
    private boolean isKingCaptured(int kingRow, int kingCol) {
        int[][] directions = { {-1, 0}, {1, 0}, {0, -1}, {0, 1} };
        // Point de vue de l'ennemi du roi : les RED (attaquants)
        Mark kingEnemy = Mark.RED;

        for (int[] dir : directions) {
            int r = kingRow + dir[0];
            int c = kingCol + dir[1];
            if (!isHostileTo(Mark.KING, r, c)) return false; // ce côté n'est pas hostile
        }
        return true;
    }

    /**
     * La case (row, col) est-elle "hostile" à la pièce 'ally' ?
     * Hostile = mur (hors plateau), case spéciale (coin/centre), ou pièce ennemie.
     */
    private boolean isHostileTo(Mark ally, int row, int col) {
        // Un mur (hors plateau) est hostile
        if (!isInBoard(row, col)) return true;
        // Une case spéciale (coin/centre) est hostile
        if (isSpecialSquare(row, col)) return true;
        // Sinon, hostile si la pièce présente est un ennemi
        return isEnemy(ally, board[row][col]);
    }

    // ---- Classification des camps ----
    // RED = attaquants ; BLACK + KING = défenseurs

    private boolean isDefenderSide(Mark m) {
        return m == Mark.BLACK || m == Mark.KING;
    }

    private boolean isEnemy(Mark m1, Mark m2) {
        if (m1 == Mark.EMPTY || m2 == Mark.EMPTY) return false;
        return isDefenderSide(m1) != isDefenderSide(m2);
    }

    public boolean isInBoard(int row, int col) {
        return row < SIZE && row >= 0 && col < SIZE && col >= 0;
    }

    public ArrayList<Move> getPossibleMoves(Mark player) {
        ArrayList<Move> moves = new ArrayList<>();
        int[][] directions = { {-1, 0}, {1, 0}, {0, -1}, {0, 1} };

        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                Mark piece = board[row][col];
                if (!belongsTo(piece, player)) continue;

                for (int[] dir : directions) {
                    int r = row + dir[0];
                    int c = col + dir[1];
                    // On avance tant que la case est vide
                    while (isInBoard(r, c) && board[r][c] == Mark.EMPTY) {
                        // Seul le roi peut s'arrêter sur une case spéciale
                        if (isSpecialSquare(r, c) && piece != Mark.KING) {
                            r += dir[0];
                            c += dir[1];
                            continue; // on peut traverser mais pas s'arrêter
                        }
                        moves.add(new Move(row, col, r, c));
                        r += dir[0];
                        c += dir[1];
                    }
                }
            }
        }
        return moves;
    }

    /** La pièce appartient-elle au joueur donné ? (le roi appartient au camp BLACK) */
    private boolean belongsTo(Mark piece, Mark player) {
        if (piece == Mark.EMPTY) return false;
        if (player == Mark.BLACK) return piece == Mark.BLACK || piece == Mark.KING;
        if (player == Mark.RED)   return piece == Mark.RED;
        return false;
    }

    /**
     * Évaluation simple du point de vue de 'player'.
     *  +100 : victoire de player
     *  -100 : défaite de player
     *  sinon : différence de matériel
     */
    public int evaluate(Mark player) {
        // 1. Le roi est-il encore là ?
        int[] kingPos = findKing();
        boolean kingAlive = (kingPos != null);

        // 2. Le roi a-t-il atteint un coin (victoire des défenseurs) ?
        boolean kingEscaped = kingAlive && isCorner(kingPos[0], kingPos[1]);

        // Conditions terminales
        if (!kingAlive) {
            // roi capturé -> victoire des RED
            return (player == Mark.RED) ? 100 : -100;
        }
        if (kingEscaped) {
            // roi évadé -> victoire des BLACK
            return (player == Mark.BLACK) ? 100 : -100;
        }

        // 3. Sinon : évaluation heuristique = différence de pions
        int red = 0, black = 0;
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (board[i][j] == Mark.RED) red++;
                else if (board[i][j] == Mark.BLACK) black++;
            }
        }

        int score = black - red; // positif = avantage défenseurs
        return (player == Mark.BLACK) ? score : -score;
    }

    private int[] findKing() {
        for (int i = 0; i < SIZE; i++)
            for (int j = 0; j < SIZE; j++)
                if (board[i][j] == Mark.KING) return new int[]{i, j};
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
