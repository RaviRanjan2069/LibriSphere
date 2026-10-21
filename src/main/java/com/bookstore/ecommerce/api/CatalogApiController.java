package com.bookstore.ecommerce.api;

import com.bookstore.ecommerce.api.Dtos.BookDto;
import com.bookstore.ecommerce.api.Dtos.CartDto;
import com.bookstore.ecommerce.api.Dtos.CartLineDto;
import com.bookstore.ecommerce.model.Book;
import com.bookstore.ecommerce.service.BookService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CatalogApiController {

    public static final List<String> CATEGORIES = List.of("Fiction", "Technology", "Science", "Business", "Biography");

    private final BookService bookService;

    public CatalogApiController(BookService bookService) {
        this.bookService = bookService;
    }

    // ---------- Books ----------

    @GetMapping("/books")
    public Map<String, Object> books(@RequestParam(required = false) String category,
                                     @RequestParam(required = false) String search,
                                     @RequestParam(defaultValue = "featured") String sort) {
        List<Book> books = bookService.searchBooksByCategory(category, search);

        Comparator<Book> order = switch (sort) {
            case "price-asc" -> Comparator.comparingDouble(Book::getPrice);
            case "price-desc" -> Comparator.comparingDouble(Book::getPrice).reversed();
            case "title" -> Comparator.comparing(Book::getTitle, String.CASE_INSENSITIVE_ORDER);
            case "newest" -> Comparator.comparing(Book::getId).reversed();
            default -> null;
        };
        if (order != null) {
            books = books.stream().sorted(order).toList();
        }

        return Map.of(
                "books", books.stream().map(BookDto::of).toList(),
                "categories", CATEGORIES);
    }

    @GetMapping("/books/{id}")
    public BookDto book(@PathVariable Long id) {
        return bookService.getBookById(id)
                .map(BookDto::of)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That book could not be found."));
    }

    // ---------- Cart (kept in the session) ----------

    public record CartAddRequest(Long bookId, Integer quantity) {
    }

    public record CartUpdateRequest(Integer quantity) {
    }

    @GetMapping("/cart")
    public CartDto cart(HttpSession session) {
        Map<Long, Integer> cart = SessionCart.get(session);
        List<CartLineDto> lines = new ArrayList<>();
        double subtotal = 0;
        for (var it = cart.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            var book = bookService.getBookById(entry.getKey());
            if (book.isEmpty()) {
                it.remove();  // book was deleted by its seller
                continue;
            }
            double total = book.get().getPrice() * entry.getValue();
            lines.add(new CartLineDto(BookDto.of(book.get()), entry.getValue(), total));
            subtotal += total;
        }
        return new CartDto(lines, SessionCart.count(session), subtotal);
    }

    @PostMapping("/cart/items")
    public Map<String, Object> addToCart(@RequestBody CartAddRequest req, HttpSession session) {
        rejectNonBuyers(session);
        int quantity = req.quantity() == null ? 1 : req.quantity();
        if (req.bookId() == null || quantity < 1) {
            throw new IllegalArgumentException("Choose a book and a quantity of at least 1.");
        }
        Book book = bookService.getBookById(req.bookId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That book could not be found."));

        Map<Long, Integer> cart = SessionCart.get(session);
        int target = cart.getOrDefault(book.getId(), 0) + quantity;
        if (target > book.getStock()) {
            throw new IllegalArgumentException(book.getStock() == 0
                    ? "\"" + book.getTitle() + "\" is out of stock."
                    : "Only " + book.getStock() + " in stock, and you already have " + cart.getOrDefault(book.getId(), 0) + " in your cart.");
        }
        cart.put(book.getId(), target);
        return Map.of("message", "\"" + book.getTitle() + "\" added to cart", "cartCount", SessionCart.count(session));
    }

    @PutMapping("/cart/items/{bookId}")
    public CartDto updateCart(@PathVariable Long bookId, @RequestBody CartUpdateRequest req, HttpSession session) {
        Map<Long, Integer> cart = SessionCart.get(session);
        if (cart.containsKey(bookId)) {
            int quantity = req.quantity() == null ? 1 : req.quantity();
            if (quantity <= 0) {
                cart.remove(bookId);
            } else {
                int stock = bookService.getBookById(bookId).map(Book::getStock).orElse(0);
                cart.put(bookId, Math.max(1, Math.min(quantity, stock)));
            }
        }
        return cart(session);
    }

    @DeleteMapping("/cart/items/{bookId}")
    public CartDto removeFromCart(@PathVariable Long bookId, HttpSession session) {
        SessionCart.get(session).remove(bookId);
        return cart(session);
    }

    private static void rejectNonBuyers(HttpSession session) {
        var user = SessionAuth.current(session);
        if (user != null && !"BUYER".equals(user.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Sign in with a buyer account to shop.");
        }
    }
}
