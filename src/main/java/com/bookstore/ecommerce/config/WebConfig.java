package com.bookstore.ecommerce.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The frontend is a plain HTML/CSS/JavaScript single-page app in /static. Its client-side routes are
 * served index.html so that refreshing or opening a deep link works; js/router.js then shows the page.
 * Keep this list in sync with the routes in static/js/app.js.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final String[] SPA_ROUTES = {
            "/", "/book/{id}", "/cart", "/checkout", "/orders", "/orders/{id}/confirmation",
            "/login", "/register", "/forgot-password", "/reset-password", "/seller", "/seller/books/new", "/seller/books/{id}/edit", "/admin", "/account", "/support",
    };

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        for (String route : SPA_ROUTES) {
            registry.addViewController(route).setViewName("forward:/index.html");
        }
        // Addresses from earlier versions of the site
        registry.addRedirectViewController("/vendor/dashboard", "/seller");
        registry.addRedirectViewController("/admin/dashboard", "/admin");
    }
}
