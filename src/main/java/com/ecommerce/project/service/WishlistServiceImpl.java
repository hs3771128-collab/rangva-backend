package com.ecommerce.project.service;

import com.ecommerce.project.exceptions.ResourceNotFoundException;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.User;
import com.ecommerce.project.model.WishlistItem;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.repository.WishlistItemRepository;
import com.ecommerce.project.util.AuthUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WishlistServiceImpl implements WishlistService {

    @Autowired
    private WishlistItemRepository wishlistItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private AuthUtil authUtil;

    @Override
    public List<ProductDTO> getWishlist() {
        User user = authUtil.loggedInUser();
        return wishlistItemRepository.findByUser_UserIdOrderByCreatedAtDesc(user.getUserId())
                .stream()
                .map(item -> productMapper.toDTO(item.getProduct()))
                .toList();
    }

    @Override
    public ProductDTO addToWishlist(Long productId) {
        User user = authUtil.loggedInUser();
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        if (!wishlistItemRepository.existsByUser_UserIdAndProduct_ProductId(user.getUserId(), productId)) {
            WishlistItem item = new WishlistItem();
            item.setUser(user);
            item.setProduct(product);
            wishlistItemRepository.save(item);
        }

        return productMapper.toDTO(product);
    }

    @Override
    @Transactional
    public void removeFromWishlist(Long productId) {
        User user = authUtil.loggedInUser();
        wishlistItemRepository.deleteByUser_UserIdAndProduct_ProductId(user.getUserId(), productId);
    }
}
