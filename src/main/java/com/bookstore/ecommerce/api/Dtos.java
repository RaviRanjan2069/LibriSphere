package com.bookstore.ecommerce.api;

import com.bookstore.ecommerce.model.Book;
import com.bookstore.ecommerce.model.Order;
import com.bookstore.ecommerce.model.OrderItem;
import com.bookstore.ecommerce.model.SupportRequest;
import com.bookstore.ecommerce.model.User;
import com.bookstore.ecommerce.service.AvatarService;

import java.time.LocalDateTime;
import java.util.List;

/** JSON shapes returned by the API. Entities are never serialized directly (they hold passwords and cyclic references). */
public final class Dtos {

    private Dtos() {
    }

    /** Only ever returned to the user themselves or to an administrator. */
    public record UserDto(Long id, String username, String email, String role, String vendorName, boolean vendorApproved,
                          boolean emailVerified, String avatarUrl, String fullName, String gender) {
        public static UserDto of(User u) {
            return u == null ? null : new UserDto(u.getId(), u.getUsername(), u.getEmail(), u.getRole(), u.getVendorName(),
                    u.isVendorApproved(), u.isEmailVerified(), avatarOf(u), u.getFullName(), u.getGender());
        }
    }

    static String avatarOf(User u) {
        return u == null ? null : AvatarService.urlFor(u.getAvatarKey());
    }

    public record BookDto(Long id, String title, String author, String description, double price, int stock,
                          String category, String coverUrl, Long vendorId, String vendorName, String vendorEmail) {
        public static BookDto of(Book b) {
            User v = b.getVendor();
            return new BookDto(b.getId(), b.getTitle(), b.getAuthor(), b.getDescription(), b.getPrice(), b.getStock(),
                    b.getCategory(), b.getCoverUrl(),
                    v == null ? null : v.getId(), v == null ? null : v.getVendorName(), v == null ? null : v.getEmail());
        }
    }

    public record CartLineDto(BookDto book, int quantity, double total) {
    }

    public record CartDto(List<CartLineDto> items, int count, double subtotal) {
    }

    public record OrderItemDto(Long id, Long bookId, String title, String author, String coverUrl, int quantity,
                               double price, String status, String vendorName, boolean cancellable) {
        public static OrderItemDto of(OrderItem i) {
            Book b = i.getBook();
            return new OrderItemDto(i.getId(), b.getId(), b.getTitle(), b.getAuthor(), b.getCoverUrl(), i.getQuantity(),
                    i.getPrice(), i.getStatus(), i.getVendor().getVendorName(), i.isCancellable());
        }
    }

    public record OrderDto(Long id, LocalDateTime orderDate, String status, double totalPrice, double refundAmount,
                           boolean cancellable, long activeItemCount, String buyerName, String buyerEmail, String buyerAvatarUrl,
                           String shippingAddress, String contactNumber, List<OrderItemDto> items) {
        public static OrderDto of(Order o) {
            return new OrderDto(o.getId(), o.getOrderDate(), o.getStatus(), o.getTotalPrice(), o.getRefundAmount(),
                    o.isCancellable(), o.getActiveItemCount(), o.getBuyerName(),
                    o.getBuyer() == null ? null : o.getBuyer().getEmail(), avatarOf(o.getBuyer()),
                    o.getShippingAddress(), o.getContactNumber(),
                    o.getItems().stream().map(OrderItemDto::of).toList());
        }
    }

    /** A Help & support request; the requester's email is included for administrators and for the requester themselves. */
    public record SupportRequestDto(Long id, String reference, String topic, String priority, String area, String subject,
                                    String description, String status, LocalDateTime createdAt, LocalDateTime resolvedAt,
                                    String resolution, String username, String email, String role) {
        public static SupportRequestDto of(SupportRequest r) {
            return new SupportRequestDto(r.getId(), r.getReference(), r.getTopic(), r.getPriority(), r.getArea(), r.getSubject(),
                    r.getDescription(), r.getStatus(), r.getCreatedAt(), r.getResolvedAt(), r.getResolution(),
                    r.getUsername(), r.getEmail(), r.getRole());
        }
    }

    /** An order line as a seller sees it, with the order details it belongs to. */
    public record SellerItemDto(Long id, Long orderId, LocalDateTime orderDate, String buyerName, String buyerAvatarUrl, Long bookId,
                                String title, String author, String coverUrl, int quantity, double price,
                                String status, boolean cancellable) {
        public static SellerItemDto of(OrderItem i) {
            Order o = i.getOrder();
            Book b = i.getBook();
            return new SellerItemDto(i.getId(), o.getId(), o.getOrderDate(), o.getBuyerName(), avatarOf(o.getBuyer()), b.getId(), b.getTitle(),
                    b.getAuthor(), b.getCoverUrl(), i.getQuantity(), i.getPrice(), i.getStatus(), i.isCancellable());
        }
    }
}
