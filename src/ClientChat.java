import java.net.*;
import java.io.*;

public class ClientChat extends Thread {
    private BufferedReader inputNetwork;
    private BufferedReader inputConsole;
    private PrintStream outputNetwork;
    private PrintStream outputConsole;
    private Socket socket;
    private volatile boolean running;

    private String serverHost;
    private int serverPort;

    public ClientChat(String[] args) {
        running = true;
        initStreams(args);
        start();
    }

    public void initStreams(String[] args) {
        if (args.length > 0) {
            serverHost = args[0];
        }

        if (args.length > 1) {
            try {
                serverPort = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("[!] --- Error: " + e.getMessage());
                serverPort = 1234;
            }
        }

        System.out.println("[+] --- Connected to " + serverHost + ":" + serverPort);

        try {
            socket = new Socket(serverHost, serverPort);
            inputNetwork = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            outputNetwork = new PrintStream(socket.getOutputStream(), true);

            // Envoyer l'identifiant client
            outputNetwork.println("JAVACLIENT_V1");

            inputConsole = new BufferedReader(new InputStreamReader(System.in));
            outputConsole = new PrintStream(System.out, true);
        } catch (IOException e) {
            e.printStackTrace();
            cleanup();
        }
    }

    public void cleanup() {
        running = false;
        try {
            if (inputNetwork != null) inputNetwork.close();
            if (outputNetwork != null) outputNetwork.close();
            if (inputConsole != null) inputConsole.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("[!] --- Error during cleanup: " + e.getMessage());
        }
    }

    public void run() {
        while (running) {
            try {
                String serverMessage = inputNetwork.readLine();
                if (serverMessage == null) {
                    System.out.println("[!] --- Server disconnected");
                    cleanup();
                    break;
                }
                outputConsole.println(serverMessage);
            } catch (IOException e) {
                if (running) {
                    System.err.println("[!] --- Error reading from server: " + e.getMessage());
                }
                cleanup();
                break;
            }
        }
    }

    public static void main(String[] args) {
        ClientChat clientChat = new ClientChat(args);

        while (clientChat.running) {
            try {
                String userMessage = clientChat.inputConsole.readLine();
                if (userMessage == null || userMessage.equalsIgnoreCase("/quit")) {
                    System.out.println("[+] --- Disconnecting from server...");
                    clientChat.outputNetwork.println("/quit");
                    clientChat.cleanup();
                    break;
                }
                clientChat.outputNetwork.println(userMessage);
            } catch (IOException e) {
                System.err.println("[!] --- Error reading from console: " + e.getMessage());
                clientChat.cleanup();
                break;
            }
        }
        System.exit(0);
    }
}