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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@Tag(name = "User API", description = "Endpoints for user cart, orders, and profile management")
public class RestUserController {

    private static final Logger logger = LoggerFactory.getLogger(RestUserController.class);

    @Autowired private UserService userService;
    @Autowired private CartService cartService;
    @Autowired private OrderService orderService;

    // ========== CART ENDPOINTS ==========
    @PostMapping("/cart")
    @Operation(summary = "Add product to cart", description = "Adds a product to the specified user's cart")
    @ApiResponse(responseCode = "200", description = "Product added to cart successfully")
    @ApiResponse(responseCode = "400", description = "Invalid user ID or request")
    public ResponseEntity<Map<String, Object>> addToCart(
            @Parameter(description = "User ID") @RequestParam Integer userId,
            @Parameter(description = "Product ID") @RequestParam Integer productId) {
        logger.info("Received add to cart request: userId={}, productId={}", userId, productId);



        try {
            Cart cart = cartService.saveCart(productId, userId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Product added to cart",
                    "cartItem", cart
            ));
        } catch (Exception e) {
            logger.error("Error adding to cart for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/cart")
    @Operation(summary = "Get user cart", description = "Retrieves all items in the specified user's cart")
    @ApiResponse(responseCode = "200", description = "Cart items retrieved successfully")
    @ApiResponse(responseCode = "400", description = "Invalid user ID")
    public ResponseEntity<?> getCart(
            @Parameter(description = "User ID") @RequestParam Integer userId) {
        logger.info("Received get cart request: userId={}", userId);



        List<Cart> carts = cartService.getCartsByUser(userId);
        return ResponseEntity.ok(carts);
    }

    @PatchMapping("/cart/{cartId}")
    @Operation(summary = "Update cart item quantity", description = "Increases or decreases item quantity")
    @ApiResponse(responseCode = "200", description = "Quantity updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid user ID or request")
    public ResponseEntity<?> updateCartQuantity(
            @Parameter(description = "User ID") @RequestParam Integer userId,
            @Parameter(description = "Cart ID") @PathVariable Integer cartId,
            @Parameter(description = "Operation (increment/decrement)") @RequestParam String operation) {
        logger.info("Received update cart quantity request: userId={}, cartId={}, operation={}", userId, cartId, operation);



        try {
            cartService.updateQuantity(operation, cartId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Quantity updated successfully"));
        } catch (Exception e) {
            logger.error("Error updating cart quantity for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    // ========== ORDER ENDPOINTS ==========
    @PostMapping("/orders")
    @Operation(summary = "Create order", description = "Creates a new order from cart items for the specified user")
    @ApiResponse(responseCode = "201", description = "Order created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid user ID, empty cart, or invalid request")
    public ResponseEntity<?> createOrder(
            @Parameter(description = "User ID") @RequestParam Integer userId,
            @Parameter(description = "Order details") @RequestBody OrderRequest request) {
        logger.info("Received order create request: userId={}, request={}", userId, request);



        // Validate cart items
        List<Cart> cartItems = cartService.getCartsByUser(userId);
        if (cartItems == null || cartItems.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", "Cannot create order: Cart is empty"));
        }

        // Validate OrderRequest fields
        if (ObjectUtils.isEmpty(request.getFirstName()) || ObjectUtils.isEmpty(request.getLastName()) ||
                ObjectUtils.isEmpty(request.getEmail()) || ObjectUtils.isEmpty(request.getMobileNo()) ||
                ObjectUtils.isEmpty(request.getAddress()) || ObjectUtils.isEmpty(request.getCity()) ||
                ObjectUtils.isEmpty(request.getState()) || ObjectUtils.isEmpty(request.getPincode()) ||
                ObjectUtils.isEmpty(request.getPaymentType())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", "All order fields are required"));
        }

        try {
            ProductOrder savedOrder = orderService.saveOrder(userId, request);
            if (savedOrder == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("success", false, "error", "Failed to create order: Unable to process cart items"));
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "message", "Order created successfully",
                    "orderId", savedOrder.getId().toString()
            ));
        } catch (Exception e) {
            logger.error("Error creating order for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Failed to create order: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/orders")
    @Operation(summary = "Get user orders", description = "Retrieves all orders for the specified user")
    @ApiResponse(responseCode = "200", description = "Orders retrieved successfully")
    @ApiResponse(responseCode = "400", description = "Invalid user ID")
    public ResponseEntity<?> getUserOrders(
            @Parameter(description = "User ID") @RequestParam Integer userId) {
        logger.info("Received get orders request: userId={}", userId);



        List<ProductOrder> orders = orderService.getOrdersByUser(userId);
        if (orders == null || orders.isEmpty()) {
            return ResponseEntity.ok(Map.of("success", true, "message", "No orders found for user ID: " + userId));
        }
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Get order details", description = "Retrieves details for a specific order")
    @ApiResponse(responseCode = "200", description = "Order retrieved successfully")
    @ApiResponse(responseCode = "400", description = "Invalid user ID or order ID")
    @ApiResponse(responseCode = "404", description = "Order not found")
    public ResponseEntity<?> getOrderDetails(
            @Parameter(description = "User ID") @RequestParam Integer userId,
            @Parameter(description = "Order ID") @PathVariable String orderId) {
        logger.info("Received get order details request: userId={}, orderId={}", userId, orderId);



        try {
            ProductOrder order = orderService.getOrdersByOrderId(orderId);
            if (order == null || !order.getUser().getId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "error", "Order not found or does not belong to user"));
            }
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            logger.error("Error retrieving order {} for user {}: {}", orderId, userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    // ========== PROFILE ENDPOINTS ==========
    /*@GetMapping("/profile")
    @Operation(summary = "Get user profile", description = "Retrieves the specified user's profile")
    @ApiResponse(responseCode = "200", description = "Profile retrieved successfully")
    @ApiResponse(responseCode = "400", description = "Invalid user ID")
    public ResponseEntity<?> getProfile(
            @Parameter(description = "User ID") @RequestParam Integer userId) {
        logger.info("Received get profile request: userId={}", userId);


        return ResponseEntity.ok(user);
    }*/

    @PutMapping("/profile")
    @Operation(summary = "Update profile", description = "Updates the specified user's profile information")
    @ApiResponse(responseCode = "200", description = "Profile updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid user ID or request")
    public ResponseEntity<?> updateProfile(
            @Parameter(description = "User ID") @RequestParam Integer userId,
            @Parameter(description = "User details") @RequestBody UserDtls userDetails) {
        logger.info("Received update profile request: userId={}", userId);



        try {
            userDetails.setId(userId);
            UserDtls updatedUser = userService.updateUser(userDetails);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Profile updated successfully",
                    "user", updatedUser
            ));
        } catch (Exception e) {
            logger.error("Error updating profile for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }



    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Serializable>> handleJsonParseException(HttpMessageNotReadableException ex) {
        logger.error("JSON parsing error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error", "Invalid JSON format: " + ex.getMessage()));
    }
}