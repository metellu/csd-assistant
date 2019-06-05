package com.uwo.csd.handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.impl.IntentRequestHandler;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.Slot;
import com.amazonaws.services.dynamodbv2.document.Attribute;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.uwo.csd.entity.Course;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import java.util.*;

import static com.amazon.ask.request.Predicates.intentName;

public class TimeTableIntentHandler implements IntentRequestHandler {
    public boolean canHandle(HandlerInput input, IntentRequest intentRequest){
        return input.matches(intentName("TimeTableIntent"));
    }
    public Optional<Response> handle(HandlerInput input, IntentRequest intentRequest){
        String speechText = "Sorry, I failed to fetch the time.";

        IntentRequest request = (IntentRequest)input.getRequestEnvelope().getRequest();
        Map<String, Slot> slots = request.getIntent().getSlots();
        String courseName = "";
        String courseCode = "";
        if( slots!=null ){
            if( slots.containsKey("course_name") ) {
                Slot nameSlot = slots.get("course_name");
                if (nameSlot != null) {
                    courseName = nameSlot.getValue();
                }
            }
            if( slots.containsKey("course_code")) {
                Slot codeSlot = slots.get("course_code");
                if ( codeSlot != null ){
                    courseCode = codeSlot.getValue();
                }
            }
        }

        DialogIntent

        DynamoDbClient dbClient = DynamoDbClient.create();
        Map<String, AttributeValue> key = new HashMap<String, AttributeValue>();
        if(!courseName.equals("")){
            key.put("course_name", AttributeValue.builder().s(courseName).build());
        }
        //if(!courseCode.equals("")){
        //    key.put("course_code", AttributeValue.builder().s(courseCode).build());
        //}
        GetItemRequest itemRequest = GetItemRequest.builder().key(key).tableName("t_csd_timetable").build();
        try {
            Map<String, AttributeValue> returned = dbClient.getItem(itemRequest).item();
            Course course;
            String name = "";
            List<String> code = new ArrayList<String>();
            String location = "";
            List<String> time = new ArrayList<String>();
            String codeConcat = "";
            String timeConcat = "";
            speechText = "I fetched data from dynamoDB.";
            for(String attrName:returned.keySet()){
                switch(attrName){
                    case "course_name":
                        name = returned.get(attrName).s();
                        break;
                    case "course_code":
                        List<AttributeValue> codes = returned.get(attrName).l();
                        for(AttributeValue codeAttr:codes){
                            code.add(codeAttr.s());
                            codeConcat = codeConcat+ codeAttr.s()+ " or ";
                        }
                        codeConcat =codeConcat.substring(0,codeConcat.lastIndexOf(" or "));
                        break;
                    case "course_location":
                        location = returned.get(attrName).s();
                        break;
                    case "course_time":
                        List<AttributeValue> times = returned.get(attrName).l();
                        for(AttributeValue timeAttr:times){
                            time.add(timeAttr.s());
                            timeConcat = timeConcat + timeAttr.s() + " or ";
                        }
                        timeConcat = timeConcat.substring(0, timeConcat.length()-3);
                        break;

                }
            }
            //Course.builder().courseName(name).courseCode(code).courseTimes(time).courseLocation(location).build();

            speechText = "CS"+ codeConcat + " " +name+ " starts "+timeConcat;
        }
        catch(DynamoDbException dde){
            System.err.println(dde.getMessage());
            speechText = "Error occurred. "+dde.getMessage();
        }
        return input.getResponseBuilder().
                withSimpleCard("CSD Assistant",speechText).withSpeech(speechText).build();

    }
}
