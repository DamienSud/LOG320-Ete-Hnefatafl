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
        // TODO 
    }

    @Override
    public String toString() {
        return Converter.indexToColLetter(startColumn)
                + Converter.reverseBoardRowIndex(startRow)
                + Converter.indexToColLetter(endColumn)
                + Converter.reverseBoardRowIndex(endRow);
    }
}