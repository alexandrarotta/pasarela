package com.pasarela.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pasarela.application.WebhookService.AdyenNotificationItem;
import com.pasarela.application.WebhookService.AdyenNotificationItemWrapper;
import com.pasarela.application.WebhookService.AdyenNotificationRequest;
import com.pasarela.application.WebhookService.Amount;
import com.pasarela.infrastructure.persistence.repository.PaymentEventRepository;
import com.pasarela.infrastructure.persistence.repository.PaymentIntentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WebhookServiceTest {
    @Autowired
    private WebhookService webhookService;

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    @Autowired
    private PaymentIntentRepository paymentIntentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.providers.adyen.hmacKey}")
    private String hmacKeyBase64;

    @Test
    void adyenWebhookWithUnknownPaymentIntentIsIgnored() throws Exception {
        String merchantReference = UUID.randomUUID().toString();
        String payload = buildAdyenPayload(merchantReference);

        String response = webhookService.handleAdyen(payload);

        assertEquals("[accepted]", response);
        assertEquals(0, paymentIntentRepository.count());
        assertEquals(0, paymentEventRepository.count());
    }

    private String buildAdyenPayload(String merchantReference) throws Exception {
        AdyenNotificationItem item = new AdyenNotificationItem();
        item.merchantReference = merchantReference;
        item.eventCode = "AUTHORISATION";
        item.success = "true";
        item.pspReference = "psp_test_ref";
        item.originalReference = "";
        item.merchantAccountCode = "test_account";
        Amount amount = new Amount();
        amount.currency = "EUR";
        amount.value = 1500L;
        item.amount = amount;
        item.additionalData = Map.of("hmacSignature", signAdyenItem(item));

        AdyenNotificationItemWrapper wrapper = new AdyenNotificationItemWrapper();
        wrapper.NotificationRequestItem = item;

        AdyenNotificationRequest req = new AdyenNotificationRequest();
        req.notificationItems = List.of(wrapper);

        return objectMapper.writeValueAsString(req);
    }

    private String signAdyenItem(AdyenNotificationItem item) throws Exception {
        String message = String.join(":",
                nvl(item.pspReference),
                nvl(item.originalReference),
                nvl(item.merchantAccountCode),
                nvl(item.merchantReference),
                item.amount == null ? "" : String.valueOf(item.amount.value),
                item.amount == null ? "" : nvl(item.amount.currency),
                nvl(item.eventCode),
                nvl(item.success)
        );

        byte[] key = Base64.getDecoder().decode(hmacKeyBase64);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        byte[] out = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(out);
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
