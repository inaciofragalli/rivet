package io.github.inaciofragalli.rivet.server.internal.ssh;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.config.hosts.HostConfigEntryResolver;
import org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier;
import org.apache.sshd.client.keyverifier.RejectAllServerKeyVerifier;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class SshClientConfiguration {
    @Bean(initMethod = "start", destroyMethod = "stop")
    public SshClient sshClient(@Value("${rivet.ssh.known-hosts-path}") String knownHostsPath,
                               @Value("${rivet.ssh.private-key-path:}") String privateKeyPath) {
        SshClient  client = SshClient.setUpDefaultClient();

        client.setServerKeyVerifier(
                new KnownHostsServerKeyVerifier(
                        RejectAllServerKeyVerifier.INSTANCE,
                        Path.of(knownHostsPath)
                )
        );

        client.setHostConfigEntryResolver(HostConfigEntryResolver.EMPTY);

        client.setKeyIdentityProvider(
                privateKeyPath.isBlank()
                        ? KeyIdentityProvider.EMPTY_KEYS_PROVIDER
                        : new FileKeyPairProvider(Path.of(privateKeyPath))
        );

        return client;
    }
}
