package com.barofarm.shopping.product.application.dto;

import com.barofarm.shopping.product.domain.Category;
import java.util.UUID;

public record CategoryListItem(
    UUID id,
    String name,
    String code,
    UUID parentId,
    Integer level,
    Integer sortOrder) {

  public static CategoryListItem from(Category category) {
    UUID parentId = category.getParent() == null ? null : category.getParent().getId();
    return new CategoryListItem(
        category.getId(),
        category.getName(),
        category.getCode(),
        parentId,
        category.getLevel(),
        category.getSortOrder());
  }
}
