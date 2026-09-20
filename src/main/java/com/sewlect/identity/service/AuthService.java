package com.sewlect.identity.service;

import com.sewlect.common.exception.ConflictException;
import com.sewlect.common.exception.UnauthorizedException;
import com.sewlect.common.logging.support.SecurityEventLogger;
import com.sewlect.common.security.properties.JwtProperties;
import com.sewlect.identity.dto.AuthResponse;
import com.sewlect.identity.dto.LoginRequest;
import com.sewlect.identity.dto.LogoutRequest;
import com.sewlect.identity.dto.RefreshRequest;
import com.sewlect.identity.dto.RegisterRequest;
import com.sewlect.identity.entity.RefreshToken;
import com.sewlect.identity.enums.Role;
import com.sewlect.identity.entity.User;
import com.sewlect.identity.entity.UserProfile;
import com.sewlect.identity.repository.RefreshTokenRepository;
import com.sewlect.identity.repository.UserProfileRepository;
import com.sewlect.identity.repository.UserRepository;
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
    private final SecurityEventLogger securityEventLogger;

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

        AuthResponse response = issueTokens(user);
        securityEventLogger.registrationSucceeded(user.getId());
        return response;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.email().toLowerCase();

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password())
            );
        } catch (BadCredentialsException e) {
            securityEventLogger.loginFailed();
            throw new UnauthorizedException("Invalid email or password");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user vanished: " + email));

        AuthResponse response = issueTokens(user);
        securityEventLogger.loginSucceeded(user.getId());
        return response;
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

        AuthResponse response = issueTokens(storedToken.getUser());
        securityEventLogger.refreshTokenRotated(storedToken.getUser().getId());
        return response;
    }

    @Transactional
    public void logout(LogoutRequest request) {
        String tokenHash = jwtService.hashToken(request.refreshToken());

        refreshTokenRepository.findByTokenHash(tokenHash)
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> {
                    token.setRevokedAt(LocalDateTime.now());
                    refreshTokenRepository.save(token);
                    securityEventLogger.logoutSucceeded(token.getUser().getId());
                });
    }

    private void revokeAllTokensForUser(UUID userId) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findByUser_IdAndRevokedAtIsNull(userId);
        LocalDateTime now = LocalDateTime.now();
        activeTokens.forEach(token -> token.setRevokedAt(now));
        refreshTokenRepository.saveAll(activeTokens);
        securityEventLogger.refreshTokenReuseDetected(userId, activeTokens.size());
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