package com.ecommerce.project.security;

import com.ecommerce.project.model.AppRole;
import com.ecommerce.project.model.Role;
import com.ecommerce.project.model.User;
import com.ecommerce.project.repository.RoleRepository;
import com.ecommerce.project.repository.UserRepository;
import com.ecommerce.project.security.services.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
//import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
//import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
//import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.ecommerce.project.security.jwt.AuthEntryPointJwt;
import com.ecommerce.project.security.jwt.AuthTokenFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

@Configuration
@EnableWebSecurity
//@EnableMethodSecurity
public class WebSecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(WebSecurityConfig.class);

    @Autowired
    UserDetailsServiceImpl userDetailsService;

    @Autowired
    private AuthEntryPointJwt unauthorizedHandler;

    /** Demo logins (user1 / seller1 / admin) are for local development only. */
    @Value("${app.seed.demo-users-enabled:true}")
    private boolean demoUsersEnabled;

    /** When set, creates the admin with this password or rotates an existing one. */
    @Value("${app.seed.admin-password:}")
    private String adminPasswordOverride;

    @Bean
    public AuthTokenFilter authenticationJwtTokenFilter() {
        return new AuthTokenFilter();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();

        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());

        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }



    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth ->
                        auth.requestMatchers("/api/auth/**").permitAll()
                                .requestMatchers("/v3/api-docs/**").permitAll()
                                .requestMatchers("/h2-console/**").permitAll()
                                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                                .requestMatchers("/api/seller/**").hasAnyRole("ADMIN", "SELLER")
                                //.requestMatchers("/api/admin/**").permitAll()
                                .requestMatchers("/api/public/**").permitAll()
                                // Gateways POST the payment result here; each callback is signature-checked.
                                .requestMatchers("/api/payments/*/callback").permitAll()
                                .requestMatchers("/api/payments/*/webhook").permitAll()
                                .requestMatchers("/swagger-ui/**").permitAll()
                                .requestMatchers("/api/test/**").permitAll()
                                // Error dispatch must stay open, otherwise any server-side
                                // error is reported as a misleading 401 from /error.
                                .requestMatchers("/error").permitAll()
                                .requestMatchers("/images/**").permitAll()
                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                .anyRequest().authenticated()
                );

        http.authenticationProvider(authenticationProvider());

        http.addFilterBefore(authenticationJwtTokenFilter(), UsernamePasswordAuthenticationFilter.class);
        http.headers(headers -> headers.frameOptions(
                frameOptions -> frameOptions.sameOrigin()));

        return http.build();
    }

    //From this we can completely by-pass from the security filter at a global level
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web -> web.ignoring().requestMatchers("/v2/api-docs",
                "/configuration/ui",
                "/swagger-resources/**",
                "/configuration/security",
                "/swagger-ui.html",
                "/webjars/**"));
    }

    @Bean
    @org.springframework.core.annotation.Order(1)
    public CommandLineRunner initData(RoleRepository roleRepository, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            // Retrieve or create roles
            Role userRole = roleRepository.findByRoleName(AppRole.ROLE_USER)
                    .orElseGet(() -> {
                        Role newUserRole = new Role(AppRole.ROLE_USER);
                        return roleRepository.save(newUserRole);
                    });

            Role sellerRole = roleRepository.findByRoleName(AppRole.ROLE_SELLER)
                    .orElseGet(() -> {
                        Role newSellerRole = new Role(AppRole.ROLE_SELLER);
                        return roleRepository.save(newSellerRole);
                    });

            Role adminRole = roleRepository.findByRoleName(AppRole.ROLE_ADMIN)
                    .orElseGet(() -> {
                        Role newAdminRole = new Role(AppRole.ROLE_ADMIN);
                        return roleRepository.save(newAdminRole);
                    });

            Set<Role> userRoles = Set.of(userRole);
            Set<Role> sellerRoles = Set.of(sellerRole);
            Set<Role> adminRoles = Set.of(userRole, sellerRole, adminRole);


            // Demo logins are for local development only. A public deployment sets
            // APP_SEED_DEMO_USERS=false so these well-known accounts never exist.
            if (demoUsersEnabled) {
                if (!userRepository.existsByUserName("user1")) {
                    User user1 = new User("user1", "user1@example.com", passwordEncoder.encode("password1"));
                    userRepository.save(user1);
                }

                if (!userRepository.existsByUserName("seller1")) {
                    User seller1 = new User("seller1", "seller1@example.com", passwordEncoder.encode("password2"));
                    userRepository.save(seller1);
                }
            }

            // The admin account is needed for the dashboard. On a fresh database it
            // is created only when a password is provided (or demo mode is on), and
            // APP_SEED_ADMIN_PASSWORD also rotates an existing admin password once.
            if (!userRepository.existsByUserName("admin")) {
                String password = adminPasswordOverride.isBlank()
                        ? (demoUsersEnabled ? "adminPass" : null)
                        : adminPasswordOverride;
                if (password != null) {
                    userRepository.save(new User("admin", "admin@example.com", passwordEncoder.encode(password)));
                    log.info("Created admin account using the configured password.");
                } else {
                    log.warn("No admin account exists. Set APP_SEED_ADMIN_PASSWORD once to create one.");
                }
            } else if (!adminPasswordOverride.isBlank()) {
                userRepository.findByUserName("admin").ifPresent(admin -> {
                    admin.setPassword(passwordEncoder.encode(adminPasswordOverride));
                    userRepository.save(admin);
                });
                log.warn("Admin password rotated from APP_SEED_ADMIN_PASSWORD. Remove that environment variable now.");
            } else if (!demoUsersEnabled) {
                log.warn("Admin still uses its existing password. Set APP_SEED_ADMIN_PASSWORD once to rotate it.");
            }

            // Update roles for existing users. The demo accounts are only kept in
            // sync in demo mode, so a production deployment can strip their roles.
            if (demoUsersEnabled) {
                userRepository.findByUserName("user1").ifPresent(user -> {
                    user.setRoles(userRoles);
                    userRepository.save(user);
                });

                userRepository.findByUserName("seller1").ifPresent(seller -> {
                    seller.setRoles(sellerRoles);
                    userRepository.save(seller);
                });
            }

            userRepository.findByUserName("admin").ifPresent(admin -> {
                admin.setRoles(adminRoles);
                userRepository.save(admin);
            });
        };
    }

}