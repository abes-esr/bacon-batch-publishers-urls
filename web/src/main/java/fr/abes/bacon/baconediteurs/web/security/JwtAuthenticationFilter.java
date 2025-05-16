package fr.abes.bacon.baconediteurs.web.security;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    @Autowired
    private JwtTokenProvider tokenProvider;


    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        log.info("Entering auth filtering");
        try {
            String jwt = tokenProvider.getJwtFromRequest(request);
            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                UserDetails user = new AnonymousUserDetails(tokenProvider.getUsernameFromJwtToken(jwt));
                Authentication authentication = new AnonymousAuthenticationToken(jwt, user, user.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            log.error(ex.getLocalizedMessage());
        }

        filterChain.doFilter(request, response);

    }

}

