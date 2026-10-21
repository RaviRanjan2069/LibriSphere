package com.bookstore.ecommerce.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "orders")
public class Order implements SequencedDocument {

    @Id
    private Long id;

    @DocumentReference
    private User buyer;
    private String buyerName;
    private String shippingAddress;
    private String contactNumber;
    private LocalDateTime orderDate;
    private String status; // "PENDING", "SHIPPED", "DELIVERED", "CANCELLED"
    private double totalPrice;

    // Items live in their own collection and point back to this order; loaded by lookup, never written here
    @ReadOnlyProperty
    @DocumentReference(lookup = "{ 'order' : ?#{#self._id} }")
    private List<OrderItem> items = new ArrayList<>();

    public Order() {
    }

    public Order(User buyer, String buyerName, String shippingAddress, String contactNumber, LocalDateTime orderDate, String status, double totalPrice) {
        this.buyer = buyer;
        this.buyerName = buyerName;
        this.shippingAddress = shippingAddress;
        this.contactNumber = contactNumber;
        this.orderDate = orderDate;
        this.status = status;
        this.totalPrice = totalPrice;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getBuyer() {
        return buyer;
    }

    public void setBuyer(User buyer) {
        this.buyer = buyer;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public void setBuyerName(String buyerName) {
        this.buyerName = buyerName;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public LocalDateTime getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDateTime orderDate) {
        this.orderDate = orderDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    /** The buyer can cancel while nothing has shipped yet and at least one item is still pending. */
    @Transient
    public boolean isCancellable() {
        boolean anyPending = items.stream().anyMatch(OrderItem::isCancellable);
        boolean allPendingOrCancelled = items.stream()
                .allMatch(i -> "PENDING".equals(i.getStatus()) || "CANCELLED".equals(i.getStatus()));
        return anyPending && allPendingOrCancelled;
    }

    /** Value of cancelled items, refunded to the buyer. */
    @Transient
    public double getRefundAmount() {
        return items.stream()
                .filter(i -> "CANCELLED".equals(i.getStatus()))
                .mapToDouble(i -> i.getPrice() * i.getQuantity())
                .sum();
    }

    @Transient
    public long getActiveItemCount() {
        return items.stream().filter(i -> !"CANCELLED".equals(i.getStatus())).count();
    }
}
