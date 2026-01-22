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
    List<String> imageUrls,
    List<String> positiveReviewSummary,
    List<String> negativeReviewSummary) {

  public static ProductDetailInfo from(Product product, int stock) {
      return from(product, stock, List.of(), List.of());
  }

  public static ProductDetailInfo from(Product product, int stock,
                                       List<String> positiveReviewSummary,
                                       List<String> negativeReviewSummary) {
      Category category = product.getCategory();
      UUID categoryId = null;
      String categoryCode = null;
      String categoryName = null;
      if (category != null) {
          try {
              categoryId = category.getId();
              categoryCode = category.getCode();
              categoryName = category.getName();
          } catch (jakarta.persistence.EntityNotFoundException ignored) {
              // 카테고리 누락 시 null 처리
          }
      }
      return new ProductDetailInfo(
          product.getId(),
          product.getSellerId(),
          product.getProductName(),
          product.getDescription(),
          categoryId,
          categoryCode,
          categoryName,
          product.getPrice(),
          stock,
          product.getProductStatus(),
          product.getCreatedAt(),
          product.getUpdatedAt(),
          product.getImages().stream()
              .map(ProductImage::getImageUrl)
              .toList(),
          positiveReviewSummary,
          negativeReviewSummary
      );
  }
}
