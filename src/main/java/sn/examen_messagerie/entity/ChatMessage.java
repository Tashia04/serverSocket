package sn.examen_messagerie.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

// Objet protocole réseau pour la communication client-serveur (pas une entité JPA)
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String action;
    private String sender;
    private String receiver;
    private String contenu;
    private MessageStatus statut;
    private LocalDateTime dateEnvoi;

    public ChatMessage() {
    }

    public ChatMessage(String action, String sender, String receiver, String contenu, MessageStatus statut) {
        this.action = action;
        this.sender = sender;
        this.receiver = receiver;
        this.contenu = contenu;
        this.statut = statut;
        this.dateEnvoi = LocalDateTime.now();
    }

    // ============ Getters et Setters ============

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getContenu() {
        return contenu;
    }

    public void setContenu(String contenu) {
        this.contenu = contenu;
    }

    public MessageStatus getStatut() {
        return statut;
    }

    public void setStatut(MessageStatus statut) {
        this.statut = statut;
    }

    public LocalDateTime getDateEnvoi() {
        return dateEnvoi;
    }

    public void setDateEnvoi(LocalDateTime dateEnvoi) {
        this.dateEnvoi = dateEnvoi;
    }
}
