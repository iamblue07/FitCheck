package com.fitcheck.feed.service;

import com.fitcheck.feed.entity.FeedEntry;
import com.fitcheck.feed.repository.FeedEntryRepository;
import com.fitcheck.identity.entity.User;
import com.fitcheck.outfit.entity.Outfit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedEntryPersistenceServiceTest {

    @Mock
    private FeedEntryRepository feedEntryRepository;

    private FeedEntryPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new FeedEntryPersistenceService(feedEntryRepository);
    }

    @Test
    void saveIfAbsent_success_buildsEntryWithGivenFieldsAndReturnsTrue() {
        User user = User.builder().id(UUID.randomUUID()).build();
        Outfit outfit = Outfit.builder().id(UUID.randomUUID()).build();
        BigDecimal rankScore = new BigDecimal("0.8765");
        when(feedEntryRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        boolean result = service.saveIfAbsent(user, outfit, rankScore);

        assertThat(result).isTrue();
        ArgumentCaptor<FeedEntry> captor = ArgumentCaptor.forClass(FeedEntry.class);
        verify(feedEntryRepository).saveAndFlush(captor.capture());
        FeedEntry saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getOutfit()).isEqualTo(outfit);
        assertThat(saved.getRankScore()).isEqualByComparingTo(rankScore);
        assertThat(saved.getShownAt()).isNull();
    }

    @Test
    void saveIfAbsent_uniqueConstraintViolation_returnsFalseRatherThanPropagating() {
        User user = User.builder().id(UUID.randomUUID()).build();
        Outfit outfit = Outfit.builder().id(UUID.randomUUID()).build();
        when(feedEntryRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint \"uq_feed_entries_user_outfit\""));

        boolean result = service.saveIfAbsent(user, outfit, new BigDecimal("0.5"));

        assertThat(result).isFalse();
    }

    @Test
    void saveIfAbsent_someOtherRuntimeException_isNotSwallowed() {
        // only the unique-constraint race is a legitimate "skip" - anything else is a real bug
        // and must surface, not be silently treated the same as a benign race
        User user = User.builder().id(UUID.randomUUID()).build();
        Outfit outfit = Outfit.builder().id(UUID.randomUUID()).build();
        when(feedEntryRepository.saveAndFlush(any())).thenThrow(new IllegalStateException("unexpected"));

        assertThatThrownBy(() -> service.saveIfAbsent(user, outfit, new BigDecimal("0.5")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void markShown_delegatesIdsAndTimestampExactlyToRepository() {
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        LocalDateTime shownAt = LocalDateTime.of(2026, 9, 4, 12, 0);

        service.markShown(ids, shownAt);

        verify(feedEntryRepository).markShown(ids, shownAt);
    }

    @Test
    void markShown_emptyIdList_stillDelegatesRatherThanShortCircuiting() {
        // the short-circuit ("don't call if page was empty") is the caller's job (FeedGenerationService),
        // not this method's - it should do exactly what it's told
        service.markShown(List.of(), LocalDateTime.now());

        verify(feedEntryRepository).markShown(eq(List.of()), any());
    }
}