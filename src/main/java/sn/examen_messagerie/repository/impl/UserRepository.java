package sn.examen_messagerie.repository.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.NoResultException;
import sn.examen_messagerie.entity.User;
import sn.examen_messagerie.utils.JPAUtils;

/**
 * Repository JPA pour la gestion des utilisateurs côté serveur.
 */
public class UserRepository {

    // Chercher un utilisateur par username
    public User findByUsername(String username) {
        EntityManager em = JPAUtils.getEntityManagerFactory().createEntityManager();
        try {
            return em.createQuery("SELECT u FROM User u WHERE u.username = :username", User.class)
                    .setParameter("username", username)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        } finally {
            em.close();
        }
    }

    // Mettre à jour un utilisateur (ex: statut)
    public void update(User user) {
        EntityManager em = JPAUtils.getEntityManagerFactory().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.merge(user);
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
        } finally {
            em.close();
        }
    }

    // Créer un utilisateur
    public void save(User user) {
        EntityManager em = JPAUtils.getEntityManagerFactory().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.persist(user);
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
        } finally {
            em.close();
        }
    }
}

