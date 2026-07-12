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

        Socket MyClient;
        BufferedInputStream input;
        BufferedOutputStream output;


        try {
            MyClient = new Socket(IP_ADDRESS, 8888);
            input    = new BufferedInputStream(MyClient.getInputStream());
            output   = new BufferedOutputStream(MyClient.getOutputStream());
            BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

            while (true) {
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
                        break;
                }
            }
        }
        catch (IOException e) {
            System.out.println(e);
        }
    }

    private void startAsRed(BufferedInputStream input, BufferedOutputStream output, BufferedReader console) throws IOException {
        // TODO 
    }

    private void startAsBlack(BufferedInputStream input) throws IOException {
        // TODO 
    }

    private void playMove(BufferedInputStream input, BufferedOutputStream output, BufferedReader console) throws IOException {
        // TODO 
    }

    private void invalidMove(BufferedOutputStream output, BufferedReader console) throws IOException {
        // TODO 
    }

    private void gameHasEnded(BufferedInputStream input, BufferedOutputStream output, BufferedReader console) throws IOException {
        // TODO 
    }

    /** Demande un coup au CPU, l'applique sur notre board, et l'envoie au serveur. */
    private void computeAndSendMove(BufferedOutputStream output) throws IOException {
        // TODO 
    }
}