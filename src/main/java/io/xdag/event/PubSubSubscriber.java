package io.xdag.event;

public interface PubSubSubscriber {

    void onPubSubEvent(PubSubEvent event);
}
