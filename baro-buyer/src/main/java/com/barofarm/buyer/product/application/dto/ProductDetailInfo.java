package com.barofarm.buyer.product.application.dto;

import com.barofarm.buyer.product.domain.Category;
import com.barofarm.buyer.product.domain.Product;
import com.barofarm.buyer.product.domain.ProductImage;
import com.barofarm.buyer.product.domain.ProductStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ProductDetailInfo(
    UUID id,
    UUID sellerId,
    String productName,
    String description,
    UUID categoryId,
    String categoryCode,
    String categoryName,
    Long price,
    Integer stockQuantity,
    ProductStatus productStatus,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<String> imageUrls) {

  public static ProductDetailInfo from(Product product, int stock) {
      Category category = product.getCategory();
      return new ProductDetailInfo(
          product.getId(),
          product.getSellerId(),
          product.getProductName(),
          product.getDescription(),
          category.getId(),
          category.getCode(),
          category.getName(),
          product.getPrice(),
          stock,
          product.getProductStatus(),
          product.getCreatedAt(),
          product.getUpdatedAt(),
          product.getImages().stream()
              .map(ProductImage::getImageUrl)
              .toList()
      );
  }
}
