package com.ecom.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;

import com.ecom.model.Cart;
import com.ecom.model.Category;
import com.ecom.model.OrderRequest;
import com.ecom.model.ProductOrder;
import com.ecom.model.UserDtls;
import com.ecom.service.CartService;
import com.ecom.service.CategoryService;
import com.ecom.service.OrderService;
import com.ecom.service.UserService;
import com.ecom.util.CommonUtil;
import com.ecom.util.OrderStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/user")
@Tag(name = "User Controller", description = "Handles all user-related operations")
public class UserController {

	@Autowired
	private UserService userService;

	@Autowired
	private CategoryService categoryService;

	@Autowired
	private CartService cartService;

	@Autowired
	private OrderService orderService;

	@Autowired
	private CommonUtil commonUtil;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Operation(summary = "Get user home page")
	@GetMapping("/")
	public String home() {
		return "user/home";
	}

	@ModelAttribute
	public void getUserDetails(Principal p, Model m) {
		if (p != null) {
			String email = p.getName();
			UserDtls userDtls = userService.getUserByEmail(email);
			m.addAttribute("user", userDtls);
			Integer countCart = cartService.getCountCart(userDtls.getId());
			m.addAttribute("countCart", countCart);
		}

		List<Category> allActiveCategory = categoryService.getAllActiveCategory();
		m.addAttribute("categorys", allActiveCategory);
	}

	@Operation(summary = "Add product to cart")
	@ApiResponse(responseCode = "302", description = "Redirects to product page with success/error message")
	@GetMapping("/addCart")
	public String addToCart(
			@Parameter(description = "Product ID") @RequestParam Integer pid,
			@Parameter(description = "User ID") @RequestParam Integer uid,
			HttpSession session) {

		Cart saveCart = cartService.saveCart(pid, uid);

		if (ObjectUtils.isEmpty(saveCart)) {
			session.setAttribute("errorMsg", "Product add to cart failed");
		} else {
			session.setAttribute("succMsg", "Product added to cart");
		}
		return "redirect:/product/" + pid;
	}

	@Operation(summary = "View user's cart")
	@ApiResponse(responseCode = "200", description = "Cart page loaded successfully")
	@GetMapping("/cart")
	public String loadCartPage(Principal p, Model m) {
		UserDtls user = getLoggedInUserDetails(p);
		List<Cart> carts = cartService.getCartsByUser(user.getId());
		m.addAttribute("carts", carts);
		if (carts.size() > 0) {
			Double totalOrderPrice = carts.get(carts.size() - 1).getTotalOrderPrice();
			m.addAttribute("totalOrderPrice", totalOrderPrice);
		}
		return "/user/cart";
	}

	@Operation(summary = "Update cart item quantity")
	@ApiResponse(responseCode = "302", description = "Redirects back to cart page")
	@GetMapping("/cartQuantityUpdate")
	public String updateCartQuantity(
			@Parameter(description = "Update type (inc/dec)") @RequestParam String sy,
			@Parameter(description = "Cart item ID") @RequestParam Integer cid) {
		cartService.updateQuantity(sy, cid);
		return "redirect:/user/cart";
	}

	private UserDtls getLoggedInUserDetails(Principal p) {
		String email = p.getName();
		return userService.getUserByEmail(email);
	}

	@Operation(summary = "View order page")
	@ApiResponse(responseCode = "200", description = "Order page loaded successfully")
	@GetMapping("/orders")
	public String orderPage(Principal p, Model m) {
		UserDtls user = getLoggedInUserDetails(p);
		List<Cart> carts = cartService.getCartsByUser(user.getId());
		m.addAttribute("carts", carts);
		if (carts.size() > 0) {
			Double orderPrice = carts.get(carts.size() - 1).getTotalOrderPrice();
			Double totalOrderPrice = carts.get(carts.size() - 1).getTotalOrderPrice() + 250 + 100;
			m.addAttribute("orderPrice", orderPrice);
			m.addAttribute("totalOrderPrice", totalOrderPrice);
		}
		return "/user/order";
	}

	@Operation(summary = "Place new order")
	@ApiResponse(responseCode = "302", description = "Redirects to success page if order placed successfully")
	@PostMapping("/save-order")
	public String saveOrder(
			@Parameter(description = "Order details") @ModelAttribute OrderRequest request,
			Principal p) throws Exception {
		UserDtls user = getLoggedInUserDetails(p);
		orderService.saveOrder(user.getId(), request);
		return "redirect:/user/success";
	}

	@Operation(summary = "Order success page")
	@ApiResponse(responseCode = "200", description = "Order success page shown")
	@GetMapping("/success")
	public String loadSuccess() {
		return "/user/success";
	}

	@Operation(summary = "View user's orders")
	@ApiResponse(responseCode = "200", description = "Orders page loaded successfully")
	@GetMapping("/user-orders")
	public String myOrder(Model m, Principal p) {
		UserDtls loginUser = getLoggedInUserDetails(p);
		List<ProductOrder> orders = orderService.getOrdersByUser(loginUser.getId());
		m.addAttribute("orders", orders);
		return "/user/my_orders";
	}

	@Operation(summary = "Update order status")
	@ApiResponse(responseCode = "302", description = "Redirects back to orders page with status message")
	@GetMapping("/update-status")
	public String updateOrderStatus(
			@Parameter(description = "Order ID") @RequestParam Integer id,
			@Parameter(description = "Status code") @RequestParam Integer st,
			HttpSession session) {

		OrderStatus[] values = OrderStatus.values();
		String status = null;

		for (OrderStatus orderSt : values) {
			if (orderSt.getId().equals(st)) {
				status = orderSt.getName();
			}
		}

		ProductOrder updateOrder = orderService.updateOrderStatus(id, status);

		try {
			commonUtil.sendMailForProductOrder(updateOrder, status);
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (!ObjectUtils.isEmpty(updateOrder)) {
			session.setAttribute("succMsg", "Status Updated");
		} else {
			session.setAttribute("errorMsg", "status not updated");
		}
		return "redirect:/user/user-orders";
	}

	@Operation(summary = "View user profile")
	@ApiResponse(responseCode = "200", description = "Profile page loaded successfully")
	@GetMapping("/profile")
	public String profile() {
		return "/user/profile";
	}

	@Operation(summary = "Update user profile")
	@ApiResponse(responseCode = "302", description = "Redirects back to profile page with status message")
	@PostMapping("/update-profile")
	public String updateProfile(
			@Parameter(description = "User details") @ModelAttribute UserDtls user,
			@Parameter(description = "Profile image") @RequestParam MultipartFile img,
			HttpSession session) {
		UserDtls updateUserProfile = userService.updateUserProfile(user, img);
		if (ObjectUtils.isEmpty(updateUserProfile)) {
			session.setAttribute("errorMsg", "Profile not updated");
		} else {
			session.setAttribute("succMsg", "Profile Updated");
		}
		return "redirect:/user/profile";
	}

	@Operation(summary = "Change user password")
	@ApiResponse(responseCode = "302", description = "Redirects back to profile page with status message")
	@PostMapping("/change-password")
	public String changePassword(
			@Parameter(description = "New password") @RequestParam String newPassword,
			@Parameter(description = "Current password") @RequestParam String currentPassword,
			Principal p, HttpSession session) {

		UserDtls loggedInUserDetails = getLoggedInUserDetails(p);
		boolean matches = passwordEncoder.matches(currentPassword, loggedInUserDetails.getPassword());

		if (matches) {
			String encodePassword = passwordEncoder.encode(newPassword);
			loggedInUserDetails.setPassword(encodePassword);
			UserDtls updateUser = userService.updateUser(loggedInUserDetails);
			if (ObjectUtils.isEmpty(updateUser)) {
				session.setAttribute("errorMsg", "Password not updated !! Error in server");
			} else {
				session.setAttribute("succMsg", "Password Updated sucessfully");
			}
		} else {
			session.setAttribute("errorMsg", "Current Password incorrect");
		}

		return "redirect:/user/profile";
	}
}