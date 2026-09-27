package com.kcalma.push;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Backed by env vars VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, VAPID_SUBJECT (see application.yml).
 * {@code publicKey}/{@code privateKey} are deliberately optional (blank by default): when either
 * is blank, {@link VapidKeyService} falls back to a key pair persisted in {@code app.push_config},
 * generating and persisting one on first use if that table is empty too — see its javadoc.
 */
@ConfigurationProperties(prefix = "app.push")
@Validated
public class VapidProperties {

    private String publicKey = "";

    private String privateKey = "";

    @NotBlank
    private String subject;

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }
}
