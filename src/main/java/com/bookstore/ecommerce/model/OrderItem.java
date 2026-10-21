package com.bookstore.ecommerce.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

@Document(collection = "order_items")
public class OrderItem implements SequencedDocument {

    @Id
    private Long id;

    // Lazy: the order loads its items, so an eager reference back would recurse
    @DocumentReference(lazy = true)
    private Order order;

    @DocumentReference
    private Book book;
    private int quantity;
    private double price;

    @DocumentReference
    private User vendor;
    private String status = "PENDING"; // "PENDING", "SHIPPED", "DELIVERED"

    public OrderItem() {
    }

    public OrderItem(Order order, Book book, int quantity, double price, User vendor) {
        this.order = order;
        this.book = book;
        this.quantity = quantity;
        this.price = price;
        this.vendor = vendor;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public Book getBook() {
        return book;
    }

    public void setBook(Book book) {
        this.book = book;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public User getVendor() {
        return vendor;
    }

    public void setVendor(User vendor) {
        this.vendor = vendor;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /** Items can be cancelled until the seller ships them. */
    @Transient
    public boolean isCancellable() {
        return "PENDING".equals(status);
    }
}
