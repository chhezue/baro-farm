package com.barofarm.shopping.product.application;

import com.barofarm.shopping.product.application.dto.internal.ReviewProductInfo;
import com.barofarm.shopping.product.domain.product.Product;
import com.barofarm.shopping.product.domain.product.ProductRepository;
import com.barofarm.shopping.product.exception.ProductErrorCode;
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
}
