package com.bookstore.ecommerce.repository;

import com.bookstore.ecommerce.model.OrderItem;
import com.bookstore.ecommerce.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByVendor(User vendor);
    List<OrderItem> findByVendorOrderByOrderOrderDateDesc(User vendor);
}
