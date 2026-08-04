public class Converter {
    /**
     * takes a col index (local indexing) and gives it's corresponding column letter
     *
     * @param index
     * @return
     */
    public static String ConvertLocalIndexToColLetter(int index) {
        return switch(index) {
            case 0 -> "A";
            case 1 -> "B";
            case 2 -> "C";
            case 3 -> "D";
            case 4 -> "E";
            case 5 -> "F";
            case 6 -> "G";
            case 7 -> "H";
            case 8 -> "I";
            case 9 -> "J";
            case 10 -> "K";
            case 11 -> "L";
            case 12 -> "M";
            default -> throw new IllegalArgumentException("invalid index");
        };
    }

    /**
     * Takes a col letter and gives it's corresponding local index
     *
     * @param letter
     * @return
     */
    public static int ConvertColletterToLocalIndex(String letter) {
        return switch(letter) {
            case "A" -> 0;
            case "B" -> 1;
            case "C" -> 2;
            case "D" -> 3;
            case "E" -> 4;
            case "F" -> 5;
            case "G" -> 6;
            case "H" -> 7;
            case "I" -> 8;
            case "J" -> 9;
            case "K" -> 10;
            case "L" -> 11;
            case "M" -> 12;
            default -> throw new IllegalArgumentException("invalid index");
        };
    }

    /**
     * Fait correspondre l'indexage des lignes du programme en local avec celui du serveur
     *
     * Le serveur considere la ligne 1 comme la derniere ligne (celle du bas) et la ligne 13 comme la premiere
     * (celle du haut), or, notre programme local indexe les lignes en partant de la premiere (celle du haut)
     * comme index 0 et la derniere ligne (celle du bas) comme index 12.
     *
     * @param index
     * @return
     */
    public static int ConvertLineIndexLocalToServer(int index) {
        if(index < 0 || index > Board.SIZE - 1) {
            throw new IndexOutOfBoundsException();
        }
        return Board.SIZE - index;
    }

    /**
     * Fait correspondre l'indexage des lignes du serveur avec celui du programme en local
     *
     * Le serveur considere la ligne 1 comme la derniere ligne (celle du bas) et la ligne 13 comme la premiere
     * (celle du haut), or, notre programme local indexe les lignes en partant de la premiere (celle du haut)
     * comme index 0 et la derniere ligne (celle du bas) comme index 12.
     * @param index
     * @return
     */
    public static int ConvertLineIndexServerToLocal(int index) {
        if(index < 1 || index > Board.SIZE) throw new IndexOutOfBoundsException();
        return Board.SIZE - index;
    }

    /**
     * compute the opponent of the given piece
     *
     * @param piece
     * @return
     */
    public static Mark getOpponent(Mark piece){
        return switch(piece){
            case EMPTY, SPECIAL, OUT -> Mark.EMPTY;
            case BLACK, KING -> Mark.RED;
            case RED -> Mark.BLACK;
        };
    }

    /**
     * Convert a int piece value into it's mark equivalence
     *
     * @param value
     * @return
     */
    public static Mark pieceValueAsMark(int value){
        return switch(value){
            case 0 -> Mark.EMPTY;
            case 2 -> Mark.BLACK;
            case 4 -> Mark.RED;
            case 5 -> Mark.KING;
            default -> throw new IllegalArgumentException("invalid value");
        };
    }

    /**
     * Convert a mark into it's one-letter string representation
     *
     * @param value
     * @return
     */
    public static String pieceMarkAsString(Mark value){

        return switch(value){
            case EMPTY -> "-";
            case BLACK -> "B";
            case RED -> "R";
            case KING -> "K";
            case SPECIAL -> "X";
            case OUT -> "O";
        };
    }

    /**
     * Convet a mark into it's int value
     *
     * @param value
     * @return
     */
    public static int pieceMarkAsInt(Mark value){
        return switch(value){
            case EMPTY, SPECIAL -> 0;
            case BLACK -> 2;
            case RED -> 4;
            case KING -> 5;
            case OUT -> -1;
        };
    }

    public static String pieceValueAsString(int value) {
        return pieceMarkAsString(pieceValueAsMark(value));
    }

    public static String watchSideAsString(SubBoard.WatchMode mode) {
        return switch(mode) {
            case FULL -> "FULL";
            case HORIZONTAL ->  "HORIZONTAL";
            case VERTICAL ->  "VERTICAL";
        };
    }
}
