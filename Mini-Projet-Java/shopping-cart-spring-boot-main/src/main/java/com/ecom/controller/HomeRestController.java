package com.ecom.controller;

import com.ecom.model.*;
import com.ecom.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@Tag(name = "Home API", description = "Endpoints for categories, products, user registration, and authentication")
public class HomeRestController {

    private static final Logger logger = LoggerFactory.getLogger(HomeRestController.class);
    private static final String UPLOAD_DIR = "static/img/profile_img/";

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ProductService productService;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;



    // Get all active categories
    @GetMapping("/categories")
    @Operation(summary = "Get active categories", description = "Retrieves all active categories")
    @ApiResponse(responseCode = "200", description = "Categories retrieved successfully")
    public ResponseEntity<List<Category>> getActiveCategories() {
        logger.info("Fetching active categories");
        return ResponseEntity.ok(categoryService.getAllActiveCategory());
    }

    // Get paginated products
    @GetMapping("/products")
    @Operation(summary = "Get products", description = "Retrieves paginated active products by category")
    @ApiResponse(responseCode = "200", description = "Products retrieved successfully")
    public ResponseEntity<Page<Product>> getProducts(
            @Parameter(description = "Category name (optional)") @RequestParam(defaultValue = "") String category,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "12") int size) {
        logger.info("Fetching products: category={}, page={}, size={}", category, page, size);
        return ResponseEntity.ok(productService.getAllActiveProductPagination(page, size, category));
    }

    // Get product by ID
    @GetMapping("/products/{id}")
    @Operation(summary = "Get product", description = "Retrieves a product by ID")
    @ApiResponse(responseCode = "200", description = "Product retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Product not found")
    public ResponseEntity<?> getProduct(@Parameter(description = "Product ID") @PathVariable int id) {
        logger.info("Fetching product: id={}", id);
        Product product = productService.getProductById(id);
        if (product == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Product not found"));
        }
        return ResponseEntity.ok(product);
    }

    // Search products
    @GetMapping("/products/search")
    @Operation(summary = "Search products", description = "Searches products by query")
    @ApiResponse(responseCode = "200", description = "Products retrieved successfully")
    public ResponseEntity<List<Product>> searchProducts(
            @Parameter(description = "Search query") @RequestParam String query) {
        logger.info("Searching products: query={}", query);
        return ResponseEntity.ok(productService.searchProduct(query));
    }

    // User registration
    @PostMapping("/register")
    @Operation(summary = "Register user", description = "Registers a new user with email, password, and name")
    @ApiResponse(responseCode = "200", description = "User registered successfully")
    @ApiResponse(responseCode = "400", description = "Invalid input or email already exists")
    public ResponseEntity<?> registerUser(
            @Parameter(description = "User details (JSON)") @RequestBody Map<String, String> userRequest) {
        logger.info("Registering user: email={}", userRequest.get("email"));

        // Validate required fields
        String email = userRequest.get("email");
        String password = userRequest.get("password");
        String name = userRequest.get("name");
        if (ObjectUtils.isEmpty(email) || ObjectUtils.isEmpty(password) || ObjectUtils.isEmpty(name)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Email, password, and name are required"));
        }

        // Check for duplicate email
        if (userService.existsEmail(email)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Email already exists"));
        }

        try {
            UserDtls user = new UserDtls();
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(password));
            user.setName(name);
            user.setProfileImage("default.jpg");
            user.setRole("ROLE_USER");

            UserDtls savedUser = userService.saveUser(user);
            return ResponseEntity.ok(savedUser);
        } catch (Exception e) {
            logger.error("Error registering user: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Failed to register user: " + e.getMessage()));
        }
    }


    // User login
    @PostMapping("/login")
    @Operation(summary = "Login user", description = "Authenticates a user and returns a JWT token")
    @ApiResponse(responseCode = "200", description = "Login successful")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    public ResponseEntity<?> loginUser(
            @Parameter(description = "Login credentials (JSON)") @RequestBody Map<String, String> loginRequest) {
        logger.info("Logging in user: email={}", loginRequest.get("email"));

        // Validate required fields
        String email = loginRequest.get("email");
        String password = loginRequest.get("password");
        if (ObjectUtils.isEmpty(email) || ObjectUtils.isEmpty(password)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Email and password are required"));
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );

            // Generate JWT token
            UserDtls user = userService.getUserByEmail(email);

            return ResponseEntity.ok(Map.of(
                    "user", user
            ));
        } catch (AuthenticationException e) {
            logger.error("Invalid login credentials: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid email or password"));
        }
    }

    // Forgot password - initiate reset
    @PostMapping("/forgot-password")
    @Operation(summary = "Initiate password reset", description = "Generates a password reset token for the user")
    @ApiResponse(responseCode = "200", description = "Reset token generated")
    @ApiResponse(responseCode = "400", description = "Invalid email")
    public ResponseEntity<?> forgotPassword(
            @Parameter(description = "User email (JSON)") @RequestBody Map<String, String> request) {
        String email = request.get("email");
        logger.info("Initiating password reset: email={}", email);

        if (ObjectUtils.isEmpty(email)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Email is required"));
        }

        UserDtls user = userService.getUserByEmail(email);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid email"));
        }

        String token = UUID.randomUUID().toString();
        userService.updateUserResetToken(email, token);
        // TODO: Send reset token via email (not implemented)
        return ResponseEntity.ok(Map.of("message", "Password reset token generated", "token", token));
    }

    // Reset password with token
    @PostMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Resets the user password using a reset token")
    @ApiResponse(responseCode = "200", description = "Password reset successfully")
    @ApiResponse(responseCode = "400", description = "Invalid token or password")
    public ResponseEntity<?> resetPassword(
            @Parameter(description = "Reset details (JSON)") @RequestBody Map<String, String> request) {
        String token = request.get("token");
        String newPassword = request.get("newPassword");
        logger.info("Resetting password: token={}", token);

        if (ObjectUtils.isEmpty(token) || ObjectUtils.isEmpty(newPassword)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Token and new password are required"));
        }

        UserDtls user = userService.getUserByToken(token);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid or expired token"));
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        userService.updateUser(user);
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }
}