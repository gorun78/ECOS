package com.chinacreator.gzcm.runtime.core.agent.mesh;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Agent-to-Agent (A2A) 进程内消息通道。
 *
 * <p>支持 Agent 间通过 topic 进行异步消息传递，
 * 典型场景：PIPELINE 模式中上游 Agent 产出结果后发送给下游。</p>
 *
 * @author CDRC Design Team
 */
@Service
public class AgentMessageBus {

    private static final Logger log = LoggerFactory.getLogger(AgentMessageBus.class);

    /** topic → 消息队列 */
    private final Map<String, Queue<Message>> topics = new ConcurrentHashMap<>();

    /**
     * 发送消息到指定 topic
     */
    public void send(String topic, String fromAgentId, String toAgentId, String content) {
        Message msg = new Message(topic, fromAgentId, toAgentId, content);
        topics.computeIfAbsent(topic, k -> new ConcurrentLinkedQueue<>()).offer(msg);
        log.debug("A2A: {} → {} [{}] {}", fromAgentId, toAgentId, topic,
                content.length() > 100 ? content.substring(0, 100) + "..." : content);
    }

    /**
     * 接收指定 topic 的最早消息（非阻塞），无消息返回 null
     */
    public Message receive(String topic) {
        Queue<Message> q = topics.get(topic);
        return q != null ? q.poll() : null;
    }

    /**
     * 清空指定 topic 的消息
     */
    public void clear(String topic) {
        Queue<Message> q = topics.get(topic);
        if (q != null) q.clear();
    }

    /**
     * 获取 topic 中待处理消息数
     */
    public int pendingCount(String topic) {
        Queue<Message> q = topics.get(topic);
        return q != null ? q.size() : 0;
    }

    /**
     * A2A 消息载体
     */
    public static class Message {
        private final String topic;
        private final String fromAgentId;
        private final String toAgentId;
        private final String content;
        private final long timestamp;

        public Message(String topic, String fromAgentId, String toAgentId, String content) {
            this.topic = topic;
            this.fromAgentId = fromAgentId;
            this.toAgentId = toAgentId;
            this.content = content;
            this.timestamp = System.currentTimeMillis();
        }

        public String getTopic() { return topic; }
        public String getFromAgentId() { return fromAgentId; }
        public String getToAgentId() { return toAgentId; }
        public String getContent() { return content; }
        public long getTimestamp() { return timestamp; }
    }
}
