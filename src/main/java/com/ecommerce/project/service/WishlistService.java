package com.ecommerce.project.service;

import com.ecommerce.project.payload.ProductDTO;

import java.util.List;

public interface WishlistService {
    List<ProductDTO> getWishlist();

    ProductDTO addToWishlist(Long productId);

    void removeFromWishlist(Long productId);
}
