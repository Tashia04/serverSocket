package sn.examen_messagerie.repository.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import sn.examen_messagerie.entity.ChatMessage;
import sn.examen_messagerie.entity.MessageStatus;
import sn.examen_messagerie.utils.JPAUtils;

import java.util.List;

/**
 * Repository JPA pour la gestion des messages côté serveur.
 */
public class MessageRepository {

    // Sauvegarder un message
    public void save(ChatMessage message) {
        EntityManager em = JPAUtils.getEntityManagerFactory().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.persist(message);
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
        } finally {
            em.close();
        }
    }

    // Récupérer les messages entre deux utilisateurs (pour l'historique)
    public List<ChatMessage> findBetweenUsers(String user1, String user2) {
        EntityManager em = JPAUtils.getEntityManagerFactory().createEntityManager();
        try {
            return em.createQuery(
                            "SELECT m FROM ChatMessage m WHERE " +
                                    "((m.sender = :u1 AND m.receiver = :u2) OR " +
                                    "(m.sender = :u2 AND m.receiver = :u1)) " +
                                    "AND m.action = 'send_message' " +
                                    "ORDER BY m.dateEnvoi ASC", ChatMessage.class)
                    .setParameter("u1", user1)
                    .setParameter("u2", user2)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    // Récupérer les messages en attente pour un utilisateur (messages hors ligne)
    public List<ChatMessage> findPendingForUser(String username) {
        EntityManager em = JPAUtils.getEntityManagerFactory().createEntityManager();
        try {
            return em.createQuery(
                            "SELECT m FROM ChatMessage m WHERE m.receiver = :username " +
                                    "AND m.statut = :status AND m.action = 'send_message' " +
                                    "ORDER BY m.dateEnvoi ASC", ChatMessage.class)
                    .setParameter("username", username)
                    .setParameter("status", MessageStatus.ENVOYE)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    // Mettre à jour le statut d'un message
    public void updateStatus(Long messageId, MessageStatus status) {
        EntityManager em = JPAUtils.getEntityManagerFactory().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            ChatMessage msg = em.find(ChatMessage.class, messageId);
            if (msg != null) {
                msg.setStatut(status);
                em.merge(msg);
            }
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
        } finally {
            em.close();
        }
    }
}

