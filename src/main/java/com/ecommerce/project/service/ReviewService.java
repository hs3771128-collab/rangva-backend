package com.ecommerce.project.service;

import com.ecommerce.project.payload.ReviewDTO;

import java.util.List;

public interface ReviewService {
    List<ReviewDTO> getReviews(Long productId);

    ReviewDTO addReview(Long productId, ReviewDTO reviewDTO);
}
