package com.sewlect.identity.service;

import com.sewlect.identity.dto.StyleTagResponse;
import com.sewlect.common.taxonomy.repository.StyleTagRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@AllArgsConstructor
public class StyleTagService {

    private final StyleTagRepository styleTagRepository;

    @Transactional(readOnly = true)
    public List<StyleTagResponse> listAll() {
        return styleTagRepository.findAll().stream()
                .map(tag -> new StyleTagResponse(tag.getId(), tag.getName()))
                .toList();
    }
}