package com.smartedtech.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Map<Long, Bucket> messageBuckets = new ConcurrentHashMap<>();

    private Bucket createAuthBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(10)
                .refillGreedy(10, Duration.ofMinutes(1))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    private Bucket createMessageBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(10)
                .refillGreedy(10, Duration.ofSeconds(5))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    public boolean tryConsumeAuth(String clientIp) {
        Bucket bucket = buckets.computeIfAbsent(clientIp, k -> createAuthBucket());
        return bucket.tryConsume(1);
    }

    public boolean tryConsumeMessage(Long userId) {
        Bucket bucket = messageBuckets.computeIfAbsent(userId, k -> createMessageBucket());
        return bucket.tryConsume(1);
    }

    public void reset() {
        buckets.clear();
        messageBuckets.clear();
    }
}
