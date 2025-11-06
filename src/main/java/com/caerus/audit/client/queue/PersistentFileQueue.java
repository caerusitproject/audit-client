package com.caerus.audit.client.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
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
    private final BlockingQueue<Path> memoryQueue = new LinkedBlockingQueue<>();

    public PersistentFileQueue(Path directory) throws IOException{
        Files.createDirectories(directory);
        this.queueFile = directory.resolve("upload-queue.txt");
        loadQueue();
    }

    private void loadQueue() throws IOException{
        if(Files.exists(queueFile)){
            List<String> lines = Files.readAllLines(queueFile);
            for(String line : lines){
                Path path = Paths.get(line);
                if(Files.exists(path)){
                    memoryQueue.add(path);
                }
            }
        }
        log.info("Loaded {} pending files from queue", memoryQueue.size());
    }

    public synchronized void enqueue(Path file) throws IOException{
        memoryQueue.offer(file);
        Files.writeString(queueFile,
                file.toString() + System.lineSeparator(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        log.info("File enqueued {} to queue", file);
    }

    public Path take() throws InterruptedException{
        return memoryQueue.take();
    }

    public synchronized void markComplete(Path file) throws IOException{
        memoryQueue.remove(file);
        List<String> remaining = memoryQueue.stream()
                        .map(Path::toString)
                        .toList();
        Files.write(queueFile, remaining);
        log.info("File marked complete {} from queue", file);
    }

    public boolean isEmpty(){
        return memoryQueue.isEmpty();
    }
}
