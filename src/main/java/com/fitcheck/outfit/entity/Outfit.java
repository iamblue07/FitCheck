package com.fitcheck.outfit.entity;

import com.fitcheck.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Table(name = "outfits")
public class Outfit extends AuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutfitSource source;

    private BigDecimal compatibilityScore;

    private BigDecimal colorScore;

    private BigDecimal layeringScore;

    private BigDecimal structuredScore;

    private BigDecimal embeddingScore;

    @Column(nullable = false, unique = true)
    private String itemSetHash;

}