package com.uwo.csd.util;

import com.amazon.ask.model.Slot;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.uwo.csd.entity.Course;

import java.util.*;

public class IntentHelper {
    public static boolean isStringValid(String input){
        boolean flag = false;
        if( input!=null && !input.isEmpty() ){
            flag = true;
        }
        return flag;
    }
    public static boolean isCourseNameEmpty(Map<String,Slot> input){
        boolean flag = true;
        if( input.containsKey("course_name") ){
            Slot slot = input.get("course_name");
            String value = slot.getValue();
            if( isStringValid(value) ) {
                flag = false;
            }
        }
        return flag;
    }

    public static boolean isCourseCodeEmpty(Map<String,Slot> input){
        boolean flag = true;
        if( input.containsKey("course_code") ){
            Slot slot = input.get("course_code");
            String value = slot.getValue();
            if( isStringValid(value) ) {
                flag = false;
            }
        }
        return flag;
    }

    public static boolean isCourseNameAndCourseCodeEmpty(Map<String,Slot> input){
        boolean flag = false;
        if( isCourseNameEmpty(input) && isCourseCodeEmpty(input) ){
            flag = true;
        }
        return flag;
    }

    public static String getCourseNameIfExists(Map<String,Slot> input){
        String name = "";
        if( !isCourseNameEmpty(input) ){
            Slot slot = input.get("course_name");
            name = slot.getValue();
        }
        else{
            name = "";
        }
        return name;
    }

    public static String getCourseCodeIfExists(Map<String,Slot> input){
        String code = "";
        if( !isCourseCodeEmpty(input) ){
            code = input.get("course_code").getValue();
        }
        else{
            code = "";
        }
        return code;
    }

    public static String getSpecifiedSlotValueIfExists(Map<String,Slot> input,String slotName){
        String value = "";
        if( input.containsKey(slotName) ){
            Slot slot = input.get(slotName);
            //String tmp = slot.getResolutions().getResolutionsPerAuthority().get(0).getValues().get(0).getValue().getName();
            String tmp = slot.getValue();
            if( isStringValid(tmp) ){
                value = tmp;
            }
            else{
                value = "";
            }
        }
        return value;
    }

    public static Course buildCourseObj(Map<String,AttributeValue> items){
        String name = "";
        List<String> codes = new ArrayList<>();
        List<Map<String,String>> timeLocation = new ArrayList<>();
        List<String> instructors = new ArrayList<>();
        String desc = "";
        if( items.containsKey("course_name") ){
            name = items.get("course_name").getS();
        }
        if( items.containsKey("course_code") ){
            List<AttributeValue> codesVal = items.get("course_code").getL();
            codesVal.stream().forEach(val->codes.add(val.getS()));
        }
        if( items.containsKey("time_location") ){
            List<AttributeValue> timeLocVals = items.get("time_location").getL();
            for(AttributeValue timeLocVal:timeLocVals){
                Map<String,AttributeValue> timeLoc = timeLocVal.getM();
                String timeTmp = "from "+timeLoc.get("start").getS()+" to "+timeLoc.get("end").getS()+" "+timeLoc.get("day_in_week").getS();
                String locTmp = timeLoc.get("location").getS();
                Map<String,String> mapTmp = new HashMap<>();
                mapTmp.put("time",timeTmp);
                mapTmp.put("location",locTmp);
                timeLocation.add(mapTmp);
            }
        }
        if( items.containsKey("instructor") ){
            List<AttributeValue> item = items.get("instructor").getL();
            item.stream().forEach(val->instructors.add(val.getS()));
        }
        if( items.containsKey("description") ){
            desc = items.get("description").getS();
        }
        Course course = Course.builder().courseCode(codes).courseName(name).instructors(instructors).courseTimeLoc(timeLocation).description(desc).build();
        return course;
    }

    public static List<Map<String, AttributeValue>> DBQueryByCourseName(Map<String, AttributeValue> exprAttr, String projections){
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
        ScanRequest scanRequest = new ScanRequest()
                .withTableName("t_csd_course")
                .withFilterExpression("course_name = :courseName")
                .withProjectionExpression(projections)
                .withExpressionAttributeValues(exprAttr);
        ScanResult result = client.scan(scanRequest);
        List<Map<String, AttributeValue>> items = result.getItems();
        return items;
    }

    public static List<Map<String, AttributeValue>> DBQueryByCourseCode(Map<String, AttributeValue> exprAttr, String projections){
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
        ScanRequest scanRequest = new ScanRequest()
                .withTableName("t_csd_course")
                .withFilterExpression("contains(course_code,:courseCode)")
                .withProjectionExpression(projections)
                .withExpressionAttributeValues(exprAttr);
        ScanResult result = client.scan(scanRequest);
        List<Map<String, AttributeValue>> items = result.getItems();
        return items;
    }

    public static List<Map<String, AttributeValue>> DBQueryByInstructorName(Map<String, AttributeValue> exprAttr, String projections){
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
        ScanRequest scanRequest = new ScanRequest()
                .withTableName("t_csd_instructors")
                .withFilterExpression("fullname = :name")
                .withProjectionExpression(projections)
                .withExpressionAttributeValues(exprAttr);
        ScanResult result = client.scan(scanRequest);
        List<Map<String, AttributeValue>> items = result.getItems();
        return items;
    }

    public static List<Map<String, AttributeValue>> DBQueryByPartialInstructorName(Map<String, AttributeValue> exprAttr, String projections){
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
        ScanRequest scanRequest = new ScanRequest()
                .withTableName("t_csd_instructors")
                .withFilterExpression("contains(fullname,:name)")
                .withProjectionExpression(projections)
                .withExpressionAttributeValues(exprAttr);
        ScanResult result = client.scan(scanRequest);
        List<Map<String, AttributeValue>> items = result.getItems();
        return items;
    }
    public static List<Map<String, AttributeValue>> DBQueryCourseByInstructor(Map<String, AttributeValue> exprAttr, String projections){
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
        ScanRequest scanRequest = new ScanRequest()
                .withTableName("t_csd_course")
                .withFilterExpression("contains(instructor,:name)")
                .withProjectionExpression(projections)
                .withExpressionAttributeValues(exprAttr);
        ScanResult result = client.scan(scanRequest);
        List<Map<String, AttributeValue>> items = result.getItems();
        return items;
    }
    public static List<Map<String, AttributeValue>> DBQueryCourseByTerm(Map<String, AttributeValue> exprAttr, String projections){
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
        ScanRequest scanRequest = new ScanRequest()
                .withTableName("t_csd_course")
                .withFilterExpression("term = :term")
                .withProjectionExpression(projections)
                .withExpressionAttributeValues(exprAttr);
        ScanResult result = client.scan(scanRequest);
        List<Map<String, AttributeValue>> items = result.getItems();
        return items;
    }
    public static String capitalizeName(String name){
        String[] nameParts = name.split("\\s");
        String output = "";
        for(String namePart:nameParts){
            char[] nameChar = namePart.toCharArray();
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(Character.toString(nameChar[0]).toUpperCase());
            for(int i=1; i<nameChar.length;i++){
                stringBuilder.append(nameChar[i]);
            }
            output += stringBuilder.toString()+" ";
        }
        output.substring(0,output.length()-1);
        return output;
    }

    public static String formCourseCodeString(List<AttributeValue> codeAttr){
        String returned = "";
        for(AttributeValue value:codeAttr){
            returned += value.getS()+"/";
        }
        return returned.substring(0,returned.lastIndexOf("/"));
    }
    public static String formInstructorString(List<AttributeValue> instructorAttr){
        String returned = "";
        for(AttributeValue value:instructorAttr){
            returned += value.getS()+"|";
        }
        return returned.substring(0,returned.lastIndexOf("|"));
    }
    public static Map<String,String> formCourseTimeMap(Map<String,AttributeValue> item){
        String timeConcat = "";
        Map<String,String> returned = new HashMap<>();
        if( item.containsKey("time_location") ){
            AttributeValue timeVals = item.get("time_location");
            if (timeVals!=null){
                for (AttributeValue timeVal : timeVals.getL()) {
                    Map<String,AttributeValue> timeMap = timeVal.getM();
                    String tmp = "from "+timeMap.get("start").getS()+" to "+timeMap.get("end").getS()+" "+timeMap.get("day_in_week").getS();
                    returned.put(timeMap.get("day_in_week").getS(),tmp);
                }
            }
        }
        return returned;
    }
    public static String convertDayInWeek(int dayInWeek){
        String day = "";
        Map<Integer,String> dayOfWeek = new HashMap<>();
        dayOfWeek.put(1,"Monday");
        dayOfWeek.put(2,"Tuesday");
        dayOfWeek.put(3,"Wednesday");
        dayOfWeek.put(4,"Thursday");
        dayOfWeek.put(5,"Friday");
        dayOfWeek.put(6,"Saturday");
        dayOfWeek.put(7,"Sunday");
        if( dayInWeek>0 && dayInWeek<8){
            day = dayOfWeek.get(dayInWeek);
        }
        return day;
    }
    public static int getCurrentMonth(){
        Calendar cal = Calendar.getInstance();
        int month = cal.get(Calendar.MONTH)+1;
        return month;
    }
    public static String determineTerm(int month){
        String term = "";
        if(month>=1 && month<=4){
            term = "winter";
        }
        else if( month>=5 && month<=8 ){
            term = "summer";
        }
        else if( month>=9 && month<=12 ){
            term = "fall";
        }
        return term;
    }
    public static String determineEnrollmentTerm(int month){
        String term = "";
        if(month>=12 && month<=1){
            term = "winter";
        }
        else if( month>=4 && month<=5 ){
            term = "summer";
        }
        else if( month>=8 && month<=9 ){
            term = "fall";
        }
        return term;
    }
}
