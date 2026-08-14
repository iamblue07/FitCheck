package com.fitcheck.catalog.entity;

import com.fitcheck.common.persistence.BaseEntity;
import com.fitcheck.common.taxonomy.StyleTag;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "product_style_tags", uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "style_tag_id"}))
public class ProductStyleTag extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "style_tag_id", nullable = false)
    private StyleTag styleTag;
}
