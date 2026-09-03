package com.fitcheck.catalog.entity;

import com.fitcheck.common.persistence.AuditableEntity;
import com.fitcheck.common.taxonomy.GarmentRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "products")
public class Product extends AuditableEntity {

    public static final int TEXT_EMBEDDING_DIMENSIONS = 2000;

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
    private String occasion;
    private String primaryColor;
    private String secondaryColor;
    private String layeringRole;

    @Enumerated(EnumType.STRING)
    private GarmentRole garmentRole;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = TEXT_EMBEDDING_DIMENSIONS)
    private float[] textEmbedding;

}
