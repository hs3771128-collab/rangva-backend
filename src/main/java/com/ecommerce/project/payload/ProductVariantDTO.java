package com.ecommerce.project.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariantDTO {
    private Long variantId;
    private String label;
    private String variantType;
    private Integer stock;
    private Double extraPrice;
    private Integer sortOrder;
}
