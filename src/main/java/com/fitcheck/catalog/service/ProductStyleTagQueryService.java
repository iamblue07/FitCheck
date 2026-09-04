package com.fitcheck.catalog.service;

import com.fitcheck.catalog.repository.ProductStyleTagRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class ProductStyleTagQueryService {

    private final ProductStyleTagRepository productStyleTagRepository;

    public List<UUID> findProductIdsByStyleTagIds(Collection<UUID> styleTagIds) {
        return productStyleTagRepository.findDistinctProductIdByStyleTagIdIn(styleTagIds);
    }

    public List<UUID> findStyleTagIdsByProductIds(Collection<UUID> productIds) {
        return productStyleTagRepository.findDistinctStyleTagIdByProductIdIn(productIds);
    }
}