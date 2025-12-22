package com.pasarela.api.webhooks;

import com.pasarela.application.WebhookService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {
    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping(value = "/stripe", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void stripe(@RequestBody String payload, @RequestHeader("Stripe-Signature") String signatureHeader) {
        webhookService.handleStripe(payload, signatureHeader);
    }

    @PostMapping(value = "/adyen", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public String adyen(@RequestBody String payload) {
        return webhookService.handleAdyen(payload);
    }
}

