package com.uwo.csd.handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.impl.IntentRequestHandler;
import com.amazon.ask.model.Intent;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.Slot;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import lombok.extern.slf4j.Slf4j;
import java.util.*;
import java.util.Optional;
import com.uwo.csd.util.IntentHelper;
import static com.amazon.ask.request.Predicates.intentName;

@Slf4j
public class EnrollmentEligibilityIntentHandler implements IntentRequestHandler {
    public boolean canHandle(HandlerInput input, IntentRequest intentRequest){
        return input.matches(intentName("EnrollmentEligibilityIntent"));
    }
    public Optional<Response> handle(HandlerInput input, IntentRequest intentRequest) {
        String speechText = "";
        String courseName = "";
        String courseCode = "";
        String preqConcat = "";
        String confirm    = "";
        List<String> prerequisite = new ArrayList<>();

        Map<String,Slot> slots = intentRequest.getIntent().getSlots();
        courseName = IntentHelper.getCourseNameIfExists(slots);
        courseCode = IntentHelper.getCourseCodeIfExists(slots);
        try {
            confirm = slots.get("prerequisites_confirm").getResolutions().getResolutionsPerAuthority().get(0).getValues().get(0).getValue().getName();
        }
        catch(Exception ex){
            speechText = ex.getMessage();
        }

        List<String> confirms = new ArrayList<>();

        Map<String,Object> attr = input.getAttributesManager().getSessionAttributes();
        if( attr.containsKey("prerequisites") ){
            prerequisite = (List<String>) attr.get("prerequisites");
        }
        if( attr.containsKey("confirms") ){
            confirms = (List<String>)attr.get("confirms");
        }

        if( courseCode.isEmpty() && courseName.isEmpty() ){

            if( attr.containsKey("course_name") ){
                courseName = (String)attr.get("course_name");
            }
            else{
                return input.getResponseBuilder().addElicitSlotDirective("course_name",intentRequest.getIntent()).withSpeech("Please tell me which course you would like to check?").build();
            }
        }

        if( IntentHelper.isStringValid(confirm) ) {
            confirms.add(confirm);
            attr.put("confirms",confirms);
            if (confirms.stream().anyMatch(str -> IntentHelper.isStringValid(str))) {
                if (confirms.stream().anyMatch(str -> str.equalsIgnoreCase("no"))) {
                    speechText = "Sorry, it appears you don't meet all the prerequisites. You cannot enroll in this course.";
                    confirms.clear();
                    attr.replace("confirms",confirms);
                }
                else {

                    if (prerequisite.size() > 0) {
                        String cond = prerequisite.remove(0);
                        attr.replace("prerequisites", prerequisite);
                        input.getAttributesManager().setSessionAttributes(attr);
                        return input.getResponseBuilder().addElicitSlotDirective("prerequisites_confirm", intentRequest.getIntent()).withSpeech(cond).build();
                    } else {
                        speechText = "Congrats. You meet all the prerequisites. You may be able to enroll in this course.";
                    }
                }
            }
        }
        else {
            AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
            List<Map<String, AttributeValue>> items = new ArrayList<>();
            try {
                if ( IntentHelper.isStringValid(courseName) ) {
                    Map<String, AttributeValue> exprAttr = new HashMap<String, AttributeValue>();
                    exprAttr.put(":courseName", new AttributeValue().withS(courseName));
                    items = IntentHelper.DBQueryByCourseName(exprAttr,"prerequisites,course_name,course_code");

                    if (items.size() == 0) {
                        return input.getResponseBuilder().addElicitSlotDirective("course_name", intentRequest.getIntent()).withSpeech("Sorry, the course you're looking for seems unavailable this year. Would you like to try another course?").build();
                    }
                }
                else if( IntentHelper.isStringValid(courseCode) ){
                    Map<String, AttributeValue> exprAttr = new HashMap<String, AttributeValue>();
                    exprAttr.put(":courseCode", new AttributeValue().withS(courseCode));
                    items = IntentHelper.DBQueryByCourseCode(exprAttr,"prerequisites,course_name,course_code");
                    if (items.size() == 0) {
                        return input.getResponseBuilder().addElicitSlotDirective("course_name", intentRequest.getIntent()).withSpeech("Sorry, the course you're looking for seems unavailable this year. Would you like to try another course?").build();
                    }
                }
                Map<String, AttributeValue> item = items.get(0);
                if (item.containsKey("prerequisites")) {
                    List<AttributeValue> attrVals = item.get("prerequisites").getL();
                    if( attrVals.size()== 0 ){
                        speechText = "Sorry, it seems that there is no available prerequisite for that course.";
                    }
                    else {
                        for (AttributeValue attrVal : attrVals) {
                            prerequisite.add(attrVal.getS());
                            preqConcat += attrVal.getS() + ", ";
                        }
                        courseName = (courseName != null && !courseName.isEmpty()) ? courseName : item.get("course_name").getS();
                        Map<String, Object> sessionAttr = input.getAttributesManager().getSessionAttributes();
                        sessionAttr.put("course_name", courseName);
                        input.getAttributesManager().setSessionAttributes(sessionAttr);
                        //preqConcat = preqConcat.substring(0, preqConcat.lastIndexOf(","));
                        //preqConcat = preqConcat.substring(0,preqConcat.lastIndexOf(","))+" and " + preqConcat.substring(preqConcat.lastIndexOf(",")+2);
                        speechText = "According to the course description of " + courseName + ", students are expected to meet all the prerequisites to enroll in the course. Please provide yes or no answers to the following questions. Then I can help you determine your eligibility. " + prerequisite.get(0);
                        prerequisite.remove(0);
                        attr.put("prerequisites", prerequisite);
                        input.getAttributesManager().setSessionAttributes(attr);
                        return input.getResponseBuilder().addElicitSlotDirective("prerequisites_confirm", intentRequest.getIntent()).withSpeech(speechText).build();
                    }

                }
            }
            catch(Exception ex){
                speechText = ex.getMessage();
                log.error(ex.getMessage());
            }
        }
        return input.getResponseBuilder().withSpeech(speechText).withSimpleCard("CSD Assistant",speechText).withShouldEndSession(false).build();
    }
}
