package org.example;

import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.services.s3.model.SelectObjectContentEventStream;
import software.amazon.awssdk.services.s3.model.SelectObjectContentResponse;
import software.amazon.awssdk.services.s3.model.SelectObjectContentResponseHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * An implementation of the SelectObjectContentResponseHandler interface, which essentially
 * collects the received results in a list that can later be fetched when the request completes.
 */
public class S3SelectHandler  implements SelectObjectContentResponseHandler {
    private List<SelectObjectContentEventStream> receivedEvents = new ArrayList<>();

    @Override
    public void responseReceived(SelectObjectContentResponse response) {
    }

    @Override
    public void onEventStream(SdkPublisher<SelectObjectContentEventStream> publisher) {
        publisher.subscribe(receivedEvents::add);
    }

    @Override
    public void exceptionOccurred(Throwable throwable) {
    }

    @Override
    public void complete() {
    }

    public List<SelectObjectContentEventStream> getReceivedEvents() {
        return receivedEvents;
    }
}