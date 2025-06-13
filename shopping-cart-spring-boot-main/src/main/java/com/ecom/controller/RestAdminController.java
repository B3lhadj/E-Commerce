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
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin API", description = "Endpoints for admin management of categories, products, users, and orders")
public class RestAdminController {

    private static final Logger logger = LoggerFactory.getLogger(RestAdminController.class);
    private static final String UPLOAD_DIR = "uploads/";

    @Autowired private CategoryService categoryService;
    @Autowired private ProductService productService;
    @Autowired private UserService userService;
    @Autowired private OrderService orderService;
    @Autowired private CartService cartService;

    // ========== CATEGORY ENDPOINTS ==========
    @GetMapping("/categories")
    @Operation(summary = "Get all categories", description = "Retrieves a paginated list of categories")
    @ApiResponse(responseCode = "200", description = "Categories retrieved successfully")
    public ResponseEntity<Page<Category>> getAllCategories(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {
        logger.info("Fetching categories: page={}, size={}", page, size);
        return ResponseEntity.ok(categoryService.getAllCategorPagination(page, size));
    }

    @PostMapping(value = "/categories", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    @Operation(summary = "Create category", description = "Creates a new category with name, status, and image")
    @ApiResponse(responseCode = "200", description = "Category created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid input")
    public ResponseEntity<?> createCategory(
            @Parameter(description = "Category name") @RequestParam String name,
            @Parameter(description = "Active status") @RequestParam boolean isActive,
            @Parameter(description = "Category image file") @RequestPart MultipartFile image) {
        logger.info("Creating category: name={}, isActive={}", name, isActive);

        if (ObjectUtils.isEmpty(name)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Category name is required"));
        }
        if (image == null || image.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Category image is required"));
        }

        try {
            // Save image file
            String fileName = UUID.randomUUID() + "_" + image.getOriginalFilename();
            Path filePath = Paths.get(UPLOAD_DIR, fileName);
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, image.getBytes());

            Category category = new Category();
            category.setName(name.trim());
            category.setIsActive(isActive);
            category.setImageName(fileName);

            // Check for duplicate name


            Category savedCategory = categoryService.saveCategory(category);
            return ResponseEntity.ok(savedCategory);
        } catch (IOException e) {
            logger.error("Error saving category image: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to save image: " + e.getMessage()));
        }
    }

    @PutMapping(value = "/categories/{id}", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    @Operation(summary = "Update category", description = "Updates an existing category with optional image")
    @ApiResponse(responseCode = "200", description = "Category updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid input")
    @ApiResponse(responseCode = "404", description = "Category not found")
    public ResponseEntity<?> updateCategory(
            @Parameter(description = "Category ID") @PathVariable int id,
            @Parameter(description = "Category name") @RequestParam String name,
            @Parameter(description = "Active status") @RequestParam boolean isActive,
            @Parameter(description = "Category image file (optional)") @RequestPart(required = false) MultipartFile image) {
        logger.info("Updating category: id={}, name={}, isActive={}", id, name, isActive);

        Category existing = categoryService.getCategoryById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Category not found"));
        }

        if (ObjectUtils.isEmpty(name)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Category name is required"));
        }

        try {
            existing.setName(name.trim());
            existing.setIsActive(isActive);

            // Update image if provided
            if (image != null && !image.isEmpty()) {
                String fileName = UUID.randomUUID() + "_" + image.getOriginalFilename();
                Path filePath = Paths.get(UPLOAD_DIR, fileName);
                Files.createDirectories(filePath.getParent());
                Files.write(filePath, image.getBytes());
                existing.setImageName(fileName);
            }

            // Check for duplicate name (excluding current category)


            // Check if category is used by products


            Category updatedCategory = categoryService.saveCategory(existing);
            return ResponseEntity.ok(updatedCategory);
        } catch (IOException e) {
            logger.error("Error updating category image: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to update image: " + e.getMessage()));
        }
    }

    @DeleteMapping("/categories/{id}")
    @Operation(summary = "Delete category", description = "Deletes a category by ID")
    @ApiResponse(responseCode = "204", description = "Category deleted successfully")
    @ApiResponse(responseCode = "400", description = "Category is in use")
    @ApiResponse(responseCode = "404", description = "Category not found")
    public ResponseEntity<?> deleteCategory(@Parameter(description = "Category ID") @PathVariable int id) {
        logger.info("Deleting category: id={}", id);

        Category category = categoryService.getCategoryById(id);
        if (category == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Category not found"));
        }



        try {
            categoryService.deleteCategory(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting category {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to delete category: " + e.getMessage()));
        }
    }

    // ========== PRODUCT ENDPOINTS ==========
    @GetMapping("/products")
    @Operation(summary = "Get all products", description = "Retrieves a paginated list of products with optional search")
    @ApiResponse(responseCode = "200", description = "Products retrieved successfully")
    public ResponseEntity<Page<Product>> getAllProducts(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Search term (optional)") @RequestParam(required = false) String search) {
        logger.info("Fetching products: page={}, size={}, search={}", page, size, search);
        if (search != null && !search.isEmpty()) {
            return ResponseEntity.ok(productService.searchProductPagination(page, size, search));
        }
        return ResponseEntity.ok(productService.getAllProductsPagination(page, size));
    }

    @PostMapping("/products")
    @Operation(summary = "Create product", description = "Creates a new product")
    @ApiResponse(responseCode = "200", description = "Product created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid input")
    public ResponseEntity<?> createProduct(@RequestBody Product product) {
        logger.info("Creating product: title={}", product.getTitle());





        try {
            Product savedProduct = productService.saveProduct(product);
            return ResponseEntity.ok(savedProduct);
        } catch (Exception e) {
            logger.error("Error creating product: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to create product: " + e.getMessage()));
        }
    }

    @PutMapping("/products/{id}")
    @Operation(summary = "Update product", description = "Updates an existing product")
    @ApiResponse(responseCode = "200", description = "Product updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid input")
    @ApiResponse(responseCode = "404", description = "Product not found")
    public ResponseEntity<?> updateProduct(
            @Parameter(description = "Product ID") @PathVariable int id,
            @RequestBody Product product) {
        logger.info("Updating product: id={}", id);

        Product existing = productService.getProductById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Product not found"));
        }





        try {
            existing.setTitle(product.getTitle());
            existing.setDescription(product.getDescription());
            existing.setPrice(product.getPrice());
            existing.setDiscount(product.getDiscount());
            existing.setDiscountPrice(product.getPrice() * (100 - product.getDiscount()) / 100);

            Product updatedProduct = productService.saveProduct(existing);
            return ResponseEntity.ok(updatedProduct);
        } catch (Exception e) {
            logger.error("Error updating product {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to update product: " + e.getMessage()));
        }
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable int id) {
        logger.info("Deleting product: id={}", id);

        Product product = productService.getProductById(id);
        if (product == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Product not found"));
        }

      /* // Check for active orders or cart items
        if (orderService.existsByProductId(id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot delete product: Used in active orders"));
        }
        if (cartService.existsByProductId(id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot delete product: Used in carts"));
        }*/

        try {
            productService.deleteProduct(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting product {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to delete product: " + e.getMessage()));
        }
    }

    // ========== USER MANAGEMENT ==========
    @GetMapping("/users")
    @Operation(summary = "Get users by role", description = "Retrieves users by role")
    @ApiResponse(responseCode = "200", description = "Users retrieved successfully")
    public ResponseEntity<List<UserDtls>> getUsersByRole(
            @Parameter(description = "User role") @RequestParam String role) {
        logger.info("Fetching users: role={}", role);
        return ResponseEntity.ok(userService.getUsers(role));
    }

    @PutMapping("/users/{id}/status")
    @Operation(summary = "Update user status", description = "Updates a user's account status")
    @ApiResponse(responseCode = "204", description = "User status updated successfully")
    public ResponseEntity<Void> updateUserStatus(
            @Parameter(description = "User ID") @PathVariable int id,
            @Parameter(description = "Account status") @RequestParam boolean status) {
        logger.info("Updating user status: id={}, status={}", id, status);
        userService.updateAccountStatus(id, status);
        return ResponseEntity.noContent().build();
    }

    // ========== ORDER MANAGEMENT ==========
    @GetMapping("/orders")
    @Operation(summary = "Get all orders", description = "Retrieves a paginated list of orders")
    @ApiResponse(responseCode = "200", description = "Orders retrieved successfully")
    public ResponseEntity<Page<ProductOrder>> getAllOrders(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {
        logger.info("Fetching orders: page={}, size={}", page, size);
        return ResponseEntity.ok(orderService.getAllOrdersPagination(page, size));
    }

    @PutMapping("/orders/{id}/status")
    @Operation(summary = "Update order status", description = "Updates the status of an order")
    @ApiResponse(responseCode = "200", description = "Order status updated successfully")
    public ResponseEntity<ProductOrder> updateOrderStatus(
            @Parameter(description = "Order ID") @PathVariable int id,
            @Parameter(description = "New status") @RequestParam String status) {
        logger.info("Updating order status: id={}, status={}", id, status);
        return ResponseEntity.ok(orderService.updateOrderStatus(id, status));
    }

    @GetMapping("/orders/search")
    @Operation(summary = "Search order", description = "Retrieves an order by order ID")
    @ApiResponse(responseCode = "200", description = "Order retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Order not found")
    public ResponseEntity<?> searchOrder(
            @Parameter(description = "Order ID") @RequestParam String orderId) {
        logger.info("Searching order: orderId={}", orderId);
        ProductOrder order = orderService.getOrdersByOrderId(orderId);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Order not found"));
        }
        return ResponseEntity.ok(order);
    }
}