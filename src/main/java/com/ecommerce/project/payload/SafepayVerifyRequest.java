package com.ecommerce.project.payload;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Asked by the confirmation page after Safepay redirects the customer back. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SafepayVerifyRequest {

    @NotBlank(message = "tracker is required")
    private String tracker;

    /** Optional fallback when the tracker could not be stored during init. */
    private Long orderId;
}
