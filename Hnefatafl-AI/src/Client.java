import java.io.*;
import java.net.*;
import java.util.HashSet;
import java.util.Set;
import java.util.Scanner;

class Client {

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

        Scanner scanner = new Scanner(System.in);

        System.out.print("Entrez une adresse IP : ");
        String ip = scanner.nextLine();

        scanner = new Scanner(System.in);

        System.out.print("Entrez un port : ");
        String portInput = scanner.nextLine();
        int port = Integer.parseInt(portInput);

        try(Socket MyClient = new Socket(ip, port)) {
            input    = new BufferedInputStream(MyClient.getInputStream());
            output   = new BufferedOutputStream(MyClient.getOutputStream());
            BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

            while (running) {
                char cmd = (char) input.read();

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
                        //running = false;
                        while (true);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void startAsRed(BufferedInputStream input, BufferedOutputStream output, BufferedReader console) throws IOException {
        String s = getStringFromInputStream(input, 350);

        board = new Board(s);
        seenPositions.clear();
        recordCurrentPosition();

        cpu = new CPUPlayer(Mark.RED);

        computeAndSendMove(output, console);
    }

    private void startAsBlack(BufferedInputStream input) throws IOException {
        String s = getStringFromInputStream(input, 350);

        board = new Board(s);
        seenPositions.clear();
        recordCurrentPosition();

        cpu = new CPUPlayer(Mark.BLACK);
    }

    private void playMove(BufferedInputStream input, BufferedOutputStream output, BufferedReader console) throws IOException {
        String m = getStringFromInputStream(input, 16);

        if(Move.getMoveMatcherOrNull(m) != null) {
            board.play(new Move(m));
            recordCurrentPosition();
        }

        computeAndSendMove(output, console);
    }

    private void invalidMove(BufferedOutputStream output, BufferedReader console) throws IOException {
        throw new IllegalArgumentException("invalid Move");
    }

    private void gameHasEnded(BufferedInputStream input, BufferedOutputStream output, BufferedReader console) throws IOException {
    }

    /** Demande un coup au CPU, l'applique sur notre board, et l'envoie au serveur. */
    private void computeAndSendMove(BufferedOutputStream output, BufferedReader console) throws IOException {
        Move move_obj = null;

        if(MANUAL_MODE) {
            String move_str = console.readLine();
            move_obj = new Move(move_str);
        } else {
            move_obj = cpu.getBestMoveWithinMillis(
                    board, MOVE_LIMIT_MS - SEARCH_MARGIN_MS, seenPositions);
        }

        output.write(move_obj.toString().getBytes(), 0, move_obj.toString().length());
        output.flush();

        board.play(move_obj);
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