package com.inteliroadmap.backend.config;

//import com.inteliroadmap.backend.security.JwtAuthenticationFilter;
import com.inteliroadmap.backend.security.OAuth2AuthenticationFailureHandler;
import com.inteliroadmap.backend.security.OAuth2AuthenticationSuccessHandler;
import com.inteliroadmap.backend.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final com.inteliroadmap.backend.services.OAuth2UserService oAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults()) // ← Tự động dùng CorsConfig bean
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth

                        // ============================================================
                        // PUBLIC ENDPOINTS - No authentication required
                        // ============================================================
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/auth/**",
                                "/oauth2/**",
                                "/login/oauth2/**"
                        ).permitAll()

                        // ============================================================
                        // SWAGGER - No authentication required
                        // ============================================================
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/webjars/**"
                        ).permitAll()

                        // ============================================================
                        // STUDENT ENDPOINTS - Role: STUDENT
                        // Sprint 2: Profile & Assessment
                        // ============================================================
                        .requestMatchers(HttpMethod.GET, "/profile/**").hasRole("STUDENT")

                        // ============================================================
                        // ROADMAP ENDPOINTS - Role: STUDENT
                        // Sprint 3: Roadmap
                        // ============================================================
                        .requestMatchers(HttpMethod.GET, "/roadmap/**").hasRole("STUDENT")
                        .requestMatchers(HttpMethod.PUT, "/roadmap/**").hasRole("STUDENT")

                        // ============================================================
                        // AI CHAT ENDPOINTS - Role: STUDENT
                        // Sprint 4: AI Virtual Mentor
                        // ============================================================
                        // .requestMatchers(HttpMethod.POST, "/chat/**").hasRole("STUDENT")
                        // .requestMatchers(HttpMethod.GET, "/chat/**").hasRole("STUDENT")

                        // ============================================================
                        // PORTFOLIO ENDPOINTS - Role: STUDENT
                        // Sprint 4: E-Portfolio
                        // ============================================================
                        // .requestMatchers(HttpMethod.POST, "/portfolio/**").hasRole("STUDENT")
                        // .requestMatchers(HttpMethod.GET, "/portfolio/**").hasRole("STUDENT")

                        // ============================================================
                        // COUNSELOR ENDPOINTS - Role: COUNSELOR
                        // Sprint 5: Counselor Dashboard
                        // ============================================================
                        .requestMatchers(HttpMethod.GET, "/counselor/**").hasRole("COUNSELOR")
                        .requestMatchers(HttpMethod.POST, "/feedback/**").hasRole("COUNSELOR")

                        // ============================================================
                        // MARKET PULSE ENDPOINTS - Role: STUDENT, COUNSELOR
                        // Sprint 5: Market Pulse
                        // ============================================================
                        // .requestMatchers(HttpMethod.GET, "/market/**")
                        //     .hasAnyRole("STUDENT", "COUNSELOR")

                        // ============================================================
                        // All other endpoints require authentication
                        // ============================================================
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(oAuth2UserService)
                        )
                        .successHandler(oAuth2AuthenticationSuccessHandler)
                        .failureHandler(oAuth2AuthenticationFailureHandler)
                )
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    /**
     * Password Encoder - BCrypt
     * Used to encode and verify passwords
     * @return BCryptPasswordEncoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}