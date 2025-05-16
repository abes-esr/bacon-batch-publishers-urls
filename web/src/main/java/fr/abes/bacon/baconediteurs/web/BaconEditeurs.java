package fr.abes.bacon.baconediteurs.web;

import fr.abes.bacon.baconediteurs.web.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
public class BaconEditeurs implements CommandLineRunner {
    @Value("${jwt.anonymousUser")
    private String user;
    @Autowired
    private JwtTokenProvider tokenProvider;

    public static void main(String[] args) {
        SpringApplication.run(BaconEditeurs.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        List<GrantedAuthority> roles = new ArrayList<>();
        roles.add(new SimpleGrantedAuthority("ANONYMOUS"));
        String token = tokenProvider.generateToken();
        System.out.println(token);
        Authentication auth = new AnonymousAuthenticationToken(token, user, roles);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
