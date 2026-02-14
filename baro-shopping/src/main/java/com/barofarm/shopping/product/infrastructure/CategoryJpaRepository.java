package com.barofarm.shopping.product.infrastructure;

import com.barofarm.shopping.product.domain.Category;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryJpaRepository extends JpaRepository<Category, UUID> {
  List<Category> findByParentIsNullOrderBySortOrderAscNameAsc();

  List<Category> findByParentIdOrderBySortOrderAscNameAsc(UUID parentId);
}
