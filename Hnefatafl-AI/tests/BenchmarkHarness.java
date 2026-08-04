/**
 * Benchmark profondeur / nœuds pour la validation finale.
 */
public class BenchmarkHarness {

    public static void main(String[] args) {
        run(new Board(ValidationHarness.START), "opening");
    }

    private static void run(Board board, String label) {
        runSide(board, label, Mark.RED);
        runSide(board, label, Mark.BLACK);
    }

    private static void runSide(Board board, String label, Mark side) {
        CPUPlayer cpu = new CPUPlayer(side);
        long start = System.nanoTime();
        cpu.getBestMoveWithinMillis(board, 4_500, null);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        int nodes = cpu.getNumOfExploredNodes();
        double nps = nodes * 1000.0 / Math.max(1, elapsedMs);
        System.out.printf(
                "%s %s depth=%d nodes=%d nps=%.0f ttHit=%d%%%n",
                label,
                side,
                cpu.getLastCompletedDepth(),
                nodes,
                nps,
                cpu.getTranspositionTable().getHitRatePercent());
    }
}
