package com.pasarela.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pasarela.application.routing.RoutingConfig;
import com.pasarela.domain.model.PaymentProvider;
import com.pasarela.infrastructure.crypto.Sha256;
import com.pasarela.infrastructure.persistence.entity.MerchantEntity;
import com.pasarela.infrastructure.persistence.repository.MerchantRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MerchantService {
    private final MerchantRepository merchantRepository;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public MerchantService(MerchantRepository merchantRepository, ObjectMapper objectMapper) {
        this.merchantRepository = merchantRepository;
        this.objectMapper = objectMapper;
    }

    public MerchantCreated create(String name) {
        String apiKey = generateApiKey();
        String apiKeyHash = Sha256.hex(apiKey);

        MerchantEntity entity = new MerchantEntity();
        entity.setName(name);
        entity.setApiKeyHash(apiKeyHash);
        entity.setConfigJson(writeConfig(RoutingConfig.defaults()));

        MerchantEntity saved = merchantRepository.save(entity);
        return new MerchantCreated(saved, apiKey);
    }

    public List<MerchantEntity> list() {
        return merchantRepository.findAll();
    }

    public Optional<MerchantEntity> get(UUID merchantId) {
        return merchantRepository.findById(merchantId);
    }

    public MerchantEntity updateRoutingConfig(UUID merchantId, RoutingConfigPatch patch) {
        MerchantEntity merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("merchant not found"));

        RoutingConfig current = readConfig(merchant.getConfigJson());

        String forceProvider = patch.forceProvider() == null ? current.forceProvider() : patch.forceProvider();
        var weights = patch.weights() == null ? current.weights() : patch.weights();
        var costModel = patch.costModel() == null ? current.costModel() : patch.costModel();

        RoutingConfig next = new RoutingConfig(forceProvider, weights, costModel);
        merchant.setConfigJson(writeConfig(next));
        return merchantRepository.save(merchant);
    }

    public record MerchantCreated(MerchantEntity merchant, String apiKey) {}

    public record RoutingConfigPatch(
            String forceProvider,
            com.pasarela.application.routing.RoutingWeights weights,
            java.util.Map<PaymentProvider, Double> costModel
    ) {}

    private RoutingConfig readConfig(String json) {
        try {
            if (json == null || json.isBlank()) return RoutingConfig.defaults();
            RoutingConfig cfg = objectMapper.readValue(json, RoutingConfig.class);
            return cfg == null ? RoutingConfig.defaults() : cfg;
        } catch (Exception e) {
            return RoutingConfig.defaults();
        }
    }

    private String writeConfig(RoutingConfig config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("routing config serialization failed", e);
        }
    }

    private String generateApiKey() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return "po_demo_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

