package com.wgblackmon.aihealthcare.application.service;

import com.wgblackmon.aihealthcare.domain.exception.DuplicateSubscriberException;
import com.wgblackmon.aihealthcare.domain.exception.SubscriberNotFoundException;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterDeliveryPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterRunPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.service.DeliveryService;
import com.wgblackmon.aihealthcare.domain.service.NewsletterTeaserBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DeliveryService} subscriber management.
 *
 * <p>{@link SubscriberPort} is injected as a Mockito mock — no Spring context,
 * no database.  Tests cover the happy path for each use-case method plus the
 * two business-rule guards: duplicate detection on add and not-found check on remove.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-13
 * @updated 2026-04-13
 */
@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    @Mock
    private SubscriberPort          subscriberPort;
    @Mock
    private NewsletterRunPort       newsletterRunPort;
    @Mock
    private NewsletterDeliveryPort  newsletterDeliveryPort;
    @Mock
    private NewsletterTeaserBuilder teaserBuilder;

    private DeliveryService service;

    private static final String EMAIL = "jane.doe@example.com";
    private static final String NAME  = "Jane Doe";

    private static final Subscriber SUBSCRIBER = new Subscriber(
            EMAIL, NAME, true, Instant.parse("2026-04-13T10:00:00Z"), null);

    @BeforeEach
    void setUp() {
        service = new DeliveryService(subscriberPort, newsletterRunPort, newsletterDeliveryPort, teaserBuilder);
    }

    // -------------------------------------------------------------------------
    // addSubscriber()
    // -------------------------------------------------------------------------

    @Test
    void addSubscriber_savesAndReturnsNewSubscriber() {
        when(subscriberPort.findByEmail(EMAIL)).thenReturn(Optional.empty());

        Subscriber result = service.addSubscriber(EMAIL, NAME);

        ArgumentCaptor<Subscriber> captor = ArgumentCaptor.forClass(Subscriber.class);
        verify(subscriberPort).save(captor.capture());

        assertThat(captor.getValue().email()).isEqualTo(EMAIL);
        assertThat(captor.getValue().name()).isEqualTo(NAME);
        assertThat(captor.getValue().active()).isTrue();
        assertThat(captor.getValue().subscribedAt()).isNotNull();
        assertThat(result.email()).isEqualTo(EMAIL);
    }

    @Test
    void addSubscriber_duplicateEmail_throwsDuplicateSubscriberException() {
        when(subscriberPort.findByEmail(EMAIL)).thenReturn(Optional.of(SUBSCRIBER));

        assertThatThrownBy(() -> service.addSubscriber(EMAIL, NAME))
                .isInstanceOf(DuplicateSubscriberException.class)
                .hasMessageContaining(EMAIL);

        verify(subscriberPort, never()).save(any());
    }

    @Test
    void addSubscriber_blankEmail_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.addSubscriber("  ", NAME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");

        verify(subscriberPort, never()).findByEmail(anyString());
    }

    @Test
    void addSubscriber_blankName_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.addSubscriber(EMAIL, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");

        verify(subscriberPort, never()).findByEmail(anyString());
    }

    // -------------------------------------------------------------------------
    // removeSubscriber()
    // -------------------------------------------------------------------------

    @Test
    void removeSubscriber_callsDeleteByEmail() {
        when(subscriberPort.findByEmail(EMAIL)).thenReturn(Optional.of(SUBSCRIBER));

        service.removeSubscriber(EMAIL);

        verify(subscriberPort).deleteByEmail(EMAIL);
    }

    @Test
    void removeSubscriber_unknownEmail_throwsSubscriberNotFoundException() {
        when(subscriberPort.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeSubscriber(EMAIL))
                .isInstanceOf(SubscriberNotFoundException.class)
                .hasMessageContaining(EMAIL);

        verify(subscriberPort, never()).deleteByEmail(anyString());
    }

    // -------------------------------------------------------------------------
    // listSubscribers()
    // -------------------------------------------------------------------------

    @Test
    void listSubscribers_delegatesToPort() {
        when(subscriberPort.findAll()).thenReturn(List.of(SUBSCRIBER));

        List<Subscriber> result = service.listSubscribers();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).email()).isEqualTo(EMAIL);
    }

    @Test
    void listSubscribers_emptyStore_returnsEmptyList() {
        when(subscriberPort.findAll()).thenReturn(List.of());

        List<Subscriber> result = service.listSubscribers();

        assertThat(result).isEmpty();
    }
}
