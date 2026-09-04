package com.fitcheck.feed.service;

import com.fitcheck.feed.dto.FeedCursor;
import com.fitcheck.feed.dto.FeedPage;
import com.fitcheck.feed.entity.FeedEntry;
import com.fitcheck.feed.repository.FeedEntryRepository;
import com.fitcheck.identity.entity.UserProfile;
import com.fitcheck.identity.service.UserProfileQueryService;
import com.fitcheck.outfit.entity.Outfit;
import com.fitcheck.outfit.service.OutfitCandidateGenerator;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
@EnableConfigurationProperties(FeedProperties.class)
public class FeedGenerationService {

    private final UserProfileQueryService userProfileQueryService;
    private final OutfitCandidateGenerator outfitCandidateGenerator;
    private final FeedEntryRepository feedEntryRepository;
    private final FeedEntryPersistenceService feedEntryPersistenceService;
    private final FeedRankingService feedRankingService;
    private final FeedCursorCodec feedCursorCodec;
    private final FeedRefillGuard feedRefillGuard;

    @Qualifier("feedRefillExecutor")
    private final AsyncTaskExecutor feedRefillExecutor;

    private final FeedProperties feedProperties;
    private final Clock clock;

    public void refillFor(UUID userId) {
        UserProfile profile = userProfileQueryService.getById(userId);
        List<Outfit> candidates = outfitCandidateGenerator.generate(profile);

        int saved = 0;
        for (Outfit outfit : candidates) {
            if (feedEntryRepository.existsByUserIdAndOutfitId(userId, outfit.getId())) {
                continue;
            }
            BigDecimal rankScore = feedRankingService.rankScore(profile, outfit);
            if (feedEntryPersistenceService.saveIfAbsent(profile.getUser(), outfit, rankScore)) {
                saved++;
            }
        }

        log.debug("Feed refill for user {}: {} candidates generated, {} new entries saved",
                userId, candidates.size(), saved);
    }

    public FeedPage getPage(UUID userId, String cursor, int pageSize) {
        if (feedEntryRepository.countByUserIdAndShownAtIsNull(userId) == 0) {
            refillFor(userId);
        }

        Limit limit = Limit.of(pageSize);
        List<FeedEntry> page = cursor == null
                ? feedEntryRepository.findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(userId, limit)
                : fetchNextPage(userId, cursor, limit);

        if (!page.isEmpty()) {
            List<UUID> pageIds = page.stream().map(FeedEntry::getId).toList();
            feedEntryPersistenceService.markShown(pageIds, LocalDateTime.now(clock));
        }

        triggerRefillIfNeeded(userId);

        String nextCursor = page.size() < pageSize ? null : encodeCursor(page.get(page.size() - 1));
        return new FeedPage(page, nextCursor);
    }

    private List<FeedEntry> fetchNextPage(UUID userId, String cursor, Limit limit) {
        FeedCursor decoded = feedCursorCodec.decode(cursor);
        return feedEntryRepository.findNextPage(userId, decoded.rankScore(), decoded.id(), limit);
    }

    private void triggerRefillIfNeeded(UUID userId) {
        long remaining = feedEntryRepository.countByUserIdAndShownAtIsNull(userId);
        if (remaining >= feedProperties.refillThreshold()) {
            return;
        }
        if (!feedRefillGuard.tryClaim(userId)) {
            return;
        }
        feedRefillExecutor.submit(() -> {
            try {
                refillFor(userId);
            } catch (RuntimeException e) {
                log.error("Background feed refill failed for user {}", userId, e);
            } finally {
                feedRefillGuard.release(userId);
            }
        });
    }

    private String encodeCursor(FeedEntry lastEntry) {
        return feedCursorCodec.encode(new FeedCursor(lastEntry.getRankScore(), lastEntry.getId()));
    }
}