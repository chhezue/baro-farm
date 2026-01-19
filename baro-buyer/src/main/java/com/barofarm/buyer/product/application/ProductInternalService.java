package com.barofarm.buyer.product.application;

import com.barofarm.buyer.product.application.dto.internal.ReviewProductInfo;
import com.barofarm.buyer.product.domain.Product;
import com.barofarm.buyer.product.domain.ProductRepository;
import com.barofarm.buyer.product.domain.SeasonalityType;
import com.barofarm.buyer.product.exception.ProductErrorCode;
import com.barofarm.exception.CustomException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductInternalService {

    public final ProductRepository productRepository;

    public ReviewProductInfo getInternalProductDetail(UUID id) {
        Product product =
            productRepository
                .findById(id)
                .orElseThrow(() -> new CustomException(ProductErrorCode.PRODUCT_NOT_FOUND));

        return ReviewProductInfo.from(product);
    }

    public void updateSeasonality(UUID productId, SeasonalityType seasonalityType, String seasonalityValue) {
        Product product =
            productRepository
                .findById(productId)
                .orElseThrow(() -> new CustomException(ProductErrorCode.PRODUCT_NOT_FOUND));

        product.updateSeasonality(seasonalityType, seasonalityValue);
        productRepository.save(product);
    }
}
