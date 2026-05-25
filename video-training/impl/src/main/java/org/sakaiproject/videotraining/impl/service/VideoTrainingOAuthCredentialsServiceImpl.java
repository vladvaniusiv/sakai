package org.sakaiproject.videotraining.impl.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.StringUtils;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.videotraining.api.model.VideoProviderType;
import org.sakaiproject.videotraining.api.model.VideoTrainingOAuthCredentials;
import org.sakaiproject.videotraining.api.repository.VideoTrainingOAuthCredentialsRepository;
import org.sakaiproject.videotraining.api.service.VideoTrainingOAuthCredentialsService;
import org.springframework.transaction.annotation.Transactional;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Transactional
public class VideoTrainingOAuthCredentialsServiceImpl implements VideoTrainingOAuthCredentialsService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BIT = 128;
    public static final String ENCRYPTION_KEY_PROPERTY = "video.training.oauth.encryption.key";

    @Setter
    private VideoTrainingOAuthCredentialsRepository credentialsRepository;

    @Override
    public List<VideoTrainingOAuthCredentials> getAllCredentials() {
        return credentialsRepository.findAllByOrderByProviderTypeAsc();
    }

    @Override
    public Optional<VideoTrainingOAuthCredentials> getCredentials(VideoProviderType providerType) {
        if (providerType == null) {
            return Optional.empty();
        }

        return credentialsRepository.findByProviderType(providerType).map(this::decryptSecretIfNeeded);
    }

    @Override
    public VideoTrainingOAuthCredentials saveCredentials(VideoProviderType providerType, String clientId, String apiKey, String clientSecret, String refreshToken) {
        if (providerType == null) {
            throw new IllegalArgumentException("providerType must not be null");
        }

        VideoTrainingOAuthCredentials credentials = credentialsRepository.findByProviderType(providerType)
                .orElseGet(VideoTrainingOAuthCredentials::new);
        credentials.setProviderType(providerType);
        String normalizedClientId = StringUtils.trimToNull(clientId);
        String normalizedApiKey = StringUtils.trimToNull(apiKey);
        String normalizedClientSecret = StringUtils.trimToNull(clientSecret);
        String normalizedRefreshToken = StringUtils.trimToNull(refreshToken);

        if (normalizedClientId != null || credentials.getId() == null) {
            credentials.setClientId(normalizedClientId);
        }
        if (normalizedApiKey != null || credentials.getId() == null) {
            credentials.setApiKey(normalizedApiKey);
        }
        if (normalizedClientSecret != null || credentials.getId() == null) {
            credentials.setClientSecret(encrypt(normalizedClientSecret));
        }
        if (normalizedRefreshToken != null || credentials.getId() == null) {
            credentials.setRefreshToken(encrypt(normalizedRefreshToken));
        }
        if (credentials.getCreatedOn() == null) {
            credentials.setCreatedOn(Instant.now());
        }
        credentials.setModifiedOn(Instant.now());
        return credentialsRepository.save(credentials);
    }

    @Override
    public boolean isConfigured(VideoProviderType providerType) {
        return getCredentials(providerType)
            .map(credentials -> StringUtils.isNotBlank(credentials.getClientId())
                    && StringUtils.isNotBlank(credentials.getClientSecret())
                    && StringUtils.isNotBlank(credentials.getRefreshToken()))
            .orElse(false);
    }

    private VideoTrainingOAuthCredentials decryptSecretIfNeeded(VideoTrainingOAuthCredentials credentials) {
        if (credentials == null) {
            return null;
        }

        VideoTrainingOAuthCredentials copy = new VideoTrainingOAuthCredentials();
        copy.setId(credentials.getId());
        copy.setProviderType(credentials.getProviderType());
        copy.setClientId(credentials.getClientId());
        copy.setApiKey(credentials.getApiKey());
        copy.setClientSecret(decrypt(credentials.getClientSecret()));
        copy.setRefreshToken(decrypt(credentials.getRefreshToken()));
        copy.setCreatedOn(credentials.getCreatedOn());
        copy.setModifiedOn(credentials.getModifiedOn());
        return copy;
    }

    private static byte[] getKey() {
        String key = ServerConfigurationService.getString(ENCRYPTION_KEY_PROPERTY, null);
        if (key == null || key.length() != 32) {
            throw new RuntimeException("Property '" + ENCRYPTION_KEY_PROPERTY + "' must be defined in sakai.properties and contain 32 ASCII characters for AES-256");
        }
        return key.getBytes(StandardCharsets.UTF_8);
    }

    private String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }

        try {
            SecureRandom random = new SecureRandom();
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(getKey(), "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, combined, IV_LENGTH, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("Error encrypting video training credential", e);
            return null;
        }
    }

    private String decrypt(String cipherText) {
        if (cipherText == null) {
            return null;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(cipherText);
            if (decoded.length <= IV_LENGTH) {
                return null;
            }

            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[decoded.length - IV_LENGTH];
            System.arraycopy(decoded, 0, iv, 0, IV_LENGTH);
            System.arraycopy(decoded, IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(getKey(), "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Error decrypting video training credential", e);
            return null;
        }
    }
}