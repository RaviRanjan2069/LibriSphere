package com.bookstore.ecommerce.api;

import com.bookstore.ecommerce.api.Dtos.OrderDto;
import com.bookstore.ecommerce.model.Book;
import com.bookstore.ecommerce.model.Order;
import com.bookstore.ecommerce.model.OrderItem;
import com.bookstore.ecommerce.model.User;
import com.bookstore.ecommerce.repository.OrderRepository;
import com.bookstore.ecommerce.service.BookService;
import com.bookstore.ecommerce.service.OrderService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A buyer's orders: checkout, history and cancellation. */
@RestController
@RequestMapping("/api/orders")
public class OrderApiController {

    private final OrderService orderService;
    private final BookService bookService;
    private final OrderRepository orderRepository;

    public OrderApiController(OrderService orderService, BookService bookService, OrderRepository orderRepository) {
        this.orderService = orderService;
        this.bookService = bookService;
        this.orderRepository = orderRepository;
    }

    public record CheckoutRequest(String buyerName, String contactNumber, String shippingAddress) {
    }

    @GetMapping
    public List<OrderDto> myOrders(HttpSession session) {
        User buyer = SessionAuth.require(session, "BUYER");
        return orderService.getBuyerOrders(buyer).stream().map(OrderDto::of).toList();
    }

    @GetMapping("/{id}")
    public OrderDto order(@PathVariable Long id, HttpSession session) {
        User buyer = SessionAuth.require(session, "BUYER");
        Order order = orderRepository.findById(id)
                .filter(o -> o.getBuyer().getId().equals(buyer.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That order could not be found."));
        return OrderDto.of(order);
    }

    /** Checkout: turns the session cart into an order. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDto checkout(@RequestBody CheckoutRequest req, HttpSession session) {
        User buyer = SessionAuth.require(session, "BUYER");
        String name = trim(req.buyerName());
        String phone = trim(req.contactNumber());
        String address = trim(req.shippingAddress());
        if (name.isEmpty() || phone.isEmpty() || address.isEmpty()) {
            throw new IllegalArgumentException("Name, phone number and shipping address are all required.");
        }

        Map<Long, Integer> cart = SessionCart.get(session);
        if (cart.isEmpty()) {
            throw new IllegalArgumentException("Your cart is empty.");
        }
        Map<Book, Integer> lines = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> entry : cart.entrySet()) {
            Book book = bookService.getBookById(entry.getKey())
                    .orElseThrow(() -> new IllegalArgumentException("A book in your cart is no longer available. Please review your cart."));
            lines.put(book, entry.getValue());
        }

        Order order = orderService.placeOrder(buyer, name, address, phone, lines);
        SessionCart.clear(session);
        return OrderDto.of(order);
    }

    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancelOrder(@PathVariable Long id, HttpSession session) {
        User buyer = SessionAuth.require(session, "BUYER");
        orderService.cancelOrder(id, buyer);
        return Map.of("message", "Order #LBS-" + id + " was cancelled. Your refund is on its way.");
    }

    @PostMapping("/items/{itemId}/cancel")
    public Map<String, Object> cancelItem(@PathVariable Long itemId, HttpSession session) {
        User buyer = SessionAuth.require(session, "BUYER");
        OrderItem item = orderService.cancelOrderItem(itemId, buyer);
        return Map.of("message", "\"" + item.getBook().getTitle() + "\" was removed from your order.");
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
