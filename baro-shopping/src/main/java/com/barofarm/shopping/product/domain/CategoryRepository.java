package com.barofarm.shopping.product.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository {
  Optional<Category> findById(UUID id);

  List<Category> findRoots();

  List<Category> findChildren(UUID parentId);

  Category save(Category category);
}
