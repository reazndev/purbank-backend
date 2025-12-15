package ch.purbank.core.service;

import ch.purbank.core.domain.User;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class MobileSecurityService {

    private static final Logger logger = Logger.getLogger(MobileSecurityService.class.getName());
    // Important note: The algorithm must match the one used for signing on the
    // mobile app
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final String KEY_ALGORITHM = "RSA";

    public boolean isValidSignature(User user, String signedMobileVerifyMessage) {
        if (signedMobileVerifyMessage == null || signedMobileVerifyMessage.isEmpty()) {
            return false;
        }

        String publicKeyString = user.getMobilePublicKey();
        if (publicKeyString == null || publicKeyString.isEmpty()) {
            logger.log(Level.WARNING, "User {0} has no registered mobile public key.", user.getId());
            return false;
        }

        try {
            PublicKey mobilePublicKey = getPublicKeyFromString(publicKeyString);

            String[] parts = signedMobileVerifyMessage.split("\\|");
            if (parts.length != 2) {
                logger.log(Level.WARNING, "Invalid signed message format for user {0}.", user.getId());
                return false;
            }

            String originalMessage = parts[0];
            byte[] signatureBytes = Base64.getDecoder().decode(parts[1]);

            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initVerify(mobilePublicKey);
            signature.update(originalMessage.getBytes());

            return signature.verify(signatureBytes);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Signature verification failed for user " + user.getId(), e);
            return false;
        }
    }

    private PublicKey getPublicKeyFromString(String publicKeyBase64) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        return keyFactory.generatePublic(keySpec);
    }

    public String extractMobileVerifyCode(String signedMessage) {
        int closingBraceIndex = signedMessage.indexOf('}');
        if (closingBraceIndex != -1 && signedMessage.length() > closingBraceIndex + 1) {

            String tokenPart = signedMessage.substring(closingBraceIndex + 1);
            int signatureSeparator = tokenPart.indexOf('|');

            if (signatureSeparator != -1) {
                return tokenPart.substring(0, signatureSeparator);
            }

            return tokenPart;
        }
        return "";
    }
}