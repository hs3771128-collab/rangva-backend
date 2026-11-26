package com.ecommerce.project.service;

import com.ecommerce.project.exceptions.APIException;
import com.ecommerce.project.exceptions.ResourceNotFoundException;
import com.ecommerce.project.model.Cart;
import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.ProductVariant;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.CartDTO;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.payload.ProductResponse;
import com.ecommerce.project.payload.ProductVariantDTO;
import com.ecommerce.project.repository.CartRepository;
import com.ecommerce.project.repository.CategoryRepository;
import com.ecommerce.project.repository.OrderItemRepository;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.repository.ProductVariantRepository;
import com.ecommerce.project.repository.ReviewRepository;
import com.ecommerce.project.repository.WishlistItemRepository;
import com.ecommerce.project.util.AuthUtil;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.criteria.Subquery;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

    @Autowired
    CartRepository cartRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private WishlistItemRepository wishlistItemRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private FileService fileService;

    @Value("${project.image}")
    private String path;

    @Autowired
    AuthUtil authUtil;

    @Override
    @Transactional
    public ProductDTO addProduct(Long categoryId, ProductDTO productDTO) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Category", "categoryId", categoryId));

        boolean isProductNotPresent = true;

        List<Product> products = category.getProducts();
        for (Product value : products) {
            if (value.getProductName().equals(productDTO.getProductName())) {
                isProductNotPresent = false;
                break;
            }
        }
        if (isProductNotPresent) {
            Product product = modelMapper.map(productDTO, Product.class);
            product.setProductId(null);
            product.setImage("default.png");
            product.setCategory(category);
            product.setUser(authUtil.loggedInUser());
            product.setDepartment(resolveDepartment(productDTO, category));
            double specialPrice = product.getPrice() -
                    ((product.getDiscount() * 0.01) * product.getPrice());
            product.setSpecialPrice(specialPrice);
            Product savedProduct = productRepository.save(product);

            saveVariants(savedProduct, productDTO.getVariants());

            return productMapper.toDTO(savedProduct);
        } else {
            throw new APIException("Product already exist!!");
        }
    }

    private String resolveDepartment(ProductDTO productDTO, Category category) {
        if (productDTO.getDepartment() != null && !productDTO.getDepartment().isBlank()) {
            return productDTO.getDepartment().toUpperCase();
        }
        String name = category.getCategoryName() == null ? "" : category.getCategoryName().toLowerCase();
        boolean beauty = name.contains("skin") || name.contains("makeup") || name.contains("hair")
                || name.contains("fragrance") || name.contains("beauty");
        return beauty ? "BEAUTY" : "FASHION";
    }

    private void saveVariants(Product product, List<ProductVariantDTO> variants) {
        if (variants == null || variants.isEmpty()) {
            return;
        }
        int order = 0;
        for (ProductVariantDTO dto : variants) {
            if (dto.getLabel() == null || dto.getLabel().isBlank()) {
                continue;
            }
            ProductVariant variant = new ProductVariant();
            variant.setProduct(product);
            variant.setLabel(dto.getLabel().trim());
            variant.setVariantType(dto.getVariantType() == null ? "ONE" : dto.getVariantType());
            variant.setStock(dto.getStock() == null ? 0 : dto.getStock());
            variant.setExtraPrice(dto.getExtraPrice() == null ? 0.0 : dto.getExtraPrice());
            variant.setSortOrder(order++);
            productVariantRepository.save(variant);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getAllProducts(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder,
                                          String keyword, String category, String department, String brand,
                                          Double minPrice, Double maxPrice, String size) {
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();

        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);

        Specification<Product> spec = Specification.where(null);

        if (keyword != null && !keyword.isEmpty()) {
            String like = "%" + keyword.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("productName")), like),
                    cb.like(cb.lower(root.get("brand")), like),
                    cb.like(cb.lower(root.get("description")), like)
            ));
        }

        if (category != null && !category.isEmpty()) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(cb.lower(root.get("category").get("categoryName")), category.toLowerCase()));
        }

        if (department != null && !department.isEmpty() && !department.equalsIgnoreCase("all")) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(cb.upper(root.get("department")), department.toUpperCase()));
        }

        if (brand != null && !brand.isEmpty() && !brand.equalsIgnoreCase("all")) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(cb.lower(root.get("brand")), brand.toLowerCase()));
        }

        if (minPrice != null) {
            spec = spec.and((root, query, cb) -> cb.ge(root.get("specialPrice"), minPrice));
        }

        if (maxPrice != null) {
            spec = spec.and((root, query, cb) -> cb.le(root.get("specialPrice"), maxPrice));
        }

        if (size != null && !size.isEmpty() && !size.equalsIgnoreCase("all")) {
            spec = spec.and((root, query, cb) -> {
                Subquery<Long> sub = query.subquery(Long.class);
                var variantRoot = sub.from(ProductVariant.class);
                sub.select(variantRoot.get("product").get("productId"))
                        .where(cb.equal(cb.lower(variantRoot.get("label")), size.toLowerCase()),
                                cb.greaterThan(variantRoot.get("stock"), 0));
                return root.get("productId").in(sub);
            });
        }

        Page<Product> pageProducts = productRepository.findAll(spec, pageDetails);
        return toResponse(pageProducts);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDTO getProductById(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));
        return productMapper.toDTO(product);
    }

    private ProductResponse toResponse(Page<Product> pageProducts) {
        List<ProductDTO> productDTOS = pageProducts.getContent().stream()
                .map(productMapper::toDTO)
                .toList();

        ProductResponse productResponse = new ProductResponse();
        productResponse.setContent(productDTOS);
        productResponse.setPageNumber(pageProducts.getNumber());
        productResponse.setPageSize(pageProducts.getSize());
        productResponse.setTotalElements(pageProducts.getTotalElements());
        productResponse.setTotalPages(pageProducts.getTotalPages());
        productResponse.setLastPage(pageProducts.isLast());
        return productResponse;
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse searchByCategory(Long categoryId, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Category", "categoryId", categoryId));

        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();

        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);
        Page<Product> pageProducts = productRepository.findByCategoryOrderByPriceAsc(category, pageDetails);

        if (pageProducts.getContent().isEmpty()) {
            throw new APIException(category.getCategoryName() + " category does not have any products");
        }

        return toResponse(pageProducts);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse searchProductByKeyword(String keyword, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {

        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();

        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);
        Page<Product> pageProducts = productRepository.findByProductNameLikeIgnoreCase('%' + keyword + '%', pageDetails);

        if (pageProducts.getContent().isEmpty()) {
            throw new APIException("Products not found with keyword:" + keyword);
        }

        return toResponse(pageProducts);
    }

    @Override
    @Transactional
    public ProductDTO updateProduct(Long productId, ProductDTO productDTO) {
        Product existingProduct = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        existingProduct.setProductName(productDTO.getProductName());
        existingProduct.setDescription(productDTO.getDescription());
        existingProduct.setQuantity(productDTO.getQuantity());
        existingProduct.setDiscount(productDTO.getDiscount());
        existingProduct.setPrice(productDTO.getPrice());

        if (productDTO.getBrand() != null) {
            existingProduct.setBrand(productDTO.getBrand());
        }
        if (productDTO.getDepartment() != null) {
            existingProduct.setDepartment(productDTO.getDepartment());
        }

        double specialPrice = existingProduct.getPrice() -
                ((existingProduct.getDiscount() * 0.01) * existingProduct.getPrice());
        existingProduct.setSpecialPrice(specialPrice);

        Product savedProduct = productRepository.save(existingProduct);

        if (productDTO.getVariants() != null) {
            productVariantRepository.deleteByProductId(productId);
            saveVariants(savedProduct, productDTO.getVariants());
        }

        // Keep every cart that holds this product in sync with the new price
        List<Cart> carts = cartRepository.findCartsByProductId(productId);
        List<CartDTO> cartDTOs = carts.stream().map(cart -> {
            CartDTO cartDTO = modelMapper.map(cart, CartDTO.class);
            cartDTO.setProducts(cart.getCartItems().stream()
                    .map(p -> modelMapper.map(p.getProduct(), ProductDTO.class))
                    .collect(Collectors.toList()));
            return cartDTO;
        }).collect(Collectors.toList());

        cartDTOs.forEach(cart -> cartService.updateProductInCarts(cart.getCartId(), productId));

        return productMapper.toDTO(savedProduct);
    }

    @Override
    @Transactional
    public ProductDTO deleteProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        // Order history must keep pointing at the product row, and sellers should
        // not be able to erase evidence of past sales.
        if (orderItemRepository.existsByProduct_ProductId(productId)) {
            throw new APIException("Yeh product delete nahi ho sakta kyunke is ke orders maujood hain. "
                    + "Store se hatane ke liye stock 0 kar dein.");
        }

        List<Cart> carts = cartRepository.findCartsByProductId(productId);
        carts.forEach(cart -> cartService.deleteProductFromCart(cart.getCartId(), productId));

        ProductDTO dto = productMapper.toDTO(product);

        // The cart-item rows were removed with bulk queries, so the eagerly
        // loaded collection on the product still holds stale entities. Clear it
        // before the flush, otherwise Product's cascade tries to re-persist them.
        product.getProducts().clear();

        wishlistItemRepository.deleteByProduct_ProductId(productId);
        reviewRepository.deleteByProduct_ProductId(productId);
        productVariantRepository.deleteByProductId(productId);
        productRepository.deleteProductRow(productId);

        return dto;
    }

    @Override
    public ProductDTO updateProductImage(Long productId, MultipartFile image) throws IOException {
        Product productFromDb = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        String fileName = fileService.uploadImage(path, image);
        productFromDb.setImage(fileName);
        Product updatedProduct = productRepository.save(productFromDb);

        return productMapper.toDTO(updatedProduct);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getAllProductsForAdmin(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();

        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);
        Page<Product> pageProducts = productRepository.findAll(pageDetails);
        return toResponse(pageProducts);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getAllProductsForSeller(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();

        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);

        User user = authUtil.loggedInUser();
        Page<Product> pageProducts = productRepository.findByUser(user, pageDetails);
        return toResponse(pageProducts);
    }

    /**
     * Guards the /api/seller/** product routes: a seller can only touch products
     * they own (admins use the /api/admin/** routes instead).
     */
    @Override
    public void assertSellerOwnsProduct(Long productId) {
        User seller = authUtil.loggedInUser();
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        if (product.getUser() == null || product.getUser().getUserId() == null
                || !product.getUser().getUserId().equals(seller.getUserId())) {
            throw new APIException("Yeh product aap ke account ka nahi hai.");
        }
    }
}
