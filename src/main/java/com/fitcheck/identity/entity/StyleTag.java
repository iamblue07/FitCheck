package com.fitcheck.identity.entity;

import com.fitcheck.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name="style_tags")
public class StyleTag extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

}
