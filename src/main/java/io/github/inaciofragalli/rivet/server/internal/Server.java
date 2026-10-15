package io.github.inaciofragalli.rivet.server.internal;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "servers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Server {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(nullable = false)
    private String host;

    @Column(name = "ssh_port", nullable = false)
    private Integer sshPort;

    @Column(name = "ssh_username", nullable = false, length = 30)
    private String sshUsername;

    public Server(String name, String host, Integer sshPort, String sshUsername) {
        this.name = name;
        this.host = host;
        this.sshPort = sshPort;
        this.sshUsername = sshUsername;
    }
}
