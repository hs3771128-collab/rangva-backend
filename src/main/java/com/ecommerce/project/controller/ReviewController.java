package com.ecommerce.project.controller;

import com.ecommerce.project.payload.ReviewDTO;
import com.ecommerce.project.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    @GetMapping("/public/products/{productId}/reviews")
    public ResponseEntity<List<ReviewDTO>> getReviews(@PathVariable Long productId) {
        return new ResponseEntity<>(reviewService.getReviews(productId), HttpStatus.OK);
    }

    @PostMapping("/products/{productId}/reviews")
    public ResponseEntity<ReviewDTO> addReview(@PathVariable Long productId,
                                               @RequestBody ReviewDTO reviewDTO) {
        return new ResponseEntity<>(reviewService.addReview(productId, reviewDTO), HttpStatus.CREATED);
    }
}
