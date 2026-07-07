public class Converter {
    /**
     * Il existe sans doute une maniere de faire ca plus proprement avec un one liner et le code des characteres mais on verra ca plus tard
     * @param index
     * @return
     */
    public static String indexToColLetter(int index) {
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
            default -> "";
        };
    }

    /**
     * Il existe sans doute une maniere de faire ca plus proprement avec un one liner et le code des characteres mais on verra ca plus tard
     * @param letter
     * @return
     */
    public static int colLetterToLocalIndex(String letter) {
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
            default -> -1;
        };
    }

    /**
     * Fait correspondre l'indexage des lignes du programme en local avec celui du serveur
     * @param index
     * @return
     */
    public static int reverseBoardRowIndex(int index) {
        if(index < 0 || index > Board.SIZE - 1) throw new IndexOutOfBoundsException();
        return Board.SIZE - index;
    }

    /**
     * Fait correspondre l'indexage des lignes du serveur avec celui du programme en local
     * @param index
     * @return
     */
    public static int reversedIndexForLocalBoard(int index) {
        if(index < 1 || index > Board.SIZE) throw new IndexOutOfBoundsException();
        return Board.SIZE - index;
    }

    public static Mark getOpponent(Mark piece){
        return switch(piece){
            case EMPTY -> Mark.EMPTY;
            case BLACK -> Mark.RED;
            case RED -> Mark.BLACK;
            case KING -> Mark.RED;
            default -> Mark.EMPTY;
        };
    }

    public static Mark pieceValueAsMark(int value){
        return switch(value){
            case 0 -> Mark.EMPTY;
            case 2 -> Mark.BLACK;
            case 4 -> Mark.RED;
            case 5 -> Mark.KING;
            default -> Mark.EMPTY;
        };
    }

    public static String pieceMarkAsString(Mark value){
        return switch(value){
            case EMPTY -> "-";
            case BLACK -> "B";
            case RED -> "R";
            default -> "-";
        };
    }
}
