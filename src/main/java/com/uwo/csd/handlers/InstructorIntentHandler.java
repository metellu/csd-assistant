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
        String pronoun    = "";
        String courseName = "";
        String courseCode = "";
        String confirm    = "";
        boolean noInput   = false;
        try {
            IntentRequest request = (IntentRequest) input.getRequestEnvelope().getRequest();
            Map<String, Slot> slots = request.getIntent().getSlots();
            instructor = IntentHelper.getSpecifiedSlotValueIfExists(slots,"instructor_fullname");
            confirm    = IntentHelper.getSpecifiedSlotValueIfExists(slots,"confirm");
            if( IntentHelper.isStringValid(confirm) ){
                confirm = slots.get("confirm").getResolutions().getResolutionsPerAuthority().get(0).getValues().get(0).getValue().getName();
                if( confirm.equalsIgnoreCase("yes") ) {
                    if (input.getAttributesManager().getSessionAttributes().containsKey("course_name")) {
                        courseName = (String) input.getAttributesManager().getSessionAttributes().get("course_name");
                    }
                    else{
                        courseName = "";
                    }
                    Slot nameSlot = Slot.builder().withName("course_name").withValue(courseName).build();
                    Slot codeSlot = Slot.builder().withName("course_code").withValue("").build();
                    Slot confirmSlot = Slot.builder().withName("eligibility_confirm").withValue("").build();
                    Intent timeIntent = Intent.builder().putSlotsItem("course_name", nameSlot).putSlotsItem("course_code", codeSlot).putSlotsItem("eligibility_confirm", confirmSlot).withName("TimeTableIntent").build();

                    return input.getResponseBuilder().addDelegateDirective(timeIntent).build();
                }
                else{
                    Map<String,Object> attr = input.getAttributesManager().getSessionAttributes();
                    attr.remove("origin");
                    input.getAttributesManager().setSessionAttributes(attr);
                    speechText = "Ok. If you want to query about other courses, please let me know the course name.";
                    return input.getResponseBuilder().withSpeech(speechText).withSimpleCard("CSD Assistant",speechText).withShouldEndSession(false).build();
                }
            }
            else {
                if (instructor.isEmpty()) {
                    Map<String, Object> attr = input.getAttributesManager().getSessionAttributes();
                    if (attr.containsKey("instructor")) {
                        noInput = true;
                        instructor = (String) attr.get("instructor");

                    } else {
                        return input.getResponseBuilder().addElicitSlotDirective("instructor_fullname", intentRequest.getIntent()).withSpeech("Please tell me which instructor you would like to know?").build();
                    }
                }
                if (instructor.indexOf("|") > 0) {
                    String[] tutors = instructor.split("\\|");
                    speechText = "I found this course is co-hosted by professor " + IntentHelper.capitalizeName(tutors[0]) + " and professor " + IntentHelper.capitalizeName(tutors[1]) + ". Please tell me which instructor you would like to know. ";
                    return input.getResponseBuilder().addElicitSlotDirective("instructor_fullname", intentRequest.getIntent()).withSpeech(speechText).build();
                }
                Map<String, AttributeValue> exprAttr = new HashMap<String, AttributeValue>();

                String[] nameArr = instructor.split("\\s");
                if (nameArr.length == 1) {
                    exprAttr.put(":name", new AttributeValue().withS(nameArr[0].toLowerCase()));
                    List<Map<String, AttributeValue>> items = IntentHelper.DBQueryByPartialInstructorName(exprAttr, "fullname");
                    if (items.size() == 0) {
                        return input.getResponseBuilder().addElicitSlotDirective("instructor_fullname", intentRequest.getIntent()).withSpeech("Sorry, the name you provided does not match any instructor. Please tell me which instructor you would like to know.").build();
                    } else {
                        instructor = items.get(0).get("fullname").getS();
                        Slot slot = Slot.builder().withName("instructor_fullname").withValue(instructor).build();
                        Slot confirmSlot = Slot.builder().withName("confirm").withValue("").build();
                        Intent intent = Intent.builder().withName("InstructorIntent").putSlotsItem("confirm",confirmSlot).putSlotsItem("instructor_fullname", slot).build();
                        return input.getResponseBuilder().addConfirmSlotDirective("instructor_fullname", intent).withSpeech("Did you mean professor " + IntentHelper.capitalizeName(instructor) + "?").build();
                    }
                }
                exprAttr.put(":name", new AttributeValue().withS(instructor.toLowerCase()));
                List<Map<String, AttributeValue>> items = IntentHelper.DBQueryByInstructorName(exprAttr, "title, research_interests, gender");
                List<Map<String, AttributeValue>> courseItems = IntentHelper.DBQueryCourseByInstructor(exprAttr, "course_name, course_code, time_location, description, term");

                if (items.size() == 0) {
                    speechText = "Sorry, it seems the instructor you're looking for is not working at Computer Science Department any more. If you would like to look up another instructor, please give me the instructor's full name.";
                    return input.getResponseBuilder().addElicitSlotDirective("instructor_fullname", intentRequest.getIntent()).withSpeech(speechText).build();
                } else {

                    Map<String, AttributeValue> item = items.get(0);
                    if (item.containsKey("research_interests")) {
                        interests = item.get("research_interests").getS();
                    }
                    if (item.containsKey("title")) {
                        title = item.get("title").getS();
                        if (title.startsWith("a")) {
                            title = "an " + title;
                        } else {
                            title = "a " + title;
                        }
                    }
                    if (item.containsKey("gender")) {
                        gender = item.get("gender").getS();
                        gender = gender.equals("male") ? "His" : "Her";
                        pronoun = gender.equals("His") ? "he" : "she";
                    }

                    speechText = "Dr." + IntentHelper.capitalizeName(instructor) + " is " + title + " of the department of computer science at the Western University. " + gender + " research interests includes " + interests;
                    speechText += ". Currently " + pronoun + " is teaching ";

                    for (int i = 0; i < courseItems.size(); i++) {
                        if (courseItems.get(i).containsKey("course_name")) {
                            courseName = courseItems.get(i).get("course_name").getS();
                            List<AttributeValue> courseCodes = courseItems.get(i).get("course_code").getL();
                            if (courseCodes.size() > 1) {
                                courseCode = "CS" + courseCodes.get(0).getS() + "/" + courseCodes.get(1).getS();
                            } else {
                                courseCode = courseCodes.get(0).getS();
                            }
                            if (noInput == false) {
                                Map<String, Object> attr = input.getAttributesManager().getSessionAttributes();
                                attr.put("course_name", courseName);
                                attr.put("course_code", courseCode);
                                attr.put("course_desc", courseItems.get(i).get("description").getS());
                                attr.put("instructor", instructor);
                                input.getAttributesManager().setSessionAttributes(attr);
                            }
                            speechText += courseCode + " " + IntentHelper.capitalizeName(courseName) + " in " + courseItems.get(i).get("term").getS() + " term and ";
                        }
                    }
                    speechText = speechText.substring(0, speechText.lastIndexOf("and"));

                    if (input.getAttributesManager().getSessionAttributes().containsKey("origin")) {
                        speechText += ". Now if you're still intersted in the course, I can show you the course time. Do you want me to proceed with the course time lookup?";
                        Map<String, Object> attr = input.getAttributesManager().getSessionAttributes();
                        return input.getResponseBuilder().addElicitSlotDirective("confirm", intentRequest.getIntent()).withSpeech(speechText).build();
                    }
                }
            }
        }
        catch(Exception ex){
            speechText += ex.toString()+ex.getMessage();
        }
        return input.getResponseBuilder().withSimpleCard("CSD Assistant",speechText).withSpeech(speechText).withShouldEndSession(false).build();
    }
}
