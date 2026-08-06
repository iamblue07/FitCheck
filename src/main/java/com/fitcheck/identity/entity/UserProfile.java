package com.fitcheck.identity.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
@Getter
@Setter
@Table(name = "user_profiles")
@EntityListeners(AuditingEntityListener.class)
public class UserProfile {

    @Id
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    private Sex sex;

    private BigDecimal heightCm;

    private BigDecimal weightKg;

    private BigDecimal footLengthCm;

    private BigDecimal averageBudgetPerOutfit;

    private String currency;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}