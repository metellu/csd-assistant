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
import com.uwo.csd.util.IntentHelper;

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
        String instructor = "";
        String title      = "";
        String interests  = "";
        String gender     = "";
        try {
            IntentRequest request = (IntentRequest) input.getRequestEnvelope().getRequest();
            Map<String, Slot> slots = request.getIntent().getSlots();

            instructor = IntentHelper.getSpecifiedSlotValueIfExists(slots,"instructor_fullname");
            if ( instructor.isEmpty() ) {
                Map<String, Object> attr = input.getAttributesManager().getSessionAttributes();
                if (attr.containsKey("instructor")) {
                    instructor = (String)attr.get("instructor");
                }
                else{
                    return input.getResponseBuilder().addElicitSlotDirective("instructor",intentRequest.getIntent()).withSpeech("Please tell me which instructor you would like to know?").build();
                }
            }
            Map<String, AttributeValue> exprAttr = new HashMap<String, AttributeValue>();

            String[] nameArr = instructor.split("\\s");
            if ( nameArr.length == 1 ){
                exprAttr.put(":name",new AttributeValue().withS(nameArr[0].toLowerCase()));
                List<Map<String,AttributeValue>> items = IntentHelper.DBQueryByPartialInstructorName(exprAttr,"fullname");
                if( items.size() == 0 ){
                    return input.getResponseBuilder().addElicitSlotDirective("instructor_fullname",intentRequest.getIntent()).withSpeech("Sorry, the name you provided does not match any instructor. Please tell me which instructor you would like to know.").build();
                }
                else{
                    instructor = items.get(0).get("fullname").getS();
                    Slot slot = Slot.builder().withName("instructor_fullname").withValue(instructor).build();
                    Intent intent = Intent.builder().withName("InstructorIntent").putSlotsItem("instructor_fullname",slot).build();
                    return input.getResponseBuilder().addConfirmSlotDirective("instructor_fullname",intent).withSpeech("Did you mean professor"+instructor+"?").build();
                }
            }

            exprAttr.put(":name", new AttributeValue().withS(instructor.toLowerCase()));
            List<Map<String, AttributeValue>> items = IntentHelper.DBQueryByInstructorName(exprAttr,"title, research_interests, gender");


            if (items.size() == 0) {
                return input.getResponseBuilder().addElicitSlotDirective("instructor_fullname",intentRequest.getIntent()).withSpeech("Please tell me which instructor you would like to know?").build();
            } else {
                Map<String, AttributeValue> item = items.get(0);
                if (item.containsKey("research_interests")) {
                    interests = item.get("research_interests").getS();
                }
                if( item.containsKey("title") ){
                    title = item.get("title").getS();
                    if( title.startsWith("assistant") ){
                        title = "an "+title;
                    }
                    else{
                        title = "a "+title;
                    }
                }
                if( item.containsKey("gender") ){
                    gender = item.get("gender").getS();
                    gender = gender.equals("male")?"His":"Her";
                }
                speechText = instructor +" is "+title+" of the department of computer science at the Western University. "+ gender +" research interests includes "+interests;
            }
        }
        catch(Exception ex){
            speechText += ex.getMessage();
        }
        return input.getResponseBuilder().withSimpleCard("CSD Assistant",speechText).withSpeech(speechText).withShouldEndSession(false).build();
    }
}
