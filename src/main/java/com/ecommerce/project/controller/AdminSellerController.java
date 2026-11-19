package com.ecommerce.project.controller;

import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.security.request.SignupRequest;
import com.ecommerce.project.security.response.MessageResponse;
import com.ecommerce.project.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Seller management for the admin dashboard.
 *
 * These routes live under /api/admin/**, which WebSecurityConfig restricts to
 * ROLE_ADMIN. Public signup can no longer create sellers or admins, and the
 * former public /api/auth/sellers listing (which leaked seller emails) is gone.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminSellerController {

    @Autowired
    private AuthService authService;

    @GetMapping("/sellers")
    public ResponseEntity<?> getAllSellers(
            @RequestParam(name = "pageNumber", defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber) {

        Sort sortByAndOrder = Sort.by(AppConstants.SORT_USERS_BY).descending();
        Pageable pageDetails = PageRequest.of(pageNumber,
                Integer.parseInt(AppConstants.PAGE_SIZE), sortByAndOrder);

        return ResponseEntity.ok(authService.getAllSellers(pageDetails));
    }

    @PostMapping("/sellers")
    public ResponseEntity<MessageResponse> createSeller(@Valid @RequestBody SignupRequest signUpRequest) {
        return authService.registerSeller(signUpRequest);
    }
}
