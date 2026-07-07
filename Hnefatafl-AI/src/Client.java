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
        String s = readBoard(input);
        System.out.println(s);
        board = new Board(s);
        cpu = new CPUPlayer(Mark.RED);
        System.out.println("Nouvelle partie! Vous jouez rouge (attaquant), le CPU joue le premier coup :");
        computeAndSendMove(output);
    }

    private void startAsBlack(BufferedInputStream input) throws IOException {
        System.out.println("Nouvelle partie! Vous jouez noir, attendez le coup des rouges");
        String s = readBoard(input);
        System.out.println(s);
        board = new Board(s);
        cpu = new CPUPlayer(Mark.BLACK);   // ⚠️ tu l'avais oublié ici !
    }

    private void playMove(BufferedInputStream input, BufferedOutputStream output, BufferedReader console) throws IOException {
        String lastMove = readMove(input);   // déjà nettoyé : "F1F2"
        System.out.println("Dernier coup : " + lastMove);

        if (!lastMove.startsWith("A0")) {
            board.play(new Move(lastMove));
        }
        computeAndSendMove(output);
    }

    private void invalidMove(BufferedOutputStream output, BufferedReader console) throws IOException {
        System.out.println("Coup invalide, entrez un nouveau coup : ");
        String move = null;
        move = console.readLine();
        output.write(move.getBytes(),0,move.length());
        output.flush();

        // ici complexe de gerer le cas par CPU car le board est desynchronisé, ne devrait jamais arrivé. SINON
        // creer un systeme de rollback a l'etat precedent (ca reste lourd a faire et pas nescessaire si on
        // s'assure de la validiter systematique des mouvements possibles generés)
    }

    private void gameHasEnded(BufferedInputStream input, BufferedOutputStream output, BufferedReader console) throws IOException {
        byte[] aBuffer = new byte[16];
        int size = input.available();
        input.read(aBuffer,0,size);
        String s = new String(aBuffer);
        System.out.println("Partie Terminé. Le dernier coup joué est: "+s);
        String move = null;
        move = console.readLine();
        output.write(move.getBytes(),0,move.length());
        output.flush();
    }

    /** Demande un coup au CPU, l'applique sur notre board, et l'envoie au serveur. */
    private void computeAndSendMove(BufferedOutputStream output) throws IOException {
        ArrayList<Move> best = cpu.getNextMoveAB(board, 3);

        if (best.isEmpty()) {
            // Aucun coup possible : sécurité pour éviter le crash sur nextInt(0)
            System.out.println("Aucun coup possible, la partie est probablement finie.");
            return;
        }

        Move chosen = best.get(new java.util.Random().nextInt(best.size()));
        board.play(chosen);

        String move = chosen.toString();
        System.out.println("Le CPU joue : " + move);
        output.write(move.getBytes(), 0, move.length());
        output.flush();
    }

    /** Lit jusqu'à avoir 169 valeurs de pièce (plateau complet). Bloquant. */
    private String readBoard(BufferedInputStream input) throws IOException {
        StringBuilder sb = new StringBuilder();
        int pieceCount = 0;
        while (pieceCount < Board.SIZE * Board.SIZE) {
            int c = input.read();
            if (c == -1) break;
            sb.append((char) c);
            int v = c - '0';
            if (v == 0 || v == 2 || v == 4 || v == 5) pieceCount++;
        }
        return sb.toString().trim();
    }

    /** Lit jusqu'à avoir un coup complet (2 coordonnées). Bloquant. */
    private String readMove(BufferedInputStream input) throws IOException {
        StringBuilder sb = new StringBuilder();
        while (true) {
            int c = input.read();
            if (c == -1) break;
            sb.append((char) c);
            String cleaned = sb.toString().replaceAll("\\s", "").replace("-", "");
            if (cleaned.matches("[A-M]\\d{1,2}[A-M]\\d{1,2}")) {
                return cleaned;   // ex : "F1F2", "A0A0", "G12L12"
            }
        }
        return sb.toString().trim();
    }
}