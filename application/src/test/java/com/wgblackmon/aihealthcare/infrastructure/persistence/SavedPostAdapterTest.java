package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.SavedPost;
import com.wgblackmon.aihealthcare.domain.model.SavedPostStatus;
import com.wgblackmon.aihealthcare.domain.model.SocialPlatform;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @DataJpaTest integration tests for {@link SavedPostAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
@DataJpaTest
@Import(SavedPostAdapter.class)
class SavedPostAdapterTest {

    @Autowired
    private SavedPostAdapter adapter;

    private SavedPost buildPost(String id, SocialPlatform platform, SavedPostStatus status) {
        Instant now = Instant.parse("2026-10-01T12:00:00Z");
        return new SavedPost(id, platform, "October 1, 2026",
                "Post body " + id, "comment", "my take", status, now, now, null);
    }

    @Test
    void saveAndFindById_roundTrip() {
        SavedPost post = buildPost("post-1", SocialPlatform.LINKEDIN, SavedPostStatus.DRAFT);
        adapter.save(post);

        Optional<SavedPost> result = adapter.findById("post-1");

        assertThat(result).isPresent();
        assertThat(result.get().postId()).isEqualTo("post-1");
        assertThat(result.get().platform()).isEqualTo(SocialPlatform.LINKEDIN);
        assertThat(result.get().status()).isEqualTo(SavedPostStatus.DRAFT);
    }

    @Test
    void findByStatus_returnsDraftOnly() {
        adapter.save(buildPost("draft-1", SocialPlatform.LINKEDIN, SavedPostStatus.DRAFT));
        adapter.save(buildPost("draft-2", SocialPlatform.FACEBOOK, SavedPostStatus.DRAFT));
        adapter.save(buildPost("posted-1", SocialPlatform.LINKEDIN, SavedPostStatus.POSTED));

        List<SavedPost> drafts = adapter.findByStatus(SavedPostStatus.DRAFT);

        assertThat(drafts).hasSize(2);
        assertThat(drafts).allMatch(p -> p.status() == SavedPostStatus.DRAFT);
    }

    @Test
    void save_overwritesExistingRecord() {
        adapter.save(buildPost("post-1", SocialPlatform.LINKEDIN, SavedPostStatus.DRAFT));

        Instant now = Instant.now();
        SavedPost updated = new SavedPost("post-1", SocialPlatform.LINKEDIN, "October 1, 2026",
                "updated body", null, null, SavedPostStatus.POSTED, now, now, now);
        adapter.save(updated);

        Optional<SavedPost> result = adapter.findById("post-1");
        assertThat(result).isPresent();
        assertThat(result.get().body()).isEqualTo("updated body");
        assertThat(result.get().status()).isEqualTo(SavedPostStatus.POSTED);
    }

    @Test
    void delete_removesRecord() {
        adapter.save(buildPost("post-del", SocialPlatform.FACEBOOK, SavedPostStatus.DRAFT));

        adapter.delete("post-del");

        assertThat(adapter.findById("post-del")).isEmpty();
    }

    @Test
    void findById_notFound_returnsEmpty() {
        assertThat(adapter.findById("nonexistent")).isEmpty();
    }
}
