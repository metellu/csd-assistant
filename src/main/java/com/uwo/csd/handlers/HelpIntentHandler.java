package com.uwo.csd.handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.Response;

import java.util.Optional;

import static com.amazon.ask.request.Predicates.intentName;

public class HelpIntentHandler implements RequestHandler {

    @Override
    public boolean canHandle(HandlerInput input) {
        return input.matches(intentName("AMAZON.HelpIntent"));
    }

    @Override
    public Optional<Response> handle(HandlerInput input) {
        String speechText = "You can ask questions about course description, course time, venue, evaluation method, instructor info as well as your eligibility to enroll into a certain course. For instant, you may say 'tell me something about unstructured data' to check course description, 'check the time for unstructured data' to check the course time and venue, 'tell me something about professor Hanan Lutfiyya' to get a brief description of the instructor, 'show me the evaluation method of intro to data science' to get a brief narration of the course evaluation, or 'Am I allowed to enroll in Intro to data science' to check your eligibility of course enrollment";
        return input.getResponseBuilder()
                .withSpeech(speechText)
                .withSimpleCard("CSD Assistant", speechText)
                .withReprompt(speechText)
                .build();
    }
}