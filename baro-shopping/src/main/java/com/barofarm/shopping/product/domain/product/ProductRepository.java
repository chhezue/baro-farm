package com.barofarm.shopping.product.domain.product;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductRepository {
  Page<Product> findAll(Pageable pageable);

  Optional<Product> findById(UUID id);

  Product save(Product product);

  void deleteById(UUID id);
}
