package com.twitchtts.queue;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MessageQueue {
    private final BlockingQueue<QueueMessage> queue = new LinkedBlockingQueue<>();
    
    public void addMessage(QueueMessage message) {
        queue.offer(message);
        System.out.println("[TwitchTTS] Message added to queue: " + message.username + " - " + message.message);
    }
    
    public QueueMessage takeMessage() throws InterruptedException {
        return queue.take();
    }
    
    public boolean isEmpty() {
        return queue.isEmpty();
    }
    
    public static class QueueMessage {
        public final String username;
        public final String message;
        public final long timestamp;
        
        public QueueMessage(String username, String message) {
            this.username = username;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
    }
}