package com.fitcheck.outfit.entity;

import com.fitcheck.common.persistence.AuditableEntity;
import com.fitcheck.identity.entity.User;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Table(name = "ai_prompt_queries")
public class AiPromptQuery extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String rawPrompt;

    @JdbcTypeCode(SqlTypes.JSON)
    private String structuredQuery;

    @Column(nullable = false)
    private boolean matchProfile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiPromptQueryStatus status;

    private String errorMessage;
}