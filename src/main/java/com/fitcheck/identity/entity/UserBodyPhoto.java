package com.fitcheck.identity.entity;

import com.fitcheck.common.persistence.AuditableEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_body_photos", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "photo_type"}))
public class UserBodyPhoto extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PhotoType photoType;

    @Column(nullable = false)
    private String storageKey;
}
