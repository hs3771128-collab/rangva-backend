package com.ecommerce.project.service;

import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.AnalyticsResponse;
import com.ecommerce.project.payload.SellerAnalyticsResponse;
import com.ecommerce.project.repository.OrderRepository;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.util.AuthUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AuthUtil authUtil;

    @Override
    public AnalyticsResponse getAnalyticsData() {
        AnalyticsResponse response = new AnalyticsResponse();

        long productCount = productRepository.count();
        long totalOrders = orderRepository.count();
        Double totalRevenue = orderRepository.getTotalRevenue();

        response.setProductCount(String.valueOf(productCount));
        response.setTotalOrders(String.valueOf(totalOrders));
        response.setTotalRevenue(String.valueOf(totalRevenue != null ? totalRevenue : 0));

        return response;
    }

    /**
     * Seller dashboard: counts and revenue limited to the logged-in seller's own
     * products, so one seller never sees another seller's numbers.
     */
    @Override
    public SellerAnalyticsResponse getSellerAnalyticsData() {
        User seller = authUtil.loggedInUser();

        long productCount = productRepository.findByUser(seller, PageRequest.of(0, 1))
                .getTotalElements();
        long totalOrders = orderRepository.countBySeller(seller);
        Double revenue = orderRepository.getRevenueBySeller(seller);

        return new SellerAnalyticsResponse(
                String.valueOf(productCount),
                String.valueOf(revenue != null ? revenue : 0),
                String.valueOf(totalOrders));
    }
}
