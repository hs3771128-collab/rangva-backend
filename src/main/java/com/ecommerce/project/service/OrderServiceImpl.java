package com.ecommerce.project.service;

import com.ecommerce.project.exceptions.APIException;
import com.ecommerce.project.exceptions.ResourceNotFoundException;
import com.ecommerce.project.model.*;
import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.payload.OrderItemDTO;
import com.ecommerce.project.payload.OrderResponse;
import com.ecommerce.project.repository.*;
import com.ecommerce.project.util.AuthUtil;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    CartRepository cartRepository;

    @Autowired
    AddressRepository addressRepository;

    @Autowired
    OrderItemRepository orderItemRepository;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    CartService cartService;

    @Autowired
    ModelMapper modelMapper;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    ProductVariantRepository productVariantRepository;

    @Autowired
    AuthUtil authUtil;

    /** Nationwide delivery is free above this subtotal (PKR). */
    private static final double FREE_DELIVERY_THRESHOLD = 3000.0;
    private static final double DELIVERY_FEE = 199.0;

    /** Payment methods the storefront is allowed to submit. */
    private static final Set<String> SUPPORTED_PAYMENT_METHODS = Set.of(
            "CASH_ON_DELIVERY", "COD", "JAZZCASH", "EASYPAISA", "SAFEPAY", "BANK_TRANSFER");

    @Override
    @Transactional
    public OrderDTO placeOrder(String emailId, Long addressId, String paymentMethod, String pgName, String pgPaymentId, String pgStatus, String pgResponseMessage) {
        if (addressId == null) {
            throw new APIException("Delivery address is required");
        }

        // Getting User Cart
        Cart cart = cartRepository.findCartByEmail(emailId);
        if (cart == null) {
            throw new ResourceNotFoundException("Cart", "email", emailId);
        }

        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", "addressId", addressId));

        List<CartItem> cartItems = cart.getCartItems();
        if (cartItems.isEmpty()) {
            throw new APIException("Cart is empty");
        }

        // Pakistani delivery rules: free over Rs. 3,000, otherwise Rs. 199 nationwide
        double subtotal = cart.getTotalPrice() == null ? 0.0 : cart.getTotalPrice();
        double deliveryFee = subtotal >= FREE_DELIVERY_THRESHOLD ? 0.0 : DELIVERY_FEE;

        String normalizedMethod = paymentMethod == null
                ? "CASH_ON_DELIVERY"
                : paymentMethod.trim().toUpperCase(Locale.ROOT);

        if (!SUPPORTED_PAYMENT_METHODS.contains(normalizedMethod)) {
            throw new APIException("Unsupported payment method: " + paymentMethod);
        }

        boolean cashOnDelivery = normalizedMethod.equals("CASH_ON_DELIVERY") || normalizedMethod.equals("COD");
        boolean alreadyPaid = "PAID".equalsIgnoreCase(pgStatus) || "COMPLETED".equalsIgnoreCase(pgStatus);

        // Only cash on delivery is settled later by the rider; online and bank
        // payments stay "pending" until the gateway callback (or admin) confirms them.
        String orderStatus = cashOnDelivery ? "PLACED" : (alreadyPaid ? "CONFIRMED" : "PENDING_PAYMENT");

        // Create a new order with payment info
        Order order = new Order();
        order.setEmail(emailId);
        order.setOrderDate(LocalDate.now());
        order.setTotalAmount(subtotal + deliveryFee);
        order.setOrderStatus(orderStatus);
        order.setAddress(address);

        String resolvedStatus = pgStatus != null ? pgStatus.toUpperCase(Locale.ROOT)
                : (alreadyPaid ? "PAID" : "PENDING");
        String resolvedMessage = pgResponseMessage != null ? pgResponseMessage
                : (cashOnDelivery ? "Cash on delivery" : "Awaiting payment confirmation");

        Payment payment = new Payment(normalizedMethod,
                pgPaymentId,
                resolvedStatus,
                resolvedMessage,
                pgName == null ? normalizedMethod : pgName);
        payment.setOrder(order);
        payment = paymentRepository.save(payment);
        order.setPayment(payment);

        Order savedOrder = orderRepository.save(order);

        // Move cart items into order items
        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItem cartItem : cartItems) {
            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(cartItem.getProduct());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setDiscount(cartItem.getDiscount());
            orderItem.setOrderedProductPrice(cartItem.getProductPrice());
            orderItem.setVariantLabel(cartItem.getVariantLabel());
            orderItem.setOrder(savedOrder);
            orderItems.add(orderItem);
        }

        orderItems = orderItemRepository.saveAll(orderItems);

        // Update product and variant stock
        cart.getCartItems().forEach(item -> {
            int quantity = item.getQuantity();
            Product product = item.getProduct();

            product.setQuantity(Math.max(0, product.getQuantity() - quantity));
            productRepository.save(product);

            if (item.getVariantLabel() != null) {
                ProductVariant variant = productVariantRepository
                        .findByProductAndLabel(product.getProductId(), item.getVariantLabel());
                if (variant != null) {
                    variant.setStock(Math.max(0, variant.getStock() - quantity));
                    productVariantRepository.save(variant);
                }
            }

            cartService.deleteProductFromCart(cart.getCartId(), item.getProduct().getProductId());
        });

        // Send back the order summary
        OrderDTO orderDTO = modelMapper.map(savedOrder, OrderDTO.class);
        orderItems.forEach(item -> orderDTO.getOrderItems().add(modelMapper.map(item, OrderItemDTO.class)));

        orderDTO.setAddressId(addressId);

        return orderDTO;
    }

    @Override
    @Transactional
    public OrderDTO getOrderForUser(String emailId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", orderId));

        if (!order.getEmail().equalsIgnoreCase(emailId)) {
            throw new APIException("This order does not belong to your account");
        }

        OrderDTO orderDTO = modelMapper.map(order, OrderDTO.class);
        if (order.getAddress() != null) {
            orderDTO.setAddressId(order.getAddress().getAddressId());
        }
        return orderDTO;
    }

    @Override
    public OrderResponse getAllOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);
        Page<Order> pageOrders = orderRepository.findAll(pageDetails);
        List<Order> orders = pageOrders.getContent();
        List<OrderDTO> orderDTOs = orders.stream()
                .map(order -> modelMapper.map(order, OrderDTO.class))
                .toList();
        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setContent(orderDTOs);
        orderResponse.setPageNumber(pageOrders.getNumber());
        orderResponse.setPageSize(pageOrders.getSize());
        orderResponse.setTotalElements(pageOrders.getTotalElements());
        orderResponse.setTotalPages(pageOrders.getTotalPages());
        orderResponse.setLastPage(pageOrders.isLast());
        return orderResponse;
    }

    // This is for updating the order Status like shipped, delivered .,etc
    @Override
    public OrderDTO updateOrder(Long orderId, String status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order","orderId",orderId));
        order.setOrderStatus(status);
        orderRepository.save(order);
        return modelMapper.map(order, OrderDTO.class);
    }

    @Override
    public OrderResponse getAllSellerOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);

        User seller = authUtil.loggedInUser();

        Page<Order> pageOrders = orderRepository.findAll(pageDetails);

        List<Order> sellerOrders = pageOrders.getContent().stream()
                .filter(order -> order.getOrderItems().stream()
                        .anyMatch(orderItem -> {
                            var product = orderItem.getProduct();
                            if (product == null || product.getUser() == null) {  //So if the product is not associated with any seller then in that case don't take that product and just skip it.
                                return false;
                            }
                            return product.getUser().getUserId().equals(
                                    seller.getUserId());
                        }))
                .toList();

        List<OrderDTO> orderDTOs = sellerOrders.stream()
                .map(order -> modelMapper.map(order, OrderDTO.class))
                .toList();
        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setContent(orderDTOs);
        orderResponse.setPageNumber(pageOrders.getNumber());
        orderResponse.setPageSize(pageOrders.getSize());
        orderResponse.setTotalElements(pageOrders.getTotalElements());
        orderResponse.setTotalPages(pageOrders.getTotalPages());
        orderResponse.setLastPage(pageOrders.isLast());
        return orderResponse;
    }
}
