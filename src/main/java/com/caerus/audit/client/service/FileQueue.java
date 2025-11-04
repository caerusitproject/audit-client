package com.caerus.audit.client.service;

import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class FileQueue {
    private final BlockingQueue<Path> queue = new LinkedBlockingQueue<>();

    public void enqueue(Path p){ queue.add(p); }

    public Path take() throws InterruptedException { return queue.take(); }

    public int size(){ return queue.size(); }
}
