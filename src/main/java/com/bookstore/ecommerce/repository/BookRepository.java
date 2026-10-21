package com.bookstore.ecommerce.repository;

import com.bookstore.ecommerce.model.Book;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookRepository extends MongoRepository<Book, Long> {

    // References are stored as the referenced document's id
    @Query("{ 'vendor': ?0 }")
    List<Book> findByVendorId(Long vendorId);

    List<Book> findByCategory(String category);

    // pattern must already be regex-escaped; matched case-insensitively against title, author and category
    @Query("{ '$or': [ { 'title': { '$regex': ?0, '$options': 'i' } }, { 'author': { '$regex': ?0, '$options': 'i' } }, { 'category': { '$regex': ?0, '$options': 'i' } } ] }")
    List<Book> searchBooks(String pattern);

    @Query("{ 'category': ?0, '$or': [ { 'title': { '$regex': ?1, '$options': 'i' } }, { 'author': { '$regex': ?1, '$options': 'i' } } ] }")
    List<Book> searchBooksByCategory(String category, String pattern);
}
