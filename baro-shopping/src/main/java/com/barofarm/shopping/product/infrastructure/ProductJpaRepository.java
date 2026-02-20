package com.barofarm.shopping.product.infrastructure;

import com.barofarm.shopping.product.domain.product.Product;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductJpaRepository extends JpaRepository<Product, UUID> {}
