package ch.purbank.core.service;

import ch.purbank.core.domain.RegistrationCodes;
import ch.purbank.core.domain.User;
import ch.purbank.core.repository.RegistrationCodesRepository;
import ch.purbank.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final RegistrationCodesRepository registrationCodesRepository;

    public User createUser(User user) {
        return userRepository.save(user);
    }

    public User getUser(UUID id) {
        return userRepository.findById(id).orElse(null);
    }

    public RegistrationCodes createRegistrationCode(UUID userId, String title, String description) {
        User user = userRepository.findById(userId).orElseThrow();

        RegistrationCodes r = new RegistrationCodes();
        r.setUser(user);

        return registrationCodesRepository.save(r);
    }

    public List<RegistrationCodes> listRegistrationCodes(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();
        return user.getRegistrationCodes();
    }

    public boolean deleteRegistrationCode(UUID userId, UUID codeId) {
        RegistrationCodes code = registrationCodesRepository.findById(codeId)
                .orElse(null);
        if (code == null)
            return false;
        if (!code.getUser().getId().equals(userId))
            return false;

        registrationCodesRepository.delete(code);
        return true;
    }
}
