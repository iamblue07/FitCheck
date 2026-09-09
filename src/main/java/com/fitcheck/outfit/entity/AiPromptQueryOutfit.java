package com.fitcheck.outfit.entity;

import com.fitcheck.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Table(name = "ai_prompt_query_outfits",
        uniqueConstraints = @UniqueConstraint(name = "uq_ai_prompt_query_outfits",
                columnNames = {"ai_prompt_query_id", "outfit_id"}))
public class AiPromptQueryOutfit extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_prompt_query_id", nullable = false)
    private AiPromptQuery aiPromptQuery;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outfit_id", nullable = false)
    private Outfit outfit;

    @Column(nullable = false)
    private int rank;

}