package com.sewlect.identity.service;

import com.sewlect.identity.config.StyleTagCacheConfig;
import com.sewlect.identity.dto.StyleTagResponse;
import com.sewlect.common.taxonomy.repository.StyleTagRepository;
import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@AllArgsConstructor
public class StyleTagService {

    private final StyleTagRepository styleTagRepository;

    @Cacheable(StyleTagCacheConfig.STYLE_TAGS_CACHE)
    @Transactional(readOnly = true)
    public List<StyleTagResponse> listAll() {
        return styleTagRepository.findAll().stream()
                .map(tag -> new StyleTagResponse(tag.getId(), tag.getName()))
                .toList();
    }
}