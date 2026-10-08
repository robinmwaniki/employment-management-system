package com.ems.config;

import com.ems.security.filter.JwtAuthenticationFilter;
import com.ems.security.handler.ApiSecurityErrorHandler;
import com.ems.security.jwt.JwtService;
import com.ems.security.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Security configuration with two independent filter chains:
 * <ul>
 *   <li>{@code /api/**}: stateless, JWT bearer tokens, no CSRF (no cookies are used).</li>
 *   <li>everything else: the Thymeleaf web UI, session based, CSRF protected.</li>
 * </ul>
 * Role rules are declared here, in one place, and method security
 * ({@code @PreAuthorize}) can narrow them further on individual endpoints.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";
    private static final String HR_MANAGER = "HR_MANAGER";

    /** Staff pages of the web UI (employees use the /employee/** pages instead). */
    private static final String[] STAFF_PAGES = {
            "/dashboard", "/dashboard/**",
            "/employees", "/employees/**",
            "/departments", "/departments/**",
            "/attendance", "/attendance/**",
            "/leave", "/leave/**",
            "/payroll", "/payroll/**",
            "/performance", "/performance/**",
            "/reports", "/reports/**",
            "/announcements", "/announcements/**",
            "/notifications", "/notifications/**"
    };

    private final CustomUserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final ApiSecurityErrorHandler apiSecurityErrorHandler;

    /**
     * Strength 12 for new passwords. Existing hashes created with the default
     * strength still verify, because the cost is stored inside each hash.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Spring Security 7 requires the UserDetailsService in the constructor;
     * the no-arg constructor and setUserDetailsService were removed.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());

        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration)
            throws Exception {

        return configuration.getAuthenticationManager();
    }

    /**
     * REST API chain: stateless JWT authentication.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http)
            throws Exception {

        http
                .securityMatcher("/api/**")

                // Bearer tokens are sent explicitly by the client, not by the
                // browser, so CSRF does not apply to this chain.
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(apiSecurityErrorHandler)
                        .accessDeniedHandler(apiSecurityErrorHandler))

                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(HttpMethod.POST, "/api/auth/login")
                        .permitAll()

                        // Accounts are created by an administrator only.
                        .requestMatchers("/api/auth/register")
                        .hasRole(ADMIN)

                        // Department endpoints carry their own @PreAuthorize rules
                        // (employees may read departments).
                        .requestMatchers("/api/departments", "/api/departments/**")
                        .authenticated()

                        // Everything else in the API is staff-only.
                        .requestMatchers("/api/**")
                        .hasAnyRole(ADMIN, HR_MANAGER)
                )

                .authenticationProvider(authenticationProvider())

                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService, userDetailsService),
                        UsernamePasswordAuthenticationFilter.class
                )

                .headers(headers -> headers
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy
                                        .STRICT_ORIGIN_WHEN_CROSS_ORIGIN)));

        return http.build();
    }

    /**
     * Web UI chain: session based, CSRF protected.
     * Thymeleaf adds the CSRF token to every {@code th:action} form automatically,
     * and every state-changing action is a POST form.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http)
            throws Exception {

        http
                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(
                                "/", "/login", "/error",
                                "/css/**", "/js/**", "/images/**"
                        ).permitAll()

                        // Health probes for the cloud platform.
                        .requestMatchers(
                                "/actuator/health", "/actuator/health/**",
                                "/actuator/info"
                        ).permitAll()

                        // API documentation (disabled outside development).
                        .requestMatchers(
                                "/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs", "/v3/api-docs/**"
                        ).hasRole(ADMIN)

                        // Employee photos are only shown to signed-in users.
                        .requestMatchers("/uploads/**").authenticated()

                        .requestMatchers(
                                "/users", "/users/**",
                                "/system-logs", "/system-logs/**"
                        ).hasRole(ADMIN)

                        .requestMatchers(STAFF_PAGES)
                        .hasAnyRole(ADMIN, HR_MANAGER)

                        // Self-service pages for any signed-in user.
                        .requestMatchers("/employee/**").authenticated()

                        .anyRequest().authenticated()
                )

                .formLogin(form -> form

                        .loginPage("/login")

                        .successHandler((request, response, authentication) ->
                                response.sendRedirect(
                                        landingPageFor(authentication)))

                        .permitAll()
                )

                // Logout is POST-only (with CSRF token): the default once CSRF is on.
                .logout(logout -> logout

                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )

                .authenticationProvider(authenticationProvider())

                .headers(headers -> headers
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy
                                        .STRICT_ORIGIN_WHEN_CROSS_ORIGIN)));

        return http.build();
    }

    /**
     * Staff land on the admin dashboard, everybody else on the employee one.
     */
    private String landingPageFor(Authentication authentication) {

        boolean staff = authentication.getAuthorities()
                .stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())
                        || "ROLE_HR_MANAGER".equals(a.getAuthority()));

        if (staff) {
            return "/dashboard";
        }

        boolean employee = authentication.getAuthorities()
                .stream()
                .anyMatch(a -> "ROLE_EMPLOYEE".equals(a.getAuthority()));

        return employee ? "/employee/dashboard" : "/login?error";
    }
}