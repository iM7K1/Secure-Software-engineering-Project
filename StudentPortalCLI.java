import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Pattern;

public class StudentPortalCLI {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]{3,32}$");
    private static final Pattern GRADE_PATTERN = Pattern.compile("^(A\\+|A|B\\+|B|C\\+|C|D|F)$");
    private static final int MAX_FAILED_ATTEMPTS = 4;
    private static final long LOCKOUT_SECONDS = 30L;

    private final Map<String, User> users = new LinkedHashMap<String, User>();
    private final Map<String, String> grades = new LinkedHashMap<String, String>();
    private final Map<String, LoginAttemptState> loginAttempts = new LinkedHashMap<String, LoginAttemptState>();
    private final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        new StudentPortalCLI().run();
    }

    private void run() {
        seedData();

        while (true) {
            System.out.println("\n=== Online Secure Student Information System ===");
            System.out.println("1. Create account");
            System.out.println("2. Login");
            System.out.println("3. Exit");
            System.out.print("Choose: ");
            String choice = scanner.nextLine().trim();

            if ("1".equals(choice)) {
                createAccount();
            } else if ("2".equals(choice)) {
                login();
            } else if ("3".equals(choice)) {
                System.out.println("Goodbye.");
                return;
            } else {
                System.out.println("Invalid choice.");
            }
        }
    }

    private void seedData() {
        users.put("student1", new User("student1", hashPassword("SecurePass123!"), "student"));
        users.put("instructor1", new User("instructor1", hashPassword("InstructorPass123!"), "instructor"));
        grades.put("student1:SWE401", "A");
    }

    private void createAccount() {
        System.out.print("Enter username: ");
        String username = scanner.nextLine().trim();
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            System.out.println("Invalid username format.");
            return;
        }
        if (users.containsKey(username)) {
            System.out.println("Username already exists.");
            return;
        }

        System.out.print("Enter password: ");
        String password = scanner.nextLine().trim();
        if (password.length() < 12) {
            System.out.println("Password must be at least 12 characters.");
            return;
        }

        System.out.print("Enter role (student/instructor): ");
        String role = scanner.nextLine().trim().toLowerCase();
        if (!"student".equals(role) && !"instructor".equals(role)) {
            System.out.println("Invalid role.");
            return;
        }

        users.put(username, new User(username, hashPassword(password), role));
        System.out.println("Account created successfully.");
    }

    private void login() {
        System.out.print("Username: ");
        String username = scanner.nextLine().trim();
        System.out.print("Password: ");
        String password = scanner.nextLine().trim();

        LoginAttemptState attemptState = getAttemptState(username);

        if (attemptState.lockedUntil != null && Instant.now().isBefore(attemptState.lockedUntil)) {
            long remaining = attemptState.lockedUntil.getEpochSecond() - Instant.now().getEpochSecond();
            if (remaining < 1) {
                remaining = 1;
            }
            System.out.println("Account is locked. Try again after " + remaining + " seconds.");
            return;
        }

        User user = users.get(username);
        boolean validUsername = USERNAME_PATTERN.matcher(username).matches();
        boolean loginSuccess = validUsername && user != null && user.passwordHash.equals(hashPassword(password));

        if (!loginSuccess) {
            int remainingAttempts = registerFailedAttempt(attemptState);
            if (remainingAttempts > 0) {
                System.out.println("Invalid username or password. Remaining attempts: " + remainingAttempts);
                return;
            }
            System.out.println("Invalid username or password.");
            return;
        }

        loginAttempts.remove(username);

        System.out.println("Login successful.");
        if ("student".equals(user.role)) {
            studentMenu(user);
        } else {
            instructorMenu(user);
        }
    }

    private void studentMenu(User user) {
        while (true) {
            System.out.println("\n=== Student Menu ===");
            System.out.println("1. View grades");
            System.out.println("2. Update account password");
            System.out.println("3. Logout");
            System.out.print("Choose: ");
            String choice = scanner.nextLine().trim();

            if ("1".equals(choice)) {
                viewGrades(user.username);
            } else if ("2".equals(choice)) {
                updatePassword(user);
            } else if ("3".equals(choice)) {
                return;
            } else {
                System.out.println("Invalid choice.");
            }
        }
    }

    private void instructorMenu(User user) {
        while (true) {
            System.out.println("\n=== Instructor Menu ===");
            System.out.println("1. Post or update grades");
            System.out.println("2. Update account password");
            System.out.println("3. Logout");
            System.out.print("Choose: ");
            String choice = scanner.nextLine().trim();

            if ("1".equals(choice)) {
                postOrUpdateGrade();
            } else if ("2".equals(choice)) {
                updatePassword(user);
            } else if ("3".equals(choice)) {
                return;
            } else {
                System.out.println("Invalid choice.");
            }
        }
    }

    private void viewGrades(String username) {
        System.out.println("\nYour Grades:");
        boolean found = false;
        for (Map.Entry<String, String> entry : grades.entrySet()) {
            String[] parts = entry.getKey().split(":");
            if (parts[0].equals(username)) {
                System.out.println(parts[1] + " -> " + entry.getValue());
                found = true;
            }
        }
        if (!found) {
            System.out.println("No grades found.");
        }
    }

    private void postOrUpdateGrade() {
        System.out.print("Student username: ");
        String studentUsername = scanner.nextLine().trim();
        if (!users.containsKey(studentUsername) || !"student".equals(users.get(studentUsername).role)) {
            System.out.println("Student not found.");
            return;
        }

        System.out.print("Course code: ");
        String courseCode = scanner.nextLine().trim().toUpperCase();
        if (courseCode.isEmpty()) {
            System.out.println("Course code is required.");
            return;
        }

        System.out.print("Grade (A+, A, B+, B, C+, C, D, F): ");
        String grade = scanner.nextLine().trim().toUpperCase();
        if (!GRADE_PATTERN.matcher(grade).matches()) {
            System.out.println("Invalid grade.");
            return;
        }

        grades.put(studentUsername + ":" + courseCode, grade);
        System.out.println("Grade saved successfully.");
    }

    private void updatePassword(User user) {
        System.out.print("Enter new password: ");
        String newPassword = scanner.nextLine().trim();
        if (newPassword.length() < 12) {
            System.out.println("Password must be at least 12 characters.");
            return;
        }
        user.passwordHash = hashPassword(newPassword);
        System.out.println("Password updated successfully.");
    }

    private LoginAttemptState getAttemptState(String username) {
        String key = username == null ? "" : username;
        if (!loginAttempts.containsKey(key)) {
            loginAttempts.put(key, new LoginAttemptState());
        }
        return loginAttempts.get(key);
    }

    private int registerFailedAttempt(LoginAttemptState attemptState) {
        if (attemptState.lockedUntil != null && Instant.now().isBefore(attemptState.lockedUntil)) {
            return 0;
        }

        attemptState.failedAttempts += 1;
        if (attemptState.failedAttempts >= MAX_FAILED_ATTEMPTS) {
            attemptState.failedAttempts = 0;
            attemptState.lockedUntil = Instant.now().plusSeconds(LOCKOUT_SECONDS);
            System.out.println("Too many failed attempts. Account locked for 30 seconds.");
            return 0;
        }

        return MAX_FAILED_ATTEMPTS - attemptState.failedAttempts;
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(password.getBytes());
            StringBuilder builder = new StringBuilder();
            for (byte value : hashed) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private static class User {
        private final String username;
        private String passwordHash;
        private final String role;
        
        private User(String username, String passwordHash, String role) {
            this.username = username;
            this.passwordHash = passwordHash;
            this.role = role;
        }
    }

    private static class LoginAttemptState {
        private int failedAttempts;
        private Instant lockedUntil;

        private LoginAttemptState() {
            this.failedAttempts = 0;
            this.lockedUntil = null;
        }
    }
}
