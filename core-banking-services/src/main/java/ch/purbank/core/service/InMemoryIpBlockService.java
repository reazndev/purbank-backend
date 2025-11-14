package ch.purbank.core.service;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.LocalDateTime;

@Service
public class InMemoryIpBlockService implements IpBlockService {

    private final Map<String, LocalDateTime> blockedIps = new ConcurrentHashMap<>();

    @Override
    public void blockIp(String ip, int minutes) {
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(minutes);
        blockedIps.put(ip, expiresAt);
    }

    @Override
    public boolean isBlocked(String ip) {
        LocalDateTime expiresAt = blockedIps.get(ip);

        if (expiresAt == null) {
            return false;
        }

        if (LocalDateTime.now().isAfter(expiresAt)) {
            blockedIps.remove(ip);
            return false;
        }

        return true;
    }
}