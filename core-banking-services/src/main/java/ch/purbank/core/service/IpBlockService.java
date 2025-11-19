package ch.purbank.core.service;

public interface IpBlockService {
    void blockIp(String ip, int minutes);

    boolean isBlocked(String ip);
}
