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
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import java.util.*;

import static com.amazon.ask.request.Predicates.intentName;

public class TimeTableIntentHandler implements IntentRequestHandler {
    public boolean canHandle(HandlerInput input, IntentRequest intentRequest){
        return input.matches(intentName("TimeTableIntent"));
    }
    public Optional<Response> handle(HandlerInput input, IntentRequest intentRequest) {
        String speechText = "Sorry, I failed to fetch the time.";
        IntentRequest request = (IntentRequest) input.getRequestEnvelope().getRequest();
        Map<String, Slot> slots = request.getIntent().getSlots();
        String courseName = "";
        String courseCode = "";
        if (slots != null) {
            if (slots.containsKey("course_name")) {
                Slot nameSlot = slots.get("course_name");
                if (nameSlot != null) {
                    courseName = nameSlot.getValue();
                    if (courseName != null) {
                        speechText = "course code is null.";
                    }
                }
            }
            if (slots.containsKey("course_code")) {
                Slot codeSlot = slots.get("course_code");
                if (codeSlot != null) {
                    courseCode = codeSlot.getValue();
                    speechText = "slot is not null.";

                    if (courseCode == null) {
                        speechText += "course code is null.";
                    } else if (courseCode.equals("")) {
                        speechText += "course code is empty.";
                    }
                }
            }
        } else {
            speechText = "slot is null.";
        }

        String codeConcat = "";
        String timeConcat = "";
        String error = "";

        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
        Map<String, AttributeValue> exprAttr = new HashMap<String, AttributeValue>();

        if (courseCode != null && !courseCode.isEmpty()) {
            exprAttr.put(":code", new AttributeValue().withS(courseCode));
            ScanRequest scanReq = new ScanRequest()
                    .withTableName("t_csd_course")
                    .withFilterExpression("contains(course_code,:code)")
                    .withProjectionExpression("course_name,course_time,instructor")
                    .withExpressionAttributeValues(exprAttr);
            ScanResult result = client.scan(scanReq);
            Map<String, String> courseInfo = retrieveCourseTime(result);
            timeConcat = courseInfo.get("time");
            courseName = courseInfo.get("name");
            courseName = courseName;

            speechText = speechText = "Course CS" + courseCode + " " + courseName + " starts " + timeConcat;
            input.getAttributesManager().setSessionAttributes(Collections.singletonMap("course_name", courseName));
            if(!request.getDialogState().toString().equals("COMPLETED")){
                Slot nameSlot = Slot.builder().withName("course_name").withValue(courseName).build();
                Slot codeSlot = Slot.builder().withName("course_code").withValue(courseCode).build();
                Intent returnedIntent = request.getIntent().builder().putSlotsItem("course_name",nameSlot).putSlotsItem("course_code",codeSlot).withName("TimeTableIntent").build();
                return input.getResponseBuilder().addDelegateDirective(returnedIntent).build();
            }
        } else if (courseName != null && !courseName.equals("")) {
            exprAttr.put(":name", new AttributeValue().withS(courseName));
            ScanRequest scanReq = new ScanRequest()
                    .withTableName("t_csd_course")
                    .withFilterExpression("course_name = :name")
                    .withProjectionExpression("course_code, course_time")
                    .withExpressionAttributeValues(exprAttr);
            ScanResult result = client.scan(scanReq);
            Map<String, String> courseInfo = retrieveCourseTime(result);
            if (courseInfo.containsKey("code")) {
                codeConcat = courseInfo.get("code");
            }
            if (courseInfo.containsKey("time")) {
                timeConcat = courseInfo.get("time");
            }
            if (timeConcat.startsWith("Sorry")) {
                speechText = timeConcat;
            } else {
                speechText = "Course CS" + codeConcat + " " + courseName + " starts " + timeConcat;
            }
            return input.getResponseBuilder().
                    withSimpleCard("CSD Assistant",speechText).withSpeech(speechText).build();
        }

        return input.getResponseBuilder().
                withSimpleCard("CSD Assistant",speechText).withSpeech(speechText).build();

    }
    private Map<String,String> retrieveCourseTime(ScanResult result) {
        String timeConcat = "";
        String codeConcat = "";
        String name = "";
        Map<String,String> returned = new HashMap<String,String>();
        List<Map<String, AttributeValue>> items = result.getItems();
        if(items.size()==0){
            timeConcat = "Sorry, error occurs.";
        }
        else{
            Map<String, AttributeValue> item = items.get(0);
            if(item.containsKey("course_code")){
                AttributeValue codeVals = item.get("course_code");
                for (AttributeValue codeVal : codeVals.getL()){
                    codeConcat = codeConcat + codeVal.getS() + " or ";
                }
                codeConcat = codeConcat.substring(0,codeConcat.lastIndexOf("or"));
                returned.put("code",codeConcat);
            }
            if(item.containsKey("course_time")){
                AttributeValue timeVals = item.get("course_time");
                for (AttributeValue timeVal : timeVals.getL()) {
                    timeConcat = timeConcat + timeVal.getS() + " or ";
                }
                timeConcat = timeConcat.substring(0, timeConcat.lastIndexOf("or"));
                returned.put("time",timeConcat);
            }
            if(item.containsKey("course_name")){
                AttributeValue nameVal = item.get("course_name");
                name = nameVal.getS();
                returned.put("name",name);
            }
        }
        return returned;
    }

}
