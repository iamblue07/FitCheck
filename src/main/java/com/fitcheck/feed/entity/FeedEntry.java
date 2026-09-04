package com.fitcheck.feed.entity;

import com.fitcheck.common.persistence.AuditableEntity;
import com.fitcheck.identity.entity.User;
import com.fitcheck.outfit.entity.Outfit;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Table(name = "feed_entries")
public class FeedEntry extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outfit_id", nullable = false)
    private Outfit outfit;

    private BigDecimal rankScore;

    private LocalDateTime shownAt;

}