package com.ecommerce.project.service;

import com.ecommerce.project.exceptions.APIException;
import com.ecommerce.project.exceptions.ResourceNotFoundException;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.Review;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.ReviewDTO;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.repository.ReviewRepository;
import com.ecommerce.project.util.AuthUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReviewServiceImpl implements ReviewService {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AuthUtil authUtil;

    @Override
    public List<ReviewDTO> getReviews(Long productId) {
        productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));
        return reviewRepository.findByProduct_ProductIdOrderByCreatedAtDesc(productId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public ReviewDTO addReview(Long productId, ReviewDTO reviewDTO) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        if (reviewDTO.getRating() == null || reviewDTO.getRating() < 1 || reviewDTO.getRating() > 5) {
            throw new APIException("Rating must be between 1 and 5");
        }

        User user = authUtil.loggedInUser();

        if (reviewRepository.existsByProduct_ProductIdAndUser_UserId(productId, user.getUserId())) {
            throw new APIException("You have already reviewed this product");
        }

        Review review = new Review();
        review.setProduct(product);
        review.setUser(user);
        review.setRating(reviewDTO.getRating());
        review.setTitle(reviewDTO.getTitle());
        review.setComment(reviewDTO.getComment());
        review.setCreatedAt(LocalDateTime.now());
        review.setVerifiedPurchase(false);

        return toDTO(reviewRepository.save(review));
    }

    private ReviewDTO toDTO(Review review) {
        ReviewDTO dto = new ReviewDTO();
        dto.setReviewId(review.getReviewId());
        dto.setProductId(review.getProduct().getProductId());
        dto.setRating(review.getRating());
        dto.setTitle(review.getTitle());
        dto.setComment(review.getComment());
        dto.setCreatedAt(review.getCreatedAt());
        dto.setVerifiedPurchase(review.getVerifiedPurchase());
        if (review.getUser() != null) {
            dto.setUserName(review.getUser().getUserName());
        }
        return dto;
    }
}
