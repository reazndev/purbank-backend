package ch.purbank.core.service;

import ch.purbank.core.domain.User;
import org.springframework.stereotype.Service;

@Service
public class ActionDecisionService {

    public boolean requiresApproval(User user, String deviceId, String ipAddress, String actionType,
            String actionDataPayload) {
        return true;
    }

    public void executeImmediateAction(User user, String actionDataPayload) {
    }
}