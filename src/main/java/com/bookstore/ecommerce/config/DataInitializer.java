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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@org.springframework.core.annotation.Order(1)
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    // In production this comes from ADMIN_PASSWORD (see application-prod.properties), never the public demo password
    @Value("${app.demo.admin-password:admin123}")
    private String adminPassword;

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.count() > 0) {
            return; // DB already initialized
        }

        // Initialize Users
        User admin = new User("admin", passwordEncoder.encode(adminPassword), "admin@bookstore.com", "ADMIN");
        admin.setVendorApproved(true);
        userRepository.save(admin);

        User buyer = new User("buyer", passwordEncoder.encode("buyer123"), "buyer@gmail.com", "BUYER");
        buyer.setVendorApproved(true);
        userRepository.save(buyer);

        User vendorA = new User("vendor_a", passwordEncoder.encode("vendor123"), "sales@apexbooks.com", "VENDOR", "Apex Books", true);
        userRepository.save(vendorA);

        User vendorB = new User("vendor_b", passwordEncoder.encode("vendor123"), "contact@beaconbooks.net", "VENDOR", "Beacon Bookshop", true);
        userRepository.save(vendorB);

        User vendorC = new User("vendor_c", passwordEncoder.encode("vendor123"), "admin@cosmosreads.org", "VENDOR", "Cosmos Reads & Zines", false);
        userRepository.save(vendorC);

        // Initialize Books — 75 titles, real covers (Open Library ISBN), prices in INR (₹)
        List<Book> books = new ArrayList<>();

        // ── Technology ──────────────────────────────────────────────────────────
        books.add(new Book("Clean Code", "Robert C. Martin",
                "A practical guide to writing readable, maintainable software. Martin covers naming conventions, functions, error handling, and unit tests with concrete refactoring examples.",
                1899.0, 15, "Technology",
                "/covers/9780132350884.jpg", vendorA));

        books.add(new Book("Design Patterns", "Gang of Four",
                "The definitive catalog of 23 classic object-oriented design patterns. Each pattern is presented with a real-world example, a class diagram, and implementation guidance.",
                2499.0, 10, "Technology",
                "/covers/9780201633610.jpg", vendorB));

        books.add(new Book("The Pragmatic Programmer", "David Thomas & Andrew Hunt",
                "From journeyman to master — timeless advice on career development, coding philosophy, and the habits that separate good engineers from great ones.",
                2099.0, 18, "Technology",
                "/covers/9780135957059.jpg", vendorA));

        books.add(new Book("You Don't Know JS Yet", "Kyle Simpson",
                "A deep dive into the JavaScript language's core mechanisms — scope, closures, the 'this' keyword, prototypes, and async patterns — without the hand-waving.",
                1499.0, 22, "Technology",
                "/covers/9781491904244.jpg", vendorB));

        books.add(new Book("Cracking the Coding Interview", "Gayle Laakmann McDowell",
                "189 programming questions and solutions covering arrays, strings, trees, graphs, dynamic programming, and system design — the essential prep for technical interviews.",
                1749.0, 30, "Technology",
                "/covers/9780984782857.jpg", vendorA));

        // ── Fiction ─────────────────────────────────────────────────────────────
        books.add(new Book("The Hobbit", "J.R.R. Tolkien",
                "Bilbo Baggins, a comfort-loving hobbit, is swept into an epic quest with thirteen dwarves and a wizard to reclaim treasure guarded by the dragon Smaug.",
                499.0, 30, "Fiction",
                "https://covers.openlibrary.org/b/isbn/9780547928227-L.jpg", vendorB));

        books.add(new Book("1984", "George Orwell",
                "In Oceania, Big Brother watches everyone. Winston Smith dares to question the regime and starts a forbidden love affair — a timeless warning about totalitarianism.",
                399.0, 45, "Fiction",
                "/covers/9780451524935.jpg", vendorA));

        books.add(new Book("Dune", "Frank Herbert",
                "On the desert planet Arrakis, young Paul Atreides navigates political betrayal, ecological intrigue, and prophecy to become the messiah of the Fremen people.",
                649.0, 20, "Fiction",
                "/covers/9780441013593.jpg", vendorB));

        books.add(new Book("The Midnight Library", "Matt Haig",
                "Between life and death lies an infinite library where every book represents a different life Nora Seed could have lived. A moving story about regret, possibility, and finding reasons to live.",
                599.0, 28, "Fiction",
                "/covers/9780525559474.jpg", vendorA));

        books.add(new Book("Tomorrow, and Tomorrow, and Tomorrow", "Gabrielle Zevin",
                "A sweeping story of two game designers whose creative partnership spans thirty years of friendship, love, and the games they build together — an ode to imagination.",
                799.0, 14, "Fiction",
                "/covers/9780593321201.jpg", vendorB));

        books.add(new Book("Piranesi", "Susanna Clarke",
                "In a labyrinthine house with infinite halls and statues, a gentle man records tides, birds, and a mystery about his own identity. Dreamlike and utterly original.",
                549.0, 17, "Fiction",
                "/covers/9781635575637.jpg", vendorA));

        // ── Science ─────────────────────────────────────────────────────────────
        books.add(new Book("Sapiens", "Yuval Noah Harari",
                "A sweeping narrative of humankind's journey from obscure African savannah to global dominance — covering cognitive revolution, agriculture, empire, and capitalism.",
                899.0, 25, "Science",
                "/covers/9780062316097.jpg", vendorA));

        books.add(new Book("A Brief History of Time", "Stephen Hawking",
                "Hawking guides readers from the Big Bang to black holes, tackling space, time, the nature of reality, and the possibility of a unified theory of everything.",
                549.0, 35, "Science",
                "/covers/9780553380163.jpg", vendorB));

        books.add(new Book("The Selfish Gene", "Richard Dawkins",
                "A revolutionary view of evolution: it is the gene, not the individual or species, that is the unit of selection. Dawkins introduces the concept of the 'meme'.",
                699.0, 20, "Science",
                "/covers/9780198788607.jpg", vendorA));

        books.add(new Book("The Gene: An Intimate History", "Siddhartha Mukherjee",
                "From Mendel's peas to CRISPR, a Pulitzer Prize–winning author traces the history of the gene and grapples with its profound moral implications for humanity.",
                1099.0, 12, "Science",
                "/covers/9781476733524.jpg", vendorB));

        // ── Business ────────────────────────────────────────────────────────────
        books.add(new Book("Zero to One", "Peter Thiel",
                "Legendary entrepreneur Peter Thiel argues that the next wave of progress will come not from competing but from creating monopolies in entirely new markets.",
                699.0, 12, "Business",
                "/covers/9780804139298.jpg", vendorB));

        books.add(new Book("The Lean Startup", "Eric Ries",
                "A methodology for building businesses by testing assumptions quickly, learning from failures, and pivoting before wasting years on the wrong product.",
                849.0, 16, "Business",
                "/covers/9780307887894.jpg", vendorA));

        books.add(new Book("Good to Great", "Jim Collins",
                "What separates companies that merely succeed from those that achieve lasting greatness? Collins distills seven principles from an eleven-year research project.",
                999.0, 9, "Business",
                "/covers/9780066620992.jpg", vendorB));

        books.add(new Book("Think and Grow Rich", "Napoleon Hill",
                "A classic on success philosophy, Hill distills interviews with 500 of the most successful Americans into 13 principles for achieving wealth and achievement.",
                449.0, 40, "Business",
                "/covers/9781585424337.jpg", vendorA));

        books.add(new Book("Atomic Habits", "James Clear",
                "Clear reveals how tiny changes compound into remarkable results, offering a proven framework for building good habits and breaking bad ones — one percent at a time.",
                799.0, 50, "Business",
                "/covers/9780735211292.jpg", vendorB));

        // ── Biography ───────────────────────────────────────────────────────────
        books.add(new Book("Steve Jobs", "Walter Isaacson",
                "Drawing on 40+ interviews with Jobs, Isaacson paints a riveting portrait of the creative genius who transformed six industries — and the human cost of his vision.",
                1249.0, 8, "Biography",
                "/covers/9781451648539.jpg", vendorA));

        books.add(new Book("Becoming", "Michelle Obama",
                "The former First Lady chronicles her journey from the South Side of Chicago to the White House, exploring identity, purpose, and the power of using your voice.",
                1199.0, 22, "Biography",
                "/covers/9781524763138.jpg", vendorB));

        books.add(new Book("The Diary of a Young Girl", "Anne Frank",
                "Anne Frank's candid diary, written while hiding from the Nazis in Amsterdam, is one of the most powerful personal accounts of the Holocaust ever published.",
                399.0, 38, "Biography",
                "/covers/9780553296983.jpg", vendorA));

        books.add(new Book("Leonardo da Vinci", "Walter Isaacson",
                "Isaacson draws on thousands of pages of Da Vinci's notebooks to portray a man whose insatiable curiosity and wild imagination were the secrets of his genius.",
                1399.0, 11, "Biography",
                "/covers/9781501139154.jpg", vendorB));

        // ── History ─────────────────────────────────────────────────────────────
        books.add(new Book("The Guns of August", "Barbara Tuchman",
                "A Pulitzer Prize–winning account of the first month of World War I, showing how miscalculations and miscommunications sent millions toward catastrophic slaughter.",
                749.0, 13, "History",
                "/covers/9780345476098.jpg", vendorA));

        books.add(new Book("SPQR: A History of Ancient Rome", "Mary Beard",
                "A leading classicist recounts the story of Rome across a thousand years — from a small village on the Tiber to the capital of the world's greatest ancient empire.",
                949.0, 15, "History",
                "/covers/9781631492228.jpg", vendorB));

        books.add(new Book("Guns, Germs, and Steel", "Jared Diamond",
                "Why did some civilisations conquer others? Diamond argues geography and biology — not race or culture — determined the global distribution of power.",
                849.0, 18, "History",
                "/covers/9780393317558.jpg", vendorA));

        // ── Philosophy ──────────────────────────────────────────────────────────
        books.add(new Book("Meditations", "Marcus Aurelius",
                "The private journal of a Roman emperor and Stoic philosopher — a timeless guide on resilience, self-discipline, and how to live and lead with integrity.",
                349.0, 60, "Philosophy",
                "/covers/9780812968255.jpg", vendorB));

        books.add(new Book("The Republic", "Plato",
                "Plato's masterwork explores justice, the ideal state, and the philosopher-king, presenting his famous Allegory of the Cave and theory of Forms.",
                399.0, 25, "Philosophy",
                "/covers/9780140455113.jpg", vendorA));

        books.add(new Book("Being and Time", "Martin Heidegger",
                "A foundational text of 20th-century existentialism, exploring human existence, authenticity, and our relationship with time, death, and meaning.",
                1199.0, 7, "Philosophy",
                "/covers/9780061575594.jpg", vendorB));

        books.add(new Book("The Courage to Be Disliked", "Ichiro Kishimi & Fumitake Koga",
                "Through a Socratic dialogue between a philosopher and a youth, this book introduces Adlerian psychology and argues we can choose happiness regardless of the past.",
                599.0, 32, "Philosophy",
                "/covers/9781501197277.jpg", vendorA));

        // ── Self-Help ───────────────────────────────────────────────────────────
        books.add(new Book("The 7 Habits of Highly Effective People", "Stephen R. Covey",
                "Covey presents a principle-centred approach to personal and professional effectiveness, moving from dependence to independence to interdependence.",
                799.0, 42, "Self-Help",
                "https://covers.openlibrary.org/b/isbn/9780743269513-L.jpg", vendorB));

        books.add(new Book("Man's Search for Meaning", "Viktor E. Frankl",
                "A Holocaust survivor's account of life in Nazi concentration camps and the development of logotherapy — the belief that meaning is the primary motivation of human life.",
                449.0, 55, "Self-Help",
                "/covers/9780807014271.jpg", vendorA));

        books.add(new Book("The Power of Now", "Eckhart Tolle",
                "Tolle guides readers toward enlightenment through presence, explaining how living in the 'now' dissolves anxiety, depression, and the suffering caused by the ego.",
                649.0, 27, "Self-Help",
                "/covers/9781577314806.jpg", vendorB));

        books.add(new Book("Deep Work", "Cal Newport",
                "Newport argues the ability to focus without distraction is becoming both rare and valuable. He offers rules for cultivating a deep work ethic in a distracted world.",
                899.0, 19, "Self-Help",
                "/covers/9781455586691.jpg", vendorA));

        // ── Mystery ─────────────────────────────────────────────────────────────
        books.add(new Book("Gone Girl", "Gillian Flynn",
                "On their fifth wedding anniversary, Nick Dunne's wife Amy disappears. As the police investigation unfolds, the story twists with unreliable narrators and dark secrets.",
                549.0, 24, "Mystery",
                "https://covers.openlibrary.org/b/isbn/9780307588371-L.jpg", vendorB));

        books.add(new Book("The Girl with the Dragon Tattoo", "Stieg Larsson",
                "Journalist Mikael Blomkvist and hacker Lisbeth Salander investigate a decades-old disappearance within a wealthy Swedish family — uncovering dark secrets along the way.",
                599.0, 21, "Mystery",
                "/covers/9780307454546.jpg", vendorA));

        books.add(new Book("In the Woods", "Tana French",
                "Dublin detective Rob Ryan investigates a child's murder in the woods where he was the sole survivor of a childhood trauma he can't remember — a haunting debut.",
                649.0, 16, "Mystery",
                "/covers/9780143113492.jpg", vendorB));

        // ── Romance ─────────────────────────────────────────────────────────────
        books.add(new Book("Pride and Prejudice", "Jane Austen",
                "Elizabeth Bennet and the proud Mr. Darcy navigate family pressure, social class, and their own prejudices in a witty and irresistible love story.",
                299.0, 48, "Romance",
                "/covers/9780141439518.jpg", vendorA));

        books.add(new Book("The Notebook", "Nicholas Sparks",
                "Noah and Allie fall in love one summer only to be separated by war and circumstance. Decades later, an old man reads their story to a woman losing her memory.",
                449.0, 31, "Romance",
                "https://covers.openlibrary.org/b/isbn/9780446605236-L.jpg", vendorB));

        books.add(new Book("Beach Read", "Emily Henry",
                "Two novelists with writer's block swap genres for the summer — a literary fiction writer tries romance, and a romance writer tries literary fiction. Sparks fly.",
                599.0, 23, "Romance",
                "/covers/9781984806734.jpg", vendorA));

        // ── Sci-Fi ──────────────────────────────────────────────────────────────
        books.add(new Book("The Three-Body Problem", "Liu Cixin",
                "During China's Cultural Revolution, a secret military project sends signals into space. The response triggers a terrifying chain of events that will threaten humanity.",
                749.0, 20, "Sci-Fi",
                "/covers/9780765382030.jpg", vendorB));

        books.add(new Book("Ender's Game", "Orson Scott Card",
                "Child prodigy Ender Wiggin is recruited by the military to defeat an alien race. What unfolds is a story of strategy, isolation, and moral ambiguity.",
                549.0, 33, "Sci-Fi",
                "/covers/9780812550702.jpg", vendorA));

        books.add(new Book("Project Hail Mary", "Andy Weir",
                "Ryland Grace wakes alone on a spacecraft with no memory of how he got there. He's humanity's last hope against an extinction event — and he has an unexpected ally.",
                849.0, 26, "Sci-Fi",
                "/covers/9780593135204.jpg", vendorB));

        books.add(new Book("The Martian", "Andy Weir",
                "After a freak storm, astronaut Mark Watney is stranded on Mars with only his wits, limited supplies, and a dark sense of humour to survive until rescue.",
                599.0, 19, "Sci-Fi",
                "/covers/9780804139021.jpg", vendorA));

        // ── Psychology ──────────────────────────────────────────────────────────
        books.add(new Book("Thinking, Fast and Slow", "Daniel Kahneman",
                "Nobel laureate Kahneman explains the two systems that drive thinking — fast, intuitive System 1, and slow, deliberate System 2 — and how their interplay shapes judgment.",
                999.0, 29, "Psychology",
                "/covers/9780374533557.jpg", vendorB));

        books.add(new Book("The Body Keeps the Score", "Bessel van der Kolk",
                "A groundbreaking exploration of how trauma reshapes body and brain, and new therapeutic methods — yoga, EMDR, theatre — that allow people to heal and reclaim their lives.",
                1099.0, 15, "Psychology",
                "/covers/9780670785933.jpg", vendorA));

        books.add(new Book("Quiet: The Power of Introverts", "Susan Cain",
                "Cain challenges a culture that undervalues introverts and argues the world needs the thoughtful, creative contributions of its quieter citizens.",
                749.0, 22, "Psychology",
                "/covers/9780307352156.jpg", vendorB));

        // ── Children ────────────────────────────────────────────────────────────
        books.add(new Book("Harry Potter and the Philosopher's Stone", "J.K. Rowling",
                "Orphan Harry Potter discovers on his eleventh birthday that he is a wizard and is whisked off to Hogwarts School of Witchcraft and Wizardry — beginning a legendary adventure.",
                399.0, 75, "Children",
                "/covers/9780439708180.jpg", vendorA));

        books.add(new Book("The Little Prince", "Antoine de Saint-Exupéry",
                "A tiny prince who travels the universe shares his wisdom about love, loneliness, and what truly matters. A deceptively simple fable beloved by readers of all ages.",
                299.0, 62, "Children",
                "/covers/9780156012195.jpg", vendorB));

        books.add(new Book("Charlotte's Web", "E.B. White",
                "A spider named Charlotte and a pig named Wilbur form one of literature's most touching friendships in this tender story about loyalty, love, and loss.",
                349.0, 44, "Children",
                "/covers/9780064400558.jpg", vendorA));

        // ── Health ──────────────────────────────────────────────────────────────
        books.add(new Book("Why We Sleep", "Matthew Walker",
                "A neuroscientist reveals the extraordinary importance of sleep and its devastating effects when lost — covering dreams, memory, cancer, Alzheimer's, and mental health.",
                949.0, 17, "Health",
                "/covers/9781501144318.jpg", vendorB));

        books.add(new Book("How Not to Die", "Michael Greger",
                "A physician examines the 15 top causes of death in America and reveals how diet and lifestyle choices can prevent or even reverse them.",
                1149.0, 13, "Health",
                "/covers/9781250066619.jpg", vendorA));

        // ── Travel ──────────────────────────────────────────────────────────────
        books.add(new Book("A Walk in the Woods", "Bill Bryson",
                "Bryson attempts to hike the 2,200-mile Appalachian Trail with his curmudgeonly friend Katz. Hilarious and informative, it's a love letter to the American wilderness.",
                649.0, 21, "Travel",
                "/covers/9780767902526.jpg", vendorB));

        books.add(new Book("Into the Wild", "Jon Krakauer",
                "The true story of Christopher McCandless, who gave away his savings and hiked into the Alaskan wilderness alone — a meditation on freedom, identity, and risk.",
                549.0, 26, "Travel",
                "/covers/9780385486804.jpg", vendorA));

        // ── Art & Design ────────────────────────────────────────────────────────
        books.add(new Book("The Elements of Typographic Style", "Robert Bringhurst",
                "Called the 'typographer's bible', this book covers the history and practice of typography with elegant precision — essential for anyone who works with text and design.",
                1699.0, 8, "Art & Design",
                "/covers/9780881792126.jpg", vendorB));

        books.add(new Book("The Artist's Way", "Julia Cameron",
                "A 12-week program to unlock creative potential through morning pages, artist dates, and exercises that unblock fear and reignite imagination.",
                799.0, 20, "Art & Design",
                "/covers/9780143129257.jpg", vendorA));

        // ── Spirituality & Sacred Texts ──────────────────────────────────────────
        books.add(new Book("Bhagavad Gita As It Is", "A.C. Bhaktivedanta Swami Prabhupada",
                "The Bhagavad Gita — the 'Song of God' — is the essence of Vedic knowledge. Spoken by Lord Krishna to the warrior Arjuna on the battlefield of Kurukshetra, it addresses the eternal questions of duty, devotion, and the nature of the self.",
                299.0, 80, "Spirituality",
                "/covers/9780892132683.jpg", vendorA));

        books.add(new Book("The Ramayana", "Valmiki (trans. Arshia Sattar)",
                "Valmiki's ancient Sanskrit epic follows the noble prince Rama, his devoted wife Sita, and the loyal Hanuman through exile, abduction, and a great war — a timeless story of dharma, love, and courage.",
                449.0, 55, "Spirituality",
                "/covers/9780143064664.jpg", vendorB));

        books.add(new Book("Mahabharata: A Modern Rendering", "Ramesh Menon",
                "A gripping modern prose retelling of the world's longest epic — the rivalry between the Kauravas and Pandavas, the role of Krishna, and the great war at Kurukshetra that shaped the moral universe of India.",
                699.0, 40, "Spirituality",
                "/covers/9781611720082.jpg", vendorA));

        books.add(new Book("Autobiography of a Yogi", "Paramahansa Yogananda",
                "One of the great spiritual classics of the 20th century, Yogananda recounts his encounters with Indian saints and sages and reveals the hidden science behind miracles and yoga.",
                499.0, 45, "Spirituality",
                "/covers/9780876120798.jpg", vendorB));

        books.add(new Book("The Upanishads", "trans. Eknath Easwaran",
                "The Upanishads are the philosophical bedrock of Hinduism, exploring Brahman, Atman, and the nature of ultimate reality. Easwaran's lyrical translation makes these 3,000-year-old texts accessible to modern readers.",
                449.0, 35, "Spirituality",
                "/covers/9781586380212.jpg", vendorA));

        books.add(new Book("The Gospel of Sri Ramakrishna", "Mahendranath Gupta",
                "A detailed record of the teachings and conversations of the 19th-century mystic Sri Ramakrishna, whose life bridged the gap between ancient Hindu spirituality and the modern world.",
                599.0, 28, "Spirituality",
                "/covers/9780911206012.jpg", vendorB));

        books.add(new Book("Ashtavakra Gita", "trans. John Richards",
                "A radical dialogue between the sage Ashtavakra and King Janaka on the nature of pure consciousness and liberation — one of Advaita Vedanta's most direct and powerful texts.",
                349.0, 22, "Spirituality",
                "/covers/9788120820449.jpg", vendorA));

        // ── Indian Culture & History ─────────────────────────────────────────────
        books.add(new Book("India: A History", "John Keay",
                "A sweeping, single-volume history of the Indian subcontinent from the Indus Valley Civilisation to independence — authoritative, vivid, and an essential introduction to one of the world's oldest cultures.",
                999.0, 30, "Indian Culture",
                "/covers/9780802137975.jpg", vendorB));

        books.add(new Book("The Wonder That Was India", "A.L. Basham",
                "A classic scholarly survey of India's civilisation before the coming of the Muslims, covering society, religion, art, literature, and science with remarkable depth and clarity.",
                1149.0, 18, "Indian Culture",
                "/covers/9780330439091.jpg", vendorA));

        books.add(new Book("Indica: A Deep Natural History of the Indian Subcontinent", "Pranay Lal",
                "A thrilling journey through 600 million years of Indian natural history — dinosaurs, ancient seas, early humans, and the formation of the Himalayas — told through fossils and living wonders.",
                899.0, 20, "Indian Culture",
                "https://covers.openlibrary.org/b/isbn/9780670088119-L.jpg", vendorB));

        books.add(new Book("City of Djinns", "William Dalrymple",
                "A year spent in Delhi's layered, chaotic, magnificent cityscape — tracing the city's Mughal, British, and modern lives through its alleyways, shrines, and forgotten monuments.",
                699.0, 25, "Indian Culture",
                "https://covers.openlibrary.org/b/isbn/9780006548164-L.jpg", vendorA));

        books.add(new Book("Inglorious Empire", "Shashi Tharoor",
                "A devastating and eloquent case against British colonialism in India, arguing that the Raj systematically drained the subcontinent of wealth, dignity, and self-determination.",
                799.0, 35, "Indian Culture",
                "/covers/9781849049849.jpg", vendorB));

        // ── Indian Fiction ───────────────────────────────────────────────────────
        books.add(new Book("The God of Small Things", "Arundhati Roy",
                "Set in Kerala in 1969, Roy's Booker Prize-winning debut weaves a heartbreaking story of forbidden love, caste, and the weight of small choices that reshape entire lives. Lush, lyrical, and unforgettable.",
                499.0, 38, "Indian Fiction",
                "/covers/9780812979657.jpg", vendorA));

        books.add(new Book("Gitanjali", "Rabindranath Tagore",
                "The collection of 103 devotional poems that won Tagore the Nobel Prize in Literature in 1913. Written in Bengali and translated into luminous English prose by the poet himself — an offering of songs to the divine.",
                249.0, 50, "Indian Fiction",
                "https://covers.openlibrary.org/b/isbn/9780020571704-L.jpg", vendorB));

        books.add(new Book("Malgudi Days", "R.K. Narayan",
                "Thirty-two stories set in the fictional south Indian town of Malgudi — gentle, humorous, and deeply human portraits of small-town life that capture the universal in the local.",
                349.0, 42, "Indian Fiction",
                "https://covers.openlibrary.org/b/isbn/9780140088939-L.jpg", vendorA));

        books.add(new Book("A Suitable Boy", "Vikram Seth",
                "One of the longest novels in the English language and one of India's greatest — a sweeping family saga set in 1950s post-partition India, following a mother's quest to find the right husband for her daughter Lata.",
                1299.0, 15, "Indian Fiction",
                "/covers/9780060786526.jpg", vendorB));

        books.add(new Book("The White Tiger", "Aravind Adiga",
                "Told through letters to the Chinese premier, a driver from rural Bihar narrates his violent rise from servitude to entrepreneur. A dark, satirical, and bracingly honest portrait of modern India. Booker Prize winner.",
                499.0, 32, "Indian Fiction",
                "/covers/9781416562603.jpg", vendorA));

        books.add(new Book("Midnight's Children", "Salman Rushdie",
                "Saleem Sinai, born at the exact moment India gained independence, is granted telepathic powers that link him to the 1,001 children born in that midnight hour. Rushdie's magical masterpiece. Booker Prize winner.",
                649.0, 27, "Indian Fiction",
                "/covers/9780812976533.jpg", vendorB));

        books.add(new Book("Five Point Someone", "Chetan Bhagat",
                "Three friends at IIT Delhi — Hari, Alok, and Ryan — struggle with pressure, love, and rebellion in India's most competitive engineering college. The novel that started a revolution in Indian popular fiction.",
                299.0, 60, "Indian Fiction",
                "/covers/9788129135490.jpg", vendorA));

        books.add(new Book("The Namesake", "Jhumpa Lahiri",
                "Gogol Ganguli grows up caught between his Bengali parents' immigrant identity and his own desire to be simply American. Lahiri writes with quiet precision about belonging, memory, and what we inherit from our families.",
                549.0, 29, "Indian Fiction",
                "/covers/9780618485222.jpg", vendorB));

        // ── Indian Non-Fiction ───────────────────────────────────────────────────
        books.add(new Book("Wings of Fire", "A.P.J. Abdul Kalam",
                "The extraordinary autobiography of India's 'Missile Man' and beloved President, from a humble childhood in Rameswaram to leading the nation's space and defence programmes — an inspirational story of scientific vision and humble determination.",
                349.0, 70, "Indian Non-Fiction",
                "/covers/9788173711466.jpg", vendorA));

        books.add(new Book("Discovery of India", "Jawaharlal Nehru",
                "Written during his imprisonment in Ahmednagar Fort, Nehru explores India's rich civilisational history, philosophy, and culture — a profound meditation on nationhood, identity, and the idea of India itself.",
                699.0, 22, "Indian Non-Fiction",
                "/covers/9780195623598.jpg", vendorB));

        books.add(new Book("An Era of Darkness", "Shashi Tharoor",
                "Tharoor argues with forensic detail that British rule was not merely exploitative but actively impoverished India — stripping it of industry, manufacturing, and self-confidence over 200 years.",
                799.0, 28, "Indian Non-Fiction",
                "https://covers.openlibrary.org/b/isbn/9789386228772-L.jpg", vendorA));

        books.add(new Book("The Palace of Illusions", "Chitra Banerjee Divakaruni",
                "The Mahabharata retold through the eyes of Draupadi — fiery, prophesied, and overlooked. A feminist reimagining of India's greatest epic that gives voice to the woman at the heart of the story.",
                599.0, 34, "Indian Fiction",
                "/covers/9781401309442.jpg", vendorB));

        books.add(new Book("Train to Pakistan", "Khushwant Singh",
                "Set in the summer of 1947, this unflinching novel depicts the Partition of India through the lives of villagers on the Punjab border — love, communal violence, and individual acts of courage amid collective madness.",
                399.0, 36, "Indian Fiction",
                "/covers/9780802141224.jpg", vendorA));

        bookRepository.saveAll(books);

        bookRepository.saveAll(books);

        // Pre-create a Sample Order to fill dashboards
        // Buyer buys "Clean Code" (Vendor A, Qty 1) and "Design Patterns" (Vendor B, Qty 2)
        double book1Price = books.get(0).getPrice();  // Clean Code — vendorA
        double book2Price = books.get(1).getPrice();  // Design Patterns — vendorB
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

        OrderItem item2 = new OrderItem(savedOrder, books.get(1), 2, book2Price, vendorB);
        item2.setStatus("SHIPPED"); // Design Patterns shipped for visual contrast
        orderItemRepository.save(item2);

        savedOrder.getItems().add(item1);
        savedOrder.getItems().add(item2);
        orderRepository.save(savedOrder);
        
        // Deduct initial stocks
        books.get(0).setStock(books.get(0).getStock() - 1);
        books.get(1).setStock(books.get(1).getStock() - 2);
        bookRepository.save(books.get(0));
        bookRepository.save(books.get(1));
    }
}
