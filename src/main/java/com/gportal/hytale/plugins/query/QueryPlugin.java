package com.gportal.hytale.plugins.query;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class QueryPlugin extends JavaPlugin {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final StatusWriter statusWriter = new StatusWriter();

    public QueryPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        statusWriter.start();

        scheduler.scheduleAtFixedRate(statusWriter::updateStatus, 30, 30, TimeUnit.SECONDS);
    }

    @Override
    protected void shutdown() {
        statusWriter.stop();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
}
