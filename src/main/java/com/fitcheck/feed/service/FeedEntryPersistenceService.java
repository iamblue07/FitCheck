package com.fitcheck.feed.service;

import com.fitcheck.feed.entity.FeedEntry;
import com.fitcheck.feed.repository.FeedEntryRepository;
import com.fitcheck.identity.entity.User;
import com.fitcheck.outfit.entity.Outfit;
import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class FeedEntryPersistenceService {

    private final FeedEntryRepository feedEntryRepository;

    @Transactional
    public boolean saveIfAbsent(User user, Outfit outfit, BigDecimal rankScore) {
        FeedEntry entry = FeedEntry.builder()
                .user(user)
                .outfit(outfit)
                .rankScore(rankScore)
                .build();
        try {
            feedEntryRepository.saveAndFlush(entry);
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    @Transactional
    public void markShown(List<UUID> ids, LocalDateTime shownAt) {
        feedEntryRepository.markShown(ids, shownAt);
    }
}