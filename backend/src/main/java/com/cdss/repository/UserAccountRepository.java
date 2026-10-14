package com.cdss.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cdss.entity.Role;
import com.cdss.entity.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

	Optional<UserAccount> findByUsername(String username);

	boolean existsByUsername(String username);

	List<UserAccount> findAllByOrderByUsernameAsc();

	long countByRoleAndEnabledTrue(Role role);

}
