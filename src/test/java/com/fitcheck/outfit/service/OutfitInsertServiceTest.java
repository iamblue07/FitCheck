package com.fitcheck.outfit.service;

import com.fitcheck.outfit.domain.CompatibilityScoreBreakdown;
import com.fitcheck.outfit.entity.Outfit;
import com.fitcheck.outfit.enums.OutfitSource;
import com.fitcheck.outfit.repository.OutfitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutfitInsertServiceTest {

    @Mock
    private OutfitRepository outfitRepository;

    private OutfitInsertService service;

    @BeforeEach
    void setUp() {
        service = new OutfitInsertService(outfitRepository);
    }

    @Test
    void insert_buildsTheRowFromEveryBreakdownComponentPlusHashAndSource() {
        when(outfitRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompatibilityScoreBreakdown breakdown = new CompatibilityScoreBreakdown(
                new BigDecimal("0.9100"), new BigDecimal("0.8200"), new BigDecimal("0.8650"),
                new BigDecimal("0.7300"), new BigDecimal("0.7975"));

        Outfit result = service.insert(breakdown, "hash-abc", OutfitSource.MANUAL_SWAP);

        ArgumentCaptor<Outfit> captor = ArgumentCaptor.forClass(Outfit.class);
        verify(outfitRepository).saveAndFlush(captor.capture());
        Outfit persisted = captor.getValue();

        assertThat(result).isSameAs(persisted);
        assertThat(persisted.getSource()).isEqualTo(OutfitSource.MANUAL_SWAP);
        assertThat(persisted.getItemSetHash()).isEqualTo("hash-abc");
        assertThat(persisted.getColorScore()).isEqualByComparingTo("0.9100");
        assertThat(persisted.getLayeringScore()).isEqualByComparingTo("0.8200");
        assertThat(persisted.getStructuredScore()).isEqualByComparingTo("0.8650");
        assertThat(persisted.getEmbeddingScore()).isEqualByComparingTo("0.7300");
        assertThat(persisted.getCompatibilityScore()).isEqualByComparingTo("0.7975");
    }

    @Test
    void insert_neverLeavesAnyOfTheFourNotNullScoreColumnsUnset() {
        when(outfitRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Outfit result = service.insert(breakdownOf("0.5"), "hash-abc", OutfitSource.PROFILE_GENERATED);

        assertThat(result.getColorScore()).isNotNull();
        assertThat(result.getLayeringScore()).isNotNull();
        assertThat(result.getStructuredScore()).isNotNull();
        assertThat(result.getEmbeddingScore()).isNotNull();
    }

    @Test
    void insert_returnsTheFlushedEntityRatherThanTheBuiltOne() {
        Outfit flushed = Outfit.builder().id(UUID.randomUUID()).itemSetHash("hash-abc").build();
        when(outfitRepository.saveAndFlush(any())).thenReturn(flushed);

        Outfit result = service.insert(breakdownOf("0.5"), "hash-abc", OutfitSource.AI_PROMPT);

        assertThat(result).isSameAs(flushed);
    }

    @Test
    void insert_uniqueConstraintViolation_propagatesSoTheCallerCanReQuery() {
        DataIntegrityViolationException violation =
                new DataIntegrityViolationException("duplicate key value violates uq item_set_hash");
        when(outfitRepository.saveAndFlush(any())).thenThrow(violation);

        assertThatThrownBy(() -> service.insert(breakdownOf("0.5"), "hash-abc", OutfitSource.AI_PROMPT))
                .isSameAs(violation);

        verify(outfitRepository, times(1)).saveAndFlush(any());
    }

    private CompatibilityScoreBreakdown breakdownOf(String score) {
        BigDecimal value = new BigDecimal(score);
        return new CompatibilityScoreBreakdown(value, value, value, value, value);
    }
}