package com.fitcheck.tryon.entity;

import com.fitcheck.common.persistence.AuditableEntity;
import com.fitcheck.identity.entity.User;
import com.fitcheck.outfit.entity.Outfit;
import com.fitcheck.tryon.enums.TryonRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Table(name = "tryon_requests")
public class TryonRequest extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outfit_id")
    private Outfit outfit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TryonRequestStatus status;

    private String resultImageStorageKey;

    private String errorMessage;

    private LocalDateTime completedAt;

}