package com.ecommerce.project.payload;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GatewayInitRequest {

    @NotNull(message = "orderId is required")
    private Long orderId;
}
