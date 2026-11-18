package com.ecommerce.project.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Everything the browser needs to hand the customer over to a hosted payment
 * page: the gateway URL and the signed form fields that must be POSTed to it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GatewayInitResponse {

    private String gateway;
    private String redirectUrl;
    private Map<String, String> fields;
    private Long orderId;
    private Double amount;
    /** Gateway-side reference (JazzCash txn ref, Easypaisa order ref, Safepay tracker). */
    private String reference;
}
