package com.fitcheck.catalog.pipeline;

import com.fitcheck.catalog.config.GarmentRoleBackfillProperties;
import com.fitcheck.catalog.entity.Product;
import com.fitcheck.catalog.repository.ProductRepository;
import com.fitcheck.common.taxonomy.GarmentRole;
import com.fitcheck.common.taxonomy.GarmentRoleResolver;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@AllArgsConstructor
@EnableConfigurationProperties(GarmentRoleBackfillProperties.class)
public class GarmentRoleBackfillRunner implements CommandLineRunner {

    private static final int PAGE_SIZE = 500;

    private final GarmentRoleBackfillProperties properties;
    private final ProductRepository productRepository;
    private final GarmentRoleResolver garmentRoleResolver;


    @Override
    public void run(String... args) {
        if (!properties.enabled()) {
            log.debug("catalog.role-backfill.enabled is false; skipping catalog role backfilling");
            return;
        }
        log.info("Starting garment role backfill:");
        Set<UUID> unresolvedIds = new HashSet<>();
        int backfilledCount = 0;

        List<Product> page = nextPage(unresolvedIds);
        while(!page.isEmpty()) {
            backfilledCount += backfillPage(page, unresolvedIds);
            page = nextPage(unresolvedIds);
        }
        log.info("Garment role backfill complete: {} backfilled, {} unresolved", backfilledCount, unresolvedIds.size());
    }

    private List<Product> nextPage(Set<UUID> unresolvedIds) {
        return unresolvedIds.isEmpty()
                ? productRepository.findAllByGarmentRoleIsNull(Limit.of(PAGE_SIZE))
                : productRepository.findAllByGarmentRoleIsNullAndIdNotIn(unresolvedIds, Limit.of(PAGE_SIZE));
    }

    private int backfillPage(List<Product> page, Set<UUID> unresolvedIds) {
        Map<GarmentRole, List<UUID>> idsByRole = new EnumMap<>(GarmentRole.class);

        for (Product product : page) {
            Optional<GarmentRole> role = garmentRoleResolver.resolve(product.getArticleType());
            if (role.isPresent()) {
                idsByRole.computeIfAbsent(role.get(), r -> new ArrayList<>()).add(product.getId());
            } else {
                log.warn("No GarmentRole mapping for articleType: '{}' (product {})", product.getArticleType(), product.getId());
                unresolvedIds.add(product.getId());
            }
        }

        int backfilledCount = 0;
        for (Map.Entry<GarmentRole, List<UUID>> entry : idsByRole.entrySet()) {
            backfilledCount += productRepository.updateGarmentRoleByIdIn(entry.getKey(), entry.getValue());
        }
        return backfilledCount;
    }
}