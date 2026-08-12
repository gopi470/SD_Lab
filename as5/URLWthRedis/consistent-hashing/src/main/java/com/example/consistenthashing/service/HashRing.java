package com.example.consistenthashing.service;

import com.example.consistenthashing.model.StorageNode;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

@Component
public class HashRing {

    private final TreeMap<Long, StorageNode> ring = new TreeMap<>();

    private static final int VIRTUAL_NODES = 10;

    public void addNode(StorageNode node) {
        for (int i = 0; i < VIRTUAL_NODES; i++) {
            long hash = hash(node.getName() + "-" + i);
            ring.put(hash, node);
        }
    }

    public void removeNode(StorageNode node) {
        for (int i = 0; i < VIRTUAL_NODES; i++) {
            long hash = hash(node.getName() + "-" + i);
            ring.remove(hash);
        }
    }

    public StorageNode getNode(String key) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("No storage nodes available");
        }

        long hash = hash(key);

        Map.Entry<Long, StorageNode> entry = ring.ceilingEntry(hash);

        if (entry == null) {
            entry = ring.firstEntry();
        }

        return entry.getValue();
    }

    public long hash(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(value.getBytes());
            return ByteBuffer.wrap(bytes).getLong() & Long.MAX_VALUE;
        } catch (Exception e) {
            throw new RuntimeException("Hashing failed", e);
        }
    }

    public boolean isEmpty() {
        return ring.isEmpty();
    }
}
