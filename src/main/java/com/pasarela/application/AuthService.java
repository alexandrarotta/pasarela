package com.pasarela.application;

import com.pasarela.domain.model.UserRole;
import com.pasarela.infrastructure.persistence.entity.UserEntity;
import com.pasarela.infrastructure.persistence.repository.UserRepository;
import com.pasarela.infrastructure.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public String register(String email, String password) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("email already exists");
        }
        UserEntity user = new UserEntity();
        user.setEmail(email.toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(UserRole.ADMIN);
        userRepository.save(user);
        return jwtService.mint(user.getEmail(), user.getRole());
    }

    public String login(String email, String password) {
        UserEntity user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("invalid credentials"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("invalid credentials");
        }
        return jwtService.mint(user.getEmail(), user.getRole());
    }
}

