package com.uwo.csd.handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.impl.IntentRequestHandler;
import com.amazon.ask.model.Intent;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.Slot;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.uwo.csd.entity.Course;
import com.uwo.csd.util.IntentHelper;
import java.util.*;

import static com.amazon.ask.request.Predicates.intentName;

public class CourseDescIntentHandler implements IntentRequestHandler {
    public boolean canHandle(HandlerInput input, IntentRequest intentRequest){
        return input.matches(intentName("CourseDescIntent"));
    }

    public Optional<Response> handle(HandlerInput input, IntentRequest intentRequest){
        String speechText = "";
        String name = "";
        String code = "";
        String timeConfirm = "";
        String eligStr = "";
        Course course = Course.builder().build();
        boolean useCache = false;
        try {
            IntentRequest request = (IntentRequest) input.getRequestEnvelope().getRequest();
            Intent intent = request.getIntent();
            Map<String, Slot> slots = intent.getSlots();

            name = IntentHelper.getCourseNameIfExists(slots);
            code = IntentHelper.getCourseCodeIfExists(slots);
            timeConfirm = IntentHelper.getSpecifiedSlotValueIfExists(slots,"eval_confirm");
            //eligStr = IntentHelper.getSpecifiedSlotValueIfExists(slots,"eligibility_confirm");
            //if( IntentHelper.isStringValid(eligStr) && (eligStr.equals("yes") || eligStr.equals("yeap"))) {
            //    Intent eligIntent = Intent.builder().withName("EnrollmentEligibilityIntent").build();
            //    return input.getResponseBuilder().addDelegateDirective(eligIntent).build();
            //}
            Map<String, Object> sessionAttr = input.getAttributesManager().getSessionAttributes();
            //eligStr = (String) sessionAttr.get("eligibility_confirm");


            if( code.isEmpty() && name.isEmpty() ) {
                if ( sessionAttr.containsKey("course_name") && IntentHelper.isStringValid((String)sessionAttr.get("course_name")) ) {
                    name = (String)sessionAttr.get("course_name");
                } else {
                    speechText += "Please tell me which course you want to query?";
                    //if course name is presented neither in slot nor in session, the skill needs to prompt user to provide one.
                    return input.getResponseBuilder().addElicitSlotDirective("course_name", intentRequest.getIntent()).withSpeech(speechText).build();
                }
            }

            if( IntentHelper.isStringValid(timeConfirm) ){
                timeConfirm = slots.get("eval_confirm").getResolutions().getResolutionsPerAuthority().get(0).getValues().get(0).getValue().getName();
                if( timeConfirm.equalsIgnoreCase("evaluation") ){
                    Slot nameSlot = Slot.builder().withName("course_name").withValue(name).build();
                    Slot codeSlot = Slot.builder().withName("course_code").withValue(code).build();
                    Slot confirmSlot = Slot.builder().withName("time_confirm").withValue("").build();
                    Slot degreeSlot  = Slot.builder().withName("degree").withValue("").build();
                    Intent timeIntent = Intent.builder().withName("EvaluationIntent").putSlotsItem("course_name",nameSlot).putSlotsItem("course_code",codeSlot).putSlotsItem("time_confirm",confirmSlot).putSlotsItem("degree",degreeSlot).build();
                    return input.getResponseBuilder().addDelegateDirective(timeIntent).build();
                }
                else if( timeConfirm.equalsIgnoreCase("instructor") ){
                    String instr = "";
                    if( sessionAttr.containsKey("instructor") ) {
                        instr = (String) input.getAttributesManager().getSessionAttributes().get("instructor");
                    }
                    Slot instructor = Slot.builder().withName("instructor_fullname").withValue(instr).build();
                    Slot confirmSlot    = Slot.builder().withName("confirm").withValue("").build();
                    Intent instructorIntent = Intent.builder().withName("InstructorIntent").putSlotsItem("instructor_fullname",instructor).putSlotsItem("confirm",confirmSlot).build();
                    return input.getResponseBuilder().addDelegateDirective(instructorIntent).build();
                }
                else{
                    Map<String,Object> attr = input.getAttributesManager().getSessionAttributes();
                    attr.remove("origin");
                    input.getAttributesManager().setSessionAttributes(attr);
                    speechText = "Ok. If you want to query about other courses, please let me know the course name.";
                    return input.getResponseBuilder().withSimpleCard("CSD Assistant",speechText).withSpeech(speechText).withShouldEndSession(false).build();
                }
            }

            Map<String, AttributeValue> exprAttr = new HashMap<String, AttributeValue>();
            List<AttributeValue> timeLocationMap = new ArrayList<>();
            if( !code.isEmpty() ) {
                if( sessionAttr.containsKey("course_code") ){//if the queried course exists in session
                    String codes = (String)sessionAttr.get("course_code");
                    List<String> codesArr = Arrays.asList(codes.split("/"));
                    if(codesArr.contains(code)){
                        speechText = (String)sessionAttr.get("course_desc");
                        useCache = true;
                    }

                }
                if(useCache!=true)
                {
                    exprAttr.put(":courseCode", new AttributeValue().withS(code));
                    List<Map<String, AttributeValue>> items = IntentHelper.DBQueryByCourseCode(exprAttr, "course_name,course_code, description,instructor,time_location");
                    if (items.size() == 0) {
                        return input.getResponseBuilder().addElicitSlotDirective("course_name", intent).withSpeech("Sorry, course CS" + code + " may not be available this term. In order to avoid ambiguity, please provide me with the course name.").build();
                    } else {
                        course = IntentHelper.buildCourseObj(items.get(0));
                        speechText = course.getDescription();
                    }
                }
            }
            else if( !name.isEmpty()) {
                name = slots.get("course_name").getResolutions().getResolutionsPerAuthority().get(0).getValues().get(0).getValue().getName();
                if( sessionAttr.containsKey("course_name")){//if the queried course exists in session
                    if(((String)sessionAttr.get("course_name")).equalsIgnoreCase(name)){
                        speechText = (String)sessionAttr.get("course_desc");
                        useCache = true;
                    }
                }
                if( useCache!= true )
                {
                    exprAttr.put(":courseName", new AttributeValue().withS(name));
                    List<Map<String, AttributeValue>> items = IntentHelper.DBQueryByCourseName(exprAttr, "course_name, course_code, description, instructor,time_location");
                    if (items.size() == 0) {
                        //if none item is retrieved, it says that the course name provided by the user is invalid. So we need to prompt the user to give another one.
                        return input.getResponseBuilder().addElicitSlotDirective("course_name", intent).withSpeech("Sorry, course " + name + " may not be available this year. If you would like to know about another course, please give me the course name.").build();
                    } else {
                        course = IntentHelper.buildCourseObj(items.get(0));
                        speechText = course.getDescription();
                    }
                }
            }
            if(useCache == false) {
                sessionAttr.clear();
                List<String> tutors = course.getInstructors();
                String tutorConcat = "";
                if( tutors==null || tutors.size() == 0 ){
                    tutorConcat = "";
                }
                else {
                    tutorConcat = "";
                    for (String tutor : tutors) {
                        tutorConcat += tutor + "|";
                    }
                }
                if( tutorConcat.contains("|") ) {
                    tutorConcat = tutorConcat.substring(0, tutorConcat.lastIndexOf("|"));
                }
                sessionAttr.put("instructor", tutorConcat);
                sessionAttr.put("course_name", course.getCourseName());
                List<String> codeTmps = course.getCourseCode();
                String codeConcat = "";
                if( codeTmps.size()>1 ){
                    codeConcat = codeTmps.get(0)+"/"+codeTmps.get(1);
                }
                else{
                    codeConcat = codeTmps.get(0);
                }
                sessionAttr.put("course_code",codeConcat);
                //sessionAttr.put("time_location", timeLocationMap);
                sessionAttr.put("course_desc", course.getDescription());

            }
            sessionAttr.put("origin","CourseDescIntent");
            input.getAttributesManager().setSessionAttributes(sessionAttr);

            if( !IntentHelper.isStringValid(timeConfirm) ){
                speechText += " If you're interested in this course, I could provide more information on the course evalution and instructor. Just tell me which one you would like to know, course evalution method or instructor?";
                return input.getResponseBuilder().addElicitSlotDirective("eval_confirm",intentRequest.getIntent()).withSpeech(speechText).build();
            }
            else{
                speechText = "Ok. ";
            }

        }
        catch(Exception ex){
            speechText += "";
        }

        //if( !IntentHelper.isStringValid(eligStr) ){
        //    return input.getResponseBuilder().addElicitSlotDirective("eligibility_confirm",intentRequest.getIntent()).withSpeech(speechText + "Do you want me to check the eligibility of enrollment for you?").build();
        //}
        return input.getResponseBuilder().withSpeech(speechText).withSimpleCard("CSD Assistant V1", speechText).withShouldEndSession(false).build();
    }
}
