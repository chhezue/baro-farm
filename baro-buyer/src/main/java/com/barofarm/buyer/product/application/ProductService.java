package com.barofarm.buyer.product.application;

import com.barofarm.buyer.common.exception.CustomException;
import com.barofarm.buyer.common.response.CustomPage;
import com.barofarm.buyer.inventory.application.InventoryService;
import com.barofarm.buyer.product.application.dto.ProductCreateCommand;
import com.barofarm.buyer.product.application.dto.ProductDetailInfo;
import com.barofarm.buyer.product.application.dto.ProductUpdateCommand;
import com.barofarm.buyer.product.application.event.ProductEventPublisher;
import com.barofarm.buyer.product.domain.Product;
import com.barofarm.buyer.product.domain.ProductRepository;
import com.barofarm.buyer.product.domain.ProductStatus;
import com.barofarm.buyer.product.domain.UserType;
import com.barofarm.buyer.product.exception.ProductErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductEventPublisher productEventPublisher;
    private final InventoryService inventoryService;

    @Transactional(readOnly = true)
    public ProductDetailInfo getProductDetail(UUID id) {
//        Product product =
//            productRepository
//                .findById(id)
//                .orElseThrow(() -> new CustomException(ProductErrorCode.PRODUCT_NOT_FOUND));
//
//        return ProductDetailInfo.from(product);
        return null;
    }

    @Transactional(readOnly = true)
    public CustomPage<ProductDetailInfo> getProducts(Pageable pageable) {
//        Page<ProductDetailInfo> products = productRepository.findAll(pageable)
//            .map(ProductDetailInfo::from);
//
//        return CustomPage.from(products);
        return null;
    }

    public ProductDetailInfo createProduct(ProductCreateCommand command) {
        // user의 역할이 isSeller가 아니라면 에러 호출
        validateSeller(command);

        Product product = Product.create(
            command.sellerId(),
            command.productName(),
            command.description(),
            command.productCategory(),
            command.price(),
            ProductStatus.ON_SALE);

        //To-do 이미지 저장

        // 상품 저장 및 재고 저장
        Product savedProduct = saveProductAndInventory(product, command.stockQuantity());

        return ProductDetailInfo.from(savedProduct, command.stockQuantity());
    }

    @Transactional
    protected Product saveProductAndInventory(Product product, Integer stockQuantity) {
        Product saved= productRepository.save(product);

        //재고 생성 로직
        //inventoryService.create(UUID productId, command.stockQuantity);

        // 카프카 이벤트 발행
        log.info("📤 [PRODUCT_SERVICE] Publishing PRODUCT_CREATED event to Kafka - Product ID: {}, Name: {}",
            product.getId(), product.getProductName());
        productEventPublisher.publishProductCreated(product);

        return saved;
    }

  public ProductDetailInfo updateProduct(UUID id, ProductUpdateCommand command) {
//    Product product =
//        productRepository
//            .findById(id)
//            .orElseThrow(() -> new CustomException(ProductErrorCode.PRODUCT_NOT_FOUND));
//
//    //    MemberRole memberRole = MemberRole.from(role);
//    //
//    //    if (memberRole != MemberRole.SELLER) {
//    //      throw new CustomException(ErrorCode.FORBIDDEN_ONLY_SELLER);
//    //    }
//
//    product.validateOwner(command.memberId());
//
//    product.update(
//        command.productName(),
//        command.description(),
//        command.productCategory(),
//        command.price(),
//        command.productStatus());
//
//    return ProductDetailInfo.from(product);
      return null;
  }

    public void deleteProduct(UUID id, UUID memberId, UserType role) {
//    Product product =
//        productRepository
//            .findById(id)
//            .orElseThrow(() -> new CustomException(ProductErrorCode.PRODUCT_NOT_FOUND));
//
//    //    MemberRole memberRole = MemberRole.from(role);
//    //
//    //    if (memberRole != MemberRole.SELLER) {
//    //      throw new CustomException(ErrorCode.FORBIDDEN_ONLY_SELLER);
//    //    }
//
//    product.validateOwner(memberId);
//
//    productRepository.deleteById(id);
//
//      // 카프카 이벤트 발행
//      productEventPublisher.publishProductDeleted(product);
  }


    private static void validateSeller(ProductCreateCommand command) {
        if(!command.role().isSeller()){
            throw new CustomException(ProductErrorCode.FORBIDDEN_ONLY_SELLER);
        }
    }
}
