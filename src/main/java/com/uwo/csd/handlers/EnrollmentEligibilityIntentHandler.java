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

        Map<String,Slot> slots = intentRequest.getIntent().getSlots();
        courseName = IntentHelper.getCourseNameIfExists(slots);
        courseCode = IntentHelper.getCourseCodeIfExists(slots);
        confirm    = IntentHelper.getSpecifiedSlotValueIfExists(slots,"prerequisites_confirm");

        if( courseCode.isEmpty() && courseName.isEmpty() ){
            Map<String,Object> attr = input.getAttributesManager().getSessionAttributes();
            if( attr.containsKey("course_name") ){
                courseName = (String)attr.get("course_name");
            }
            else{
                return input.getResponseBuilder().addElicitSlotDirective("course_name",intentRequest.getIntent()).withSpeech("Which course you would like to check?").build();
            }
        }

        log.info("confirm:"+confirm);
        log.info("course name:"+courseName);
        if( confirm!=null && !confirm.isEmpty() ){
            if( confirm.equals("no") || confirm.equals("nope") ){
                speechText = "Sorry, it appears you don't meet all the prerequisites. You cannot enroll in this course.";
            }
            else if( confirm.equals("yes") || confirm.equals("yeap") ){
                speechText = "Congrats. You meet all the prerequisites. You may be able to enroll in this course.";
            }
        }
        else {
            AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
            List<Map<String, AttributeValue>> items = new ArrayList<>();
            try {
                if ( IntentHelper.isStringValid(courseName) ) {
                    Map<String, AttributeValue> exprAttr = new HashMap<String, AttributeValue>();
                    exprAttr.put(":courseName", new AttributeValue().withS(courseName));
                    items = IntentHelper.DBQueryByCourseName(exprAttr,"prerequisites");

                    if (items.size() == 0) {
                        return input.getResponseBuilder().addElicitSlotDirective("course_name", intentRequest.getIntent()).withSpeech("Sorry, the course you're looking for seems unavailable this year. Would you like to try another course?").build();
                    }
                }
                else if( IntentHelper.isStringValid(courseCode) ){
                    Map<String, AttributeValue> exprAttr = new HashMap<String, AttributeValue>();
                    exprAttr.put(":courseCode", new AttributeValue().withS(courseCode));
                    items = IntentHelper.DBQueryByCourseCode(exprAttr,"prerequisites");
                    if (items.size() == 0) {
                        return input.getResponseBuilder().addElicitSlotDirective("course_name", intentRequest.getIntent()).withSpeech("Sorry, the course you're looking for seems unavailable this year. Would you like to try another course?").build();
                    }
                }
                Map<String, AttributeValue> item = items.get(0);
                if (item.containsKey("prerequisites")) {
                    List<AttributeValue> attrVals = item.get("prerequisites").getL();
                    for (AttributeValue attrVal : attrVals) {
                        preqConcat += attrVal.getS() + ", ";
                    }
                    preqConcat = preqConcat.substring(0, preqConcat.lastIndexOf(","));
                    preqConcat = preqConcat.substring(0,preqConcat.lastIndexOf(","))+" and " + preqConcat.substring(preqConcat.lastIndexOf(",")+2);
                    speechText = "According to the course description of " + courseName + ", students are expected to meet all the prerequisites to enroll in the course, including " + preqConcat + ". Do you think you meet all the pre-conditions?";
                    return input.getResponseBuilder().addElicitSlotDirective("prerequisites_confirm", intentRequest.getIntent()).withSpeech(speechText).build();
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
