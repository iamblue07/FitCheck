package com.fitcheck.social.repository;

import com.fitcheck.common.persistence.config.JpaAuditingConfig;
import com.fitcheck.identity.entity.User;
import com.fitcheck.identity.enums.Role;
import com.fitcheck.identity.repository.UserRepository;
import com.fitcheck.outfit.entity.Outfit;
import com.fitcheck.outfit.enums.OutfitSource;
import com.fitcheck.outfit.repository.OutfitRepository;
import com.fitcheck.social.entity.UserOutfitInteraction;
import com.fitcheck.social.enums.InteractionType;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(JpaAuditingConfig.class)
@TestPropertySource(properties = "spring.test.database.replace=none")
class UserOutfitInteractionRepositoryTest {

    @Autowired
    private UserOutfitInteractionRepository userOutfitInteractionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OutfitRepository outfitRepository;

    @Autowired
    private EntityManager entityManager;

    private User user;
    private User otherUser;
    private Outfit firstOutfit;
    private Outfit secondOutfit;

    @BeforeEach
    void setUp() {
        user = persistUser();
        otherUser = persistUser();
        firstOutfit = persistOutfit();
        secondOutfit = persistOutfit();
    }

    @Test
    void findByUserIdAndInteractionTypeOrderByCreatedAtDesc_joinFetchesOutfitWithoutLazyInitializationException() {
        persistInteraction(user, firstOutfit, InteractionType.SAVE);
        entityManager.flush();
        entityManager.clear();

        Page<UserOutfitInteraction> page = userOutfitInteractionRepository
                .findByUserIdAndInteractionTypeOrderByCreatedAtDesc(
                        user.getId(), InteractionType.SAVE, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(Hibernate.isInitialized(page.getContent().get(0).getOutfit())).isTrue();
        assertThat(page.getContent().get(0).getOutfit().getItemSetHash()).isNotNull();
    }

    @Test
    void findByUserIdAndInteractionTypeOrderByCreatedAtDesc_tiedCreatedAtTimestamps_secondarySortByIdKeepsOrderDeterministic() {
        UserOutfitInteraction first = persistInteraction(user, firstOutfit, InteractionType.SAVE);
        UserOutfitInteraction second = persistInteraction(user, secondOutfit, InteractionType.SAVE);
        entityManager.flush();

        LocalDateTime tiedTimestamp = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
        entityManager.createQuery(
                        "UPDATE UserOutfitInteraction i SET i.createdAt = :ts WHERE i.id IN :ids")
                .setParameter("ts", tiedTimestamp)
                .setParameter("ids", List.of(first.getId(), second.getId()))
                .executeUpdate();
        entityManager.clear();

        List<UUID> firstCallOrder = idsOf(userOutfitInteractionRepository
                .findByUserIdAndInteractionTypeOrderByCreatedAtDesc(
                        user.getId(), InteractionType.SAVE, PageRequest.of(0, 20)));
        entityManager.clear();
        List<UUID> secondCallOrder = idsOf(userOutfitInteractionRepository
                .findByUserIdAndInteractionTypeOrderByCreatedAtDesc(
                        user.getId(), InteractionType.SAVE, PageRequest.of(0, 20)));

        assertThat(firstCallOrder).containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThat(secondCallOrder).containsExactlyElementsOf(firstCallOrder);
    }

    @Test
    void findOutfitIdsByUserIdAndInteractionType_returnsScalarUuidsNotFullEntities() {
        persistInteraction(user, firstOutfit, InteractionType.LIKE);
        persistInteraction(user, secondOutfit, InteractionType.LIKE);
        entityManager.flush();
        entityManager.clear();

        List<UUID> outfitIds = userOutfitInteractionRepository
                .findOutfitIdsByUserIdAndInteractionType(user.getId(), InteractionType.LIKE);

        assertThat(outfitIds).containsExactlyInAnyOrder(firstOutfit.getId(), secondOutfit.getId());
        assertThat((Object) outfitIds.get(0)).isInstanceOf(UUID.class);
        assertThat((Object) outfitIds.get(0)).isNotInstanceOf(UserOutfitInteraction.class);
        assertThat((Object) outfitIds.get(0)).isNotInstanceOf(Outfit.class);
    }

    @Test
    void existsByUserIdAndOutfitIdAndInteractionType_afterInsert_trueForThatTripleOnlyFalseForOtherTypesOrUsers() {
        persistInteraction(user, firstOutfit, InteractionType.SAVE);
        entityManager.flush();
        entityManager.clear();

        assertThat(userOutfitInteractionRepository.existsByUserIdAndOutfitIdAndInteractionType(
                user.getId(), firstOutfit.getId(), InteractionType.SAVE)).isTrue();
        assertThat(userOutfitInteractionRepository.existsByUserIdAndOutfitIdAndInteractionType(
                user.getId(), firstOutfit.getId(), InteractionType.LIKE)).isFalse();
        assertThat(userOutfitInteractionRepository.existsByUserIdAndOutfitIdAndInteractionType(
                otherUser.getId(), firstOutfit.getId(), InteractionType.SAVE)).isFalse();
        assertThat(userOutfitInteractionRepository.existsByUserIdAndOutfitIdAndInteractionType(
                user.getId(), secondOutfit.getId(), InteractionType.SAVE)).isFalse();
    }

    @Test
    void uniqueConstraint_duplicateInsertOnSameTriple_throwsDataIntegrityViolationException() {
        persistInteraction(user, firstOutfit, InteractionType.SAVE);
        entityManager.flush();

        UserOutfitInteraction duplicate = UserOutfitInteraction.builder()
                .user(user)
                .outfit(firstOutfit)
                .interactionType(InteractionType.SAVE)
                .build();

        assertThatThrownBy(() -> userOutfitInteractionRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private List<UUID> idsOf(Page<UserOutfitInteraction> page) {
        return page.getContent().stream().map(UserOutfitInteraction::getId).toList();
    }

    private UserOutfitInteraction persistInteraction(User owner, Outfit outfit, InteractionType type) {
        return userOutfitInteractionRepository.save(UserOutfitInteraction.builder()
                .user(owner)
                .outfit(outfit)
                .interactionType(type)
                .build());
    }

    private User persistUser() {
        return userRepository.save(User.builder()
                .email("test-" + UUID.randomUUID() + "@example.com")
                .passwordHash("{noop}irrelevant")
                .role(Role.USER)
                .build());
    }

    private Outfit persistOutfit() {
        return outfitRepository.save(Outfit.builder()
                .source(OutfitSource.PROFILE_GENERATED)
                .itemSetHash("hash-" + UUID.randomUUID())
                .colorScore(new BigDecimal("0.9000"))
                .layeringScore(new BigDecimal("0.8000"))
                .structuredScore(new BigDecimal("0.8500"))
                .embeddingScore(new BigDecimal("0.7000"))
                .compatibilityScore(new BigDecimal("0.8200"))
                .build());
    }
}