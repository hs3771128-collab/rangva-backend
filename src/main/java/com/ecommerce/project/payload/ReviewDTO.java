package com.ecommerce.project.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDTO {
    private Long reviewId;
    private Long productId;
    private String userName;
    private Integer rating;
    private String title;
    private String comment;
    private LocalDateTime createdAt;
    private Boolean verifiedPurchase;
}
