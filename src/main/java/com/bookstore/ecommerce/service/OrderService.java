package com.bookstore.ecommerce.service;

import com.bookstore.ecommerce.model.Book;
import com.bookstore.ecommerce.model.Order;
import com.bookstore.ecommerce.model.OrderItem;
import com.bookstore.ecommerce.model.User;
import com.bookstore.ecommerce.repository.BookRepository;
import com.bookstore.ecommerce.repository.OrderItemRepository;
import com.bookstore.ecommerce.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private BookRepository bookRepository;

    @Transactional
    public Order placeOrder(User buyer, String buyerName, String address, String contact, Map<Book, Integer> cartItems) {
        if (cartItems.isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }

        double total = 0;
        for (Map.Entry<Book, Integer> entry : cartItems.entrySet()) {
            Book book = bookRepository.findById(entry.getKey().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Book not found: " + entry.getKey().getTitle()));
            
            if (book.getStock() < entry.getValue()) {
                throw new IllegalArgumentException("Insufficient stock for: " + book.getTitle());
            }
            
            // Deduct stock
            book.setStock(book.getStock() - entry.getValue());
            bookRepository.save(book);

            total += book.getPrice() * entry.getValue();
        }

        // Create main order
        Order order = new Order(buyer, buyerName, address, contact, LocalDateTime.now(), "PENDING", total);
        Order savedOrder = orderRepository.save(order);

        // Create order items
        for (Map.Entry<Book, Integer> entry : cartItems.entrySet()) {
            Book book = entry.getKey();
            int qty = entry.getValue();
            
            OrderItem orderItem = new OrderItem(savedOrder, book, qty, book.getPrice(), book.getVendor());
            orderItemRepository.save(orderItem);
            savedOrder.getItems().add(orderItem);
        }

        return savedOrder;
    }

    public List<Order> getBuyerOrders(User buyer) {
        return orderRepository.findByBuyerOrderByOrderDateDesc(buyer);
    }

    public List<OrderItem> getVendorOrderItems(User vendor) {
        return orderItemRepository.findByVendorOrderByOrderOrderDateDesc(vendor);
    }

    @Transactional
    public void updateOrderItemStatus(Long itemId, String newStatus, User vendor) {
        OrderItem item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Order item not found"));
        
        if (!item.getVendor().getId().equals(vendor.getId())) {
            throw new SecurityException("Unauthorized status update");
        }
        
        item.setStatus(newStatus);
        orderItemRepository.save(item);

        // Check if all items in the parent order are SHIPPED/DELIVERED
        Order order = item.getOrder();
        boolean allShippedOrDelivered = order.getItems().stream()
                .allMatch(i -> "SHIPPED".equals(i.getStatus()) || "DELIVERED".equals(i.getStatus()));
        
        boolean allDelivered = order.getItems().stream()
                .allMatch(i -> "DELIVERED".equals(i.getStatus()));

        if (allDelivered) {
            order.setStatus("DELIVERED");
        } else if (allShippedOrDelivered) {
            order.setStatus("SHIPPED");
        }
        orderRepository.save(order);
    }

    // Vendor Dashboard Stats
    public double getVendorTotalSales(User vendor) {
        return orderItemRepository.findByVendor(vendor).stream()
                .filter(item -> !"CANCELLED".equals(item.getStatus()))
                .mapToDouble(item -> item.getPrice() * item.getQuantity())
                .sum();
    }

    public long getVendorTotalBooksSold(User vendor) {
        return orderItemRepository.findByVendor(vendor).stream()
                .filter(item -> !"CANCELLED".equals(item.getStatus()))
                .mapToLong(OrderItem::getQuantity)
                .sum();
    }

    public long getVendorTotalOrdersCount(User vendor) {
        return orderItemRepository.findByVendor(vendor).stream()
                .map(item -> item.getOrder().getId())
                .distinct()
                .count();
    }

    // Admin Dashboard Stats
    public double getPlatformTotalSales() {
        return orderRepository.findAll().stream()
                .mapToDouble(Order::getTotalPrice)
                .sum();
    }

    public long getPlatformTotalOrders() {
        return orderRepository.count();
    }
}
