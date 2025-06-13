package com.ecom.controller;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.ecom.model.Category;
import com.ecom.model.Product;
import com.ecom.model.UserDtls;
import com.ecom.service.CartService;
import com.ecom.service.CategoryService;
import com.ecom.service.ProductService;
import com.ecom.service.UserService;
import com.ecom.util.CommonUtil;

import io.micrometer.common.util.StringUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
@Tag(name = "E-Commerce Controller", description = "Handles all e-commerce web operations")
public class HomeController {

	@Autowired
	private CategoryService categoryService;

	@Autowired
	private ProductService productService;

	@Autowired
	private UserService userService;

	@Autowired
	private CommonUtil commonUtil;

	@Autowired
	private BCryptPasswordEncoder passwordEncoder;

	@Autowired
	private CartService cartService;

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

	@Operation(summary = "Get home page with featured products and categories")
	@GetMapping("/")
	public String index(Model m) {
		List<Category> allActiveCategory = categoryService.getAllActiveCategory().stream()
				.sorted((c1, c2) -> c2.getId().compareTo(c1.getId())).limit(6).toList();
		List<Product> allActiveProducts = productService.getAllActiveProducts("").stream()
				.sorted((p1, p2) -> p2.getId().compareTo(p1.getId())).limit(8).toList();
		m.addAttribute("category", allActiveCategory);
		m.addAttribute("products", allActiveProducts);
		return "index";
	}

	@Operation(summary = "Show login page")
	@GetMapping("/signin")
	public String login() {
		return "login";
	}

	@Operation(summary = "Show registration page")
	@GetMapping("/register")
	public String register() {
		return "register";
	}

	@Operation(summary = "Show products page with filtering and pagination")
	@ApiResponse(responseCode = "200", description = "Products page loaded successfully")
	@GetMapping("/products")
	public String products(Model m,
						   @Parameter(description = "Category filter") @RequestParam(value = "category", defaultValue = "") String category,
						   @Parameter(description = "Page number") @RequestParam(name = "pageNo", defaultValue = "0") Integer pageNo,
						   @Parameter(description = "Page size") @RequestParam(name = "pageSize", defaultValue = "12") Integer pageSize,
						   @Parameter(description = "Search term") @RequestParam(defaultValue = "") String ch) {

		List<Category> categories = categoryService.getAllActiveCategory();
		m.addAttribute("paramValue", category);
		m.addAttribute("categories", categories);

		Page<Product> page = null;
		if (StringUtils.isEmpty(ch)) {
			page = productService.getAllActiveProductPagination(pageNo, pageSize, category);
		} else {
			page = productService.searchActiveProductPagination(pageNo, pageSize, category, ch);
		}

		List<Product> products = page.getContent();
		m.addAttribute("products", products);
		m.addAttribute("productsSize", products.size());

		m.addAttribute("pageNo", page.getNumber());
		m.addAttribute("pageSize", pageSize);
		m.addAttribute("totalElements", page.getTotalElements());
		m.addAttribute("totalPages", page.getTotalPages());
		m.addAttribute("isFirst", page.isFirst());
		m.addAttribute("isLast", page.isLast());

		return "product";
	}

	@Operation(summary = "Show product details page")
	@ApiResponse(responseCode = "200", description = "Product details page loaded")
	@ApiResponse(responseCode = "404", description = "Product not found")
	@GetMapping("/product/{id}")
	public String product(
			@Parameter(description = "Product ID") @PathVariable int id, Model m) {
		Product productById = productService.getProductById(id);
		m.addAttribute("product", productById);
		return "view_product";
	}

	@Operation(summary = "Register new user")
	@ApiResponse(responseCode = "302", description = "Redirect to registration page with success/error message")
	@PostMapping("/saveUser")
	public String saveUser(
			@Parameter(description = "User details") @ModelAttribute UserDtls user,
			@Parameter(description = "Profile image") @RequestParam("img") MultipartFile file,
			HttpSession session) throws IOException {

		Boolean existsEmail = userService.existsEmail(user.getEmail());

		if (existsEmail) {
			session.setAttribute("errorMsg", "Email already exist");
		} else {
			String imageName = file.isEmpty() ? "default.jpg" : file.getOriginalFilename();
			user.setProfileImage(imageName);
			UserDtls saveUser = userService.saveUser(user);

			if (!ObjectUtils.isEmpty(saveUser)) {
				if (!file.isEmpty()) {
					File saveFile = new ClassPathResource("static/img").getFile();
					Path path = Paths.get(saveFile.getAbsolutePath() + File.separator + "profile_img" + File.separator
							+ file.getOriginalFilename());
					Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
				}
				session.setAttribute("succMsg", "Register successfully");
			} else {
				session.setAttribute("errorMsg", "something wrong on server");
			}
		}

		return "redirect:/register";
	}

	@Operation(summary = "Show forgot password page")
	@GetMapping("/forgot-password")
	public String showForgotPassword() {
		return "forgot_password.html";
	}

	@Operation(summary = "Process forgot password request")
	@ApiResponse(responseCode = "302", description = "Redirect to forgot password page with message")
	@PostMapping("/forgot-password")
	public String processForgotPassword(
			@Parameter(description = "User's email address") @RequestParam String email,
			HttpSession session,
			HttpServletRequest request) throws UnsupportedEncodingException, MessagingException {

		UserDtls userByEmail = userService.getUserByEmail(email);

		if (ObjectUtils.isEmpty(userByEmail)) {
			session.setAttribute("errorMsg", "Invalid email");
		} else {
			String resetToken = UUID.randomUUID().toString();
			userService.updateUserResetToken(email, resetToken);

			String url = CommonUtil.generateUrl(request) + "/reset-password?token=" + resetToken;
			Boolean sendMail = commonUtil.sendMail(url, email);

			if (sendMail) {
				session.setAttribute("succMsg", "Please check your email..Password Reset link sent");
			} else {
				session.setAttribute("errorMsg", "Something wrong on server ! Email not send");
			}
		}

		return "redirect:/forgot-password";
	}

	@Operation(summary = "Show reset password page")
	@ApiResponse(responseCode = "200", description = "Reset password page shown")
	@ApiResponse(responseCode = "404", description = "Invalid or expired token")
	@GetMapping("/reset-password")
	public String showResetPassword(
			@Parameter(description = "Password reset token") @RequestParam String token,
			HttpSession session, Model m) {

		UserDtls userByToken = userService.getUserByToken(token);

		if (userByToken == null) {
			m.addAttribute("msg", "Your link is invalid or expired !!");
			return "message";
		}
		m.addAttribute("token", token);
		return "reset_password";
	}

	@Operation(summary = "Process password reset")
	@ApiResponse(responseCode = "200", description = "Password changed successfully")
	@ApiResponse(responseCode = "404", description = "Invalid or expired token")
	@PostMapping("/reset-password")
	public String resetPassword(
			@Parameter(description = "Password reset token") @RequestParam String token,
			@Parameter(description = "New password") @RequestParam String password,
			HttpSession session, Model m) {

		UserDtls userByToken = userService.getUserByToken(token);
		if (userByToken == null) {
			m.addAttribute("errorMsg", "Your link is invalid or expired !!");
			return "message";
		} else {
			userByToken.setPassword(passwordEncoder.encode(password));
			userByToken.setResetToken(null);
			userService.updateUser(userByToken);
			m.addAttribute("msg", "Password change successfully");
			return "message";
		}
	}

	@Operation(summary = "Search products")
	@ApiResponse(responseCode = "200", description = "Search results page loaded")
	@GetMapping("/search")
	public String searchProduct(
			@Parameter(description = "Search term") @RequestParam String ch, Model m) {
		List<Product> searchProducts = productService.searchProduct(ch);
		m.addAttribute("products", searchProducts);
		List<Category> categories = categoryService.getAllActiveCategory();
		m.addAttribute("categories", categories);
		return "product";
	}
}