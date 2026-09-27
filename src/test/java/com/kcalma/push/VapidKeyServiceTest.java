package com.kcalma.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Plain Mockito unit tests -- no Spring context needed to exercise {@link VapidKeyService}'s resolution order. */
class VapidKeyServiceTest {

    private final PushConfigRepository repository = mock(PushConfigRepository.class);
    private final VapidProperties properties = new VapidProperties();
    private VapidKeyService service;

    @BeforeEach
    void setUp() {
        properties.setSubject("https://kcalma-frontend.vercel.app");
        service = new VapidKeyService(repository, properties);
    }

    @Test
    void resolveKeyPair_envVarsSet_usesThemDirectlyAndNeverTouchesTheRepository() {
        KeyPair fromEnv = EcKeys.generateKeyPair();
        properties.setPublicKey(base64Public(fromEnv));
        properties.setPrivateKey(base64Private(fromEnv));

        KeyPair resolved = service.resolveKeyPair();

        assertThat(base64Public(resolved)).isEqualTo(base64Public(fromEnv));
        verifyNoInteractions(repository);
    }

    @Test
    void resolveKeyPair_noEnvVarsButAnExistingRow_reusesThePersistedKeyPairWithoutGeneratingANewOne() {
        KeyPair persisted = EcKeys.generateKeyPair();
        PushConfig existing = new PushConfig(base64Public(persisted), base64Private(persisted));
        when(repository.findById(PushConfig.SINGLETON_ID)).thenReturn(Optional.of(existing));

        KeyPair resolved = service.resolveKeyPair();

        assertThat(base64Public(resolved)).isEqualTo(base64Public(persisted));
        verify(repository, never()).save(any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void resolveKeyPair_noEnvVarsAndNoExistingRow_generatesAndPersistsOnceThenCachesInMemory() {
        when(repository.findById(PushConfig.SINGLETON_ID)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        String publicKey = service.getPublicKeyBase64Url();

        assertThat(EcKeys.fromBase64Url(publicKey)).hasSize(EcKeys.UNCOMPRESSED_POINT_BYTES);
        verify(repository, times(1)).saveAndFlush(any(PushConfig.class));

        service.getPublicKeyBase64Url(); // second resolution must come from the in-memory cache
        verify(repository, times(1)).findById(PushConfig.SINGLETON_ID); // still just the one lookup from the first call
    }

    private static String base64Public(KeyPair keyPair) {
        return EcKeys.toBase64Url(EcKeys.encodePublicKey((ECPublicKey) keyPair.getPublic()));
    }

    private static String base64Private(KeyPair keyPair) {
        return EcKeys.toBase64Url(EcKeys.encodePrivateKey((ECPrivateKey) keyPair.getPrivate()));
    }
}
