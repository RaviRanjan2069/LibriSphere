package com.bookstore.ecommerce.repository;

import com.bookstore.ecommerce.model.Order;
import com.bookstore.ecommerce.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByBuyerOrderByOrderDateDesc(User buyer);
}
