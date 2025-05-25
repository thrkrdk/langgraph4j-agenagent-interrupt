package com.langgraph4j.agent.interrupt.controller;

import com.langgraph4j.agent.interrupt.langgraph.QAAssistant;
import com.langgraph4j.agent.interrupt.langgraph.QAState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/chat")
public class ChatController {

    /**
     * sessionId → local in memory agent state. (next step moving to redis)
     **/
    private final Map<String, QAAssistant> sessions = new ConcurrentHashMap<>();

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest req) throws Exception {
        // if sessionId is null, agent starts with from scratch
        String sessionId = req.sessionId() != null
                ? req.sessionId()
                : UUID.randomUUID().toString();

        // get latest agent state from memory
        QAAssistant assistant = sessions.computeIfAbsent(sessionId, id -> {
            try {
                return new QAAssistant();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        System.out.println("assistant: " + assistant.toString());

        QAState state;
        if (req.sessionId() == null) {
            state = assistant.startConversation(req.message());
        } else {
            state = assistant.provideFeedback(req.message());
        }

        String agentMsg = state.messages();
        boolean waitingForUser = state.country().isBlank() || state.city().isBlank();
        return ResponseEntity.ok(new ChatResponse(sessionId, agentMsg, waitingForUser));
    }
}