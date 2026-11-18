package com.ecommerce.project.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Used by the development test gateway page to approve or fail a payment. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MockPaymentRequest {

    @NotNull(message = "orderId is required")
    private Long orderId;

    @NotBlank(message = "gateway is required")
    private String gateway;

    private boolean success;
}
