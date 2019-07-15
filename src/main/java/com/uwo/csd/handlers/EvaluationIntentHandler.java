package com.uwo.csd.handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.impl.IntentRequestHandler;
import com.amazon.ask.model.Intent;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.Slot;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.uwo.csd.util.IntentHelper;

import java.nio.file.attribute.AttributeView;
import java.util.*;

import static com.amazon.ask.request.Predicates.intentName;

public class EvaluationIntentHandler implements IntentRequestHandler {
    public boolean canHandle(HandlerInput input, IntentRequest intentRequest){
        return input.matches(intentName("EvaluationIntent"));
    }
    public Optional<Response> handle(HandlerInput input, IntentRequest intentRequest){
        String speechText = "";
        String courseName = "";
        String courseCode = "";
        String degree     = "";
        String timeConfirm = "";
        try{
            Map<String, Slot> slots = intentRequest.getIntent().getSlots();
            courseName = IntentHelper.getCourseNameIfExists(slots);
            courseCode = IntentHelper.getCourseCodeIfExists(slots);
            degree     = IntentHelper.getSpecifiedSlotValueIfExists(slots,"degree");
            timeConfirm = IntentHelper.getSpecifiedSlotValueIfExists(slots,"time_confirm");

            if( IntentHelper.isStringValid(timeConfirm) ){
                if( timeConfirm.equalsIgnoreCase("yes") ){
                    if( !IntentHelper.isStringValid(courseName) ){
                        if( input.getAttributesManager().getSessionAttributes().containsKey("course_name") ) {
                            courseName = (String) input.getAttributesManager().getSessionAttributes().get("course_name");
                        }
                    }
                    Slot nameSlot = Slot.builder().withName("course_name").withValue(courseName).build();
                    Slot codeSlot = Slot.builder().withName("course_code").withValue("").build();
                    Slot confirmSlot = Slot.builder().withName("eligibility_confirm").withValue("").build();
                    Intent intent = Intent.builder().withName("TimeTableIntent").putSlotsItem("course_name",nameSlot).putSlotsItem("course_code",codeSlot).putSlotsItem("eligibility_confirm",confirmSlot).build();
                    return input.getResponseBuilder().addDelegateDirective(intent).build();
                }
                else{
                    Map<String,Object> attr = input.getAttributesManager().getSessionAttributes();
                    attr.remove("origin");
                    input.getAttributesManager().setSessionAttributes(attr);
                    speechText = "Ok. If you want to query about other courses, pleaes let me know the course name.";
                }
            }
            else {

                Map<String, Object> sessionAttr = input.getAttributesManager().getSessionAttributes();
                if (!IntentHelper.isStringValid(degree)) {
                    return input.getResponseBuilder().addElicitSlotDirective("degree", intentRequest.getIntent()).withSpeech("In order to proceed, I would like to know if you are a graduate student or undergraduate student.").build();
                } else {
                    degree = slots.get("degree").getResolutions().getResolutionsPerAuthority().get(0).getValues().get(0).getValue().getName();
                }

                if (!IntentHelper.isStringValid(courseName) && !IntentHelper.isStringValid(courseCode)) {
                    if (sessionAttr.containsKey("course_name")) {
                        courseName = (String) sessionAttr.get("course_name");
                    } else {
                        speechText = "Please tell which course you would like to know.";
                        return input.getResponseBuilder().addElicitSlotDirective("course_name", intentRequest.getIntent()).withSpeech(speechText).build();
                    }
                }

                Map<String, AttributeValue> expr = new HashMap<>();
                List<Map<String, AttributeValue>> items = new ArrayList<>();
                if (IntentHelper.isStringValid(courseName)) {
                    courseName = slots.get("course_name").getResolutions().getResolutionsPerAuthority().get(0).getValues().get(0).getValue().getName();
                    expr.put(":courseName", new AttributeValue().withS(courseName));
                    items = IntentHelper.DBQueryByCourseName(expr, "evaluation, course_name, course_code, description, instructor");
                } else if (IntentHelper.isStringValid(courseCode)) {
                    expr.put(":courseCode", new AttributeValue().withS(courseCode));
                    items = IntentHelper.DBQueryByCourseCode(expr, "evaluation, course_name, course_code, description, instructor");
                }
                if (items.size() == 0) {
                    speechText = "Sorry, course " + courseName + " may not be available this term. In order to avoid ambiguity, please provide me with the course name.";
                    return input.getResponseBuilder().addElicitSlotDirective("course_name", intentRequest.getIntent()).withSpeech(speechText).build();
                } else {
                    Map<String, AttributeValue> item = items.get(0);
                    String instructorConcat = IntentHelper.formInstructorString(item.get("instructor").getL());
                    courseName = item.get("course_name").getS();
                    courseCode = IntentHelper.formCourseCodeString(item.get("course_code").getL());
                    sessionAttr.clear();
                    sessionAttr.put("course_name", courseName);
                    sessionAttr.put("course_code", courseCode);
                    sessionAttr.put("course_desc", item.get("description").getS());
                    sessionAttr.put("degree", degree);
                    sessionAttr.put("instructor", instructorConcat);
                    sessionAttr.put("origin","EvaluationIntent");
                    input.getAttributesManager().setSessionAttributes(sessionAttr);
                    if( item.containsKey("evaluation") && item.get("evaluation").getM()!=null ) {
                        Map<String, AttributeValue> evals = item.get("evaluation").getM();
                        if (evals.containsKey(degree)) {
                            speechText = "Components of the course grade and their weighting are as follows:";
                            speechText += " " + evals.get(degree).getS();
                        }
                        else{
                            speechText = "Sorry, I didn't find any evaluation method list for this course";
                        }
                    }
                    if (sessionAttr.containsKey("origin")) {
                        speechText += ". If you're still interested in the course, I can check the course time for you. Do you want me to proceed ?";
                        return input.getResponseBuilder().addElicitSlotDirective("time_confirm", intentRequest.getIntent()).withSpeech(speechText).build();
                    }
                }
            }
        }
        catch(Exception ex){
            speechText = ex.toString();
        }
        return input.getResponseBuilder().withSimpleCard("CSD Assistant",speechText).withSpeech(speechText).withShouldEndSession(false).build();
    }
}
