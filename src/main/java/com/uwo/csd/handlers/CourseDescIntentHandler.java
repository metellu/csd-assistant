package com.uwo.csd.handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.impl.IntentRequestHandler;
import com.amazon.ask.model.Intent;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.Slot;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.uwo.csd.util.IntentHelper;

import javax.management.Attribute;
import java.util.*;

import static com.amazon.ask.request.Predicates.intentName;

public class CourseDescIntentHandler implements IntentRequestHandler {
    public boolean canHandle(HandlerInput input, IntentRequest intentRequest){
        return input.matches(intentName("CourseDescIntent"));
    }

    public Optional<Response> handle(HandlerInput input, IntentRequest intentRequest){
        String speechText = "";
        String name = "";
        String code = "";
        String tutor = "";
        try {
            IntentRequest request = (IntentRequest) input.getRequestEnvelope().getRequest();
            Intent intent = request.getIntent();
            Map<String, Slot> slots = intent.getSlots();
            name = IntentHelper.getCourseNameIfExists(slots);
            code = IntentHelper.getCourseCodeIfExists(slots);

            Map<String, Object> sessionAttr = input.getAttributesManager().getSessionAttributes();

            if( code.isEmpty() && name.isEmpty() ) {
                if (sessionAttr != null && sessionAttr.containsKey("course_name")) {
                    name = (String) sessionAttr.get("course_name");
                } else {
                    //if course name is presented neither in slot nor in session, the skill needs to prompt user to provide one.
                    return input.getResponseBuilder().addElicitSlotDirective("course_name", intentRequest.getIntent()).withSpeech("Which course you want to query?").build();
                }
            }

            Map<String, AttributeValue> exprAttr = new HashMap<String, AttributeValue>();

            if( !code.isEmpty() ) {
                exprAttr.put(":courseCode", new AttributeValue().withS(code));
                List<Map<String, AttributeValue>> items = IntentHelper.DBQueryByCourseCode(exprAttr,"course_name,description,instructor");
                if( items.size()==0 ){
                    return input.getResponseBuilder().addElicitSlotDirective("course_name",intent).withSpeech("Sorry, course CS"+code+" may not be available this term. In order to avoid ambiguity, please provide me with the course name.").build();
                }
                else{
                    name = items.get(0).get("course_name").getS();
                    List<AttributeValue> tutors = items.get(0).get("instructor").getL();
                    for(AttributeValue tutorVal:tutors){
                        tutor += tutorVal.getS()+" ";
                    }
                    tutor = tutor.substring(0, tutor.lastIndexOf(" "));
                    speechText = items.get(0).get("description").getS();
                }
            }
            else if( !name.isEmpty()) {
                exprAttr.put(":courseName", new AttributeValue().withS(name));
                List<Map<String, AttributeValue>> items = IntentHelper.DBQueryByCourseName(exprAttr,"description, instructor");
                if (items.size() == 0) {
                    //if none item is retrieved, it says that the course name provided by the user is invalid. So we need to prompt the user to give another one.
                    return input.getResponseBuilder().addElicitSlotDirective("course_name", intent).withSpeech("Sorry, course " + name + " is not available this term. Would you like to try another course?").build();
                } else {
                    speechText = items.get(0).get("description").getS();
                    List<AttributeValue> tutors = items.get(0).get("instructor").getL();
                    for(AttributeValue tutorVal:tutors){
                        tutor += tutorVal.getS()+" ";
                    }
                    tutor = tutor.substring(0, tutor.lastIndexOf(" "));
                }
            }
            sessionAttr.put("instructor",tutor);
            sessionAttr.put("course_name",name);
            input.getAttributesManager().setSessionAttributes(sessionAttr);
        }
        catch(Exception ex){
            speechText += " error:"+ex.getMessage();
        }


        return input.getResponseBuilder().withSpeech(speechText).withSimpleCard("CSD Assistant V1", speechText).withShouldEndSession(false).build();
    }
}
