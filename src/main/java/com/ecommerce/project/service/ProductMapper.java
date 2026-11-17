package com.ecommerce.project.service;

import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.ProductVariant;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.payload.ProductVariantDTO;
import com.ecommerce.project.repository.ProductVariantRepository;
import com.ecommerce.project.repository.ReviewRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Single place that turns a Product entity into the DTO the storefront uses.
 * Keeps image URLs, variants and rating info consistent everywhere.
 */
@Component
public class ProductMapper {

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private ProductVariantRepository variantRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Value("${image.base.url}")
    private String imageBaseUrl;

    public ProductDTO toDTO(Product product) {
        ProductDTO dto = modelMapper.map(product, ProductDTO.class);
        dto.setImage(constructImageUrl(product.getImage()));

        if (product.getCategory() != null) {
            dto.setCategoryId(product.getCategory().getCategoryId());
            dto.setCategoryName(product.getCategory().getCategoryName());
        }

        List<ProductVariant> variants =
                variantRepository.findByProduct_ProductIdOrderBySortOrderAsc(product.getProductId());
        dto.setVariants(variants.stream()
                .map(v -> new ProductVariantDTO(v.getVariantId(), v.getLabel(), v.getVariantType(),
                        v.getStock(), v.getExtraPrice(), v.getSortOrder()))
                .toList());

        Double average = reviewRepository.averageRating(product.getProductId());
        dto.setAvgRating(average == null ? 0.0 : Math.round(average * 10.0) / 10.0);
        dto.setReviewCount(reviewRepository.countByProduct(product.getProductId()));

        return dto;
    }

    public String constructImageUrl(String imageName) {
        if (imageName == null || imageName.isBlank()) {
            return imageName;
        }
        if (imageName.startsWith("http://") || imageName.startsWith("https://")) {
            return imageName;
        }
        return imageBaseUrl.endsWith("/") ? imageBaseUrl + imageName : imageBaseUrl + "/" + imageName;
    }
}
