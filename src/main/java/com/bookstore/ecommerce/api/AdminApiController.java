package com.bookstore.ecommerce.api;

import com.bookstore.ecommerce.api.Dtos.OrderDto;
import com.bookstore.ecommerce.api.Dtos.SupportRequestDto;
import com.bookstore.ecommerce.model.SupportRequest;
import com.bookstore.ecommerce.api.Dtos.UserDto;
import com.bookstore.ecommerce.model.User;
import com.bookstore.ecommerce.service.BookService;
import com.bookstore.ecommerce.service.EmailAddresses;
import com.bookstore.ecommerce.service.PasswordRules;
import com.bookstore.ecommerce.service.SupportService;
import com.bookstore.ecommerce.service.OrderService;
import com.bookstore.ecommerce.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminApiController {

    private final UserService userService;
    private final BookService bookService;
    private final OrderService orderService;
    private final SupportService supportService;

    public AdminApiController(UserService userService, BookService bookService, OrderService orderService, SupportService supportService) {
        this.userService = userService;
        this.bookService = bookService;
        this.orderService = orderService;
        this.supportService = supportService;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(HttpSession session) {
        SessionAuth.require(session, "ADMIN");
        List<User> vendors = userService.getAllVendors();

        Map<String, Object> body = new HashMap<>();
        body.put("totalSales", orderService.getPlatformTotalSales());
        body.put("totalOrders", orderService.getPlatformTotalOrders());
        body.put("vendorCount", userService.getVendorCount());
        body.put("buyerCount", userService.getBuyerCount());
        body.put("bookCount", bookService.getBookCount());
        body.put("pendingVendors", vendors.stream().filter(v -> !v.isVendorApproved()).map(UserDto::of).toList());
        body.put("approvedVendors", vendors.stream().filter(User::isVendorApproved).map(UserDto::of).toList());
        body.put("recentOrders", orderService.getRecentOrders(10).stream().map(OrderDto::of).toList());
        body.put("admins", userService.getAdmins().stream().map(UserDto::of).toList());
        body.put("supportRequests", supportService.all().stream().map(SupportRequestDto::of).toList());
        return body;
    }

    public record NewAdminRequest(String username, String email, String password, String confirmPassword) {
    }

    /**
     * Creates another administrator account. Only a signed-in administrator can do this; sign-up never
     * creates admins. The email starts unverified: the new admin confirms it from their Profile page.
     */
    @PostMapping("/admins")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createAdmin(@RequestBody NewAdminRequest req, HttpSession session) {
        SessionAuth.require(session, "ADMIN");
        String name = req.username() == null ? "" : req.username().trim();
        if (!name.matches("[A-Za-z0-9_.-]{3,30}")) {
            throw ApiException.badRequest("username", "Use 3–30 letters, numbers, dots, dashes or underscores (no spaces).");
        }
        String email = EmailAddresses.normalize(req.email());
        PasswordRules.check(req.password(), req.confirmPassword(), "password");
        if (userService.usernameTaken(name)) {
            throw new ApiException(HttpStatus.CONFLICT, "That user id is taken. Try another.", "username");
        }
        if (userService.emailTaken(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Another account already uses this email address.", "email");
        }
        User admin = userService.registerUser(new User(name, null, email, "ADMIN"), req.password());
        return Map.of("message", "Administrator " + admin.getUsername() + " created. They can sign in from the Admin tab.",
                "admin", UserDto.of(admin));
    }

    public record ResolveRequest(String reply) {
    }

    /** Answers a Help & support request and marks it resolved; the user is emailed the reply. */
    @PostMapping("/support/{id}/resolve")
    public Map<String, Object> resolveSupport(@PathVariable Long id, @RequestBody ResolveRequest req, HttpSession session) {
        User admin = SessionAuth.require(session, "ADMIN");
        String reply = req.reply() == null ? "" : req.reply().trim();
        if (reply.length() < 2) {
            throw ApiException.badRequest("reply", "Write a reply for the customer.");
        }
        if (reply.length() > 2000) {
            throw ApiException.badRequest("reply", "Keep the reply under 2000 characters.");
        }
        SupportRequest r = supportService.resolve(admin, id, reply);
        return Map.of("message", r.getReference() + " resolved. The customer has been emailed your reply.");
    }

    @PostMapping("/vendors/{id}/approve")
    public Map<String, Object> approve(@PathVariable Long id, HttpSession session) {
        SessionAuth.require(session, "ADMIN");
        userService.approveVendor(id);
        return Map.of("message", "Seller approved. They can now sign in and list books.");
    }
}
