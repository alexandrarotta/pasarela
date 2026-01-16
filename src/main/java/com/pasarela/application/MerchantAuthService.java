package com.pasarela.application;

import com.pasarela.domain.security.MerchantPrincipal;
import com.pasarela.infrastructure.crypto.Sha256;
import com.pasarela.infrastructure.persistence.entity.MerchantEntity;
import com.pasarela.infrastructure.persistence.repository.MerchantRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class MerchantAuthService {
    private final MerchantRepository merchantRepository;

    public MerchantAuthService(MerchantRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
    }

    public Optional<MerchantPrincipal> authenticate(String apiKey) {
        String hash = Sha256.hex(apiKey);
        Optional<MerchantEntity> merchant = merchantRepository.findByApiKeyHash(hash);
        return merchant.map(m -> new MerchantPrincipal(m.getId(), m.getName()));
    }
}

