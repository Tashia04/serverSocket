package sn.examen_messagerie.repository.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import sn.examen_messagerie.entity.Message;
import sn.examen_messagerie.entity.MessageStatus;
import sn.examen_messagerie.utils.JPAUtils;

import java.util.List;

// Repository JPA pour la gestion des messages côté serveur
public class MessageRepository {

    // Sauvegarder un message en base
    public void save(Message message) {
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

    // Mettre à jour le statut d'un message (ENVOYE -> RECU -> LU)
    public void updateStatus(Long messageId, MessageStatus status) {
        EntityManager em = JPAUtils.getEntityManagerFactory().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Message msg = em.find(Message.class, messageId);
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

    // Récupérer les messages entre deux utilisateurs, triés par date (RG8)
    public List<Message> findBetweenUsers(String user1, String user2) {
        EntityManager em = JPAUtils.getEntityManagerFactory().createEntityManager();
        try {
            return em.createQuery(
                            "SELECT m FROM Message m WHERE " +
                                    "(m.sender.username = :u1 AND m.receiver.username = :u2) OR " +
                                    "(m.sender.username = :u2 AND m.receiver.username = :u1) " +
                                    "ORDER BY m.dateEnvoi ASC", Message.class)
                    .setParameter("u1", user1)
                    .setParameter("u2", user2)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    // Récupérer les messages en attente pour un utilisateur hors ligne (RG6)
    public List<Message> findPendingForUser(String username) {
        EntityManager em = JPAUtils.getEntityManagerFactory().createEntityManager();
        try {
            return em.createQuery(
                            "SELECT m FROM Message m WHERE m.receiver.username = :username " +
                                    "AND m.statut = :status " +
                                    "ORDER BY m.dateEnvoi ASC", Message.class)
                    .setParameter("username", username)
                    .setParameter("status", MessageStatus.ENVOYE)
                    .getResultList();
        } finally {
            em.close();
        }
    }
}
