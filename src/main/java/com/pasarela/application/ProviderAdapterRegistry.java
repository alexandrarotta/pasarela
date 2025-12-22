package com.pasarela.application;

import com.pasarela.domain.model.PaymentProvider;
import com.pasarela.infrastructure.provider.PaymentProviderAdapter;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class ProviderAdapterRegistry {
    private final Map<PaymentProvider, PaymentProviderAdapter> adapters;

    public ProviderAdapterRegistry(List<PaymentProviderAdapter> adapters) {
        EnumMap<PaymentProvider, PaymentProviderAdapter> map = new EnumMap<>(PaymentProvider.class);
        for (PaymentProviderAdapter adapter : adapters) {
            map.put(adapter.provider(), adapter);
        }
        this.adapters = Map.copyOf(map);
    }

    public PaymentProviderAdapter get(PaymentProvider provider) {
        PaymentProviderAdapter adapter = adapters.get(provider);
        if (adapter == null) throw new IllegalStateException("No adapter for " + provider);
        return adapter;
    }
}

