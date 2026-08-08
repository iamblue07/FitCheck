package com.fitcheck.identity.service;

import com.fitcheck.identity.dto.StyleTagResponse;
import com.fitcheck.identity.entity.StyleTag;
import com.fitcheck.identity.repository.StyleTagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StyleTagServiceTest {

    @Mock
    private StyleTagRepository styleTagRepository;

    @InjectMocks
    private StyleTagService styleTagService;

    @Test
    void listAll_returnsEverySeededTag() {
        StyleTag minimalist = StyleTag.builder().id(UUID.randomUUID()).name("minimalist").build();
        StyleTag streetwear = StyleTag.builder().id(UUID.randomUUID()).name("streetwear").build();
        StyleTag preppy = StyleTag.builder().id(UUID.randomUUID()).name("preppy").build();

        when(styleTagRepository.findAll()).thenReturn(List.of(minimalist, streetwear, preppy));

        List<StyleTagResponse> result = styleTagService.listAll();

        assertThat(result).containsExactlyInAnyOrder(
                new StyleTagResponse(minimalist.getId(), "minimalist"),
                new StyleTagResponse(streetwear.getId(), "streetwear"),
                new StyleTagResponse(preppy.getId(), "preppy"));
    }

    @Test
    void listAll_noTagsSeeded_returnsEmptyList() {
        when(styleTagRepository.findAll()).thenReturn(List.of());

        List<StyleTagResponse> result = styleTagService.listAll();

        assertThat(result).isEmpty();
    }

}