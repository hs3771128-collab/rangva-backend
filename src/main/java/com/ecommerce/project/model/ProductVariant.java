package com.ecommerce.project.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single purchasable option of a product: a clothing size, a beauty shade,
 * a bottle volume, etc. Fashion products need this the most (S/M/L, 40/41/42),
 * beauty products use it for shades and pack sizes.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "product_variants")
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long variantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Display label shown on the chip: "M", "42", "50ml", "Rosewood". */
    @Column(nullable = false)
    private String label;

    /** SIZE, VOLUME, SHADE, COLOR or ONE. */
    private String variantType;

    private Integer stock = 0;

    /** Optional price difference for this variant (e.g. XXL costs more). */
    private Double extraPrice = 0.0;

    private Integer sortOrder = 0;
}
