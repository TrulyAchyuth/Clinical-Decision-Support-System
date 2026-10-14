package com.cdss.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.cdss.dto.CreateUserRequest;
import com.cdss.dto.RoleChangeRequest;
import com.cdss.dto.UserSummaryResponse;
import com.cdss.service.UserAdminService;

import jakarta.validation.Valid;

/** User management; ADMIN only (see SecurityConfig). No DELETE: accounts are deactivated instead. */
@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

	private final UserAdminService userAdminService;

	public UserAdminController(UserAdminService userAdminService) {
		this.userAdminService = userAdminService;
	}

	@GetMapping
	public List<UserSummaryResponse> list() {
		return userAdminService.list();
	}

	@PostMapping
	public ResponseEntity<UserSummaryResponse> create(@Valid @RequestBody CreateUserRequest request) {
		UserSummaryResponse created = userAdminService.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
		return ResponseEntity.created(location).body(created);
	}

	@PostMapping("/{id}/activate")
	public UserSummaryResponse activate(@PathVariable Long id, Authentication authentication) {
		return userAdminService.setEnabled(id, true, authentication.getName());
	}

	@PostMapping("/{id}/deactivate")
	public UserSummaryResponse deactivate(@PathVariable Long id, Authentication authentication) {
		return userAdminService.setEnabled(id, false, authentication.getName());
	}

	@PutMapping("/{id}/role")
	public UserSummaryResponse changeRole(@PathVariable Long id, @Valid @RequestBody RoleChangeRequest request,
			Authentication authentication) {
		return userAdminService.changeRole(id, request.role(), authentication.getName());
	}

}
