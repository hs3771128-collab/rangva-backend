package com.ecommerce.project.controller;

import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.service.WishlistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    @Autowired
    private WishlistService wishlistService;

    @GetMapping
    public ResponseEntity<List<ProductDTO>> getWishlist() {
        return new ResponseEntity<>(wishlistService.getWishlist(), HttpStatus.OK);
    }

    @PostMapping("/{productId}")
    public ResponseEntity<ProductDTO> addToWishlist(@PathVariable Long productId) {
        return new ResponseEntity<>(wishlistService.addToWishlist(productId), HttpStatus.CREATED);
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<String> removeFromWishlist(@PathVariable Long productId) {
        wishlistService.removeFromWishlist(productId);
        return new ResponseEntity<>("Removed from wishlist", HttpStatus.OK);
    }
}
