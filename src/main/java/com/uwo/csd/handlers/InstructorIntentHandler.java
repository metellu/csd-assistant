package com.uwo.csd.handlers;
import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.impl.IntentRequestHandler;
import com.amazon.ask.model.Intent;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.Slot;
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
        String courseName = "";
        String courseCode = "";
        try {
            IntentRequest request = (IntentRequest) input.getRequestEnvelope().getRequest();
            Map<String, Slot> slots = request.getIntent().getSlots();

            instructor = IntentHelper.getSpecifiedSlotValueIfExists(slots,"instructor_fullname");
            if ( instructor.isEmpty() ) {
                Map<String, Object> attr = input.getAttributesManager().getSessionAttributes();
                if (attr.containsKey("instructor")) {
                    instructor = (String)attr.get("instructor");
                    if(instructor.indexOf("|")>0){
                        String[] tutors = instructor.split("\\|");
                        speechText = "I found this course is co-hosted by professor "+IntentHelper.capitalizeName(tutors[0])+" and professor "+ IntentHelper.capitalizeName(tutors[1])+". Please tell me which instructor you would like to know? ";
                        return input.getResponseBuilder().addElicitSlotDirective("instructor_fullname",intentRequest.getIntent()).withSpeech(speechText).build();
                    }
                }
                else{
                    return input.getResponseBuilder().addElicitSlotDirective("instructor_fullname",intentRequest.getIntent()).withSpeech("Please tell me which instructor you would like to know?").build();
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
                    return input.getResponseBuilder().addConfirmSlotDirective("instructor_fullname",intent).withSpeech("Did you mean professor "+ IntentHelper.capitalizeName(instructor) +"?").build();
                }
            }

            exprAttr.put(":name", new AttributeValue().withS(instructor.toLowerCase()));
            List<Map<String, AttributeValue>> items = IntentHelper.DBQueryByInstructorName(exprAttr,"title, research_interests, gender");
            List<Map<String, AttributeValue>> courseItems = IntentHelper.DBQueryCourseByInstructor(exprAttr,"course_name, course_code, time_location, description");

            if (items.size() == 0) {
                return input.getResponseBuilder().addElicitSlotDirective("instructor_fullname",intentRequest.getIntent()).withSpeech("Please tell me which instructor you would like to know?").build();
            } else {
                Map<String, AttributeValue> item = items.get(0);
                if (item.containsKey("research_interests")) {
                    interests = item.get("research_interests").getS();
                }
                if (item.containsKey("title")) {
                    title = item.get("title").getS();
                    if (title.startsWith("assistant")) {
                        title = "an " + title;
                    } else {
                        title = "a " + title;
                    }
                }
                if (item.containsKey("gender")) {
                    gender = item.get("gender").getS();
                    gender = gender.equals("male") ? "His" : "Her";
                }
                speechText = instructor + " is " + title + " of the department of computer science at the Western University. " + gender + " research interests includes " + interests;
                speechText += ". Currently he is teaching ";
                for (int i = 0; i < courseItems.size(); i++) {
                    if (courseItems.get(i).containsKey("course_name")) {
                        courseName = courseItems.get(i).get("course_name").getS();
                        List<AttributeValue> courseCodes = courseItems.get(i).get("course_code").getL();
                        if (courseCodes.size() > 1) {
                            courseCode = "CS" + courseCodes.get(0).getS() + "/" + courseCodes.get(1).getS();
                        } else {
                            courseCode = courseCodes.get(0).getS();
                        }
                        Map<String, Object> attr = input.getAttributesManager().getSessionAttributes();
                        attr.put("course_name", courseName);
                        attr.put("course_code", courseCode);
                        attr.put("course_desc", courseItems.get(i).get("description").getS());
                        input.getAttributesManager().setSessionAttributes(attr);
                        speechText += courseCode + " " + courseName + " and ";
                    }
                }
                speechText = speechText.substring(0,speechText.lastIndexOf("and"));
            }
        }
        catch(Exception ex){
            speechText += ex.getMessage();
        }
        return input.getResponseBuilder().withSimpleCard("CSD Assistant",speechText).withSpeech(speechText).withShouldEndSession(false).build();
    }
}
