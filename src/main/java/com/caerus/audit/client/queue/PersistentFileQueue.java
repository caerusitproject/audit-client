package com.caerus.audit.client.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Disk-backed persistent FIFO queue for screenshot files.
 * Survives restarts and guarantees ordered processing.
 */
public class PersistentFileQueue {
    private static final Logger log = LoggerFactory.getLogger(PersistentFileQueue.class);

    private final Path queueFile;
    private final BlockingQueue<QueueEntry> memoryQueue = new LinkedBlockingQueue<>();

    private static final int MAX_RETRIES = 3;

    public record QueueEntry(Path file, int retries){
        @Override
        public String toString() {
            return file.toString() + "|" + retries;
        }

        public static QueueEntry from(String line){
            String[] parts = line.split("\\|");
            Path path = Paths.get(parts[0]);
            int retries = (parts.length>1) ? Integer.parseInt(parts[1]) : 0;
            return new QueueEntry(path, retries);
        }
    }

    public PersistentFileQueue(Path directory) throws IOException{
        Files.createDirectories(directory);
        this.queueFile = directory.resolve("upload-queue.txt");
        loadQueue();
    }

    private synchronized void loadQueue() throws IOException{
        if(Files.exists(queueFile)){
            List<String> lines = Files.readAllLines(queueFile);
            for(String line : lines){
                QueueEntry entry = QueueEntry.from(line);
                if(Files.exists(entry.file())){
                    memoryQueue.offer(entry);
                }
            }
        }
        log.info("Loaded {} pending files from queue", memoryQueue.size());
    }

    public synchronized void enqueue(Path file) throws IOException{
        QueueEntry entry = new QueueEntry(file, 0);
        memoryQueue.offer(entry);
        persistQueue();
        log.info("File enqueued {}", file);
    }

    public QueueEntry peek() {
        return memoryQueue.peek();
    }

    public synchronized void markComplete(Path file) throws IOException{
        memoryQueue.removeIf(entry -> entry.file().equals(file));
        persistQueue();
        log.info("File marked complete {}", file);
    }

    public synchronized boolean incrementRetry(Path file) throws IOException{
        List<QueueEntry> updated = new ArrayList<>();
        for(QueueEntry e: memoryQueue){
            if(e.file().equals(file)){
                if(e.retries() + 1 >= MAX_RETRIES){
                    log.warn("File {} exceeded retry limit ({}) - will be discarded", file, MAX_RETRIES);
                    continue;
                }
                updated.add(new QueueEntry(file, e.retries() + 1));
            } else{
                updated.add(e);
            }
        }
        memoryQueue.clear();
        memoryQueue.addAll(updated);
        persistQueue();
        return true;
    }

    private void persistQueue() throws IOException{
        List<String> lines = memoryQueue.stream().map(QueueEntry::toString).toList();
        Files.write(queueFile, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public boolean isEmpty(){
        return memoryQueue.isEmpty();
    }

    public boolean hasExceededRetryLimit(Path file){
        return memoryQueue.stream()
                .filter(e -> e.file().equals(file))
                .anyMatch(e -> e.retries() >= MAX_RETRIES);
    }
}
