package com.langgraph4j.agent.interrupt.controller;


public record ChatResponse(String sessionId, String agentMessage, boolean waitingForUser) {
}