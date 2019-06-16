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
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.amazon.ask.request.Predicates.intentName;

public class InstructorIntentHandler implements IntentRequestHandler{
    public boolean canHandle(HandlerInput input, IntentRequest intentRequest){
        return input.matches(intentName("InstructorIntent"));
    }
    public Optional<Response> handle(HandlerInput input, IntentRequest intentRequest){
        String speechText = "";
        try {
            IntentRequest request = (IntentRequest) input.getRequestEnvelope().getRequest();
            Map<String, Slot> slots = request.getIntent().getSlots();
            Slot slot = slots.get("instructor_fullname");
            if (slot != null) {
                String instructor = slot.getValue();
                if (instructor != null && !instructor.isEmpty()) {
                    Map<String, AttributeValue> exprAttr = new HashMap<String, AttributeValue>();
                    exprAttr.put(":name", new AttributeValue().withS(instructor.toLowerCase()));
                    AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
                    ScanRequest scanReq = new ScanRequest()
                            .withTableName("t_csd_instructors")
                            .withFilterExpression("fullname = :name")
                            .withProjectionExpression("research_interests")
                            .withExpressionAttributeValues(exprAttr);

                    ScanResult result = client.scan(scanReq);
                    if (result != null) {
                        List<Map<String, AttributeValue>> items = result.getItems();
                        if (items.size() == 0) {
                            speechText = "Error occurs: retrieved 0 items."+instructor;
                        } else {
                            Map<String, AttributeValue> item = items.get(0);
                            if (item.containsKey("research_interests")) {
                                speechText = item.get("research_interests").getS();
                            } else {
                                speechText = "Error occurs: returned item does not contain info";
                            }
                        }
                    }
                }
            } else {
                speechText = "Error occurs: empty slot";
            }
        }
        catch(Exception ex){
            speechText += ex.getMessage();
        }
        return input.getResponseBuilder().withSimpleCard("CSD Assistant",speechText).withSpeech(speechText).withShouldEndSession(false).build();
    }
}
