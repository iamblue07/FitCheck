package com.fitcheck.identity.service;

import com.fitcheck.common.exception.ConflictException;
import com.fitcheck.common.exception.UnauthorizedException;
import com.fitcheck.common.security.JwtProperties;
import com.fitcheck.identity.dto.AuthResponse;
import com.fitcheck.identity.dto.LoginRequest;
import com.fitcheck.identity.dto.LogoutRequest;
import com.fitcheck.identity.dto.RefreshRequest;
import com.fitcheck.identity.dto.RegisterRequest;
import com.fitcheck.identity.entity.RefreshToken;
import com.fitcheck.identity.entity.Role;
import com.fitcheck.identity.entity.User;
import com.fitcheck.identity.entity.UserProfile;
import com.fitcheck.identity.repository.RefreshTokenRepository;
import com.fitcheck.identity.repository.UserProfileRepository;
import com.fitcheck.identity.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .build();
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("An account with this email already exists");
        }

        UserProfile profile = UserProfile.builder()
                .user(user)
                .build();
        userProfileRepository.save(profile);

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.email().toLowerCase();

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password())
            );
        } catch (BadCredentialsException e) {
            throw new UnauthorizedException("Invalid email or password");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user vanished: " + email));

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String tokenHash = jwtService.hashToken(request.refreshToken());

        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (storedToken.getRevokedAt() != null) {
            revokeAllTokensForUser(storedToken.getUser().getId());
            throw new UnauthorizedException("Invalid refresh token");
        }

        if (storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        storedToken.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(storedToken);

        return issueTokens(storedToken.getUser());
    }

    @Transactional
    public void logout(LogoutRequest request) {
        String tokenHash = jwtService.hashToken(request.refreshToken());

        refreshTokenRepository.findByTokenHash(tokenHash)
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> {
                    token.setRevokedAt(LocalDateTime.now());
                    refreshTokenRepository.save(token);
                });
    }

    private void revokeAllTokensForUser(UUID userId) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findByUser_IdAndRevokedAtIsNull(userId);
        LocalDateTime now = LocalDateTime.now();
        activeTokens.forEach(token -> token.setRevokedAt(now));
        refreshTokenRepository.saveAll(activeTokens);
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = jwtService.generateRefreshToken();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(jwtService.hashToken(rawRefreshToken))
                .expiresAt(LocalDateTime.now().plus(jwtProperties.refreshExpiration()))
                .build();
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.of(accessToken, rawRefreshToken, jwtProperties.accessExpiration().toSeconds());
    }
}