package com.ecommerce.project.controller;

import com.ecommerce.project.payload.GatewayInitRequest;
import com.ecommerce.project.payload.GatewayInitResponse;
import com.ecommerce.project.payload.GatewayStatusResponse;
import com.ecommerce.project.payload.PaymentMethodsResponse;
import com.ecommerce.project.payload.SafepayVerifyRequest;
import com.ecommerce.project.payment.PaymentGatewayService;
import com.ecommerce.project.util.AuthUtil;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;

/**
 * Hosted payment gateway endpoints (JazzCash, Easypaisa, Safepay cards).
 *
 * <p>{@code /api/payments/{gateway}/init} needs a signed-in customer, while
 * callbacks and webhooks are called directly by the gateway and are therefore
 * public. Every callback/webhook is validated inside
 * {@link PaymentGatewayService} before an order is marked paid.</p>
 */
@RestController
@RequestMapping("/api")
public class PaymentGatewayController {

    private final PaymentGatewayService paymentGatewayService;
    private final AuthUtil authUtil;

    public PaymentGatewayController(PaymentGatewayService paymentGatewayService, AuthUtil authUtil) {
        this.paymentGatewayService = paymentGatewayService;
        this.authUtil = authUtil;
    }

    /** Which online gateways are configured, so the checkout only offers working methods. */
    @GetMapping("/public/payments/methods")
    public PaymentMethodsResponse methods() {
        return paymentGatewayService.availableMethods();
    }

    /** Starts a payment for an existing order and returns the signed gateway form. */
    @PostMapping("/payments/{gateway}/init")
    public GatewayInitResponse init(@PathVariable String gateway,
                                    @Valid @RequestBody GatewayInitRequest request) {
        return paymentGatewayService.init(gateway, request.getOrderId(), authUtil.loggedInEmail());
    }

    @RequestMapping(value = "/payments/jazzcash/callback",
            method = {RequestMethod.GET, RequestMethod.POST})
    public RedirectView jazzCashCallback(@RequestParam Map<String, String> params) {
        return new RedirectView(paymentGatewayService.handleJazzCashCallback(params));
    }

    @RequestMapping(value = "/payments/easypaisa/callback",
            method = {RequestMethod.GET, RequestMethod.POST})
    public RedirectView easypaisaCallback(@RequestParam Map<String, String> params) {
        return new RedirectView(paymentGatewayService.handleEasypaisaCallback(params));
    }

    /**
     * Safepay webhook. The raw body is needed because the
     * {@code X-SFPY-SIGNATURE} header is an HMAC of exactly those bytes.
     */
    @PostMapping("/payments/safepay/webhook")
    public ResponseEntity<String> safepayWebhook(
            @RequestBody(required = false) String rawBody,
            @RequestHeader(value = "X-SFPY-SIGNATURE", required = false) String signature) {
        boolean accepted = paymentGatewayService.handleSafepayWebhook(rawBody, signature);
        if (!accepted) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid signature");
        }
        return ResponseEntity.ok("ok");
    }

    /** Used by the confirmation page when Safepay redirects the customer back. */
    @PostMapping("/payments/safepay/verify")
    public GatewayStatusResponse verifySafepay(@Valid @RequestBody SafepayVerifyRequest request) {
        return paymentGatewayService.verifySafepay(
                request.getTracker(), request.getOrderId(), authUtil.loggedInEmail());
    }
}
