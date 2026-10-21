package com.bookstore.ecommerce.api;

import com.bookstore.ecommerce.api.Dtos.BookDto;
import com.bookstore.ecommerce.api.Dtos.SellerItemDto;
import com.bookstore.ecommerce.model.Book;
import com.bookstore.ecommerce.model.User;
import com.bookstore.ecommerce.service.BookService;
import com.bookstore.ecommerce.service.OrderService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Seller dashboard: listings, orders to fulfil and sales figures. */
@RestController
@RequestMapping("/api/vendor")
public class VendorApiController {

    private final BookService bookService;
    private final OrderService orderService;

    public VendorApiController(BookService bookService, OrderService orderService) {
        this.bookService = bookService;
        this.orderService = orderService;
    }

    public record BookRequest(String title, String author, String category, Double price, Integer stock,
                              String coverUrl, String description) {
    }

    public record StatusRequest(String status) {
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(HttpSession session) {
        User vendor = SessionAuth.require(session, "VENDOR");
        List<Book> books = bookService.getBooksByVendor(vendor);

        Map<String, Object> body = new HashMap<>();
        body.put("vendorName", vendor.getVendorName());
        body.put("totalSales", orderService.getVendorTotalSales(vendor));
        body.put("booksSold", orderService.getVendorTotalBooksSold(vendor));
        body.put("totalOrders", orderService.getVendorTotalOrdersCount(vendor));
        body.put("lowStockCount", books.stream().filter(b -> b.getStock() <= 5).count());
        body.put("books", books.stream().map(BookDto::of).toList());
        body.put("orderItems", orderService.getVendorOrderItems(vendor).stream().map(SellerItemDto::of).toList());
        return body;
    }

    @GetMapping("/books/{id}")
    public BookDto book(@PathVariable Long id, HttpSession session) {
        User vendor = SessionAuth.require(session, "VENDOR");
        return BookDto.of(ownBook(id, vendor));
    }

    @PostMapping("/books")
    @ResponseStatus(HttpStatus.CREATED)
    public BookDto create(@RequestBody BookRequest req, HttpSession session) {
        User vendor = SessionAuth.require(session, "VENDOR");
        Book book = new Book();
        apply(req, book);
        book.setVendor(vendor);
        return BookDto.of(bookService.saveBook(book));
    }

    @PutMapping("/books/{id}")
    public BookDto update(@PathVariable Long id, @RequestBody BookRequest req, HttpSession session) {
        User vendor = SessionAuth.require(session, "VENDOR");
        Book book = ownBook(id, vendor);
        apply(req, book);
        return BookDto.of(bookService.saveBook(book));
    }

    @DeleteMapping("/books/{id}")
    public Map<String, Object> delete(@PathVariable Long id, HttpSession session) {
        User vendor = SessionAuth.require(session, "VENDOR");
        bookService.deleteBook(id, vendor);
        return Map.of("message", "The listing was removed.");
    }

    @PutMapping("/order-items/{id}/status")
    public Map<String, Object> updateStatus(@PathVariable Long id, @RequestBody StatusRequest req, HttpSession session) {
        User vendor = SessionAuth.require(session, "VENDOR");
        String status = req.status() == null ? "" : req.status().toUpperCase();
        orderService.updateOrderItemStatus(id, status, vendor);
        return Map.of("message", "Item marked as " + status.toLowerCase() + ".");
    }

    private Book ownBook(Long id, User vendor) {
        return bookService.getBookById(id)
                .filter(b -> b.getVendor().getId().equals(vendor.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That listing doesn't exist or belongs to another seller."));
    }

    private static void apply(BookRequest req, Book book) {
        String title = trim(req.title());
        String author = trim(req.author());
        if (title.isEmpty() || author.isEmpty()) {
            throw new IllegalArgumentException("Title and author are required.");
        }
        if (req.price() == null || req.stock() == null) {
            throw new IllegalArgumentException("Price and stock are required.");
        }
        String category = trim(req.category());
        book.setTitle(title);
        book.setAuthor(author);
        book.setCategory(CatalogApiController.CATEGORIES.contains(category) ? category : "Fiction");
        book.setPrice(req.price());
        book.setStock(req.stock());
        book.setCoverUrl(trim(req.coverUrl()));
        book.setDescription(trim(req.description()));
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
