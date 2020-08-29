package io.xdag.event;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;

@Slf4j
public class EventProcesser {

    private static EventBus eventBus = new AsyncEventBus(Executors.newCachedThreadPool());

    public static EventBus getEventBus() {
        return eventBus;
    }

    @Subscribe
    protected void onKernelBootingEvent(KernelBootingEvent e) {
        log.info("onKernelBootingEvent");
    }

}
