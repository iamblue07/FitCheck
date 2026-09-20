package com.fitcheck.outfit.support;

import com.fitcheck.catalog.entity.Product;
import com.fitcheck.outfit.entity.Outfit;
import com.fitcheck.outfit.entity.OutfitItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class OutfitItemAssembler {

    public OutfitItem toOutfitItem(Outfit outfit, Product product) {
        return OutfitItem.builder()
                .outfit(outfit)
                .product(product)
                .slot(product.getGarmentRole())
                .build();
    }

    public List<OutfitItem> toOutfitItems(Outfit outfit, List<Product> products) {
        List<OutfitItem> items = new ArrayList<>();
        for (Product product : products) {
            items.add(toOutfitItem(outfit, product));
        }
        return items;
    }
}