package sn.examen_messagerie.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    private Status status;

    private LocalDateTime dateCreation;

    public User() {
    }

    public User(String username, String password, Status status, LocalDateTime dateCreation) {
        this.username = username;
        this.password = password;
        this.status = status;
        this.dateCreation = dateCreation;
    }

    public User(Long id, String username, String password, Status status, LocalDateTime dateCreation) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.status = status;
        this.dateCreation = dateCreation;
    }
}

