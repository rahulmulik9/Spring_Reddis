package com.rahul.taskmanager.service;

import com.rahul.taskmanager.dto.AuthResponse;
import com.rahul.taskmanager.dto.LoginRequest;
import com.rahul.taskmanager.dto.RegisterRequest;
import com.rahul.taskmanager.entity.Role;
import com.rahul.taskmanager.entity.User;
import com.rahul.taskmanager.repository.UserRepository;
import com.rahul.taskmanager.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username already taken: " + request.getUsername());
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .build();

        userRepository.save(user);

        String token = jwtService.generateToken(getSpringSecurityUser(user));

        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole().name())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String token = jwtService.generateToken(getSpringSecurityUser(user));

        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole().name())
                .build();
    }

    // Converts our domain User entity into a Spring Security UserDetails object.
    // Using Spring Security's built-in User class here for simplicity, since it's
    // enough to carry username, password, and role/authorities for auth purposes.
    // A user can instead implement their own UserDetails entity class (e.g. CustomUserDetails)
    // if they need to carry extra fields (like id) through the SecurityContext —
    // that class just needs to implement UserDetails from Spring Security.
    private UserDetails getSpringSecurityUser(User user) {
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .roles(user.getRole().name())
                .build();
    }
}