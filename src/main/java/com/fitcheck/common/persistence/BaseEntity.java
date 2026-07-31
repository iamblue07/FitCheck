package com.fitcheck.common.persistence;

import jakarta.persistence.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.util.UUID;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    public UUID getId() {
        return id;
    }
}
