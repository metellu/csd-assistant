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
        String degree     = "";
        String instructor_confirm = "";
        String startover_confirm = "";
        List<String> prerequisite = new ArrayList<>();

        Map<String,Slot> slots = intentRequest.getIntent().getSlots();
        courseName = IntentHelper.getSpecifiedSlotValueIfExists(slots,"course_name");
        if( IntentHelper.isStringValid(courseName) ) {
            if (slots.get("course_name").getResolutions() != null) {
                courseName = slots.get("course_name").getResolutions().getResolutionsPerAuthority().get(0).getValues().get(0).getValue().getName();
            }
        }
        courseCode = IntentHelper.getCourseCodeIfExists(slots);
        degree     = IntentHelper.getSpecifiedSlotValueIfExists(slots,"degree");

        if( !IntentHelper.isStringValid(degree) ) {
            if (input.getAttributesManager().getSessionAttributes().containsKey("degree")) {
                degree = (String) input.getAttributesManager().getSessionAttributes().get("degree");
            } else {
                return input.getResponseBuilder().addElicitSlotDirective("degree", intentRequest.getIntent()).withSpeech("In order to proceed, I would like to know if you are a graduate student or undergraduate student.").build();
            }
        }
        else{
            if( slots.get("degree").getResolutions()!=null ) {
                degree = slots.get("degree").getResolutions().getResolutionsPerAuthority().get(0).getValues().get(0).getValue().getName();
            }
        }
        if (slots.get("prerequisites_confirm").getResolutions() != null) {
            confirm = slots.get("prerequisites_confirm").getResolutions().getResolutionsPerAuthority().get(0).getValues().get(0).getValue().getName();
        }
        if (slots.get("instructor_info_confirm").getResolutions() != null) {
            instructor_confirm = slots.get("instructor_info_confirm").getResolutions().getResolutionsPerAuthority().get(0).getValues().get(0).getValue().getName();
        }
        if (slots.get("startover_confirm").getResolutions() != null) {
            startover_confirm = slots.get("startover_confirm").getResolutions().getResolutionsPerAuthority().get(0).getValues().get(0).getValue().getName();
        }

        if( IntentHelper.isStringValid(startover_confirm) ){
            if( startover_confirm.equalsIgnoreCase("yes") ){
                Slot nameSlot = Slot.builder().withName("course_name").withValue("").build();
                Slot codeSlot = Slot.builder().withName("course_code").withValue("").build();
                Slot confirmSlot = Slot.builder().withName("eval_confirm").withValue("").build();
                Intent intent = Intent.builder().withName("CourseDescIntent").putSlotsItem("course_name",nameSlot).putSlotsItem("course_code",codeSlot).putSlotsItem("eval_confirm",confirmSlot).build();
                return input.getResponseBuilder().addDelegateDirective(intent).build();
            }
            else{
                speechText = "Ok. Thank you for using CSD Assistant. Goodbye.";
                return input.getResponseBuilder().withSpeech(speechText).withSimpleCard("CSD Assistant",speechText).build();
            }
        }

        if( IntentHelper.isStringValid(instructor_confirm) && instructor_confirm.equalsIgnoreCase("yes") ){
            Map<String,Object> sessionAttr = input.getAttributesManager().getSessionAttributes();
            if( sessionAttr.containsKey("instructor") ){
                String instructors = (String)sessionAttr.get("instructor");
                String[] inst = instructors.split("\\|");
                speechText = "You can reach ";
                for(String instr:inst) {
                    Map<String, AttributeValue> exprAttr = new HashMap<>();
                    exprAttr.put(":name",new AttributeValue().withS(instr));
                    List<Map<String,AttributeValue>> items = IntentHelper.DBQueryByInstructorName(exprAttr,"fullname,email");
                    if( items.size()==1 ){
                        speechText += "Professor "+IntentHelper.capitalizeName(items.get(0).get("fullname").getS())+" at "+items.get(0).get("email").getS()+" and ";
                    }
                    speechText = speechText.substring(0,speechText.lastIndexOf("and"));
                }
            }
            return input.getResponseBuilder().withSpeech(speechText).withSimpleCard("CSD Assistant",speechText).withShouldEndSession(false).build();
        }

        List<String> confirms = new ArrayList<>();

        Map<String,Object> attr = input.getAttributesManager().getSessionAttributes();
        if( attr.containsKey("prerequisites") ){
            prerequisite = (List<String>) attr.get("prerequisites");
        }
        if( attr.containsKey("confirms") ){
            confirms = (List<String>)attr.get("confirms");
        }

        if( courseCode.isEmpty() && courseName.isEmpty() ){

            if( attr.containsKey("course_name") ){
                courseName = (String)attr.get("course_name");
            }
            else{
                return input.getResponseBuilder().addElicitSlotDirective("course_name",intentRequest.getIntent()).withSpeech("Please tell me which course you would like to check?").build();
            }
        }

        if( IntentHelper.isStringValid(confirm) ) {
            confirms.add(confirm);
            attr.put("confirms",confirms);
            if (confirms.stream().anyMatch(str -> IntentHelper.isStringValid(str))) {
                if (confirms.stream().anyMatch(str -> str.equalsIgnoreCase("no"))) {
                    speechText = "Unfortunately, it appears you don't meet all the prerequisites. You probably will not be allowed to enroll. However, you still can communicate with the instructor to gain his or her approval for enrollment. Would you like to know the professor's contact info?";
                    confirms.clear();
                    attr.replace("confirms",confirms);
                    prerequisite.clear();
                    attr.replace("prerequisites",prerequisite);
                    input.getAttributesManager().setSessionAttributes(attr);
                    return input.getResponseBuilder().addElicitSlotDirective("instructor_info_confirm",intentRequest.getIntent()).withSpeech(speechText).build();
                }
                else {

                    if (prerequisite.size() > 0) {
                        String cond = prerequisite.remove(0);
                        attr.replace("prerequisites", prerequisite);
                        input.getAttributesManager().setSessionAttributes(attr);
                        return input.getResponseBuilder().addElicitSlotDirective("prerequisites_confirm", intentRequest.getIntent()).withSpeech(cond).build();
                    } else {
                        speechText = "Congratulations. You meet all the prerequisites. ";
                        int month = IntentHelper.getCurrentMonth();
                        String curTerm = IntentHelper.determineEnrollmentTerm(month);
                        String term = "";
                        if( attr.containsKey("term") ) {
                            term = (String)attr.get("term");
                        }
                        if(!term.equalsIgnoreCase(curTerm)){
                            speechText += "But this course is scheduled for "+term +" term. So you may not be able to enroll at this moment.";
                        }
                        else{
                            speechText += "You may be able to enroll in this course. To sign up for the course, you are required to login student.uwo.ca and finish the enrollment on that website.";
                        }

                    }
                }
            }
        }
        else {
            //AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
            List<Map<String, AttributeValue>> items = new ArrayList<>();
            try {
                if ( IntentHelper.isStringValid(courseName) ) {
                    Map<String, AttributeValue> exprAttr = new HashMap<String, AttributeValue>();
                    exprAttr.put(":courseName", new AttributeValue().withS(courseName));
                    items = IntentHelper.DBQueryByCourseName(exprAttr,"prerequisites,course_name,course_code,instructor,term");

                    if (items.size() == 0) {
                        return input.getResponseBuilder().addElicitSlotDirective("course_name", intentRequest.getIntent()).withSpeech("Sorry, the course you're looking for seems unavailable this year. Please provide me with a course name, if you would like to try another course.").build();
                    }
                }
                else if( IntentHelper.isStringValid(courseCode) ){
                    Map<String, AttributeValue> exprAttr = new HashMap<String, AttributeValue>();
                    exprAttr.put(":courseCode", new AttributeValue().withS(courseCode));
                    items = IntentHelper.DBQueryByCourseCode(exprAttr,"prerequisites,course_name,course_code,term");
                    if (items.size() == 0) {
                        return input.getResponseBuilder().addElicitSlotDirective("course_name", intentRequest.getIntent()).withSpeech("Sorry, the course you're looking for seems unavailable this year. Please provide me with a course name, if you would like to try another course.").build();
                    }
                }
                Map<String, AttributeValue> item = items.get(0);
                if (item.containsKey("prerequisites")) {

                    Map<String,AttributeValue> attrVals = item.get("prerequisites").getM();
                    if( attrVals.containsKey(degree) ){
                        String term = item.get("term").getS();
                        List<AttributeValue> attrList = attrVals.get(degree).getL();
                        for( AttributeValue attrVal: attrList ){
                            prerequisite.add(attrVal.getS());
                        }
                        speechText = "According to the course description of " + courseName + ", students are expected to meet all the prerequisites to enroll in the course. Please provide yes or no answers to the following questions. Then I can help you determine your eligibility. " + prerequisite.get(0);
                        prerequisite.remove(0);
                        String instructor = IntentHelper.formInstructorString(item.get("instructor").getL());
                        attr.put("prerequisites", prerequisite);
                        attr.put("instructor",instructor);
                        attr.put("term",term);
                        input.getAttributesManager().setSessionAttributes(attr);
                        return input.getResponseBuilder().addElicitSlotDirective("prerequisites_confirm", intentRequest.getIntent()).withSpeech(speechText).build();
                    }
                    else{
                        speechText = "It appears that there is no prequisites list for a "+degree+" student. ";
                        int month = IntentHelper.getCurrentMonth();
                        String curTerm = IntentHelper.determineEnrollmentTerm(month);
                        String term = "";
                        if( attr.containsKey("term") ) {
                            term = (String)attr.get("term");
                        }
                        if(!term.equalsIgnoreCase(curTerm)){
                            speechText += "But this course is scheduled for "+term +" term. So you may not be able to enroll at this moment.";
                        }
                        else{
                            speechText += "You may be able to enroll in this course. To sign up for the course, you are required to login student.uwo.ca and finish the enrollment on that website.";
                        }
                    }
                    courseName = (courseName != null && !courseName.isEmpty()) ? courseName : item.get("course_name").getS();
                    Map<String, Object> sessionAttr = input.getAttributesManager().getSessionAttributes();
                    sessionAttr.put("course_name", courseName);
                    input.getAttributesManager().setSessionAttributes(sessionAttr);
                    //preqConcat = preqConcat.substring(0, preqConcat.lastIndexOf(","));
                    //preqConcat = preqConcat.substring(0,preqConcat.lastIndexOf(","))+" and " + preqConcat.substring(preqConcat.lastIndexOf(",")+2);


                }

            }
            catch(Exception ex){
                speechText = ex.getMessage();
                log.error(ex.getMessage());
            }
        }
        speechText += " Do you have other courses you would like to check?";
        return input.getResponseBuilder().addElicitSlotDirective("startover_confirm",intentRequest.getIntent()).withSpeech(speechText).build();
    }
}
