package main.java.server;

import main.java.server.Server;
import main.java.sn.examen_messagerie.entity.ChatMessage;
//import sn.examen_messagerie.entity.ChatMessage;
import sn.examen_messagerie.entity.MessageStatus;
import sn.examen_messagerie.entity.User;
import sn.examen_messagerie.repository.impl.MessageRepository;
import sn.examen_messagerie.service.UserService;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

// Gère la communication avec un client connecté (un thread par client)
public class ClientHandler implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());

    private final Socket socket;
    private final UserService userService;
    private final MessageRepository messageRepository;
    private String currentUser;          // nom de l'utilisateur connecté
    private ObjectOutputStream out;      // flux de sortie vers le client
    private ObjectInputStream in;        // flux d'entrée depuis le client

    public ClientHandler(Socket socket, UserService userService) {
        this.socket = socket;
        this.userService = userService;
        this.messageRepository = new MessageRepository();
    }

    @Override
    public void run() {
        try {
            // Initialisation des flux (out AVANT in pour éviter le deadlock)
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            // Boucle principale : on lit les messages du client
            while (true) {
                ChatMessage request = (ChatMessage) in.readObject();
                String action = request.getAction();

                // Aiguillage selon l'action demandée
                switch (action) {
                    case "register":
                        handleRegister(request);
                        break;
                    case "login":
                        handleLogin(request);
                        break;
                    case "logout":
                        handleLogout();
                        return; // on sort de la boucle et du thread
                    case "send_message":
                        handleSendMessage(request);
                        break;
                    case "get_users":
                        handleGetUsers();
                        break;
                    case "get_history":
                        handleGetHistory(request);
                        break;
                    default:
                        sendResponse("error", "Action inconnue : " + action);
                        break;
                }
            }

        } catch (Exception e) {
            // Perte de connexion du client (RG10)
            LOGGER.info("[SERVEUR] Connexion perdue avec " +
                    (currentUser != null ? currentUser : "client inconnu"));
        } finally {
            // Nettoyage à la déconnexion
            disconnect();
        }
    }

    // ===================== INSCRIPTION (RG1, RG9) =====================
    private void handleRegister(ChatMessage request) {
        String username = request.getSender();
        String password = request.getContenu();

        boolean success = userService.register(username, password);

        if (success) {
            LOGGER.info("[SERVEUR] Inscription réussie : " + username);
            sendResponse("register_response", "OK");
        } else {
            LOGGER.warning("[SERVEUR] Echec inscription : " + username + " (existe déjà)");
            sendResponse("register_response", "Ce nom d'utilisateur existe déjà");
        }
    }

    // ===================== CONNEXION (RG3, RG4, RG6) =====================
    private void handleLogin(ChatMessage request) {
        String username = request.getSender();
        String password = request.getContenu();

        // RG3 : vérifier si l'utilisateur est déjà connecté
        if (Server.connectedClients.containsKey(username)) {
            sendResponse("login_response", "Cet utilisateur est déjà connecté");
            return;
        }

        boolean success = userService.login(username, password);

        if (success) {
            currentUser = username;

            // Enregistrer ce client dans la liste des connectés
            Server.connectedClients.put(username,this);
            LOGGER.info("[SERVEUR] Connexion : " + username);

            sendResponse("login_response", "OK");

            // RG6 : livrer les messages en attente (reçus pendant que l'utilisateur était hors ligne)
            deliverPendingMessages();

            // Informer tous les clients de la mise à jour de la liste
            Server.broadcastUserList();
        } else {
            LOGGER.warning("[SERVEUR] Echec connexion : " + username);
            sendResponse("login_response", "Identifiants incorrects");
        }
    }

    // ===================== DECONNEXION (RG4) =====================
    private void handleLogout() {
        if (currentUser != null) {
            LOGGER.info("[SERVEUR] Déconnexion : " + currentUser);
            userService.logout(currentUser);
        }
    }

    // ===================== ENVOI DE MESSAGE (RG5, RG7) =====================
    private void handleSendMessage(ChatMessage request) {
        // RG2 : vérifier que l'expéditeur est connecté
        if (currentUser == null) {
            sendResponse("error", "Vous devez être connecté pour envoyer un message");
            return;
        }

        String receiver = request.getReceiver();
        String content = request.getContenu();

        // RG5 : vérifier que le destinataire existe
        if (userService.findByUsername(receiver) == null) {
            sendResponse("error", "L'utilisateur " + receiver + " n'existe pas");
            return;
        }

        // RG7 : valider le contenu du message
        if (content == null || content.trim().isEmpty()) {
            sendResponse("error", "Le message ne peut pas être vide");
            return;
        }
        if (content.length() > 1000) {
            sendResponse("error", "Le message ne doit pas dépasser 1000 caractères");
            return;
        }

        // Créer le message avec le bon expéditeur
        request.setSender(currentUser);
        request.setDateEnvoi(LocalDateTime.now());

        // Vérifier si le destinataire est en ligne
        ClientHandler receiverHandler = Server.connectedClients.get(receiver);

        if (receiverHandler != null) {
            // Destinataire en ligne : on lui envoie et on marque RECU
            request.setStatut(MessageStatus.RECU);
            request.setAction("receive_message");
            receiverHandler.sendMessage(request);
        } else {
            // Destinataire hors ligne : on marque ENVOYE (RG6 : livré plus tard)
            request.setStatut(MessageStatus.ENVOYE);
        }

        // Sauvegarder le message en base
        request.setAction("send_message");
        messageRepository.save(request);

        LOGGER.info("[SERVEUR] Message de " + currentUser + " vers " + receiver);

        // Confirmer l'envoi à l'expéditeur
        sendResponse("message_sent", "OK");
    }

    // ===================== LISTE DES UTILISATEURS CONNECTÉS =====================
    private void handleGetUsers() {
        StringBuilder users = new StringBuilder();
        userService.findOnlineUsers().forEach(user -> {
            // Cast explicite vers User pour accéder à getUsername()
            String username = ((User) user).getUsername();
            if (!username.equals(currentUser)) {
                if (users.length() > 0) {
                    users.append(",");
                }
                users.append(username);
            }
        });
        sendResponse("user_list", users.toString());
    }

    // ===================== HISTORIQUE DES MESSAGES (RG8) =====================
    private void handleGetHistory(ChatMessage request) {
        String otherUser = request.getReceiver();

        // Récupérer les messages entre les deux utilisateurs, triés par date
        List<ChatMessage> history = messageRepository.findBetweenUsers(currentUser, otherUser);

        // Envoyer chaque message de l'historique au client
        for (ChatMessage msg : history) {
            ChatMessage historyMsg = new ChatMessage();
            historyMsg.setAction("history");
            historyMsg.setSender(msg.getSender());
            historyMsg.setReceiver(msg.getReceiver());
            historyMsg.setContenu(msg.getContenu());
            historyMsg.setDateEnvoi(msg.getDateEnvoi());
            historyMsg.setStatut(msg.getStatut());
            sendMessage(historyMsg);
        }

        // Envoyer un marqueur de fin d'historique
        sendResponse("history_end", "OK");
    }

    // ===================== LIVRAISON DES MESSAGES EN ATTENTE (RG6) =====================
    private void deliverPendingMessages() {
        List<ChatMessage> pendingMessages = messageRepository.findPendingForUser(currentUser);

        for (ChatMessage msg : pendingMessages) {
            ChatMessage delivery = new ChatMessage();
            delivery.setAction("receive_message");
            delivery.setSender(msg.getSender());
            delivery.setReceiver(msg.getReceiver());
            delivery.setContenu(msg.getContenu());
            delivery.setDateEnvoi(msg.getDateEnvoi());
            delivery.setStatut(MessageStatus.RECU);
            sendMessage(delivery);

            // Mettre à jour le statut en base : ENVOYE -> RECU
            messageRepository.updateStatus(msg.getId(), MessageStatus.RECU);
        }
    }

    // ===================== METHODES UTILITAIRES =====================

    // Envoyer un ChatMessage au client
    public void sendMessage(ChatMessage message) {
        try {
            out.writeObject(message);
            out.flush();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[SERVEUR] Erreur envoi vers " +
                    (currentUser != null ? currentUser : "inconnu"), e);
        }
    }

    // Envoyer une réponse simple (action + contenu)
    private void sendResponse(String action, String content) {
        ChatMessage response = new ChatMessage();
        response.setAction(action);
        response.setContenu(content);
        sendMessage(response);
    }

    // Nettoyage quand le client se déconnecte
    private void disconnect() {
        if (currentUser != null) {
            Server.connectedClients.remove(currentUser);
            userService.logout(currentUser);
            LOGGER.info("[SERVEUR] " + currentUser + " déconnecté");
            Server.broadcastUserList();
        }

        try {
            socket.close();
        } catch (Exception ignored) {
        }
    }
}