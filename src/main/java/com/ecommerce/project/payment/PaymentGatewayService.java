package com.ecommerce.project.payment;

import com.ecommerce.project.exceptions.APIException;
import com.ecommerce.project.exceptions.ResourceNotFoundException;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.model.Payment;
import com.ecommerce.project.payload.GatewayInitResponse;
import com.ecommerce.project.payload.PaymentMethodsResponse;
import com.ecommerce.project.repository.OrderRepository;
import com.ecommerce.project.repository.PaymentRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

/**
 * Orchestrates the hosted payment gateways: starts a payment for an order,
 * validates the gateway callback and updates the order/payment accordingly.
 */
@Service
public class PaymentGatewayService {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayService.class);

    private static final String PAYMENT_PENDING = "PENDING";
    private static final String PAYMENT_PAID = "PAID";
    private static final String PAYMENT_FAILED = "FAILED";

    private static final String ORDER_PENDING_PAYMENT = "PENDING_PAYMENT";
    private static final String ORDER_PAYMENT_FAILED = "PAYMENT_FAILED";
    private static final String ORDER_CONFIRMED = "CONFIRMED";
    private static final String ORDER_CANCELLED = "CANCELLED";

    private final JazzCashGateway jazzCashGateway;
    private final EasypaisaGateway easypaisaGateway;
    private final PaymentGatewayProperties properties;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    public PaymentGatewayService(JazzCashGateway jazzCashGateway,
                                 EasypaisaGateway easypaisaGateway,
                                 PaymentGatewayProperties properties,
                                 OrderRepository orderRepository,
                                 PaymentRepository paymentRepository) {
        this.jazzCashGateway = jazzCashGateway;
        this.easypaisaGateway = easypaisaGateway;
        this.properties = properties;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
    }

    public PaymentMethodsResponse availableMethods() {
        return new PaymentMethodsResponse(jazzCashGateway.isEnabled(), easypaisaGateway.isEnabled());
    }

    @Transactional
    public GatewayInitResponse init(String gateway, Long orderId, String email) {
        String name = gateway == null ? "" : gateway.trim().toLowerCase(Locale.ROOT);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", orderId));

        if (!order.getEmail().equalsIgnoreCase(email)) {
            throw new APIException("This order does not belong to your account");
        }
        if (ORDER_CANCELLED.equalsIgnoreCase(order.getOrderStatus())) {
            throw new APIException("This order is cancelled");
        }

        Payment payment = order.getPayment();
        if (payment == null) {
            payment = new Payment(name.toUpperCase(Locale.ROOT), null, PAYMENT_PENDING,
                    "Awaiting payment", name);
            payment.setOrder(order);
            order.setPayment(payment);
        }
        if (PAYMENT_PAID.equalsIgnoreCase(payment.getPgStatus())) {
            throw new APIException("This order is already paid");
        }

        GatewayInitResponse response;
        switch (name) {
            case JazzCashGateway.NAME -> {
                requireEnabled(jazzCashGateway.isEnabled(), JazzCashGateway.LABEL);
                response = jazzCashGateway.init(order);
                payment.setPaymentMethod("JAZZCASH");
                payment.setPgName(JazzCashGateway.LABEL);
            }
            case EasypaisaGateway.NAME -> {
                requireEnabled(easypaisaGateway.isEnabled(), EasypaisaGateway.LABEL);
                response = easypaisaGateway.init(order);
                payment.setPaymentMethod("EASYPAISA");
                payment.setPgName(EasypaisaGateway.LABEL);
            }
            default -> throw new APIException("Unknown payment gateway: " + gateway);
        }

        payment.setPgStatus(PAYMENT_PENDING);
        payment.setPgResponseMessage("Customer redirected to " + payment.getPgName());
        paymentRepository.save(payment);

        order.setOrderStatus(ORDER_PENDING_PAYMENT);
        orderRepository.save(order);

        return response;
    }

    @Transactional
    public String handleJazzCashCallback(Map<String, String> params) {
        if (!jazzCashGateway.isEnabled()) {
            log.warn("JazzCash callback received but the gateway is not configured");
            return redirect(null, false, JazzCashGateway.NAME);
        }
        if (!jazzCashGateway.verifyCallback(params)) {
            log.warn("JazzCash callback rejected: invalid pp_SecureHash");
            return redirect(null, false, JazzCashGateway.NAME);
        }

        Long orderId = parseLong(params.get("pp_BillReference"));
        Order order = orderId == null ? null : orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("JazzCash callback for unknown order reference {}", params.get("pp_BillReference"));
            return redirect(null, false, JazzCashGateway.NAME);
        }

        Double paidAmount = parseAmountInPaisa(params.get("pp_Amount"));
        if (!amountMatches(order, paidAmount)) {
            log.warn("JazzCash amount mismatch for order {}: expected {} got {}",
                    orderId, order.getTotalAmount(), paidAmount);
            markFailed(order, "Amount mismatch", JazzCashGateway.LABEL);
            return redirect(orderId, false, JazzCashGateway.NAME);
        }

        if (jazzCashGateway.isSuccess(params)) {
            markPaid(order, jazzCashGateway.transactionId(params),
                    jazzCashGateway.responseMessage(params), JazzCashGateway.LABEL);
            return redirect(orderId, true, JazzCashGateway.NAME);
        }

        markFailed(order, jazzCashGateway.responseMessage(params), JazzCashGateway.LABEL);
        return redirect(orderId, false, JazzCashGateway.NAME);
    }

    @Transactional
    public String handleEasypaisaCallback(Map<String, String> params) {
        if (!easypaisaGateway.isEnabled()) {
            log.warn("Easypaisa callback received but the gateway is not configured");
            return redirect(null, false, EasypaisaGateway.NAME);
        }
        if (!easypaisaGateway.storeMatches(params)) {
            log.warn("Easypaisa callback rejected: store id does not match");
            return redirect(null, false, EasypaisaGateway.NAME);
        }
        if (!easypaisaGateway.verifyCallback(params)) {
            log.warn("Easypaisa callback rejected: invalid merchantHashedReq");
            return redirect(null, false, EasypaisaGateway.NAME);
        }

        Long orderId = parseOrderRef(params.get("orderRefNum"));
        Order order = orderId == null ? null : orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Easypaisa callback for unknown order reference {}", params.get("orderRefNum"));
            return redirect(null, false, EasypaisaGateway.NAME);
        }

        Double paidAmount = parseAmount(params.get("amount"));
        if (!amountMatches(order, paidAmount)) {
            log.warn("Easypaisa amount mismatch for order {}: expected {} got {}",
                    orderId, order.getTotalAmount(), paidAmount);
            markFailed(order, "Amount mismatch", EasypaisaGateway.LABEL);
            return redirect(orderId, false, EasypaisaGateway.NAME);
        }

        if (easypaisaGateway.isSuccess(params)) {
            markPaid(order, easypaisaGateway.transactionId(params),
                    easypaisaGateway.responseMessage(params), EasypaisaGateway.LABEL);
            return redirect(orderId, true, EasypaisaGateway.NAME);
        }

        markFailed(order, easypaisaGateway.responseMessage(params), EasypaisaGateway.LABEL);
        return redirect(orderId, false, EasypaisaGateway.NAME);
    }

    private void markPaid(Order order, String transactionId, String message, String gatewayName) {
        Payment payment = order.getPayment();
        if (payment != null && PAYMENT_PAID.equalsIgnoreCase(payment.getPgStatus())) {
            return; // idempotent: gateway retries must not change a paid order
        }
        if (payment == null) {
            payment = new Payment(gatewayName.toUpperCase(Locale.ROOT), transactionId, PAYMENT_PAID,
                    message, gatewayName);
            payment.setOrder(order);
        }
        payment.setPgStatus(PAYMENT_PAID);
        payment.setPgPaymentId(transactionId);
        payment.setPgResponseMessage(message);
        payment.setPgName(gatewayName);
        paymentRepository.save(payment);

        order.setOrderStatus(ORDER_CONFIRMED);
        orderRepository.save(order);
    }

    private void markFailed(Order order, String message, String gatewayName) {
        Payment payment = order.getPayment();
        if (payment != null && PAYMENT_PAID.equalsIgnoreCase(payment.getPgStatus())) {
            return;
        }
        if (payment == null) {
            payment = new Payment(gatewayName.toUpperCase(Locale.ROOT), null, PAYMENT_FAILED,
                    message, gatewayName);
            payment.setOrder(order);
        }
        payment.setPgStatus(PAYMENT_FAILED);
        payment.setPgResponseMessage(message);
        payment.setPgName(gatewayName);
        paymentRepository.save(payment);

        order.setOrderStatus(ORDER_PAYMENT_FAILED);
        orderRepository.save(order);
    }

    private void requireEnabled(boolean enabled, String label) {
        if (!enabled) {
            throw new APIException(label + " is not configured on this store yet");
        }
    }

    private boolean amountMatches(Order order, Double paidAmount) {
        if (order.getTotalAmount() == null || paidAmount == null) {
            return false;
        }
        return Math.abs(order.getTotalAmount() - paidAmount) <= 0.5;
    }

    private String redirect(Long orderId, boolean success, String gateway) {
        String base = properties.getFrontendReturnUrl();
        String separator = base.contains("?") ? "&" : "?";
        StringBuilder url = new StringBuilder(base)
                .append(separator)
                .append("payment=").append(success ? "success" : "failed")
                .append("&gateway=").append(gateway);
        if (orderId != null) {
            url.append("&orderId=").append(orderId);
        }
        return url.toString();
    }

    private Long parseOrderRef(String orderRef) {
        if (orderRef == null) {
            return null;
        }
        String first = orderRef.split("-")[0].trim();
        return first.matches("\\d+") ? Long.valueOf(first) : null;
    }

    private Long parseLong(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.matches("\\d+") ? Long.valueOf(trimmed) : null;
    }

    /** JazzCash sends the amount in paisa, so convert it back to rupees. */
    private Double parseAmountInPaisa(String value) {
        Double amount = parseAmount(value);
        return amount == null ? null : amount / 100.0;
    }

    private Double parseAmount(String value) {
        if (!PaymentGatewayProperties.hasText(value)) {
            return null;
        }
        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
