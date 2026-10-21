package com.bookstore.ecommerce.repository;

import com.bookstore.ecommerce.model.OrderItem;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends MongoRepository<OrderItem, Long> {

    @Query("{ 'vendor': ?0 }")
    List<OrderItem> findByVendorId(Long vendorId);

    @Query(value = "{ 'book': ?0 }", exists = true)
    boolean existsByBookId(Long bookId);
}
