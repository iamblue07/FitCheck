package com.fitcheck.identity.repository;

import com.fitcheck.identity.entity.StyleTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StyleTagRepository extends JpaRepository<StyleTag, UUID> {

    List<StyleTag> findAllById(Iterable<UUID> ids);
}
