package com.uwo.csd.handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.impl.IntentRequestHandler;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.uwo.csd.util.IntentHelper;

import java.util.*;

import static com.amazon.ask.request.Predicates.intentName;

public class VenueIntentHandler implements IntentRequestHandler {
    public boolean canHandle(HandlerInput input, IntentRequest intentRequest){
        return input.matches(intentName("VenueIntent"));
    }
    public Optional<Response> handle(HandlerInput input, IntentRequest intentRequest){
        String speechText = "";
        try {
            String name = IntentHelper.getCourseNameIfExists(intentRequest.getIntent().getSlots());
            String code = IntentHelper.getCourseCodeIfExists(intentRequest.getIntent().getSlots());

            Map<String, Object> sessionAttr = input.getAttributesManager().getSessionAttributes();
            if (!IntentHelper.isStringValid(name) && !IntentHelper.isStringValid(code)) {
                if (sessionAttr.containsKey("course_name")) {
                    name = (String) sessionAttr.get("course_name");
                } else {
                    speechText = "Please tell me which course venue you would like to know.";
                    input.getResponseBuilder().addElicitSlotDirective("course_name", intentRequest.getIntent()).withSpeech(speechText).build();
                }
            }
            List<Map<String, AttributeValue>> items = new ArrayList<>();
            if (IntentHelper.isStringValid(name)) {
                Map<String, AttributeValue> exprAttr = new HashMap<>();
                exprAttr.put(":courseName", new AttributeValue().withS(name));
                items = IntentHelper.DBQueryByCourseName(exprAttr, "course_name, course_code, time_location");
            } else if (IntentHelper.isStringValid(code)) {
                Map<String, AttributeValue> exprAttr = new HashMap<>();
                exprAttr.put(":courseCode", new AttributeValue().withS(code));
                items = IntentHelper.DBQueryByCourseCode(exprAttr, "course_name, course_code, time_location");
            }
            if (items.size() == 0) {
                speechText = "Sorry, it appears that course " + name + " is not available this term. Would you like to try another course?";
                input.getResponseBuilder().addElicitSlotDirective("course_name", intentRequest.getIntent()).withSpeech(speechText).build();
            } else {
                List<AttributeValue> locs = items.get(0).get("time_location").getL();
                List<AttributeValue> codes = items.get(0).get("course_code").getL();
                String location = "venue";
                String be       = "is";
                if( locs.size() >1 ){
                    location +="s";
                    be = "are";
                }
                speechText = "The "+location+" of course CS" + IntentHelper.formCourseCodeString(codes) + " " + name + " "+be+" ";
                for (AttributeValue locAttr : locs) {
                    Map<String, AttributeValue> locMap = locAttr.getM();
                    String locTmp = locMap.get("location").getS();
                    String dateTmp = locMap.get("day_in_week").getS();
                    speechText += locTmp + " on " + dateTmp + " and ";
                }
                speechText = speechText.substring(0, speechText.lastIndexOf("and"));
            }
        }
        catch(Exception ex){
            speechText = ex.getMessage();
        }
        return input.getResponseBuilder().withSpeech(speechText).withSimpleCard("CSD Assistant",speechText).withShouldEndSession(false).build();
    }
}
