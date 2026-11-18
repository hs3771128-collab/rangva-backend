package com.ecommerce.project.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of checking a payment after the customer returns from a hosted
 * checkout page.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GatewayStatusResponse {

    private Long orderId;
    private String status;
    private boolean paid;
    private String message;
}
