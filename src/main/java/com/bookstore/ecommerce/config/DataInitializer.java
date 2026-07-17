package com.bookstore.ecommerce.config;

import com.bookstore.ecommerce.model.Book;
import com.bookstore.ecommerce.model.Order;
import com.bookstore.ecommerce.model.OrderItem;
import com.bookstore.ecommerce.model.User;
import com.bookstore.ecommerce.repository.BookRepository;
import com.bookstore.ecommerce.repository.OrderItemRepository;
import com.bookstore.ecommerce.repository.OrderRepository;
import com.bookstore.ecommerce.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.count() > 0) {
            return; // DB already initialized
        }

        // Initialize Users
        User admin = new User("admin", "admin123", "admin@bookstore.com", "ADMIN");
        admin.setVendorApproved(true);
        userRepository.save(admin);

        User buyer = new User("buyer", "buyer123", "buyer@gmail.com", "BUYER");
        buyer.setVendorApproved(true);
        userRepository.save(buyer);

        User vendorA = new User("vendor_a", "vendor123", "sales@apexbooks.com", "VENDOR", "Apex Books", true);
        userRepository.save(vendorA);

        User vendorB = new User("vendor_b", "vendor123", "contact@beaconbooks.net", "VENDOR", "Beacon Bookshop", true);
        userRepository.save(vendorB);

        User vendorC = new User("vendor_c", "vendor123", "admin@cosmosreads.org", "VENDOR", "Cosmos Reads & Zines", false);
        userRepository.save(vendorC);

        // Initialize Books
        List<Book> books = new ArrayList<>();
        
        books.add(new Book(
                "Clean Code: A Handbook of Agile Software Craftsmanship",
                "Robert C. Martin",
                "Even bad code can function. But if code isn't clean, it can bring a development organization to its knees. Every year, countless hours and significant resources are lost because of poorly written code. But it doesn't have to be that way.",
                42.99,
                15,
                "Technology",
                "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=400&q=80",
                vendorA
        ));

        books.add(new Book(
                "The Hobbit",
                "J.R.R. Tolkien",
                "Written for J.R.R. Tolkien's own children, The Hobbit met with instant critical acclaim when it was first published in 1937. Bilbo Baggins is a hobbit who enjoys a comfortable, unambitious life, rarely traveling any farther than his pantry.",
                14.99,
                30,
                "Fiction",
                "https://images.unsplash.com/photo-1610116306796-6fea9f4fae38?auto=format&fit=crop&w=400&q=80",
                vendorB
        ));

        books.add(new Book(
                "Sapiens: A Brief History of Humankind",
                "Yuval Noah Harari",
                "100,000 years ago, at least six human species inhabited the earth. Today there is just one. Us. Homo sapiens. How did our species succeed in the battle for dominance? Why did our foraging ancestors come together to create cities and kingdoms?",
                22.50,
                25,
                "Science",
                "https://images.unsplash.com/photo-1589829085413-56de8ae18c73?auto=format&fit=crop&w=400&q=80",
                vendorA
        ));

        books.add(new Book(
                "Zero to One: Notes on Startups, or How to Build the Future",
                "Peter Thiel",
                "The great secret of our time is that there are still uncharted frontiers to explore and new inventions to create. In Zero to One, legendary entrepreneur and investor Peter Thiel shows how we can find singular ways to create those new things.",
                18.00,
                12,
                "Business",
                "https://images.unsplash.com/photo-1506880018603-83d5b814b5a6?auto=format&fit=crop&w=400&q=80",
                vendorB
        ));

        books.add(new Book(
                "Steve Jobs",
                "Walter Isaacson",
                "Based on more than forty interviews with Jobs conducted over two years—as well as interviews with more than a hundred family members, friends, adversaries, competitors, and colleagues—Walter Isaacson has written a riveting story of the roller-coaster life.",
                29.99,
                8,
                "Biography",
                "https://images.unsplash.com/photo-1531988042231-d39a9cc12a9a?auto=format&fit=crop&w=400&q=80",
                vendorA
        ));

        books.add(new Book(
                "Design Patterns: Elements of Reusable Object-Oriented Software",
                "Erich Gamma, Richard Helm, Ralph Johnson, John Vlissides",
                "Capturing a wealth of experience about the design of object-oriented software, four top-notch designers present a catalog of simple and succinct solutions to commonly occurring design problems.",
                54.95,
                10,
                "Technology",
                "https://images.unsplash.com/photo-1629654297299-c8506221ca97?auto=format&fit=crop&w=400&q=80",
                vendorB
        ));

        bookRepository.saveAll(books);

        // Pre-create a Sample Order to fill dashboards
        // Buyer buys "Clean Code" (Vendor A, Qty 1, Price $42.99) and "Zero to One" (Vendor B, Qty 2, Price $18.00)
        double book1Price = books.get(0).getPrice();
        double book2Price = books.get(3).getPrice();
        double orderTotal = (book1Price * 1) + (book2Price * 2);

        Order sampleOrder = new Order(
                buyer,
                "Jane Doe",
                "123 Library Lane, Booktown, BK 54321",
                "+1 (555) 019-2834",
                LocalDateTime.now().minusDays(2),
                "PENDING",
                orderTotal
        );
        Order savedOrder = orderRepository.save(sampleOrder);

        // OrderItems
        OrderItem item1 = new OrderItem(savedOrder, books.get(0), 1, book1Price, vendorA);
        item1.setStatus("PENDING");
        orderItemRepository.save(item1);

        OrderItem item2 = new OrderItem(savedOrder, books.get(3), 2, book2Price, vendorB);
        item2.setStatus("SHIPPED"); // Let's make B's item already shipped for visual contrast
        orderItemRepository.save(item2);

        savedOrder.getItems().add(item1);
        savedOrder.getItems().add(item2);
        orderRepository.save(savedOrder);
        
        // Deduct initial stocks
        books.get(0).setStock(books.get(0).getStock() - 1);
        books.get(3).setStock(books.get(3).getStock() - 2);
        bookRepository.save(books.get(0));
        bookRepository.save(books.get(3));
    }
}
