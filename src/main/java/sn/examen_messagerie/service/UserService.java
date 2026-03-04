package sn.examen_messagerie.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import org.mindrot.jbcrypt.BCrypt;
import sn.examen_messagerie.entity.Status;
import sn.examen_messagerie.entity.User;
import sn.examen_messagerie.utils.JPAUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service utilisateur côté serveur :
 * - inscription / connexion / déconnexion
 * - lecture des utilisateurs ONLINE.
 */
public class UserService {

    // Méthode pour trouver un utilisateur par username
    public User findByUsername(String username) {
        EntityManager em = JPAUtils.getEntityManagerFactory().createEntityManager();
        try {
            return em.createQuery("SELECT u FROM User u WHERE u.username = :username", User.class)
                    .setParameter("username", username)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null; // aucun utilisateur trouvé
        } finally {
            em.close();
        }
    }

    /**
     * Retourne la liste des utilisateurs marqués ONLINE en base.
     * Utilisée pour la liste des utilisateurs connectés côté client.
     */
    public List<User> findOnlineUsers() {
        EntityManager em = JPAUtils.getEntityManagerFactory().createEntityManager();
        try {
            TypedQuery<User> query = em.createQuery(
                    "SELECT u FROM User u WHERE u.status = :status", User.class);
            query.setParameter("status", Status.ONLINE);
            return query.getResultList();
        } finally {
            em.close();
        }
    }

    // INSCRIPTION
    public boolean register(String username, String password) {

        EntityManager em = JPAUtils.getEntityManagerFactory().createEntityManager();
        EntityTransaction transaction = em.getTransaction();

        try {
            // Vérifier si username existe déjà
            if (findByUsername(username) != null) {
                return false; // utilisateur existe déjà
            }

            transaction.begin();

            // Hachage du mot de passe
            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt(12));

            // Création utilisateur (toujours OFFLINE au départ)
            User user = new User(username, hashedPassword, Status.OFFLINE, LocalDateTime.now());

            em.persist(user);

            transaction.commit();
            return true;

        } catch (Exception e) {
            if (transaction.isActive()) transaction.rollback();
            e.printStackTrace();
            return false;

        } finally {
            em.close();
        }
    }

    // CONNEXION
    public boolean login(String username, String password) {

        EntityManager em = JPAUtils.getEntityManagerFactory().createEntityManager();
        EntityTransaction transaction = em.getTransaction();

        try {
            User user = em.createQuery(
                            "SELECT u FROM User u WHERE u.username = :username", User.class)
                    .setParameter("username", username)
                    .getSingleResult();

            // Vérification mot de passe
            if (BCrypt.checkpw(password, user.getPassword())) {

                transaction.begin();
                user.setStatus(Status.ONLINE);
                em.merge(user);
                transaction.commit();

                return true;
            }

        } catch (Exception e) {
            if (transaction.isActive()) transaction.rollback();
        } finally {
            em.close();
        }

        return false;
    }

    // DECONNEXION
    public void logout(String username) {

        EntityManager em = JPAUtils.getEntityManagerFactory().createEntityManager();
        EntityTransaction transaction = em.getTransaction();

        try {
            User user = em.createQuery(
                            "SELECT u FROM User u WHERE u.username = :username", User.class)
                    .setParameter("username", username)
                    .getSingleResult();

            transaction.begin();
            user.setStatus(Status.OFFLINE);
            em.merge(user);
            transaction.commit();

        } catch (Exception e) {
            if (transaction.isActive()) transaction.rollback();
        } finally {
            em.close();
        }
    }

    // Vérifier si un utilisateur est ONLINE
    public boolean isOnline(String username) {
        User user = findByUsername(username);
        return user != null && user.getStatus() == Status.ONLINE;
    }
}

