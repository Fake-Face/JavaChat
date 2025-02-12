import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.io.*;
import java.net.*;

/*
* ### TODO LIST ###
*      - Refaire les commentaires au propre (pour la version finale quand elle sera annoncée)
*      - Rendre le code plus clean
* */

public class ServiceChat extends Thread {

    private volatile boolean running = true;
    private boolean isJavaClient = false;

    private static final String CREDS_FILE = "creds.txt";
    public static final int NB_USER_MAX = 3;
    public static int nbUsers = 0;
    public String username; // Pseudo de l'utilisateur
    private int index;

    private static final Set<String> usernames = new HashSet<>(); // Structure pour pseudo unique
    private static final Map<String, String> userCredentials = new HashMap<>();
    private static final List<ServiceChat> activeClients = Collections.synchronizedList(new ArrayList<>()); // Liste (statique) des clients actifs
    public static PrintStream[] outputs = new PrintStream[NB_USER_MAX];

    private final Socket clientSocket;
    private Scanner clientScanner;


    // Constants for log formatting
    private static final String LOG_PREFIX = "[LOG]";
    private static final String ERROR_PREFIX = "[ERROR]";
    private static final String INFO_PREFIX = "[INFO]";
    private static final String WARN_PREFIX = "[WARN]";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ServiceChat(Socket clientSocket) {
        this.clientSocket = clientSocket;
        this.start();
    }

    // Logger class to handle all output formatting
    private static class Logger {
        private static void log(String prefix, String message) {
            String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
            System.out.printf("%s %s --- %s%n", timestamp, prefix, message);
        }

        static void info(String message) {
            log(INFO_PREFIX, message);
        }

        static void error(String message) {
            System.err.printf("%s %s --- %s%n",
                    LocalDateTime.now().format(TIME_FORMATTER),
                    ERROR_PREFIX,
                    message);
        }

        static void warn(String message) {
            log(WARN_PREFIX, message);
        }

        static void debug(String message) {
            log(LOG_PREFIX, message);
        }
    }

    // Client message formatting
    private static class MessageFormatter {
        static String systemMessage(String message) {
            return String.format("[*] --- %s", message);
        }

        static String errorMessage(String message) {
            return String.format("[!] --- %s", message);
        }

        static String promptMessage(String message) {
            return String.format("[?] --- %s", message);
        }

        static String privateMessage(String from, String message) {
            return String.format("[PM from %s] %s", from, message);
        }

        static String inputPrompt() {
            return "[>] Enter message: ";
        }
    }

    private synchronized void initStream() {
        try {
            this.index = nbUsers;
            outputs[nbUsers] = new PrintStream(this.clientSocket.getOutputStream());
            this.clientScanner = new Scanner(this.clientSocket.getInputStream());

            clientSocket.setSoTimeout(5000);
            try {
                String firstLine = this.clientScanner.nextLine();
                this.isJavaClient = "JAVACLIENT_V1".equals(firstLine);
            } catch (Exception e) {
                this.isJavaClient = false;
            }
            clientSocket.setSoTimeout(0);

            activeClients.add(this);
            nbUsers++;

            Logger.info(String.format("New %s client connected from %s",
                    (isJavaClient ? "Java" : "Telnet"),
                    clientSocket.getRemoteSocketAddress()));
        } catch (Exception e) {
            Logger.error("Failed to initialize stream: " + e.getMessage());
        }
    }

    private String formatUserTag(String username) {
        return "<" + username + " | " + (isJavaClient ? "Client" : "Telnet") + ">";
    }

    private void mainLoop() {
        PrintStream out = outputs[this.index];
        sendUserList(out);

        String buffer;
        while (running && this.clientScanner.hasNextLine()) {
            buffer = this.clientScanner.nextLine().trim();

            if (buffer.startsWith("/")) {
                if (handleCommand(buffer, out)) {
                    break;  // Sort de la boucle si /quit
                }
            } else {
                broadcast(formatUserTag(username) + " " + buffer);
                // broadcast("<" + username + "> " + buffer);
            }

            if (running) {
                out.print("[>] Enter message: ");
            }
        }
    }

    // Charge les credentials depuis le fichier
    private static void loadCredentials() {
        try {
            File file = new File(CREDS_FILE);
            if (!file.exists()) {
                try (PrintWriter writer = new PrintWriter(file)) {
                    writer.println("alice:pass123");
                    writer.println("bob:pass456");
                    writer.println("charlie:pass789");
                }
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(":");
                    if (parts.length == 2) {
                        userCredentials.put(parts[0], parts[1]);
                    }
                }
            }
            Logger.info("Credentials loaded successfully");
        } catch (IOException e) {
            Logger.error("Failed to load credentials: " + e.getMessage());
        }
    }

    // Sauvegarde les credentials dans le fichier --> Servira plus tard pour l'inscription (avec addUser)
    private static synchronized void saveCredentials() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CREDS_FILE))) {
            for (Map.Entry<String, String> entry : userCredentials.entrySet()) {
                writer.println(entry.getKey() + ":" + entry.getValue());
            }
            System.out.println("[LOG] --- Credentials saved successfully");

        } catch (IOException e) {
            System.err.println("[ERROR] --- Failed to save credentials: " + e.getMessage());
        }
    }

    // Ajoute un nouvel utilisateur --> Servira plus tard pour l'inscription
    public static synchronized boolean addUser(String username, String password) {
        if (userCredentials.containsKey(username)) {
            return false;
        }

        userCredentials.put(username, password);
        saveCredentials();
        return true;
    }

    private boolean authenticate() {
        try {
            PrintStream out = outputs[this.index];
            int maxAttempts = 3;
            int attempts = 0;

            while (attempts < maxAttempts) {
                try {
                    out.println("[?] --- Enter your username: ");
                    String inputUsername = this.clientScanner.nextLine().trim();

                    out.println("[?] --- Enter your password: ");
                    String inputPassword = this.clientScanner.nextLine().trim();

                    if (userCredentials.containsKey(inputUsername) &&
                            userCredentials.get(inputUsername).equals(inputPassword)) {

                        synchronized (usernames) {
                            if (usernames.contains(inputUsername)) {
                                out.println("[!] --- This user is already connected!");
                                attempts++;
                                continue;
                            }

                            this.username = inputUsername;
                            usernames.add(inputUsername);
                            broadcast("[*] --- " + username + " has joined the chat | Total users : " + nbUsers);
                            out.println("[>] Enter message: ");

                            System.out.println("[LOG] --- User '" + username + "' authenticated and connected. Total users: " + nbUsers);
                            return true;
                        }
                    }

                    out.println("[!] --- Invalid username or password!");
                    attempts++;

                    if (attempts < maxAttempts) {
                        out.println("[*] --- " + (maxAttempts - attempts) + " attempts remaining");
                    }
                } catch (NoSuchElementException e) {
                    // Le client s'est déconnecté pendant l'authentification
                    System.out.println("[LOG] --- Client disconnected during authentication");
                    return false;
                }
            }

            out.println("[!] --- Too many failed attempts. Connection closed.");
            return false;

        } catch (Exception e) {
            System.out.println("[LOG] --- Authentication error: " + e.getMessage());
            return false;
        }
    }

    // Méthode utilitaire pour trouver un client par son username
    private ServiceChat findClientByUsername(String targetUsername) {
        for (ServiceChat client : getActiveClients()) {
            if (client.username != null && client.username.equals(targetUsername)) {
                return client;
            }
        }
        return null;
    }

    // Implémentation de getActiveClients
    private List<ServiceChat> getActiveClients() {
        return new ArrayList<>(activeClients); // Retourne une copie de la liste pour éviter les modifications concurrentes
    }

    private boolean handleCommand(String command, PrintStream out) {
        String[] parts = command.split("\\s+", 3); // Divise en max 3 parties: commande, destinataire, message
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "/quit":
                out.println("Goodbye!");
                // La déconnexion sera gérée par le finally dans run()
                // clientScanner.close();
                running = false;
                return true;

            case "/list":
                sendUserList(out);
                break;

            case "/sendall":
                if (parts.length < 2) {
                    out.println("[!] Usage: /sendall <message>");
                    break;
                }
                String broadcastMsg = command.substring(9); // Longueur de "/sendall "
                broadcast("<" + username + "> " + broadcastMsg);
                break;

            case "/msgto":
                if (parts.length < 3) {
                    out.println("[!] Usage: /msgto <username> <message>");
                    break;
                }
                String targetUser = parts[1];
                String privateMsg = parts[2];
                sendPrivateMessage(targetUser, privateMsg, out);
                break;

            default:
                out.println("[!] Unknown command. Available commands:");
                out.println("    /quit - Leave the chat");
                out.println("    /list - Show connected users");
                out.println("    /sendall <message> - Send message to all users");
                out.println("    /msgto <username> <message> - Send private message");
        }

        return false;
    }

    private void disconnect() {
        running = false;
        try {
            if (username != null) {
                broadcast(MessageFormatter.systemMessage(username +
                        " has disconnected | Total users now : " + (nbUsers - 1)));
                synchronized(usernames) {
                    usernames.remove(username);
                }
            }

            activeClients.remove(this);

            // Close resources
            if (outputs[index] != null) outputs[index].close();
            if (clientScanner != null) clientScanner.close();

            if (nbUsers > 0) {
                nbUsers--;
                if (index < nbUsers) {
                    outputs[index] = outputs[nbUsers];
                }
                outputs[nbUsers] = null;
            }

            if (!clientSocket.isClosed()) {
                clientSocket.close();
            }

            Logger.info(String.format("%s disconnected. Remaining users: %d",
                    (username != null ? "User '" + username + "'" : "Unauthenticated user"),
                    nbUsers));

        } catch (IOException e) {
            Logger.error("Error during disconnect: " + e.getMessage());
        }
    }


    // Méthode pour envoyer un message privé modifiée pour utiliser la nouvelle structure
    private void sendPrivateMessage(String targetUser, String message, PrintStream senderOut) {
        ServiceChat targetClient = findClientByUsername(targetUser);

        if (targetClient == null) {
            senderOut.println(MessageFormatter.errorMessage("User '" + targetUser + "' not found"));
            return;
        }

        String formattedSenderTag = formatUserTag(username);
        String formattedTargetTag = formatUserTag(targetUser);

        // Send to recipient
        PrintStream targetOut = outputs[targetClient.index];
        targetOut.println(MessageFormatter.privateMessage(formattedSenderTag, message));

        // Confirmation to sender
        senderOut.println(MessageFormatter.privateMessage("to " + formattedTargetTag, message));

        Logger.debug(String.format("Private message from %s to %s: %s",
                formattedSenderTag, formattedTargetTag, message));
    }

    private void sendUserList(PrintStream out) {
        synchronized (usernames) {
            out.println("[*] Connected users (" + usernames.size() + "):");
            for (ServiceChat client : getActiveClients()) {
                if (client.username != null) {
                    String clientType = client.isJavaClient ? "Client" : "Telnet";
                    out.println("    - <" + client.username + " | " + clientType + ">" +
                            (client.username.equals(this.username) ? " (you)" : ""));
                }
            }
        }
    }

    public void run() {
        this.initStream();
        if (authenticate()) {  // On utilise authenticate() au lieu de login()
            try {
                this.mainLoop();
            } finally {
                disconnect();
            }
        } else {
            disconnect();  // Si l'authentification échoue, on déconnecte proprement
        }
    }


    // Vérifier si le serveur est plein
    public static synchronized boolean isServerFull() {
        return nbUsers >= NB_USER_MAX;
    }

    // Gère une nouvelle connexion
    public static void handleNewConnection(Socket clientSocket) throws IOException {
        if (isServerFull()) {
            rejectConnection(clientSocket);
        } else {
            acceptConnection(clientSocket);
        }
    }

    // Méthode privée pour rejeter une connexion
    private static void rejectConnection(Socket clientSocket) throws IOException {
        try (PrintStream out = new PrintStream(clientSocket.getOutputStream())) {
            out.println("[!] --- Server is full, try again later!");
        }
        clientSocket.close();
    }

    // Méthode privée pour accepter une connexion
    private static void acceptConnection(Socket clientSocket) {
        System.out.println("[+] --- New connection from "
                + clientSocket.getRemoteSocketAddress().toString()
                + " | Client number (ID) : " + nbUsers);
        new ServiceChat(clientSocket);
    }

    public static synchronized void broadcast(String message) {
        for(int i = 0; i < nbUsers; i++) {
            if (outputs[i] != null) {
                outputs[i].println(message);
            }
        }
        Logger.debug("Broadcast: " + message);
    }

    public static void main(String[] args) {
        Socket clientSocketMain;
        ServerSocket serverSocketMain;

        try {
            // Charge les credentials au démarrage
            loadCredentials();

            Scanner clientScannerMain = new Scanner(System.in);
            System.out.print("[?] --- Enter the port number : ");
            int port = clientScannerMain.nextInt();

            serverSocketMain = new ServerSocket(port);
            System.out.println("[+] --- JavaChat listening on the port : " + port);
            System.out.println("[+] --- Credentials loaded from " + CREDS_FILE);

            boolean isWaiting = true;

            while (true) {
                if (isWaiting) {
                    System.out.println("[SERVER] --- En attente de connexion...");
                    isWaiting = false;
                }

                clientSocketMain = serverSocketMain.accept();
                System.out.println("[SERVER] --- En cours de tentative de connexion...");
                isWaiting = true;

                handleNewConnection(clientSocketMain);
            }

        } catch (IOException e) {
            System.err.println("[ERROR] --- Server error: " + e.getMessage());
        }
    }
}