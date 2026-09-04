package com.fitcheck.feed.service;

import com.fitcheck.feed.config.FeedProperties;
import com.fitcheck.feed.dto.FeedCursor;
import com.fitcheck.feed.dto.FeedPage;
import com.fitcheck.feed.entity.FeedEntry;
import com.fitcheck.feed.repository.FeedEntryRepository;
import com.fitcheck.identity.entity.User;
import com.fitcheck.identity.entity.UserProfile;
import com.fitcheck.identity.service.UserProfileQueryService;
import com.fitcheck.outfit.entity.Outfit;
import com.fitcheck.outfit.service.OutfitCandidateGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.data.domain.Limit;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedGenerationServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-04T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private UserProfileQueryService userProfileQueryService;
    @Mock
    private OutfitCandidateGenerator outfitCandidateGenerator;
    @Mock
    private FeedEntryRepository feedEntryRepository;
    @Mock
    private FeedEntryPersistenceService feedEntryPersistenceService;
    @Mock
    private FeedRankingService feedRankingService;
    @Mock
    private FeedCursorCodec feedCursorCodec;
    @Mock
    private FeedRefillGuard feedRefillGuard;
    @Mock
    private AsyncTaskExecutor feedRefillExecutor;

    private final FeedProperties feedProperties = new FeedProperties(20, 30);

    private FeedGenerationService service;

    private UUID userId;
    private UserProfile profile;

    @BeforeEach
    void setUp() {
        service = new FeedGenerationService(
                userProfileQueryService, outfitCandidateGenerator, feedEntryRepository,
                feedEntryPersistenceService, feedRankingService, feedCursorCodec, feedRefillGuard,
                feedRefillExecutor, feedProperties, FIXED_CLOCK);

        userId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        profile = UserProfile.builder().userId(userId).user(user).averageBudgetPerOutfit(new BigDecimal("100")).build();
        lenient().when(userProfileQueryService.getById(userId)).thenReturn(profile);
    }

    // ================= refillFor =================

    @Test
    void refillFor_allCandidatesNew_savesEveryOne() {
        Outfit outfit1 = outfitWithId();
        Outfit outfit2 = outfitWithId();
        when(outfitCandidateGenerator.generate(profile)).thenReturn(List.of(outfit1, outfit2));
        when(feedEntryRepository.existsByUserIdAndOutfitId(eq(userId), any())).thenReturn(false);
        when(feedRankingService.rankScore(any(), any())).thenReturn(new BigDecimal("0.5"));
        when(feedEntryPersistenceService.saveIfAbsent(any(), any(), any())).thenReturn(true);

        service.refillFor(userId);

        verify(feedEntryPersistenceService).saveIfAbsent(profile.getUser(), outfit1, new BigDecimal("0.5"));
        verify(feedEntryPersistenceService).saveIfAbsent(profile.getUser(), outfit2, new BigDecimal("0.5"));
    }

    @Test
    void refillFor_someCandidatesAlreadyInUsersFeed_skipsThemEntirely() {
        Outfit alreadyPresent = outfitWithId();
        Outfit brandNew = outfitWithId();
        when(outfitCandidateGenerator.generate(profile)).thenReturn(List.of(alreadyPresent, brandNew));
        when(feedEntryRepository.existsByUserIdAndOutfitId(userId, alreadyPresent.getId())).thenReturn(true);
        when(feedEntryRepository.existsByUserIdAndOutfitId(userId, brandNew.getId())).thenReturn(false);
        when(feedRankingService.rankScore(any(), eq(brandNew))).thenReturn(new BigDecimal("0.6"));
        when(feedEntryPersistenceService.saveIfAbsent(any(), any(), any())).thenReturn(true);

        service.refillFor(userId);

        // the already-present outfit never even reaches rank-scoring or persistence
        verify(feedRankingService, never()).rankScore(any(), eq(alreadyPresent));
        verify(feedEntryPersistenceService, never()).saveIfAbsent(any(), eq(alreadyPresent), any());
        verify(feedEntryPersistenceService).saveIfAbsent(profile.getUser(), brandNew, new BigDecimal("0.6"));
    }

    @Test
    void refillFor_oneCandidateLosesRaceOnSave_othersStillProcessedNoException() {
        Outfit raceLoser = outfitWithId();
        Outfit raceWinner = outfitWithId();
        when(outfitCandidateGenerator.generate(profile)).thenReturn(List.of(raceLoser, raceWinner));
        when(feedEntryRepository.existsByUserIdAndOutfitId(eq(userId), any())).thenReturn(false);
        when(feedRankingService.rankScore(any(), any())).thenReturn(new BigDecimal("0.5"));
        when(feedEntryPersistenceService.saveIfAbsent(any(), eq(raceLoser), any())).thenReturn(false);
        when(feedEntryPersistenceService.saveIfAbsent(any(), eq(raceWinner), any())).thenReturn(true);

        assertThatCode(() -> service.refillFor(userId)).doesNotThrowAnyException();

        verify(feedEntryPersistenceService).saveIfAbsent(any(), eq(raceLoser), any());
        verify(feedEntryPersistenceService).saveIfAbsent(any(), eq(raceWinner), any());
    }

    @Test
    void refillFor_generatorReturnsNoCandidates_completesCleanlyWithNoWrites() {
        when(outfitCandidateGenerator.generate(profile)).thenReturn(List.of());

        service.refillFor(userId);

        verify(feedEntryPersistenceService, never()).saveIfAbsent(any(), any(), any());
        verify(feedEntryRepository, never()).existsByUserIdAndOutfitId(any(), any());
    }

    // ================= getPage: cold start / warm start =================

    @Test
    void getPage_zeroUnseenEntries_triggersSynchronousRefillBeforeReturning() {
        when(feedEntryRepository.countByUserIdAndShownAtIsNull(userId)).thenReturn(0L, 30L); // cold-start check, then re-check post-refill
        when(outfitCandidateGenerator.generate(profile)).thenReturn(List.of());
        when(feedEntryRepository.findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(eq(userId), any()))
                .thenReturn(List.of());

        service.getPage(userId, null, 20);

        // proves refillFor actually ran synchronously, not just that count was checked
        verify(userProfileQueryService).getById(userId);
        verify(outfitCandidateGenerator).generate(profile);
    }

    @Test
    void getPage_nonZeroUnseenAboveThreshold_neverTriggersAnyRefillAtAll() {
        when(feedEntryRepository.countByUserIdAndShownAtIsNull(userId)).thenReturn(40L); // both checks return the same, well above threshold=30
        when(feedEntryRepository.findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(eq(userId), any()))
                .thenReturn(List.of());

        service.getPage(userId, null, 20);

        verify(userProfileQueryService, never()).getById(any());
        verify(outfitCandidateGenerator, never()).generate(any());
        verify(feedRefillExecutor, never()).submit(any(Runnable.class));
    }

    // ================= getPage: cursor branch selection =================

    @Test
    void getPage_nullCursor_usesFirstPageQuery() {
        when(feedEntryRepository.countByUserIdAndShownAtIsNull(userId)).thenReturn(40L);
        when(feedEntryRepository.findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(eq(userId), any()))
                .thenReturn(List.of());

        service.getPage(userId, null, 20);

        verify(feedEntryRepository).findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(eq(userId), any());
        verify(feedCursorCodec, never()).decode(any());
    }

    @Test
    void getPage_presentCursor_decodesAndUsesKeysetQuery() {
        when(feedEntryRepository.countByUserIdAndShownAtIsNull(userId)).thenReturn(40L);
        FeedCursor decoded = new FeedCursor(new BigDecimal("0.7500"), UUID.randomUUID());
        when(feedCursorCodec.decode("opaque-cursor")).thenReturn(decoded);
        when(feedEntryRepository.findNextPage(eq(userId), eq(decoded.rankScore()), eq(decoded.id()), any()))
                .thenReturn(List.of());

        service.getPage(userId, "opaque-cursor", 20);

        verify(feedEntryRepository).findNextPage(userId, decoded.rankScore(), decoded.id(), Limit.of(20));
        verify(feedEntryRepository, never()).findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(any(), any());
    }

    // ================= getPage: marking shown =================

    @Test
    void getPage_nonEmptyPage_marksEveryReturnedEntryShownWithClockDerivedTimestamp() {
        FeedEntry entry1 = entryWithId();
        FeedEntry entry2 = entryWithId();
        when(feedEntryRepository.countByUserIdAndShownAtIsNull(userId)).thenReturn(40L);
        when(feedEntryRepository.findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(eq(userId), any()))
                .thenReturn(List.of(entry1, entry2));

        service.getPage(userId, null, 20);

        LocalDateTime expectedNow = LocalDateTime.now(FIXED_CLOCK);
        verify(feedEntryPersistenceService).markShown(List.of(entry1.getId(), entry2.getId()), expectedNow);
    }

    @Test
    void getPage_emptyPage_neverCallsMarkShown() {
        when(feedEntryRepository.countByUserIdAndShownAtIsNull(userId)).thenReturn(40L);
        when(feedEntryRepository.findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(eq(userId), any()))
                .thenReturn(List.of());

        service.getPage(userId, null, 20);

        verify(feedEntryPersistenceService, never()).markShown(any(), any());
    }

    // ================= getPage: nextCursor =================

    @Test
    void getPage_fullPage_encodesNextCursorFromLastEntry() {
        FeedEntry last = entryWithId();
        when(feedEntryRepository.countByUserIdAndShownAtIsNull(userId)).thenReturn(40L);
        when(feedEntryRepository.findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(eq(userId), any()))
                .thenReturn(List.of(last));
        when(feedCursorCodec.encode(new FeedCursor(last.getRankScore(), last.getId()))).thenReturn("encoded-cursor");

        FeedPage page = service.getPage(userId, null, 1); // pageSize=1, page.size()==1 -> "full"

        assertThat(page.nextCursor()).isEqualTo("encoded-cursor");
    }

    @Test
    void getPage_shortPage_nextCursorIsNullAndEncodeNeverCalled() {
        FeedEntry only = entryWithId();
        when(feedEntryRepository.countByUserIdAndShownAtIsNull(userId)).thenReturn(40L);
        when(feedEntryRepository.findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(eq(userId), any()))
                .thenReturn(List.of(only)); // 1 result, pageSize 20 -> short page

        FeedPage page = service.getPage(userId, null, 20);

        assertThat(page.nextCursor()).isNull();
        verify(feedCursorCodec, never()).encode(any());
    }

    @Test
    void getPage_emptyPage_nextCursorIsNull() {
        when(feedEntryRepository.countByUserIdAndShownAtIsNull(userId)).thenReturn(40L);
        when(feedEntryRepository.findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(eq(userId), any()))
                .thenReturn(List.of());

        FeedPage page = service.getPage(userId, null, 20);

        assertThat(page.nextCursor()).isNull();
    }

    // ================= getPage: background refill triggering =================

    @Test
    void getPage_belowThresholdAndGuardAvailable_submitsBackgroundRefill() {
        when(feedEntryRepository.countByUserIdAndShownAtIsNull(userId)).thenReturn(40L, 10L); // warm start, then low after paging
        when(feedEntryRepository.findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(eq(userId), any()))
                .thenReturn(List.of());
        when(feedRefillGuard.tryClaim(userId)).thenReturn(true);

        service.getPage(userId, null, 20);

        verify(feedRefillExecutor).submit(any(Runnable.class));
    }

    @Test
    void getPage_aboveThreshold_neverAttemptsToClaimOrSubmit() {
        when(feedEntryRepository.countByUserIdAndShownAtIsNull(userId)).thenReturn(40L); // stays above threshold=30 both checks
        when(feedEntryRepository.findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(eq(userId), any()))
                .thenReturn(List.of());

        service.getPage(userId, null, 20);

        verify(feedRefillGuard, never()).tryClaim(any());
        verify(feedRefillExecutor, never()).submit(any(Runnable.class));
    }

    @Test
    void getPage_belowThresholdButRefillAlreadyInFlight_doesNotSubmitASecondOne() {
        when(feedEntryRepository.countByUserIdAndShownAtIsNull(userId)).thenReturn(40L, 5L);
        when(feedEntryRepository.findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(eq(userId), any()))
                .thenReturn(List.of());
        when(feedRefillGuard.tryClaim(userId)).thenReturn(false); // another request already claimed it

        service.getPage(userId, null, 20);

        verify(feedRefillExecutor, never()).submit(any(Runnable.class));
    }

    // ================= captured async task behavior =================

    @SuppressWarnings("unchecked")
    @Test
    void backgroundRefillTask_onSuccess_releasesGuardAfterCompleting() {
        when(feedEntryRepository.countByUserIdAndShownAtIsNull(userId)).thenReturn(40L, 5L);
        when(feedEntryRepository.findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(eq(userId), any()))
                .thenReturn(List.of());
        when(feedRefillGuard.tryClaim(userId)).thenReturn(true);
        when(outfitCandidateGenerator.generate(profile)).thenReturn(List.of());
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(feedRefillExecutor.submit(taskCaptor.capture()))
                .thenReturn(mock(Future.class));

        service.getPage(userId, null, 20);
        taskCaptor.getValue().run(); // simulate the executor actually running it

        verify(feedRefillGuard).release(userId);
        // and the refill itself genuinely ran, not just a no-op
        verify(outfitCandidateGenerator, times(1)).generate(profile);
    }

    @SuppressWarnings("unchecked")
    @Test
    void backgroundRefillTask_refillThrows_exceptionSwallowedAndGuardStillReleased() {
        when(feedEntryRepository.countByUserIdAndShownAtIsNull(userId)).thenReturn(40L, 5L);
        when(feedEntryRepository.findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(eq(userId), any()))
                .thenReturn(List.of());
        when(feedRefillGuard.tryClaim(userId)).thenReturn(true);
        when(outfitCandidateGenerator.generate(profile)).thenThrow(new IllegalStateException("Ollama is down"));
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(feedRefillExecutor.submit(taskCaptor.capture()))
                .thenReturn(mock(Future.class));

        service.getPage(userId, null, 20);

        // the whole point: a background failure must never propagate out and kill the pool thread
        assertThatCode(() -> taskCaptor.getValue().run()).doesNotThrowAnyException();
        // ...but the guard release in the finally block must still have fired, or this user
        // would be permanently locked out of ever refilling again
        verify(feedRefillGuard).release(userId);
    }

    // ================= fixtures =================

    private Outfit outfitWithId() {
        return Outfit.builder().id(UUID.randomUUID()).compatibilityScore(new BigDecimal("0.7")).build();
    }

    private FeedEntry entryWithId() {
        return FeedEntry.builder().id(UUID.randomUUID()).rankScore(new BigDecimal("0.6500")).build();
    }

    @SuppressWarnings("unchecked")
    private Future<?> mockFuture() {
        return org.mockito.Mockito.mock(Future.class);
    }
}