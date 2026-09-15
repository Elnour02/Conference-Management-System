import java.sql.*;
import java.time.LocalDate;
import java.util.Scanner;

public class ConferenceApp {

    private final String URL = "jdbc:postgresql://localhost:5432/example_database";
    private final String USER = "example_user";
    private final String PASSWORD = "example_password";

    private Connection conn;
    private Scanner sc;

    private String admin = "admin";
    private String adminPassword = "1234";
    
    LocalDate localDate = LocalDate.of(2024, 1, 15); 
    Date todaysDate = Date.valueOf(localDate);

    public ConferenceApp() throws SQLException {
        conn = DriverManager.getConnection(URL, USER, PASSWORD);
        sc = new Scanner(System.in);
    }

    public static void main(String[] args) {
        try {
            ConferenceApp app = new ConferenceApp();
            boolean running = true;
            while (running) {
                System.out.println();
                System.out.print("Enter as:\nAdmin (1)\nAuthor (2)\nReviewer (3)\nExit (any key): ");
                String role = app.sc.nextLine();

                switch (role) {
                    case "1":
                        System.out.println();
                        app.handleAdmin();
                        break;

                    case "2":
                        System.out.println();
                        app.handleAuthor();
                        break;

                    case "3":
                        System.out.println();
                        app.handleReviewer();
                        break;

                    default:
                        running = false;
                }
            }
            app.sc.close();
        } 
        catch (SQLException e) {
            System.out.println("Database connection failed.");
            System.out.println("Message: " + e.getMessage());
        }
    }

//---------------------------------------------------------------------------------------------------------------------------
// ADMIN
//---------------------------------------------------------------------------------------------------------------------------

    private void handleAdmin() {    
        System.out.print("Enter username for admin: ");
        String name = sc.nextLine();
        System.out.print("Enter password for admin: ");
        String password = sc.nextLine();
        if (!name.equals(admin) || !password.equals(adminPassword)) {
            System.out.println("Name or password is wrong.");
            return;
        }
        System.out.println("Login successful.");
        
        boolean loggedIn = true;
        while (loggedIn) {
            System.out.println();
            System.out.print("Add submission period (1)\nAdd reviewer (2)\nRemove reviewer (3)\nAssign reviewers (4)\nList current year articles (5)\nSearch for articles (6)\nLogout (any key)\n:");
            String choice = sc.nextLine();

            switch (choice) {
                case "1":
                    System.out.println();
                    addSubmissionPeriod();
                    break;

                case "2":
                    System.out.println();
                    addReviewer();
                    break;

                case "3":
                    System.out.println();
                    removeReviewer();
                    break;

                case "4":
                    System.out.println();
                    assignReviewers();
                    break;

                case "5":
                    System.out.println();
                    listArticles();
                    break;

                case "6":
                    System.out.println();
                    searchArticles();
                    break;

                default:
                    loggedIn = false;
            }
        }
    }

    private void addSubmissionPeriod() {
        try {
            System.out.print("Enter start date (YYYY-MM-DD): ");
            String startDate = sc.nextLine();
            System.out.print("Enter end date (YYYY-MM-DD): ");
            String endDate = sc.nextLine();

            if (startDate.isEmpty() || endDate.isEmpty()) {
                System.out.println("Start date and end date cannot be empty.");
                return;
            }

            PreparedStatement ps = conn.prepareStatement("SELECT add_submission_period(?, ?)");
            ps.setDate(1, Date.valueOf(startDate));
            ps.setDate(2, Date.valueOf(endDate));
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                int result = rs.getInt(1);
                if (result == 0) System.out.println("Submission period added successfully.");
                else if (result == 1) System.out.println("Start date must be before end date.");
                else if (result == 2) System.out.println("Submission period for this year already exists.");
            }
        } 
        catch (Exception e) {
            System.out.println("Error adding submission period: " + e.getMessage());
        }
    }

    private void addReviewer() {
        try {
            System.out.print("Reviewer ID number: ");
            String idNumber = sc.nextLine();
            System.out.print("Full name: ");
            String fullName = sc.nextLine();
            System.out.print("Phone number (leave empty to skip): ");
            String phone = sc.nextLine();
            System.out.print("Research area (leave empty to skip): ");
            String research = sc.nextLine();

            if (idNumber.isEmpty() || fullName.isEmpty()) {
                System.out.println("ID number and full name cannot be empty.");
                return;
            }

            PreparedStatement ps = conn.prepareStatement("SELECT add_reviewer(?, ?, ?, ?)");
            ps.setString(1, idNumber);
            ps.setString(2, fullName);
            if (phone.isEmpty()) ps.setNull(3, java.sql.Types.VARCHAR);
            else ps.setString(3, phone);
            if (research.isEmpty()) ps.setNull(4, java.sql.Types.VARCHAR);
            else ps.setString(4, research);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                boolean success = rs.getBoolean(1);
                if (success) System.out.println("Reviewer added successfully.");
                else System.out.println("Reviewer already exists.");
            }
        } 
        catch (Exception e) {
            System.out.println("Error adding reviewer: " + e.getMessage());
        }
    }

    private void removeReviewer() {
        try {
            System.out.print("Reviewer ID: ");
            int reviewerId = Integer.parseInt(sc.nextLine());

            PreparedStatement ps = conn.prepareStatement("SELECT remove_reviewer(?)");
            ps.setInt(1, reviewerId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                int result = rs.getInt(1);
                if (result == 0) System.out.println("Reviewer deactivated successfully.");
                else if (result == 1) System.out.println("Reviewer already inactive.");
                else if (result == 2) System.out.println("Reviewer does not exist.");
            }
        } 
        catch (Exception e) {
            System.out.println("Error removing reviewer: " + e.getMessage());
        }
    }

    private void assignReviewers() {
        try {
            PreparedStatement check = conn.prepareStatement("SELECT is_submission_open(?)");
            check.setDate(1, todaysDate);
            ResultSet rs = check.executeQuery();
            rs.next();

            if (rs.getBoolean(1)) {
                System.out.println("Submission period is still open.");
                return;
            }

            System.out.print("Article ID: ");
            int articleId = Integer.parseInt(sc.nextLine());
            System.out.print("First reviewer ID: ");
            int reviewer1 = Integer.parseInt(sc.nextLine());
            System.out.print("Second reviewer ID: ");
            int reviewer2 = Integer.parseInt(sc.nextLine());

            PreparedStatement ps = conn.prepareStatement("SELECT assign_reviewers(?, ?, ?)");
            ps.setInt(1, articleId);
            ps.setInt(2, reviewer1);
            ps.setInt(3, reviewer2);
            rs = ps.executeQuery();

            if (rs.next()) {
                int result = rs.getInt(1);
                if (result == 0) System.out.println("Reviewers assigned successfully.");
                else if (result == 1) System.out.println("Article already has 2 reviewers.");
                else if (result == 2) System.out.println("Cannot assign the same reviewer twice.");
                else if (result == 3) System.out.println("One of the reviewers is already assigned.");
                else if (result == 4) System.out.println("One of the reviewers is inactive.");
            }
        } 
        catch (Exception e) {
            System.out.println("Error assigning reviewers: " + e.getMessage());
        }
    }

    private void listArticles() {
        try {
            PreparedStatement ps = conn.prepareStatement(
                "SELECT article_id, title, status, year, first_name, last_name " +
                "FROM articles " +
                "JOIN authors ON author = author_id " +
                "WHERE year = EXTRACT(YEAR FROM ?::date) " +
                "ORDER BY article_id"
            );
            ps.setDate(1, todaysDate);
            ResultSet rs = ps.executeQuery();

            System.out.println("Articles for year " + localDate.getYear() + ":");
            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.printf("ID: %d | Title: %s | Author: %s %s | Status: %s%n",
                    rs.getInt("article_id"),
                    rs.getString("title"),
                    rs.getString("first_name"),
                    rs.getString("last_name"),
                    rs.getString("status"),
                    rs.getInt("year")
                );
            }
            if (!found) System.out.println("No articles found.");
        } 
        catch (Exception e) {
            System.out.println("Error listing articles: " + e.getMessage());
        }
    }

    private void searchArticles() {
        try {
            System.out.println("Leave field empty to skip it.");
            System.out.print("Title: ");
            String title = sc.nextLine().trim();
            System.out.print("Author first name: ");
            String firstName = sc.nextLine().trim();
            System.out.print("Author last name: ");
            String lastName = sc.nextLine().trim();
            System.out.print("Year: ");
            String yearInput = sc.nextLine().trim();
            System.out.print("Article type (short_article, full_article, poster): ");
            String type = sc.nextLine().trim();
            System.out.print("Status (submitted, under_review, accepted, rejected): ");
            String status = sc.nextLine().trim();

            PreparedStatement ps = conn.prepareStatement(
                "SELECT article_id, title, year, type, status, " + 
                "first_name, last_name " + 
                "FROM articles " + 
                "JOIN authors ON author = author_id " + 
                "WHERE (? = '' OR title = ?) " + 
                "AND (? = '' OR first_name = ?) " + 
                "AND (? = '' OR last_name = ?) " + 
                "AND (? = '' OR year = ?::int) " + 
                "AND (? = '' OR type::text = ?) " + 
                "AND (? = '' OR status::text = ?) " + 
                "ORDER BY year DESC, article_id"
            );
            ps.setString(1, title);
            ps.setString(2, title);
            ps.setString(3, firstName);
            ps.setString(4, firstName);
            ps.setString(5, lastName);
            ps.setString(6, lastName);
            ps.setString(7, yearInput);
            ps.setString(8, yearInput);
            ps.setString(9, type);
            ps.setString(10, type);
            ps.setString(11, status);
            ps.setString(12, status);
            ResultSet rs = ps.executeQuery();

            System.out.println("Search results:");
            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.printf("ID: %d | Title: %s | Author: %s %s | Year: %d | Type: %s | Status: %s%n",
                    rs.getInt("article_id"),
                    rs.getString("title"),
                    rs.getString("first_name"),
                    rs.getString("last_name"),
                    rs.getInt("year"),
                    rs.getString("type"),
                    rs.getString("status")
                );
            }
            if (!found) System.out.println("No articles found.");
        } 
        catch (Exception e) {
            System.out.println("Error searching articles: " + e.getMessage());
        }
    }

//---------------------------------------------------------------------------------------------------------------------------
// AUTHOR
//---------------------------------------------------------------------------------------------------------------------------

    private void handleAuthor() {
        int authorId = 0;

        System.out.print("Register (1)\nLogin (2)\nBack (any key): ");
        String choice = sc.nextLine();

        if (choice.equals("1")) registerAuthor();
        else if (choice.equals("2")) authorId = loginAuthor();
        else return;

        if (authorId == 0) return;

        boolean loggedIn = true;
        while (loggedIn) {
            System.out.println();
            System.out.print("View submission period (1)\nSubmit article (2)\nView my articles (3)\nLogout (any key)\n: ");
            choice = sc.nextLine();

            switch (choice) {
                case "1":
                    System.out.println();
                    viewSubmissionPeriod();
                    break;
                case "2":
                    System.out.println();
                    submitArticle(authorId);
                    break;
                case "3":
                    System.out.println();
                    listMyArticles(authorId);
                    break;
                default:
                    loggedIn = false;
            }
        }
    }

    private void registerAuthor() {
        try {
            System.out.print("First name: ");
            String first = sc.nextLine();
            System.out.print("Last name: ");
            String last = sc.nextLine();
            System.out.print("Affiliation (leave empty to skip): ");
            String affiliation = sc.nextLine();
            System.out.print("Email: ");
            String email = sc.nextLine();
            System.out.print("Phone number (leave empty to skip): ");
            String phone = sc.nextLine();

            if (first.isEmpty() || last.isEmpty() || email.isEmpty()) {
                System.out.println("First name, last name and email cannot be empty.");
                return;
            }

            PreparedStatement ps = conn.prepareStatement("SELECT register_author(?, ?, ?, ?, ?)");
            ps.setString(1, first);
            ps.setString(2, last);
            if (affiliation.isEmpty()) ps.setNull(3, java.sql.Types.VARCHAR);
            else ps.setString(3, affiliation);
            ps.setString(4, email);
            if (phone.isEmpty()) ps.setNull(5, java.sql.Types.VARCHAR);
            else ps.setString(5, phone);

            ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getBoolean(1)) System.out.println("Author registered successfully.");
            else System.out.println("Author already exists.");
        } 
        catch (Exception e) {
            System.out.println("Error registering author: " + e.getMessage());
        }
    }

    private int loginAuthor() {
        try {
            System.out.print("Email: ");
            String email = sc.nextLine();

            PreparedStatement ps = conn.prepareStatement("SELECT author_id FROM authors WHERE email = ?");
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                System.out.println("Login successful.");
                return rs.getInt("author_id");
            } 
            else {
                System.out.println("Author not found.");
                return 0;
            }
        } 
        catch (Exception e) {
            System.out.println("Error logging in: " + e.getMessage());
            return 0;
        }
    }

    private void viewSubmissionPeriod() {
        try {
            PreparedStatement ps = conn.prepareStatement("SELECT start_date, end_date FROM submission_periods WHERE year = EXTRACT(YEAR FROM ?::date)");
            ps.setDate(1, todaysDate);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                System.out.println("Submission period:");
                System.out.println("Start: " + rs.getDate("start_date"));
                System.out.println("End: " + rs.getDate("end_date"));
            } 
            else {
                System.out.println("No submission period defined for this year.");
            }
        } 
        catch (Exception e) {
            System.out.println("Error viewing submission period: " + e.getMessage());
        }
    }

    private void submitArticle(int authorId) {
        try {
            PreparedStatement check = conn.prepareStatement("SELECT is_submission_open(?)");
            check.setDate(1, todaysDate);
            ResultSet rs = check.executeQuery();
            rs.next();

            if (!rs.getBoolean(1)) {
                System.out.println("Submission period is closed.");
                return;
            }

            System.out.print("Article type (short_article, full_article, poster): ");
            String type = sc.nextLine();
            System.out.print("Title: ");
            String title = sc.nextLine();
            System.out.print("Keywords (comma separated, max 4, leave empty to skip): ");
            String keywords = sc.nextLine();
            System.out.print("Article text: ");
            String text = sc.nextLine();

            if (type.isEmpty() || title.isEmpty() || text.isEmpty()) {
                System.out.println("Article type, title and text cannot be empty.");
                return;
            }

            PreparedStatement ps = conn.prepareStatement("CALL submit_article(?, ?, ?::article_type, ?, ?, ?)");
            ps.setDate(1, todaysDate);
            ps.setInt(2, authorId);
            ps.setString(3, type);
            ps.setString(4, title);
            if (keywords.isEmpty()) ps.setNull(5, java.sql.Types.VARCHAR);
            else ps.setString(5, keywords);
            ps.setString(6, text);

            ps.execute();
            System.out.println("Article submitted successfully.");
        } 
        catch (Exception e) {
            System.out.println("Error submitting article: " + e.getMessage());
        }
    }

    private void listMyArticles(int authorId) {
        try {
            PreparedStatement ps = conn.prepareStatement(
                "SELECT article_id, title, status, comment " +
                "FROM articles " +
                "LEFT JOIN article_reviews ON article_id = article " +
                "WHERE author = ? AND year = EXTRACT(YEAR FROM ?::date)"
            );
            ps.setInt(1, authorId);
            ps.setDate(2, todaysDate);
            ResultSet rs = ps.executeQuery();

            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.printf(
                    "ID: %d | Title: %s | Status: %s",
                    rs.getInt("article_id"),
                    rs.getString("title"),
                    rs.getString("status")
                );
                System.out.print(" | Comment: ");
                if (rs.getString("comment") != null) System.out.println(rs.getString("comment"));
                else System.out.println("-");
            }
            if (!found) System.out.println("No articles found.");
        } 
        catch (Exception e) {
            System.out.println("Error listing articles: " + e.getMessage());
        }
    }

//---------------------------------------------------------------------------------------------------------------------------
// REVIEWER
//---------------------------------------------------------------------------------------------------------------------------

    private void handleReviewer() {
        int reviewerId = loginReviewer();
        if (reviewerId == 0) return;

        boolean loggedIn = true;
        while (loggedIn) {
            System.out.println();
            System.out.print("View assigned articles (1)\nView unreviewed articles (2)\nReview article (3)\nLogout (any key)\n: ");
            String choice = sc.nextLine();

            switch (choice) {
                case "1":
                    System.out.println();
                    listAssignedArticles(reviewerId);
                    break;
                case "2":
                    System.out.println();
                    listUnreviewedArticles(reviewerId);
                    break;
                case "3":
                    reviewArticle(reviewerId);
                    System.out.println();
                    break;
                default:
                    loggedIn = false;
            }
        }
    }

    private int loginReviewer() {
        try {
            System.out.print("ID number: ");
            String id = sc.nextLine();

            PreparedStatement ps = conn.prepareStatement("SELECT reviewer_id FROM reviewers WHERE id_number = ? AND active = TRUE");
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                System.out.println("Login successful.");
                return rs.getInt("reviewer_id");
            } 
            else {
                System.out.println("Reviewer not found or inactive.");
                return 0;
            }
        } 
        catch (Exception e) {
            System.out.println("Error logging in: " + e.getMessage());
            return 0;
        }
    }

    private void listAssignedArticles(int reviewerId) {
        try {
            PreparedStatement ps = conn.prepareStatement(
                "SELECT article_id, title, decision " +
                "FROM article_reviews " +
                "JOIN articles ON article = article_id " +
                "WHERE reviewer = ? AND year = EXTRACT(YEAR FROM ?::date)"
            );
            ps.setInt(1, reviewerId);
            ps.setDate(2, todaysDate);
            ResultSet rs = ps.executeQuery();

            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.printf(
                    "ID: %d | Title: %s",
                    rs.getInt("article_id"),
                    rs.getString("title"),
                    rs.getString("decision")
                );
                System.out.print(" | Decision: ");
                if (rs.getString("decision") != null) System.out.println(rs.getString("decision"));
                else System.out.println("-");
            }
            if (!found) System.out.println("No articles found.");
        } 
        catch (Exception e) {
            System.out.println("Error listing assigned articles: " + e.getMessage());
        }
    }

    private void listUnreviewedArticles(int reviewerId) {
        try {
            PreparedStatement ps = conn.prepareStatement(
                "SELECT article_id, title " +
                "FROM article_reviews " +
                "JOIN articles ON article = article_id " +
                "WHERE reviewer = ? AND decision IS NULL"
            );
            ps.setInt(1, reviewerId);
            ResultSet rs = ps.executeQuery();

            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.printf("ID: %d | Title: %s%n", rs.getInt("article_id"), rs.getString("title"));
            }
            if (!found) System.out.println("No articles found.");
        } 
        catch (Exception e) {
            System.out.println("Error searching articles: " + e.getMessage());
        }
    }

    private void reviewArticle(int reviewerId) {
        try {
            System.out.print("Article ID: ");
            int articleId = Integer.parseInt(sc.nextLine());
            System.out.print("Decision (accepted/rejected): ");
            String decision = sc.nextLine();
            System.out.print("Comment (leave empty to skip): ");
            String comment = sc.nextLine();

            if (decision.isEmpty() || (!decision.equals("accepted") && !decision.equals("rejected"))) {
                System.out.println("Decision must be accepted/rejected");
                return;
            }

            PreparedStatement ps = conn.prepareStatement("SELECT review(?, ?, ?::review_decision, ?)");
            ps.setInt(1, reviewerId);
            ps.setInt(2, articleId);
            ps.setString(3, decision);
            if (comment.isEmpty()) ps.setNull(4, java.sql.Types.VARCHAR);
            else ps.setString(4, comment);
            ResultSet rs = ps.executeQuery();
            
            if (rs.next() && rs.getBoolean(1)) {
                System.out.println("Review submitted successfully.");
            } 
            else {
                System.out.println("Article not found.");
            }
        } 
        catch (Exception e) {
            System.out.println("Error reviewing article: " + e.getMessage());
        }
    }

}

