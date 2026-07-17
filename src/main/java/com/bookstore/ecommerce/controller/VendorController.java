package com.bookstore.ecommerce.controller;

import com.bookstore.ecommerce.model.Book;
import com.bookstore.ecommerce.model.OrderItem;
import com.bookstore.ecommerce.model.User;
import com.bookstore.ecommerce.service.BookService;
import com.bookstore.ecommerce.service.OrderService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/vendor")
public class VendorController {

    @Autowired
    private BookService bookService;

    @Autowired
    private OrderService orderService;

    @ModelAttribute
    public void checkVendorAuth(HttpSession session, Model model) {
        User user = (User) session.getAttribute("currentUser");
        if (user == null || !"VENDOR".equals(user.getRole())) {
            throw new SecurityException("Unauthorized. Vendor credentials required.");
        }
        
        // Check if approved
        if (!user.isVendorApproved()) {
            throw new SecurityException("Account pending approval.");
        }
        
        model.addAttribute("currentUser", user);
    }

    @ExceptionHandler(SecurityException.class)
    public String handleSecurityException(SecurityException ex, HttpSession session) {
        session.invalidate();
        return "redirect:/login?error=" + ex.getMessage();
    }

    @GetMapping("/dashboard")
    public String showDashboard(HttpSession session, Model model) {
        User vendor = (User) session.getAttribute("currentUser");
        
        // Retrieve stats
        double totalSales = orderService.getVendorTotalSales(vendor);
        long booksSold = orderService.getVendorTotalBooksSold(vendor);
        long totalOrders = orderService.getVendorTotalOrdersCount(vendor);
        
        // Listings and order items
        List<Book> myBooks = bookService.getBooksByVendor(vendor);
        List<OrderItem> orderItems = orderService.getVendorOrderItems(vendor);

        model.addAttribute("totalSales", totalSales);
        model.addAttribute("booksSold", booksSold);
        model.addAttribute("totalOrders", totalOrders);
        model.addAttribute("myBooks", myBooks);
        model.addAttribute("orderItems", orderItems);
        
        return "vendor/dashboard";
    }

    @GetMapping("/book/new")
    public String showAddBookForm(Model model) {
        model.addAttribute("book", new Book());
        return "vendor/book-form";
    }

    @PostMapping("/book/new")
    public String addBook(@ModelAttribute Book book, HttpSession session, Model model) {
        User vendor = (User) session.getAttribute("currentUser");
        try {
            book.setVendor(vendor);
            bookService.saveBook(book);
            return "redirect:/vendor/dashboard?success=Book listed successfully!";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("book", book);
            return "vendor/book-form";
        }
    }

    @GetMapping("/book/edit/{id}")
    public String showEditBookForm(@PathVariable Long id, HttpSession session, Model model) {
        User vendor = (User) session.getAttribute("currentUser");
        Optional<Book> bookOpt = bookService.getBookById(id);
        if (bookOpt.isPresent()) {
            Book book = bookOpt.get();
            if (book.getVendor().getId().equals(vendor.getId())) {
                model.addAttribute("book", book);
                return "vendor/book-form";
            }
        }
        return "redirect:/vendor/dashboard?error=Unauthorized book access";
    }

    @PostMapping("/book/edit/{id}")
    public String editBook(@PathVariable Long id, @ModelAttribute Book bookForm, HttpSession session, Model model) {
        User vendor = (User) session.getAttribute("currentUser");
        try {
            Book existingBook = bookService.getBookById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Book not found"));

            if (!existingBook.getVendor().getId().equals(vendor.getId())) {
                return "redirect:/vendor/dashboard?error=Unauthorized action";
            }

            existingBook.setTitle(bookForm.getTitle());
            existingBook.setAuthor(bookForm.getAuthor());
            existingBook.setDescription(bookForm.getDescription());
            existingBook.setPrice(bookForm.getPrice());
            existingBook.setStock(bookForm.getStock());
            existingBook.setCategory(bookForm.getCategory());
            existingBook.setCoverUrl(bookForm.getCoverUrl());

            bookService.saveBook(existingBook);
            return "redirect:/vendor/dashboard?success=Book updated successfully!";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("book", bookForm);
            return "vendor/book-form";
        }
    }

    @PostMapping("/book/delete/{id}")
    public String deleteBook(@PathVariable Long id, HttpSession session) {
        User vendor = (User) session.getAttribute("currentUser");
        try {
            bookService.deleteBook(id, vendor);
            return "redirect:/vendor/dashboard?success=Book removed from listings.";
        } catch (Exception e) {
            return "redirect:/vendor/dashboard?error=" + e.getMessage();
        }
    }

    @PostMapping("/order-item/status")
    public String updateOrderStatus(@RequestParam Long orderItemId, @RequestParam String status, HttpSession session) {
        User vendor = (User) session.getAttribute("currentUser");
        try {
            orderService.updateOrderItemStatus(orderItemId, status, vendor);
            return "redirect:/vendor/dashboard?success=Order item status updated to " + status;
        } catch (Exception e) {
            return "redirect:/vendor/dashboard?error=" + e.getMessage();
        }
    }
}
