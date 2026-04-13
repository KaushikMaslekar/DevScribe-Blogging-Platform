package com.devscribe.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.devscribe.dto.auth.AuthLoginRequest;
import com.devscribe.dto.auth.AuthRegisterRequest;
import com.devscribe.dto.auth.AuthTokenPayload;
import com.devscribe.dto.auth.UserMeResponse;
import com.devscribe.entity.User;
import com.devscribe.entity.UserRole;
import com.devscribe.repository.UserRepository;
import com.devscribe.security.JwtTokenProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditLogService auditLogService;

    @Transactional
    public AuthTokenPayload register(AuthRegisterRequest request) {
        String normalizedEmail = request.email().toLowerCase().trim();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(BAD_REQUEST, "Email already in use");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new ResponseStatusException(BAD_REQUEST, "Username already in use");
        }

        User user = User.builder()
                .email(normalizedEmail)
                .username(request.username().trim())
                .displayName(request.username().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.WRITER)
                .build();

        userRepository.save(user);
        auditLogService.log(
                user,
                "AUTH_REGISTER",
                "USER",
                String.valueOf(user.getId()),
                "email=" + user.getEmail()
        );
        String token = jwtTokenProvider.generateToken(user.getEmail());
        return new AuthTokenPayload(token, jwtTokenProvider.getExpirationMs(), toMeResponse(user));
    }

    @Transactional(readOnly = true)
    public AuthTokenPayload login(AuthLoginRequest request) {
        String normalizedEmail = request.email().toLowerCase().trim();

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
            );
        } catch (BadCredentialsException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid credentials");
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));

        auditLogService.log(
                user,
                "AUTH_LOGIN",
                "USER",
                String.valueOf(user.getId()),
                "email=" + user.getEmail()
        );
        String token = jwtTokenProvider.generateToken(user.getEmail());
        return new AuthTokenPayload(token, jwtTokenProvider.getExpirationMs(), toMeResponse(user));
    }

    @Transactional(readOnly = true)
    public UserMeResponse me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));
        return toMeResponse(user);
    }

    private UserMeResponse toMeResponse(User user) {
        return new UserMeResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getDisplayName(),
                user.getBio(),
                user.getAvatarUrl(),
                user.getRole()
        );
    }
}
