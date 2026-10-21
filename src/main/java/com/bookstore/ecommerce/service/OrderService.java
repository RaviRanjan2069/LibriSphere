package com.bookstore.ecommerce.service;

import com.bookstore.ecommerce.model.Book;
import com.bookstore.ecommerce.model.Order;
import com.bookstore.ecommerce.model.OrderItem;
import com.bookstore.ecommerce.model.User;
import com.bookstore.ecommerce.repository.BookRepository;
import com.bookstore.ecommerce.repository.OrderItemRepository;
import com.bookstore.ecommerce.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/*
 * The local MongoDB runs as a standalone server, which has no multi-document transactions.
 * Stock changes therefore use single atomic updates ($inc guarded by a stock check), and
 * status changes update only the status field instead of re-saving whole documents.
 */
@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private MongoTemplate mongo;

    public Order placeOrder(User buyer, String buyerName, String address, String contact, Map<Book, Integer> cartItems) {
        if (cartItems.isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }

        // Reserve stock book by book; if any book runs short, give back what was already reserved
        Map<Book, Integer> reserved = new LinkedHashMap<>();
        double total = 0;
        try {
            for (Map.Entry<Book, Integer> entry : cartItems.entrySet()) {
                Book book = bookRepository.findById(entry.getKey().getId())
                        .orElseThrow(() -> new IllegalArgumentException("Book not found: " + entry.getKey().getTitle()));
                int qty = entry.getValue();

                if (!reserveStock(book.getId(), qty)) {
                    throw new IllegalArgumentException("Insufficient stock for: " + book.getTitle());
                }
                reserved.put(book, qty);
                total += book.getPrice() * qty;
            }
        } catch (RuntimeException e) {
            reserved.forEach((book, qty) -> changeStock(book.getId(), qty));
            throw e;
        }

        Order savedOrder = orderRepository.save(
                new Order(buyer, buyerName, address, contact, LocalDateTime.now(), "PENDING", total));

        for (Map.Entry<Book, Integer> entry : reserved.entrySet()) {
            Book book = entry.getKey();
            OrderItem orderItem = new OrderItem(savedOrder, book, entry.getValue(), book.getPrice(), book.getVendor());
            orderItemRepository.save(orderItem);
            savedOrder.getItems().add(orderItem);
        }

        return savedOrder;
    }

    /** Atomically takes qty copies if at least that many are in stock. */
    private boolean reserveStock(Long bookId, int qty) {
        return mongo.updateFirst(
                Query.query(where("_id").is(bookId).and("stock").gte(qty)),
                new Update().inc("stock", -qty),
                Book.class).getModifiedCount() == 1;
    }

    private void changeStock(Long bookId, int delta) {
        mongo.updateFirst(Query.query(where("_id").is(bookId)), new Update().inc("stock", delta), Book.class);
    }

    private void setStatus(Class<?> type, Long id, String status) {
        mongo.updateFirst(Query.query(where("_id").is(id)), Update.update("status", status), type);
    }

    public List<Order> getBuyerOrders(User buyer) {
        return orderRepository.findByBuyerId(buyer.getId());
    }

    public List<OrderItem> getVendorOrderItems(User vendor) {
        // Newest first; the order date lives on the referenced order document
        return orderItemRepository.findByVendorId(vendor.getId()).stream()
                .sorted(Comparator.comparing((OrderItem item) -> item.getOrder().getOrderDate()).reversed())
                .toList();
    }

    private static final Set<String> SELLER_STATUSES = Set.of("PENDING", "SHIPPED", "DELIVERED", "CANCELLED");

    public void updateOrderItemStatus(Long itemId, String newStatus, User vendor) {
        OrderItem item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Order item not found"));

        if (!item.getVendor().getId().equals(vendor.getId())) {
            throw new SecurityException("Unauthorized status update");
        }
        if (!SELLER_STATUSES.contains(newStatus)) {
            throw new IllegalArgumentException("Unknown status: " + newStatus);
        }
        if ("CANCELLED".equals(item.getStatus())) {
            throw new IllegalArgumentException("This item was cancelled and can no longer be updated");
        }
        if ("CANCELLED".equals(newStatus)) {
            if (!item.isCancellable()) {
                throw new IllegalArgumentException("Only items that have not shipped can be cancelled");
            }
            restock(item);
        }

        setStatus(OrderItem.class, item.getId(), newStatus);
        refreshOrderStatus(item.getOrder().getId());
    }

    /** Buyer cancels every item in the order that has not shipped yet. */
    public Order cancelOrder(Long orderId, User buyer) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        if (!order.getBuyer().getId().equals(buyer.getId())) {
            throw new SecurityException("You can only cancel your own orders");
        }
        if (!order.isCancellable()) {
            throw new IllegalArgumentException("This order can no longer be cancelled because part of it has already shipped");
        }

        for (OrderItem item : order.getItems()) {
            if (item.isCancellable()) {
                restock(item);
                setStatus(OrderItem.class, item.getId(), "CANCELLED");
            }
        }
        refreshOrderStatus(order.getId());
        return order;
    }

    /** Buyer cancels a single item that has not shipped yet. */
    public OrderItem cancelOrderItem(Long itemId, User buyer) {
        OrderItem item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Order item not found"));
        if (!item.getOrder().getBuyer().getId().equals(buyer.getId())) {
            throw new SecurityException("You can only cancel your own orders");
        }
        if (!item.isCancellable()) {
            throw new IllegalArgumentException("\"" + item.getBook().getTitle() + "\" has already shipped and can't be cancelled");
        }

        restock(item);
        setStatus(OrderItem.class, item.getId(), "CANCELLED");
        refreshOrderStatus(item.getOrder().getId());
        return item;
    }

    private void restock(OrderItem item) {
        changeStock(item.getBook().getId(), item.getQuantity());
    }

    /** Derive the order's overall status from its items; cancelled items are ignored unless every item is cancelled. */
    private void refreshOrderStatus(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        List<String> active = order.getItems().stream()
                .map(OrderItem::getStatus)
                .filter(status -> !"CANCELLED".equals(status))
                .toList();

        String status;
        if (active.isEmpty()) {
            status = "CANCELLED";
        } else if (active.stream().allMatch("DELIVERED"::equals)) {
            status = "DELIVERED";
        } else if (active.stream().allMatch(s -> "SHIPPED".equals(s) || "DELIVERED".equals(s))) {
            status = "SHIPPED";
        } else {
            status = "PENDING";
        }
        setStatus(Order.class, orderId, status);
    }

    // Vendor Dashboard Stats
    public double getVendorTotalSales(User vendor) {
        return orderItemRepository.findByVendorId(vendor.getId()).stream()
                .filter(item -> !"CANCELLED".equals(item.getStatus()))
                .mapToDouble(item -> item.getPrice() * item.getQuantity())
                .sum();
    }

    public long getVendorTotalBooksSold(User vendor) {
        return orderItemRepository.findByVendorId(vendor.getId()).stream()
                .filter(item -> !"CANCELLED".equals(item.getStatus()))
                .mapToLong(OrderItem::getQuantity)
                .sum();
    }

    public long getVendorTotalOrdersCount(User vendor) {
        return orderItemRepository.findByVendorId(vendor.getId()).stream()
                .filter(item -> !"CANCELLED".equals(item.getStatus()))
                .map(item -> item.getOrder().getId())
                .distinct()
                .count();
    }

    // Admin Dashboard Stats
    public double getPlatformTotalSales() {
        return orderItemRepository.findAll().stream()
                .filter(item -> !"CANCELLED".equals(item.getStatus()))
                .mapToDouble(item -> item.getPrice() * item.getQuantity())
                .sum();
    }

    public List<Order> getRecentOrders(int limit) {
        return orderRepository.findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "orderDate"))).getContent();
    }

    public long getPlatformTotalOrders() {
        return orderRepository.count();
    }
}
