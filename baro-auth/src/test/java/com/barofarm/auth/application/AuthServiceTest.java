package com.barofarm.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barofarm.auth.application.port.HotlistEventPublisher;
import com.barofarm.auth.domain.user.User;
import com.barofarm.auth.infrastructure.jpa.AuthCredentialJpaRepository;
import com.barofarm.auth.infrastructure.jpa.RefreshTokenJpaRepository;
import com.barofarm.auth.infrastructure.jpa.UserJpaRepository;
import com.barofarm.auth.infrastructure.security.JwtTokenProvider;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    @Test
    void updateUserStatePublishesHotlistEventAndRevokesTokens() {
        UserJpaRepository userRepository = mock(UserJpaRepository.class);
        AuthCredentialJpaRepository credentialRepository = mock(AuthCredentialJpaRepository.class);
        RefreshTokenJpaRepository refreshTokenRepository = mock(RefreshTokenJpaRepository.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        HotlistEventPublisher hotlistEventPublisher = mock(HotlistEventPublisher.class);
        Clock clock = Clock.systemUTC();

        AuthService authService = new AuthService(
            userRepository,
            credentialRepository,
            refreshTokenRepository,
            emailVerificationService,
            passwordEncoder,
            jwtTokenProvider,
            hotlistEventPublisher,
            clock
        );

        UUID userId = UUID.randomUUID();
        User user = User.create("user@example.com", "User", "010-1234-5678", false);
        setUserId(user, userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.updateUserState(userId, User.UserState.BLOCKED, "manual");

        verify(userRepository).save(user);
        verify(refreshTokenRepository).deleteAllByUserId(userId);

        ArgumentCaptor<com.barofarm.auth.application.event.HotlistEventMessage> captor =
            ArgumentCaptor.forClass(com.barofarm.auth.application.event.HotlistEventMessage.class);
        verify(hotlistEventPublisher).publish(captor.capture());

        var event = captor.getValue();
        assertThat(event.getSubjectType()).isEqualTo("user");
        assertThat(event.getSubjectId()).isEqualTo(userId.toString());
        assertThat(event.getStatus()).isEqualTo("BLOCKED");
        assertThat(event.getActive()).isTrue();
    }

    @Test
    void updateUserStateActiveDoesNotRevokeTokens() {
        UserJpaRepository userRepository = mock(UserJpaRepository.class);
        AuthCredentialJpaRepository credentialRepository = mock(AuthCredentialJpaRepository.class);
        RefreshTokenJpaRepository refreshTokenRepository = mock(RefreshTokenJpaRepository.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        HotlistEventPublisher hotlistEventPublisher = mock(HotlistEventPublisher.class);
        Clock clock = Clock.systemUTC();

        AuthService authService = new AuthService(
            userRepository,
            credentialRepository,
            refreshTokenRepository,
            emailVerificationService,
            passwordEncoder,
            jwtTokenProvider,
            hotlistEventPublisher,
            clock
        );

        UUID userId = UUID.randomUUID();
        User user = User.create("user@example.com", "User", "010-1234-5678", false);
        setUserId(user, userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.updateUserState(userId, User.UserState.ACTIVE, "restore");

        verify(userRepository).save(user);
        verify(refreshTokenRepository, never()).deleteAllByUserId(userId);
        verify(hotlistEventPublisher).publish(any());
    }

    private void setUserId(User user, UUID userId) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, userId);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to set user id for test", e);
        }
    }
}
