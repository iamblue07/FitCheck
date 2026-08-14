package com.fitcheck.catalog.entity;

import com.fitcheck.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Table(name = "products")
public class Product extends AuditableEntity {

    @Column(nullable = false)
    private String externalId;
    private String gender;
    private String masterCategory;
    private String subCategory;
    private String articleType;
    private String baseColour;
    private String season;
    private Integer year;
    private String usage;
    private String productDisplayName;
    private String imageUrl;
    private String fit;
    private String silhouette;
    private String pattern;
    private String materialGuess;
    private String formality;
    private String description;
    private BigDecimal basePrice;
    private String currency;

}
