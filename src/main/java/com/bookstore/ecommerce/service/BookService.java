package com.bookstore.ecommerce.service;

import com.bookstore.ecommerce.model.Book;
import com.bookstore.ecommerce.model.User;
import com.bookstore.ecommerce.repository.BookRepository;
import com.bookstore.ecommerce.repository.OrderItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class BookService {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

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
        return bookRepository.findByVendorId(vendor.getId());
    }

    public List<Book> searchBooks(String query) {
        if (query == null || query.trim().isEmpty()) {
            return bookRepository.findAll();
        }
        return bookRepository.searchBooks(escapeRegex(query.trim()));
    }

    public List<Book> searchBooksByCategory(String category, String query) {
        if (category == null || category.trim().isEmpty() || "All".equalsIgnoreCase(category)) {
            return searchBooks(query);
        }
        if (query == null || query.trim().isEmpty()) {
            return getBooksByCategory(category);
        }
        return bookRepository.searchBooksByCategory(category, escapeRegex(query.trim()));
    }

    public Book saveBook(Book book) {
        if (book.getPrice() < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
        if (book.getStock() < 0) {
            throw new IllegalArgumentException("Stock cannot be negative");
        }
        return bookRepository.save(book);
    }

    public void deleteBook(Long bookId, User vendor) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found"));
        
        // Ensure only the owner vendor can delete the book
        if (!book.getVendor().getId().equals(vendor.getId())) {
            throw new SecurityException("Unauthorized delete action");
        }
        // Past orders reference the book, so keep it and let the seller stop sales by setting stock to 0
        if (orderItemRepository.existsByBookId(book.getId())) {
            throw new IllegalArgumentException("\"" + book.getTitle() + "\" appears in customer orders, so it can't be deleted. Set its stock to 0 to stop selling it.");
        }
        bookRepository.delete(book);
    }

    /** Search text is matched literally, so characters like ( or + don't act as regex syntax. */
    private static String escapeRegex(String text) {
        return text.replaceAll("[^\\p{Alnum}\\s]", "\\\\$0");
    }

    public long getBookCount() {
        return bookRepository.count();
    }
}
