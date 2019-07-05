package com.uwo.csd.handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.impl.IntentRequestHandler;
import com.amazon.ask.model.Intent;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.Slot;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.uwo.csd.util.IntentHelper;

import java.util.*;

import static com.amazon.ask.request.Predicates.intentName;

public class CoursesWithinSpecifiedTermIntentHandler implements IntentRequestHandler {
    public boolean canHandle(HandlerInput input, IntentRequest intentRequest){
        return input.matches(intentName("CoursesWithinSpecifiedTermIntent"));
    }

    public Optional<Response> handle(HandlerInput input, IntentRequest intentRequest){
        String speechText = "Computer Science Department delivers the following course: ";
        String term = "";
        try {
            Map<String, Slot> slots = intentRequest.getIntent().getSlots();
            if (slots.containsKey("term")) {
                Slot slot = slots.get("term");
                term = slot.getValue();

                if (!IntentHelper.isStringValid(term)) {
                    Calendar cal = Calendar.getInstance();
                    int month = cal.get(Calendar.MONTH) + 1;
                    if (month >= 9 && month <= 12) {
                        term = "fall";
                    } else if (month >= 1 && month <= 4) {
                        term = "winter";
                    } else if (month >= 5 && month <= 8) {
                        term = "summer";
                    }
                }

                Map<String, AttributeValue> exprAttr = new HashMap<>();
                exprAttr.put(":term", new AttributeValue().withS(term));
                List<Map<String, AttributeValue>> items = IntentHelper.DBQueryCourseByTerm(exprAttr, "course_name, course_code");
                if (items.size() == 0) {
                    speechText = "Sorry, it appears that there is no courses available in the " + term + " term.";
                } else {
                    for (Map<String, AttributeValue> item : items) {
                        List<AttributeValue> codeTmp = item.get("course_code").getL();
                        String codeStr = "";
                        for (AttributeValue codeVal : codeTmp) {
                            codeStr += codeVal.getS() + "/";
                        }
                        codeStr = codeStr.substring(0, codeStr.lastIndexOf("/"));
                        speechText += "CS"+codeStr + " " + item.get("course_name").getS() + ", ";
                    }
                    speechText = speechText.substring(0, speechText.lastIndexOf(","));
                }
            }
        }
        catch(Exception ex){
            speechText = ex.getMessage();
        }
        return input.getResponseBuilder().withSimpleCard("CSD Assistant",speechText).withSpeech(speechText).withShouldEndSession(false).build();
    }
}
