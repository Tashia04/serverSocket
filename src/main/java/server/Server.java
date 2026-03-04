package main.java.server;

import main.java.sn.examen_messagerie.entity.ChatMessage;

import sn.examen_messagerie.service.UserService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.logging.Level;

// Serveur principal qui accepte les connexions des clients
public class Server {

    private static final Logger LOGGER = Logger.getLogger(Server.class.getName());

    private static final int PORT = 9000;

    // Liste globale des clients connectés (clé = username, valeur = handler)
    public static Map<String, server.ClientHandler> connectedClients = new ConcurrentHashMap<>();

    // Pool de threads pour gérer plusieurs clients en parallèle (RG11)
    private final ExecutorService threadPool = Executors.newFixedThreadPool(10);

    private final UserService userService = new UserService();

    // Démarre le serveur et attend les connexions
    public void start() {
        LOGGER.info("[SERVEUR] Démarré sur le port " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

            while (!threadPool.isShutdown()) {
                // Attendre une nouvelle connexion client
                Socket clientSocket = serverSocket.accept();
                LOGGER.info("[SERVEUR] Nouveau client : " + clientSocket.getInetAddress());

                // Lancer un thread pour ce client
                threadPool.execute(new server.ClientHandler(clientSocket, userService));
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[SERVEUR] Erreur : " + e.getMessage(), e);
        } finally {
            shutdown();
        }
    }

    // Envoyer la liste des utilisateurs connectés à tous les clients
    public static void broadcastUserList() {
        // Construire la liste des usernames séparés par des virgules
        String users = String.join(",", connectedClients.keySet());

        ChatMessage userListMsg = new ChatMessage();
        userListMsg.setAction("user_list");
        userListMsg.setContenu(users);

        // Envoyer à chaque client connecté
        for (server.ClientHandler handler : connectedClients.values()) {
            handler.sendMessage(userListMsg);
        }
    }

    // Arrêter proprement le serveur
    private void shutdown() {
        LOGGER.info("[SERVEUR] Arrêt...");
        threadPool.shutdown();
    }

    // Point d'entrée du serveur
    public static void main(String[] args) {
        new Server().start();
    }
}
