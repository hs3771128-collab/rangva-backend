package com.ecommerce.project.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDTO {
    private Long productId;
    private String productName;
    private String image;
    private String description;
    private Integer quantity;
    private double price;
    private double discount;
    private double specialPrice;

    // Rangva additions
    private String brand;
    /** FASHION or BEAUTY */
    private String department;
    /** Main category name, so cards can show "Women · Kurta". */
    private String categoryName;
    private Long categoryId;
    private List<ProductVariantDTO> variants = new ArrayList<>();
    private Double avgRating;
    private Long reviewCount;
    /** Selected variant label (used by cart lines). */
    private String variantLabel;
}
