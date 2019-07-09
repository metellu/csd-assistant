package com.uwo.csd.handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.impl.IntentRequestHandler;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.Slot;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.uwo.csd.util.IntentHelper;

import java.util.*;

import static com.amazon.ask.request.Predicates.intentName;

public class CoursesOnSpecifiedDayIntentHandler implements IntentRequestHandler {
    public boolean canHandle(HandlerInput input, IntentRequest intentRequest){
        return input.matches(intentName("CoursesOnSpecifiedDayIntent"));
    }

    public Optional<Response> handle(HandlerInput input, IntentRequest intentRequest) {
        String speechText = "";

        Map<String, Slot> slots= intentRequest.getIntent().getSlots();
        String day = IntentHelper.getSpecifiedSlotValueIfExists(slots,"day_in_week");
        String term = IntentHelper.getSpecifiedSlotValueIfExists(slots,"term");
        String timeConcat = "";


        Calendar cal = Calendar.getInstance();
        if( !IntentHelper.isStringValid(day) ){
            int day_in_week = cal.get(Calendar.DAY_OF_WEEK);
            if(cal.getFirstDayOfWeek() == Calendar.SUNDAY){
                day_in_week = day_in_week -1;
                if( day_in_week == 0 ){
                    day_in_week = 7;
                }
            }
            day = IntentHelper.convertDayInWeek(day_in_week);
        }
        if( !IntentHelper.isStringValid(term) ){
            int month = cal.get(Calendar.MONTH)+1;
            if( month>=1 && month<=4 ){
                term = "winter";
            }
            else if( month>=9 && month<=12 ){
                term = "fall";
            }
            else{
                term = "summer";
            }
        }

        if( IntentHelper.isStringValid(day) && IntentHelper.isStringValid(term)){
            Map<String,AttributeValue> exprAttr = new HashMap<>();
            exprAttr.put(":term",new AttributeValue().withS(term));
            List<Map<String,AttributeValue>> items = IntentHelper.DBQueryCourseByTerm(exprAttr,"course_name,course_code,time_location");
            if( items.size()==0 ){
                speechText = "Sorry, it appears that there's no courses on "+day+" in " + term + " term. Would you like to check another day?";
                return input.getResponseBuilder().addElicitSlotDirective("day_in_week",intentRequest.getIntent()).withSpeech(speechText).build();
            }
            else{
                for(Map<String,AttributeValue> item:items){
                    if( item.containsKey("time_location") ){
                        String courseName = item.get("course_name").getS();
                        String courseCode = IntentHelper.formCourseCodeString(item.get("course_code").getL());
                        List<AttributeValue> times = item.get("time_location").getL();
                        for(AttributeValue timeAttr:times){
                            Map<String,AttributeValue> timeMap = timeAttr.getM();
                            if( timeMap.get("day_in_week").getS().equalsIgnoreCase(day) ){
                                Map<String,String> courseTimeMap = IntentHelper.formCourseTimeMap(item);
                                if( courseTimeMap.containsKey(day) ) {
                                    timeConcat += "CS" + courseCode + " " + courseName + " starts " + courseTimeMap.get(day)+"; ";
                                }
                            }
                        }
                    }
                }
                speechText += "Courses available on "+day+" in "+term+" term includes "+timeConcat;
            }

        }

        return input.getResponseBuilder().withSimpleCard("CSD Assistant",speechText).withSpeech(speechText).withShouldEndSession(false).build();
    }
}
