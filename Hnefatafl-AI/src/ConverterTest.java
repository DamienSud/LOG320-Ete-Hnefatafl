public class ConverterTest {

    static int passed = 0, failed = 0;

    public static void main(String[] args) {
        testColRoundTrip();
        testRowRoundTrip();
        testRowBounds();
        testMoveRoundTrip();
        System.out.println("\n=== " + passed + " passed, " + failed + " failed ===");
    }

    // --- Colonnes : local -> lettre -> local ---
    static void testColRoundTrip() {
        for (int i = 0; i < Board.SIZE; i++) {
            String letter = Converter.indexToColLetter(i);
            int back = Converter.colLetterToLocalIndex(letter);
            check("Col round-trip idx=" + i, i == back);
        }
    }

    // --- Lignes : local -> serveur -> local ---
    static void testRowRoundTrip() {
        for (int local = 0; local < Board.SIZE; local++) {
            int server = Converter.reverseBoardRowIndex(local);
            int back = Converter.reversedIndexForLocalBoard(server);
            check("Row round-trip local=" + local + " (server=" + server + ")", local == back);
        }
    }

    // --- Bornes : le serveur envoie 1..13, on ne doit jamais throw ---
    static void testRowBounds() {
        try {
            Converter.reversedIndexForLocalBoard(1);   // ligne serveur min
            Converter.reversedIndexForLocalBoard(13);  // ligne serveur max
            check("Row bounds 1..13 acceptées", true);
        } catch (IndexOutOfBoundsException e) {
            check("Row bounds 1..13 acceptées", false);
        }
    }

    // --- Move complet : string serveur -> Move -> string serveur ---
    static void testMoveRoundTrip() {
        String[] samples = { "G12L12", "A1A2", "M13A1", "D6D5", "G2 - H2", "D6 - D5" };
        for (String s : samples) {
            Move m = new Move(s);
            String back = m.toString();
            // Move.toString() produit toujours la forme canonique sans espaces ni tiret.
            // On compare donc à l'entrée normalisée de la même manière.
            String expected = s.replaceAll("\\s", "").replace("-", "");
            check("Move round-trip '" + s + "' -> '" + back + "' (attendu '" + expected + "')",
                    expected.equals(back));
        }
    }

    static void check(String label, boolean condition) {
        if (condition) { passed++; System.out.println("[OK]   " + label); }
        else           { failed++; System.out.println("[FAIL] " + label); }
    }
}