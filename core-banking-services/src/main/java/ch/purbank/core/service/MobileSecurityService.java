package ch.purbank.core.service;

import ch.purbank.core.domain.User;
import ch.purbank.core.domain.MobileDevice;
import ch.purbank.core.domain.enums.MobileDeviceStatus;
import ch.purbank.core.repository.MobileDeviceRepository;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class MobileSecurityService {

    private static final Logger logger = Logger.getLogger(MobileSecurityService.class.getName());
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final String KEY_ALGORITHM = "RSA";

    private final MobileDeviceRepository mobileDeviceRepository;

    public MobileSecurityService(MobileDeviceRepository mobileDeviceRepository) {
        this.mobileDeviceRepository = mobileDeviceRepository;
    }

    public boolean isValidSignature(User user, String signedMobileVerifyMessage) {
        if (signedMobileVerifyMessage == null || signedMobileVerifyMessage.isEmpty()) {
            return false;
        }

        List<MobileDevice> activeDevices = mobileDeviceRepository.findByUserAndStatus(user, MobileDeviceStatus.ACTIVE);

        if (activeDevices.isEmpty()) {
            logger.log(Level.WARNING, "User {0} has no active mobile devices registered for approval.", user.getId());
            return false;
        }

        String[] parts = signedMobileVerifyMessage.split("\\|");
        if (parts.length != 2) {
            logger.log(Level.WARNING, "Invalid signed message format for user {0}.", user.getId());
            return false;
        }

        String originalMessage = parts[0];
        byte[] signatureBytes;
        try {
            signatureBytes = Base64.getDecoder().decode(parts[1]);
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "Invalid Base64 signature format for user {0}.", user.getId());
            return false;
        }

        for (MobileDevice device : activeDevices) {
            String publicKeyString = device.getPublicKey();
            if (publicKeyString == null || publicKeyString.isEmpty()) {
                continue;
            }

            try {
                PublicKey mobilePublicKey = getPublicKeyFromString(publicKeyString);
                Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
                signature.initVerify(mobilePublicKey);
                signature.update(originalMessage.getBytes());

                if (signature.verify(signatureBytes)) {
                    return true;
                }
            } catch (Exception e) {
                logger.log(Level.FINE, "Failed to verify signature with key for device {0}.", device.getId());
            }
        }

        logger.log(Level.WARNING, "Signature failed verification against all active keys for user {0}.", user.getId());
        return false;
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