package com.ecommerce.project.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Numbers shown on the seller dashboard (only the seller's own data). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SellerAnalyticsResponse {
    private String productCount;
    private String totalRevenue;
    private String totalOrders;
}
