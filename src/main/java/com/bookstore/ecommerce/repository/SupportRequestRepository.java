package com.bookstore.ecommerce.repository;

import com.bookstore.ecommerce.model.SupportRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportRequestRepository extends MongoRepository<SupportRequest, Long> {
    @Query(value = "{ 'userId': ?0 }", sort = "{ 'createdAt': -1 }")
    List<SupportRequest> findByUserId(Long userId);

    @Query(value = "{}", sort = "{ 'createdAt': -1 }")
    List<SupportRequest> findAllNewestFirst();
}
