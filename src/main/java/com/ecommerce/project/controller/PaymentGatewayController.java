package com.ecommerce.project.controller;

import com.ecommerce.project.payload.GatewayInitRequest;
import com.ecommerce.project.payload.GatewayInitResponse;
import com.ecommerce.project.payload.PaymentMethodsResponse;
import com.ecommerce.project.payment.PaymentGatewayService;
import com.ecommerce.project.util.AuthUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;

/**
 * Hosted payment gateway endpoints (JazzCash + Easypaisa).
 *
 * <p>{@code /api/payments/{gateway}/init} needs a signed-in customer, while the
 * callback endpoints are called directly by the gateway and are therefore
 * public. Every callback is validated inside
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
}
