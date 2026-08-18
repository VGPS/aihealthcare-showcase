package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.exception.InvalidResetTokenException;
import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.PasswordResetToken;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.PasswordHashingPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.PasswordResetPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TransactionalEmailPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PasswordResetService}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-17
 * @updated 2026-08-17
 */
class PasswordResetServiceTest {

    private AppUserPort appUserPort;
    private PasswordResetPort passwordResetPort;
    private PasswordHashingPort passwordHashingPort;
    private TransactionalEmailPort transactionalEmailPort;
    private PasswordResetService service;

    private static final AppUser EXISTING_USER = new AppUser(
            "user@example.com", "$2b$10$oldHash", "Existing User", "USER", true, SubscriptionTier.FREE, null);

    @BeforeEach
    void setUp() {
        appUserPort = mock(AppUserPort.class);
        passwordResetPort = mock(PasswordResetPort.class);
        passwordHashingPort = mock(PasswordHashingPort.class);
        transactionalEmailPort = mock(TransactionalEmailPort.class);
        service = new PasswordResetService(appUserPort, passwordResetPort, passwordHashingPort, transactionalEmailPort);
    }

    // -------------------------------------------------------------------------
    // requestReset()
    // -------------------------------------------------------------------------

    @Test
    void requestReset_knownEmail_savesTokenAndSendsEmail() {
        when(appUserPort.findByEmail("user@example.com")).thenReturn(Optional.of(EXISTING_USER));

        service.requestReset("user@example.com");

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetPort).save(captor.capture());
        PasswordResetToken saved = captor.getValue();
        assertThat(saved.email()).isEqualTo("user@example.com");
        assertThat(saved.used()).isFalse();
        assertThat(saved.expiresAt()).isAfter(Instant.now());

        verify(transactionalEmailPort).sendPasswordReset("user@example.com", "Existing User", saved.token());
    }

    @Test
    void requestReset_unknownEmail_doesNothing() {
        when(appUserPort.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        service.requestReset("nobody@example.com");

        verify(passwordResetPort, never()).save(org.mockito.ArgumentMatchers.any());
        verify(transactionalEmailPort, never()).sendPasswordReset(anyString(), anyString(), anyString());
    }

    // -------------------------------------------------------------------------
    // validateToken()
    // -------------------------------------------------------------------------

    @Test
    void validateToken_freshToken_returnsTrue() {
        PasswordResetToken token = new PasswordResetToken(
                "tok-1", "user@example.com", Instant.now().plus(30, ChronoUnit.MINUTES), false);
        when(passwordResetPort.findByToken("tok-1")).thenReturn(Optional.of(token));

        assertThat(service.validateToken("tok-1")).isTrue();
    }

    @Test
    void validateToken_expiredToken_returnsFalse() {
        PasswordResetToken token = new PasswordResetToken(
                "tok-1", "user@example.com", Instant.now().minus(5, ChronoUnit.MINUTES), false);
        when(passwordResetPort.findByToken("tok-1")).thenReturn(Optional.of(token));

        assertThat(service.validateToken("tok-1")).isFalse();
    }

    @Test
    void validateToken_usedToken_returnsFalse() {
        PasswordResetToken token = new PasswordResetToken(
                "tok-1", "user@example.com", Instant.now().plus(30, ChronoUnit.MINUTES), true);
        when(passwordResetPort.findByToken("tok-1")).thenReturn(Optional.of(token));

        assertThat(service.validateToken("tok-1")).isFalse();
    }

    @Test
    void validateToken_unknownToken_returnsFalse() {
        when(passwordResetPort.findByToken("ghost")).thenReturn(Optional.empty());

        assertThat(service.validateToken("ghost")).isFalse();
    }

    // -------------------------------------------------------------------------
    // resetPassword()
    // -------------------------------------------------------------------------

    @Test
    void resetPassword_validToken_updatesPasswordAndMarksTokenUsed() {
        PasswordResetToken token = new PasswordResetToken(
                "tok-1", "user@example.com", Instant.now().plus(30, ChronoUnit.MINUTES), false);
        when(passwordResetPort.findByToken("tok-1")).thenReturn(Optional.of(token));
        when(appUserPort.findByEmail("user@example.com")).thenReturn(Optional.of(EXISTING_USER));
        when(passwordHashingPort.hash("newPassword123")).thenReturn("$2b$10$newHash");

        service.resetPassword("tok-1", "newPassword123");

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserPort).save(userCaptor.capture());
        assertThat(userCaptor.getValue().passwordHash()).isEqualTo("$2b$10$newHash");
        assertThat(userCaptor.getValue().email()).isEqualTo("user@example.com");

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetPort).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().used()).isTrue();
    }

    @Test
    void resetPassword_expiredToken_throwsInvalidResetTokenException() {
        PasswordResetToken token = new PasswordResetToken(
                "tok-1", "user@example.com", Instant.now().minus(5, ChronoUnit.MINUTES), false);
        when(passwordResetPort.findByToken("tok-1")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.resetPassword("tok-1", "newPassword123"))
                .isInstanceOf(InvalidResetTokenException.class);

        verify(appUserPort, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resetPassword_usedToken_throwsInvalidResetTokenException() {
        PasswordResetToken token = new PasswordResetToken(
                "tok-1", "user@example.com", Instant.now().plus(30, ChronoUnit.MINUTES), true);
        when(passwordResetPort.findByToken("tok-1")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.resetPassword("tok-1", "newPassword123"))
                .isInstanceOf(InvalidResetTokenException.class);

        verify(appUserPort, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resetPassword_unknownToken_throwsInvalidResetTokenException() {
        when(passwordResetPort.findByToken("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("ghost", "newPassword123"))
                .isInstanceOf(InvalidResetTokenException.class);

        verify(appUserPort, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
