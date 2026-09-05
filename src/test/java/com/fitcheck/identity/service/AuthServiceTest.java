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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties(
                "unused-by-authservice-directly", Duration.ofMinutes(15), Duration.ofDays(7));
        authService = new AuthService(userRepository, userProfileRepository, refreshTokenRepository,
                passwordEncoder, authenticationManager, jwtService, jwtProperties);
    }

    private User buildUser(String email, Role role) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash("existing-hash")
                .role(role)
                .build();
    }

    private RefreshToken buildRefreshToken(User owner, String tokenHash, LocalDateTime expiresAt, LocalDateTime revokedAt) {
        return RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(owner)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .revokedAt(revokedAt)
                .build();
    }

    // ---- register ----

    @Test
    void register_newEmail_createsUserAndProfileInOneTransaction() {
        RegisterRequest request = new RegisterRequest("new@example.com", "password123");
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtService.generateRefreshToken()).thenReturn("raw-refresh-token");
        when(jwtService.hashToken("raw-refresh-token")).thenReturn("refresh-token-hash");

        authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("new@example.com");
        assertThat(savedUser.getRole()).isEqualTo(Role.USER);

        ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getUser()).isEqualTo(savedUser);
    }

    @Test
    void register_duplicateEmail_throwsConflictException() {
        RegisterRequest request = new RegisterRequest("existing@example.com", "password123");
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class);

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void register_concurrentRegistrationLosesUniqueConstraintRace_throwsConflictExceptionNotDataIntegrityViolationException() {
        RegisterRequest request = new RegisterRequest("racing@example.com", "password123");
        when(userRepository.existsByEmail("racing@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key value violates unique constraint \"users_email_key\""));

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("An account with this email already exists");

        verify(userProfileRepository, never()).save(any(UserProfile.class));
    }

    @Test
    void register_lowercasesEmailBeforeStoring() {
        RegisterRequest request = new RegisterRequest("MixedCase@Example.COM", "password123");
        when(userRepository.existsByEmail("mixedcase@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtService.generateRefreshToken()).thenReturn("raw-refresh-token");
        when(jwtService.hashToken("raw-refresh-token")).thenReturn("refresh-token-hash");

        authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("mixedcase@example.com");
    }

    @Test
    void register_hashesPasswordBeforeStoring_neverPersistsRawPassword() {
        RegisterRequest request = new RegisterRequest("new@example.com", "plainTextPassword123");
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plainTextPassword123")).thenReturn("{bcrypt}$2a$10$hashedvalue");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtService.generateRefreshToken()).thenReturn("raw-refresh-token");
        when(jwtService.hashToken("raw-refresh-token")).thenReturn("refresh-token-hash");

        authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("{bcrypt}$2a$10$hashedvalue");
        assertThat(userCaptor.getValue().getPasswordHash()).isNotEqualTo("plainTextPassword123");
    }

    // ---- login ----

    @Test
    void login_correctCredentials_returnsAccessAndRefreshTokens() {
        User existingUser = buildUser("user@example.com", Role.USER);
        LoginRequest request = new LoginRequest("user@example.com", "correctPassword");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existingUser));
        when(jwtService.generateAccessToken(existingUser)).thenReturn("access-token-123");
        when(jwtService.generateRefreshToken()).thenReturn("raw-refresh-456");
        when(jwtService.hashToken("raw-refresh-456")).thenReturn("hashed-456");

        AuthResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token-123");
        assertThat(response.refreshToken()).isEqualTo("raw-refresh-456");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(Duration.ofMinutes(15).toSeconds());
    }

    @Test
    void login_wrongPassword_throwsUnauthorizedExceptionWithGenericMessage() {
        LoginRequest request = new LoginRequest("user@example.com", "wrongPassword");
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_nonexistentEmail_throwsExactSameMessageAsWrongPassword() {
        LoginRequest wrongPasswordRequest = new LoginRequest("existing@example.com", "wrongPassword");
        LoginRequest nonexistentEmailRequest = new LoginRequest("nobody@example.com", "anyPassword");

        when(authenticationManager.authenticate(argThat(auth -> auth != null && "existing@example.com".equals(auth.getPrincipal()))))
                .thenThrow(new BadCredentialsException("Bad credentials"));
        when(authenticationManager.authenticate(argThat(auth -> auth != null && "nobody@example.com".equals(auth.getPrincipal()))))
                .thenThrow(new BadCredentialsException("User nobody@example.com not found"));

        String wrongPasswordMessage = catchThrowableOfType(UnauthorizedException.class,
                () -> authService.login(wrongPasswordRequest)).getMessage();
        String nonexistentEmailMessage = catchThrowableOfType(UnauthorizedException.class,
                () -> authService.login(nonexistentEmailRequest)).getMessage();

        assertThat(wrongPasswordMessage).isEqualTo(nonexistentEmailMessage);
    }

    // ---- refresh ----

    @Test
    void refresh_validUnexpiredToken_rotatesAndReturnsNewTokenPair() {
        User owner = buildUser("user@example.com", Role.USER);
        RefreshToken storedToken = buildRefreshToken(owner, "old-token-hash", LocalDateTime.now().plusDays(1), null);
        RefreshRequest request = new RefreshRequest("raw-old-token");

        when(jwtService.hashToken("raw-old-token")).thenReturn("old-token-hash");
        when(refreshTokenRepository.findByTokenHash("old-token-hash")).thenReturn(Optional.of(storedToken));
        when(jwtService.generateAccessToken(owner)).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken()).thenReturn("raw-new-token");
        when(jwtService.hashToken("raw-new-token")).thenReturn("new-token-hash");

        AuthResponse response = authService.refresh(request);

        assertThat(response.refreshToken()).isEqualTo("raw-new-token");
        assertThat(response.refreshToken()).isNotEqualTo("raw-old-token");
        assertThat(storedToken.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void refresh_revokedTokenReused_revokesEntireUserTokenSet() {
        User owner = buildUser("victim@example.com", Role.USER);
        RefreshToken reusedToken = buildRefreshToken(owner, "stolen-token-hash",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().minusHours(1));
        RefreshToken otherActiveToken1 = buildRefreshToken(owner, "other-hash-1", LocalDateTime.now().plusDays(2), null);
        RefreshToken otherActiveToken2 = buildRefreshToken(owner, "other-hash-2", LocalDateTime.now().plusDays(3), null);
        RefreshRequest request = new RefreshRequest("raw-stolen-token");

        when(jwtService.hashToken("raw-stolen-token")).thenReturn("stolen-token-hash");
        when(refreshTokenRepository.findByTokenHash("stolen-token-hash")).thenReturn(Optional.of(reusedToken));
        when(refreshTokenRepository.findByUser_IdAndRevokedAtIsNull(owner.getId()))
                .thenReturn(List.of(otherActiveToken1, otherActiveToken2));

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid refresh token");

        assertThat(otherActiveToken1.getRevokedAt()).isNotNull();
        assertThat(otherActiveToken2.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).saveAll(List.of(otherActiveToken1, otherActiveToken2));
    }

    @Test
    void refresh_expiredToken_throwsUnauthorizedException() {
        User owner = buildUser("user@example.com", Role.USER);
        RefreshToken expiredToken = buildRefreshToken(owner, "expired-hash", LocalDateTime.now().minusMinutes(1), null);
        RefreshRequest request = new RefreshRequest("raw-expired-token");

        when(jwtService.hashToken("raw-expired-token")).thenReturn("expired-hash");
        when(refreshTokenRepository.findByTokenHash("expired-hash")).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid refresh token");

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        verify(jwtService, never()).generateAccessToken(any(User.class));
    }

    @Test
    void refresh_unknownTokenHash_throwsUnauthorizedException() {
        RefreshRequest request = new RefreshRequest("raw-unknown-token");
        when(jwtService.hashToken("raw-unknown-token")).thenReturn("unknown-hash");
        when(refreshTokenRepository.findByTokenHash("unknown-hash")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid refresh token");
    }

    // ---- logout ----

    @Test
    void logout_marksMatchingRefreshTokenRevoked() {
        User owner = buildUser("user@example.com", Role.USER);
        RefreshToken activeToken = buildRefreshToken(owner, "active-hash", LocalDateTime.now().plusDays(1), null);
        LogoutRequest request = new LogoutRequest("raw-active-token");

        when(jwtService.hashToken("raw-active-token")).thenReturn("active-hash");
        when(refreshTokenRepository.findByTokenHash("active-hash")).thenReturn(Optional.of(activeToken));

        authService.logout(request);

        assertThat(activeToken.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).save(activeToken);
    }

    @Test
    void logout_unknownOrAlreadyRevokedToken_noOpDoesNotThrow() {
        LogoutRequest unknownTokenRequest = new LogoutRequest("raw-unknown-token");
        when(jwtService.hashToken("raw-unknown-token")).thenReturn("unknown-hash");
        when(refreshTokenRepository.findByTokenHash("unknown-hash")).thenReturn(Optional.empty());

        assertThatCode(() -> authService.logout(unknownTokenRequest)).doesNotThrowAnyException();

        User owner = buildUser("user@example.com", Role.USER);
        RefreshToken alreadyRevoked = buildRefreshToken(owner, "already-revoked-hash",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().minusHours(1));
        LogoutRequest alreadyRevokedRequest = new LogoutRequest("raw-already-revoked-token");
        when(jwtService.hashToken("raw-already-revoked-token")).thenReturn("already-revoked-hash");
        when(refreshTokenRepository.findByTokenHash("already-revoked-hash")).thenReturn(Optional.of(alreadyRevoked));

        assertThatCode(() -> authService.logout(alreadyRevokedRequest)).doesNotThrowAnyException();

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }
}