package com.bookstore.ecommerce.controller;

import com.bookstore.ecommerce.model.User;
import com.bookstore.ecommerce.service.BookService;
import com.bookstore.ecommerce.service.OrderService;
import com.bookstore.ecommerce.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private UserService userService;

    @Autowired
    private BookService bookService;

    @Autowired
    private OrderService orderService;

    @ModelAttribute
    public void checkAdminAuth(HttpSession session, Model model) {
        User user = (User) session.getAttribute("currentUser");
        if (user == null || !"ADMIN".equals(user.getRole())) {
            throw new SecurityException("Access Denied. Administrator credentials required.");
        }
        model.addAttribute("currentUser", user);
    }

    @ExceptionHandler(SecurityException.class)
    public String handleSecurityException(SecurityException ex, HttpSession session) {
        session.invalidate();
        return "redirect:/login?error=" + ex.getMessage();
    }

    @GetMapping("/dashboard")
    public String showAdminDashboard(Model model) {
        // Collect site statistics
        double totalSales = orderService.getPlatformTotalSales();
        long totalOrders = orderService.getPlatformTotalOrders();
        long vendorCount = userService.getVendorCount();
        long buyerCount = userService.getBuyerCount();
        long bookCount = bookService.getBookCount();

        // Get pending vendor registrations
        List<User> pendingVendors = userService.getPendingVendors();
        List<User> approvedVendors = userService.getAllVendors().stream()
                .filter(User::isVendorApproved)
                .toList();

        model.addAttribute("totalSales", totalSales);
        model.addAttribute("totalOrders", totalOrders);
        model.addAttribute("vendorCount", vendorCount);
        model.addAttribute("buyerCount", buyerCount);
        model.addAttribute("bookCount", bookCount);
        model.addAttribute("pendingVendors", pendingVendors);
        model.addAttribute("approvedVendors", approvedVendors);

        return "admin/dashboard";
    }

    @PostMapping("/vendor/approve")
    public String approveVendor(@RequestParam Long vendorId) {
        try {
            userService.approveVendor(vendorId);
            return "redirect:/admin/dashboard?success=Vendor approved successfully!";
        } catch (Exception e) {
            return "redirect:/admin/dashboard?error=" + e.getMessage();
        }
    }
}
