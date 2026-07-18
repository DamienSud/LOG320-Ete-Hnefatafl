import java.util.Arrays;
import java.util.regex.*;

/**
 * Represent a move with it's starting end ending position with the local indexing standard.
 *
 */
public class Move {

    private final int startRow;
    private final int startColumn;
    private final int endRow;
    private final int endColumn;

    public final String[] displayModes = {"server", "local"};
    public String displayMode = displayModes[0];

    public Move(int startRow, int startCol, int endRow, int endCol, String mode){

        this.startColumn = startCol;
        this.endColumn = endCol;

        switch(mode) {
            case "local":
                    this.startRow = startRow;
                    this.endRow = endRow;
                break;

            case "server":
                    this.startRow = Converter.ConvertLineIndexServerToLocal(startRow);
                    this.endRow = Converter.ConvertLineIndexServerToLocal(endRow);
                break;

            default:
                throw new IllegalArgumentException("mode can only be 'local' or 'server'");
        }
    }

    public Move(int startRow, String startCol, int endRow, String endCol, String mode) {

        this.startColumn = Converter.ConvertColletterToLocalIndex(startCol);
        this.endColumn = Converter.ConvertColletterToLocalIndex(endCol);

        switch(mode) {
            case "local":
                this.startRow = startRow;
                this.endRow = endRow;
                break;

            case "server":
                this.startRow = Converter.ConvertLineIndexServerToLocal(startRow);
                this.endRow = Converter.ConvertLineIndexServerToLocal(endRow);
                break;

            default:
                throw new IllegalArgumentException("mode can only be 'local' or 'server'");
        }
    }

    public Move(String move) {

        java.util.regex.Matcher matcher = getMoveMatcherOrNull(move);
        if (matcher == null) throw new IllegalArgumentException("received incorrect move format");

        this.startColumn = Converter.ConvertColletterToLocalIndex(matcher.group(1));
        this.startRow = Converter.ConvertLineIndexServerToLocal(Integer.parseInt(matcher.group(2)));
        this.endColumn = Converter.ConvertColletterToLocalIndex(matcher.group(3));
        this.endRow = Converter.ConvertLineIndexServerToLocal(Integer.parseInt(matcher.group(4)));
    }

    /**
     * return a regex matcher for the move string or null if invalid format.
     *
     * @param move
     * @return
     */
    public static java.util.regex.Matcher getMoveMatcherOrNull(String move) {
        move = String.join("", move.trim().split(" ")); // remove empty spaces
        move = String.join("", move.split("-"));
        String regex = "^([A-M])([1-9]|1[0-3])([A-M])([1-9]|1[0-3])$";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(move);
        System.out.println(move);
        System.out.println(matcher);

        if(!matcher.matches()) return null;

        return matcher;
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

    public void setDisplayMode(String mode) {
        if(!Arrays.asList(this.displayModes).contains(mode))
            throw new IllegalArgumentException("incorrect display mode for a move object");

        this.displayMode = mode;
    }

    @Override
    public String toString() {

        return switch (this.displayMode) {
            case "local" -> String.format("%s(%s)%s-%s(%s)%s",
                    Converter.ConvertLocalIndexToColLetter(startColumn),
                    startColumn,
                    startRow,
                    Converter.ConvertLocalIndexToColLetter(endColumn),
                    endColumn,
                    endRow
            );
            case "server" -> String.format("%s%s-%s%s",
                    Converter.ConvertLocalIndexToColLetter(startColumn),
                    Converter.ConvertLineIndexLocalToServer(startRow),
                    Converter.ConvertLocalIndexToColLetter(endColumn),
                    Converter.ConvertLineIndexLocalToServer(endRow)
            );
            default -> throw new IllegalArgumentException("incorrect display mode for a move object");
        };
    }
}