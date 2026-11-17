package com.ecommerce.project.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItemDTO {
    private Long productId;
    private Integer quantity;
    /** Optional: selected size / shade / volume. */
    private String variantLabel;

    public CartItemDTO(Long productId, Integer quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }
}
