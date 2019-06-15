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

import java.util.*;

import static com.amazon.ask.request.Predicates.intentName;

public class CourseDescIntentHandler implements IntentRequestHandler {
    public boolean canHandle(HandlerInput input, IntentRequest intentRequest){
        return input.matches(intentName("CourseDescIntent"));
    }

    public Optional<Response> handle(HandlerInput input, IntentRequest intentRequest){
        String speechText = "";
        try {
            IntentRequest request = (IntentRequest) input.getRequestEnvelope().getRequest();
            Intent intent = request.getIntent();
            Map<String, Slot> slots = intent.getSlots();
            Slot slot = slots.get("course_name");
            String name = slot.getValue();
            if(name == null || name.isEmpty()){
                Map<String,Object> sessionAttr = input.getAttributesManager().getSessionAttributes();
                if(sessionAttr.containsKey("course_name")){
                    name = (String)sessionAttr.get("course_name");
                }
                else{
                    return input.getResponseBuilder().addElicitSlotDirective("course_name",intentRequest.getIntent()).withSpeech("Which course you want to query?").build();
                }
            }
            else{
                input.getAttributesManager().setSessionAttributes(Collections.singletonMap("course_name",name));
            }

            AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
            Map<String, AttributeValue> exprAttr = new HashMap<String, AttributeValue>();
            exprAttr.put(":name", new AttributeValue().withS(name));

            ScanRequest scanReq = new ScanRequest()
                    .withTableName("t_csd_course")
                    .withFilterExpression("course_name = :name")
                    .withProjectionExpression("description")
                    .withExpressionAttributeValues(exprAttr);

            ScanResult result = client.scan(scanReq);
            List<Map<String, AttributeValue>> items = result.getItems();
            if (items.size() == 0) {
                return input.getResponseBuilder().addElicitSlotDirective("course_name",intent).withSpeech("Sorry, course "+name+" is not available this term. Would you like to try another course?").build();
            } else {
                speechText = items.get(0).get("description").getS();
            }
        }
        catch(Exception ex){
            speechText += ex.getStackTrace();
        }

        return input.getResponseBuilder().withSpeech(speechText).withSimpleCard("CSD Assistant", speechText).build();
    }
}
