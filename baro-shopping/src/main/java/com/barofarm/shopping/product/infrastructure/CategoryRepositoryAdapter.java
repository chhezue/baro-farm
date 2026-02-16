package com.barofarm.shopping.product.infrastructure;

import com.barofarm.shopping.product.domain.Category;
import com.barofarm.shopping.product.domain.CategoryRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CategoryRepositoryAdapter implements CategoryRepository {

  private final CategoryJpaRepository categoryJpaRepository;

  @Override
  public Optional<Category> findById(UUID id) {
    return categoryJpaRepository.findById(id);
  }

  @Override
  public List<Category> findRoots() {
    return categoryJpaRepository.findByParentIsNullOrderBySortOrderAscNameAsc();
  }

  @Override
  public List<Category> findChildren(UUID parentId) {
    return categoryJpaRepository.findByParentIdOrderBySortOrderAscNameAsc(parentId);
  }

  @Override
  public Category save(Category category) {
    return categoryJpaRepository.save(category);
  }
}
