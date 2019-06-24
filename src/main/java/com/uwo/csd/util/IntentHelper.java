package com.uwo.csd.util;

import com.amazon.ask.model.Slot;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;

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
        return name;
    }

    public static String getCourseCodeIfExists(Map<String,Slot> input){
        String code = "";
        if( !isCourseCodeEmpty(input) ){
            code = input.get("course_code").getValue();
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
        }
        return value;
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
}
