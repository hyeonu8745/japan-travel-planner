package com.example.japantravelplanner.global.config;

import com.example.japantravelplanner.global.security.JwtAuthenticationFilter;
import com.example.japantravelplanner.global.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 설정.
 * - 세션 없이 JWT 로만 인증 (JwtAuthenticationFilter), 로그인 안 하면 401 JSON
 * - 로그인 없이 쓰는 주소: 가입·로그인·토큰 갱신·로그아웃, 관광지·식당 검색. 나머지(장소 상세 포함)는 로그인 필요
 * - CORS: 설정한 프론트 주소만, 갱신 토큰 쿠키를 주고받도록 credentials 허용
 * - 비밀번호는 BCrypt
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    /** 비밀번호 해시 (BCrypt) */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 사용자 이름·비밀번호 로그인은 우리 /api/auth/login 이 하고, 나머지는 JWT 로만 인증한다.
     * 이 빈이 없으면 Spring 이 임시 계정(user + 무작위 비밀번호)을 만들고 기동 로그에 비밀번호를 찍는다
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(username);
        };
    }

    /** 주소별 로그인 필요 여부, 세션 없음(STATELESS), JWT 필터, 401 을 JSON 으로 */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 로그인 없이 접근 가능한 경로
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login",
                                "/api/auth/refresh", "/api/auth/logout").permitAll()
                        // 관광지·식당 상세와 사진은 부를 때마다 Google 요금이 들어서 로그인한 사용자만 (아래 공개 규칙보다 먼저 와야 한다)
                        .requestMatchers(HttpMethod.GET, "/api/attractions/*/details", "/api/attractions/*/photo",
                                "/api/restaurants/*/details", "/api/restaurants/*/photo").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/restaurants/**", "/api/attractions/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // 그 외 모든 요청은 인증 필요
                        .anyRequest().authenticated()
                )
                // 인증이 필요한데 인증 정보가 없거나 잘못된 경우 → 401 JSON
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"message\":\"인증이 필요합니다.\"}");
                        })
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** CORS: 설정한 프론트 주소만, 쓰는 메서드·헤더만, 쿠키 허용. /api/** 에만 적용 */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // 갱신 토큰 쿠키를 주고받으려면 (프론트는 credentials: include / withCredentials)
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);   // 사전 확인 결과를 브라우저가 1시간 기억

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}