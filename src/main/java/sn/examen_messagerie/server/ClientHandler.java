package sn.examen_messagerie.server;

import sn.examen_messagerie.entity.ChatMessage;
import sn.examen_messagerie.entity.Message;
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

// Gère la communication avec un client connecté (un thread par client, RG11)
public class ClientHandler implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());

    private final Socket socket;
    private final UserService userService;
    private final MessageRepository messageRepository;
    private String currentUser;
    private ObjectOutputStream out;
    private ObjectInputStream in;

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
                        return;
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
            LOGGER.warning("[SERVEUR] Connexion perdue avec " +
                    (currentUser != null ? currentUser : "client inconnu"));
        } finally {
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
            LOGGER.info("[SERVEUR] Échec inscription : " + username + " (existe déjà)");
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
            Server.connectedClients.put(username, this);
            LOGGER.info("[SERVEUR] Connexion : " + username);

            sendResponse("login_response", "OK");

            // RG6 : livrer les messages en attente
            deliverPendingMessages();

            // Informer tous les clients de la mise à jour de la liste
            Server.broadcastUserList();
        } else {
            LOGGER.info("[SERVEUR] Échec connexion : " + username);
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

    // ===================== ENVOI DE MESSAGE (RG2, RG5, RG7) =====================
    private void handleSendMessage(ChatMessage request) {
        // RG2 : l'expéditeur doit être authentifié
        if (currentUser == null) {
            sendResponse("error", "Vous devez être connecté pour envoyer un message");
            return;
        }

        String receiver = request.getReceiver();
        String content = request.getContenu();

        // RG5 : le destinataire doit exister
        User receiverUser = userService.findByUsername(receiver);
        if (receiverUser == null) {
            sendResponse("error", "L'utilisateur " + receiver + " n'existe pas");
            return;
        }

        // RG7 : le contenu ne doit pas être vide ni dépasser 1000 caractères
        if (content == null || content.trim().isEmpty()) {
            sendResponse("error", "Le message ne peut pas être vide");
            return;
        }
        if (content.length() > 1000) {
            sendResponse("error", "Le message ne doit pas dépasser 1000 caractères");
            return;
        }

        User senderUser = userService.findByUsername(currentUser);
        LocalDateTime now = LocalDateTime.now();

        // Vérifier si le destinataire est en ligne
        ClientHandler receiverHandler = Server.connectedClients.get(receiver);
        MessageStatus status = (receiverHandler != null) ? MessageStatus.RECU : MessageStatus.ENVOYE;

        // Sauvegarder en base avec les relations @ManyToOne User
        Message message = new Message(senderUser, receiverUser, content, now, status);
        messageRepository.save(message);

        // Transférer au destinataire s'il est en ligne
        if (receiverHandler != null) {
            ChatMessage delivery = new ChatMessage();
            delivery.setAction("receive_message");
            delivery.setSender(currentUser);
            delivery.setReceiver(receiver);
            delivery.setContenu(content);
            delivery.setDateEnvoi(now);
            delivery.setStatut(MessageStatus.RECU);
            receiverHandler.sendMessage(delivery);
        }

        LOGGER.info("[SERVEUR] Message de " + currentUser + " vers " + receiver);
        sendResponse("message_sent", "OK");
    }

    // ===================== LISTE DES UTILISATEURS CONNECTÉS =====================
    private void handleGetUsers() {
        StringBuilder users = new StringBuilder();
        for (String username : Server.connectedClients.keySet()) {
            if (!username.equals(currentUser)) {
                if (users.length() > 0) {
                    users.append(",");
                }
                users.append(username);
            }
        }
        sendResponse("user_list", users.toString());
    }

    // ===================== HISTORIQUE DES MESSAGES (RG2, RG8) =====================
    private void handleGetHistory(ChatMessage request) {
        // RG2 : l'utilisateur doit être authentifié
        if (currentUser == null) {
            sendResponse("error", "Vous devez être connecté pour consulter les messages");
            return;
        }

        String otherUser = request.getReceiver();
        List<Message> history = messageRepository.findBetweenUsers(currentUser, otherUser);

        // Envoyer chaque message de l'historique au client
        for (Message msg : history) {
            ChatMessage historyMsg = new ChatMessage();
            historyMsg.setAction("history");
            historyMsg.setSender(msg.getSender().getUsername());
            historyMsg.setReceiver(msg.getReceiver().getUsername());
            historyMsg.setContenu(msg.getContenu());
            historyMsg.setDateEnvoi(msg.getDateEnvoi());
            historyMsg.setStatut(msg.getStatut());
            sendMessage(historyMsg);
        }

        sendResponse("history_end", "OK");
    }

    // ===================== LIVRAISON DES MESSAGES EN ATTENTE (RG6) =====================
    private void deliverPendingMessages() {
        List<Message> pendingMessages = messageRepository.findPendingForUser(currentUser);

        for (Message msg : pendingMessages) {
            ChatMessage delivery = new ChatMessage();
            delivery.setAction("receive_message");
            delivery.setSender(msg.getSender().getUsername());
            delivery.setReceiver(msg.getReceiver().getUsername());
            delivery.setContenu(msg.getContenu());
            delivery.setDateEnvoi(msg.getDateEnvoi());
            delivery.setStatut(MessageStatus.RECU);
            sendMessage(delivery);

            // Mettre à jour le statut en base : ENVOYE -> RECU
            messageRepository.updateStatus(msg.getId(), MessageStatus.RECU);
        }
    }

    // ===================== MÉTHODES UTILITAIRES =====================

    // Envoyer un ChatMessage au client
    public void sendMessage(ChatMessage message) {
        try {
            out.writeObject(message);
            out.flush();
        } catch (Exception e) {
            LOGGER.warning("[SERVEUR] Erreur envoi vers " +
                    (currentUser != null ? currentUser : "inconnu"));
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
