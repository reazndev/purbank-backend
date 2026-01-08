package ch.purbank.core.service;

import ch.purbank.core.domain.RegistrationCodes;
import ch.purbank.core.domain.User;
import ch.purbank.core.domain.enums.RegistrationCodeStatus; // <-- Added this import
import ch.purbank.core.domain.enums.Role;
import ch.purbank.core.repository.RegistrationCodesRepository;
import ch.purbank.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class AdminUserService {

	private final UserRepository userRepository;
	private final RegistrationCodesRepository registrationCodesRepository;
	private final PasswordEncoder passwordEncoder;

	public User createUser(User user) {
		if (userRepository.existsByEmail(user.getEmail())) {
			throw new IllegalArgumentException("A user with this email already exists.");
		}

		// Auto-generate contract number if not provided
		if (user.getContractNumber() == null || user.getContractNumber().isEmpty()) {
			user.setContractNumber(generateUniqueContractNumber());
		}

		if (user.getPassword() != null && !user.getPassword().isEmpty()) {
			user.setPassword(passwordEncoder.encode(user.getPassword()));
		}

		if (user.getRole() == null) {
			user.setRole(Role.USER);
		}
		return userRepository.save(user);
	}

	private String generateUniqueContractNumber() {
		SecureRandom random = new SecureRandom();
		String contractNumber;
		int attempts = 0;
		int maxAttempts = 100;

		do {
			// Generate 8-digit contract number
			int number = 10000000 + random.nextInt(90000000);
			contractNumber = String.valueOf(number);
			attempts++;

			if (attempts >= maxAttempts) {
				throw new IllegalStateException("Unable to generate unique contract number after " + maxAttempts + " attempts");
			}
		} while (userRepository.existsByContractNumber(contractNumber));

		return contractNumber;
	}

	public User getUser(UUID id) {
		return userRepository.findById(id).orElse(null);
	}

	public List<User> getAllUsers() {
		return userRepository.findAll();
	}

	public RegistrationCodes createRegistrationCode(UUID userId, String title, String description) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new IllegalArgumentException("User not found"));
		RegistrationCodes code = new RegistrationCodes();
		code.setUser(user);
		code.setTitle(title);
		code.setDescription(description);
		code.setStatus(RegistrationCodeStatus.OPEN);
		return registrationCodesRepository.save(code);
	}

	public List<RegistrationCodes> listRegistrationCodes(UUID userId) {
		return registrationCodesRepository.findAllByUserId(userId);
	}

	public boolean deleteRegistrationCode(UUID userId, UUID codeId) {
		RegistrationCodes code = registrationCodesRepository.findById(codeId).orElse(null);
		if (code == null)
			return false;
		if (!code.getUser().getId().equals(userId))
			return false;
		registrationCodesRepository.delete(code);
		return true;
	}
}