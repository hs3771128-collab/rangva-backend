package com.ecommerce.project.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Public order tracking lookup: order id plus the email or phone used at checkout. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrackOrderRequest {

    @NotNull(message = "orderId is required")
    private Long orderId;

    @NotBlank(message = "email or phone is required")
    private String contact;
}
