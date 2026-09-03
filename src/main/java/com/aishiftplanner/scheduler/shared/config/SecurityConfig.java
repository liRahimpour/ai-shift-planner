package com.aishiftplanner.scheduler.shared.config;

import com.aishiftplanner.scheduler.auth.infrastructure.JwtAuthenticationFilter;
import com.aishiftplanner.scheduler.shared.api.ApiError;
import com.aishiftplanner.scheduler.shared.api.ErrorCode;
import com.aishiftplanner.scheduler.shared.observability.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Security for the whole API.
 *
 * <p>Two properties matter most here and are worth stating explicitly:
 *
 * <ol>
 *   <li><b>The frontend is never the security boundary.</b> Every rule below runs on the
 *       server, and finer-grained checks live on the service methods
 *       ({@code @EnableMethodSecurity}) plus explicit tenant assertions via
 *       {@code CurrentUserProvider.requireTenant}.
 *   <li><b>Stateless.</b> No HTTP session is created, so any pod can serve any request and
 *       pods can be replaced freely during a rolling update.
 * </ol>
 *
 * <p>Note that only {@code /auth/login} and {@code /auth/refresh} are public —
 * {@code /auth/me} deliberately is not, since it returns a profile.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JsonMapper jsonMapper;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            JsonMapper jsonMapper) {

        this.jwtAuthenticationFilter =
                jwtAuthenticationFilter;

        this.jsonMapper =
                jsonMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        /*
         * Delegating encoder defaulting to bcrypt ({bcrypt}):
         * salted, adaptive and upgradeable later.
         */
        return PasswordEncoderFactories
                .createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http) throws Exception {

        http
                /*
                 * Stateless bearer-token API.
                 * No authenticated session cookie exists for CSRF to exploit.
                 */
                .csrf(csrf ->
                        csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth ->
                        auth.requestMatchers(
                                        "/actuator/health",
                                        "/actuator/health/**",
                                        "/actuator/info",
                                        "/v3/api-docs/**",
                                        "/swagger-ui/**",
                                        "/swagger-ui.html",
                                        "/api/v1/auth/login",
                                        "/api/v1/auth/refresh")
                                .permitAll()
                                .anyRequest()
                                .authenticated())

                .exceptionHandling(ex ->
                        ex.authenticationEntryPoint(
                                        (request,
                                         response,
                                         authException) ->
                                                writeError(
                                                        response,
                                                        HttpServletResponse
                                                                .SC_UNAUTHORIZED,
                                                        ErrorCode
                                                                .UNAUTHENTICATED,
                                                        "Authentication is required."))
                                .accessDeniedHandler(
                                        (request,
                                         response,
                                         deniedException) ->
                                                writeError(
                                                        response,
                                                        HttpServletResponse
                                                                .SC_FORBIDDEN,
                                                        ErrorCode.FORBIDDEN,
                                                        "You are not allowed to perform this action.")))

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Ensures authentication/authorization failures use the same
     * {@link ApiError} shape as every other API error.
     */
    private void writeError(
            HttpServletResponse response,
            int status,
            ErrorCode code,
            String message)
            throws java.io.IOException {

        response.setStatus(status);

        response.setContentType(
                MediaType.APPLICATION_JSON_VALUE);

        jsonMapper.writeValue(
                response.getOutputStream(),
                ApiError.of(
                        code,
                        message,
                        CorrelationIdFilter.currentOrNew()));
    }
}