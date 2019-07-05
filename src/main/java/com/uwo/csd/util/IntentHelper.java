package com.uwo.csd.util;

import com.amazon.ask.model.Slot;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.uwo.csd.entity.Course;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            name = input.get("course_name").getValue();
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
}
