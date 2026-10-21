package com.bookstore.ecommerce.repository;

import com.bookstore.ecommerce.model.Order;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends MongoRepository<Order, Long> {

    @Query(value = "{ 'buyer': ?0 }", sort = "{ 'orderDate': -1 }")
    List<Order> findByBuyerId(Long buyerId);
}
