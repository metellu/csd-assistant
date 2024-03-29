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
        String speechText = "Welcome to the Western University CSD Assistant. You can ask me questions about any Computer Science Department courses you are interested in. Please tell me which course you would like to know. If you have no idea what courses are available, you can say show me all available courses this term.";
        return input.getResponseBuilder()
                .withSpeech(speechText)
                .withSimpleCard("CSD Assistant", speechText)
                .withReprompt(speechText)
                .build();
    }

}
