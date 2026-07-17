package com.bookstore.ecommerce.controller;

import com.bookstore.ecommerce.model.Book;
import com.bookstore.ecommerce.model.Order;
import com.bookstore.ecommerce.model.User;
import com.bookstore.ecommerce.service.BookService;
import com.bookstore.ecommerce.service.OrderService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
public class MainController {

    @Autowired
    private BookService bookService;

    @Autowired
    private OrderService orderService;

    @ModelAttribute
    public void addCommonAttributes(HttpSession session, Model model) {
        User user = (User) session.getAttribute("currentUser");
        model.addAttribute("currentUser", user);
        
        // Cart size counter
        Map<Long, Integer> cart = (Map<Long, Integer>) session.getAttribute("cart");
        int cartSize = 0;
        if (cart != null) {
            cartSize = cart.values().stream().mapToInt(Integer::intValue).sum();
        }
        model.addAttribute("cartSize", cartSize);
    }

    @GetMapping("/")
    public String showStorefront(@RequestParam(required = false) String category,
                                 @RequestParam(required = false) String search,
                                 Model model) {
        List<Book> books;
        if (category != null && !category.trim().isEmpty() && !"All".equalsIgnoreCase(category)) {
            books = bookService.searchBooksByCategory(category, search);
        } else {
            books = bookService.searchBooks(search);
        }
        
        model.addAttribute("books", books);
        model.addAttribute("selectedCategory", category != null ? category : "All");
        model.addAttribute("searchQuery", search);
        
        // Add static categories for navigation
        model.addAttribute("categories", Arrays.asList("Fiction", "Technology", "Science", "Business", "Biography"));
        return "index";
    }

    @GetMapping("/book/{id}")
    public String showBookDetails(@PathVariable Long id, Model model) {
        Optional<Book> bookOpt = bookService.getBookById(id);
        if (bookOpt.isPresent()) {
            model.addAttribute("book", bookOpt.get());
            return "book-detail";
        }
        return "redirect:/";
    }

    @PostMapping("/cart/add")
    @ResponseBody
    public Map<String, Object> addToCart(@RequestParam Long bookId, @RequestParam int quantity, HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        
        Optional<Book> bookOpt = bookService.getBookById(bookId);
        if (bookOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "Book not found");
            return response;
        }

        Book book = bookOpt.get();
        Map<Long, Integer> cart = (Map<Long, Integer>) session.getAttribute("cart");
        if (cart == null) {
            cart = new HashMap<>();
            session.setAttribute("cart", cart);
        }

        int currentQty = cart.getOrDefault(bookId, 0);
        int targetQty = currentQty + quantity;

        if (book.getStock() < targetQty) {
            response.put("success", false);
            response.put("message", "Cannot add more items. Available stock: " + book.getStock());
            return response;
        }

        cart.put(bookId, targetQty);
        session.setAttribute("cart", cart);

        int totalItems = cart.values().stream().mapToInt(Integer::intValue).sum();
        response.put("success", true);
        response.put("message", book.getTitle() + " added to cart!");
        response.put("cartSize", totalItems);
        return response;
    }

    @GetMapping("/cart")
    public String showCart(HttpSession session, Model model) {
        Map<Long, Integer> cart = (Map<Long, Integer>) session.getAttribute("cart");
        List<Map<String, Object>> cartItems = new ArrayList<>();
        double subtotal = 0.0;

        if (cart != null) {
            for (Map.Entry<Long, Integer> entry : cart.entrySet()) {
                Optional<Book> bookOpt = bookService.getBookById(entry.getKey());
                if (bookOpt.isPresent()) {
                    Book book = bookOpt.get();
                    Map<String, Object> itemDetail = new HashMap<>();
                    itemDetail.put("book", book);
                    itemDetail.put("quantity", entry.getValue());
                    itemDetail.put("total", book.getPrice() * entry.getValue());
                    cartItems.add(itemDetail);
                    subtotal += book.getPrice() * entry.getValue();
                }
            }
        }

        model.addAttribute("cartItems", cartItems);
        model.addAttribute("subtotal", subtotal);
        return "cart";
    }

    @PostMapping("/cart/update")
    public String updateCart(@RequestParam Long bookId, @RequestParam int quantity, HttpSession session) {
        Map<Long, Integer> cart = (Map<Long, Integer>) session.getAttribute("cart");
        if (cart != null && cart.containsKey(bookId)) {
            Optional<Book> bookOpt = bookService.getBookById(bookId);
            if (bookOpt.isPresent()) {
                Book book = bookOpt.get();
                if (quantity <= 0) {
                    cart.remove(bookId);
                } else if (quantity <= book.getStock()) {
                    cart.put(bookId, quantity);
                }
            }
        }
        return "redirect:/cart";
    }

    @PostMapping("/cart/remove")
    public String removeFromCart(@RequestParam Long bookId, HttpSession session) {
        Map<Long, Integer> cart = (Map<Long, Integer>) session.getAttribute("cart");
        if (cart != null) {
            cart.remove(bookId);
        }
        return "redirect:/cart";
    }

    @GetMapping("/checkout")
    public String showCheckoutPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("currentUser");
        if (user == null) {
            return "redirect:/login";
        }

        Map<Long, Integer> cart = (Map<Long, Integer>) session.getAttribute("cart");
        if (cart == null || cart.isEmpty()) {
            return "redirect:/cart";
        }

        double subtotal = 0.0;
        List<Map<String, Object>> checkoutItems = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : cart.entrySet()) {
            Optional<Book> bookOpt = bookService.getBookById(entry.getKey());
            if (bookOpt.isPresent()) {
                Book book = bookOpt.get();
                Map<String, Object> details = new HashMap<>();
                details.put("book", book);
                details.put("quantity", entry.getValue());
                details.put("total", book.getPrice() * entry.getValue());
                checkoutItems.add(details);
                subtotal += book.getPrice() * entry.getValue();
            }
        }

        model.addAttribute("items", checkoutItems);
        model.addAttribute("subtotal", subtotal);
        return "checkout";
    }

    @PostMapping("/checkout")
    public String processCheckout(@RequestParam String buyerName,
                                  @RequestParam String shippingAddress,
                                  @RequestParam String contactNumber,
                                  HttpSession session,
                                  Model model) {
        User user = (User) session.getAttribute("currentUser");
        if (user == null) {
            return "redirect:/login";
        }

        Map<Long, Integer> cart = (Map<Long, Integer>) session.getAttribute("cart");
        if (cart == null || cart.isEmpty()) {
            return "redirect:/cart";
        }

        // Map containing Books and their quantities
        Map<Book, Integer> bookQuantities = new HashMap<>();
        try {
            for (Map.Entry<Long, Integer> entry : cart.entrySet()) {
                Book book = bookService.getBookById(entry.getKey())
                        .orElseThrow(() -> new IllegalArgumentException("Book no longer available"));
                bookQuantities.put(book, entry.getValue());
            }

            Order order = orderService.placeOrder(user, buyerName, shippingAddress, contactNumber, bookQuantities);
            
            // Clear cart
            session.removeAttribute("cart");
            model.addAttribute("order", order);
            return "order-success";

        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            // Re-render checkout
            return showCheckoutPage(session, model);
        }
    }

    @GetMapping("/orders")
    public String viewBuyerOrderHistory(HttpSession session, Model model) {
        User user = (User) session.getAttribute("currentUser");
        if (user == null) {
            return "redirect:/login";
        }
        
        List<Order> orders = orderService.getBuyerOrders(user);
        model.addAttribute("orders", orders);
        return "orders";
    }
}
