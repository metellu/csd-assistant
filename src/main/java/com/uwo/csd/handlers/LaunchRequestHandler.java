package com.uwo.csd.handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.LaunchRequest;
import com.amazon.ask.model.Response;

import java.util.Optional;

import static com.amazon.ask.request.Predicates.requestType;

public class LaunchRequestHandler implements RequestHandler {

    @Override
    public boolean canHandle(HandlerInput input) {
        return input.matches(requestType(LaunchRequest.class));
    }

    @Override
    public Optional<Response> handle(HandlerInput input) {
        String speechText = "Welcome to the Western University CSD Assistant. You can ask me questions about any CS Department courses you are interested in. Like time or course description ";
        return input.getResponseBuilder()
                .withSpeech(speechText)
                .withSimpleCard("CSD Assistant", speechText)
                .withReprompt(speechText)
                .build();
    }

}
