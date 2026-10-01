package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.SavedPost;
import com.wgblackmon.aihealthcare.domain.model.SavedPostStatus;
import com.wgblackmon.aihealthcare.domain.model.SocialPlatform;
import com.wgblackmon.aihealthcare.domain.port.outbound.SavedPostPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SavedPostService}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
@ExtendWith(MockitoExtension.class)
class SavedPostServiceTest {

    @Mock
    private SavedPostPort savedPostPort;

    @InjectMocks
    private SavedPostService service;

    private SavedPost buildPost(String id, SavedPostStatus status) {
        Instant now = Instant.now();
        return new SavedPost(id, SocialPlatform.LINKEDIN, "2026-10-01",
                "body text", "comment", "my take", status, now, now, null);
    }

    @Test
    void save_createsNewDraftWithGeneratedId() {
        when(savedPostPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<SavedPost> captor = ArgumentCaptor.forClass(SavedPost.class);
        service.save(SocialPlatform.LINKEDIN, "2026-10-01", "body", "comment", "my take");

        verify(savedPostPort).save(captor.capture());
        SavedPost saved = captor.getValue();
        assertThat(saved.postId()).isNotBlank();
        assertThat(saved.status()).isEqualTo(SavedPostStatus.DRAFT);
        assertThat(saved.platform()).isEqualTo(SocialPlatform.LINKEDIN);
        assertThat(saved.yourTake()).isEqualTo("my take");
    }

    @Test
    void save_nullYourTake_defaultsToEmpty() {
        when(savedPostPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<SavedPost> captor = ArgumentCaptor.forClass(SavedPost.class);
        service.save(SocialPlatform.FACEBOOK, "2026-10-01", "body", null, null);

        verify(savedPostPort).save(captor.capture());
        assertThat(captor.getValue().yourTake()).isEmpty();
    }

    @Test
    void listDrafts_delegatesToPort() {
        SavedPost draft = buildPost("id-1", SavedPostStatus.DRAFT);
        when(savedPostPort.findByStatus(SavedPostStatus.DRAFT)).thenReturn(List.of(draft));

        List<SavedPost> result = service.listDrafts();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).postId()).isEqualTo("id-1");
    }

    @Test
    void update_nonExistentPost_throwsIllegalArgument() {
        when(savedPostPort.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("missing", "body", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void update_existingPost_updatesBodyAndTimestamp() {
        SavedPost existing = buildPost("id-1", SavedPostStatus.DRAFT);
        when(savedPostPort.findById("id-1")).thenReturn(Optional.of(existing));
        when(savedPostPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<SavedPost> captor = ArgumentCaptor.forClass(SavedPost.class);
        service.update("id-1", "new body", null, "new take");

        verify(savedPostPort).save(captor.capture());
        SavedPost updated = captor.getValue();
        assertThat(updated.body()).isEqualTo("new body");
        assertThat(updated.yourTake()).isEqualTo("new take");
        assertThat(updated.status()).isEqualTo(SavedPostStatus.DRAFT);
    }

    @Test
    void markPosted_setsStatusAndPostedAt() {
        SavedPost existing = buildPost("id-1", SavedPostStatus.DRAFT);
        when(savedPostPort.findById("id-1")).thenReturn(Optional.of(existing));
        when(savedPostPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<SavedPost> captor = ArgumentCaptor.forClass(SavedPost.class);
        service.markPosted("id-1");

        verify(savedPostPort).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(SavedPostStatus.POSTED);
        assertThat(captor.getValue().postedAt()).isNotNull();
    }

    @Test
    void delete_delegatesToPort() {
        service.delete("id-1");
        verify(savedPostPort).delete("id-1");
    }
}
