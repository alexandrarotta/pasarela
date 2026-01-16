package com.pasarela.api.admin;

import com.pasarela.application.routing.ProviderHealthService;
import com.pasarela.application.routing.ProviderSnapshot;
import com.pasarela.domain.model.PaymentProvider;
import com.pasarela.infrastructure.persistence.entity.RoutingDecisionEntity;
import com.pasarela.infrastructure.persistence.repository.RoutingDecisionRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/admin/routing")
public class AdminRoutingController {
    private final ProviderHealthService providerHealthService;
    private final RoutingDecisionRepository routingDecisionRepository;

    public AdminRoutingController(ProviderHealthService providerHealthService, RoutingDecisionRepository routingDecisionRepository) {
        this.providerHealthService = providerHealthService;
        this.routingDecisionRepository = routingDecisionRepository;
    }

    @GetMapping("/health")
    public List<ProviderSnapshot> health() {
        return providerHealthService.getAllSnapshots();
    }

    @GetMapping("/decisions")
    public List<RoutingDecisionEntity> decisions(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(value = "provider", required = false) PaymentProvider provider
    ) {
        return routingDecisionRepository.search(from, to, provider);
    }
}
