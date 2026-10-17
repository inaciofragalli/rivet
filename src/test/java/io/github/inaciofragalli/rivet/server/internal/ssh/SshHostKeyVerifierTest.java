package io.github.inaciofragalli.rivet.server.internal.ssh;

import org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier;
import org.apache.sshd.client.keyverifier.RejectAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SshHostKeyVerifierTest {

    private static final String HOST = "192.0.2.10";

    @TempDir
    Path tempDir;

    @Test
    void shouldAcceptHostKeyRegisteredInKnownHosts() throws Exception {
        KeyPair expected = generateKeyPair();
        Path knownHosts = tempDir.resolve("known_hosts");
        Files.writeString(
                knownHosts,
                HOST + " " + PublicKeyEntry.toString(expected.getPublic()) + "\n"
        );

        KnownHostsServerKeyVerifier verifier = verifier(knownHosts);

        assertTrue(checkKey(verifier, expected));
    }

    @Test
    void shouldRejectModifiedHostKey() throws Exception {
        KeyPair expected = generateKeyPair();
        KeyPair different = generateKeyPair();
        Path knownHosts = tempDir.resolve("known_hosts");
        Files.writeString(
                knownHosts,
                HOST + " " + PublicKeyEntry.toString(expected.getPublic()) + "\n"
        );

        KnownHostsServerKeyVerifier verifier = verifier(knownHosts);

        assertFalse(checkKey(verifier, different));
    }

    @Test
    void shouldRejectUnknownHostWithoutAddingItAutomatically() throws Exception {
        KeyPair presented = generateKeyPair();
        Path knownHosts = tempDir.resolve("known_hosts");
        Files.writeString(knownHosts, "");

        KnownHostsServerKeyVerifier verifier = verifier(knownHosts);

        assertFalse(checkKey(verifier, presented));
        assertTrue(Files.readString(knownHosts).isEmpty());
    }

    private KnownHostsServerKeyVerifier verifier(Path knownHosts) {
        return new KnownHostsServerKeyVerifier(
                RejectAllServerKeyVerifier.INSTANCE,
                knownHosts
        );
    }

    private boolean checkKey(KnownHostsServerKeyVerifier verifier, KeyPair keyPair) {
        InetSocketAddress address = new InetSocketAddress(HOST, 22);
        ClientSession session = mock(ClientSession.class);
        when(session.getConnectAddress()).thenReturn(address);

        return verifier.verifyServerKey(session, address, keyPair.getPublic());
    }

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    @Test
    void shouldAcceptEd25519HostKeyRegisteredInKnownHosts() throws Exception {
        KeyPair expected = generateEd25519KeyPair();
        Path knownHosts = tempDir.resolve("known_hosts");
        Files.writeString(
                knownHosts,
                HOST + " " + PublicKeyEntry.toString(expected.getPublic()) + "\n"
        );

        KnownHostsServerKeyVerifier verifier = verifier(knownHosts);

        assertTrue(checkKey(verifier, expected));
    }

    @Test
    void shouldRejectModifiedEd25519HostKey() throws Exception {
        KeyPair expected = generateEd25519KeyPair();
        KeyPair different = generateEd25519KeyPair();
        Path knownHosts = tempDir.resolve("known_hosts");
        Files.writeString(
                knownHosts,
                HOST + " " + PublicKeyEntry.toString(expected.getPublic()) + "\n"
        );

        KnownHostsServerKeyVerifier verifier = verifier(knownHosts);

        assertFalse(checkKey(verifier, different));
    }

    private KeyPair generateEd25519KeyPair() throws Exception {
        SecurityUtils.isBouncyCastleRegistered();
        return SecurityUtils.getKeyPairGenerator("Ed25519").generateKeyPair();
    }
}
