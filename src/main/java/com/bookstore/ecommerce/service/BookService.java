package com.bookstore.ecommerce.service;

import com.bookstore.ecommerce.model.Book;
import com.bookstore.ecommerce.model.User;
import com.bookstore.ecommerce.repository.BookRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class BookService {

    @Autowired
    private BookRepository bookRepository;

    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }

    public Optional<Book> getBookById(Long id) {
        return bookRepository.findById(id);
    }

    public List<Book> getBooksByCategory(String category) {
        return bookRepository.findByCategory(category);
    }

    public List<Book> getBooksByVendor(User vendor) {
        return bookRepository.findByVendor(vendor);
    }

    public List<Book> searchBooks(String query) {
        if (query == null || query.trim().isEmpty()) {
            return bookRepository.findAll();
        }
        return bookRepository.searchBooks(query);
    }

    public List<Book> searchBooksByCategory(String category, String query) {
        if (category == null || category.trim().isEmpty() || "All".equalsIgnoreCase(category)) {
            return searchBooks(query);
        }
        if (query == null || query.trim().isEmpty()) {
            return getBooksByCategory(category);
        }
        return bookRepository.searchBooksByCategory(category, query);
    }

    @Transactional
    public Book saveBook(Book book) {
        if (book.getPrice() < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
        if (book.getStock() < 0) {
            throw new IllegalArgumentException("Stock cannot be negative");
        }
        return bookRepository.save(book);
    }

    @Transactional
    public void deleteBook(Long bookId, User vendor) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found"));
        
        // Ensure only the owner vendor can delete the book
        if (book.getVendor().getId().equals(vendor.getId())) {
            bookRepository.delete(book);
        } else {
            throw new SecurityException("Unauthorized delete action");
        }
    }

    public long getBookCount() {
        return bookRepository.count();
    }
}
