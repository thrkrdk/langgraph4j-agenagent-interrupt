package com.langgraph4j.agent.interrupt.langgraph;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.StateSnapshot;

import java.util.Map;
import java.util.UUID;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

public class QAAssistant {

    private final CompiledGraph<QAState> graph;
    private RunnableConfig config;
    private StateSnapshot<QAState> lastSnapshot;
    private final String threadId;

    public QAAssistant() throws GraphStateException {
        // 1) Define node's actions
        AsyncNodeAction<QAState> askCountry = node_async(s -> {
            System.out.println("askCountry node is working. State: " + s);
            return Map.of("messages", "Which country do you live in?");
        });
        // wait for user response
        AsyncNodeAction<QAState> waitForCountry = node_async(s -> {
            System.out.println("waitForCountry node is  working. State: " + s);
            return Map.of();
        });
        AsyncNodeAction<QAState> askCity = node_async(s -> {
            System.out.println("askCity node is  working. State: " + s);
            return Map.of("messages", "For which city would you like the forecast?");
        });
        // wait for user response
        AsyncNodeAction<QAState> waitForCity = node_async(s -> {
            System.out.println("waitForCity node is working. State: " + s);
            return Map.of();
        });
        AsyncNodeAction<QAState> showWeather = node_async(s -> {
            System.out.println("showWeather node is working. State: " + s);
            int temp = fetchTemperature(s.country(), s.city());
            return Map.of("messages",
                    String.format("The temperature in %s is %d °C.", s.city(), temp)
            );
        });

        // defining nodes and edges
        StateGraph<QAState> builder = new StateGraph<>(QAState.SCHEMA, QAState::new)
                .addNode("ask_country", askCountry)
                .addNode("wait_for_country", waitForCountry)
                .addNode("ask_city", askCity)
                .addNode("wait_for_city", waitForCity)
                .addNode("show_weather", showWeather)
                .addEdge(START, "ask_country")
                .addEdge("ask_country", "wait_for_country")
                .addEdge("wait_for_country", "ask_city")
                .addEdge("ask_city", "wait_for_city")
                .addEdge("wait_for_city", "show_weather")
                .addEdge("show_weather", END);

        // 3)   time-travel node state
        CompileConfig compileConfig = CompileConfig.builder()
                // .interruptBefore("ask_country", "ask_city")
                .checkpointSaver(new MemorySaver())
                .interruptAfter("ask_country", "ask_city")
                .releaseThread(true)
                .build();

        this.graph = builder.compile(compileConfig);
        this.threadId = UUID.randomUUID().toString();
    }


    // first rest call
    public StateSnapshot<QAState> startConversation(String question) {
        System.out.println("question: " + question);

        // genereate threadId
        config = RunnableConfig.builder()
                .threadId(threadId)
                .streamMode(CompiledGraph.StreamMode.SNAPSHOTS)
                .build();


        graph.stream(Map.of("question", question), config)
                .forEach(o -> {
                    System.out.println("data: " + o.state().data());
                });


        StateSnapshot<QAState> snap = graph.getState(config);
        System.out.println("snap: " + snap);
        lastSnapshot = snap;
        return snap;
    }

    // second and third rest call
    public StateSnapshot<QAState> provideFeedback(String input) throws Exception {
        System.out.println("assistant: " + this);
        System.out.println("input: " + input);

        String field = lastSnapshot.state().country().isBlank() ? "country" : "city";

        config = graph.updateState(
                config,
                Map.of(field, input)
        );
        System.out.println("config after updateState: " + config);

        graph.streamSnapshots(
                null,
                 config
        ).forEach(o -> {
            System.out.println("data: " + o.state().data());
        });

        StateSnapshot<QAState> snap = graph.getState(config);
        System.out.println("snap: " + snap);

        lastSnapshot = snap;
        return snap;
    }


    private int fetchTemperature(String country, String city) {
        System.out.println("fetchTemperature(" + country + ", " + city + ")");
        return 35;
    }
}