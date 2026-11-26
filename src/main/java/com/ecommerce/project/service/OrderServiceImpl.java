package com.ecommerce.project.service;

import com.ecommerce.project.exceptions.APIException;
import com.ecommerce.project.exceptions.ResourceNotFoundException;
import com.ecommerce.project.model.*;
import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.payload.OrderItemDTO;
import com.ecommerce.project.payload.OrderResponse;
import com.ecommerce.project.payload.OrderTrackingResponse;
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
    @Transactional
    public OrderTrackingResponse trackOrder(Long orderId, String contact) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new APIException(NOT_FOUND_MESSAGE));

        if (!contactMatches(order, contact)) {
            // Same message for "no such order" and "wrong contact" so the
            // public page cannot be used to guess order ids.
            throw new APIException(NOT_FOUND_MESSAGE);
        }

        String status = order.getOrderStatus() == null
                ? ""
                : order.getOrderStatus().trim().toUpperCase(Locale.ROOT);
        boolean cancelled = status.contains("CANCEL");
        boolean confirmed = cancelled || Set.of("PLACED", "CONFIRMED", "PROCESSING",
                "ACCEPTED", "SHIPPED", "DELIVERED").contains(status);
        boolean shipped = !cancelled && Set.of("SHIPPED", "DELIVERED").contains(status);
        boolean delivered = !cancelled && status.equals("DELIVERED");

        List<OrderTrackingResponse.TrackingStep> steps = new ArrayList<>();
        steps.add(new OrderTrackingResponse.TrackingStep(
                "Order placed", "Aap ka order receive ho gaya", true, false));
        steps.add(new OrderTrackingResponse.TrackingStep(
                "Confirmed", "Order confirm ho kar packing ke liye tayyar hai", confirmed, false));
        steps.add(new OrderTrackingResponse.TrackingStep(
                "Shipped", "Parcel courier ko de diya gaya hai", shipped, false));
        steps.add(new OrderTrackingResponse.TrackingStep(
                "Delivered", "Parcel aap ke pate par pohanch gaya", delivered, false));

        if (!cancelled) {
            boolean currentMarked = false;
            for (OrderTrackingResponse.TrackingStep step : steps) {
                if (!step.isDone() && !currentMarked) {
                    step.setCurrent(true);
                    currentMarked = true;
                }
            }
            if (!currentMarked && !steps.isEmpty()) {
                steps.get(steps.size() - 1).setCurrent(true);
            }
        }

        List<OrderTrackingResponse.TrackingItem> items = new ArrayList<>();
        List<OrderItem> orderItems = order.getOrderItems() == null ? List.of() : order.getOrderItems();
        for (OrderItem item : orderItems) {
            String productName = item.getProduct() != null
                    ? item.getProduct().getProductName()
                    : "Item";
            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
            items.add(new OrderTrackingResponse.TrackingItem(
                    productName,
                    item.getVariantLabel(),
                    quantity,
                    item.getOrderedProductPrice() * quantity));
        }

        Payment payment = order.getPayment();
        Address address = order.getAddress();
        String city = address != null ? address.getCity() : null;
        String recipient = address == null ? null
                : firstNonNull(address.getBuildingName(), address.getArea(), address.getCity());

        LocalDate orderDate = order.getOrderDate() == null ? LocalDate.now() : order.getOrderDate();
        int days = isFastDeliveryCity(city) ? 4 : 6;

        return new OrderTrackingResponse(
                order.getOrderId(),
                orderDate,
                order.getOrderStatus(),
                statusLabel(status),
                statusNote(status),
                payment == null ? null : paymentMethodLabel(payment.getPaymentMethod()),
                payment == null ? "PENDING" : paymentStatusLabel(payment.getPgStatus()),
                order.getTotalAmount(),
                items.stream().mapToInt(OrderTrackingResponse.TrackingItem::getQuantity).sum(),
                city,
                recipient,
                orderDate.plusDays(days),
                cancelled,
                null,
                null,
                items,
                steps);
    }

    private static final String NOT_FOUND_MESSAGE =
            "Order nahi mila — Order ID aur email/phone check karein";

    private boolean contactMatches(Order order, String contact) {
        if (contact == null || contact.isBlank()) {
            return false;
        }
        String value = contact.trim();
        if (order.getEmail() != null && order.getEmail().equalsIgnoreCase(value)) {
            return true;
        }
        Address address = order.getAddress();
        if (address != null && address.getPhone() != null) {
            String saved = address.getPhone().replaceAll("[^0-9]", "");
            String given = value.replaceAll("[^0-9]", "");
            return saved.length() >= 10 && saved.equals(given);
        }
        return false;
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "PLACED" -> "Order placed";
            case "PENDING_PAYMENT" -> "Payment pending";
            case "PAYMENT_FAILED" -> "Payment failed";
            case "CONFIRMED", "ACCEPTED" -> "Confirmed";
            case "PROCESSING" -> "Processing";
            case "SHIPPED", "OUT_FOR_DELIVERY" -> "Shipped";
            case "DELIVERED" -> "Delivered";
            case "CANCELLED", "CANCELED" -> "Cancelled";
            default -> status.isBlank() ? "Order placed" : status;
        };
    }

    private String statusNote(String status) {
        return switch (status) {
            case "PENDING_PAYMENT" -> "Payment confirm hote hi order ship ke liye tayyar ho jayega.";
            case "PAYMENT_FAILED" -> "Payment mukammal nahi hui — confirmation page se dobara koshish karein.";
            case "PLACED", "CONFIRMED", "ACCEPTED" -> "Order confirm hai aur packing ke liye tayyar hai.";
            case "PROCESSING" -> "Order pack ho raha hai — jald courier ko diya jayega.";
            case "SHIPPED", "OUT_FOR_DELIVERY" -> "Parcel raaste me hai, 2–4 working days me pohanch jayega.";
            case "DELIVERED" -> "Parcel deliver ho chuka hai — shukriya!";
            case "CANCELLED", "CANCELED" -> "Yeh order cancel kar diya gaya hai.";
            default -> "Aap ka order humare paas hai.";
        };
    }

    private String paymentMethodLabel(String method) {
        if (method == null) {
            return "Cash on Delivery";
        }
        return switch (method.trim().toUpperCase(Locale.ROOT)) {
            case "CASH_ON_DELIVERY", "COD" -> "Cash on Delivery";
            case "JAZZCASH" -> "JazzCash";
            case "EASYPAISA" -> "Easypaisa";
            case "SAFEPAY" -> "Card (Safepay)";
            case "BANK_TRANSFER" -> "Bank Transfer";
            default -> method;
        };
    }

    private String paymentStatusLabel(String status) {
        if (status == null) {
            return "PENDING";
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "PAID", "COMPLETED" -> "PAID";
            case "FAILED" -> "FAILED";
            case "REFUNDED" -> "REFUNDED";
            default -> "PENDING";
        };
    }

    private boolean isFastDeliveryCity(String city) {
        if (city == null) {
            return false;
        }
        String value = city.trim().toLowerCase(Locale.ROOT);
        return value.equals("karachi") || value.equals("lahore") || value.equals("islamabad");
    }

    private String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
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

        // Proper server-side pagination: only orders that contain this seller's
        // products are counted, so totals/pages are correct.
        Page<Order> pageOrders = orderRepository.findBySeller(seller, pageDetails);

        List<OrderDTO> orderDTOs = pageOrders.getContent().stream()
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

    /**
     * A seller may only change the status of an order that contains one of their
     * own products. Admins (who use /api/admin/orders/{id}/status) are not routed
     * through this check.
     */
    @Override
    public OrderDTO updateOrderForSeller(Long orderId, String status) {
        User seller = authUtil.loggedInUser();
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", orderId));

        boolean ownsItem = order.getOrderItems().stream()
                .anyMatch(item -> item.getProduct() != null
                        && item.getProduct().getUser() != null
                        && item.getProduct().getUser().getUserId().equals(seller.getUserId()));

        if (!ownsItem) {
            throw new APIException("Yeh order aap ke products ka nahi hai.");
        }

        order.setOrderStatus(status);
        orderRepository.save(order);
        return modelMapper.map(order, OrderDTO.class);
    }
}
