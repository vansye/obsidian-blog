package luv.vansye.config;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean
    public UrlBasedCorsConfigurationSource corsSource(@Value("${blog.cors-origins:}") String configured) {
        List<String> origins = Arrays.stream(configured.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
        for (String origin : origins) {
            URI uri = URI.create(origin);
            if (!List.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                    || origin.contains("*") || uri.getUserInfo() != null
                    || !(uri.getPath().isEmpty()) || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("CORS_ORIGINS must contain exact http(s) origins without paths");
            }
        }
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "HEAD", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Accept", "If-None-Match", "Content-Type"));
        cors.setExposedHeaders(List.of("ETag", "Cache-Control"));
        cors.setAllowCredentials(false);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", cors);
        source.registerCorsConfiguration("/assets/**", cors);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, UrlBasedCorsConfigurationSource corsSource) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable()).formLogin(form -> form.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((req, res, error) -> forbidden(res))
                        .accessDeniedHandler((req, res, error) -> forbidden(res)))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/**", "/assets/**").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/v1/**", "/assets/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/", "/p/**", "/archive", "/notes", "/lab", "/about",
                                "/css/**", "/favicon.ico", "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/sync/webhook").permitAll()
                        .anyRequest().denyAll()).build();
    }

    private static void forbidden(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        response.setStatus(403);
        response.setContentType("application/problem+json");
        response.setHeader("Cache-Control", "no-cache");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,\"code\":\"FORBIDDEN\"}");
    }
}