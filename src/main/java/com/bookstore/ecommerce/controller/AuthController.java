package com.bookstore.ecommerce.controller;

import com.bookstore.ecommerce.model.User;
import com.bookstore.ecommerce.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
public class AuthController {

    @Autowired
    private UserService userService;

    @GetMapping("/login")
    public String showLoginForm(HttpSession session, Model model) {
        if (session.getAttribute("currentUser") != null) {
            return "redirect:/";
        }
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        HttpSession session,
                        Model model) {
        Optional<User> userOpt = userService.loginUser(username, password);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if ("VENDOR".equals(user.getRole()) && !user.isVendorApproved()) {
                model.addAttribute("error", "Your vendor account is pending approval by the administrator.");
                return "login";
            }
            session.setAttribute("currentUser", user);
            
            // Redirect based on role
            if ("ADMIN".equals(user.getRole())) {
                return "redirect:/admin/dashboard";
            } else if ("VENDOR".equals(user.getRole())) {
                return "redirect:/vendor/dashboard";
            } else {
                return "redirect:/";
            }
        }
        model.addAttribute("error", "Invalid username or password");
        return "login";
    }

    @GetMapping("/register")
    public String showRegisterForm(HttpSession session, Model model) {
        if (session.getAttribute("currentUser") != null) {
            return "redirect:/";
        }
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute User user,
                           @RequestParam(required = false) String vendorShopName,
                           Model model) {
        try {
            if ("VENDOR".equals(user.getRole())) {
                if (vendorShopName == null || vendorShopName.trim().isEmpty()) {
                    model.addAttribute("error", "Vendor Bookstore Name is required for vendor registration!");
                    model.addAttribute("user", user);
                    return "register";
                }
                user.setVendorName(vendorShopName);
            }
            userService.registerUser(user);
            
            if ("VENDOR".equals(user.getRole())) {
                model.addAttribute("success", "Registration successful! Please wait for admin approval before logging in.");
            } else {
                model.addAttribute("success", "Registration successful! You can now log in.");
            }
            return "login";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("user", user);
            return "register";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
