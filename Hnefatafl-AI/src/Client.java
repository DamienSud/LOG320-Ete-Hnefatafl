import java.io.*;
import java.net.*;
import java.util.HashSet;
import java.util.Set;

class Client {

    /** Trace la décomposition de l'évaluation après chaque coup joué. */
    private static final boolean DEBUG_EVAL = true;

    public static String IP_ADDRESS = "localhost";
    private final boolean MANUAL_MODE = false;
    /** Le serveur accorde 5 s; cette marge couvre l'envoi réseau et la JVM. */
    private static final long MOVE_LIMIT_MS = 5_000;
    private static final long SEARCH_MARGIN_MS = 500;

    public static void main(String[] args) {
        Client cl = new Client();
        cl.run();
    }

    private Board board;
    private CPUPlayer cpu;
    private final Set<String> seenPositions = new HashSet<>();

    private Client() {
        this.board = new Board();
        this.cpu = new CPUPlayer(Mark.EMPTY);
    }

    private void run() {

        BufferedInputStream input;
        BufferedOutputStream output;

        boolean running = true;

        try(Socket MyClient = new Socket(IP_ADDRESS, 8888)) {
            input    = new BufferedInputStream(MyClient.getInputStream());
            output   = new BufferedOutputStream(MyClient.getOutputStream());
            BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

            while (running) {
                char cmd = (char) input.read();

                System.out.printf("received request type : %s\n", cmd);

                switch (cmd) {
                    case '1':
                        startAsRed(input, output, console);
                        break;
                    case '2':
                        startAsBlack(input);
                        break;
                    case '3':
                        playMove(input, output, console);
                        break;
                    case '4':
                        invalidMove(output, console);
                        break;
                    case '5':
                        gameHasEnded(input, output, console);

                        // avoid crash at the end of the game
                        while(true);
                }
            }
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    private void startAsRed(BufferedInputStream input, BufferedOutputStream output, BufferedReader console) throws IOException {
        System.out.println("Starting as attackers (red team) : \n");
        String s = getStringFromInputStream(input, 350);
        System.out.printf("Received board (as a one line string): \n%s\n\n", s);

        board = new Board(s);
        seenPositions.clear();
        recordCurrentPosition();
        System.out.println(board);

        cpu = new CPUPlayer(Mark.RED);

        computeAndSendMove(output, console);

        System.out.println(board);
    }

    private void startAsBlack(BufferedInputStream input) throws IOException {
        System.out.println("Starting as defenders (black & king team) : \n");
        String s = getStringFromInputStream(input, 350);
        System.out.printf("Received board (as a one line string): \n%s\n\n", s);

        board = new Board(s);
        seenPositions.clear();
        recordCurrentPosition();
        System.out.println(board);

        cpu = new CPUPlayer(Mark.BLACK);
    }

    private void playMove(BufferedInputStream input, BufferedOutputStream output, BufferedReader console) throws IOException {
        System.out.println("waiting for your turn...\n");
        String m = getStringFromInputStream(input, 16);
        System.out.printf("Received move representation string : %s\n\n", m);

        if(Move.getMoveMatcherOrNull(m) == null) {
            System.out.println("invalid move received (we have to play the first move as red team) :");
        } else {
            board.play(new Move(m));
            recordCurrentPosition();
            System.out.println(board);
        }

        computeAndSendMove(output, console);

        System.out.println(board);
    }

    private void invalidMove(BufferedOutputStream output, BufferedReader console) throws IOException {
        throw new IllegalArgumentException("invalid Move");
    }

    private void gameHasEnded(BufferedInputStream input, BufferedOutputStream output, BufferedReader console) throws IOException {
        System.out.println("End of the game.\nResult : \n");

        if(board.isKingCaptured()) {
            System.out.println("RED team won");
        } else if(board.isKingEscaped()) {
            System.out.println("BLACK team won");
        } else {
            System.out.println("game is a draw");
        }
    }

    /** Demande un coup au CPU, l'applique sur notre board, et l'envoie au serveur. */
    private void computeAndSendMove(BufferedOutputStream output, BufferedReader console) throws IOException {
        Move move_obj = null;

        if(MANUAL_MODE) {
            System.out.print("(MANUAL MODE ACTIVE) play a move : ");
            String move_str = console.readLine();
            move_obj = new Move(move_str);
        } else {
            long start = System.nanoTime();
            move_obj = cpu.getBestMoveWithinMillis(
                    board, MOVE_LIMIT_MS - SEARCH_MARGIN_MS, seenPositions);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            System.out.printf("search: depth %d, nodes %d, time %d ms%n",
                    cpu.getLastCompletedDepth(),
                    cpu.getNumOfExploredNodes(),
                    elapsedMs);
        }

        output.write(move_obj.toString().getBytes(), 0, move_obj.toString().length());
        output.flush();

        System.out.printf("move played : %s\n\n",  move_obj.toString());
        board.play(move_obj);
        if (DEBUG_EVAL)  System.out.println(new HeuristicEvaluator().explain(board, cpu.getMaxPlayer()));
        recordCurrentPosition();
    }

    private void recordCurrentPosition() {
        seenPositions.add(board.getBoardAsOneLineString("int"));
    }

    private static String getStringFromInputStream(BufferedInputStream in, int bufferSize) throws IOException {
        byte[] aBuffer = new byte[bufferSize];
        int contentSize = in.available();
        in.read(aBuffer,0, contentSize);
        return String.join("", new String(aBuffer).trim().split(" "));
    }
}