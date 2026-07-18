import java.io.*;
import java.net.*;
import java.util.ArrayList;


class Client {

    public static String IP_ADDRESS = "localhost";

    public static void main(String[] args) {
        Client cl = new Client();
        cl.run();
    }

    private Board board;
    private CPUPlayer cpu;

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
                        running = false;
                        break;
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
        System.out.println(board);

        System.out.print("play a move : ");

        String move = console.readLine();
        Move myhardCodedMove = new Move(move);

        computeAndSendMove(output, myhardCodedMove);

        System.out.println(board);

        s = board.getBoardAsOneLineString("int");

        /**
         * testing subboard
         */

        System.out.printf("Received board (as a one line string): \n%s\n\n", s);

        SubBoard sub = new SubBoard(board.board, 6, 2);

        System.out.println(sub);

        sub.set(1, 1, Mark.KING);

        System.out.println(sub);

        System.out.println(board);

        System.exit(1);
    }

    private void startAsBlack(BufferedInputStream input) throws IOException {
        System.out.println("Starting as defenders (black & king team) : \n");
        String s = getStringFromInputStream(input, 350);
        System.out.printf("Received board (as a one line string): \n%s\n\n", s);

        board = new Board(s);
        System.out.println(board);
    }

    private void playMove(BufferedInputStream input, BufferedOutputStream output, BufferedReader console) throws IOException {
        System.out.println("waiting for your turn...\n");
        String m = getStringFromInputStream(input, 16);
        System.out.println("Received move representation string : " + m);

        if(Move.getMoveMatcherOrNull(m) == null) {
            System.out.println("invalid move received (we have to play the first move as red team) :");
        } else {
            board.play(new Move(m));
            System.out.println(board);
        }

        System.out.print("play a move : ");

        String move = console.readLine();
        Move myhardCodedMove = new Move(move);

        computeAndSendMove(output, myhardCodedMove);

        System.out.println(board);
    }

    private void invalidMove(BufferedOutputStream output, BufferedReader console) throws IOException {
        throw new IllegalArgumentException("Coup invalid");
    }

    private void gameHasEnded(BufferedInputStream input, BufferedOutputStream output, BufferedReader console) throws IOException {
        // TODO 
    }

    /** Demande un coup au CPU, l'applique sur notre board, et l'envoie au serveur. */
    private void computeAndSendMove(BufferedOutputStream output, Move move) throws IOException {
        output.write(move.toString().getBytes(), 0, move.toString().length());
        output.flush();
        board.play(move);
    }

    private static String getStringFromInputStream(BufferedInputStream in, int bufferSize) throws IOException {
        byte[] aBuffer = new byte[bufferSize];
        int contentSize = in.available();
        in.read(aBuffer,0, contentSize);
        return String.join("", new String(aBuffer).trim().split(" "));
    }
}