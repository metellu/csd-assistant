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
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import java.util.*;

import static com.amazon.ask.request.Predicates.intentName;

public class TimeTableIntentHandler implements IntentRequestHandler {
    public boolean canHandle(HandlerInput input, IntentRequest intentRequest){
        return input.matches(intentName("TimeTableIntent"));
    }
    public Optional<Response> handle(HandlerInput input, IntentRequest intentRequest) {
        String speechText = "Sorry, I failed to fetch the time.";
        IntentRequest request = (IntentRequest) input.getRequestEnvelope().getRequest();
        Map<String, Slot> slots = request.getIntent().getSlots();
        String courseName = "";
        String courseCode = "";
        if( slots != null ) {
            if( slots.containsKey("course_name") ) {
                Slot nameSlot = slots.get("course_name");
                if( nameSlot != null ) {
                    courseName = nameSlot.getValue();
                    if( courseName != null ) {
                        speechText = "course code is null.";
                    }
                }
            }
            if( slots.containsKey("course_code") ) {
                Slot codeSlot = slots.get("course_code");
                if(codeSlot != null) {
                    courseCode = codeSlot.getValue();
                }
            }
        } else {
            speechText = "slot is null.";
        }

        if( (courseCode==null && courseName==null) || (courseCode!=null && courseCode.isEmpty()) && (courseName!= null && courseName.isEmpty()) ){
            //When user inputs some courses that don't show in the value list of the 'Course' slot type, the skill will send null to the backend.
            //So here, I used two if conditions. One for no valid course name and course code; The other for all two input values are empty.(may not be useful.)

            //As course_name is defined as an required slot, its value must be set for the skill. For that reason, if this intent is invoked after other intents,
            //the course name value can be accessed via looking up in session.
            Map<String,Object> sessionAttr = input.getAttributesManager().getSessionAttributes();
            if( sessionAttr.containsKey("course_name") ){
                courseName = (String)sessionAttr.get("course_name");
            }
            else{
                //if the course name is neither provided by a user nor found in session, this intent will return Dialog.ElicitSlot Directive to prompt user to provide one.
                return input.getResponseBuilder().addElicitSlotDirective("course_name",intentRequest.getIntent()).withSpeech("Sorry, I'm afraid either you are looking for a non-exist course or you didn't provide a valid course name. If you would like to continue, please tell me another course name. ").build();
            }
        }

        String codeConcat = "";
        String timeConcat = "";
        String error = "";

        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
        Map<String, AttributeValue> exprAttr = new HashMap<String, AttributeValue>();

        if( courseCode != null && !courseCode.isEmpty() ) { //if course code is provided by user
            exprAttr.put(":code", new AttributeValue().withS(courseCode));
            ScanRequest scanReq = new ScanRequest()
                    .withTableName("t_csd_course")
                    .withFilterExpression("contains(course_code,:code)")
                    .withProjectionExpression("course_name,course_time,instructor")
                    .withExpressionAttributeValues(exprAttr);
            ScanResult result = client.scan(scanReq);
            Map<String, String> courseInfo = retrieveCourseTime(result);
            timeConcat = courseInfo.get("time");
            courseName = courseInfo.get("name");
            courseName = courseName;

            speechText = speechText = "Course CS" + courseCode + " " + courseName + " starts " + timeConcat;
            if( !request.getDialogState().toString().equals("COMPLETED") ){
                // as mentioned before, course name is defined as an required slot. However, the design of the skill is to take in either a course code or a course name.
                //In the case of user providing a course code, I take advantage of the course name that retrieved from DB to form the 'course_name' slot. Then return
                //'Dialog.Delegate' Directive to skill to finish the whole intent.
                Slot nameSlot = Slot.builder().withName("course_name").withValue(courseName).build();
                Slot codeSlot = Slot.builder().withName("course_code").withValue(courseCode).build();
                Intent returnedIntent = request.getIntent().builder().putSlotsItem("course_name",nameSlot).putSlotsItem("course_code",codeSlot).withName("TimeTableIntent").build();
                return input.getResponseBuilder().addDelegateDirective(returnedIntent).build();
            }
        } else if( courseName != null && !courseName.equals("") ) {
            exprAttr.put(":name", new AttributeValue().withS(courseName));
            ScanRequest scanReq = new ScanRequest()
                    .withTableName("t_csd_course")
                    .withFilterExpression("course_name = :name")
                    .withProjectionExpression("course_code, course_time, instructor")
                    .withExpressionAttributeValues(exprAttr);
            ScanResult result = client.scan(scanReq);
            Map<String, String> courseInfo = retrieveCourseTime(result);
            if( courseInfo.containsKey("code") ) {
                codeConcat = courseInfo.get("code");
            }
            if( courseInfo.containsKey("time") ) {
                timeConcat = courseInfo.get("time");
            }
            if( timeConcat.equals("") ) {
                //Since the slot validation is disabled in the skill side,it is implemented manually here. When no item can be retrieved from DB, I assume that the input
                // course name is not valid. In that case, the code will prompt user to try another course.
                return input.getResponseBuilder().addElicitSlotDirective("course_name",intentRequest.getIntent()).withSpeech("Sorry, course "+ courseName+ "may be not available this year. Please tell me another course, if you would like to continue.").build();
            } else{
                speechText = "Course CS" + codeConcat + " " + courseName + " starts " + timeConcat;
            }
        }

        input.getAttributesManager().setSessionAttributes(Collections.singletonMap("course_name", courseName));
        return input.getResponseBuilder().
                withSimpleCard("CSD Assistant",speechText).withSpeech(speechText).withShouldEndSession(false).build();

    }
    private Map<String,String> retrieveCourseTime(ScanResult result) {
        String timeConcat = "";
        String codeConcat = "";
        String name = "";
        String tutorConcat = "";
        List<String> tutors = new ArrayList<>();
        Map<String,String> returned = new HashMap<String,String>();
        List<Map<String, AttributeValue>> items = result.getItems();
        if(items.size()==0){
            timeConcat = "ERR";
        }
        else{
            Map<String, AttributeValue> item = items.get(0);
            if( item.containsKey("course_code") ){
                AttributeValue codeVals = item.get("course_code");
                for (AttributeValue codeVal : codeVals.getL()){
                    codeConcat = codeConcat + codeVal.getS() + " or ";
                }
                codeConcat = codeConcat.substring(0,codeConcat.lastIndexOf("or"));
                returned.put("code",codeConcat);
            }
            if( item.containsKey("course_time") ){
                AttributeValue timeVals = item.get("course_time");
                if (timeVals==null){
                    timeConcat = "ERR";
                }
                else {
                    for (AttributeValue timeVal : timeVals.getL()) {
                        timeConcat = timeConcat + timeVal.getS() + " or ";
                    }
                    timeConcat = timeConcat.substring(0, timeConcat.lastIndexOf("or"));
                    returned.put("time", timeConcat);
                }
            }
            if( item.containsKey("course_name") ){
                AttributeValue nameVal = item.get("course_name");
                name = nameVal.getS();
                returned.put("name",name);
            }
            if( item.containsKey("instructor") ){
                AttributeValue tutorVals = item.get("instructor");
                for(AttributeValue tutorVal:tutorVals.getL()){
                    tutorConcat += tutorVal.getS()+" ";
                }
                returned.put("instructor",tutorConcat);
            }
        }
        return returned;
    }

}
