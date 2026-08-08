package com.fitcheck.identity.entity;

import com.fitcheck.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Table(name = "user_style_preferences", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "style_tag_id"}))
public class UserStylePreference extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "style_tag_id", nullable = false)
    private StyleTag styleTag;
}
