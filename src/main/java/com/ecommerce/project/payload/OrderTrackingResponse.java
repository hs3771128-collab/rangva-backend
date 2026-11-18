package com.ecommerce.project.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * What the public tracking page is allowed to see about an order. It never
 * contains the customer's full address or payment references.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderTrackingResponse {

    private Long orderId;
    private LocalDate orderDate;
    private String orderStatus;
    private String statusLabel;
    private String statusNote;
    private String paymentMethod;
    private String paymentStatus;
    private Double totalAmount;
    private int itemCount;
    private String city;
    private String recipient;
    private LocalDate expectedDelivery;
    private boolean cancelled;
    private String courier;
    private String trackingNumber;
    private List<TrackingItem> items;
    private List<TrackingStep> steps;

    /** One line of the order, without internal ids or stock data. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackingItem {
        private String productName;
        private String variantLabel;
        private Integer quantity;
        private Double lineTotal;
    }

    /** A stage in the delivery timeline. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackingStep {
        private String label;
        private String description;
        private boolean done;
        private boolean current;
    }
}
