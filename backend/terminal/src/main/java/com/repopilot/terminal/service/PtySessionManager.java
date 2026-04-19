package com.repopilot.terminal.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

@Service
public class PtySessionManager {

    private final ConcurrentMap<String, PtySession> sessions = new ConcurrentHashMap<>();

    public PtySession create(String sessionId, Consumer<String> stdoutConsumer) throws IOException {
        remove(sessionId);
        PtySession session = new PtySession(sessionId, stdoutConsumer);
        sessions.put(sessionId, session);
        return session;
    }

    public PtySession get(String sessionId) {
        return sessions.get(sessionId);
    }

    public void remove(String sessionId) {
        PtySession session = sessions.remove(sessionId);
        if (session != null) {
            session.close();
        }
    }
}