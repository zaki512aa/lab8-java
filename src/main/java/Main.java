import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.sql.*;
import java.util.List;

public class Main {
    private static final String URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASS = "";

    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS)) {
            initMusicDatabase(conn);

            task1ListAllMusic(conn);
            task2FilterMusic(conn);
            task3AddFavoriteSong(conn);
            task4LoadBooksFromJson(conn);
            task5SortBooksByYear(conn);
            task6BooksBefore2000(conn);
            task7AddSelfAndBooks(conn);
            task8DropTables(conn);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void initMusicDatabase(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS study");
            stmt.execute("RUNSCRIPT FROM 'classpath:music-create.sql'");
        }
    }

    // № 1. Список музыкальных композиций
    private static void task1ListAllMusic(Connection conn) throws SQLException {
        System.out.println("=== № 1. Все композиции ===");
        String sql = "SELECT id, name FROM study.music";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                System.out.printf("%d. %s%n", rs.getInt("id"), rs.getString("name"));
            }
        }
    }

    // № 2. Фильтрация композиций (отсутствуют обе буквы 'm' и 't' без учёта регистра)
    private static void task2FilterMusic(Connection conn) throws SQLException {
        System.out.println("\n=== № 2. Композиции без букв 'm' и 't' ===");
        String sql = "SELECT id, name FROM study.music WHERE LOWER(name) NOT LIKE '%m%' AND LOWER(name) NOT LIKE '%t%'";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                System.out.printf("%d. %s%n", rs.getInt("id"), rs.getString("name"));
            }
        }
    }

    // № 3. Добавление любимой композиции
    private static void task3AddFavoriteSong(Connection conn) throws SQLException {
        System.out.println("\n=== № 3. Добавление композиции ===");
        String insertSql = "INSERT INTO study.music (id, name) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setInt(1, 21);
            ps.setString(2, "Numb");
            ps.executeUpdate();
            System.out.println("Добавлена композиция: Numb (id = 21)");
        }
    }

    // № 4. Загрузка уникальных посетителей и книг из books.json
    private static void task4LoadBooksFromJson(Connection conn) throws Exception {
        System.out.println("\n=== № 4. Загрузка данных из books.json ===");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS visitors (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    surname VARCHAR(100) NOT NULL,
                    phone VARCHAR(30),
                    subscribed BOOLEAN,
                    UNIQUE (name, surname)
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS books (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    author VARCHAR(255) NOT NULL,
                    publishing_year INT NOT NULL,
                    isbn VARCHAR(30) NOT NULL UNIQUE,
                    publisher VARCHAR(255)
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS visitor_books (
                    visitor_id INT REFERENCES visitors(id) ON DELETE CASCADE,
                    book_id INT REFERENCES books(id) ON DELETE CASCADE,
                    PRIMARY KEY (visitor_id, book_id)
                )
            """);
        }

        ObjectMapper mapper = new ObjectMapper();
        List<VisitorDto> visitors;
        try (InputStream is = Main.class.getClassLoader().getResourceAsStream("books.json")) {
            visitors = mapper.readValue(is, new TypeReference<>() {});
        }

        String mergeVisitorSql = """
            MERGE INTO visitors (name, surname, phone, subscribed)
            KEY (name, surname)
            VALUES (?, ?, ?, ?)
        """;
        String mergeBookSql = """
            MERGE INTO books (name, author, publishing_year, isbn, publisher)
            KEY (isbn)
            VALUES (?, ?, ?, ?, ?)
        """;
        String linkSql = """
            MERGE INTO visitor_books (visitor_id, book_id)
            KEY (visitor_id, book_id)
            VALUES (
                (SELECT id FROM visitors WHERE name = ? AND surname = ?),
                (SELECT id FROM books WHERE isbn = ?)
            )
        """;

        try (PreparedStatement psVisitor = conn.prepareStatement(mergeVisitorSql);
             PreparedStatement psBook = conn.prepareStatement(mergeBookSql);
             PreparedStatement psLink = conn.prepareStatement(linkSql)) {

            for (VisitorDto v : visitors) {
                psVisitor.setString(1, v.name());
                psVisitor.setString(2, v.surname());
                psVisitor.setString(3, v.phone());
                psVisitor.setBoolean(4, v.subscribed());
                psVisitor.executeUpdate();

                if (v.favoriteBooks() != null) {
                    for (BookDto b : v.favoriteBooks()) {
                        psBook.setString(1, b.name());
                        psBook.setString(2, b.author());
                        psBook.setInt(3, b.publishingYear());
                        psBook.setString(4, b.isbn());
                        psBook.setString(5, b.publisher());
                        psBook.executeUpdate();

                        psLink.setString(1, v.name());
                        psLink.setString(2, v.surname());
                        psLink.setString(3, b.isbn());
                        psLink.executeUpdate();
                    }
                }
            }
        }
        System.out.println("Уникальные посетители и книги загружены в БД.");
    }

    // № 5. Сортировка книг по году издания
    private static void task5SortBooksByYear(Connection conn) throws SQLException {
        System.out.println("\n=== № 5. Книги, отсортированные по году издания ===");
        String sql = "SELECT name, author, publishing_year, isbn FROM books ORDER BY publishing_year ASC";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                System.out.printf("%d — \"%s\" (%s, ISBN: %s)%n",
                        rs.getInt("publishing_year"),
                        rs.getString("name"),
                        rs.getString("author"),
                        rs.getString("isbn"));
            }
        }
    }

    // № 6. Книги до 2000 года
    private static void task6BooksBefore2000(Connection conn) throws SQLException {
        System.out.println("\n=== № 6. Книги, изданные до 2000 года ===");
        String sql = "SELECT name, author, publishing_year FROM books WHERE publishing_year < 2000 ORDER BY publishing_year";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                System.out.printf("%d — \"%s\" (%s)%n",
                        rs.getInt("publishing_year"),
                        rs.getString("name"),
                        rs.getString("author"));
            }
        }
    }

    // № 7. Добавление информации о себе и любимых книг
    private static void task7AddSelfAndBooks(Connection conn) throws SQLException {
        System.out.println("\n=== № 7. Добавление информации о себе и любимых книг ===");
        String insertVisitor = "INSERT INTO visitors (name, surname, phone, subscribed) VALUES (?, ?, ?, ?)";
        int myVisitorId;
        try (PreparedStatement ps = conn.prepareStatement(insertVisitor, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, "Александр");
            ps.setString(2, "Синяков");
            ps.setString(3, "+7-900-000-00-00");
            ps.setBoolean(4, true);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                myVisitorId = keys.getInt(1);
            }
        }

        String mergeBook = """
            MERGE INTO books (name, author, publishing_year, isbn, publisher)
            KEY (isbn)
            VALUES (?, ?, ?, ?, ?)
        """;
        String linkBook = """
            INSERT INTO visitor_books (visitor_id, book_id)
            VALUES (?, (SELECT id FROM books WHERE isbn = ?))
        """;

        List<BookDto> myBooks = List.of(
                new BookDto("Мастер и Маргарита", "Михаил Булгаков", 1967, "9785170878888", "АСТ"),
                new BookDto("Clean Code", "Robert C. Martin", 2008, "9780132350884", "Prentice Hall")
        );

        try (PreparedStatement psBook = conn.prepareStatement(mergeBook);
             PreparedStatement psLink = conn.prepareStatement(linkBook)) {
            for (BookDto b : myBooks) {
                psBook.setString(1, b.name());
                psBook.setString(2, b.author());
                psBook.setInt(3, b.publishingYear());
                psBook.setString(4, b.isbn());
                psBook.setString(5, b.publisher());
                psBook.executeUpdate();

                psLink.setInt(1, myVisitorId);
                psLink.setString(2, b.isbn());
                psLink.executeUpdate();
            }
        }

        String selectSelfSql = """
            SELECT v.name AS v_name, v.surname, v.phone, b.name AS b_name, b.author, b.publishing_year
            FROM visitors v
            JOIN visitor_books vb ON v.id = vb.visitor_id
            JOIN books b ON b.id = vb.book_id
            WHERE v.id = ?
        """;
        try (PreparedStatement ps = conn.prepareStatement(selectSelfSql)) {
            ps.setInt(1, myVisitorId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.printf("Посетитель: %s %s (%s) | Книга: \"%s\" — %s (%d)%n",
                            rs.getString("v_name"),
                            rs.getString("surname"),
                            rs.getString("phone"),
                            rs.getString("b_name"),
                            rs.getString("author"),
                            rs.getInt("publishing_year"));
                }
            }
        }
    }

    // № 8. Удаление созданных таблиц посетителей и книг
    private static void task8DropTables(Connection conn) throws SQLException {
        System.out.println("\n=== № 8. Удаление таблиц ===");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS visitor_books");
            stmt.execute("DROP TABLE IF EXISTS books");
            stmt.execute("DROP TABLE IF EXISTS visitors");
            System.out.println("Таблицы visitor_books, books и visitors успешно удалены.");
        }
    }
}