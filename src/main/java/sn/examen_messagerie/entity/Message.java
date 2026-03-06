package sn.examen_messagerie.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// Entité JPA représentant un message persisté en base de données
@Getter
@Setter
@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Column(length = 1000, nullable = false)
    private String contenu;

    private LocalDateTime dateEnvoi;

    @Enumerated(EnumType.STRING)
    private MessageStatus statut;

    public Message() {
    }

    public Message(User sender, User receiver, String contenu, LocalDateTime dateEnvoi, MessageStatus statut) {
        this.sender = sender;
        this.receiver = receiver;
        this.contenu = contenu;
        this.dateEnvoi = dateEnvoi;
        this.statut = statut;
    }
}
