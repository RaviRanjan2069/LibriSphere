package com.bookstore.ecommerce.repository;

import com.bookstore.ecommerce.model.Book;
import com.bookstore.ecommerce.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {
    List<Book> findByVendor(User vendor);
    List<Book> findByCategory(String category);
    
    @Query("SELECT b FROM Book b WHERE LOWER(b.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(b.author) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Book> searchBooks(@Param("query") String query);
    
    @Query("SELECT b FROM Book b WHERE b.category = :category AND (LOWER(b.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(b.author) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Book> searchBooksByCategory(@Param("category") String category, @Param("query") String query);
}
