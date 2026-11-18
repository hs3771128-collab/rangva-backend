package com.ecommerce.project.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fails fast when the development test mode is combined with a production
 * profile, and prints a loud reminder otherwise. Test mode simulates payments,
 * so it must never be reachable on a live store.
 */
@Component
public class PaymentTestModeGuard {

    private static final Logger log = LoggerFactory.getLogger(PaymentTestModeGuard.class);

    public PaymentTestModeGuard(PaymentGatewayProperties properties, Environment environment) {
        if (!properties.isTestMode()) {
            return;
        }
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                throw new IllegalStateException(
                        "PAYMENTS_TEST_MODE must not be enabled with a production profile");
            }
        }
        log.warn("================================================================");
        log.warn(" PAYMENTS TEST MODE IS ON: gateways without credentials are");
        log.warn(" simulated and NO REAL MONEY MOVES. Unset PAYMENTS_TEST_MODE");
        log.warn(" before going live.");
        log.warn("================================================================");
    }
}
