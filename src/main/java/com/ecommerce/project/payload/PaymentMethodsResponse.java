package com.ecommerce.project.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tells the storefront which online gateways are actually configured, so the
 * checkout only offers payment methods that can complete a payment.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethodsResponse {

    private boolean jazzcash;
    private boolean easypaisa;
    private boolean safepay;
}
