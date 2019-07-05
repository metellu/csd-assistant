package com.uwo.csd.handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.impl.IntentRequestHandler;
import com.amazon.ask.model.Intent;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.Slot;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;

import java.util.*;

import static com.amazon.ask.request.Predicates.intentName;

public class EventIntentHandler implements IntentRequestHandler {
    public boolean canHandle(HandlerInput input, IntentRequest intentRequest){
        return input.matches(intentName("AMAZON.AddAction<object@Event>"));
    }
    public Optional<Response> handle(HandlerInput input, IntentRequest intentRequest) {
        String speechText = "";
        String courseName = "";
        List<AttributeValue> location   = new ArrayList<>();
        Map<String, AttributeValue> times = new HashMap<>();
        Map<String, Object> sessionAttr = input.getAttributesManager().getSessionAttributes();
        Map<String,Slot> slots =intentRequest.getIntent().getSlots();
        if( !intentRequest.getDialogState().toString().equals("COMPLETED") && !slots.containsKey("location.name")){
            if( sessionAttr.containsKey("course_name") ) {
                courseName = (String) sessionAttr.get("course_name");
            }
            if( sessionAttr.containsKey("course_time") ){
                times = (Map<String,AttributeValue>)sessionAttr.get("course_time");
            }
            if( sessionAttr.containsKey("location") ){
                location = (List<AttributeValue>)sessionAttr.get("location");
            }

            slots = new HashMap<>();
            Slot nameSlot = Slot.builder().withName("object.name").withValue(courseName).build();
            slots.put("object.name",nameSlot);
            Slot locationSlot = Slot.builder().withName("object.location.name").withValue("TC205").build();
            slots.put("object.location.name",locationSlot);
            Slot collectionSlot = Slot.builder().withName("targetCollection.type").withValue("calendar").build();
            slots.put("targetCollection.type",collectionSlot);
            Slot collectionOwnerSlot = Slot.builder().withName("targetCollection.owner.name").withValue("my").build();
            slots.put("targetCollection.owner.name",collectionOwnerSlot);
            Slot dateSlot = Slot.builder().withName("object.startDate").withValue("every Friday").build();
            slots.put("object.startDate",dateSlot);
            Slot timeSlot = Slot.builder().withName("object.startTime").withValue("2:30 PM").build();
            slots.put("object.startTime",timeSlot);
            Slot typeSlot = Slot.builder().withName("object.type").withValue("course").build();
            slots.put("object.type",typeSlot);
            Slot descTypeSlot = Slot.builder().withName("object.description.type").withValue("").build();
            slots.put("object.description.type",descTypeSlot);
            Slot attendeeSlot = Slot.builder().withName("object.attendee.name").withValue("me").build();
            slots.put("object.attendee.name",attendeeSlot);
            Slot ownerSlot = Slot.builder().withName("object.owner.name").withValue("me").build();
            slots.put("object.owner.name",ownerSlot);
            Slot eventTypeSlot = Slot.builder().withName("object.event.type").withValue("course").build();
            slots.put("object.event.type",eventTypeSlot);

            Intent intent = Intent.builder().withSlots(slots).withName("AMAZON.AddAction<object@Event>").build();
            return input.getResponseBuilder().addDelegateDirective(intent).build();
        }
        return input.getResponseBuilder().withSpeech("Done").withSimpleCard("CSD Assistant","Done").build();
    }

}
