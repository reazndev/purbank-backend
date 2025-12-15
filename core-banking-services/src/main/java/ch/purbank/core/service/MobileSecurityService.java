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
import java.util.Optional;
import java.util.UUID;
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

    public boolean isValidSignature(User user, String signedMobileVerifyMessage, String deviceIdString) {
        if (signedMobileVerifyMessage == null || signedMobileVerifyMessage.isEmpty() || deviceIdString == null) {
            return false;
        }

        UUID deviceId;
        try {
            deviceId = UUID.fromString(deviceIdString);
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "Invalid deviceId format: {0}", deviceIdString);
            return false;
        }

        Optional<MobileDevice> deviceOpt = mobileDeviceRepository.findByIdAndStatus(deviceId,
                MobileDeviceStatus.ACTIVE);

        if (deviceOpt.isEmpty() || !deviceOpt.get().getUser().getId().equals(user.getId())) {
            logger.log(Level.WARNING, "No active device found for id {0} or device does not belong to user {1}.",
                    new Object[] { deviceIdString, user.getId() });
            return false;
        }

        String publicKeyString = deviceOpt.get().getPublicKey();

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
            logger.log(Level.SEVERE, "Signature verification failed for device " + deviceIdString, e);
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