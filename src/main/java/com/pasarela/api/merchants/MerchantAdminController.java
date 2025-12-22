package com.pasarela.api.merchants;

import com.pasarela.application.MerchantService;
import com.pasarela.application.routing.RoutingWeights;
import com.pasarela.domain.model.PaymentProvider;
import com.pasarela.infrastructure.persistence.entity.MerchantEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/merchants")
public class MerchantAdminController {
    private final MerchantService merchantService;

    public MerchantAdminController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @PostMapping
    public CreateMerchantResponse create(@RequestBody CreateMerchantRequest req) {
        var created = merchantService.create(req.name());
        return new CreateMerchantResponse(toDto(created.merchant()), created.apiKey());
    }

    @GetMapping
    public List<MerchantDto> list() {
        return merchantService.list().stream().map(this::toDto).toList();
    }

    @PatchMapping("/{id}/routing-config")
    public MerchantDto patchRoutingConfig(@PathVariable("id") UUID merchantId, @RequestBody RoutingConfigPatchRequest req) {
        MerchantEntity updated = merchantService.updateRoutingConfig(merchantId, new MerchantService.RoutingConfigPatch(
                req.forceProvider(),
                req.weights(),
                req.costModel()
        ));
        return toDto(updated);
    }

    private MerchantDto toDto(MerchantEntity entity) {
        return new MerchantDto(entity.getId(), entity.getName(), entity.getConfigJson(), entity.getCreatedAt());
    }

    public record CreateMerchantRequest(
            @NotBlank @Size(max = 120) String name
    ) {}

    public record CreateMerchantResponse(
            MerchantDto merchant,
            String apiKey
    ) {}

    public record RoutingConfigPatchRequest(
            String forceProvider,
            RoutingWeights weights,
            Map<PaymentProvider, Double> costModel
    ) {}

    public record MerchantDto(
            UUID id,
            String name,
            String configJson,
            Instant createdAt
    ) {}
}
