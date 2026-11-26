package com.ecommerce.project.service;

import com.ecommerce.project.payload.AnalyticsResponse;
import com.ecommerce.project.payload.SellerAnalyticsResponse;

public interface AnalyticsService {
    AnalyticsResponse getAnalyticsData();

    SellerAnalyticsResponse getSellerAnalyticsData();
}
