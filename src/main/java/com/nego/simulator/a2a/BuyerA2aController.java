package com.nego.simulator.a2a;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.spec.AgentCard;
import io.a2a.spec.CancelTaskRequest;
import io.a2a.spec.EventKind;
import io.a2a.spec.GetTaskRequest;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.SendMessageRequest;
import io.a2a.spec.SendStreamingMessageRequest;
import io.a2a.server.ServerCallContext;
import io.a2a.server.auth.UnauthenticatedUser;
import io.a2a.spec.Task;
import io.a2a.spec.TaskQueryParams;
import io.a2a.spec.TaskIdParams;
import io.a2a.spec.MessageSendParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@ConditionalOnProperty(name = "nego.role", havingValue = "buyer")
public class BuyerA2aController {

    private static final Logger log = LoggerFactory.getLogger(BuyerA2aController.class);

    private final AgentCard agentCard;
    private final RequestHandler requestHandler;
    private final ObjectMapper objectMapper;

    public BuyerA2aController(AgentCard agentCard, RequestHandler requestHandler, ObjectMapper objectMapper) {
        this.agentCard = agentCard;
        this.requestHandler = requestHandler;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/.well-known/agent-card.json")
    public AgentCard getCard() {
        return agentCard;
    }

    @PostMapping(value = "/a2a", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> handleJsonRpc(@RequestBody JsonNode body) {
        try {
            String method = body.has("method") ? body.get("method").asText() : "";
            Object id = body.has("id") ? objectMapper.treeToValue(body.get("id"), Object.class) : null;
            JsonNode paramsNode = body.has("params") ? body.get("params") : null;

            ServerCallContext callContext = new ServerCallContext(
                    UnauthenticatedUser.INSTANCE, Map.of(), java.util.Set.of());

            switch (method) {
                case "message/send" -> {
                    MessageSendParams params = objectMapper.treeToValue(paramsNode, MessageSendParams.class);
                    EventKind result = requestHandler.onMessageSend(params, callContext);
                    return ResponseEntity.ok(buildResponse(id, result));
                }
                case "tasks/get" -> {
                    TaskQueryParams params = objectMapper.treeToValue(paramsNode, TaskQueryParams.class);
                    Task result = requestHandler.onGetTask(params, callContext);
                    return ResponseEntity.ok(buildResponse(id, result));
                }
                case "tasks/cancel" -> {
                    TaskIdParams params = objectMapper.treeToValue(paramsNode, TaskIdParams.class);
                    Task result = requestHandler.onCancelTask(params, callContext);
                    return ResponseEntity.ok(buildResponse(id, result));
                }
                default -> {
                    return ResponseEntity.ok(buildErrorResponse(id,
                            new JSONRPCError(-32601, "Method not found: " + method, null)));
                }
            }
        } catch (JSONRPCError e) {
            Object id = body.has("id") ? null : null;
            return ResponseEntity.ok(buildErrorResponse(id, e));
        } catch (Exception e) {
            log.error("Error handling A2A JSON-RPC request", e);
            return ResponseEntity.ok(buildErrorResponse(null,
                    new JSONRPCError(-32603, "Internal error: " + e.getMessage(), null)));
        }
    }

    private Map<String, Object> buildResponse(Object id, Object result) {
        return Map.of(
                "jsonrpc", "2.0",
                "id", id != null ? id : JsonNodeFactory.instance.nullNode(),
                "result", result
        );
    }

    private Map<String, Object> buildErrorResponse(Object id, JSONRPCError error) {
        return Map.of(
                "jsonrpc", "2.0",
                "id", id != null ? id : JsonNodeFactory.instance.nullNode(),
                "error", Map.of(
                        "code", error.getCode(),
                        "message", error.getMessage() != null ? error.getMessage() : "",
                        "data", error.getData() != null ? error.getData() : JsonNodeFactory.instance.nullNode()
                )
        );
    }
}
