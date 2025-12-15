package ch.purbank.core.service;

import ch.purbank.core.domain.User;
import org.springframework.stereotype.Service;

@Service
public class MobileSecurityService {


    public boolean isValidSignature(User user, String signedMobileVerifyMessage) {
        return true;
    }

    public String extractMobileVerifyCode(String signedMessage) {
        int closingBraceIndex = signedMessage.indexOf('}');
        if (closingBraceIndex != -1 && signedMessage.length() > closingBraceIndex + 1) {
            return signedMessage.substring(closingBraceIndex + 1);
        }
        return "";
    }
}