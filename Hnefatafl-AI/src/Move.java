import java.util.Arrays;

public class Move {
    private final int startRow;
    private final int startColumn;
    private final int endRow;
    private final int endColumn;

    public Move(int startRow, int startCol, int EndRow, int EndCol){
        this.startColumn = startCol;
        this.endColumn = EndCol;
        this.startRow = startRow;
        this.endRow = EndRow;
    }

    public int getStartRow(){
        return this.startRow;
    }
    public int getStartColumn() {
        return this.startColumn;
    }
    public int getEndRow(){
        return this.endRow;
    }
    public int getEndColumn(){
        return this.endColumn;
    }

    public Move(String move) {
        move = move.replaceAll("\\s", "").replace("-", "");

        // Colonne de départ = 1 lettre
        this.startColumn = Converter.colLetterToLocalIndex(move.substring(0, 1));

        // On lit les chiffres jusqu'à la prochaine lettre pour la ligne de départ
        int i = 1;
        StringBuilder num = new StringBuilder();
        while (i < move.length() && Character.isDigit(move.charAt(i))) {
            num.append(move.charAt(i));
            i++;
        }
        this.startRow = Converter.reversedIndexForLocalBoard(Integer.parseInt(num.toString()));

        // Colonne d'arrivée
        this.endColumn = Converter.colLetterToLocalIndex(move.substring(i, i + 1));
        i++;

        // Ligne d'arrivée
        num = new StringBuilder();
        while (i < move.length() && Character.isDigit(move.charAt(i))) {
            num.append(move.charAt(i));
            i++;
        }
        this.endRow = Converter.reversedIndexForLocalBoard(Integer.parseInt(num.toString()));
    }

    @Override
    public String toString() {
        return Converter.indexToColLetter(startColumn)
                + Converter.reverseBoardRowIndex(startRow)
                + Converter.indexToColLetter(endColumn)
                + Converter.reverseBoardRowIndex(endRow);
    }
}