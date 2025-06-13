package com.ecom.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Principal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.ecom.model.Category;
import com.ecom.model.Product;
import com.ecom.model.ProductOrder;
import com.ecom.model.UserDtls;
import com.ecom.service.CartService;
import com.ecom.service.CategoryService;
import com.ecom.service.OrderService;
import com.ecom.service.ProductService;
import com.ecom.service.UserService;
import com.ecom.util.CommonUtil;
import com.ecom.util.OrderStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/admin")
@Tag(name = "Admin Controller", description = "Handles all admin operations")
public class AdminController {

	@Autowired
	private CategoryService categoryService;

	@Autowired
	private ProductService productService;

	@Autowired
	private UserService userService;

	@Autowired
	private CartService cartService;

	@Autowired
	private OrderService orderService;

	@Autowired
	private CommonUtil commonUtil;

	@Autowired
	private PasswordEncoder passwordEncoder;

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

	@Operation(summary = "Get admin dashboard")
	@GetMapping("/")
	public String index() {
		return "admin/index";
	}

	@Operation(summary = "Load add product page")
	@GetMapping("/loadAddProduct")
	public String loadAddProduct(Model m) {
		List<Category> categories = categoryService.getAllCategory();
		m.addAttribute("categories", categories);
		return "admin/add_product";
	}

	@Operation(summary = "View categories with pagination")
	@GetMapping("/category")
	public String category(Model m,
						   @Parameter(description = "Page number") @RequestParam(name = "pageNo", defaultValue = "0") Integer pageNo,
						   @Parameter(description = "Page size") @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize) {

		Page<Category> page = categoryService.getAllCategorPagination(pageNo, pageSize);
		List<Category> categorys = page.getContent();
		m.addAttribute("categorys", categorys);

		m.addAttribute("pageNo", page.getNumber());
		m.addAttribute("pageSize", pageSize);
		m.addAttribute("totalElements", page.getTotalElements());
		m.addAttribute("totalPages", page.getTotalPages());
		m.addAttribute("isFirst", page.isFirst());
		m.addAttribute("isLast", page.isLast());

		return "admin/category";
	}

	@Operation(summary = "Save new category")
	@PostMapping("/saveCategory")
	public String saveCategory(
			@Parameter(description = "Category details") @ModelAttribute Category category,
			@Parameter(description = "Category image") @RequestParam("file") MultipartFile file,
			HttpSession session) throws IOException {

		String imageName = file != null ? file.getOriginalFilename() : "default.jpg";
		category.setImageName(imageName);

		Boolean existCategory = categoryService.existCategory(category.getName());

		if (existCategory) {
			session.setAttribute("errorMsg", "Category Name already exists");
		} else {
			Category saveCategory = categoryService.saveCategory(category);

			if (ObjectUtils.isEmpty(saveCategory)) {
				session.setAttribute("errorMsg", "Not saved ! internal server error");
			} else {
				File saveFile = new ClassPathResource("static/img").getFile();
				Path path = Paths.get(saveFile.getAbsolutePath() + File.separator + "category_img" + File.separator
						+ file.getOriginalFilename());
				Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
				session.setAttribute("succMsg", "Saved successfully");
			}
		}
		return "redirect:/admin/category";
	}

	@Operation(summary = "Delete category")
	@GetMapping("/deleteCategory/{id}")
	public String deleteCategory(
			@Parameter(description = "Category ID") @PathVariable int id,
			HttpSession session) {
		Boolean deleteCategory = categoryService.deleteCategory(id);

		if (deleteCategory) {
			session.setAttribute("succMsg", "category delete success");
		} else {
			session.setAttribute("errorMsg", "something wrong on server");
		}
		return "redirect:/admin/category";
	}

	@Operation(summary = "Load edit category page")
	@GetMapping("/loadEditCategory/{id}")
	public String loadEditCategory(
			@Parameter(description = "Category ID") @PathVariable int id,
			Model m) {
		m.addAttribute("category", categoryService.getCategoryById(id));
		return "admin/edit_category";
	}

	@Operation(summary = "Update category")
	@PostMapping("/updateCategory")
	public String updateCategory(
			@Parameter(description = "Category details") @ModelAttribute Category category,
			@Parameter(description = "Category image") @RequestParam("file") MultipartFile file,
			HttpSession session) throws IOException {

		Category oldCategory = categoryService.getCategoryById(category.getId());
		String imageName = file.isEmpty() ? oldCategory.getImageName() : file.getOriginalFilename();

		if (!ObjectUtils.isEmpty(category)) {
			oldCategory.setName(category.getName());
			oldCategory.setIsActive(category.getIsActive());
			oldCategory.setImageName(imageName);
		}

		Category updateCategory = categoryService.saveCategory(oldCategory);

		if (!ObjectUtils.isEmpty(updateCategory)) {
			if (!file.isEmpty()) {
				File saveFile = new ClassPathResource("static/img").getFile();
				Path path = Paths.get(saveFile.getAbsolutePath() + File.separator + "category_img" + File.separator
						+ file.getOriginalFilename());
				Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
			}
			session.setAttribute("succMsg", "Category update success");
		} else {
			session.setAttribute("errorMsg", "something wrong on server");
		}
		return "redirect:/admin/loadEditCategory/" + category.getId();
	}

	// ... [Additional methods with similar annotations] ...

	@Operation(summary = "View admin profile")
	@GetMapping("/profile")
	public String profile() {
		return "/admin/profile";
	}

	@Operation(summary = "Update admin profile")
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
		return "redirect:/admin/profile";
	}

	@Operation(summary = "Change admin password")
	@PostMapping("/change-password")
	public String changePassword(
			@Parameter(description = "New password") @RequestParam String newPassword,
			@Parameter(description = "Current password") @RequestParam String currentPassword,
			Principal p, HttpSession session) {

		UserDtls loggedInUserDetails = commonUtil.getLoggedInUserDetails(p);
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
		return "redirect:/admin/profile";
	}
}