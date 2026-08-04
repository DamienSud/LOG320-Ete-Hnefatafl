/**
 * Table de transposition compacte : deux tableaux fixes indexés par hash,
 * sans objets Long/Entry ni vidage complet en cours de recherche.
 */
public class TranspositionTable {

    public static final int EXACT = 0;
    public static final int LOWER_BOUND = 1;
    public static final int UPPER_BOUND = 2;

    private static final int SEARCH_SIZE = 1 << 20;
    private static final int EVAL_SIZE = 1 << 19;
    private static final int SEARCH_MASK = SEARCH_SIZE - 1;
    private static final int EVAL_MASK = EVAL_SIZE - 1;

    public static final class Entry {
        public final int depth;
        public final int score;
        public final int flag;
        public final Move bestMove;

        Entry(int depth, int score, int flag, Move bestMove) {
            this.depth = depth;
            this.score = score;
            this.flag = flag;
            this.bestMove = bestMove;
        }
    }

    private final long[] searchKeys = new long[SEARCH_SIZE];
    private final int[] searchDepths = new int[SEARCH_SIZE];
    private final int[] searchScores = new int[SEARCH_SIZE];
    private final byte[] searchFlags = new byte[SEARCH_SIZE];
    private final int[] searchBestFromRow = new int[SEARCH_SIZE];
    private final int[] searchBestFromCol = new int[SEARCH_SIZE];
    private final int[] searchBestToRow = new int[SEARCH_SIZE];
    private final int[] searchBestToCol = new int[SEARCH_SIZE];
    private final byte[] searchHasBest = new byte[SEARCH_SIZE];

    private final long[] evalKeys = new long[EVAL_SIZE];
    private final int[] evalScores = new int[EVAL_SIZE];
    private final byte[] evalValid = new byte[EVAL_SIZE];

    private byte generation;
    private int searchHits;
    private int searchLookups;
    private int evaluationHits;
    private int evaluationLookups;

    public Entry get(long key) {
        searchLookups++;
        int slot = slotForSearch(key);
        if (searchKeys[slot] != key) {
            return null;
        }
        searchHits++;
        Move bestMove = null;
        if (searchHasBest[slot] != 0) {
            bestMove = new Move(
                    searchBestFromRow[slot],
                    searchBestFromCol[slot],
                    searchBestToRow[slot],
                    searchBestToCol[slot],
                    "local");
        }
        return new Entry(searchDepths[slot], searchScores[slot], searchFlags[slot], bestMove);
    }

    public void store(long key, int depth, int score, int flag, Move bestMove) {
        int slot = slotForSearch(key);
        if (searchKeys[slot] == key && searchDepths[slot] > depth) {
            return;
        }
        searchKeys[slot] = key;
        searchDepths[slot] = depth;
        searchScores[slot] = score;
        searchFlags[slot] = (byte) flag;
        if (bestMove != null) {
            searchHasBest[slot] = 1;
            searchBestFromRow[slot] = bestMove.getStartRow();
            searchBestFromCol[slot] = bestMove.getStartColumn();
            searchBestToRow[slot] = bestMove.getEndRow();
            searchBestToCol[slot] = bestMove.getEndColumn();
        } else {
            searchHasBest[slot] = 0;
        }
    }

    public Integer getEvaluation(long boardKey) {
        evaluationLookups++;
        int slot = slotForEval(boardKey);
        if (evalValid[slot] == 0 || evalKeys[slot] != boardKey) {
            return null;
        }
        evaluationHits++;
        return evalScores[slot];
    }

    public void storeEvaluation(long boardKey, int score) {
        int slot = slotForEval(boardKey);
        evalKeys[slot] = boardKey;
        evalScores[slot] = score;
        evalValid[slot] = generation;
    }

    public void clear() {
        java.util.Arrays.fill(searchKeys, 0L);
        java.util.Arrays.fill(searchHasBest, (byte) 0);
        java.util.Arrays.fill(evalValid, (byte) 0);
        generation++;
        resetStatistics();
    }

    public void resetStatistics() {
        searchHits = 0;
        searchLookups = 0;
        evaluationHits = 0;
        evaluationLookups = 0;
    }

    public int getSearchHits() {
        return searchHits;
    }

    public int getEvaluationHits() {
        return evaluationHits;
    }

    public int getSize() {
        return SEARCH_SIZE + EVAL_SIZE;
    }

    public int getHitRatePercent() {
        int lookups = searchLookups + evaluationLookups;
        if (lookups == 0) {
            return 0;
        }
        return (int) ((searchHits + evaluationHits) * 100L / lookups);
    }

    private static int slotForSearch(long key) {
        return (int) (key & SEARCH_MASK);
    }

    private static int slotForEval(long key) {
        return (int) (key & EVAL_MASK);
    }
}
